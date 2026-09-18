-- ============================================================================
--  DevNet - Verificacion de la instalacion del esquema
--
--  Ejecutar DESPUES de aplicar V1__baseline.sql, en el SQL Editor de Supabase
--  o en psql. Cada bloque dice que resultado se espera.
--
--  Si algun bloque no da lo esperado, el esquema no quedo bien instalado.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. Conteo general      ESPERADO: tablas = 29, vistas = 0
-- ----------------------------------------------------------------------------
SELECT
    count(*) FILTER (WHERE table_type = 'BASE TABLE') AS tablas,
    count(*) FILTER (WHERE table_type = 'VIEW')       AS vistas
FROM information_schema.tables
WHERE table_schema = 'public';


-- ----------------------------------------------------------------------------
-- 2. Listado de tablas con su numero de columnas
--    ESPERADO: las 29 en orden alfabetico, desde auditoria hasta usuario_rol
-- ----------------------------------------------------------------------------
SELECT
    t.table_name AS tabla,
    count(c.column_name) AS columnas
FROM information_schema.tables t
JOIN information_schema.columns c
     ON c.table_schema = t.table_schema AND c.table_name = t.table_name
WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE'
GROUP BY t.table_name
ORDER BY t.table_name;


-- ----------------------------------------------------------------------------
-- 3. Indices declarados      ESPERADO: 22 con prefijo ix_ o ux_
--    (el total de pg_indexes es mayor porque incluye los automaticos de
--     PRIMARY KEY y UNIQUE: ver docs/bd/04-indices.md)
-- ----------------------------------------------------------------------------
SELECT
    count(*) FILTER (WHERE indexname LIKE 'ix\_%' OR indexname LIKE 'ux\_%') AS declarados,
    count(*)                                                                 AS total_incluyendo_automaticos
FROM pg_indexes
WHERE schemaname = 'public';


-- ----------------------------------------------------------------------------
-- 4. Indices parciales      ESPERADO: 10
--    Son la firma del modelo: casi toda consulta filtra por un estado.
-- ----------------------------------------------------------------------------
SELECT indexname, tablename
FROM pg_indexes
WHERE schemaname = 'public' AND indexdef LIKE '%WHERE%'
ORDER BY tablename, indexname;


-- ----------------------------------------------------------------------------
-- 5. Restricciones por tipo
--    ESPERADO aproximado: PRIMARY KEY 29, CHECK abundantes, FOREIGN KEY ~40
-- ----------------------------------------------------------------------------
SELECT
    CASE contype
        WHEN 'p' THEN 'PRIMARY KEY'
        WHEN 'f' THEN 'FOREIGN KEY'
        WHEN 'u' THEN 'UNIQUE'
        WHEN 'c' THEN 'CHECK'
    END AS tipo,
    count(*) AS cantidad
FROM pg_constraint con
JOIN pg_class rel   ON rel.oid = con.conrelid
JOIN pg_namespace n ON n.oid = rel.relnamespace
WHERE n.nspname = 'public'
GROUP BY contype
ORDER BY cantidad DESC;


-- ----------------------------------------------------------------------------
-- 6. Datos de referencia
--    ESPERADO:  roles 4 | permisos 20 | rol_permiso 40 | tecnologias 20
-- ----------------------------------------------------------------------------
SELECT 'rol'         AS tabla, count(*) AS filas, 4  AS esperado FROM rol
UNION ALL SELECT 'permiso',     count(*), 20 FROM permiso
UNION ALL SELECT 'rol_permiso', count(*), 40 FROM rol_permiso
UNION ALL SELECT 'tecnologia',  count(*), 20 FROM tecnologia;


-- ----------------------------------------------------------------------------
-- 7. Roles con MFA obligatorio      ESPERADO: MODERADOR y ADMIN
-- ----------------------------------------------------------------------------
SELECT codigo, exige_mfa, descripcion
FROM rol
ORDER BY id;


-- ----------------------------------------------------------------------------
-- 8. Permisos por rol      ESPERADO: VISITANTE 1 | DESARROLLADOR 13
--                                    MODERADOR 17 | ADMIN 9
-- ----------------------------------------------------------------------------
SELECT r.codigo AS rol, count(rp.permiso_id) AS permisos
FROM rol r
LEFT JOIN rol_permiso rp ON rp.rol_id = r.id
GROUP BY r.codigo, r.id
ORDER BY r.id;


-- ----------------------------------------------------------------------------
-- 9. La columna generada de busqueda funciona
--    ESPERADO: una fila con un tsvector no vacio, tipo 'texto_muestra'
-- ----------------------------------------------------------------------------
SELECT to_tsvector('spanish'::regconfig,
                   'Proyecto de arquitectura hexagonal con Spring Boot y PostgreSQL')
       AS texto_muestra;


