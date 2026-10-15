-- ============================================================================
--  DevNet - Medicion de planes de ejecucion
--
--  Lineamientos §5.3: "medir planes de ejecucion" y "crear indices a partir de
--  patrones reales de consulta". Llena el "Registro de mediciones" de
--  docs/bd/04-indices.md.
--
--  Requisitos: una base con las migraciones V1 a V5 y los datos de
--  datos-representativos.sql. NUNCA contra produccion: EXPLAIN ANALYZE ejecuta de
--  verdad cada consulta.
--
--      podman/docker exec -i <contenedor> psql -U postgres -d devnet < medicion-planes.sql
--
--  Como mide: cada consulta se ejecuta 30 veces con EXPLAIN (ANALYZE, FORMAT JSON),
--  cada vez con un parametro distinto tomado de los propios datos (otro usuario,
--  otra publicacion, otro termino). De cada ejecucion se guarda el tiempo y, del
--  plan, los indices usados y las tablas recorridas de forma secuencial. Los
--  valores se incrustan como literales, igual que hace PostgreSQL con un plan
--  personalizado para una consulta preparada.
--
--  Las consultas son las de 05-consultas-no-triviales.sql (P01-P13) y las que el
--  codigo ejecuta de verdad desde el sprint 2 (HU-07 a HU-12), copiadas de sus
--  repositorios.
-- ============================================================================

\set ON_ERROR_STOP on
SELECT setseed(0.42);

CREATE TEMP TABLE medicion (
    consulta     text,
    ejecuciones  int,
    p50_ms       numeric,
    p95_ms       numeric,
    max_ms       numeric,
    filas_max    bigint,
    indices      text,
    secuenciales text
);

