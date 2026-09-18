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