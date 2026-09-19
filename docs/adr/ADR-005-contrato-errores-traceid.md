# ADR-005: Contrato de errores uniforme y correlación de extremo a extremo por `traceId`

- **Estado:** Aceptada
- **Fecha:** 2026-09-13
- **Responsable:** _[completar nombre]_ — Arquitectura de Software
- **Historias relacionadas:** transversal a todo el backlog

## Contexto

§3.3 exige "devolver errores uniformes con códigos HTTP correctos y campos como
`errorCode`, `message`, `details` y `traceId`", además de "generar logs estructurados en
JSON o en otro formato consistente". §4.3 pide trazabilidad que correlacione "requisitos,
historias, cambios, pruebas, despliegues y eventos operativos", y §7.3 pide "logs
estructurados y correlación mediante `traceId`".

En un monolito modular ([ADR-001](ADR-001-estilo-arquitectonico.md)) el manejo de errores
es uno de los puntos donde la modularidad se rompe más fácil: si cada módulo inventa su
formato, el contrato deja de ser uniforme aunque el código esté bien separado.

## Alternativas consideradas

### A. `ProblemDetail` de RFC 9457 tal cual

Spring 6 lo trae de fábrica (`ProblemDetail`, `ErrorResponseException`) y es un estándar
real, con campos `type`, `title`, `status`, `detail`, `instance`.

Se descarta como formato **exclusivo** porque sus nombres de campo no coinciden con los
que pide §3.3, y renombrarlos rompería la conformidad con el RFC. Cumplir a medias dos
contratos es peor que cumplir uno.

### B. Excepciones de Spring sin manejador global

Dejar que Spring devuelva su respuesta de error por defecto. Se descarta sin discusión:
filtra trazas de pila y nombres de clase al cliente, y no es uniforme.

### C. Contrato propio alineado con §3.3, construido sobre `ProblemDetail` como base

## Decisión

Se adopta la **alternativa C**: un único `@RestControllerAdvice` en `shared` que traduce
toda excepción a este cuerpo, en **todos** los casos de error, sin excepción ni módulo
exento:

```json
{
  "errorCode": "PUBLICACION_TRANSICION_INVALIDA",
  "message": "No se puede publicar una publicación archivada.",
  "details": [
    { "campo": "estado", "valor": "ARCHIVADO", "razon": "transicion_no_permitida" }
  ],
  "traceId": "4b1e9f2a7c3d5e80",
  "timestamp": "2026-09-13T14:22:31.118Z",
  "path": "/api/v1/publicaciones/8f3a.../publicacion"
}
```

### Reglas del contrato

1. **`errorCode`** es estable, en `SCREAMING_SNAKE_CASE`, con prefijo del módulo
   (`PUBLICACION_`, `COLABORACION_`, `AUTH_`, `MENSAJE_`). Es lo que un cliente debe
   programar. Forma parte del contrato: cambiarlo o eliminarlo exige `v2`
   ([ADR-002](ADR-002-estilo-api-rest-openapi.md)).
2. **`message`** está en español, dirigido a una persona, y **nunca** contiene nombres de
   clase, consultas SQL, rutas de archivo ni fragmentos de traza de pila.
3. **`details`** es siempre un arreglo, incluso con un solo elemento o vacío. Para errores
   de validación lleva un elemento por campo inválido.
4. **`traceId`** aparece en absolutamente toda respuesta de error y también como cabecera
   `X-Trace-Id` en **todas** las respuestas, incluidas las exitosas.
5. Toda excepción no contemplada se traduce a `500` con `errorCode: ERROR_INTERNO` y un
   `message` genérico. El detalle real va al log, correlacionado por el mismo `traceId`.
   El usuario recibe el identificador; el equipo tiene el diagnóstico.

### Mapa de códigos HTTP

