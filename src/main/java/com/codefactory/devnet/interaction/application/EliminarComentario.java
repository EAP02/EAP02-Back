package com.codefactory.devnet.interaction.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.interaction.domain.CodigoErrorInteraccion;
import com.codefactory.devnet.interaction.domain.Comentario;
import com.codefactory.devnet.interaction.domain.PoliticaComentario;
import com.codefactory.devnet.interaction.domain.RepositorioComentarios;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.shared.audit.EventoAuditoria;
import com.codefactory.devnet.shared.audit.RegistroAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * HU-13, criterios 3 y 4: eliminar un comentario.
 *
 * <p>El autor elimina el suyo y quien tenga {@code comentario:eliminar-ajeno}
 * (ADMIN) elimina cualquiera. Eliminar es pasar a ELIMINADO: el comentario deja de
 * mostrarse pero la fila se conserva.</p>
 */
@Service
public class EliminarComentario {

    private final RepositorioComentarios comentarios;
    private final UsuarioDirectorio directorio;
    private final RegistroAuditoria auditoria;

    public EliminarComentario(RepositorioComentarios comentarios,
                              UsuarioDirectorio directorio,
                              RegistroAuditoria auditoria) {
        this.comentarios = comentarios;
        this.directorio = directorio;
        this.auditoria = auditoria;
    }

    @Transactional
    public void ejecutar(Comando comando) {
        UsuarioDirectorio.UsuarioResumen actor = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion"));

        // Un comentario ya eliminado (u oculto) responde 404: "deja de mostrarse"
        // significa que, para el cliente, ya no existe.
        Comentario actual = comentarios.porId(comando.comentarioId())
                .filter(Comentario::estaVisible)
                .orElseThrow(() -> new ExcepcionNegocio(CodigoErrorInteraccion.COMENTARIO_NO_ENCONTRADO));

        boolean puedeEliminarAjenos = actor.tienePermiso(PoliticaComentario.PERMISO_ELIMINAR_AJENOS);

        if (!PoliticaComentario.puedeEliminar(actual.autorId(), actor.id(), puedeEliminarAjenos)) {
            // La auditoria escribe en su propia transaccion: el registro sobrevive al
            // rollback que provoca la excepcion de abajo.
            auditoria.registrar(EventoAuditoria.accesoDenegado(
                    "DELETE /api/v1/comentarios/" + comando.comentarioId(), actor.id(), comando.ipOrigen()));
            throw PoliticaComentario.rechazo();
        }

        comentarios.marcarEliminado(actual.id());

        // Si el autor elimina lo suyo no hay nada que rendir; si es un administrador
        // sobre contenido ajeno, queda auditado.
        if (PoliticaComentario.esIntervencionDeAdministrador(actual.autorId(), actor.id())) {
            auditoria.registrar(new EventoAuditoria(
                    "comentario",
                    EventoAuditoria.Operacion.DELETE,
                    actual.id().toString(),
                    actor.id(),
                    null,
                    null,
                    comando.ipOrigen()));
        }
    }

    public record Comando(UUID comentarioId, String ipOrigen) { }
}
