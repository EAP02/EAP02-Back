# Contexto del Sprint 1 — Arquitectura de Software y Bases de Datos

**Caso 13 · DevNet — Red social para desarrolladores · Equipo avanzado, CodeF@ctory UdeA**

Este documento describe qué quedó construido en el frente de ArquiSoft y Bases de Datos,
por qué está construido así, y qué necesita saber el resto del equipo para trabajar
encima. Es el punto de entrada al repositorio.

Última actualización: 16 de septiembre de 2026.

---

## 1. Estado del sprint

### Arquitectura de Software

| Entregable (§3.7) | Estado |
|---|---|
| Diagrama de paquetes y componentes con interfaces | Listo |
| C4: contexto, contenedores, componentes | Listo |
| Estilo arquitectónico preliminar justificado | Listo |
| Mínimo 3 ADR priorizados | Listo — **hay 6** |
| Proyecto base Spring Boot en GitHub | Listo |
| Al menos una HU implementada | Listo — **hay 2** |
| Despliegue inicial | **Pendiente** |

### Bases de Datos

| Entregable (§3.7) | Estado |
|---|---|
| Entidades y relaciones | Listo |
| Preguntas / consultas clave | Listo — 17, por actor |
| Modelo lógico normalizado | Listo — 29 tablas en 5 agrupaciones |
| Modelo físico inicial | Listo y **verificado contra PostgreSQL 16 real** |

El modelo físico no solo está escrito: la aplicación arranca con
`spring.jpa.hibernate.ddl-auto=validate`, lo que significa que **si una entidad JPA no
correspondiera al esquema que Flyway construyó, la aplicación no levantaría**. Que
arranque es la prueba de que modelo lógico, modelo físico y código coinciden.

---

## 2. Las dos historias implementadas

### HU-06 (AB#15) — Control de acceso por rol

| Criterio de aceptación | Dónde vive | Cómo se verifica |
|---|---|---|
| Un usuario sin permiso recibe 403 y queda registrado el intento | `ManejadorGlobalErrores.accesoDenegado()` escribe en la tabla `auditoria` | `ControlAccesoPorRolIT.el_intento_denegado_se_audita` |
| Un moderador puede ejecutar las acciones de su rol | `POST /api/v1/proyectos/{id}/ocultamiento` con `@PreAuthorize("hasAuthority('publicacion:moderar')")` | `ControlAccesoPorRolIT.moderador_pasa_la_autorizacion` |
| La autorización se verifica en el servidor ante peticiones directas | JWT RS256 + `@EnableMethodSecurity`; sin token 401, token manipulado 401 | `ControlAccesoPorRolIT.sin_token_no_hay_acceso`, `.token_manipulado_se_rechaza` |

El registro del intento denegado se escribe en **transacción propia**
(`REQUIRES_NEW`). Sin eso desaparecería con el rollback de la petición, que es
exactamente el caso que el criterio exige dejar registrado.

Un detalle que conviene conocer: un rol que exige MFA y no lo tiene inscrito **se
autentica pero su token sale sin permisos**. Mismo rol, cero capacidades
(lineamiento 3.4).

### HU-07 (AB#17) — Publicar un proyecto

| Criterio de aceptación | Dónde vive | Cómo se verifica |
|---|---|---|
| Con datos válidos queda asociado al perfil del autor y visible en su lista | `PublicarProyecto` + `GET /api/v1/proyectos/autor/{id}` | `PublicarProyectoIT.CriterioUno` |
| Los campos obligatorios se validan; si faltan, se rechaza | `Proyecto.publicar()` — acumula **todos** los fallos y los devuelve juntos | `ProyectoTest.CriterioDos`, `PublicarProyectoIT.CriterioDos` |
| Solo el autor puede publicar en su nombre | `Proyecto.exigirAutoriaPropia()`; el autor sale del token, nunca del cuerpo | `ProyectoTest.CriterioTres`, `PublicarProyectoIT.CriterioTres` |

**El cruce de ambas historias** es la mejor demostración de que los roles están
diferenciados de verdad: un `ADMIN` autenticado **también recibe 403 al publicar**,
porque administra pero no tiene `publicacion:crear`.

---

## 3. Una decisión revertida: el rol MODERADOR

En una conversación de equipo se acordó **eliminar el rol de moderador**. Al recibir las
historias del sprint se vio que **HU-06 lo exige literalmente**: *"diferenciar permisos
entre usuario, **moderador** y administrador"* y *"un **moderador** puede ejecutar las
acciones permitidas a su rol"*.

Por tanto **el rol se conserva**. Lo que sí quedó fuera del alcance es el **módulo
completo de moderación** (reportes, revisión, apelaciones, suspensiones). HU-06 pide
diferenciar permisos y verificarlos en el servidor, no construir ese flujo.