-- ----------------------------------------------------------------------------
-- 10. PRUEBA FUNCIONAL COMPLETA
--     Inserta un usuario, su perfil, una publicacion de tipo proyecto con su
--     subtipo, y verifica que la columna generada de busqueda se llena sola.
--     Todo dentro de una transaccion que se revierte: NO deja datos.
--
--     ESPERADO: la consulta final devuelve 1 fila con coincide_busqueda = true
-- ----------------------------------------------------------------------------
BEGIN;

INSERT INTO usuario (id, correo, nombre_usuario, clave_hash)
VALUES ('11111111-1111-1111-1111-111111111111',
        'prueba@devnet.test', 'prueba_dev', '$argon2id$fake');

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT '11111111-1111-1111-1111-111111111111', id FROM rol WHERE codigo = 'DESARROLLADOR';

INSERT INTO perfil (usuario_id, nombre_completo, titular)
VALUES ('11111111-1111-1111-1111-111111111111',
        'Usuario de Prueba', 'Backend developer');

INSERT INTO publicacion (id, autor_id, tipo, titulo, contenido, estado, publicado_en)
VALUES ('22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111',
        'PROYECTO',
        'Motor de plantillas en Java',
        'Un motor de plantillas minimalista escrito en Java 21 con soporte para expresiones.',
        'PUBLICADO',
        now());

INSERT INTO publicacion_proyecto (publicacion_id, resumen, estado_proyecto, busca_colaboradores)
VALUES ('22222222-2222-2222-2222-222222222222',
        'Motor de plantillas ligero', 'EN_DESARROLLO', true);

INSERT INTO publicacion_tecnologia (publicacion_id, tecnologia_id)
SELECT '22222222-2222-2222-2222-222222222222', id FROM tecnologia WHERE slug = 'java';

INSERT INTO historial_estado_publicacion (publicacion_id, estado_anterior, estado_nuevo, actor_id, motivo)
VALUES ('22222222-2222-2222-2222-222222222222', 'BORRADOR', 'PUBLICADO',
        '11111111-1111-1111-1111-111111111111', 'Publicacion inicial');

SELECT
    p.titulo,
    pp.estado_proyecto,
    (SELECT count(*) FROM publicacion_tecnologia pt
      WHERE pt.publicacion_id = p.id)                         AS tecnologias,
    (SELECT count(*) FROM historial_estado_publicacion h
      WHERE h.publicacion_id = p.id)                          AS transiciones,
    (p.busqueda_tsv @@ websearch_to_tsquery('spanish', 'plantillas')) AS coincide_busqueda
FROM publicacion p
JOIN publicacion_proyecto pp ON pp.publicacion_id = p.id
WHERE p.id = '22222222-2222-2222-2222-222222222222';

ROLLBACK;   -- no deja rastro


-- ----------------------------------------------------------------------------
-- 11. PRUEBAS NEGATIVAS
--     Cada una DEBE FALLAR. Si alguna pasa, una restriccion no quedo aplicada.
--     Ejecutarlas de a una, leyendo el mensaje de error.
-- ----------------------------------------------------------------------------

-- 11a. Un borrador no puede tener fecha de publicacion  -> ck_publicacion_publicado_en
-- INSERT INTO publicacion (autor_id, tipo, titulo, contenido, estado, publicado_en)
-- VALUES ('11111111-1111-1111-1111-111111111111', 'PROYECTO',
--         'Titulo valido', 'Contenido suficientemente largo para pasar el check.',
--         'BORRADOR', now());

-- 11b. Nadie puede seguirse a si mismo  -> ck_seguimiento_no_reflexivo
-- INSERT INTO seguimiento (seguidor_id, seguido_id)
-- VALUES ('11111111-1111-1111-1111-111111111111',
--         '11111111-1111-1111-1111-111111111111');

-- 11c. Un reporte no puede apuntar a nada  -> ck_reporte_objetivo_exclusivo
-- INSERT INTO reporte_moderacion (reportante_id, motivo)
-- VALUES ('11111111-1111-1111-1111-111111111111', 'SPAM');

-- 11d. Una suspension automatica no puede tener actor  -> ck_suspension_autoria
-- INSERT INTO suspension (usuario_id, motivo, termina_en, aplicada_por, automatica)
-- VALUES ('11111111-1111-1111-1111-111111111111', 'prueba',
--         now() + interval '7 days', '11111111-1111-1111-1111-111111111111', true);

-- 11e. Un comentario raiz no puede tener profundidad > 0  -> ck_comentario_raiz
-- INSERT INTO comentario (publicacion_id, autor_id, contenido, profundidad)
-- VALUES ('22222222-2222-2222-2222-222222222222',
--         '11111111-1111-1111-1111-111111111111', 'hola', 2);


-- ----------------------------------------------------------------------------
-- 12. LIMPIEZA (opcional)
--     Deja el esquema vacio para que Flyway lo construya desde cero cuando
--     exista el backend. Ver la advertencia de docs/bd/README.md.
--
--     CUIDADO: borra TODO el esquema public.
-- ----------------------------------------------------------------------------
-- DROP SCHEMA public CASCADE;
-- CREATE SCHEMA public;
-- GRANT ALL ON SCHEMA public TO postgres, anon, authenticated, service_role;
