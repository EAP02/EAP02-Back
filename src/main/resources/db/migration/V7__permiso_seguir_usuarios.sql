-- ============================================================================
--  V7 - Permiso para seguir a otros usuarios
--
--  HU-15: "seguir a otros miembros".
--
--  V1 ya trae la tabla seguimiento (clave primaria compuesta y CHECK contra seguirse
--  a si mismo), pero ningun permiso para usarla. Se anade uno propio, usuario:seguir,
--  en vez de reutilizar perfil:editar: seguir a alguien no edita ningun perfil.
--
--  Lo reciben DESARROLLADOR y MODERADOR, igual que reaccion:crear (las capacidades
--  sociales). ADMIN y VISITANTE no: un visitante es de solo lectura.
-- ============================================================================

INSERT INTO permiso (codigo, descripcion) VALUES
    ('usuario:seguir', 'Seguir y dejar de seguir a otros usuarios');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r
JOIN permiso p ON p.codigo = 'usuario:seguir'
WHERE r.codigo IN ('DESARROLLADOR', 'MODERADOR');
