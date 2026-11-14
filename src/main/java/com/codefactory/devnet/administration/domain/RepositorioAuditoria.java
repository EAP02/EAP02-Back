package com.codefactory.devnet.administration.domain;

/** Puerto de lectura del registro de auditoria. La escritura es de {@code shared.audit}. */
public interface RepositorioAuditoria {

    /** Del mas reciente al mas antiguo; a igual instante, por id descendente (orden estable). */
    Pagina<RegistroAuditado> buscar(FiltroAuditoria filtro, Paginacion paginacion);
}
