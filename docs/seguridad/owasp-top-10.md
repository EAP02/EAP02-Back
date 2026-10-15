# Identificación de vulnerabilidades — OWASP Top 10:2025

Entregable de Arquitectura de Software del sprint 2 (§3.7: *«identificación de
vulnerabilidades con OWASP Top Ten»*). Responde también a §3.4 (*«gestión de
vulnerabilidades con tiempos de remediación definidos»*) y a §6.3.

- **Edición:** [OWASP Top 10:2025](https://owasp.org/Top10/2025/0x00_2025-Introduction/),
  la vigente. Sustituye a la de 2021: incorpora *Software Supply Chain Failures* (A03) y
  *Mishandling of Exceptional Conditions* (A10), y SSRF pasa a formar parte de A01.
- **Alcance:** la API desplegada en `main` (`https://eap02-back.onrender.com`), su base de
  datos y su pipeline. No hay frontend (§1.3).
- **Fecha del análisis:** 14 de octubre de 2026, sobre el commit de `develop` posterior a
  HU-12 y al pipeline de CI/CD.
- **Método:** revisión del código y la configuración, más comprobaciones contra producción
  marcadas como *verificado en producción*. **No es una prueba de penetración** ni un DAST:
  esos corresponden a §6.1 (*Pruebas*) y quedan como pendiente.

Cada categoría lista primero los **controles que existen**, con la clase o el test que los
demuestra, y después los **hallazgos**: lo que falta o es débil. Los hallazgos se
consolidan al final con severidad y plazo.

---

## A01:2025 — Broken Access Control

**Controles existentes**

| Control | Dónde | Evidencia |
|---|---|---|
| Denegación por defecto: toda ruta no listada como pública exige autenticación | `SeguridadConfig` (`anyRequest().authenticated()`) | `ControlAccesoPorRolIT` |
| RBAC por permiso, no por nombre de rol, en cada endpoint que escribe | `@PreAuthorize("hasAuthority(...)")` en los controladores | `ControlAccesoPorRolIT`, `PublicarProyectoIT` |
| ABAC por propiedad: solo el titular edita su perfil; solo el autor o un moderador modifica un proyecto | `PoliticaPerfil`, `PoliticaProyecto` (dominio) | `PoliticaPerfilTest`, `EditarProyectoTest`, `EditarProyectoIT`, `ArchivarProyectoIT` |
| La identidad sale siempre del token, nunca de la ruta ni del cuerpo | `EditarPerfil`, `PublicarProyecto`, `CrearDiscusion` | `PublicarProyectoIT` (criterio 3), `CrearDiscusionIT` |
| Un recurso no visible responde 404 y no 403, para no confirmar su existencia | `PoliticaProyecto.rechazoPara` (ADR-005) | `EditarProyectoIT.un_proyecto_oculto_no_se_revela` |
| Todo acceso denegado queda auditado, también los de las reglas de dominio | `ManejadorGlobalErrores.accesoDenegado`, `EditarProyecto`, `ArchivarProyecto` | `ControlAccesoPorRolIT.el_intento_denegado_se_audita`, `EditarProyectoIT.el_intento_se_audita` |
| CORS restringido a orígenes configurados | `SeguridadConfig.configuracionCors`, `CORS_ORIGENES` | *Verificado en producción:* un origen no autorizado no recibe `Access-Control-Allow-Origin` |
| Actuator cerrado salvo `health` e `info` | `SeguridadConfig` | *Verificado en producción:* `/actuator/env` responde 401 |
| SSRF: el servidor no hace peticiones salientes con URLs del usuario | — | Las URLs de repositorio, demo y avatar solo se guardan, y deben ser `https://` (`Proyecto`, `Perfil`) |

**Hallazgos**

- **H-01** · No existe ningún caso de uso que suspenda o desactive una cuenta, ni endpoint
  para asignar roles. La revocación efectiva depende de modificar la base a mano, y el token
  de acceso sigue valiendo hasta 15 minutos (ADR-004).
- **H-02** · SSRF pasará a ser relevante cuando se integre la API de GitHub, prevista en el
  ADR-004. Hoy no aplica; se deja anotado para no perderlo.

## A02:2025 — Security Misconfiguration

**Controles existentes**

| Control | Dónde | Evidencia |
|---|---|---|
| El perfil `prod` no arranca si falta un secreto: sin valores por defecto | `application-prod.yml` | Comentario del archivo; `docs/pruebas-sprint1.md` §0.4 |
| Esquema gobernado solo por Flyway; Hibernate en `validate` | `application.yml` (ADR-003) | La app no arranca si entidad y esquema difieren |
| Sesión sin estado; CSRF deshabilitado con justificación escrita | `SeguridadConfig` | Comentario de la cadena de filtros (ADR-004) |
| Cabeceras de seguridad | Valores por defecto de Spring Security | *Verificado en producción:* `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store` |
| Cookie de refresco `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth` | `CookieRefresco` | `RefrescoIT`; `docs/pruebas-sprint1.md` §2 |
| Imagen sin Maven, sin código fuente y con usuario sin privilegios | `Dockerfile` (multi-stage, `USER devnet`) | — |

**Hallazgos**

- **H-03** · Swagger UI y `/v3/api-docs` están abiertos en producción. Es un **riesgo
  aceptado** y registrado en el ADR-007: sin frontend es la única superficie de demostración.
  Expone la enumeración de endpoints, no acceso.
- **H-04** · ~~Los roles de base de datos de mínimo privilegio estaban escritos en
  `docs/bd/db-roles.sql` pero no aplicados, y producción conectaba como `postgres`.~~
  **Resuelto el 14 de octubre de 2026:** producción conecta como `devnet_app`, que lee y
  escribe datos pero no puede crear ni alterar objetos, y las migraciones las aplica
  `devnet_migrador`, propietario del esquema. `devnet_lectura` no ve credenciales ni
  mensajes. Procedimiento y pruebas en [`docs/bd/README.md`](../bd/README.md).
- **H-05** · No hay `Content-Security-Policy`. En una API que solo devuelve JSON el impacto
  es bajo, pero Swagger UI sí sirve HTML desde el mismo origen.
- **H-06** · ~~`.env.example` proponía el pooler de transacción (puerto 6543), mientras el
  diagrama de despliegue documenta que debe usarse el de sesión (5432) por el bloqueo de
  Flyway.~~ **Resuelto el 14 de octubre de 2026:** la plantilla usa el *Session pooler*
  (5432) y explica por qué.

## A03:2025 — Software Supply Chain Failures

**Controles existentes**

| Control | Dónde |
|---|---|
| Versiones gestionadas por el BOM de Spring Boot; las externas, fijadas explícitamente | `pom.xml` (Testcontainers, ArchUnit, Bouncy Castle) |
| La imagen que se publica es la del commit que pasó las pruebas, etiquetada con su SHA | `.github/workflows/ci.yml`, job `imagen` |
| Producción solo recibe código que pasó el pipeline (deploy hook tras `verificar`) | `ci.yml`, job `desplegar`; `docs/ci-cd.md` |
| Ramas `develop` y `main` protegidas: PR con revisión y comprobación obligatoria | Reglas de rama de GitHub (§7.1) |

**Hallazgos**

- **H-07** · **No hay análisis de composición de software (SCA).** Ninguna herramienta
  revisa las dependencias contra bases de CVE: ni Dependabot, ni OWASP Dependency-Check, ni
  Trivy. §3.4 y §7.2 lo exigen. Es el hallazgo de mayor prioridad de esta categoría.
- **H-08** · Las acciones de GitHub y las imágenes base de Docker se referencian por
  etiqueta (`@v4`, `eclipse-temurin:17`), no por digest. Una etiqueta se puede mover.
- **H-09** · No hay inventario de dependencias (SBOM) generado en el pipeline.

## A04:2025 — Cryptographic Failures

**Controles existentes**

| Control | Dónde |
|---|---|
| Contraseñas con Argon2id (parámetros OWASP: 19 MiB, 2 iteraciones) | `SeguridadConfig.codificadorClaves` |
| Tokens de acceso firmados con RS256; la clave privada solo existe en el backend | `JwtConfig`, `ServicioJwt` (ADR-004) |
| Tokens de refresco de 256 bits aleatorios; en la base solo se guarda su SHA-256 | `GeneradorTokenRefrescoSeguro`, `RepositorioTokensRefrescoJpa` |
| TLS en todo el camino: HTTPS en Render, `sslmode=require` hacia Supabase | `application-prod.yml`, `.env.example` |
| Secretos fuera del repositorio: variables de Render y GitHub Secrets | `.env.example`, `.dockerignore`, `docs/ci-cd.md` |

**Hallazgos**

- **H-10** · Fuera de `prod`, si no hay claves JWT configuradas se genera un par efímero
  (`JwtConfig`). Es intencional para desarrollo local y se avisa por log; en `prod` la
  aplicación no arranca sin ellas. Sin acción, se documenta para que nadie lo lea como fallo.

## A05:2025 — Injection

**Controles existentes**

| Control | Dónde | Evidencia |
|---|---|---|
| Consultas parametrizadas en todo el acceso a datos: JPA, consultas nativas con `@Param` y `JdbcTemplate` con `?` | Adaptadores de `infrastructure` | §5.3: *«nunca concatenar entrada del usuario en SQL»* |
| El SQL dinámico solo concatena **fragmentos constantes**; los valores van siempre como parámetros | `RepositorioProyectosJpa.buscar`, `RepositorioDiscusionesJdbc.listar` y `etiquetasDe` | Javadoc de cada método; `BuscarProyectosIT`, `ListarDiscusionesIT` |
| La búsqueda de texto usa `websearch_to_tsquery`, que no falla ante sintaxis arbitraria del usuario | `RepositorioProyectosJpa.buscar` | `BuscarProyectosIT` |
| Validación de forma en dos capas: Bean Validation en la API y reglas en el dominio | Controladores + `Proyecto`, `Discusion`, `Perfil`, `CriteriosBusqueda` | `ProyectoTest`, `DiscusionTest`, `CriteriosBusquedaTest` |
| Restricciones `CHECK` en la base como última línea de defensa | `V1__baseline.sql` | `EsquemaIT` |

**Hallazgos**

- **H-11** · La API guarda y devuelve texto libre (títulos, descripciones, cuerpos) sin
  sanearlo, como corresponde a una API JSON. **El cliente que lo pinte debe escapar la
  salida.** Hoy no hay frontend; el riesgo de XSS almacenado nace el día que lo haya. Se
  documenta para el equipo que lo construya.

## A06:2025 — Insecure Design

**Controles existentes**

| Control | Dónde |
|---|---|
| Reglas de negocio en el dominio, sin framework, imposibles de saltar por otra ruta | Capa `domain` de cada módulo; `FronterasModularesTest` regla 2 |
| Fronteras entre módulos verificadas en cada build | `FronterasModularesTest` (7 reglas) |
| Máquina de estados de la publicación con transiciones restringidas e historial transaccional | `proceso-principal.md` (R1–R6), `historial_estado_publicacion` |
| Eliminar exige confirmación escribiendo el título, y archiva en lugar de borrar | `Proyecto.archivar` (HU-08.3) |
| Límites de abuso en lecturas: páginas de 50 como máximo, término de búsqueda de 100 caracteres, paginación por cursor | `BuscarProyectos`, `ListarDiscusiones`, `CriteriosBusqueda` |
| Bloqueo de cuenta tras 5 intentos fallidos durante 15 minutos | `Usuario`, `application.yml` |

**Hallazgos**

- **H-12** · **No hay limitación de tasa** en `/api/v1/auth/**` ni en el resto de la API
  (pendiente con Bucket4j desde el sprint 1, ADR-004). Sin ella, el bloqueo por intentos se
  vuelve un arma: cualquiera puede **bloquear la cuenta de otro usuario** enviando cinco
  contraseñas incorrectas con su correo, y repetirlo cada 15 minutos.
- **H-13** · El registro responde `409 AUTH_CORREO_YA_REGISTRADO`, lo que permite comprobar
  si un correo tiene cuenta. El inicio de sesión sí responde igual para correo inexistente y
  contraseña incorrecta. Es un compromiso habitual de usabilidad, pero debería decidirse y
  registrarse explícitamente.

## A07:2025 — Authentication Failures

**Controles existentes**

| Control | Dónde | Evidencia |
|---|---|---|
| Mismo error para correo inexistente y contraseña incorrecta | `AutenticarUsuario` | `docs/pruebas-sprint1.md` §2.1 |
| Bloqueo tras intentos fallidos | `Usuario` | `UsuarioTest` |
| Token de acceso de 15 minutos; refresco de 7 días con **rotación por familia y detección de reuso** | `RefrescarSesion`, `TokenRefresco` (ADR-004) | `RefrescoIT`, `RefrescarSesionTest`, `TokenRefrescoTest` |
| Ante un reuso se revoca la familia entera, en su propia transacción | `RepositorioTokensRefrescoJpa.revocarFamilia` (`REQUIRES_NEW`) | `RefrescoIT` |
| Cierre de sesión que revoca el refresco y borra la cookie | `CerrarSesion` | `RefrescoIT` |
| Un rol que exige MFA y no lo tiene inscrito obtiene un token **sin permisos** | `AutenticarUsuario`, `Rol.exigeMfa` | `ControlAccesoPorRolIT.moderador_sin_mfa_no_ejerce_permisos` |

**Hallazgos**

- **H-14** · **El MFA no tiene implementación.** Lo que existe es la consecuencia de no
  tenerlo (token sin permisos) y una columna `mfa_habilitado` que hoy solo se activa por
  SQL. No hay inscripción ni verificación de un segundo factor (por ejemplo TOTP). §3.4 y
  §6.2 lo exigen para accesos administrativos.
- **H-15** · No hay control contra **credenciales comprometidas** (comprobar la contraseña
  contra listas de filtraciones al registrarse o cambiarla), que §3.4 pide expresamente. La
  política actual es solo de longitud (8 a 100), alineada con NIST 800-63B en lo demás.
- **H-16** · No existe cambio ni recuperación de contraseña.

## A08:2025 — Software or Data Integrity Failures

**Controles existentes**

| Control | Dónde |
|---|---|
| Todo token se verifica por firma RS256 antes de usarse | `SeguridadConfig.oauth2ResourceServer` |
| Bloqueo optimista en las publicaciones: dos ediciones simultáneas no se pisan | `PublicacionEntity` (`@Version`) → `409 CONFLICTO_CONCURRENCIA` |
| Editar no reescribe el estado: no puede deshacer una ocultación concurrente | `PublicacionEntity` (`@DynamicUpdate`) |
| Toda transición de estado escribe su historial en la misma transacción | `RepositorioProyectosJpa.transicionarEstado` (regla R5) |
| Artefacto inmutable por commit y despliegue solo desde el pipeline | `ci.yml` |

**Hallazgos**

- **H-17** · `OcultarPublicacion` cambia el estado por JDBC sin incrementar `version`. Una
  edición que coincida exactamente con una ocultación se guarda sobre el proyecto ya oculto
  (documentado en HU-08). Ventana muy estrecha; impacto bajo.
- **H-18** · La imagen publicada no se firma (por ejemplo con cosign), y Render construye
  la suya propia desde el `Dockerfile` en lugar de desplegar la de ghcr.io.

## A09:2025 — Security Logging & Alerting Failures

**Controles existentes**

| Control | Dónde | Evidencia |
|---|---|---|
| Tabla `auditoria` con los eventos de seguridad: acceso denegado, inicio de sesión, fallo, bloqueo, rotación y **reuso** de refresco, cierre de sesión, intervención de moderación | `EventoAuditoria`, `RegistroAuditoriaJdbc`; migraciones V2 y V5 | `ControlAccesoPorRolIT`, `RefrescoIT`, `EditarProyectoIT`, `ArchivarProyectoIT` |
| La auditoría escribe en transacción propia: sobrevive al rollback de la petición | `RegistroAuditoriaJdbc` (`REQUIRES_NEW`) | — |
| Correlación por `traceId`: en el MDC de los logs, en la cabecera `X-Trace-Id`, en cada error y en la fila de auditoría | `FiltroTraceId` (ADR-005) | *Verificado en producción:* `x-trace-id` en todas las respuestas |
| Nunca se registran contraseñas, tokens ni la cookie | `ManejadorGlobalErrores`, `CookieRefresco`; §6.2 | — |

**Hallazgos**

- **H-19** · **No hay alertas.** Un `REFRESCO_REUSADO` —el evento que indica una cookie
  robada— queda registrado, pero nadie se entera hasta que consulte la tabla. Las alertas
  van con Prometheus/Grafana, en el **sprint 3** (§3.7).
- **H-20** · Si escribir la auditoría falla, `RegistroAuditoriaJdbc` lo registra con
  `log.error` y deja continuar la operación (falla abierto, a propósito). Sin alertas, ese
  `log.error` tampoco lo ve nadie.
- **H-21** · Los logs son de texto con el formato de Spring, no JSON (§3.3 admite *«otro
  formato consistente»*), y el patrón no imprime el `traceId` del MDC. Correlacionar un
  error del cliente con su línea de log exige hoy buscar por hora.
- **H-22** · `auditoria` no tiene política de retención ni purga.

## A10:2025 — Mishandling of Exceptional Conditions

**Controles existentes**

| Control | Dónde | Evidencia |
|---|---|---|
| Un único manejador traduce toda excepción al contrato de error; las no previstas salen como 500 genérico, sin traza ni detalles internos | `ManejadorGlobalErrores` (`@ExceptionHandler(Exception.class)`); ADR-005 | *Verificado en producción:* un JSON malformado responde `CUERPO_ILEGIBLE` sin exponer el parser |
| Solo el kernel construye respuestas de error | `FronterasModularesTest` regla 6 | — |
| Falla cerrado ante configuración incompleta: sin secretos o con el esquema desalineado, la aplicación no arranca | `application-prod.yml`, `ddl-auto: validate` | — |
| Una violación de restricción de la base se responde como 409 sin filtrar el nombre de la restricción ni los valores | `ManejadorGlobalErrores.integridad` | — |
| Entradas de navegación corruptas no rompen la petición: un cursor ilegible devuelve la primera página | `Cursor.decodificar` en proyectos y discusiones | — |
| Validación de estado antes que de forma: no se pide corregir campos de algo que no se puede modificar | `Proyecto.editar`, `Proyecto.archivar` | `EditarProyectoTest`, `ArchivarProyectoTest` |

**Hallazgos**

- **H-23** · La auditoría falla abierto (ver H-20). Es una decisión consciente —auditar no
  debe tumbar la petición del usuario—, pero exige la alerta de H-19 para ser aceptable.

---

## Hallazgos consolidados

### Plazos de remediación

§3.4 y §6.3 piden tiempos de remediación definidos. El equipo adopta estos:

| Severidad | Criterio | Plazo |
|---|---|---|
| **Crítica** | Explotable sin autenticación con impacto en confidencialidad o integridad de datos de usuarios | Antes de cualquier liberación a `main`. Bloquea el despliegue (§6.3) |
| **Alta** | Explotable con poco esfuerzo, o requisito de seguridad explícito de los lineamientos sin cumplir | Dentro del sprint en curso o el siguiente |
| **Media** | Requiere condiciones particulares o tiene impacto acotado | Planificada en el backlog, antes del cierre del proyecto |
| **Baja** | Defensa en profundidad o riesgo aceptado | Backlog, o aceptación documentada |

### Tabla de hallazgos

No se identificaron vulnerabilidades **críticas**. De las cuatro altas, H-04 se resolvió
el mismo sprint del análisis; quedan tres abiertas.

| ID | Categoría | Hallazgo | Severidad | Plazo / encaje |
|---|---|---|---|---|
| H-12 | A06 | Sin limitación de tasa; el bloqueo por intentos permite bloquear cuentas ajenas | **Alta** | Sprint 3, junto con el aseguramiento de tokens (§3.7) |
| H-07 | A03 | Sin SCA de dependencias | **Alta** | Coordinar con Calidad de Software: va en el pipeline (§7.2) |
| H-14 | A07 | MFA sin implementación | **Alta** | Requiere HU propia en el backlog |
| ~~H-04~~ | A02 | Roles de BD de mínimo privilegio sin aplicar | ~~Alta~~ | ✅ **Resuelto** el 14/10/2026 |
| H-15 | A07 | Sin control de credenciales comprometidas | Media | Requiere HU propia |
| H-01 | A01 | Sin suspensión de cuentas ni gestión de roles por API | Media | Requiere HU propia |
| H-19 | A09 | Sin alertas sobre eventos de seguridad | Media | Sprint 3 (Prometheus/Grafana, §3.7) |
| H-21 | A09 | Logs sin `traceId` impreso ni formato estructurado | Media | Sprint 3, con la observabilidad |
| H-08 | A03 | Acciones e imágenes base por etiqueta, no por digest | Media | Backlog técnico |
| H-13 | A06 | El registro permite comprobar si un correo existe | Baja | Decidir y registrar en un ADR |
| H-16 | A07 | Sin cambio ni recuperación de contraseña | Baja | Requiere HU propia |
| H-17 | A08 | Ocultar no incrementa `version` | Baja | Backlog técnico |
| H-18 | A08 | Imagen sin firmar; Render no despliega la de ghcr.io | Baja | Backlog técnico |
| H-20 / H-23 | A09 / A10 | La auditoría falla abierto sin alerta | Baja | Se resuelve con H-19 |
| H-05 | A02 | Sin `Content-Security-Policy` | Baja | Backlog técnico |
| H-09 | A03 | Sin SBOM | Baja | Con H-07 |
| H-22 | A09 | Auditoría sin retención | Baja | Sprint 3, con el análisis de volumen |
| ~~H-06~~ | A02 | `.env.example` con el pooler de transacción | ~~Baja~~ | ✅ **Resuelto** el 14/10/2026 |
| H-02 | A01 | SSRF cuando se integre la API de GitHub | — | Revisar al implementarla |
| H-10 | A04 | Par JWT efímero fuera de `prod` | — | Intencional; sin acción |
| H-03 | A02 | Swagger UI abierto en producción | — | **Riesgo aceptado**, ADR-007 |

Cada hallazgo con plazo debería tener su elemento en Azure DevOps, con responsable, para
cumplir la trazabilidad de §6.3.

## Pendiente para completar el ciclo de §6.1

Este documento cubre la **identificación**. Para cerrar el ciclo faltan, en el curso y
sprint que corresponda: DAST contra un entorno apto (no producción), revisión de código
seguro por pares en cada PR y una revisión de este análisis al cierre del sprint 3.
