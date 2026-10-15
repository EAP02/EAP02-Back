-- ============================================================================
--  DevNet - Roles de base de datos y privilegios
--
--  ESTO NO ES UNA MIGRACION DE FLYWAY. Es aprovisionamiento del entorno: se
--  ejecuta UNA VEZ por proyecto Supabase, con el rol administrativo (postgres),
--  sobre una base que ya tiene el esquema aplicado.
--
--  Por que fuera de Flyway: las migraciones describen el ESQUEMA, que es igual
--  en todos los entornos. Los roles y sus contrasenas son propios de cada
--  entorno y no pueden versionarse en el repositorio.
--
--  Lineamientos 5.3: "usar cuentas de servicio con minimo privilegio, separar
--  funciones administrativas y rotar credenciales gestionadas como secretos".
--  ADR-003 fija el reparto de privilegios.
--
--  QUIEN USA CADA ROL
--    devnet_migrador  Flyway, al arrancar la aplicacion (spring.flyway.user en
--                     application-prod.yml, variables FLYWAY_USERNAME/PASSWORD).
--                     Es el propietario de todos los objetos del esquema.
--    devnet_app       La aplicacion en ejecucion (DATABASE_USERNAME/PASSWORD).
--                     Lee y escribe datos; no puede crear ni alterar objetos.
--    devnet_lectura   Uso manual, para analisis y sustentacion. No ve
--                     credenciales ni mensajes privados.
--
--  PROCEDIMIENTO (verificado de punta a punta en el sprint 2 con la aplicacion
--  real en perfil prod: ver docs/bd/README.md)
--    1. Sustituir los marcadores <...> por valores generados, nunca elegidos
--       a mano. Por ejemplo:  openssl rand -base64 32
--    2. Ejecutarlo en el SQL Editor de Supabase. Va entre BEGIN y COMMIT: si
--       algo falla, no se aplica nada y se puede corregir y repetir.
--    3. En Render, cambiar las variables y redesplegar:
--         DATABASE_USERNAME / DATABASE_PASSWORD  -> devnet_app
--         FLYWAY_USERNAME   / FLYWAY_PASSWORD    -> devnet_migrador
--       Con el pooler de Supabase el usuario lleva el sufijo del proyecto:
--       devnet_app.<project-ref> y devnet_migrador.<project-ref>, igual que
--       hoy postgres.<project-ref>.
--    4. Comprobar /actuator/health y una operacion de escritura (por ejemplo,
--       un inicio de sesion, que escribe en auditoria).
--    5. Borrar este archivo del historial de la terminal despues de ejecutarlo.
--
--    Mientras FLYWAY_USERNAME no este definida, Flyway usa las credenciales del
--    datasource: aplicar el paso 3 a medias (solo DATABASE_*) dejaria a Flyway
--    sin permiso para la siguiente migracion. Las cuatro variables van juntas.
--
--  NUNCA versionar este archivo con las contrasenas sustituidas.
-- ============================================================================


-- Todo o nada: si una sentencia falla, no queda ningun rol creado a medias.
BEGIN;

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

-- Quien ejecuta el script tiene que ser MIEMBRO de devnet_migrador, y antes de
-- la seccion 4: tanto ALTER DEFAULT PRIVILEGES FOR ROLE devnet_migrador como
-- ceder la propiedad de objetos (seccion 4b) lo exigen. Un superusuario no lo
-- necesita, por eso no fallo en las pruebas locales; en Supabase el rol postgres
-- NO es superusuario, y sin esta linea el script se detiene con
-- "permission denied to change default privileges".
GRANT devnet_migrador TO CURRENT_USER;


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
-- 4b. Propiedad de los objetos existentes
--
--    Anadido en el sprint 2 tras probar este script contra una base con V1 a V5:
--    sin esta seccion, la siguiente migracion que altere una tabla existente
--    falla con "must be owner of table ...". En PostgreSQL solo el PROPIETARIO
--    puede hacer ALTER TABLE, y las tablas de V1 a V5 las creo el rol
--    administrativo, no devnet_migrador. Los privilegios por defecto de la
--    seccion 4 solo cubren los objetos que devnet_migrador cree a partir de ahora.
--
--    Incluye flyway_schema_history: Flyway escribe en ella en cada migracion.
--
--    En Supabase el rol administrativo (postgres) no es superusuario: para ceder
--    la propiedad tiene que ser miembro del rol que la recibe. Esa membresia se
--    concede en la seccion 1.
-- ----------------------------------------------------------------------------

DO $$
DECLARE
    objeto record;
BEGIN
    -- Solo lo que es del rol que ejecuta el script y no pertenece a una extension.
    -- En Supabase, public suele tener funciones de extensiones (propiedad de
    -- supabase_admin): intentar cederlas abortaria todo el script.
    FOR objeto IN
        SELECT format('ALTER TABLE %I.%I OWNER TO devnet_migrador', n.nspname, c.relname) AS sentencia
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r', 'p')
          AND pg_get_userbyid(c.relowner) = CURRENT_USER
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype = 'e')
        UNION ALL
        SELECT format('ALTER SEQUENCE %I.%I OWNER TO devnet_migrador', n.nspname, c.relname)
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind = 'S'
          AND pg_get_userbyid(c.relowner) = CURRENT_USER
          -- Las de columnas IDENTITY cambian de propietario con su tabla.
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype IN ('e', 'i', 'a'))
        UNION ALL
        SELECT format('ALTER VIEW %I.%I OWNER TO devnet_migrador', n.nspname, c.relname)
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind = 'v'
          AND pg_get_userbyid(c.relowner) = CURRENT_USER
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype = 'e')
        UNION ALL
        SELECT format('ALTER FUNCTION %s OWNER TO devnet_migrador', p.oid::regprocedure)
        FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND pg_get_userbyid(p.proowner) = CURRENT_USER
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = p.oid AND d.deptype = 'e')
    LOOP
        EXECUTE objeto.sentencia;
    END LOOP;
END $$;



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


COMMIT;


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
