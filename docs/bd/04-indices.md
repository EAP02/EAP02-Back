# Índices: justificación y verificación

§5.3 es explícito: "crear índices a partir de patrones reales de consulta; evitar índices
redundantes y validar su costo de escritura". §3.2 añade: "definir índices para consultas
clave y **justificar su aporte**".

Por eso este documento existe y por eso está escrito **después** de
[01-consultas-clave.md](01-consultas-clave.md). Cada índice del modelo físico apunta a una
pregunta concreta. No hay ninguno creado por intuición ni "por si acaso".

---

## Inventario

| Índice | Tabla | Pregunta | Tipo | Por qué así |
|---|---|---|---|---|
| `ix_publicacion_feed` | `publicacion` | P01 | B-tree **parcial** | El feed jamás mira borradores, ocultos ni archivados. Filtrar por `estado = 'PUBLICADO'` deja fuera del índice esas filas: ocupa menos, cabe mejor en caché y no se actualiza cuando cambia una publicación que el feed no muestra |
| `ix_publicacion_busqueda` | `publicacion` | P04 | **GIN** | Búsqueda de texto completo sobre `busqueda_tsv`. GIN y no GiST porque el patrón es escritura moderada y lectura intensa, que es exactamente donde GIN gana |
| `ix_publicacion_autor_estado` | `publicacion` | P01, P10 | B-tree compuesto | "Publicaciones de este autor en este estado, recientes primero". El orden `(autor_id, estado, creado_en DESC)` sirve también a la consulta por solo `autor_id` gracias al prefijo izquierdo |
| `ix_publicacion_tecnologia_inverso` | `publicacion_tecnologia` | P01, P03 | B-tree compuesto | La clave primaria es `(publicacion_id, tecnologia_id)` y resuelve "tecnologías de esta publicación". Este índice resuelve el **sentido inverso**, "publicaciones con esta tecnología", que es el que usa el feed. No es redundante: es el otro sentido |
| `ix_proyecto_busca_colaboradores` | `publicacion_proyecto` | P03 | B-tree **parcial** | Los proyectos que buscan colaboradores son minoría permanente. Un índice parcial sobre esa minoría es una fracción del tamaño del índice completo |
| `ix_comentario_publicacion_padre` | `comentario` | P02 | B-tree compuesto | Es el ancla del CTE recursivo: la parte no recursiva busca por `(publicacion_id, comentario_padre_id IS NULL)` y cada iteración busca hijos por `comentario_padre_id` |
| `ix_mensaje_conversacion` | `mensaje` | P05 | B-tree compuesto | `(conversacion_id, enviado_en DESC)` sirve a las dos operaciones de la bandeja: el `DISTINCT ON` del último mensaje y el `COUNT` de no leídos, ambas por recorrido ordenado del índice |
| `ix_participante_usuario` | `participante_conversacion` | P05 | B-tree **parcial** | "Mis conversaciones", excluyendo las abandonadas, que no vuelven a aparecer |
| `ux_conversacion_directa` | `conversacion` | P05 | **Único parcial** | Impone que dos usuarios no puedan abrir dos conversaciones directas entre sí. Parcial porque en las conversaciones de grupo la columna es nula, y un índice único ordinario permitiría **una sola** fila nula en algunos motores. Aquí la restricción se aplica solo donde tiene sentido |
| `ix_evento_reputacion_usuario` | `evento_reputacion` | P06 | B-tree compuesto | Libro mayor de un usuario en orden cronológico inverso |
| `ix_seguimiento_seguido` | `seguimiento` | P07 | B-tree compuesto | La clave primaria `(seguidor_id, seguido_id)` responde "a quién sigo". Este responde "quién me sigue". Otra vez: el sentido inverso, no un duplicado |
| `ix_colaboracion_usuario` | `colaboracion` | P08 | B-tree compuesto | "Mis solicitudes por estado" |
| `ix_reporte_pendiente` | `reporte_moderacion` | P09 | B-tree **parcial** | La cola de moderación solo mira lo pendiente. Un reporte resuelto sale del índice para siempre, así que el índice tiende a un tamaño estable aunque la tabla crezca sin límite |
| `ix_historial_publicacion` | `historial_estado_publicacion` | P11 | B-tree compuesto | Historial de una publicación, más reciente primero |
| `ix_suspension_vigente` | `suspension` | P12 | B-tree **parcial** | Solo las suspensiones no levantadas |
| `ix_auditoria_registro` | `auditoria` | P16 | B-tree compuesto | "Quién tocó este registro": `(tabla, registro_id, creado_en DESC)` |
| `ix_auditoria_trace` | `auditoria` | P16 | B-tree **parcial** | Une auditoría con los logs por `traceId`. Parcial porque las filas generadas fuera de una petición HTTP no lo tienen |
| `ix_tecnologia_pendiente` | `tecnologia` | P17 | B-tree **parcial** | Cola de aprobación del catálogo |
| `ix_token_familia` | `token_refresco` | — | B-tree **parcial** | Revocación en cascada de una familia ante detección de reuso (ADR-004) |
| `ix_token_usuario_vigente` | `token_refresco` | — | B-tree **parcial** | Sesiones activas de un usuario y limpieza de expirados |

