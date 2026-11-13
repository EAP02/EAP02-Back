-- ============================================================================
--  V11 - Procedimiento almacenado sp_consolidar_metricas_diarias
--
--  §3.2: "incluir al menos un procedimiento almacenado cuando sea coherente con
--  la distribucion de responsabilidades definida en la arquitectura". §5.4 lo
--  admite para "procesamiento cercano a los datos".
--
--  Por que aqui y no en Java. metricas_diarias (V1) es la tabla de hechos que
--  separa el camino analitico del transaccional (P13-P15 de
--  docs/bd/01-consultas-clave.md). Llenarla es agregar varias tablas de varios
--  modulos (identity, project, discussion, interaction, messaging) por rango de
--  fechas. En Java, un modulo tendria que leer las tablas de los demas, que es
--  justo lo que prohiben las fronteras de ADR-006. En la base es una sola
--  pasada cercana a los datos y no acopla ningun modulo con otro.
--
--  Contrato:
--    CALL sp_consolidar_metricas_diarias();                          -- ayer
--    CALL sp_consolidar_metricas_diarias('2026-11-01');              -- un dia
--    CALL sp_consolidar_metricas_diarias('2026-11-01', '2026-11-13'); -- rango
--
--  - Idempotente: recalcula y reemplaza (ON CONFLICT DO UPDATE). Ejecutarlo dos
--    veces, o volver a ejecutar un dia ya cerrado, deja el mismo resultado.
--  - Los dias son de America/Bogota, no UTC: un dia UTC empieza a las 7 p. m.
--    en Colombia y partiria la actividad de una tarde en dos fechas.
--  - Quien lo invoca: hoy, a mano (demo y backfill). Programarlo (pg_cron en
--    Supabase o una tarea en la aplicacion) es decision de quien implemente el
--    reporte de HU-19; ver docs/bd/README.md.
-- ============================================================================

CREATE OR REPLACE PROCEDURE sp_consolidar_metricas_diarias(
    p_desde date DEFAULT NULL,
    p_hasta date DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    zona   constant text := 'America/Bogota';
    hoy    date := (now() AT TIME ZONE zona)::date;
    desde  date := COALESCE(p_desde, hoy - 1);
    hasta  date := COALESCE(p_hasta, COALESCE(p_desde, hoy - 1));
    dia    date;
    inicio timestamptz;
    fin    timestamptz;
BEGIN
    IF hasta < desde THEN
        RAISE EXCEPTION 'Rango invertido: % es anterior a %', hasta, desde
              USING ERRCODE = 'invalid_parameter_value';
    END IF;
    IF hasta > hoy THEN
        RAISE EXCEPTION 'No se consolidan dias futuros: % es posterior a hoy (%)', hasta, hoy
              USING ERRCODE = 'invalid_parameter_value';
    END IF;

    dia := desde;
    WHILE dia <= hasta LOOP
        -- [inicio, fin): medianoche a medianoche en hora de Colombia
        inicio := dia::timestamp AT TIME ZONE zona;
        fin    := (dia + 1)::timestamp AT TIME ZONE zona;

        INSERT INTO metricas_diarias AS m (
            fecha, usuarios_nuevos, usuarios_activos, publicaciones_nuevas,
            proyectos_nuevos, discusiones_nuevas, comentarios_nuevos,
            mensajes_enviados, colaboraciones_aceptadas, reportes_abiertos,
            calculado_en)
        SELECT
            dia,
            (SELECT count(*) FROM usuario
              WHERE creado_en >= inicio AND creado_en < fin),

            -- Activo: inicio o renovacion de sesion, o cualquier contribucion
            (SELECT count(DISTINCT u) FROM (
                 SELECT actor_id FROM auditoria
                  WHERE operacion IN ('INICIO_SESION', 'REFRESCO_ROTADO')
                    AND creado_en >= inicio AND creado_en < fin
                 UNION ALL
                 SELECT autor_id FROM publicacion
                  WHERE publicado_en >= inicio AND publicado_en < fin
                 UNION ALL
                 SELECT autor_id FROM comentario
                  WHERE creado_en >= inicio AND creado_en < fin
                 UNION ALL
                 SELECT usuario_id FROM reaccion_publicacion
                  WHERE creado_en >= inicio AND creado_en < fin
                 UNION ALL
                 SELECT remitente_id FROM mensaje
                  WHERE enviado_en >= inicio AND enviado_en < fin
             ) AS actividad(u)
             WHERE u IS NOT NULL),

            p.total, p.proyectos, p.discusiones,

            (SELECT count(*) FROM comentario
              WHERE creado_en >= inicio AND creado_en < fin),
            (SELECT count(*) FROM mensaje
              WHERE enviado_en >= inicio AND enviado_en < fin),
            (SELECT count(*) FROM colaboracion
              WHERE estado IN ('ACEPTADA', 'ACTIVA')
                AND resuelto_en >= inicio AND resuelto_en < fin),
            -- "Abiertos" = creados ese dia, no los que siguen pendientes hoy:
            -- una metrica diaria no puede depender de cuando se calcula.
            (SELECT count(*) FROM reporte_moderacion
              WHERE creado_en >= inicio AND creado_en < fin),

            now()
        FROM (
            -- Publicadas ese dia (publicado_en es NULL solo en borradores)
            SELECT count(*)                                    AS total,
                   count(*) FILTER (WHERE tipo = 'PROYECTO')   AS proyectos,
                   count(*) FILTER (WHERE tipo = 'DISCUSION')  AS discusiones
              FROM publicacion
             WHERE publicado_en >= inicio AND publicado_en < fin
        ) AS p
        ON CONFLICT (fecha) DO UPDATE SET
            usuarios_nuevos          = EXCLUDED.usuarios_nuevos,
            usuarios_activos         = EXCLUDED.usuarios_activos,
            publicaciones_nuevas     = EXCLUDED.publicaciones_nuevas,
            proyectos_nuevos         = EXCLUDED.proyectos_nuevos,
            discusiones_nuevas       = EXCLUDED.discusiones_nuevas,
            comentarios_nuevos       = EXCLUDED.comentarios_nuevos,
            mensajes_enviados        = EXCLUDED.mensajes_enviados,
            colaboraciones_aceptadas = EXCLUDED.colaboraciones_aceptadas,
            reportes_abiertos        = EXCLUDED.reportes_abiertos,
            calculado_en             = EXCLUDED.calculado_en;

        dia := dia + 1;
    END LOOP;
END;
$$;

COMMENT ON PROCEDURE sp_consolidar_metricas_diarias(date, date) IS
    'Llena metricas_diarias para un rango de dias de America/Bogota (por defecto, ayer). Idempotente. Camino analitico de P13-P15 y base del reporte de HU-19.';
