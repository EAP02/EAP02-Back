package com.codefactory.devnet.project.application;

import com.codefactory.devnet.project.domain.CodigoErrorProyecto;
import com.codefactory.devnet.project.domain.RepositorioProyectos;
import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * HU-06 (AB#15), criterio 2: "un moderador puede ejecutar las acciones permitidas a
 * su rol".
 *
 * <p>Esta es esa accion. Retirar de la vista publica un proyecto es competencia
 * exclusiva del permiso {@code publicacion:moderar}, que solo conceden los roles
 * MODERADOR y ADMIN.</p>
 *
 * <p>Es deliberadamente minima: el modulo completo de moderacion (reportes, revision,
 * apelaciones, suspensiones) esta fuera del alcance de este sprint. Lo que HU-06 pide
 * es diferenciar permisos y verificarlos en el servidor, no construir el flujo
 * completo.</p>
 */
@Service
public class OcultarPublicacion {

    private static final String ESTADO_OCULTO = "OCULTO";
    private static final int MOTIVO_MINIMO = 20;

    private final RepositorioProyectos proyectos;
    private final UsuarioDirectorio directorio;

    public OcultarPublicacion(RepositorioProyectos proyectos,
                              UsuarioDirectorio directorio) {
        this.proyectos = proyectos;
        this.directorio = directorio;
    }

    @Transactional
    public void ejecutar(UUID publicacionId, String motivo) {

        UsuarioDirectorio.UsuarioResumen moderador = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion"));

        // No hay resolucion silenciosa: ocultar contenido ajeno exige justificarlo por
        // escrito, y ese texto queda en el historial para una eventual apelacion.
        String motivoLimpio = motivo == null ? "" : motivo.strip();
        if (motivoLimpio.length() < MOTIVO_MINIMO) {
            throw new ExcepcionNegocio(
                    CodigoErrorProyecto.PROYECTO_DATOS_INVALIDOS,
                    "Ocultar una publicacion exige un motivo de al menos %d caracteres."
                            .formatted(MOTIVO_MINIMO),
                    List.of(DetalleError.de("motivo", motivoLimpio.length(),
                            "longitud_minima_" + MOTIVO_MINIMO)));
        }

        boolean existia = proyectos.transicionarEstado(
                publicacionId, ESTADO_OCULTO, moderador.id(), motivoLimpio);

        if (!existia) {
            throw new ExcepcionNegocio(CodigoErrorProyecto.PROYECTO_NO_ENCONTRADO);
        }

    }
}
