package com.codefactory.devnet.shared.audit;

/**
 * Escribe en el registro de auditoria.
 *
 * <p>Lineamiento 5.3: "registrar auditoria para acciones criticas sin almacenar
 * secretos ni datos personales innecesarios en logs".</p>
 *
 * <p>La implementacion escribe en una transaccion propia: un evento de seguridad
 * debe quedar registrado aunque la operacion que lo provoco termine revertida. Un
 * intento de acceso denegado que desaparece con el rollback no sirve de nada.</p>
 */
public interface RegistroAuditoria {

    void registrar(EventoAuditoria evento);
}