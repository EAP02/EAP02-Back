package com.codefactory.devnet.discussion.application;

import com.codefactory.devnet.discussion.domain.CategoriaDiscusion;
import com.codefactory.devnet.discussion.domain.CodigoErrorDiscusion;
import com.codefactory.devnet.discussion.domain.Discusion;
import com.codefactory.devnet.discussion.domain.RepositorioDiscusiones;
import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.project.api.CatalogoTecnologiasConsulta;
import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HU-11: crear una discusion.
 *
 * <p>Los criterios de aceptacion de la creacion se cumplen aqui:</p>
 * <ol>
 *   <li>con titulo, cuerpo y etiquetas validas, la discusion queda publicada;</li>
 *   <li>si falta un campo obligatorio, se rechaza con el error correspondiente;</li>
 *   <li>queda asociada al miembro que la creo: el autor sale <b>siempre</b> del token.</li>
 * </ol>
 *
 * <p>Que quede "visible con su autor" y con su hilo lo resuelve {@link ConsultarDiscusion}.</p>
 *
 * <p><b>Sin reputacion minima.</b> {@code application.yml} define
 * {@code devnet.reputacion.minima-para-discusion}, pero la HU no lo pide y hoy casi no hay
 * forma de ganar reputacion: aplicarlo impediria a casi cualquier miembro abrir una
 * discusion. Decision de equipo; queda anotado como pendiente.</p>
 */
@Service
public class CrearDiscusion {

    private final RepositorioDiscusiones discusiones;
    private final UsuarioDirectorio directorio;
    private final CatalogoTecnologiasConsulta catalogo;

    public CrearDiscusion(RepositorioDiscusiones discusiones,
                          UsuarioDirectorio directorio,
                          CatalogoTecnologiasConsulta catalogo) {
        this.discusiones = discusiones;
        this.directorio = directorio;
        this.catalogo = catalogo;
    }

    @Transactional
    public Resultado ejecutar(Comando comando) {

        // Criterio 3. No existe un campo autorId en la peticion: no hay nada que
        // suplantar.
        UsuarioDirectorio.UsuarioResumen autor = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion"));

        // Una cuenta suspendida conserva su token hasta que caduca (ADR-004); esta
        // comprobacion contra la base cierra esa ventana, como en PublicarProyecto.
        if (!directorio.estaActivo(autor.id())) {
            throw new ExcepcionNegocio(CodigoErrorDiscusion.DISCUSION_AUTOR_SIN_PERMISO_DE_ESCRITURA);
        }

        // Criterio 2: forma, en el dominio, con todos los fallos juntos.
        Discusion discusion = Discusion.abrir(
                autor.id(),
                comando.titulo(),
                comando.cuerpo(),
                comando.categoria(),
                comando.tecnologias());

        // Criterio 1: "etiquetas validas". Que existan y esten aprobadas lo sabe el
        // catalogo, que es de project; se consulta por su contrato publico.
        exigirEtiquetasAprobadas(discusion.tecnologias());

        discusiones.guardar(discusion);

        return new Resultado(discusion.id(), discusion.titulo(), discusion.publicadoEn());
    }

    private void exigirEtiquetasAprobadas(Set<Short> etiquetas) {
        Set<Short> aprobadas = catalogo.aprobadas().stream()
                .map(CatalogoTecnologiasConsulta.TecnologiaPublica::id)
                .collect(Collectors.toSet());

        if (!aprobadas.containsAll(etiquetas)) {
            throw new ExcepcionNegocio(
                    CodigoErrorDiscusion.DISCUSION_TECNOLOGIA_DESCONOCIDA,
                    CodigoErrorDiscusion.DISCUSION_TECNOLOGIA_DESCONOCIDA.mensajePorDefecto(),
                    List.of(DetalleError.de("tecnologias", etiquetas, "no_aprobadas_o_inexistentes")));
        }
    }

    /** @param categoria opcional; si es nula, DEBATE */
    public record Comando(String titulo, String cuerpo, CategoriaDiscusion categoria, Set<Short> tecnologias) { }

    public record Resultado(UUID id, String titulo, Instant publicadoEn) { }
}