**Consecuencia que el equipo debe tener presente:** el proceso principal documentado en
`docs/arquitectura/proceso-principal.md` era el ciclo de moderación (reglas R1–R7), y era
lo que aportaba los ≥3 estados con reglas que no se reducen a CRUD. Al quedar fuera, el
candidato natural a proceso principal pasa a ser **el ciclo de colaboración**
(`SOLICITADA → ACEPTADA → ACTIVA → RETIRADA`, con dueño, cupo y no reingreso), más el
gating por reputación. **Está sin decidir y hay que resolverlo en el sprint 2.**

---

## 4. Arquitectura

Spring Boot 4.0.8 · Java 17 · PostgreSQL 16. Monolito modular: un solo artefacto
desplegable, siete módulos de negocio con fronteras explícitas, y un kernel compartido.

```
com.codefactory.devnet
├── config/                 Seguridad, OpenAPI, JWT, caché, datos de demo
├── shared/
│   ├── api/                RespuestaError, ManejadorGlobalErrores, FiltroTraceId, PaginaKeyset
│   ├── integration/        ← contratos ENTRE módulos
│   └── audit/              registro de auditoría
├── identity/               transversal: auth, tokens, MFA, RBAC
├── profile/                perfiles, tecnologías, seguimiento
├── project/                proyectos, repositorios, colaboraciones
├── discussion/             hilos y categorías
├── interaction/            comentarios, reacciones, reputación
├── messaging/              conversaciones privadas
└── analytics/              reportes (solo lectura, sin capa domain)
```

Cada módulo de negocio tiene cuatro capas:

```
api/            controladores REST, DTOs, mapeadores   → application, domain
application/    casos de uso, transacciones            → domain
domain/         entidades, reglas, políticas           → nada del framework
infrastructure/ adaptadores JPA, clientes externos     → domain
```

### La regla que no se negocia

**Ningún módulo importa otro módulo.** `interaction` no conoce
`com.codefactory.devnet.project`; conoce `IContenidoInteractuable`, que vive en
`shared.integration`.

Los cuatro contratos, que son los puertos del diagrama de componentes:

| Contrato | Lo provee | Lo consume |
|---|---|---|
| `IUsuarioDirectorio` | `identity` | todos |
| `IPerfilConsulta` | `profile` | `project`, `discussion`, `interaction`, `messaging` |
| `IContenidoInteractuable` | `project` y `discussion` | `interaction` |
| `IAlmacenArchivos` | `shared` (adaptador Supabase) | `profile` |

Siete reglas de **ArchUnit** rompen el build si alguna se viola: fronteras entre módulos,
dominio sin framework, dirección de capas, kernel sin negocio, ausencia de ciclos,
contratos sin entidades JPA, y un único constructor de respuestas de error.

Si una regla estorba, la respuesta no es relajarla: es revisar si la decisión sigue
siendo la correcta y, si no lo es, escribir un ADR que la reemplace.

---

## 5. Las decisiones registradas

| ADR | Decisión |
|---|---|
| [001](docs/adr/ADR-001-estilo-arquitectonico.md) | Monolito modular con separación hexagonal, no microservicios |
| [002](docs/adr/ADR-002-estilo-api-rest-openapi.md) | API REST versionada en `/api/v1` con OpenAPI generado, no GraphQL |
| [003](docs/adr/ADR-003-persistencia-supabase-flyway.md) | PostgreSQL en Supabase; Flyway como única fuente de verdad del esquema |
| [004](docs/adr/ADR-004-identidad-propia-github-oidc.md) | Identidad propia en Spring Security con GitHub como IdP, no Supabase Auth |
| [005](docs/adr/ADR-005-contrato-errores-traceid.md) | Contrato de errores uniforme y correlación por `traceId` |
| **006** | **Pendiente de escribir** — los contratos entre módulos van en `shared.integration`, no en el `domain` del módulo destino |

### Por qué hace falta el ADR-006

ADR-001 planteaba publicar cada puerto en el paquete `domain` del módulo destino. Eso se
rompe en cuanto dos módulos deben ofrecer la misma capacidad: `interaction` comenta tanto
proyectos como discusiones, y el puerto habría tenido que declararse dos veces, con el
mismo nombre y tipos incompatibles.

Centralizarlos en `shared.integration` lo resuelve y además simplifica la verificación:
la regla de ArchUnit deja de enumerar pares permitidos y pasa a ser una sola prohibición.
**El código ya está así; falta el ADR que lo registre y supersede al 001.**

---

## 6. Dos desviaciones que las reglas de arquitectura detectaron

Vale la pena dejarlo escrito porque es evidencia directa de que el mecanismo funciona, y
es material para la sustentación.