**Diez de los veinte índices son parciales.** No es casualidad: casi todas las consultas de
este dominio filtran por un estado, y en todas ellas las filas que no cumplen el filtro
nunca se consultan. El índice parcial es el patrón dominante del modelo.

---

## Índices que existen sin declararse

Es tan importante saber qué no hay que crear como qué sí. PostgreSQL crea un índice único
automáticamente por cada `PRIMARY KEY` y cada `UNIQUE`. Declarar otro índice sobre las
mismas columnas en el mismo orden sería puro costo de escritura sin ninguna lectura
beneficiada.

Quedan cubiertos sin declaración explícita:

- Toda búsqueda por clave primaria (`publicacion.id`, `usuario.id`, …).
- El prefijo izquierdo de cada clave primaria compuesta: `usuario_rol(usuario_id)`,
  `perfil_tecnologia(usuario_id)`, `seguimiento(seguidor_id)`,
  `publicacion_tecnologia(publicacion_id)`, `reaccion_publicacion(usuario_id)`,
  `participante_conversacion(conversacion_id)`.
- `repositorio.url` y `token_refresco.token_hash`, por su restricción `UNIQUE`.

Aparte quedan `ux_usuario_correo` y `ux_usuario_nombre_usuario`, que sí se declaran
explícitamente en el modelo físico pero no figuran en el inventario de arriba porque no
responden a una pregunta de negocio: son **restricciones de unicidad insensibles a
mayúsculas**, sobre `lower(correo)` y `lower(nombre_usuario)`. Una restricción `UNIQUE`
ordinaria dejaría registrarse a `Ana@x.com` y a `ana@x.com` como dos cuentas distintas. Que
además sirvan para buscar al autenticar es un efecto secundario, no su propósito.

Con esos dos, `V1__baseline.sql` declara **22 índices**, de los cuales 10 son parciales.

### Índices posteriores a la V1

El inventario de arriba es el de la migración inicial. Tres migraciones lo modifican:

| Índice | Migración | Tipo | Por qué |
|---|---|---|---|
| `ix_auditoria_seguridad` | V2, recreado en V5 | Parcial sobre `(operacion, creado_en DESC)` | Sirve la cola de «quién está tocando puertas cerradas». Parcial porque los eventos de seguridad son una minoría permanente frente a los cambios de datos, y solo se consultan entre ellos |
| `ix_perfil_habilidad_usuario` | V3, **eliminado en V4** | — | Desapareció junto con su tabla |
| `ix_perfil_tecnologia_inverso` | V4 | Total sobre `(tecnologia_id, usuario_id)` | No es redundante con la clave primaria: **es el otro sentido**. La PK `(usuario_id, tecnologia_id)` resuelve «qué declara este usuario»; este resuelve «quién sabe esta tecnología», que es la consulta detrás de buscar colaboradores por stack (P03) |

**V5 recrea `ix_auditoria_seguridad` en vez de añadir otro.** El `WHERE` de un índice
parcial enumera los valores que sirve, así que un evento nuevo que no figure ahí queda
fuera del índice y su consulta vuelve a recorrer la tabla entera. Se le suma
`REFRESCO_REUSADO`.

