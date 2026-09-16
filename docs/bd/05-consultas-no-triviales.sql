-- ============================================================================
--  DevNet - Consultas no triviales
--
--  Lineamientos 3.2: "implementar consultas no triviales: joins, agregaciones
--  o filtros compuestos".
--
--  Cada consulta responde a una pregunta de docs/bd/01-consultas-clave.md y
--  se apoya en un indice justificado en docs/bd/04-indices.md.
--
--  Los parametros van en notacion :nombre (NamedParameterJdbcTemplate de
--  Spring). Con JPA se traducen a :nombre igualmente.
--
--  Para ejecutar a mano en psql, sustituir los parametros o declararlos con
--  \set y prefijo :'nombre'.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- P01 | Feed personalizado con paginacion por keyset
--
-- Lo no trivial: une dos criterios de interes disjuntos (a quien sigo, que
-- tecnologias me interesan) y pagina de forma determinista sin OFFSET, como
-- exige el lineamiento 5.3.
--
-- Por que keyset y no OFFSET: con OFFSET, PostgreSQL lee y descarta todas las
-- filas anteriores, asi que la pagina 50 cuesta 50 veces la pagina 1. Ademas,
-- si alguien publica mientras el usuario pagina, las filas se desplazan y
-- aparecen duplicados o huecos. La comparacion de filas (publicado_en, id) es
-- estable y ataca directamente ix_publicacion_feed.
--
-- Primera pagina: pasar NULL en :cursor_fecha y :cursor_id.
-- Indice: ix_publicacion_feed, ix_publicacion_tecnologia_inverso
-- Presupuesto: 600 ms p95
-- ----------------------------------------------------------------------------

WITH intereses AS (
    SELECT tecnologia_id
    FROM perfil_tecnologia
    WHERE usuario_id = :usuario
),
seguidos AS (
    SELECT seguido_id
    FROM seguimiento
    WHERE seguidor_id = :usuario
)
SELECT
    p.id,
    p.tipo,
    p.titulo,
    p.publicado_en,
    p.contador_comentarios,
    p.contador_reacciones,
    au.usuario_id            AS autor_id,
    au.nombre_completo       AS autor_nombre,
    au.url_avatar            AS autor_avatar,
    au.reputacion            AS autor_reputacion,
    -- Por que aparece en mi feed: util para depurar y para explicar al usuario
    CASE
        WHEN p.autor_id IN (SELECT seguido_id FROM seguidos) THEN 'SIGUES_AL_AUTOR'
        ELSE 'COINCIDE_TECNOLOGIA'
    END                      AS origen,
    ARRAY(
        SELECT t.nombre
        FROM publicacion_tecnologia pt
        JOIN tecnologia t ON t.id = pt.tecnologia_id
        WHERE pt.publicacion_id = p.id
        ORDER BY t.nombre
    )                        AS tecnologias
FROM publicacion p
JOIN perfil au ON au.usuario_id = p.autor_id
WHERE p.estado = 'PUBLICADO'
  AND (
        p.autor_id IN (SELECT seguido_id FROM seguidos)
     OR EXISTS (
            SELECT 1
            FROM publicacion_tecnologia pt
            JOIN intereses i ON i.tecnologia_id = pt.tecnologia_id
            WHERE pt.publicacion_id = p.id
        )
      )
  -- Comparacion de filas: es lo que permite al planificador usar el indice
  -- compuesto en un solo recorrido descendente.
  AND (
        :cursor_fecha::timestamptz IS NULL
     OR (p.publicado_en, p.id) < (:cursor_fecha::timestamptz, :cursor_id::uuid)
      )
ORDER BY p.publicado_en DESC, p.id DESC
LIMIT 20;


-- ----------------------------------------------------------------------------
-- P02 | Hilo completo de comentarios anidados
--
-- Lo no trivial: recorrido recursivo de un arbol con profundidad variable,
-- devolviendo los nodos ya ordenados en el orden en que deben pintarse
-- (cada respuesta inmediatamente debajo de su padre).
--
-- La clave es ruta_orden: un camino materializado que se construye durante
-- la recursion concatenando "marca de tiempo : id" de cada ancestro. Ordenar
-- por ese arreglo de texto produce exactamente el recorrido en preorden.
-- Sin el, habria que ordenar el arbol en la aplicacion.
--
-- La profundidad esta acotada a 5 por restriccion CHECK en la tabla, asi que
-- la recursion tiene techo garantizado.
--
-- Indice: ix_comentario_publicacion_padre
-- Presupuesto: 500 ms p95
-- ----------------------------------------------------------------------------

