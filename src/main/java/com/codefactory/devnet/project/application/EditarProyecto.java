package com.codefactory.devnet.project.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.project.domain.CodigoErrorProyecto;
import com.codefactory.devnet.project.domain.EstadoProyecto;
import com.codefactory.devnet.project.domain.PoliticaProyecto;
import com.codefactory.devnet.project.domain.Proyecto;
import com.codefactory.devnet.project.domain.RepositorioProyectos;
import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.shared.audit.EventoAuditoria;
import com.codefactory.devnet.shared.audit.RegistroAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * HU-08 (AB#18): editar un proyecto.
 *
 * <p>Los dos criterios de aceptacion de la edicion se cumplen aqui:</p>
 * <ol>
 *   <li>HU-08.1 (AB#39): el autor edita su proyecto y los cambios se persisten;</li>
 *   <li>HU-08.2 (AB#40): quien no es el autor ni moderador no puede editarlo, y el
 *       intento queda registrado en auditoria, como pide HU-06 para todo acceso
 *       denegado.</li>
 * </ol>
 *
 * <p>El orden de los pasos importa y esta pensado para no revelar mas de lo debido:
 * primero quien eres, despues si puedes tocar este proyecto, y solo entonces si lo que
 * envias es valido. A quien no tiene derecho a editar no se le informa de errores de
 * formato de un proyecto que no es suyo.</p>
 */
@Service
public class EditarProyecto {

    private final RepositorioProyectos proyectos;
    private final UsuarioDirectorio directorio;
    private final RegistroAuditoria auditoria;

    public EditarProyecto(RepositorioProyectos proyectos,
                          UsuarioDirectorio directorio,
                          RegistroAuditoria auditoria) {
        this.proyectos = proyectos;
        this.directorio = directorio;
        this.auditoria = auditoria;
    }

    @Transactional
    public Resultado ejecutar(Comando comando) {

        // La identidad sale SIEMPRE del token, nunca de la ruta ni del cuerpo.
        UsuarioDirectorio.UsuarioResumen actor = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion"));

        Proyecto actual = proyectos.paraEditar(comando.proyectoId())
                .orElseThrow(() -> new ExcepcionNegocio(CodigoErrorProyecto.PROYECTO_NO_ENCONTRADO));

        // Criterio 2 (HU-08.2). El permiso del token dice si el rol edita proyectos;
        // la politica, si puede editar ESTE.
        boolean puedeModerar = actor.tienePermiso(PoliticaProyecto.PERMISO_MODERAR);
        if (!PoliticaProyecto.puedeModificar(actual.autorId(), actor.id(), puedeModerar)) {
            // La auditoria escribe en su propia transaccion: el registro sobrevive al
            // rollback que provoca la excepcion de abajo.
            auditoria.registrar(EventoAuditoria.accesoDenegado(
                    "PUT /api/v1/proyectos/" + comando.proyectoId(), actor.id(), comando.ipOrigen()));
            throw PoliticaProyecto.rechazoPara(actual);
        }

        // Criterio 1 (HU-08.1). Estado y forma se validan en el dominio, con las
        // mismas reglas que al publicar.
        Proyecto editado = actual.editar(
                comando.titulo(),
                comando.descripcion(),
                comando.resumen(),
                comando.tecnologias(),
                comando.urlRepositorio(),
                comando.estadoProyecto(),
                comando.licencia(),
                comando.urlDemo(),
                comando.buscaColaboradores());

        // Estas dos necesitan la base, asi que no pueden vivir en el dominio. Son las
        // mismas comprobaciones que en PublicarProyecto.
        if (!proyectos.tecnologiasValidas(editado.tecnologias())) {
            throw new ExcepcionNegocio(
                    CodigoErrorProyecto.PROYECTO_TECNOLOGIA_DESCONOCIDA,
                    CodigoErrorProyecto.PROYECTO_TECNOLOGIA_DESCONOCIDA.mensajePorDefecto(),
                    List.of(DetalleError.de("tecnologias", editado.tecnologias(), "no_aprobadas_o_inexistentes")));
        }

        if (proyectos.existeRepositorioConUrlEnOtroProyecto(editado.urlRepositorio(), editado.id())) {
            throw new ExcepcionNegocio(
                    CodigoErrorProyecto.PROYECTO_REPOSITORIO_DUPLICADO,
                    CodigoErrorProyecto.PROYECTO_REPOSITORIO_DUPLICADO.mensajePorDefecto(),
                    List.of(DetalleError.de("urlRepositorio", editado.urlRepositorio(), "ya_enlazado")));
        }

        proyectos.actualizar(editado);

        // Un moderador que cambia contenido ajeno ejerce un poder sobre el trabajo de
        // otra persona: debe quedar constancia de quien lo hizo y sobre que. Cuando el
        // autor edita lo suyo no hay nada que rendir.
        if (PoliticaProyecto.esIntervencionDeModeracion(actual.autorId(), actor.id())) {
            auditoria.registrar(new EventoAuditoria(
                    "publicacion",
                    EventoAuditoria.Operacion.UPDATE,
                    editado.id().toString(),
                    actor.id(),
                    null,
                    null,
                    comando.ipOrigen()));
        }

        return Resultado.de(editado);
    }

    /**
     * Lo que el cliente quiere que sea el proyecto. Reemplazo completo, como en
     * {@code PUT}.
     *
     * @param ipOrigen solo para auditoria; la resuelve el controlador
     */
    public record Comando(
            UUID proyectoId,
            String titulo,
            String descripcion,
            String resumen,
            Set<Short> tecnologias,
            String urlRepositorio,
            EstadoProyecto estadoProyecto,
            String licencia,
            String urlDemo,
            boolean buscaColaboradores,
            String ipOrigen
    ) { }

    /**
     * El proyecto tal como quedo. Devuelve los valores ya normalizados (espacios
     * recortados, resumen derivado...), para que el cliente vea exactamente lo que se
     * guardo y no lo que envio.
     */
    public record Resultado(
            UUID id,
            UUID autorId,
            String titulo,
            String descripcion,
            String resumen,
            Set<Short> tecnologias,
            String urlRepositorio,
            EstadoProyecto estadoProyecto,
            String licencia,
            String urlDemo,
            boolean buscaColaboradores,
            Instant publicadoEn
    ) {
        static Resultado de(Proyecto p) {
            return new Resultado(p.id(), p.autorId(), p.titulo(), p.descripcion(), p.resumen(),
                    p.tecnologias(), p.urlRepositorio(), p.estadoProyecto(), p.licencia(),
                    p.urlDemo(), p.buscaColaboradores(), p.publicadoEn());
        }
    }
}