| Situación | HTTP | Ejemplo de `errorCode` |
|---|---|---|
| Payload inválido, violación de Bean Validation | `400` | `VALIDACION_FALLIDA` |
| Sin autenticar o token inválido/expirado | `401` | `AUTH_TOKEN_INVALIDO` |
| Autenticado pero sin permiso (RBAC o ABAC) | `403` | `ACCESO_DENEGADO` |
| Rol con MFA obligatorio sin inscribir | `403` | `AUTH_MFA_REQUERIDO` |
| Recurso inexistente, o existente pero no visible para quien pregunta | `404` | `PUBLICACION_NO_ENCONTRADA` |
| Transición de estado no permitida por la máquina de estados | `409` | `PUBLICACION_TRANSICION_INVALIDA` |
| Violación de unicidad del dominio | `409` | `SEGUIMIENTO_DUPLICADO` |
| Bloqueo optimista (`@Version`) | `409` | `CONFLICTO_CONCURRENCIA` |
| Regla de negocio incumplible con el estado actual | `422` | `REPUTACION_INSUFICIENTE` |
| Límite de tasa superado | `429` | `LIMITE_TASA_SUPERADO` |
| Fallo no previsto | `500` | `ERROR_INTERNO` |

Dos decisiones dentro de este mapa merecen constancia explícita:

- **`404` en vez de `403` para recursos no visibles.** Si un usuario pide una publicación
  oculta por moderación o un borrador ajeno, la respuesta es `404`, no `403`. Un `403`
  confirmaría la existencia del recurso y filtraría información por diferencia de
  respuestas. La autorización ocurre igual y se registra igual; lo que cambia es lo que se
  revela.
- **`409` frente a `422`.** `409` es conflicto con el *estado actual* del recurso (la
  máquina de estados no admite esa transición); `422` es una regla de negocio que la
  petición incumple independientemente del estado (reputación insuficiente, cupo de
  colaboradores lleno). Distinguirlos evita el `400` para todo, que es el olor típico de
  un manejo de errores sin diseñar.

### Correlación

Un `OncePerRequestFilter` en `shared`:

1. Lee `X-Trace-Id` de la petición si viene; si no, genera uno.
2. Lo pone en el MDC de Logback y en el contexto de la petición.
3. Lo devuelve como cabecera `X-Trace-Id` en la respuesta.
4. Lo limpia del MDC al terminar, incluso ante excepción.

Todos los logs salen en JSON mediante `logstash-logback-encoder`, con `traceId`,
`usuarioId` (cuando hay sesión), `metodo`, `ruta`, `estadoHttp` y `duracionMs` como campos
de primer nivel. El mismo `traceId` se escribe en la columna `auditoria.trace_id`, de modo
que un evento de auditoría en la base de datos, una línea de log y una respuesta de error
al cliente se pueden unir por un solo valor. Esa unión es la trazabilidad operativa que
pide §4.3.

## Consecuencias

### Positivas

- El cliente programa contra `errorCode`, que es estable, y no contra el texto del mensaje.
- Un usuario que reporta un fallo entrega su `traceId` y el equipo llega al log exacto sin
  búsqueda a ciegas.
- El manejador global centralizado impide que un módulo se desvíe del formato.
- Cuando entren Prometheus y Grafana en el sprint 3, los logs ya son consultables y
  correlacionables; no hay que reinstrumentar nada.

### Negativas y cómo se mitigan

| Riesgo | Mitigación |
|---|---|
| El catálogo de `errorCode` crece sin control | Cada código se declara en un `enum` por módulo; ArchUnit prohíbe construir la respuesta de error con cadenas literales |
| Traducir toda excepción a un código añade trabajo por caso de uso | Solo se declaran códigos específicos donde hay regla de negocio; el resto cae en los códigos genéricos por tipo de excepción |
| Un `500` genérico puede ocultar un fallo recurrente | Alerta sobre la tasa de `ERROR_INTERNO` en el tablero de Grafana del sprint 3 |
| Riesgo de filtrar datos sensibles en `details` | Prueba automatizada que verifica que ninguna respuesta de error contenga los campos marcados como sensibles |

## Verificación

- Prueba de integración que recorre un caso por cada fila del mapa de códigos HTTP y
  valida el esquema completo del cuerpo de error.
- Prueba que confirma que `X-Trace-Id` viaja en toda respuesta y que el valor entrante se
  respeta cuando el cliente lo envía.
- Prueba sobre el `appender` de logs que verifica ausencia de `password`, `token` y
  `secret` en la salida.
- El contrato de error se declara una sola vez en OpenAPI y se referencia desde cada
  endpoint; el pipeline falla si un endpoint declara un esquema de error propio.