WITH RECURSIVE hilo AS (
    -- Termino base: comentarios raiz
    SELECT
        c.id,
        c.comentario_padre_id,
        c.autor_id,
        c.contenido,
        c.estado,
        c.contador_reacciones,
        c.creado_en,
        0::smallint AS nivel,
        ARRAY[to_char(c.creado_en, 'YYYYMMDDHH24MISSUS') || ':' || c.id::text] AS ruta_orden
    FROM comentario c
    WHERE c.publicacion_id = :publicacion
      AND c.comentario_padre_id IS NULL
      AND c.estado <> 'ELIMINADO'

    UNION ALL

    -- Termino recursivo: hijos del nivel anterior
    SELECT
        h.id,
        h.comentario_padre_id,
        h.autor_id,
        h.contenido,
        h.estado,
        h.contador_reacciones,
        h.creado_en,
        (padre.nivel + 1)::smallint,
        padre.ruta_orden || (to_char(h.creado_en, 'YYYYMMDDHH24MISSUS') || ':' || h.id::text)
    FROM comentario h
    JOIN hilo padre ON h.comentario_padre_id = padre.id
    WHERE h.estado <> 'ELIMINADO'
)
SELECT
    hilo.id,
    hilo.comentario_padre_id,
    hilo.nivel,
    -- Un comentario ocultado por moderacion no se borra: se sustituye su
    -- contenido, conservando la estructura del hilo y las respuestas que colgaban de el.
    CASE WHEN hilo.estado = 'OCULTO'
         THEN '[Contenido retirado por moderacion]'
         ELSE hilo.contenido
    END                    AS contenido,
    hilo.estado,
    hilo.contador_reacciones,
    hilo.creado_en,
    pe.nombre_completo     AS autor_nombre,
    pe.url_avatar          AS autor_avatar,
    pe.reputacion          AS autor_reputacion,
    coalesce(d.comentario_aceptado_id = hilo.id, false) AS es_respuesta_aceptada
FROM hilo
JOIN perfil pe ON pe.usuario_id = hilo.autor_id
LEFT JOIN publicacion_discusion d ON d.publicacion_id = :publicacion
ORDER BY hilo.ruta_orden;


-- ----------------------------------------------------------------------------
-- P03 | Proyectos que buscan colaboradores en mis tecnologias
--
-- Lo no trivial: filtro compuesto sobre tres tablas mas un cupo calculado
-- (max_colaboradores menos los que ya estan activos) y una puntuacion de
-- afinidad basada en cuantas de mis tecnologias coinciden.
--
-- Indice: ix_proyecto_busca_colaboradores, ix_publicacion_tecnologia_inverso
-- Presupuesto: 600 ms p95
-- ----------------------------------------------------------------------------

WITH mis_tecnologias AS (
    SELECT tecnologia_id
    FROM perfil_tecnologia
    WHERE usuario_id = :usuario
),
activos_por_proyecto AS (
    SELECT publicacion_proyecto_id, count(*) AS activos
    FROM colaboracion
    WHERE estado IN ('ACEPTADA', 'ACTIVA')
    GROUP BY publicacion_proyecto_id
)
SELECT
    p.id,
    p.titulo,
    pp.resumen,
    pp.estado_proyecto,
    pp.max_colaboradores - coalesce(a.activos, 0) AS cupos_disponibles,
    au.nombre_completo AS propietario,
    count(mt.tecnologia_id)                       AS tecnologias_en_comun,
    ARRAY(
        SELECT t.nombre
        FROM publicacion_tecnologia pt2
        JOIN tecnologia t ON t.id = pt2.tecnologia_id
        WHERE pt2.publicacion_id = p.id
        ORDER BY t.nombre
    )                                             AS tecnologias
