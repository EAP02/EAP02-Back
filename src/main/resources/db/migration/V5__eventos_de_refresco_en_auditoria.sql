-- ============================================================================
--  V5 - Registrar los eventos del ciclo de refresco en auditoria
--
--  ADR-004: el JWT de acceso dura 15 minutos y no se puede revocar; la revocacion
--  opera sobre el token de refresco. Ese mecanismo solo sirve de algo si deja
--  rastro, y el lineamiento 6.2 exige "registrar eventos de seguridad".
--
--  De los tres eventos que se anaden, REFRESCO_REUSADO es el importante: significa
--  que un token de refresco llego dos veces y no hay forma de saber si fue el
--  titular repitiendo una peticion o alguien con la cookie copiada. Ante la duda se
--  revoca la familia entera.
--
--  Sin esta migracion el INSERT de ese evento violaria ck_auditoria_operacion, y
--  RegistroAuditoriaJdbc captura la excepcion y la deja en un log.error para no
--  tumbar la peticion. El resultado seria que el evento de seguridad mas relevante
--  del sistema se pierde EN SILENCIO. Es el mismo motivo por el que existe la V2.
-- ============================================================================

ALTER TABLE auditoria DROP CONSTRAINT ck_auditoria_operacion;

ALTER TABLE auditoria ADD CONSTRAINT ck_auditoria_operacion CHECK (operacion IN (
    -- Cambios de datos (V1)
    'INSERT',
    'UPDATE',
    'DELETE',
    -- Eventos de seguridad (V2, HU-06)
    'ACCESO_DENEGADO',
    'INICIO_SESION',
    'INICIO_SESION_FALLIDO',
    'CUENTA_BLOQUEADA',
    -- Ciclo de vida de la sesion (V5, ADR-004)
    'REFRESCO_ROTADO',
    'REFRESCO_REUSADO',
    'CIERRE_SESION'
));


-- ----------------------------------------------------------------------------
-- El indice se recrea, no se anade uno nuevo
--
-- ix_auditoria_seguridad es parcial y enumera en su WHERE los eventos que sirve.
-- Un evento nuevo que no aparezca ahi queda fuera del indice, y la consulta de "que
-- esta pasando con las sesiones" volveria a recorrer la tabla entera.
--
-- Recrearlo en vez de crear otro mantiene el recuento de indices parciales, que es
-- lo que EsquemaIT verifica.
--
-- REFRESCO_ROTADO se queda FUERA a proposito: es el camino feliz y ocurre cada
-- quince minutos por sesion activa. Incluirlo convertiria el indice parcial en uno
-- casi total y encareceria la escritura sin que nadie consulte esa cola.
-- ----------------------------------------------------------------------------

DROP INDEX ix_auditoria_seguridad;

CREATE INDEX ix_auditoria_seguridad
    ON auditoria (operacion, creado_en DESC)
    WHERE operacion IN (
        'ACCESO_DENEGADO',
        'INICIO_SESION_FALLIDO',
        'CUENTA_BLOQUEADA',
        'REFRESCO_REUSADO'
    );

COMMENT ON INDEX ix_auditoria_seguridad IS
    'Cola de eventos de seguridad consultables: accesos denegados, fallos de autenticacion, bloqueos y reusos de token de refresco. REFRESCO_ROTADO se excluye por ser el camino normal.';