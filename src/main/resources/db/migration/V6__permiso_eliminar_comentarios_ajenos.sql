-- ============================================================================
--  V6 - Permiso para eliminar comentarios ajenos
--
--  HU-13, criterio 4: "los comentarios pueden ser eliminados por administradores".
--
--  V1 concede comentario:crear y comentario:editar (propios) a DESARROLLADOR y
--  MODERADOR, pero ningun permiso a ADMIN sobre comentarios. Se anade uno especifico
--  en vez de reutilizar publicacion:moderar: ocultar una publicacion y eliminar un
--  comentario ajeno son acciones distintas, y asi se puede conceder la una sin la otra.
--
--  Solo ADMIN, que es lo que dice la historia. Si el equipo decide que MODERADOR
--  tambien debe poder, basta una fila mas en rol_permiso.
-- ============================================================================

INSERT INTO permiso (codigo, descripcion) VALUES
    ('comentario:eliminar-ajeno', 'Eliminar comentarios de otros usuarios');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r
JOIN permiso p ON p.codigo = 'comentario:eliminar-ajeno'
WHERE r.codigo = 'ADMIN';