`REFRESCO_ROTADO` se deja **fuera** a propósito: es el camino normal y ocurre cada quince
minutos por sesión activa. Incluirlo convertiría un índice parcial en uno casi total y
encarecería la escritura sin que nadie consulte esa cola.

Neto tras las cinco migraciones: **24 índices declarados, 11 de ellos parciales.**
`EsquemaIT` comprueba el recuento de parciales en cada ejecución, así que un índice
parcial nuevo obliga a actualizar esa constante.

### Una omisión deliberada

PostgreSQL **no** indexa automáticamente las columnas de clave foránea. Aquí se han
indexado solo aquellas que participan en una consulta real. Las que no aparecen en ninguna
pregunta (`usuario_rol.asignado_por`, `colaboracion.resuelto_por`,
`historial_estado_publicacion.actor_id`) se dejan sin índice a propósito.

El precio de esa omisión es conocido y acotado: un `DELETE` sobre `usuario` obliga a
recorrer esas tablas para verificar la integridad referencial. Es aceptable porque las
cuentas no se borran, se desactivan (ver el modelo lógico, decisiones transversales).
Si la política de retención cambiara, estos índices serían lo primero a añadir.

---

## Costo de escritura

§5.3 pide "validar su costo de escritura", no solo su beneficio de lectura. Cada índice se
actualiza en cada `INSERT`, y en cada `UPDATE` que toque sus columnas.

| Tabla | Índices que la tocan | Perfil de escritura | Evaluación |
|---|---|---|---|
| `publicacion` | 3 + PK | Baja frecuencia, alta lectura | Sobra margen |
| `reaccion_publicacion` | Solo PK | **La tabla que más crece** | Correcto no añadir ninguno |
| `mensaje` | 1 + PK | Alta frecuencia | Un solo índice, el mínimo para la bandeja |
| `comentario` | 1 + PK | Media | Aceptable |
| `evento_reputacion` | 1 + PK | Alta, ligada a reacciones | Aceptable; es append-only, sin coste de `UPDATE` |
| `auditoria` | 2 + PK | Alta, solo `INSERT` | Aceptable; candidata a retención de 12 meses |

La decisión consciente aquí es **no indexar `reaccion_publicacion` más allá de su clave
primaria**. Es la tabla de mayor crecimiento proyectado (600 000 filas al año) y su única
consulta frecuente —"¿reaccioné yo a esto?"— se responde por la clave primaria
`(usuario_id, publicacion_id)`. El conteo agregado no se calcula sobre esta tabla: vive
denormalizado en `publicacion.contador_reacciones`.

---

## Verificación

Un índice justificado en un documento pero no usado por el planificador es un índice que
sobra. La comprobación es obligatoria antes de cerrar cada sprint.

### 1. Confirmar que el plan lo usa

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.titulo, p.publicado_en
FROM publicacion p
WHERE p.estado = 'PUBLICADO'
ORDER BY p.publicado_en DESC, p.id DESC
LIMIT 20;
```

Se espera `Index Scan using ix_publicacion_feed`. Si aparece `Seq Scan`, o el índice no
aplica o no hay volumen suficiente para que el planificador lo prefiera: poblar con datos
representativos antes de concluir nada.

### 2. Detectar índices que nadie usa

```sql
SELECT relname AS tabla,
       indexrelname AS indice,
       idx_scan AS veces_usado,
       pg_size_pretty(pg_relation_size(indexrelid)) AS tamano
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC;
```

Todo índice con `idx_scan = 0` tras un sprint de uso real se elimina o se justifica por
escrito. Es el criterio de "evitar índices redundantes" convertido en procedimiento.

### 3. Detectar índices que faltan

```sql
SELECT relname AS tabla,
       seq_scan, seq_tup_read,
       idx_scan,
       seq_tup_read / NULLIF(seq_scan, 0) AS filas_por_recorrido
