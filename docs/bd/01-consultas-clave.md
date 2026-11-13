# Preguntas y consultas clave del negocio

Entregable de Sprint 1 (§3.7, Bases de Datos). §5.1 exige identificar "datos, actores,
entidades, relaciones, reglas, restricciones y requisitos no funcionales del caso de
negocio" **antes** de modelar.

El orden importa: estas preguntas se escribieron primero y el modelo se diseñó para
responderlas. No al revés. Cada índice de [04-indices.md](04-indices.md) existe porque
alguna de estas preguntas lo necesita; ningún índice se creó por intuición.

---

## Actores y sus preguntas

### Desarrollador

| # | Pregunta de negocio | Frecuencia | Presupuesto p95 |
|---|---|---|---|
| P01 | ¿Qué han publicado las personas que sigo y las tecnologías que me interesan, de lo más reciente a lo más antiguo? | Muy alta | 600 ms |
| P02 | ¿Cuál es el hilo completo de comentarios de esta publicación, con su anidamiento? | Alta | 500 ms |
| P03 | ¿Qué proyectos buscan colaboradores en las tecnologías que domino? | Media | 600 ms |
| P04 | ¿Qué publicaciones mencionan este término? | Media | 900 ms |
| P05 | ¿Cuáles son mis conversaciones, ordenadas por último mensaje, y cuántos no leídos tengo en cada una? | Muy alta | 400 ms |
| P06 | ¿Cómo se compone mi reputación y de dónde salió cada punto? | Baja | 500 ms |
| P07 | ¿Quién sigue a quién, y a cuántos sigo yo? | Media | 200 ms |
| P08 | ¿En qué estado están mis solicitudes de colaboración? | Media | 300 ms |

### Moderador

| # | Pregunta de negocio | Frecuencia | Presupuesto p95 |
|---|---|---|---|
| P09 | ¿Qué reportes están abiertos, ordenados por antigüedad, y cuáles puedo resolver yo (no soy el autor ni el reportante)? | Alta | 500 ms |
| P10 | ¿Cuántas publicaciones de este autor se han ocultado en los últimos 30 días? | Media | 200 ms |
| P11 | ¿Cuál es el historial completo de estados de esta publicación, con actor y motivo? | Media | 300 ms |
| P12 | ¿Qué usuarios están suspendidos ahora y hasta cuándo? | Baja | 200 ms |

### Administrador

| # | Pregunta de negocio | Frecuencia | Presupuesto p95 |
|---|---|---|---|
| P13 | ¿Cómo creció la comunidad en los últimos 90 días: altas, activos, publicaciones y mensajes por día? | Baja | 2 s |
| P14 | ¿Cuáles son las tecnologías más discutidas por trimestre y cómo cambió su posición? | Baja | 2 s |
| P15 | ¿Qué proporción de miembros registrados sigue activa a los 30 días de su alta? | Baja | 2 s |
| P16 | ¿Quién modificó este registro, cuándo y qué cambió exactamente? | Baja | 500 ms |
| P17 | ¿Qué tecnologías propuestas por usuarios están pendientes de aprobación? | Baja | 200 ms |

---

## Correspondencia con el caso

El caso enuncia seis capacidades. Esta tabla verifica que ninguna quedó sin preguntas ni
sin soporte en el modelo.

| Capacidad del caso | Preguntas | Tablas principales |
|---|---|---|
| Registro de usuarios con perfiles técnicos | P07, P15, P17 | `usuario`, `perfil`, `tecnologia`, `perfil_tecnologia`, `identidad_externa` |
| Publicación de proyectos y repositorios | P01, P03, P04 | `publicacion`, `publicacion_proyecto`, `repositorio`, `publicacion_tecnologia` |
| Creación de discusiones sobre tecnologías | P01, P04, P14 | `publicacion`, `publicacion_discusion`, `publicacion_tecnologia` |
| Sistema de comentarios e interacción | P02, P06 | `comentario`, `reaccion_publicacion`, `reaccion_comentario`, `evento_reputacion` |
| Mensajería privada | P05 | `conversacion`, `participante_conversacion`, `mensaje` |
| Reportes de actividad y crecimiento | P13, P14, P15 | `metricas_diarias` (consolidada por procedimiento almacenado) |

Los actores de moderación (P09 a P12) y la auditoría (P16) no salen del enunciado del
caso: se derivan de los lineamientos, que exigen un módulo transversal (§3.1), reglas de
negocio que no se reduzcan a CRUD (§3.1) y auditoría de cambios relevantes (§3.2).

---

## Qué exige cada pregunta del modelo

