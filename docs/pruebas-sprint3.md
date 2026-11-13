# Pruebas de Sprint 3 — HU-21 Logging estructurado con traceId

Guion para comprobar contra el despliegue real que **HU-21 · AB#122** cumple sus criterios
de aceptación. A diferencia de las demás historias, el resultado no se ve solo en Swagger:
se ve en los **logs de Render**, y Swagger sirve para provocarlos.

- Swagger UI: **<https://eap02-back.onrender.com/swagger-ui.html>**
- Logs: panel de Render → servicio `eap02-back` → pestaña **Logs** (tiene buscador).
- Duración: unos 10 minutos.
- Cómo se usa Swagger UI y cómo autorizarse con el token:
  [`pruebas-swagger.md`](pruebas-swagger.md#cómo-se-usa-swagger-ui).

> **Historia.** Como equipo de desarrollo, quiero logs estructurados con un identificador
> por petición, para encontrar y correlacionar errores.

---

## Antes de empezar

1. Despierta el servicio: abre `https://eap02-back.onrender.com/actuator/health` y espera
   `{"status":"UP"}` (el plan gratuito tarda más de un minuto en arrancar).
2. Ten a mano una cuenta `DESARROLLADOR` (créala con `POST /api/v1/auth/registro` si hace
   falta).

---

## Criterio 1 · Los logs del backend se generan en formato JSON

1. En Swagger, ejecuta `GET /api/v1/tecnologias`.
2. En la pestaña **Logs** de Render, mira las últimas líneas.

**Esperado:** cada línea es un objeto JSON en una sola línea, por ejemplo:

```json
{"@timestamp":"2026-11-13T19:05:42.486Z","@version":"1","message":"Peticion atendida","logger_name":"com.codefactory.devnet.shared.api.FiltroTraceId","thread_name":"http-nio-8080-exec-3","level":"INFO","level_value":20000,"traceId":"0a1b2c3d4e5f6a7b","metodo":"GET","ruta":"/api/v1/tecnologias","estadoHttp":200,"duracionMs":12}
```

`traceId`, `metodo`, `ruta`, `estadoHttp` y `duracionMs` son campos propios, no texto
dentro del mensaje: se pueden filtrar sin expresiones regulares.

## Criterio 2 · Cada petición tiene un traceId único y todos sus logs lo comparten

1. Ejecuta `GET /api/v1/tecnologias` dos veces y anota la cabecera `x-trace-id` de cada
   respuesta (sección *Response headers* de Swagger).
2. **Esperado:** los dos valores son distintos.
3. Busca cada valor en Render: cada uno lleva a la línea de su propia petición.

## Criterio 3 · Las respuestas de error incluyen el traceId para poder rastrearlas

1. Inicia sesión con el desarrollador (`POST /api/v1/auth/inicio-sesion`) y autorízate con
   el `token`.
2. Ejecuta `POST /api/v1/proyectos/{id}/ocultamiento` con cualquier UUID y el cuerpo
   `{"motivo": "Contenido duplicado de otro proyecto ya publicado en la plataforma."}`.
   Un desarrollador no puede moderar.
3. **Esperado en Swagger:** `403` con `errorCode: ACCESO_DENEGADO` y un `traceId` en el
   cuerpo, igual al de la cabecera `x-trace-id`.
4. Busca ese `traceId` en Render. **Esperado:** dos líneas de la misma petición:
   - `"level":"WARN"`, `"message":"Acceso denegado en POST /api/v1/proyectos/…"`;
   - `"message":"Peticion atendida"` con `"estadoHttp":403`.

   El mismo valor también está en la base: `SELECT * FROM auditoria WHERE trace_id = '<valor>';`
   devuelve el evento `ACCESO_DENEGADO`. Respuesta, log y auditoría se unen por un solo dato.

## Criterio 4 · Los logs no contienen contraseñas, tokens ni datos personales innecesarios

1. Haz un inicio de sesión **fallido** con tu correo y una clave inventada que puedas
   reconocer, por ejemplo `ClaveQueNoDebeVerse-2026!`.
2. Haz uno correcto y copia el `token`.
3. Busca en Render, uno por uno: la clave inventada, tu clave real, el correo, un fragmento
   del `token` y el valor de la cookie `devnet_refresco`.

**Esperado:** ninguna búsqueda encuentra nada. El inicio de sesión fallido aparece como
`"message":"Regla de negocio incumplida: AUTH_CREDENCIALES_INVALIDAS en POST /api/v1/auth/inicio-sesion"`,
sin datos de quién lo intentó.

4. Ejecuta `GET /api/v1/proyectos?q=algoPrivado` y busca `algoPrivado` en Render.
   **Esperado:** sin resultados; la línea de cierre muestra `"ruta":"/api/v1/proyectos"`, sin
   la query string.

> El único identificador que aparece es el UUID del actor en "Acceso denegado", que ya
> estaba antes de HU-21 y no es un dato personal directo. `usuarioId` no se añade a cada
> línea a propósito: ver la enmienda de
> [ADR-005](adr/ADR-005-contrato-errores-traceid.md#enmienda-2026-11-13-hu-21-logging-estructurado).

---

## Pruebas automáticas

| Historia | Unitarias | Integración | Qué cubren |
|---|---|---|---|
| HU-21 | `FiltroTraceIdTest` (4) | `LoggingEstructuradoIT` (5) | Toda la salida de logging es JSON y la línea de cierre trae los campos de ADR-005; dos peticiones tienen traceId distintos; las líneas de una petición fallida (aviso del manejador y cierre) comparten el traceId de su respuesta de error; ni contraseñas (correcta y errónea), ni token de acceso, ni cookie de refresco, ni correo aparecen en la salida; la ruta no incluye la query string; un traceId entrante válido se respeta y uno con saltos de línea se descarta; el MDC queda limpio aunque la petición falle |

`LoggingEstructuradoIT` lee la salida real de la consola (`OutputCaptureExtension`) con la
configuración de `application.yml`, la misma que produce los logs de Render.

## Trazabilidad

| Criterio | Dónde se cumple | Prueba |
|---|---|---|
| 1. Logs en JSON | `application.yml` → `logging.structured.format.console: logstash` | `LoggingEstructuradoIT.los_logs_salen_en_json` |
| 2. traceId único y compartido | `FiltroTraceId` (MDC por petición, limpieza en `finally`, línea de cierre) | `LoggingEstructuradoIT.cada_peticion_tiene_su_trace_id`, `…la_respuesta_de_error_y_sus_logs_comparten_trace_id`, `FiltroTraceIdTest` |
| 3. Errores con traceId | `ManejadorGlobalErrores` → `RespuestaError.traceId` (ADR-005, ya existente) | `LoggingEstructuradoIT.la_respuesta_de_error_y_sus_logs_comparten_trace_id` |
| 4. Sin secretos ni datos personales | `FiltroTraceId` (sin query string, sin cabeceras ni cuerpo), `application.yml` → `logServerErrorDetail: false`, `ManejadorGlobalErrores` (ya existente) | `LoggingEstructuradoIT.el_log_no_contiene_secretos_ni_datos_personales`, `…la_ruta_no_incluye_la_query_string` |

## Logs en texto para desarrollo local

Si prefieres el formato de texto de siempre en tu máquina, sin cambiar el repositorio:

```bash
LOGGING_STRUCTURED_FORMAT_CONSOLE= ./mvnw spring-boot:run
```

Con la variable vacía Spring vuelve al patrón de texto. `LoggingEstructuradoIT` fallaría
en esas condiciones, que es justo lo que debe hacer: comprueba el formato de producción.