FROM pg_stat_user_tables
WHERE schemaname = 'public' AND seq_scan > 0
ORDER BY seq_tup_read DESC;
```

Una tabla con muchos recorridos secuenciales y muchas filas leídas por recorrido está
pidiendo un índice. La cifra dice dónde mirar; la pregunta de negocio dice cuál crear.

### 4. Vigilar consultas lentas

En el entorno de integración, con `log_min_duration_statement = 500ms`. Toda consulta
registrada se analiza con `EXPLAIN ANALYZE` **antes** de considerar caché. La caché no
corrige un plan de ejecución malo, solo lo esconde.

---

## Registro de mediciones

Medido el 14 de octubre de 2026 (sprint 2), con datos representativos y no con los de
demostración.

### Cómo se midió

| | |
|---|---|
| Motor | PostgreSQL 16.15 local, imagen `postgres:16-alpine` (la misma de `docker-compose.yml`), con las migraciones V1 a V5 |
| Datos | [`datos-representativos.sql`](datos-representativos.sql): los volúmenes de la **proyección a un año** de [01-consultas-clave.md](01-consultas-clave.md), con distribuciones sesgadas (pocos autores publican mucho, pocas publicaciones concentran los comentarios, pocas conversaciones concentran los mensajes) |
| Volumen cargado | 5 000 usuarios · 40 000 publicaciones · 110 094 etiquetas · 250 000 comentarios · 400 000 mensajes · 54 812 seguimientos · 3 000 reportes · 365 días de métricas |
| Método | [`medicion-planes.sql`](medicion-planes.sql): cada consulta 30 veces con `EXPLAIN (ANALYZE, BUFFERS)`, cada vez con un parámetro distinto tomado de los datos; p50, p95 y máximo del tiempo de ejecución, y de cada plan los índices usados y las tablas recorridas en secuencial |
| Límite | Mide el tiempo **en la base**, con caché caliente. No incluye red, pool de conexiones ni serialización. Supabase tiene otra máquina y otra red: los valores absolutos no se trasladan, la forma de los planes sí |

Para repetirlo:

```bash
docker compose up -d   # o podman
# aplicar V1..V5 (las aplica Flyway al arrancar la API una vez) y después:
docker exec -i devnet-db psql -U postgres -d devnet < docs/bd/datos-representativos.sql
docker exec -i devnet-db psql -U postgres -d devnet < docs/bd/medicion-planes.sql
```

### Consultas de diseño

| Consulta | Índice esperado | Plan observado | p95 | Presupuesto | Veredicto |
|---|---|---|---|---|---|
| P01 feed | `ix_publicacion_feed` | `ix_publicacion_feed` + `ix_publicacion_tecnologia_inverso` + claves primarias. Secuencial solo sobre `tecnologia` (20 filas) | 12,53 ms | 600 ms | ✅ |
| P02 hilo | `ix_comentario_publicacion_padre` | `ix_comentario_publicacion_padre` en ambos términos del CTE recursivo, **tras corregir la consulta** (ver abajo) | 0,31 ms | 500 ms | ✅ |
| P04 búsqueda con relevancia | `ix_publicacion_busqueda` | `ix_publicacion_busqueda` (GIN). Secuencial sobre `perfil` para la puntuación por reputación | 6,80 ms | 900 ms | ✅ |
| P05 bandeja | `ix_mensaje_conversacion` | `ix_mensaje_conversacion` + `ix_participante_usuario` | 0,52 ms | 400 ms | ✅ |
| P09 cola | `ix_reporte_pendiente` | **No lo usa:** secuencial sobre `reporte_moderacion` (ver abajo) | 1,31 ms | 500 ms | ✅ con observación |
| P13 crecimiento | lectura de `metricas_diarias` | Secuencial sobre `metricas_diarias` (365 filas): es lo esperado | 0,23 ms | 2 s | ✅ |

### Consultas implementadas en el sprint 2

Copiadas literalmente de los repositorios de la aplicación.

| Consulta | Dónde | Plan observado | p95 |
|---|---|---|---|
| HU-07 proyectos de un autor | `PublicacionJpaRepository.deAutorPaginado` | `ix_publicacion_autor_estado` | 0,06 ms |
| HU-09 búsqueda por texto | `RepositorioProyectosJpa.buscar` | `ix_publicacion_feed` con términos frecuentes; `ix_publicacion_busqueda` con términos raros (ver abajo) | 0,13 ms |
| HU-09 filtro por tecnología | `RepositorioProyectosJpa.buscar` | `ix_publicacion_feed` + `ix_publicacion_tecnologia_inverso` | 0,49 ms |
| HU-09 texto + tecnología | `RepositorioProyectosJpa.buscar` | `ix_publicacion_feed` + `ix_publicacion_tecnologia_inverso` | 1,33 ms |
| HU-11 participantes del hilo | `RepositorioDiscusionesJdbc.participantes` | `ix_comentario_publicacion_padre` | 0,10 ms |
| HU-12 discusiones por etiqueta | `RepositorioDiscusionesJdbc.listar` | `ix_publicacion_feed` + `ix_publicacion_tecnologia_inverso`; hash sobre `publicacion_discusion` | 0,89 ms |

Ninguna recorre en secuencial una tabla grande. Todas quedan dos o más órdenes de magnitud
por debajo de su presupuesto.

### Hallazgos

**P02: el paso recursivo recorría la tabla entera.** La versión original unía
`comentario h JOIN hilo ON h.comentario_padre_id = padre.id` sin filtrar por publicación.
`ix_comentario_publicacion_padre` empieza por `publicacion_id`, así que ese paso no podía
usarlo y hacía un `Seq Scan` de los 250 000 comentarios **en cada nivel del árbol**: su costo
crecía con el tamaño de toda la tabla, no con el del hilo. Se añadió
`h.publicacion_id = :publicacion` al paso recursivo, una condición que no cambia el
resultado porque una respuesta siempre pertenece a la publicación de su padre.

| P02 | Plan del paso recursivo | Peor caso (hilo de 17 520 comentarios) | p95 (30 hilos) |
|---|---|---|---|
| Antes | `Seq Scan on comentario` (250 000 filas × 2 niveles) | 118 ms | 62,6 ms |
| Después | `Bitmap Index Scan on ix_comentario_publicacion_padre` | 18 ms | 0,31 ms |

Se resolvió **cambiando la consulta y no creando un índice**: el índice existente ya servía,
solo había que darle la condición que necesita. Sin costo de escritura nuevo.

**HU-09: el planificador elige el índice según la selectividad del término.** Con un
término frecuente (cada palabra del vocabulario de prueba aparece en un 8 % de los
proyectos) recorre `ix_publicacion_feed`, que ya está ordenado, y se detiene al reunir 21
resultados. Con un término raro o inexistente usa el GIN `ix_publicacion_busqueda`. Los dos
índices se justifican: cada uno gana en un extremo.

**P09: `ix_reporte_pendiente` no se usa.** La consulta ordena por
`es_apelacion DESC, creado_en`, y el índice solo cubre `creado_en`; con 3 000 reportes y 300
pendientes, el planificador prefiere leer la tabla y ordenar (1,3 ms). Se **mantiene**: es
parcial, y por eso pequeño y barato de escribir, y servirá cuando la tabla crezca. Si en el
sprint 3 la cola de moderación se implementa y la tabla supera unas decenas de miles de
filas, se evaluará cambiarlo por `(es_apelacion DESC, creado_en) WHERE estado IN (...)`.

### Tamaño en disco

| Tabla | Filas | Total | De ello, índices |
|---|---|---|---|
| `mensaje` | 400 000 | 81 MB | 37 MB |
| `comentario` | 250 000 | 62 MB | 26 MB |
| `publicacion` | 40 000 | 32 MB | 13 MB |
| `publicacion_tecnologia` | 110 094 | 16 MB | 11 MB |
| `seguimiento` | 54 812 | 10 MB | 7 MB |
| **Base completa** | | **240 MB** | |

Los datos de prueba **no** poblaron `reaccion_publicacion`, `evento_reputacion` ni
`auditoria`, porque ninguna funcionalidad las escribe todavía, y son justo las tres de mayor
crecimiento proyectado (600 000, 900 000 y 500 000 filas al año). Con ellas, la base
proyectada a un año se acercaría o superaría los **500 MB del plan gratuito de Supabase**.
Es un dato para la actualización del análisis de volumen del sprint 3 (§3.7): la retención de
`auditoria` y el particionamiento de `evento_reputacion`, que hoy figuran como «evaluados y
no necesarios», pasan a ser candidatos reales en cuanto esas tablas empiecen a llenarse.