Esta es la traducción de las preguntas a decisiones de diseño. Es la justificación que
respalda tanto el modelo lógico como los índices.

| Pregunta | Exigencia sobre el modelo |
|---|---|
| P01 | Paginación determinista por keyset sobre `(publicado_en, id)`; índice **parcial** filtrado por `estado = 'PUBLICADO'`, porque el feed nunca mira los demás estados |
| P02 | Auto-referencia en `comentario` y consulta con `WITH RECURSIVE`; profundidad acotada por restricción `CHECK` para que el árbol no crezca sin control |
| P03 | Índice sobre `publicacion_tecnologia` y bandera `busca_colaboradores` en el subtipo de proyecto |
| P04 | Columna `tsvector` **generada** y persistida, con índice GIN. No se calcula el vector en tiempo de consulta |
| P05 | `DISTINCT ON (conversacion_id)` sobre índice compuesto `(conversacion_id, enviado_en DESC)`, más `ultimo_leido_en` por participante para contar no leídos sin tabla de estado por mensaje |
| P06 | Libro mayor `evento_reputacion`: la reputación es una suma reconstruible, no un contador que se sobrescribe |
| P09 | Restricción `CHECK` que obliga a que el reporte apunte a exactamente una publicación **o** a un comentario, nunca a ambos ni a ninguno |
| P10 | `historial_estado_publicacion` indexada por `(publicacion_id, creado_en)`, y consulta por autor y ventana temporal |
| P13, P14, P15 | Tabla `metricas_diarias` preconsolidada por `sp_consolidar_metricas_diarias`. **Los reportes no agregan en vivo sobre las tablas transaccionales** |
| P16 | Tabla `auditoria` con `jsonb` de antes y después, alimentada por triggers, con `trace_id` para unir con los logs de la aplicación |

> **P04 implementada (sprint 2, HU-09).** `GET /api/v1/proyectos?q=&tecnologia=` usa
> `busqueda_tsv @@ websearch_to_tsquery('spanish', ...)` sobre `ix_publicacion_busqueda`,
> y el filtro por tecnología un `EXISTS` sobre `ix_publicacion_tecnologia_inverso`
> (P03). Se aparta de la consulta de [05-consultas-no-triviales.sql](05-consultas-no-triviales.sql)
> en una cosa: **ordena por fecha de publicación, no por la puntuación combinada**. La
> puntuación incluye un término de frescura que depende de `now()`, así que cambia entre
> una página y la siguiente y no admite paginación por cursor estable; el criterio de
> aceptación pide una lista paginada. La puntuación sigue documentada para cuando se
> quiera un orden por relevancia sin paginar (por ejemplo, un «mejores resultados»).
> Ver `RepositorioProyectosJpa.buscar`.

La decisión más consecuente de esta lista es la de P13–P15: separar el camino de lectura
analítica del transaccional. Agregar en vivo sobre `publicacion`, `comentario` y `mensaje`
para un rango de 90 días funcionaría con datos de demostración y se caería con volumen
real, justo durante la sustentación.

---

## Estimación de volumen

§3.7 pide estimación de volumen en Sprint 2 y su actualización en Sprint 3. Se registra
aquí la línea base para poder contrastarla después.

| Tabla | Filas en demo | Proyección a 1 año | Crecimiento |
|---|---|---|---|
| `usuario` / `perfil` | 150 | 5 000 | Lineal, lento |
| `publicacion` | 500 | 40 000 | Lineal |
| `comentario` | 2 000 | 250 000 | Superlineal respecto a publicaciones |
| `reaccion_publicacion` | 5 000 | 600 000 | La tabla que más crece |
| `mensaje` | 1 500 | 400 000 | Alta, concentrada en pocos usuarios |
| `evento_reputacion` | 7 000 | 900 000 | Sigue a reacciones; candidata a particionar por fecha |
| `auditoria` | 3 000 | 500 000 | Candidata a retención de 12 meses |
| `metricas_diarias` | 90 | 365 | Constante, una fila por día |

Ninguna de estas cifras justifica particionamiento en el alcance del proyecto. §5.3 pide
evaluar particionamiento "únicamente cuando el volumen lo requiera", y se deja constancia
de que se evaluó: `evento_reputacion` y `auditoria` serían las primeras candidatas, por
fecha, si el sistema pasara a operación real.