FROM publicacion_proyecto pp
JOIN publicacion p  ON p.id = pp.publicacion_id
JOIN perfil au      ON au.usuario_id = p.autor_id
JOIN publicacion_tecnologia pt ON pt.publicacion_id = p.id
JOIN mis_tecnologias mt        ON mt.tecnologia_id = pt.tecnologia_id
LEFT JOIN activos_por_proyecto a ON a.publicacion_proyecto_id = pp.publicacion_id
WHERE pp.busca_colaboradores = true
  AND p.estado = 'PUBLICADO'
  AND p.autor_id <> :usuario
  -- Excluye proyectos donde ya tengo una solicitud en curso o fui retirado
  AND NOT EXISTS (
        SELECT 1 FROM colaboracion c
        WHERE c.publicacion_proyecto_id = pp.publicacion_id
          AND c.usuario_id = :usuario
      )
GROUP BY p.id, p.titulo, pp.resumen, pp.estado_proyecto,
         pp.max_colaboradores, a.activos, au.nombre_completo
HAVING pp.max_colaboradores - coalesce(a.activos, 0) > 0
ORDER BY count(mt.tecnologia_id) DESC, p.publicado_en DESC
LIMIT 20;


-- ----------------------------------------------------------------------------
-- P04 | Busqueda por texto completo con relevancia
--
-- Lo no trivial: combina la relevancia textual de ts_rank con senales del
-- dominio (reputacion del autor y antiguedad) en una sola puntuacion, y
-- devuelve fragmentos resaltados.
--
-- El vector no se calcula aqui: publicacion.busqueda_tsv es columna generada
-- y persistida, indexada con GIN.
--
-- Indice: ix_publicacion_busqueda
-- Presupuesto: 900 ms p95
-- ----------------------------------------------------------------------------

SELECT
    p.id,
    p.tipo,
    p.titulo,
    ts_headline(
        'spanish',
        p.contenido,
        websearch_to_tsquery('spanish', :termino),
        'MaxWords=30, MinWords=10, ShortWord=3'
    )                                       AS fragmento,
    p.publicado_en,
    au.nombre_completo                      AS autor_nombre,
    ts_rank(p.busqueda_tsv, websearch_to_tsquery('spanish', :termino)) AS relevancia_texto,
    -- Puntuacion combinada: relevancia textual, prestigio del autor y frescura.
    -- Los pesos se afinan con los datos reales del sprint 2.
    (
        ts_rank(p.busqueda_tsv, websearch_to_tsquery('spanish', :termino)) * 1.0
      + least(au.reputacion, 500) / 500.0 * 0.3
      + exp(-extract(epoch FROM (now() - p.publicado_en)) / (86400 * 30)) * 0.2
    )                                       AS puntuacion
FROM publicacion p
JOIN perfil au ON au.usuario_id = p.autor_id
WHERE p.estado = 'PUBLICADO'
  AND p.busqueda_tsv @@ websearch_to_tsquery('spanish', :termino)
ORDER BY puntuacion DESC
LIMIT 20;


-- ----------------------------------------------------------------------------
-- P05 | Bandeja de conversaciones con ultimo mensaje y no leidos
--
-- Lo no trivial: DISTINCT ON de PostgreSQL para quedarse con el ultimo mensaje
-- de cada conversacion en un solo recorrido del indice, mas un conteo de no
-- leidos que no necesita tabla de estado de lectura por mensaje.
--
-- DISTINCT ON exige que ORDER BY empiece por las mismas expresiones; ese orden
-- es exactamente ix_mensaje_conversacion, asi que no hay ordenamiento extra.
--
-- Indice: ix_mensaje_conversacion, ix_participante_usuario
-- Presupuesto: 400 ms p95
-- ----------------------------------------------------------------------------

