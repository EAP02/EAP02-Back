-- ============================================================================
--  DevNet - Datos representativos para medir planes de ejecucion
--
--  Lineamientos §5.3: "identificar tablas y consultas criticas, estimar volumenes
--  y medir planes de ejecucion". docs/bd/README.md lo dejaba pendiente del sprint 2:
--  "poblar con datos representativos y completar el registro de mediciones".
--
--  Volumenes: la columna "Proyeccion a 1 año" de docs/bd/01-consultas-clave.md.
--  Con los datos de demostracion (decenas de filas) el planificador prefiere un
--  recorrido secuencial para todo, y medir no demuestra nada.
--
--  Distribuciones sesgadas a proposito, porque asi se comporta una red social y es
--  donde fallan los indices: unos pocos autores publican mucho, unas pocas
--  publicaciones concentran los comentarios y unas pocas conversaciones concentran
--  los mensajes. El sesgo se obtiene elevando random() a una potencia.
--
--  Cuidado al modificarlo: random() nunca va en una condicion JOIN ... ON.
--  PostgreSQL la reevaluaria en cada comparacion y la union devolveria cero o
--  varias filas al azar. Se calcula una vez por fila en una subconsulta y luego
--  se une por igualdad.
--
--  NUNCA EJECUTAR EN PRODUCCION. Es para una base local o de integracion vacia,
--  despues de aplicar las migraciones V1 a V5:
--
--      podman/docker exec -i <contenedor> psql -U postgres -d devnet < datos-representativos.sql
--
--  setseed hace que dos ejecuciones generen los mismos datos y las mediciones
--  sean comparables entre sprints. Tarda alrededor de un minuto.
-- ============================================================================

\set ON_ERROR_STOP on
SELECT setseed(0.42);

-- Vocabulario para que la busqueda de texto completo (P04, HU-09) encuentre algo.
CREATE TEMP TABLE vocabulario (n int, palabra text);
INSERT INTO vocabulario
SELECT row_number() OVER (), palabra FROM unnest(ARRAY[
    'motor','plantillas','api','gestor','inventarios','microservicios','compilador',
    'tablero','chat','pagos','autenticacion','cache','colas','graficos','scraper',
    'bot','juego','analizador','framework','cliente','servidor','biblioteca',
    'migracion','pruebas','despliegue','contenedores','monitoreo','recomendaciones',
    'facturacion','reservas','agenda','mapas','traductor','editor','notificaciones'
]) AS palabra;

-- ----------------------------------------------------------------------------
-- 1. Usuarios y perfiles: 5 000
-- ----------------------------------------------------------------------------
CREATE TEMP TABLE u AS
SELECT g AS n, gen_random_uuid() AS id
FROM generate_series(1, 5000) g;

INSERT INTO usuario (id, correo, nombre_usuario, clave_hash, mfa_habilitado, creado_en)
SELECT id, 'usuario' || n || '@devnet.test', 'usuario_' || lpad(n::text, 5, '0'),
       'sin-clave-datos-de-prueba', n <= 20,
       now() - (random() * interval '365 days')
FROM u;

-- Los 20 primeros son moderadores (con MFA); el resto, desarrolladores.
INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT u.id, r.id FROM u JOIN rol r ON r.codigo = CASE WHEN u.n <= 20 THEN 'MODERADOR' ELSE 'DESARROLLADOR' END;

INSERT INTO perfil (usuario_id, nombre_completo, biografia, disponible_colaborar, reputacion)
SELECT id, 'Usuario ' || n, 'Desarrollador de prueba numero ' || n,
       random() < 0.4, floor(power(random(), 3) * 800)::int
FROM u;

-- Entre 1 y 5 tecnologias declaradas, con sesgo hacia las primeras del catalogo.
INSERT INTO perfil_tecnologia (usuario_id, tecnologia_id, nivel)
SELECT DISTINCT ON (u.id, t.tid) u.id, t.tid,
       (ARRAY['BASICO','INTERMEDIO','AVANZADO','EXPERTO'])[1 + floor(random() * 4)::int]
FROM u
CROSS JOIN LATERAL (
    SELECT (1 + floor(power(random(), 2) * 20))::smallint AS tid
    FROM generate_series(1, 1 + floor(random() * 5)::int)
) t;

-- ~50 000 seguimientos, sin autoseguimiento ni duplicados.
INSERT INTO seguimiento (seguidor_id, seguido_id)
SELECT DISTINCT a.id, b.id
FROM (SELECT 1 + floor(random() * 5000)::int AS na,
             1 + floor(power(random(), 2) * 5000)::int AS nb
      FROM generate_series(1, 55000)) s
