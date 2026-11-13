-- ============================================================================
--  V10 - Auditoria por triggers en tablas criticas
--
--  Entregable de Bases de Datos del sprint 3 (§3.7) y §3.2 "auditoria y
--  trazabilidad de cambios relevantes"; §5.4 admite triggers "para auditoria".
--
--  QUE se audita aqui y que NO. La aplicacion ya escribe en auditoria, con
--  actor_id y trace_id, todo lo que pasa por la API: eventos de seguridad (V2,
--  V5, V8), la edicion y el archivado de proyectos (HU-08) y el borrado de
--  comentarios ajenos (HU-13). Un trigger sobre esas tablas duplicaria cada
--  fila, y §5.4 prohibe duplicar reglas sin una fuente de verdad.
--
--  Lo que la aplicacion NO ve son los cambios que no pasan por la API. Hoy no hay
--  endpoint para asignar roles, cambiar permisos, suspender o cambiar el estado
--  o el MFA de un usuario: se hacen por consola SQL (ver docs/pruebas-swagger.md).
--  Son justo los cambios mas sensibles, y solo un trigger los captura:
--
--      usuario_rol    quien tiene que rol (escalada de privilegios)
--      rol_permiso    que puede hacer cada rol
--      suspension     sanciones aplicadas o levantadas
--      usuario        SOLO estado y mfa_habilitado
--
--  Datos sensibles (§5.3): de usuario solo se guardan id, estado y
--  mfa_habilitado. Nunca clave_hash, mfa_secreto, correo ni nombre.
--
--  Como un cambio por consola no tiene actor_id ni trace_id, se anade
--  auditoria.usuario_bd: el rol de base de datos que hizo el cambio
--  (devnet_app = la API; cualquier otro = alguien en consola o una migracion).
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. Quien hizo el cambio, a nivel de base de datos
-- ----------------------------------------------------------------------------
-- DEFAULT current_user: la aplicacion no nombra la columna en su INSERT
-- (RegistroAuditoriaJdbc), asi que tambien sus filas quedan con devnet_app sin
-- tocar codigo.
--
-- En dos pasos a proposito: con ADD COLUMN ... DEFAULT current_user, las filas
-- historicas recibirian el usuario que ejecuta ESTA migracion, que es falso.
-- Asi quedan en NULL, que se lee "anterior a V10, origen no registrado".

ALTER TABLE auditoria ADD COLUMN usuario_bd text;
ALTER TABLE auditoria ALTER COLUMN usuario_bd SET DEFAULT current_user;

COMMENT ON COLUMN auditoria.usuario_bd IS
    'Rol de base de datos que origino el evento (V10). Distingue la API (devnet_app) de un cambio hecho por consola o por migracion, que no tienen actor_id. NULL en filas anteriores a V10.';


-- ----------------------------------------------------------------------------
-- 2. Funcion generica de auditoria
-- ----------------------------------------------------------------------------
-- Argumentos del trigger (TG_ARGV), porque una funcion sirve para las cuatro tablas:
--   [0] columnas de la clave, separadas por coma -> registro_id ("a:b" si es compuesta)
--   [1] columna con el actor de la aplicacion, o '' si la tabla no tiene
--   [2] columnas a conservar, separadas por coma, o '' para conservar todas
--
-- La fila se convierte a jsonb y se filtra por nombre de columna: lo que no esta
-- en la lista blanca nunca llega a la tabla de auditoria.

CREATE OR REPLACE FUNCTION fn_auditar_cambio()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    claves      text[] := string_to_array(TG_ARGV[0], ',');
    col_actor   text   := NULLIF(TG_ARGV[1], '');
    conservar   text[] := string_to_array(NULLIF(TG_ARGV[2], ''), ',');
    antes       jsonb;
    despues     jsonb;
    fila        jsonb;
    registro    text;
    actor       uuid;
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        antes := to_jsonb(OLD);
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        despues := to_jsonb(NEW);
    END IF;

    -- Lista blanca de columnas, si la hay
    IF conservar IS NOT NULL THEN
        SELECT jsonb_object_agg(k, v) INTO antes
          FROM jsonb_each(antes) AS e(k, v) WHERE k = ANY (conservar);
        SELECT jsonb_object_agg(k, v) INTO despues
          FROM jsonb_each(despues) AS e(k, v) WHERE k = ANY (conservar);
    END IF;

    -- La fila de referencia: la nueva, o la borrada si es un DELETE
    fila := COALESCE(to_jsonb(NEW), to_jsonb(OLD));

    SELECT string_agg(fila ->> c, ':' ORDER BY ord) INTO registro
      FROM unnest(claves) WITH ORDINALITY AS u(c, ord);

    IF col_actor IS NOT NULL THEN
        actor := (fila ->> col_actor)::uuid;
    END IF;

    INSERT INTO auditoria (tabla, operacion, registro_id, actor_id, datos_antes, datos_despues)
    VALUES (TG_TABLE_NAME, TG_OP, registro, actor, antes, despues);

    RETURN NULL;   -- AFTER: el valor de retorno se ignora
END;
$$;

COMMENT ON FUNCTION fn_auditar_cambio() IS
    'Auditoria generica por trigger (V10). Argumentos: columnas clave, columna actor y lista blanca de columnas. Solo en tablas que la API no audita.';


-- ----------------------------------------------------------------------------
-- 3. Triggers
-- ----------------------------------------------------------------------------

-- Asignar o retirar un rol. El registro (HU-01) asigna DESARROLLADOR y tambien
-- queda: es la primera fila de la historia de privilegios de la cuenta.
CREATE TRIGGER tg_auditar_usuario_rol
    AFTER INSERT OR UPDATE OR DELETE ON usuario_rol
    FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio('usuario_id,rol_id', 'asignado_por', '');

-- Que puede hacer cada rol. Las migraciones de permisos (V6, V7) ya pasaron;
-- las siguientes quedaran registradas con el usuario_bd del migrador.
CREATE TRIGGER tg_auditar_rol_permiso
    AFTER INSERT OR UPDATE OR DELETE ON rol_permiso
    FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio('rol_id,permiso_id', '', '');

-- Sanciones: aplicarlas, levantarlas o modificarlas.
CREATE TRIGGER tg_auditar_suspension
    AFTER INSERT OR UPDATE OR DELETE ON suspension
    FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio('id', 'aplicada_por', '');

-- Usuario: solo cambios de estado o de MFA. El WHEN deja fuera los UPDATE de
-- cada inicio de sesion (intentos_fallidos, ultimo_acceso_en, bloqueado_hasta),
-- que la aplicacion ya audita como eventos de seguridad y que inundarian la tabla.
CREATE TRIGGER tg_auditar_usuario
    AFTER UPDATE OF estado, mfa_habilitado ON usuario
    FOR EACH ROW
    WHEN (OLD.estado IS DISTINCT FROM NEW.estado
       OR OLD.mfa_habilitado IS DISTINCT FROM NEW.mfa_habilitado)
    EXECUTE FUNCTION fn_auditar_cambio('id', '', 'id,estado,mfa_habilitado');