WITH mis_conversaciones AS (
    SELECT pc.conversacion_id, pc.ultimo_leido_en, pc.silenciada
    FROM participante_conversacion pc
    WHERE pc.usuario_id = :usuario
      AND pc.abandonada_en IS NULL
),
ultimo_mensaje AS (
    SELECT DISTINCT ON (m.conversacion_id)
        m.conversacion_id,
        m.id           AS mensaje_id,
        m.contenido,
        m.remitente_id,
        m.enviado_en
    FROM mensaje m
    JOIN mis_conversaciones mc ON mc.conversacion_id = m.conversacion_id
    WHERE m.eliminado_en IS NULL
    ORDER BY m.conversacion_id, m.enviado_en DESC
)
SELECT
    c.id                          AS conversacion_id,
    otro.usuario_id               AS interlocutor_id,
    otro_perfil.nombre_completo   AS interlocutor_nombre,
    otro_perfil.url_avatar        AS interlocutor_avatar,
    um.contenido                  AS ultimo_mensaje,
    um.enviado_en                 AS ultimo_mensaje_en,
    (um.remitente_id = :usuario)  AS ultimo_mensaje_es_mio,
    mc.silenciada,
    (
        SELECT count(*)
        FROM mensaje m2
        WHERE m2.conversacion_id = c.id
          AND m2.remitente_id <> :usuario
          AND m2.eliminado_en IS NULL
          AND (mc.ultimo_leido_en IS NULL OR m2.enviado_en > mc.ultimo_leido_en)
    )                             AS no_leidos
FROM mis_conversaciones mc
JOIN conversacion c    ON c.id = mc.conversacion_id
JOIN ultimo_mensaje um ON um.conversacion_id = c.id
-- LATERAL para el otro participante: soporta ya conversaciones de grupo,
-- donde devolveria el primero por orden de ingreso.
LEFT JOIN LATERAL (
    SELECT pc2.usuario_id
    FROM participante_conversacion pc2
    WHERE pc2.conversacion_id = c.id
      AND pc2.usuario_id <> :usuario
    ORDER BY pc2.unido_en
    LIMIT 1
) AS otro ON true
LEFT JOIN perfil otro_perfil ON otro_perfil.usuario_id = otro.usuario_id
ORDER BY um.enviado_en DESC
LIMIT 30;


-- ----------------------------------------------------------------------------
-- P09 | Cola de moderacion, solo lo que YO puedo resolver
--
-- Lo no trivial: aplica la regla R3 en la propia consulta. El moderador que
-- resuelve debe ser distinto del reportante y distinto del autor del contenido,
-- y la autoria vive en dos tablas diferentes segun el reporte apunte a una
-- publicacion o a un comentario.
--
-- Filtrar aqui, y no solo al resolver, evita que el moderador pierda tiempo
-- abriendo casos que el sistema le va a rechazar despues.
--
-- Indice: ix_reporte_pendiente
-- Presupuesto: 500 ms p95
-- ----------------------------------------------------------------------------

SELECT
    r.id,
    r.motivo,
    r.detalle,
    r.estado,
    r.es_apelacion,
    r.creado_en,
    CASE WHEN r.publicacion_id IS NOT NULL THEN 'PUBLICACION' ELSE 'COMENTARIO' END AS objetivo_tipo,
    coalesce(r.publicacion_id, r.comentario_id)  AS objetivo_id,
    coalesce(p.titulo, left(cm.contenido, 120))  AS objetivo_resumen,
    coalesce(p.autor_id, cm.autor_id)            AS objetivo_autor_id,
    rep.nombre_completo                          AS reportante,
    -- Cuantos reportes distintos acumula el mismo objetivo: prioriza la cola
    count(*) OVER (PARTITION BY coalesce(r.publicacion_id, r.comentario_id)) AS reportes_sobre_el_objetivo,
    now() - r.creado_en                          AS antiguedad
FROM reporte_moderacion r
JOIN perfil rep ON rep.usuario_id = r.reportante_id
LEFT JOIN publicacion p ON p.id = r.publicacion_id
LEFT JOIN comentario cm ON cm.id = r.comentario_id
WHERE r.estado IN ('ABIERTO', 'EN_REVISION')
  AND r.reportante_id <> :moderador                       -- R3: no soy el reportante
  AND coalesce(p.autor_id, cm.autor_id) <> :moderador     -- R3: no soy el autor
ORDER BY r.es_apelacion DESC, r.creado_en ASC
LIMIT 50;


-- ----------------------------------------------------------------------------
-- P10 | Publicaciones de un autor ocultadas en los ultimos 30 dias
--
-- Alimenta la regla R6 (suspension automatica al llegar a tres).
-- Cuenta transiciones HACIA el estado OCULTO en la ventana, no publicaciones
-- que esten ocultas ahora: una publicacion ocultada y luego restaurada por
-- apelacion sigue contando como incidente en la ventana.
--
-- Indice: ix_historial_publicacion, ix_publicacion_autor_estado
-- Presupuesto: 200 ms p95
-- ----------------------------------------------------------------------------