JOIN u a ON a.n = s.na
JOIN u b ON b.n = s.nb
WHERE a.id <> b.id
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. Publicaciones: 40 000 (70 % proyectos, 30 % discusiones)
--    92 % publicadas, 5 % ocultas, 3 % archivadas.
-- ----------------------------------------------------------------------------
CREATE TEMP TABLE p AS
SELECT g AS n,
       gen_random_uuid() AS id,
       -- Sesgo: los usuarios de numero bajo publican mucho mas.
       1 + floor(power(random(), 3) * 5000)::int AS autor_n,
       CASE WHEN random() < 0.7 THEN 'PROYECTO' ELSE 'DISCUSION' END AS tipo,
       CASE WHEN random() < 0.92 THEN 'PUBLICADO'
            WHEN random() < 0.6  THEN 'OCULTO'
            ELSE 'ARCHIVADO' END AS estado,
       now() - (random() * interval '365 days') AS publicado_en,
       1 + floor(random() * 35)::int AS w1,
       1 + floor(random() * 35)::int AS w2,
       1 + floor(random() * 35)::int AS w3
FROM generate_series(1, 40000) g;

INSERT INTO publicacion (id, autor_id, tipo, titulo, contenido, estado, publicado_en, creado_en, actualizado_en)
SELECT p.id, u.id, p.tipo,
       initcap(v1.palabra) || ' de ' || v2.palabra || ' ' || p.n,
       'Una descripcion de prueba sobre ' || v1.palabra || ', ' || v2.palabra || ' y ' || v3.palabra
           || '. Escrita para medir la busqueda de texto completo con datos representativos.',
       p.estado, p.publicado_en, p.publicado_en, p.publicado_en
FROM p
JOIN u ON u.n = p.autor_n
JOIN vocabulario v1 ON v1.n = p.w1
JOIN vocabulario v2 ON v2.n = p.w2
JOIN vocabulario v3 ON v3.n = p.w3;

INSERT INTO publicacion_proyecto (publicacion_id, resumen, estado_proyecto, busca_colaboradores)
SELECT id, 'Resumen del proyecto ' || n,
       (ARRAY['IDEA','EN_DESARROLLO','ESTABLE','PAUSADO'])[1 + floor(random() * 4)::int],
       random() < 0.3
FROM p WHERE tipo = 'PROYECTO';

INSERT INTO publicacion_discusion (publicacion_id, categoria)
SELECT id, (ARRAY['PREGUNTA','DEBATE','ANUNCIO','RECURSO'])[1 + floor(random() * 4)::int]
FROM p WHERE tipo = 'DISCUSION';

-- Entre 1 y 5 etiquetas, con sesgo hacia las tecnologias mas populares.
INSERT INTO publicacion_tecnologia (publicacion_id, tecnologia_id)
SELECT DISTINCT p.id, (1 + floor(power(random(), 2) * 20))::smallint
FROM p CROSS JOIN LATERAL generate_series(1, 1 + floor(random() * 5)::int)
ON CONFLICT DO NOTHING;

-- Toda publicacion no publicada tiene su transicion en el historial (regla R5).
INSERT INTO historial_estado_publicacion (publicacion_id, estado_anterior, estado_nuevo, actor_id, motivo, creado_en)
SELECT p.id, 'PUBLICADO', p.estado, (SELECT id FROM u WHERE n = 1),
       'Transicion generada para datos de prueba representativos.', p.publicado_en + interval '1 day'
FROM p WHERE p.estado <> 'PUBLICADO';

-- ----------------------------------------------------------------------------
-- 3. Comentarios: 250 000 (150 000 raiz + 100 000 respuestas)
--    Muy sesgados: un 1 % de las publicaciones concentra buena parte del total.
-- ----------------------------------------------------------------------------
INSERT INTO comentario (publicacion_id, autor_id, contenido, creado_en)
SELECT p.id, u.id, 'Comentario de prueba ' || g, p.publicado_en + (random() * interval '30 days')
FROM (SELECT g, 1 + floor(power(random(), 4) * 40000)::int AS np,
                1 + floor(random() * 5000)::int AS nu
      FROM generate_series(1, 150000) g) s
JOIN p ON p.n = s.np
JOIN u ON u.n = s.nu;

INSERT INTO comentario (publicacion_id, autor_id, comentario_padre_id, profundidad, contenido, creado_en)
SELECT r.publicacion_id, u.id, r.id, 1, 'Respuesta de prueba', r.creado_en + interval '1 hour'
FROM (SELECT id, publicacion_id, creado_en, 1 + floor(random() * 5000)::int AS nu
      FROM (SELECT id, publicacion_id, creado_en FROM comentario ORDER BY random() LIMIT 100000) x) r
JOIN u ON u.n = r.nu;

