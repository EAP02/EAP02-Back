# ADR-002: API REST versionada en la ruta, con contrato OpenAPI generado desde el código

- **Estado:** Aceptada
- **Fecha:** 2026-09-13
- **Responsable:** _[completar nombre]_ — Arquitectura de Software
- **Historias relacionadas:** transversal a todo el backlog

## Contexto

El lineamiento (§1.3 y §3.1) admite **REST o GraphQL**, y exige en ambos casos
"contratos bien definidos, versionado (solo en el caso de REST), validación y
documentación", además de "definir y publicar el contrato: OpenAPI/Swagger versionado
para REST o esquema (SDL) para GraphQL".

§3.3 es más específico para REST y pide: principios de diseño orientado a recursos,
validación de payloads, errores uniformes con códigos HTTP correctos, versionado del
contrato con política de compatibilidad y retiro, y logs estructurados.

Dos hechos del proyecto pesan en la decisión:

- **No hay frontend.** El perfil avanzado marca el frontend como N/A, así que no existe
  el consumidor heterogéneo que normalmente justifica GraphQL. El consumidor real es
  Swagger UI, una colección de pruebas y los tests de aceptación.
- La **superficie de demostración es el propio contrato**. Si el contrato es bueno, la
  sustentación es buena. Eso favorece un formato que se renderice solo y que el
  evaluador ya sepa leer.

## Alternativas consideradas

### A. GraphQL con Spring for GraphQL

Un único endpoint, esquema SDL como contrato, el cliente pide exactamente los campos
que necesita. El lineamiento incluso exime a GraphQL del requisito de versionado.

Contras que pesan más que esa exención:

- La **autorización deja de ser por endpoint**. §3.4 pide explícitamente "RBAC por
  endpoint"; en GraphQL hay que implementarla por campo o por resolver, que es más
  trabajo y más difícil de evidenciar ante un evaluador.
- **N+1 de resolvers** garantizado en el feed y en los hilos de comentarios anidados.
  Resolverlo bien exige `DataLoader` y batching, que es tiempo que este equipo no tiene.
- Los **códigos HTTP correctos** que pide §3.3 no aplican: GraphQL responde 200 con
  errores en el cuerpo. Habría que justificar la desviación.
- La curva para la persona de Calidad es más pronunciada: menos herramienta estándar
  para pruebas de contrato.

### B. REST con versionado por cabecera o por *media type*

`Accept: application/vnd.devnet.v1+json`. Es más puro desde el punto de vista de la
arquitectura orientada a recursos, porque la URI identifica el recurso y no su versión.

Se descarta por costo de evidencia: el versionado deja de ser visible en Swagger UI y en
los logs, y en una sustentación de treinta minutos lo que no se ve no se evalúa.

### C. REST con versionado en la ruta y OpenAPI generado desde el código

## Decisión

Se adopta la **alternativa C**.

- **Ruta base:** `/api/v1/...`. La versión es un prefijo del path.
- **Contrato:** OpenAPI 3.1 generado por `springdoc-openapi` a partir de los
  controladores y los DTOs anotados. El contrato **no se escribe a mano**: se genera, y
  la anotación en el código es la fuente de verdad.
- **Publicación:** en cada release el pipeline exporta `openapi-v1.<release>.json` como
  artefacto versionado de la construcción. Swagger UI queda expuesto en
  `/swagger-ui.html` en los entornos no productivos.
- **Diseño orientado a recursos:** sustantivos en plural, jerarquía por contención, y
  las transiciones de estado del proceso principal se modelan como **sub-recursos de
  acción**, no como un `PATCH` genérico del campo `estado`:

  | Operación | Verbo y ruta |
  |---|---|
  | Publicar un borrador | `POST /api/v1/publicaciones/{id}/publicacion` |
  | Reportar contenido | `POST /api/v1/publicaciones/{id}/reportes` |
  | Resolver un reporte | `POST /api/v1/reportes/{id}/resolucion` |
  | Archivar | `POST /api/v1/publicaciones/{id}/archivado` |
  | Solicitar colaborar | `POST /api/v1/proyectos/{id}/colaboraciones` |
  | Aceptar colaboración | `POST /api/v1/colaboraciones/{id}/aceptacion` |

  Esto mantiene la autorización anclada a un endpoint concreto (que es lo que pide
  §3.4) y evita que un `PATCH` abierto permita transiciones que la máquina de estados
  no admite.

- **Paginación:** determinista por *keyset* (`?cursor=&limite=`) en las colecciones
  ordenadas por tiempo, tal como exige §5.3. No se usa `OFFSET` en el feed ni en la
  bandeja de mensajes.
- **HATEOAS:** no se adopta. §3.3 lo condiciona a que los enlaces sean "pertinentes y
  no decorativos"; sin cliente que los consuma, serían decorativos. Se documenta aquí
  la no adopción para que conste que fue una decisión y no un olvido.

### Política de compatibilidad y retiro

| Tipo de cambio | Permitido en `v1` |
|---|---|
| Añadir un campo opcional a una respuesta | Sí |
| Añadir un endpoint | Sí |
| Añadir un parámetro opcional | Sí |
| Volver obligatorio un campo de entrada | No, requiere `v2` |
| Eliminar o renombrar un campo de respuesta | No, requiere `v2` |
| Cambiar el tipo o la semántica de un campo | No, requiere `v2` |
| Cambiar un código HTTP de éxito | No, requiere `v2` |

Al introducir `v2`, `v1` se marca como obsoleta con la cabecera `Deprecation` y un
`Sunset` a **un sprint** de distancia. Ambas versiones conviven ese sprint.

## Consecuencias

### Positivas

- El contrato se documenta solo y no se desincroniza del código, porque se genera de él.
- `@PreAuthorize` por método de controlador da RBAC por endpoint directamente
  evidenciable.
- Toda la herramienta estándar de pruebas (MockMvc, RestAssured, la colección de
  peticiones) funciona sin adaptaciones para la persona de Calidad.
- Los códigos HTTP y el `traceId` en la respuesta hacen legible la operación en logs y
  en tableros.

### Negativas y cómo se mitigan

| Riesgo | Mitigación |
|---|---|
| *Over-fetching*: el feed devuelve más de lo necesario | Proyecciones de solo lectura en `analytics` y `profile`; DTOs de resumen distintos de los de detalle |
| Más endpoints que mantener que con un único `/graphql` | Acotado por el recorte de alcance; los reportes son tres endpoints fijos, no un constructor de consultas |
| El versionado en la ruta duplica controladores al llegar `v2` | Solo se duplica la capa `api`; `application` y `domain` no se versionan |
| Riesgo de que el contrato generado quede incompleto | El pipeline falla si un endpoint carece de `@Operation` o de respuestas de error declaradas |

## Verificación

- El pipeline genera el contrato y **falla** si difiere del publicado sin que haya
  cambio de versión declarado (comparación de esquema contra el artefacto anterior).
- Un test de arquitectura comprueba que ningún controlador quede fuera de `/api/v{n}/`.
- Los errores devueltos cumplen el contrato de [ADR-005](ADR-005-contrato-errores-traceid.md).