**Los `enum` de códigos de error usaban `org.springframework.http.HttpStatus` dentro de
`domain`.** La regla *"el dominio no depende del framework"* lo marcó. Un status HTTP es
transporte y el dominio no debe saber que HTTP existe. Se cambió a `int`: un entero es un
valor, no una dependencia, y el manejador global lo convierte.

**La regla del constructor de errores era demasiado amplia.** Usaba
`dependOnClassesThat`, que marcaba las anotaciones
`@Schema(implementation = RespuestaError.class)` de los controladores. Pero eso es
documentación del contrato y es justo lo que queremos que hagan. Se afinó a
`callConstructor`, que vigila la construcción y no la mención.

La primera era código malo; la segunda, regla mala. Ambas se corrigieron antes de llegar
a `main`. No fue un diagrama que alguien revisó a ojo: fue un test que rompió el build.

---

## 7. Lo que hay en el repositorio

| Ruta | Contenido |
|---|---|
| [docs/adr/](docs/adr/) | Las decisiones, con alternativas descartadas y cómo verificarlas |
| [docs/arquitectura/](docs/arquitectura/) | C4, paquetes con interfaces, despliegue, proceso principal, RNF |
| [docs/bd/](docs/bd/) | Consultas clave, modelo lógico, diccionario, índices, consultas no triviales, roles |
| [backend/](backend/) | El proyecto Spring Boot. Ver [su README](backend/README.md) |
| [backend/src/main/resources/db/migration/](backend/src/main/resources/db/migration/) | `V1__baseline.sql` y `V2__eventos_seguridad_en_auditoria.sql` |
| [PRUEBA-SPRINT1.md](PRUEBA-SPRINT1.md) | Guía paso a paso para probar ambas HU, con lo que debe aparecer en cada punto |

**Para levantar el proyecto:** [backend/README.md](backend/README.md) — incluye también
las diferencias de Spring Boot 4 respecto a Boot 3, que es lo que encontrarás en la
mayoría de tutoriales.

**Para probar las HU:** [PRUEBA-SPRINT1.md](PRUEBA-SPRINT1.md).

Usuarios de demostración (solo perfil `local`, clave `Devnet2026!`):

| Correo | Rol | Nota |
|---|---|---|
| `dev@devnet.test` | DESARROLLADOR | |
| `mod@devnet.test` | MODERADOR | MFA inscrito — ejerce sus permisos |
| `mod-sin-mfa@devnet.test` | MODERADOR | MFA pendiente — **token sin permisos** |
| `admin@devnet.test` | ADMIN | |

---

## 8. Trabajo adelantado

Lo siguiente **es entregable del Sprint 2 o 3** y ya está hecho:

| Qué | Sprint al que corresponde |
|---|---|
| Justificación de cada índice y su costo de escritura ([04-indices.md](docs/bd/04-indices.md)) | 2 |
| Nueve consultas no triviales ejecutables ([05-consultas-no-triviales.sql](docs/bd/05-consultas-no-triviales.sql)) | 2 |
| Tres consultas de integridad que deben devolver cero filas | 2 |
| Roles de base de datos con mínimo privilegio ([db-roles.sql](docs/bd/db-roles.sql)) | 2 |
| Estimación de volumen y clasificación de datos | 2 |
| Pipeline de GitHub Actions con SCA (Trivy) y detección de secretos (gitleaks) | 2 |
| Dockerfile multi-stage y `docker-compose` | 2–3 |
| Contrato OpenAPI 3.1 completo con esquemas de error | 2–3 |

Las **consultas no triviales** y los **índices justificados** son los que más peso tienen
en la sustentación de Bases de Datos: hay un CTE recursivo para los hilos de comentarios,
`DISTINCT ON` para la bandeja, `generate_series` con `LEFT JOIN` para que los días sin
actividad no desaparezcan de la serie, y `RANK` con `LAG` para el movimiento de
tecnologías por trimestre.

---

## 9. Pendiente

| # | Qué |
|---|---|
| 1 | **Despliegue inicial** en Render + proyecto Supabase |
| 2 | **ADR-006** que registre los contratos en `shared.integration` y supersede al 001 |
| 3 | Actualizar los `.puml`: todavía muestran el módulo **Moderación**, que no existe en el código (§4.4 lo exige en el mismo sprint) |
| 4 | Decidir el **proceso principal** que sustituye al ciclo de moderación |
| 5 | Documentar la `V2` en `docs/bd` |
| 6 | Rellenar `Responsable` en los 5 ADR y los `AB#___` de las matrices de trazabilidad |
| 7 | Subir el umbral de cobertura de `0.00` a `0.40` al cerrar el Sprint 2, y a `0.65` en el 3 |

El punto 4 es una decisión de equipo, no técnica, y conviene resolverla al planear el
Sprint 2.