-- ----------------------------------------------------------------------------
-- 4. Mensajeria: 20 000 conversaciones directas, 400 000 mensajes
--    Concentrada: los usuarios de numero bajo tienen muchas conversaciones.
-- ----------------------------------------------------------------------------
CREATE TEMP TABLE c AS
SELECT row_number() OVER () AS n, gen_random_uuid() AS id, a, b
FROM (
    SELECT DISTINCT least(x.id, y.id) AS a, greatest(x.id, y.id) AS b
    FROM (SELECT 1 + floor(power(random(), 3) * 5000)::int AS nx,
                 1 + floor(random() * 5000)::int AS ny
          FROM generate_series(1, 21000)) s
    JOIN u x ON x.n = s.nx
    JOIN u y ON y.n = s.ny
    WHERE x.id <> y.id
) pares
LIMIT 20000;

INSERT INTO conversacion (id, creada_por, clave_directa, creada_en)
SELECT id, a, a::text || ':' || b::text, now() - interval '300 days' FROM c;

INSERT INTO participante_conversacion (conversacion_id, usuario_id, ultimo_leido_en, unido_en)
SELECT id, a, now() - (random() * interval '30 days'), now() - interval '300 days' FROM c
UNION ALL
SELECT id, b, now() - (random() * interval '30 days'), now() - interval '300 days' FROM c;

INSERT INTO mensaje (conversacion_id, remitente_id, contenido, enviado_en)
SELECT c.id, CASE WHEN random() < 0.5 THEN c.a ELSE c.b END,
       'Mensaje de prueba ' || g, now() - (random() * interval '300 days')
FROM (SELECT g, 1 + floor(power(random(), 2) * (SELECT count(*) FROM c))::int AS nc
      FROM generate_series(1, 400000) g) s
JOIN c ON c.n = s.nc;

UPDATE conversacion co SET ultimo_mensaje_en = m.ultimo
FROM (SELECT conversacion_id, max(enviado_en) AS ultimo FROM mensaje GROUP BY conversacion_id) m
WHERE co.id = m.conversacion_id;

-- ----------------------------------------------------------------------------
-- 5. Moderacion: 3 000 reportes, 10 % pendientes
--    Reportan desarrolladores; resuelven los moderadores (n <= 20), nunca el
--    mismo que reporto (ck_reporte_no_autorreporte).
-- ----------------------------------------------------------------------------
INSERT INTO reporte_moderacion (reportante_id, publicacion_id, motivo, estado, moderador_id, resolucion, creado_en, resuelto_en)
SELECT rep.id, p.id,
       (ARRAY['SPAM','ACOSO','CONTENIDO_INAPROPIADO','PLAGIO','OTRA'])[1 + floor(random() * 5)::int],
       CASE WHEN g <= 300 THEN (ARRAY['ABIERTO','EN_REVISION'])[1 + (g % 2)]
            ELSE 'RESUELTO_DESESTIMADO' END,
       CASE WHEN g <= 300 THEN NULL ELSE mod.id END,
       CASE WHEN g <= 300 THEN NULL ELSE 'Resuelto en los datos de prueba representativos.' END,
       now() - (random() * interval '200 days'),
       CASE WHEN g <= 300 THEN NULL ELSE now() END
FROM (SELECT g, 1 + floor(random() * 40000)::int AS np,
                21 + floor(random() * 4980)::int AS nrep,
                1 + floor(random() * 20)::int AS nmod
      FROM generate_series(1, 3000) g) s
JOIN p       ON p.n   = s.np
JOIN u rep   ON rep.n = s.nrep
JOIN u mod   ON mod.n = s.nmod;

-- ----------------------------------------------------------------------------
-- 6. Metricas diarias: un año
-- ----------------------------------------------------------------------------
INSERT INTO metricas_diarias (fecha, usuarios_nuevos, usuarios_activos, publicaciones_nuevas, comentarios_nuevos, mensajes_enviados)
SELECT d::date, floor(random() * 30)::int, 200 + floor(random() * 400)::int,
       floor(random() * 150)::int, floor(random() * 900)::int, floor(random() * 1500)::int
FROM generate_series(current_date - 364, current_date, interval '1 day') d;

-- Estadisticas al dia: sin ANALYZE el planificador decide con estimaciones vacias.
ANALYZE;

SELECT 'usuario' AS tabla, count(*) FROM usuario
UNION ALL SELECT 'publicacion', count(*) FROM publicacion
UNION ALL SELECT 'publicacion_tecnologia', count(*) FROM publicacion_tecnologia
UNION ALL SELECT 'comentario', count(*) FROM comentario
UNION ALL SELECT 'mensaje', count(*) FROM mensaje
UNION ALL SELECT 'seguimiento', count(*) FROM seguimiento
UNION ALL SELECT 'reporte_moderacion', count(*) FROM reporte_moderacion
UNION ALL SELECT 'metricas_diarias', count(*) FROM metricas_diarias;
