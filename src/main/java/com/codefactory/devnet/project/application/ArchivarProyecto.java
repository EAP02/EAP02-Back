package com.codefactory.devnet.project.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.project.domain.CodigoErrorProyecto;
import com.codefactory.devnet.project.domain.PoliticaProyecto;
import com.codefactory.devnet.project.domain.Proyecto;
import com.codefactory.devnet.project.domain.RepositorioProyectos;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.shared.audit.EventoAuditoria;
import com.codefactory.devnet.shared.audit.RegistroAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * HU-08 (AB#18): eliminar un proyecto, que en DevNet significa archivarlo (regla R3).
 *
 * <p>Los dos criterios de la eliminacion se cumplen aqui:</p>
 * <ol>
 *   <li>HU-08.3: el autor elimina su proyecto, y la eliminacion exige una confirmacion
 *       previa: escribir el titulo exacto ({@link Proyecto#archivar});</li>
 *   <li>HU-08.4: quien no es el autor ni moderador no puede eliminarlo, y el intento
 *       queda registrado en auditoria.</li>
 * </ol>
 *
 * <p><b>Por que archivar y no borrar.</b> Lo decidio el equipo siguiendo la regla R3 de
 * {@code proceso-principal.md}: el proyecto desaparece de toda lectura publica (todas
 * filtran por {@code PUBLICADO}) pero la fila se conserva, y la transicion queda en
 * {@code historial_estado_publicacion} con quien la hizo. Un borrado fisico se llevaria
 * por delante, en cascada, el propio historial que explica que paso.</p>
 *
 * <p>Mismo orden de pasos que {@link EditarProyecto}, por la misma razon: a quien no
 * tiene derecho a eliminar no se le pide confirmacion ni se le dice si la escribio
 * bien.</p>
 */
@Service
public class ArchivarProyecto {

    static final String MOTIVO_AUTOR = "Eliminado por su autor.";
    static final String MOTIVO_MODERACION = "Eliminado por moderacion.";

    private final RepositorioProyectos proyectos;
    private final UsuarioDirectorio directorio;
    private final RegistroAuditoria auditoria;

    public ArchivarProyecto(RepositorioProyectos proyectos,
                            UsuarioDirectorio directorio,
                            RegistroAuditoria auditoria) {
        this.proyectos = proyectos;
        this.directorio = directorio;
        this.auditoria = auditoria;
    }

    @Transactional
    public void ejecutar(Comando comando) {

        UsuarioDirectorio.UsuarioResumen actor = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion"));

        Proyecto actual = proyectos.paraEditar(comando.proyectoId())
                .orElseThrow(() -> new ExcepcionNegocio(CodigoErrorProyecto.PROYECTO_NO_ENCONTRADO));

        // HU-08.4. La misma politica que la edicion: autor o moderador.
        boolean puedeModerar = actor.tienePermiso(PoliticaProyecto.PERMISO_MODERAR);
        if (!PoliticaProyecto.puedeModificar(actual.autorId(), actor.id(), puedeModerar)) {
            auditoria.registrar(EventoAuditoria.accesoDenegado(
                    "POST /api/v1/proyectos/" + comando.proyectoId() + "/archivado",
                    actor.id(), comando.ipOrigen()));
            throw PoliticaProyecto.rechazoPara(actual);
        }

        // HU-08.3. Estado y confirmacion se validan en el dominio.
        Proyecto archivado = actual.archivar(comando.confirmacionTitulo());

        boolean esModeracion = PoliticaProyecto.esIntervencionDeModeracion(actual.autorId(), actor.id());

        // Regla R5: la transicion y su historial se escriben en la misma transaccion.
        proyectos.transicionarEstado(
                archivado.id(),
                archivado.estadoPublicacion().name(),
                actor.id(),
                esModeracion ? MOTIVO_MODERACION : MOTIVO_AUTOR);

        // Igual que al editar: un moderador que retira contenido ajeno deja rastro
        // tambien en la auditoria general, no solo en el historial de la publicacion.
        if (esModeracion) {
            auditoria.registrar(new EventoAuditoria(
                    "publicacion",
                    EventoAuditoria.Operacion.UPDATE,
                    archivado.id().toString(),
                    actor.id(),
                    null,
                    null,
                    comando.ipOrigen()));
        }
    }

    /**
     * @param confirmacionTitulo el titulo del proyecto, escrito por quien elimina
     * @param ipOrigen           solo para auditoria; la resuelve el controlador
     */
    public record Comando(UUID proyectoId, String confirmacionTitulo, String ipOrigen) { }
}
