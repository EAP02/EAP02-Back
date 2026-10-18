-- Un mensaje solo puede atribuirse a un participante de su propia conversacion.
ALTER TABLE mensaje
    ADD CONSTRAINT fk_mensaje_remitente_participante
    FOREIGN KEY (conversacion_id, remitente_id)
    REFERENCES participante_conversacion (conversacion_id, usuario_id);

-- Evento de envio sin contenido; los accesos denegados usan el evento existente.
ALTER TABLE auditoria DROP CONSTRAINT ck_auditoria_operacion;
ALTER TABLE auditoria ADD CONSTRAINT ck_auditoria_operacion CHECK (operacion IN (
    'INSERT', 'UPDATE', 'DELETE', 'ACCESO_DENEGADO', 'INICIO_SESION',
    'INICIO_SESION_FALLIDO', 'CUENTA_BLOQUEADA', 'REFRESCO_ROTADO',
    'REFRESCO_REUSADO', 'CIERRE_SESION', 'MENSAJE_ENVIADO'
));