SELECT count(DISTINCT h.publicacion_id) AS ocultadas_30_dias
FROM historial_estado_publicacion h
JOIN publicacion p ON p.id = h.publicacion_id
WHERE p.autor_id   = :autor
  AND h.estado_nuevo = 'OCULTO'
  AND h.creado_en  >= now() - interval '30 days';


-- ----------------------------------------------------------------------------
-- P13 | Crecimiento de la comunidad en los ultimos 90 dias
--
-- Lo no trivial: serie temporal densa. generate_series produce TODOS los dias
-- del rango y el LEFT JOIN los cruza con metricas_diarias, de modo que los
-- dias sin actividad aparecen en cero en vez de desaparecer de la serie.
-- Una grafica construida sin esto muestra una linea continua donde en realidad
-- hubo un hueco, y miente.
--
-- Ademas: LAG para la variacion respecto al dia anterior, SUM con marco de
-- ventana para el acumulado y AVG movil de 7 dias para suavizar la estacionalidad
-- de fin de semana.
--
-- Lee metricas_diarias, preconsolidada por sp_consolidar_metricas_diarias.
-- NO agrega en vivo sobre publicacion, comentario ni mensaje: esa es la
-- decision que hace alcanzable el presupuesto.
--
-- Presupuesto: 2 s p95
-- ----------------------------------------------------------------------------

WITH dias AS (
    SELECT generate_series(
        (current_date - interval '89 days')::date,
        current_date,
        interval '1 day'
    )::date AS fecha
),
serie AS (
    SELECT
        d.fecha,
        coalesce(m.usuarios_nuevos, 0)      AS usuarios_nuevos,
        coalesce(m.usuarios_activos, 0)     AS usuarios_activos,
        coalesce(m.publicaciones_nuevas, 0) AS publicaciones_nuevas,
        coalesce(m.comentarios_nuevos, 0)   AS comentarios_nuevos,
        coalesce(m.mensajes_enviados, 0)    AS mensajes_enviados,
        coalesce(m.reportes_abiertos, 0)    AS reportes_abiertos,
        (m.fecha IS NULL)                   AS sin_consolidar
    FROM dias d
    LEFT JOIN metricas_diarias m ON m.fecha = d.fecha
)
SELECT
    fecha,
    usuarios_nuevos,
    usuarios_activos,
    publicaciones_nuevas,
    comentarios_nuevos,
    mensajes_enviados,
    reportes_abiertos,
    sin_consolidar,
    usuarios_nuevos - lag(usuarios_nuevos) OVER (ORDER BY fecha) AS variacion_altas,
    sum(usuarios_nuevos) OVER (ORDER BY fecha
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)        AS altas_acumuladas,
    round(avg(usuarios_activos) OVER (ORDER BY fecha
        ROWS BETWEEN 6 PRECEDING AND CURRENT ROW), 1)            AS activos_media_7d,
    CASE
        WHEN lag(usuarios_activos, 7) OVER (ORDER BY fecha) > 0
        THEN round(
                 (usuarios_activos - lag(usuarios_activos, 7) OVER (ORDER BY fecha))::numeric
                 / lag(usuarios_activos, 7) OVER (ORDER BY fecha) * 100, 1)
    END                                                          AS variacion_semanal_pct
FROM serie
ORDER BY fecha;


-- ----------------------------------------------------------------------------
-- P14 | Tecnologias mas discutidas por trimestre, con movimiento de posicion
--
-- Lo no trivial: dos funciones de ventana encadenadas. RANK particiona por
-- trimestre para ordenar dentro de cada uno; LAG particiona por tecnologia
-- para comparar su posicion con la del trimestre anterior.
--
-- Detalle importante: el recorte al top 10 se hace al FINAL, en el SELECT
-- exterior. Ponerlo en el mismo nivel donde se calcula LAG lo romperia, porque
-- PostgreSQL aplica WHERE antes que las funciones de ventana y LAG solo veria
-- las filas ya recortadas, perdiendo la historia de las que salieron del top.
--
-- Presupuesto: 2 s p95
-- ----------------------------------------------------------------------------

