package com.codefactory.devnet.interaction.application;

import com.codefactory.devnet.interaction.domain.RepositorioReacciones;
import com.codefactory.devnet.interaction.domain.TipoReaccion;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** HU-14: cuantas reacciones tiene una publicacion (lectura publica). */
@Service
public class ConsultarReacciones {

    private final RepositorioReacciones reacciones;

    public ConsultarReacciones(RepositorioReacciones reacciones) {
        this.reacciones = reacciones;
    }

    @Transactional(readOnly = true)
    public Resumen ejecutar(UUID publicacionId) {
        if (!reacciones.publicacionReaccionable(publicacionId)) {
            throw ExcepcionNegocio.noEncontrado("publicacion", publicacionId);
        }
        return new Resumen(publicacionId, reacciones.total(publicacionId), reacciones.porTipo(publicacionId));
    }

    public record Resumen(UUID publicacionId, int total, Map<TipoReaccion, Integer> porTipo) { }
}