-- plantilla: SQL con %L donde va cada parametro (format()).
-- parametros: consulta que devuelve una fila text[] por ejecucion.
CREATE FUNCTION pg_temp.medir(nombre text, plantilla text, parametros text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    fila     record;
    plan     jsonb;
    tiempos  numeric[] := '{}';
    indices  text[] := '{}';
    seq      text[] := '{}';
    filas    bigint := 0;
BEGIN
    FOR fila IN EXECUTE parametros LOOP
        EXECUTE 'EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) '
                || format(plantilla, VARIADIC fila.p) INTO plan;
        tiempos := tiempos || (plan -> 0 ->> 'Execution Time')::numeric;
        filas   := greatest(filas, (plan -> 0 -> 'Plan' ->> 'Actual Rows')::bigint);
        indices := indices || ARRAY(SELECT jsonb_path_query(plan, 'strict $.**."Index Name"') #>> '{}');
        seq     := seq || ARRAY(
            SELECT n ->> 'Relation Name'
            FROM jsonb_path_query(plan, 'strict $.** ? (@."Node Type" == "Seq Scan")') AS n);
    END LOOP;

    INSERT INTO medicion
    SELECT nombre,
           cardinality(tiempos),
           round(percentile_cont(0.50) WITHIN GROUP (ORDER BY t)::numeric, 2),
           round(percentile_cont(0.95) WITHIN GROUP (ORDER BY t)::numeric, 2),
           round(max(t), 2),
           filas,
           (SELECT string_agg(DISTINCT i, ', ') FROM unnest(indices) i),
           (SELECT string_agg(DISTINCT s, ', ') FROM unnest(seq) s)
    FROM unnest(tiempos) t;
END $$;

-- Calentar la cache: la primera lectura de cada tabla desde disco no es lo que
-- vive un usuario en un sistema en marcha.
SELECT count(*) FROM publicacion; SELECT count(*) FROM comentario; SELECT count(*) FROM mensaje;

-- ============================================================================
-- Consultas de diseno (05-consultas-no-triviales.sql)
-- ============================================================================

-- P01 feed personalizado, primera pagina
SELECT pg_temp.medir('P01 feed', $q$
WITH intereses AS (SELECT tecnologia_id FROM perfil_tecnologia WHERE usuario_id = %1$L),
     seguidos  AS (SELECT seguido_id FROM seguimiento WHERE seguidor_id = %1$L)
SELECT p.id, p.titulo, p.publicado_en, au.nombre_completo,
       ARRAY(SELECT t.nombre FROM publicacion_tecnologia pt JOIN tecnologia t ON t.id = pt.tecnologia_id
             WHERE pt.publicacion_id = p.id ORDER BY t.nombre) AS tecnologias
FROM publicacion p
JOIN perfil au ON au.usuario_id = p.autor_id
WHERE p.estado = 'PUBLICADO'
  AND (p.autor_id IN (SELECT seguido_id FROM seguidos)
       OR EXISTS (SELECT 1 FROM publicacion_tecnologia pt JOIN intereses i ON i.tecnologia_id = pt.tecnologia_id
                  WHERE pt.publicacion_id = p.id))
ORDER BY p.publicado_en DESC, p.id DESC
LIMIT 20
$q$, $p$ SELECT ARRAY[seguidor_id::text] AS p FROM (SELECT DISTINCT seguidor_id FROM seguimiento) s ORDER BY random() LIMIT 30 $p$);

-- P02 hilo de comentarios anidados
SELECT pg_temp.medir('P02 hilo', $q$
WITH RECURSIVE hilo AS (
    SELECT c.id, c.comentario_padre_id, c.autor_id, c.contenido, c.creado_en, 0 AS nivel,
           ARRAY[to_char(c.creado_en, 'YYYYMMDDHH24MISSUS') || ':' || c.id::text] AS ruta
    FROM comentario c
    WHERE c.publicacion_id = %1$L AND c.comentario_padre_id IS NULL AND c.estado <> 'ELIMINADO'
  UNION ALL
    SELECT h.id, h.comentario_padre_id, h.autor_id, h.contenido, h.creado_en, padre.nivel + 1,
           padre.ruta || (to_char(h.creado_en, 'YYYYMMDDHH24MISSUS') || ':' || h.id::text)
    FROM comentario h JOIN hilo padre ON h.comentario_padre_id = padre.id
    WHERE h.publicacion_id = %1$L AND h.estado <> 'ELIMINADO'
)
SELECT hilo.id, hilo.nivel, hilo.contenido, pe.nombre_completo
FROM hilo JOIN perfil pe ON pe.usuario_id = hilo.autor_id
ORDER BY hilo.ruta
$q$, $p$ SELECT ARRAY[publicacion_id::text] AS p FROM (SELECT DISTINCT publicacion_id FROM comentario) s ORDER BY random() LIMIT 30 $p$);

-- P04 busqueda por texto completo con relevancia (diseno; ver HU-09 abajo)
SELECT pg_temp.medir('P04 busqueda con relevancia', $q$
SELECT p.id, p.titulo,
       ts_rank(p.busqueda_tsv, websearch_to_tsquery('spanish', %1$L)) * 1.0
         + least(au.reputacion, 500) / 500.0 * 0.3 AS puntuacion
FROM publicacion p
JOIN perfil au ON au.usuario_id = p.autor_id
WHERE p.estado = 'PUBLICADO'
  AND p.busqueda_tsv @@ websearch_to_tsquery('spanish', %1$L)
ORDER BY puntuacion DESC
LIMIT 20
$q$, $p$ SELECT ARRAY[palabra] AS p FROM (SELECT unnest(ARRAY['motor','api','pagos','cache','juego','mapas','editor','bot','colas','agenda']) AS palabra) v, generate_series(1,3) $p$);

-- P05 bandeja de conversaciones
SELECT pg_temp.medir('P05 bandeja', $q$
WITH mis AS (
    SELECT pc.conversacion_id, pc.ultimo_leido_en FROM participante_conversacion pc
    WHERE pc.usuario_id = %1$L AND pc.abandonada_en IS NULL
),
ultimo AS (
    SELECT DISTINCT ON (m.conversacion_id) m.conversacion_id, m.contenido, m.enviado_en
    FROM mensaje m JOIN mis ON mis.conversacion_id = m.conversacion_id
    WHERE m.eliminado_en IS NULL
    ORDER BY m.conversacion_id, m.enviado_en DESC
)
SELECT u.conversacion_id, u.contenido, u.enviado_en,
       (SELECT count(*) FROM mensaje m2
         WHERE m2.conversacion_id = mis.conversacion_id AND m2.remitente_id <> %1$L
           AND m2.eliminado_en IS NULL
           AND (mis.ultimo_leido_en IS NULL OR m2.enviado_en > mis.ultimo_leido_en)) AS no_leidos
FROM mis JOIN ultimo u ON u.conversacion_id = mis.conversacion_id
ORDER BY u.enviado_en DESC
LIMIT 30
$q$, $p$ SELECT ARRAY[usuario_id::text] AS p FROM (SELECT DISTINCT usuario_id FROM participante_conversacion) s ORDER BY random() LIMIT 30 $p$);

-- P09 cola de moderacion
SELECT pg_temp.medir('P09 cola de moderacion', $q$
SELECT r.id, r.motivo, r.creado_en, coalesce(p.titulo, left(cm.contenido, 120)) AS resumen,
       count(*) OVER (PARTITION BY coalesce(r.publicacion_id, r.comentario_id)) AS reportes
FROM reporte_moderacion r
JOIN perfil rep ON rep.usuario_id = r.reportante_id
LEFT JOIN publicacion p ON p.id = r.publicacion_id
LEFT JOIN comentario cm ON cm.id = r.comentario_id
WHERE r.estado IN ('ABIERTO', 'EN_REVISION')
  AND r.reportante_id <> %1$L
  AND coalesce(p.autor_id, cm.autor_id) <> %1$L
ORDER BY r.es_apelacion DESC, r.creado_en ASC
LIMIT 50
$q$, $p$ SELECT ARRAY[ur.usuario_id::text] AS p
        FROM usuario_rol ur JOIN rol r ON r.id = ur.rol_id CROSS JOIN generate_series(1,2)
        WHERE r.codigo = 'MODERADOR' LIMIT 30 $p$);

-- P13 crecimiento de 90 dias sobre metricas_diarias
SELECT pg_temp.medir('P13 crecimiento 90 dias', $q$
WITH dias AS (SELECT generate_series((current_date - 89), current_date, interval '1 day')::date AS fecha)
SELECT d.fecha, coalesce(m.usuarios_nuevos, 0) AS nuevos,
       sum(coalesce(m.usuarios_nuevos, 0)) OVER (ORDER BY d.fecha) AS acumulado,
       round(avg(coalesce(m.usuarios_activos, 0)) OVER (ORDER BY d.fecha ROWS 6 PRECEDING), 1) AS media_7d
FROM dias d LEFT JOIN metricas_diarias m ON m.fecha = d.fecha
ORDER BY d.fecha
$q$, $p$ SELECT ARRAY['-']::text[] AS p FROM generate_series(1,30) $p$);

-- ============================================================================
-- Consultas implementadas en el sprint 2 (copiadas del codigo)
-- ============================================================================

-- HU-07: PublicacionJpaRepository.deAutorPaginado (primera pagina, limite 20 + 1)
SELECT pg_temp.medir('HU-07 proyectos de un autor', $q$
SELECT p.* FROM publicacion p
WHERE p.autor_id = %1$L AND p.tipo = 'PROYECTO' AND p.estado = 'PUBLICADO'
ORDER BY p.publicado_en DESC, p.id DESC
LIMIT 21
$q$, $p$ SELECT ARRAY[autor_id::text] AS p FROM (SELECT DISTINCT autor_id FROM publicacion) s ORDER BY random() LIMIT 30 $p$);

-- HU-09: RepositorioProyectosJpa.buscar, solo texto
SELECT pg_temp.medir('HU-09 busqueda por texto', $q$
SELECT p.id FROM publicacion p
WHERE p.tipo = 'PROYECTO' AND p.estado = 'PUBLICADO'
  AND p.busqueda_tsv @@ websearch_to_tsquery('spanish', %1$L)
ORDER BY p.publicado_en DESC, p.id DESC LIMIT 21
$q$, $p$ SELECT ARRAY[palabra] AS p FROM (SELECT unnest(ARRAY['motor','api','pagos','cache','juego','mapas','editor','bot','colas','agenda']) AS palabra) v, generate_series(1,3) $p$);

-- HU-09: solo filtro por tecnologia
SELECT pg_temp.medir('HU-09 filtro por tecnologia', $q$
SELECT p.id FROM publicacion p
WHERE p.tipo = 'PROYECTO' AND p.estado = 'PUBLICADO'
  AND EXISTS (SELECT 1 FROM publicacion_tecnologia pt WHERE pt.publicacion_id = p.id AND pt.tecnologia_id = %1$L)
ORDER BY p.publicado_en DESC, p.id DESC LIMIT 21
$q$, $p$ SELECT ARRAY[(1 + (g % 20))::text] AS p FROM generate_series(0,29) g $p$);

-- HU-09: texto y tecnologia combinados (el caso mas selectivo)
SELECT pg_temp.medir('HU-09 texto + tecnologia', $q$
SELECT p.id FROM publicacion p
WHERE p.tipo = 'PROYECTO' AND p.estado = 'PUBLICADO'
  AND p.busqueda_tsv @@ websearch_to_tsquery('spanish', %1$L)
  AND EXISTS (SELECT 1 FROM publicacion_tecnologia pt WHERE pt.publicacion_id = p.id AND pt.tecnologia_id = %2$L)
ORDER BY p.publicado_en DESC, p.id DESC LIMIT 21
$q$, $p$ SELECT ARRAY[palabra, (1 + (g % 20))::text] AS p
        FROM (SELECT unnest(ARRAY['motor','api','pagos','cache','juego','mapas','editor','bot','colas','agenda']) AS palabra) v,
             generate_series(1,3) g $p$);

-- HU-11: RepositorioDiscusionesJdbc.participantes
SELECT pg_temp.medir('HU-11 participantes del hilo', $q$
SELECT c.autor_id, count(*) AS comentarios, min(c.creado_en) AS primera
FROM comentario c
WHERE c.publicacion_id = %1$L AND c.estado = 'PUBLICADO'
GROUP BY c.autor_id
ORDER BY primera, c.autor_id
$q$, $p$ SELECT ARRAY[c.publicacion_id::text] AS p
        FROM (SELECT DISTINCT publicacion_id FROM comentario) c
        JOIN publicacion p ON p.id = c.publicacion_id AND p.tipo = 'DISCUSION'
        ORDER BY random() LIMIT 30 $p$);

-- HU-12: RepositorioDiscusionesJdbc.listar con filtro por etiqueta
SELECT pg_temp.medir('HU-12 listado de discusiones por etiqueta', $q$
SELECT p.id, p.autor_id, p.titulo, p.contenido, p.publicado_en, d.categoria, d.resuelta
FROM publicacion p
JOIN publicacion_discusion d ON d.publicacion_id = p.id
WHERE p.tipo = 'DISCUSION' AND p.estado = 'PUBLICADO'
  AND EXISTS (SELECT 1 FROM publicacion_tecnologia pt WHERE pt.publicacion_id = p.id AND pt.tecnologia_id = %1$L)
ORDER BY p.publicado_en DESC, p.id DESC LIMIT 21
$q$, $p$ SELECT ARRAY[(1 + (g % 20))::text] AS p FROM generate_series(0,29) g $p$);

-- ============================================================================
SELECT consulta, ejecuciones AS n, p50_ms, p95_ms, max_ms, filas_max,
       coalesce(indices, '-') AS indices, coalesce(secuenciales, '-') AS secuenciales
FROM medicion;