WITH menciones AS (
    SELECT
        date_trunc('quarter', p.publicado_en)::date AS trimestre,
        t.id                                        AS tecnologia_id,
        t.nombre                                    AS tecnologia,
        count(*)                                    AS publicaciones,
        count(*) FILTER (WHERE p.tipo = 'DISCUSION') AS discusiones,
        count(*) FILTER (WHERE p.tipo = 'PROYECTO')  AS proyectos,
        count(DISTINCT p.autor_id)                  AS autores_distintos
    FROM publicacion p
    JOIN publicacion_tecnologia pt ON pt.publicacion_id = p.id
    JOIN tecnologia t              ON t.id = pt.tecnologia_id
    WHERE p.estado IN ('PUBLICADO', 'ARCHIVADO')
      AND p.publicado_en >= date_trunc('quarter', current_date) - interval '1 year'
    GROUP BY 1, 2, 3
),
rankeado AS (
    SELECT
        m.*,
        rank() OVER (PARTITION BY trimestre ORDER BY publicaciones DESC, autores_distintos DESC) AS posicion
    FROM menciones m
),
con_historia AS (
    SELECT
        r.*,
        lag(posicion)      OVER (PARTITION BY tecnologia_id ORDER BY trimestre) AS posicion_anterior,
        lag(publicaciones) OVER (PARTITION BY tecnologia_id ORDER BY trimestre) AS publicaciones_anterior
    FROM rankeado r
)
SELECT
    trimestre,
    posicion,
    tecnologia,
    publicaciones,
    discusiones,
    proyectos,
    autores_distintos,
    posicion_anterior,
    CASE
        WHEN posicion_anterior IS NULL           THEN 'NUEVA'
        WHEN posicion < posicion_anterior        THEN 'SUBE'
        WHEN posicion > posicion_anterior        THEN 'BAJA'
        ELSE 'ESTABLE'
    END                                          AS movimiento,
    posicion_anterior - posicion                 AS puestos_ganados
FROM con_historia
WHERE posicion <= 10
ORDER BY trimestre DESC, posicion ASC;


-- ----------------------------------------------------------------------------
-- P15 | Retencion a 30 dias por cohorte semanal de alta
--
-- Lo no trivial: analisis de cohortes. Agrupa por semana de registro y mide
-- que proporcion de cada cohorte seguia activa en la ventana de los dias 30 a
-- 37 posteriores a su alta. "Activa" significa haber publicado, comentado o
-- enviado un mensaje: tres tablas distintas unidas por EXISTS, que corta en
-- cuanto encuentra la primera coincidencia.
--
-- Solo se incluyen cohortes cuya ventana de observacion ya cerro; si no, las
-- mas recientes apareceran con retencion artificialmente baja.
--
-- Presupuesto: 2 s p95
-- ----------------------------------------------------------------------------

WITH cohortes AS (
    SELECT
        u.id                                      AS usuario_id,
        date_trunc('week', u.creado_en)::date     AS semana_alta,
        u.creado_en                               AS alta_en
    FROM usuario u
    WHERE u.creado_en < now() - interval '37 days'
),
actividad AS (
    SELECT
        c.semana_alta,
        c.usuario_id,
        (
        EXISTS (
            SELECT 1 FROM publicacion p
            WHERE p.autor_id = c.usuario_id
              AND p.creado_en BETWEEN c.alta_en + interval '30 days'
                                  AND c.alta_en + interval '37 days'
        ) OR EXISTS (
            SELECT 1 FROM comentario cm
            WHERE cm.autor_id = c.usuario_id
              AND cm.creado_en BETWEEN c.alta_en + interval '30 days'
                                   AND c.alta_en + interval '37 days'
        ) OR EXISTS (
            SELECT 1 FROM mensaje ms
            WHERE ms.remitente_id = c.usuario_id
              AND ms.enviado_en BETWEEN c.alta_en + interval '30 days'
                                    AND c.alta_en + interval '37 days'
        )
        ) AS retenido
    FROM cohortes c
)
SELECT
    semana_alta,
    count(*)                                   AS tamano_cohorte,
    count(*) FILTER (WHERE retenido)           AS retenidos,
    round(
        count(*) FILTER (WHERE retenido)::numeric / count(*) * 100, 1
    )                                          AS retencion_pct
