# ADR-009: El reporte de actividad consolida las métricas bajo demanda

- **Estado:** Aceptada
- **Fecha:** 2026-11-14
- **Responsable:** _[completar nombre]_ — Arquitectura de Software
- **Historias relacionadas:** HU-19 (reporte de actividad y crecimiento)

## Contexto

`metricas_diarias` es la tabla de hechos que evita que los reportes agreguen en vivo sobre
`publicacion`, `comentario` y `mensaje` (P13–P15, `docs/bd/01-consultas-clave.md`).
`sp_consolidar_metricas_diarias` (V11) la llena, pero dejó abierto **quién lo invoca**: a mano,
con `pg_cron` o con una tarea de la aplicación.

HU-19 necesita datos al día cuando un administrador abre el reporte. El servicio corre en el
plan gratuito de Render, que lo **suspende tras 15 minutos sin tráfico**.

## Alternativas consideradas

### A. Tarea programada en la aplicación (`@Scheduled`, 00:10)
Es lo habitual, pero con el servicio dormido a esa hora la tarea no corre, y los días que se
salten quedan sin fila hasta que alguien los complete. Habría que escribir además la lógica de
recuperación de huecos, que es justo la parte difícil.

### B. `pg_cron` en Supabase
Corre aunque la aplicación duerma, pero ata el reporte a una extensión que no está en la base
de las pruebas (Testcontainers) ni se versiona con Flyway. Lo que no se puede probar en CI no
debería sostener un criterio de aceptación.

### C. Consolidar bajo demanda solo lo pendiente (elegida)
El reporte llama a `sp_consolidar_metricas_pendientes(desde, hasta)` (V13) antes de leer.

## Decisión

Un día está **pendiente** si no tiene fila en `metricas_diarias`, o si su fila se calculó
**antes de que el día terminara** (hora de Colombia). La segunda condición es la que impide que
el dato de «hoy» quede congelado a medias: lo calculado el 14 a las 10:00 se recalcula el 15 y
desde entonces ya no se toca. Cada día cerrado se calcula una sola vez; hoy, en cada consulta.

Los usuarios activos del rango se cuentan con `fn_usuarios_activos(desde, hasta)`: usuarios
**distintos**, porque sumar los activos de cada día cuenta dos veces a quien estuvo activo dos
días y la tabla por día no puede resolverlo.

El rango es obligatorio y de **92 días como máximo** (la ventana de 90 días de P13), para acotar
cuánto trabajo puede disparar una sola petición.

## Consecuencias

- Funciona igual con el servicio dormido o despierto, y se prueba entera con Testcontainers.
- Un `GET` escribe en `metricas_diarias`. Es una caché idempotente de datos derivados que el
  cliente no puede observar; por eso el caso de uso no es `readOnly` y el endpoint sigue siendo
  `GET`. Queda anotado en la documentación del endpoint.
- La primera consulta de un rango largo es la lenta. Hallazgo H-28 de OWASP: mitigado con el
  tope de 92 días y el acceso solo para ADMIN con MFA.
- La definición de «activo» está en dos sitios: V11 (por día) y V13 (por rango). Se deja así
  porque V11 ya está aplicada, y `ReporteActividadIT` las compara día por día para que no se
  separen sin que un test lo note.
- Si más adelante hay una tarea programada fiable, el reporte sigue funcionando: lo único que
  cambiaría es que casi nunca encontraría días pendientes.
