-- ============================================================================
--  DevNet - Roles de base de datos y privilegios
--
--  ESTO NO ES UNA MIGRACION DE FLYWAY. Es aprovisionamiento del entorno: se
--  ejecuta UNA VEZ por proyecto Supabase, con un rol administrativo, antes de
--  la primera migracion.
--
--  Por que fuera de Flyway: las migraciones describen el ESQUEMA, que es igual
--  en todos los entornos. Los roles y sus contrasenas son propios de cada
--  entorno y no pueden versionarse en el repositorio.
--
--  Lineamientos 5.3: "usar cuentas de servicio con minimo privilegio, separar
--  funciones administrativas y rotar credenciales gestionadas como secretos".
--  ADR-003 fija el reparto de privilegios.
--
--  USO
--    1. Sustituir los marcadores <...> por valores generados, nunca elegidos
--       a mano. Por ejemplo:  openssl rand -base64 32
--    2. Ejecutar con el rol administrativo del proyecto.
--    3. Guardar las credenciales en el gestor de secretos del entorno:
--         devnet_migrador -> GitHub Secrets (solo el paso de migracion)
--         devnet_app      -> variables de entorno de Render
--         devnet_lectura  -> uso manual para analisis y sustentacion
--    4. Borrar este archivo del historial de la terminal despues de ejecutarlo.
--
--  NUNCA versionar este archivo con las contrasenas sustituidas.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. Creacion de roles
-- ----------------------------------------------------------------------------

-- Aplica las migraciones de Flyway. Es el unico que puede modificar el esquema.
CREATE ROLE devnet_migrador LOGIN PASSWORD '<CLAVE_MIGRADOR>';

-- Rol con el que se conecta la aplicacion en ejecucion.
-- NO puede crear, alterar ni eliminar objetos: si la aplicacion pudiera
-- modificar el esquema, ddl-auto=validate dejaria de ser una garantia.
CREATE ROLE devnet_app LOGIN PASSWORD '<CLAVE_APP>';

-- Solo lectura, para analisis y para la sustentacion.
CREATE ROLE devnet_lectura LOGIN PASSWORD '<CLAVE_LECTURA>';


-- ----------------------------------------------------------------------------
-- 2. Privilegios sobre el esquema
-- ----------------------------------------------------------------------------

GRANT CONNECT ON DATABASE postgres TO devnet_migrador, devnet_app, devnet_lectura;

GRANT USAGE, CREATE ON SCHEMA public TO devnet_migrador;
GRANT USAGE           ON SCHEMA public TO devnet_app, devnet_lectura;


-- ----------------------------------------------------------------------------
-- 3. Privilegios sobre los objetos existentes
--    Ejecutar DESPUES de aplicar V1, o volver a ejecutar tras cada migracion
--    que cree tablas nuevas (lo cubre tambien la seccion 4).
-- ----------------------------------------------------------------------------

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA public TO devnet_app;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA public TO devnet_app;
GRANT EXECUTE                        ON ALL FUNCTIONS IN SCHEMA public TO devnet_app;

GRANT SELECT                         ON ALL TABLES    IN SCHEMA public TO devnet_lectura;


-- ----------------------------------------------------------------------------
-- 4. Privilegios por defecto para los objetos FUTUROS
--    Sin esto, cada migracion que cree una tabla dejaria a la aplicacion sin
--    acceso a ella y el fallo aparecería en tiempo de ejecucion, no al migrar.
-- ----------------------------------------------------------------------------

ALTER DEFAULT PRIVILEGES FOR ROLE devnet_migrador IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO devnet_app;

ALTER DEFAULT PRIVILEGES FOR ROLE devnet_migrador IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO devnet_app;

ALTER DEFAULT PRIVILEGES FOR ROLE devnet_migrador IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO devnet_app;

ALTER DEFAULT PRIVILEGES FOR ROLE devnet_migrador IN SCHEMA public
    GRANT SELECT ON TABLES TO devnet_lectura;


-- ----------------------------------------------------------------------------
-- 5. Restricciones adicionales sobre datos sensibles
--
--    devnet_lectura NO debe ver credenciales ni contenido privado. Se le retira
--    el acceso a las tablas correspondientes; si en el futuro necesita alguna
--    de ellas, se crea una vista que excluya las columnas sensibles.
--    Ver la clasificacion de datos en docs/bd/03-diccionario-datos.md.
-- ----------------------------------------------------------------------------

REVOKE SELECT ON usuario, token_refresco, mensaje FROM devnet_lectura;

-- Vista sin columnas secretas, para analisis sobre usuarios.
CREATE OR REPLACE VIEW v_usuario_publico AS
SELECT id, nombre_usuario, estado, creado_en
FROM usuario;

GRANT SELECT ON v_usuario_publico TO devnet_lectura;


-- ----------------------------------------------------------------------------
-- 6. Verificacion
-- ----------------------------------------------------------------------------

-- Que privilegios tiene cada rol, por tabla
SELECT grantee, table_name, string_agg(privilege_type, ', ' ORDER BY privilege_type) AS privilegios
FROM information_schema.role_table_grants
WHERE table_schema = 'public'
  AND grantee IN ('devnet_migrador', 'devnet_app', 'devnet_lectura')
GROUP BY grantee, table_name
ORDER BY grantee, table_name;

-- Debe devolver CERO FILAS: la aplicacion no puede modificar el esquema.
SELECT 'devnet_app tiene CREATE sobre public' AS hallazgo
WHERE has_schema_privilege('devnet_app', 'public', 'CREATE');

-- Debe devolver CERO FILAS: lectura no ve credenciales ni mensajes privados.
SELECT 'devnet_lectura ve ' || t AS hallazgo
FROM unnest(ARRAY['usuario', 'token_refresco', 'mensaje']) AS t
WHERE has_table_privilege('devnet_lectura', t, 'SELECT');


-- ----------------------------------------------------------------------------
-- 7. Rotacion de credenciales
--    Al cierre de cada sprint, y de inmediato ante cualquier sospecha de
--    exposicion. La rotacion no requiere recrear el rol:
--
--      ALTER ROLE devnet_app WITH PASSWORD '<NUEVA_CLAVE>';
--
--    Actualizar despues la variable de entorno en Render y reiniciar el
--    servicio. Registrar la rotacion como evento en la bitacora del equipo.
-- ----------------------------------------------------------------------------