FROM actividad
GROUP BY semana_alta
ORDER BY semana_alta DESC;


-- ----------------------------------------------------------------------------
-- P16 | Historia de auditoria de un registro, con el campo que cambio
--
-- Lo no trivial: compara los jsonb de antes y despues y devuelve UNA FILA POR
-- CAMPO MODIFICADO, no el documento completo. jsonb_each_text expande el objeto
-- a pares clave-valor y el filtro descarta lo que no cambio.
--
-- Indice: ix_auditoria_registro
-- Presupuesto: 500 ms p95
-- ----------------------------------------------------------------------------

SELECT
    a.creado_en,
    a.operacion,
    a.actor_id,
    pe.nombre_completo AS actor,
    a.trace_id,
    campo.key          AS campo,
    (a.datos_antes   ->> campo.key) AS valor_anterior,
    (a.datos_despues ->> campo.key) AS valor_nuevo
FROM auditoria a
LEFT JOIN perfil pe ON pe.usuario_id = a.actor_id
LEFT JOIN LATERAL jsonb_each_text(coalesce(a.datos_despues, a.datos_antes)) AS campo ON true
WHERE a.tabla       = :tabla
  AND a.registro_id = :registro_id
  AND (
        a.operacion <> 'UPDATE'
     OR (a.datos_antes ->> campo.key) IS DISTINCT FROM (a.datos_despues ->> campo.key)
      )
ORDER BY a.creado_en DESC, campo.key;


-- ============================================================================
--  CONSULTAS DE INTEGRIDAD
--
--  No sirven a una pantalla: verifican que las denormalizaciones declaradas en
--  docs/bd/02-modelo-logico.md siguen siendo consistentes, y que el invariante
--  del discriminador se mantiene.
--
--  Las tres deben devolver CERO FILAS. Se ejecutan en el pipeline como parte
--  de las pruebas de integracion. Una fila devuelta es un fallo.
-- ============================================================================

-- I1 | El saldo de reputacion debe coincidir con la suma del libro mayor
SELECT
    p.usuario_id,
    p.reputacion                  AS saldo_almacenado,
    coalesce(sum(e.puntos), 0)    AS saldo_reconstruido,
    p.reputacion - coalesce(sum(e.puntos), 0) AS diferencia
FROM perfil p
LEFT JOIN evento_reputacion e ON e.usuario_id = p.usuario_id
GROUP BY p.usuario_id, p.reputacion
HAVING p.reputacion <> coalesce(sum(e.puntos), 0);


-- I2 | Toda publicacion debe tener exactamente el subtipo que declara su discriminador.
--      Es el invariante que ninguna restriccion declarativa de PostgreSQL puede
--      imponer sobre una jerarquia supertipo/subtipo sin recurrir a un trigger.
SELECT
    p.id,
    p.tipo,
    (pp.publicacion_id IS NOT NULL) AS tiene_subtipo_proyecto,
    (pd.publicacion_id IS NOT NULL) AS tiene_subtipo_discusion
FROM publicacion p
LEFT JOIN publicacion_proyecto  pp ON pp.publicacion_id = p.id
LEFT JOIN publicacion_discusion pd ON pd.publicacion_id = p.id
WHERE (p.tipo = 'PROYECTO'  AND pp.publicacion_id IS NULL)
   OR (p.tipo = 'DISCUSION' AND pd.publicacion_id IS NULL)
   OR (pp.publicacion_id IS NOT NULL AND pd.publicacion_id IS NOT NULL);


-- I3 | Los contadores denormalizados deben coincidir con el conteo real
SELECT
    p.id,
    p.contador_comentarios,
    (SELECT count(*) FROM comentario c
      WHERE c.publicacion_id = p.id AND c.estado = 'PUBLICADO') AS comentarios_reales,
    p.contador_reacciones,
    (SELECT count(*) FROM reaccion_publicacion r
      WHERE r.publicacion_id = p.id)                            AS reacciones_reales
FROM publicacion p
WHERE p.contador_comentarios <> (SELECT count(*) FROM comentario c
                                  WHERE c.publicacion_id = p.id AND c.estado = 'PUBLICADO')
   OR p.contador_reacciones  <> (SELECT count(*) FROM reaccion_publicacion r
                                  WHERE r.publicacion_id = p.id);