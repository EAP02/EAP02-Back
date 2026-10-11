package com.codefactory.devnet.discussion.application;

import com.codefactory.devnet.discussion.domain.CategoriaDiscusion;
import com.codefactory.devnet.discussion.domain.CodigoErrorDiscusion;
import com.codefactory.devnet.discussion.domain.RepositorioDiscusiones;
import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.project.api.CatalogoTecnologiasConsulta;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HU-11: ver una discusion, con su autor y su hilo.
 *
 * <p>Cubre los criterios de lectura de la HU:</p>
 * <ul>
 *   <li>la discusion queda "visible con su autor": se devuelve su nombre de usuario,
 *       no solo un identificador;</li>
 *   <li>"existe un hilo con todos los usuarios que han comentado": el hilo existe desde
 *       que se crea la discusion, vacio, y lista a cada participante una sola vez.</li>
 * </ul>
 *
 * <p>Crear comentarios es otra HU. Este caso de uso solo lee los que existan, de modo
 * que cuando esa HU se implemente el hilo se llenara sin cambiar nada aqui.</p>
 *
 * <p>Lectura publica: un visitante tambien puede leer una discusion.</p>
 */
@Service
public class ConsultarDiscusion {

    private final RepositorioDiscusiones discusiones;
    private final UsuarioDirectorio directorio;
    private final CatalogoTecnologiasConsulta catalogo;

    public ConsultarDiscusion(RepositorioDiscusiones discusiones,
                              UsuarioDirectorio directorio,
                              CatalogoTecnologiasConsulta catalogo) {
        this.discusiones = discusiones;
        this.directorio = directorio;
        this.catalogo = catalogo;
    }

    @Transactional(readOnly = true)
    public DiscusionDetalle ejecutar(UUID discusionId) {
        RepositorioDiscusiones.DiscusionPublicada d = discusiones.publicadaPorId(discusionId)
                .orElseThrow(() -> new ExcepcionNegocio(CodigoErrorDiscusion.DISCUSION_NO_ENCONTRADA));

        // Las etiquetas se guardan por id; su nombre lo da el catalogo de project.
        Map<Short, CatalogoTecnologiasConsulta.TecnologiaPublica> porId = catalogo.aprobadas().stream()
                .collect(Collectors.toMap(CatalogoTecnologiasConsulta.TecnologiaPublica::id, Function.identity()));
        List<Etiqueta> etiquetas = d.tecnologias().stream()
                .map(porId::get)
                // Una tecnologia retirada del catalogo despues de etiquetar no se muestra:
                // no tiene nombre que ensenar ni filtro que la encuentre.
                .filter(Objects::nonNull)
                .map(t -> new Etiqueta(t.id(), t.nombre(), t.slug()))
                .toList();

        List<ParticipanteHilo> participantes = discusiones.participantes(discusionId).stream()
                .map(p -> new ParticipanteHilo(p.usuarioId(), nombreDe(p.usuarioId()), p.comentarios()))
                .toList();
        int totalComentarios = participantes.stream().mapToInt(ParticipanteHilo::comentarios).sum();

        return new DiscusionDetalle(
                d.id(),
                d.titulo(),
                d.cuerpo(),
                d.categoria(),
                d.resuelta(),
                etiquetas,
                new Autor(d.autorId(), nombreDe(d.autorId())),
                d.publicadoEn(),
                new Hilo(totalComentarios, participantes));
    }

    /**
     * El nombre pertenece a identity. Se pide una vez por persona: el repositorio ya
     * devuelve cada participante una sola vez.
     */
    private String nombreDe(UUID usuarioId) {
        return directorio.buscar(usuarioId)
                .map(UsuarioDirectorio.UsuarioResumen::nombreUsuario)
                .orElse(null);
    }

    public record DiscusionDetalle(
            UUID id,
            String titulo,
            String cuerpo,
            CategoriaDiscusion categoria,
            boolean resuelta,
            List<Etiqueta> tecnologias,
            Autor autor,
            Instant publicadoEn,
            Hilo hilo
    ) { }

    public record Autor(UUID id, String nombreUsuario) { }

    public record Etiqueta(short id, String nombre, String slug) { }

    /**
     * @param participantes cada usuario que ha comentado, una sola vez, por orden de su
     *                      primera intervencion. Vacio en una discusion recien creada.
     */
    public record Hilo(int totalComentarios, List<ParticipanteHilo> participantes) { }

    public record ParticipanteHilo(UUID usuarioId, String nombreUsuario, int comentarios) { }
}
