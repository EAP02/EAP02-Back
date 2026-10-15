# APIs priorizadas

Entregable de Arquitectura de Software del sprint 2 (§3.7: *«APIs priorizadas; al menos
tres APIs REST… documentación de las APIs»*).

- **Contrato:** OpenAPI 3.1 generado desde el código (ADR-002). Navegable en
  [Swagger UI](https://eap02-back.onrender.com/swagger-ui.html) y descargable en
  [`/v3/api-docs`](https://eap02-back.onrender.com/v3/api-docs). Este documento no lo
  repite: explica **qué API importa más y por qué**.
- **Versión:** todas bajo `/api/v1`, con la política de compatibilidad y retiro del
  [ADR-002](../adr/ADR-002-estilo-api-rest-openapi.md#política-de-compatibilidad-y-retiro).
- **Errores:** cuerpo uniforme con `errorCode`, `message`, `details`, `traceId`,
  `timestamp` y `path` en todas ellas (ADR-005, §3.3).
- **Estado:** a 14 de octubre de 2026, sobre `develop`.

## Criterio de prioridad

La prioridad se asigna por **dependencia**, no por preferencia: una API es más prioritaria
cuanto más cosas dejan de funcionar sin ella.

| Prioridad | Significado | Por qué ese lugar |
|---|---|---|
| **P1 — Habilitante** | Sin ella ninguna otra operación protegida es posible | Toda escritura exige un token, y todo token sale de aquí |
| **P2 — Proceso principal** | Implementa el ciclo de vida de la publicación de `proceso-principal.md` (R1–R6) | Es lo que el §2.2 y el §3.1 piden demostrar: reglas que no se reducen a CRUD, con estados y RBAC |
| **P3 — Descubrimiento y comunidad** | Da valor sobre el contenido ya publicado: encontrarlo, conversar | Depende de P2: sin proyectos publicados no hay nada que buscar ni discutir |
| **P4 — Soporte** | Necesaria para que otras funcionen, sin valor propio para el usuario | Datos de referencia y utilidades |

Dentro de cada nivel, el orden es el de implementación.

## Inventario

**17 endpoints en 5 recursos y 4 módulos.** «Público» significa que no exige token; todo lo
demás exige `Authorization: Bearer`.

### P1 — Habilitante · módulo `identity`

| Método y ruta | Qué hace | Acceso | Historia | Pruebas |
|---|---|---|---|---|
| `POST /api/v1/auth/registro` | Crea la cuenta y, por evento, su perfil inicial | Público | HU-01 | `pruebas-sprint1.md` §1 |
| `POST /api/v1/auth/inicio-sesion` | Emite el token de acceso (15 min) y la cookie de refresco; bloquea tras 5 fallos | Público | HU-02, HU-03 | `UsuarioTest`, `ControlAccesoPorRolIT` |
| `POST /api/v1/auth/refresco` | Renueva el token rotando el refresco; detecta reuso y revoca la familia | Público (credencial: la cookie) | ADR-004, §3.4 | `RefrescoIT`, `RefrescarSesionTest` |
| `POST /api/v1/auth/cierre-sesion` | Revoca el refresco y borra la cookie | Público (credencial: la cookie) | ADR-004, §3.4 | `RefrescoIT` |

### P2 — Proceso principal · módulo `project`

| Método y ruta | Qué hace | Acceso | Historia | Pruebas |
|---|---|---|---|---|
| `POST /api/v1/proyectos` | Publica un proyecto (R1) | `publicacion:crear` | HU-07 | `ProyectoTest`, `PublicarProyectoIT` |
| `GET /api/v1/proyectos/autor/{autorId}` | Proyectos publicados de un autor, por cursor | Público | HU-07 | `PublicarProyectoIT` |
| `PUT /api/v1/proyectos/{proyectoId}` | Edita el contenido; solo el autor o un moderador (R6) | `publicacion:editar` o `publicacion:moderar` + ABAC | HU-08 | `EditarProyectoTest`, `EditarProyectoIT` |
| `POST /api/v1/proyectos/{proyectoId}/archivado` | Elimina (archiva) con confirmación del título (R3) | `publicacion:archivar` o `publicacion:moderar` + ABAC | HU-08 | `ArchivarProyectoTest`, `ArchivarProyectoIT` |
| `POST /api/v1/proyectos/{proyectoId}/ocultamiento` | Retira un proyecto de la vista pública con motivo (R2) | `publicacion:moderar` | HU-06 | `ControlAccesoPorRolIT` |

### P3 — Descubrimiento y comunidad · módulos `project`, `discussion`, `profile`

| Método y ruta | Qué hace | Acceso | Historia | Pruebas |
|---|---|---|---|---|
| `GET /api/v1/proyectos?q=&tecnologia=` | Busca por texto completo y filtra por tecnología; estado vacío con mensaje | Público | HU-09 | `CriteriosBusquedaTest`, `BuscarProyectosIT` |
| `POST /api/v1/discusiones` | Abre una discusión etiquetada | `publicacion:crear` | HU-11 | `DiscusionTest`, `CrearDiscusionIT` |
| `GET /api/v1/discusiones/{discusionId}` | Discusión con su autor y el hilo de participantes | Público | HU-11 | `CrearDiscusionIT` |
| `GET /api/v1/discusiones?tecnologia=` | Lista y filtra discusiones por etiqueta, por cursor | Público | HU-12 | `FiltroDiscusionesTest`, `ListarDiscusionesIT` |
| `GET /api/v1/perfiles/{id}` | Perfil público con sus proyectos | Público | Perfil (sprint 1) | `pruebas-sprint1.md` §4 |
| `PUT /api/v1/perfiles/{id}` | Edita el perfil propio y su stack | Autenticado + ABAC (titular) | Perfil (sprint 1) | `PerfilTest`, `PoliticaPerfilTest` |
| `PATCH /api/v1/perfiles/{id}/avatar` | Cambia el avatar propio | Autenticado + ABAC (titular) | Perfil (sprint 1) | `pruebas-sprint1.md` §4.2 |

### P4 — Soporte · módulo `project`

| Método y ruta | Qué hace | Acceso | Historia | Pruebas |
|---|---|---|---|---|
| `GET /api/v1/tecnologias` | Catálogo de tecnologías aprobadas: `id` para publicar, `slug` para filtrar | Público | HU-09 | `BuscarProyectosIT` |

Fuera de `/api/v1`, `GET /actuator/health` es público y lo usan Render y el `HEALTHCHECK`
del `Dockerfile` para saber si el servicio está vivo.

## Cobertura de los requisitos del sprint 2

| Requisito | Cumplimiento |
|---|---|
| Al menos tres APIs REST (§3.7) | Cinco recursos: `auth`, `perfiles`, `proyectos`, `tecnologias`, `discusiones` |
| Endpoints de diferentes dominios o módulos (§3.3) | Cuatro módulos: `identity`, `profile`, `project`, `discussion` |
| Diseño orientado a recursos (§3.3) | Sustantivos en plural; filtros como parámetros de consulta sobre la colección; las transiciones de estado como sub-recursos de acción (`/archivado`, `/ocultamiento`), no como `PATCH` del campo `estado` (ADR-002) |
| RBAC por endpoint (§3.1, §3.4) | Cada escritura declara su permiso con `@PreAuthorize`; la propiedad se verifica además en el dominio (ABAC) |
| Validación de payloads y errores uniformes (§3.3) | Bean Validation en la API y reglas en el dominio; todos los errores salen de `ManejadorGlobalErrores` |
| Contrato versionado y documentado (§3.1, §3.3) | OpenAPI 3.1 generado, `/api/v1`, política de compatibilidad en el ADR-002 |
| Paginación determinista (§5.3) | Las tres colecciones que crecen sin tope paginan por cursor sobre `(publicado_en, id)`, con un máximo de 50 por página |

## Siguientes APIs: propuesta de prioridad

Las tablas de estas capacidades ya existen en el modelo de datos (`V1__baseline.sql`) y los
permisos están sembrados, pero no tienen endpoint. El orden es una **propuesta
técnica** para el Product Owner, según las dependencias y los hallazgos de
[`owasp-top-10.md`](../seguridad/owasp-top-10.md); la decisión de qué entra en cada
sprint es del backlog.

| Prioridad propuesta | API | Por qué | Depende de |
|---|---|---|---|
| P1 | Inscripción y verificación de MFA | Requisito de §3.4 para roles administrativos; hoy un moderador solo puede operar si se activa por SQL (hallazgo H-14) | — |
| P1 | Suspender cuentas y asignar roles (`usuario:suspender`, `usuario:gestionar-roles`) | Sin ella, revocar el acceso exige tocar la base a mano (H-01) | — |
| P2 | Comentar (`comentario:crear`) | Completa las discusiones: el hilo de HU-11 ya los muestra en cuanto existan | HU-11 |
| P2 | Restituir una publicación oculta (R4) | Cierra la máquina de estados de `proceso-principal.md` | HU-06 |
| P3 | Solicitar y resolver colaboraciones | Máquina de estados ya modelada en `colaboracion`; candidata a proceso principal | HU-07 |
| P3 | Reaccionar (`reaccion:crear`) y reputación | Alimenta `evento_reputacion` y la regla de reputación mínima pendiente | Comentarios |
| P4 | Mensajería privada (`mensaje:enviar`) | Módulo `messaging` previsto en el ADR-001 | Reputación (`minima-para-mensaje-a-desconocido`) |

## Pendiente respecto al ADR-002

El ADR-002 prevé dos controles que el pipeline todavía no aplica:

- exportar `openapi-v1.<release>.json` como artefacto de cada versión;
- fallar si un endpoint no declara `@Operation` o sus respuestas de error.

Hoy todos los endpoints los declaran, pero nada lo garantiza. Son candidatos para el
refinamiento de APIs del sprint 3 (§3.7).
