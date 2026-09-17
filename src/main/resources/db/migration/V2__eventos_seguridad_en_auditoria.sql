-- ============================================================================
--  V2 - Registrar eventos de seguridad en auditoria
--
--  HU-06 (AB#15), criterio 1: "Un usuario sin permiso que intenta una accion de
--  moderacion recibe error 403 y QUEDA REGISTRADO EL INTENTO".
--
--  auditoria.operacion solo admitia INSERT, UPDATE y DELETE, porque V1 la diseno
--  para cambios de datos. Un intento de acceso denegado no es ninguno de los tres:
--  precisamente no cambio nada. Forzarlo dentro de INSERT haria ilegible la tabla.
--
--  Se amplia el dominio de la columna con los eventos de seguridad que el
--  lineamiento 6.2 exige registrar.
-- ============================================================================

ALTER TABLE auditoria DROP CONSTRAINT ck_auditoria_operacion;

ALTER TABLE auditoria ADD CONSTRAINT ck_auditoria_operacion CHECK (operacion IN (
    -- Cambios de datos (V1)
    'INSERT',
    'UPDATE',
    'DELETE',
    -- Eventos de seguridad (HU-06)
    'ACCESO_DENEGADO',
    'INICIO_SESION',
    'INICIO_SESION_FALLIDO',
    'CUENTA_BLOQUEADA'
));

COMMENT ON COLUMN auditoria.operacion IS
    'Cambio de datos (INSERT/UPDATE/DELETE) o evento de seguridad. Los de seguridad no modifican filas: registran quien intento que y con que resultado.';

-- En un evento de seguridad, registro_id guarda el recurso que se intento alcanzar
-- (la ruta HTTP), no una clave primaria. Por eso la columna es text y no uuid.
COMMENT ON COLUMN auditoria.registro_id IS
    'Clave del registro afectado; en eventos de seguridad, la ruta que se intento alcanzar.';

-- Consulta de la cola de intentos denegados: "quien esta tocando puertas cerradas".
-- Parcial, porque los eventos de seguridad son una minoria permanente frente a los
-- cambios de datos y solo se consultan entre ellos.
CREATE INDEX ix_auditoria_seguridad
    ON auditoria (operacion, creado_en DESC)
    WHERE operacion IN ('ACCESO_DENEGADO', 'INICIO_SESION_FALLIDO', 'CUENTA_BLOQUEADA');