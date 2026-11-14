-- ============================================================================
--  V13 - Reporte de actividad y crecimiento (HU-19)
--
--  Tres piezas, todas para GET /api/v1/reportes/actividad:
--
--  1. Permiso metricas:consultar, solo ADMIN. Los permisos reporte:crear y
--     reporte:resolver son de la moderacion de contenido (denunciar una
--     publicacion), no de las metricas; mezclarlos daria a un moderador acceso
--     a estadisticas de toda la comunidad.
--
--  2. sp_consolidar_metricas_pendientes(desde, hasta)
--     V11 dejo abierto QUIEN llena metricas_diarias ("programarlo es decision
--     de quien implemente HU-19"). Se descarto un programador de tareas: el plan
--     gratuito de Render duerme el servicio tras 15 minutos de inactividad y una
--     tarea de las 00:10 simplemente no correria. En su lugar el reporte
--     consolida BAJO DEMANDA solo lo que hace falta:
--
--        un dia esta pendiente si no tiene fila, o si su fila se calculo ANTES
--        de que el dia terminara (calculado_en < medianoche siguiente, hora de
--        Colombia). La segunda condicion es la que evita que el dato de "hoy"
--        quede congelado a medias: lo que se calculo el 14 a las 10:00 se
--        recalcula el 15, y desde entonces ya no se toca.
--
--     Cada dia cerrado se calcula una sola vez; hoy se recalcula en cada
--     consulta (un dia, una pasada). Es idempotente por construccion, porque
--     delega en sp_consolidar_metricas_diarias, y no consolida dias futuros.
--
--  3. fn_usuarios_activos(desde, hasta)
--     "Usuarios activos del rango" NO es la suma de usuarios_activos de cada
--     dia: alguien activo el lunes y el martes contaria dos veces. Hace falta el
--     conteo de DISTINTOS sobre todo el rango, que una tabla de hechos por dia
--     no puede dar. Se calcula aqui, cerca de los datos, por la misma razon que
--     V11: hacerlo en Java obligaria a un modulo a leer tablas de otros.
--
--     La definicion de "activo" es la de V11 (inicio o renovacion de sesion, o
--     publicar, comentar, reaccionar, enviar un mensaje). Esta duplicada a
--     proposito en vez de reescribir V11, que ya esta aplicada; ReporteActividadIT
--     compara ambas dia por dia para que no se separen sin que lo note un test.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. Permiso
-- ----------------------------------------------------------------------------
INSERT INTO permiso (codigo, descripcion) VALUES
    ('metricas:consultar', 'Consultar el reporte de actividad y crecimiento de la comunidad');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r
JOIN permiso p ON p.codigo = 'metricas:consultar'
WHERE r.codigo = 'ADMIN';


-- ----------------------------------------------------------------------------
-- 2. Consolidar solo lo pendiente
-- ----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_consolidar_metricas_pendientes(
    p_desde date,
    p_hasta date
)
LANGUAGE plpgsql
AS $$
DECLARE
    zona constant text := 'America/Bogota';
    hoy  date := (now() AT TIME ZONE zona)::date;
    tope date := LEAST(p_hasta, hoy);      -- nunca dias futuros
    dia  date;
BEGIN
    IF p_hasta < p_desde THEN
        RAISE EXCEPTION 'Rango invertido: % es anterior a %', p_hasta, p_desde
              USING ERRCODE = 'invalid_parameter_value';
    END IF;

    -- Si todo el rango es futuro, tope < p_desde y generate_series no devuelve filas.
    FOR dia IN
        SELECT p_desde + g.i
          FROM generate_series(0, tope - p_desde) AS g(i)
         WHERE NOT EXISTS (
                SELECT 1 FROM metricas_diarias m
                 WHERE m.fecha = p_desde + g.i
                   AND m.calculado_en >= ((p_desde + g.i + 1)::timestamp AT TIME ZONE zona))
    LOOP
        CALL sp_consolidar_metricas_diarias(dia, dia);
    END LOOP;
END;
$$;

COMMENT ON PROCEDURE sp_consolidar_metricas_pendientes(date, date) IS
    'Consolida solo los dias del rango sin fila o calculados antes de que el dia terminara (V13). Lo invoca el reporte de HU-19; sustituye a un programador de tareas, que el plan gratuito de Render no puede garantizar.';


-- ----------------------------------------------------------------------------
-- 3. Usuarios activos DISTINTOS en un rango
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_usuarios_activos(p_desde date, p_hasta date)
RETURNS integer
LANGUAGE sql
STABLE
AS $$
    WITH limites AS (
        SELECT (p_desde::timestamp AT TIME ZONE 'America/Bogota')       AS inicio,
               ((p_hasta + 1)::timestamp AT TIME ZONE 'America/Bogota') AS fin
    )
    SELECT count(DISTINCT a.u)::integer
      FROM limites l,
           LATERAL (
               SELECT actor_id AS u FROM auditoria
                WHERE operacion IN ('INICIO_SESION', 'REFRESCO_ROTADO')
                  AND creado_en >= l.inicio AND creado_en < l.fin
               UNION ALL
               SELECT autor_id FROM publicacion
                WHERE publicado_en >= l.inicio AND publicado_en < l.fin
               UNION ALL
               SELECT autor_id FROM comentario
                WHERE creado_en >= l.inicio AND creado_en < l.fin
               UNION ALL
               SELECT usuario_id FROM reaccion_publicacion
                WHERE creado_en >= l.inicio AND creado_en < l.fin
               UNION ALL
               SELECT remitente_id FROM mensaje
                WHERE enviado_en >= l.inicio AND enviado_en < l.fin
           ) AS a
     WHERE a.u IS NOT NULL;
$$;

COMMENT ON FUNCTION fn_usuarios_activos(date, date) IS
    'Usuarios DISTINTOS con actividad en el rango, dias de America/Bogota, ambos extremos incluidos (V13). Misma definicion de activo que sp_consolidar_metricas_diarias (V11).';
