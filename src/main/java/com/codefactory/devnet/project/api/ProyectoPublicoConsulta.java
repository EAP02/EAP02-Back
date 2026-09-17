package com.codefactory.devnet.project.api;

import java.util.List;
import java.util.UUID;

/** Servicio publico que el modulo project ofrece a profile. */
public interface ProyectoPublicoConsulta {
    List<ProyectoPublico> publicadosPor(UUID autorId);

    record ProyectoPublico(UUID id, String titulo, String descripcion, String repositorioUrl) { }
}
