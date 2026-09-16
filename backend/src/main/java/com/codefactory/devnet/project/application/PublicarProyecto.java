package com.codefactory.devnet.project.application;

import com.codefactory.devnet.project.domain.CodigoErrorProyecto;
import com.codefactory.devnet.project.domain.EstadoProyecto;
import com.codefactory.devnet.project.domain.Proyecto;
import com.codefactory.devnet.project.domain.RepositorioProyectos;
import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.shared.audit.EventoAuditoria;
import com.codefactory.devnet.shared.audit.RegistroAuditoria;
import com.codefactory.devnet.shared.integration.IPerfilConsulta;
import com.codefactory.devnet.shared.integration.IUsuarioDirectorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * HU-07 (AB#17): publicar un proyecto.
 *
 * <p>Los tres criterios de aceptacion se cumplen aqui:</p>
 * <ol>
 *   <li>con datos validos, el proyecto queda asociado al perfil del autor y visible
 *       en su lista;</li>
 *   <li>titulo y descripcion se validan y, si faltan, se rechaza la publicacion;</li>
 *   <li>solo el autor puede publicar en su nombre.</li>
 * </ol>
 */
@Service
public class PublicarProyecto {

    private final RepositorioProyectos proyectos;
    private final IPerfilConsulta perfiles;
    private final IUsuarioDirectorio directorio;
    private final RegistroAuditoria auditoria;

    public PublicarProyecto(RepositorioProyectos proyectos,
                            IPerfilConsulta perfiles,
                            IUsuarioDirectorio directorio,
                            RegistroAuditoria auditoria) {
        this.proyectos = proyectos;
        this.perfiles = perfiles;
        this.directorio = directorio;
        this.auditoria = auditoria;
    }

    @Transactional
    public Resultado ejecutar(Comando comando) {

        IUsuarioDirectorio.UsuarioResumen autor = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion"));

        // Criterio 3. El autor SIEMPRE sale del token; el campo del cuerpo solo se
        // usa para detectar el intento y rechazarlo con un 403 explicito. Si se
        // ignorara en silencio, un cliente creeria estar publicando por otro.
        Proyecto.exigirAutoriaPropia(comando.autorIdDeclarado(), autor.id());

        if (!directorio.estaActivo(autor.id())) {
            throw new ExcepcionNegocio(CodigoErrorProyecto.PROYECTO_AUTOR_SIN_PERMISO_DE_ESCRITURA);
        }

        // Criterio 1: el proyecto "queda asociado al perfil del autor", asi que el
        // perfil tiene que existir antes.
        perfiles.buscar(autor.id())
                .orElseThrow(() -> new ExcepcionNegocio(CodigoErrorProyecto.PROYECTO_PERFIL_INEXISTENTE));

        // Criterio 2: la validacion de forma ocurre en el dominio y acumula todos los
        // fallos, para que el cliente reciba de una vez todo lo que debe corregir.
        Proyecto proyecto = Proyecto.publicar(
                autor.id(),
                comando.titulo(),
                comando.descripcion(),
                comando.resumen(),
                comando.tecnologias(),
                comando.urlRepositorio(),
                comando.estadoProyecto(),
                comando.licencia(),
                comando.urlDemo(),
                comando.buscaColaboradores());

        // Estas dos si necesitan la base, asi que no pueden vivir en el dominio.
        if (!proyectos.tecnologiasValidas(proyecto.tecnologias())) {
            throw new ExcepcionNegocio(
                    CodigoErrorProyecto.PROYECTO_TECNOLOGIA_DESCONOCIDA,
                    CodigoErrorProyecto.PROYECTO_TECNOLOGIA_DESCONOCIDA.mensajePorDefecto(),
                    List.of(DetalleError.de("tecnologias", proyecto.tecnologias(), "no_aprobadas_o_inexistentes")));
        }

        // Se comprueba antes de insertar para devolver un 409 legible en vez de la
        // violacion de la restriccion UNIQUE, que saldria como error generico.
        if (proyectos.existeRepositorioConUrl(proyecto.urlRepositorio())) {
            throw new ExcepcionNegocio(
                    CodigoErrorProyecto.PROYECTO_REPOSITORIO_DUPLICADO,
                    CodigoErrorProyecto.PROYECTO_REPOSITORIO_DUPLICADO.mensajePorDefecto(),
                    List.of(DetalleError.de("urlRepositorio", proyecto.urlRepositorio(), "ya_enlazado")));
        }

        Proyecto guardado = proyectos.guardar(proyecto);

        // Publicar con repositorio enlazado acredita reputacion: es la senal de que
        // hay trabajo real detras y no solo una idea enunciada.
        if (guardado.urlRepositorio() != null) {
            perfiles.acreditar(autor.id(),
                    IPerfilConsulta.EventoReputacion.PROYECTO_CON_REPOSITORIO,
                    guardado.id());
        }

        auditoria.registrar(new EventoAuditoria(
                "publicacion",
                EventoAuditoria.Operacion.INSERT,
                guardado.id().toString(),
                autor.id(),
                null,
                null,
                null));

        return new Resultado(guardado.id(), guardado.titulo(), guardado.publicadoEn());
    }

    /**
     * @param autorIdDeclarado lo que el cliente dijo. Solo se usa para comprobar que
     *                         coincide con el autenticado; nunca para decidir el autor.
     */
    public record Comando(
            UUID autorIdDeclarado,
            String titulo,
            String descripcion,
            String resumen,
            Set<Short> tecnologias,
            String urlRepositorio,
            EstadoProyecto estadoProyecto,
            String licencia,
            String urlDemo,
            boolean buscaColaboradores
    ) { }

    public record Resultado(UUID id, String titulo, java.time.Instant publicadoEn) { }
}