> **Contraste con datos cargados (sprint 2).** Con estos volúmenes de proyección cargados
> ([`datos-representativos.sql`](datos-representativos.sql)), la base ocupa **240 MB**
> **sin** `reaccion_publicacion`, `evento_reputacion` ni `auditoria`, que aún no tienen
> funcionalidad que las escriba. Son las tres de mayor crecimiento: con ellas, el primer
> año se acercaría o superaría los **500 MB del plan gratuito de Supabase**. Detalle por
> tabla en [04-indices.md](04-indices.md#tamaño-en-disco). Para la actualización del
> análisis de volumen del sprint 3.
---

## Actualización del sprint 3

§3.7 pide en el sprint 3 la *«actualización del análisis de volumen»*. Desde el sprint 2
cambiaron dos cosas: `reaccion_publicacion` (HU-14), la mensajería (HU-17, HU-18) y
`auditoria` ya tienen funcionalidad que las escribe, y la base ganó triggers (V9, V10) y
un procedimiento (V11) cuyo costo hay que conocer.

**Cómo se midió.** PostgreSQL 16 local con V1–V11 y
[`datos-representativos.sql`](datos-representativos.sql) ampliado: ahora carga también
616 082 reacciones y 500 000 eventos de auditoría de un año, y calcula las métricas con el
procedimiento en vez de inventarlas. Detalle de cada medición en
[04-indices.md](04-indices.md#mediciones-del-sprint-3).

| Tabla | Proyección del sprint 1 | Filas cargadas | Tamaño medido | Escritor real | Observación |
|---|---|---|---|---|---|
| `auditoria` | 500 000 | 505 000 | **180 MB** (93 MB de índices) | Aplicación + triggers V10 | **La tabla más grande de la base.** ~45 % de las filas son `REFRESCO_ROTADO`, el camino normal de la sesión |
| `comentario` | 250 000 | 250 000 | 103 MB | HU-13 | +21 MB por `ux_comentario_id_publicacion`, el apoyo de la FK compuesta de V9 |
| `mensaje` | 400 000 | 400 000 | 81 MB | HU-17 | Sin cambios respecto del sprint 2 |
| `reaccion_publicacion` | 600 000 | 616 082 | 77 MB | HU-14 | Medida por primera vez |
| `publicacion` | 40 000 | 40 000 | 27 MB compactada | HU-07, HU-08, HU-11 | Llegó a 127 MB tras la carga masiva: tuplas muertas de las actualizaciones de contadores (ver abajo) |
| `evento_reputacion` | 900 000 | 0 | — | Ninguno | Sigue sin escritor (ninguna HU acredita reputación) |
| `metricas_diarias` | 365 | 365 | < 1 MB | V11 | Calculada por el procedimiento |
| **Base completa** | | | **535 MB** compactada | | Sprint 2: 240 MB sin reacciones ni auditoría |

### Conclusiones

1. **Un año de actividad ya supera los 500 MB del plan gratuito de Supabase** (535 MB, y
   eso sin `evento_reputacion`). En el sprint 2 era una proyección; ahora está medido.
2. **`auditoria` es la que decide.** Con 180 MB, es un tercio de la base, y su índice
   `ix_auditoria_registro` (56 MB) pesa más que tablas enteras. El hallazgo H-22 de
   [OWASP](../seguridad/owasp-top-10.md) (sin política de retención) deja de ser teórico.
   **Recomendación**, no implementada porque cambia qué evidencia se conserva y eso lo
   decide el equipo: purgar `REFRESCO_ROTADO` a los 90 días (es el camino normal, no un
   incidente) y conservar 12 meses el resto de eventos. Con la distribución de los datos de
   prueba (un supuesto: 45 % de renovaciones), solo lo primero reduciría la tabla cerca de
   un 40 % y la base volvería a caber en el plan gratuito. La proporción real hay que
   confirmarla en producción con la consulta P16 agrupada por `operacion`.
3. **Particionamiento: sigue sin justificarse.** Ninguna tabla llega al millón de filas y
   todas las consultas clave siguen muy por debajo de su presupuesto. La retención resuelve
   el tamaño con mucho menos costo operativo que particionar `auditoria` por fecha, que
   queda como la opción si la tabla pasara de unos pocos millones de filas.
4. **Los contadores generan tuplas muertas en `publicacion`.** Cada comentario o reacción
   actualiza la fila de su publicación; en una carga masiva eso dejó 26 000 tuplas muertas
   y la tabla en 127 MB (27 MB tras compactar). En operación normal el `autovacuum` las
   recoge, y el 77 % de esas actualizaciones fueron HOT (sin tocar índices), porque los
   contadores no están indexados. No requiere acción; si `publicacion` se hinchara en
   producción, la medida sería bajar su `fillfactor` para favorecer las actualizaciones HOT.
