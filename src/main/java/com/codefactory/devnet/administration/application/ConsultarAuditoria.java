package com.codefactory.devnet.administration.application;

import com.codefactory.devnet.administration.domain.FiltroAuditoria;
import com.codefactory.devnet.administration.domain.Pagina;
import com.codefactory.devnet.administration.domain.Paginacion;
import com.codefactory.devnet.administration.domain.RangoFechas;
import com.codefactory.devnet.administration.domain.RegistroAuditado;
import com.codefactory.devnet.administration.domain.RepositorioAuditoria;
import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HU-20: consulta del registro de auditoria. Solo administradores.
 *
 * <p>La autorizacion vive en el controlador ({@code auditoria:consultar}); aqui solo se
 * valida la consulta y se resuelve el nombre de quien actuo.</p>
 *
 * <p>El nombre pertenece a identity: se pide por {@link UsuarioDirectorio}, una vez por
 * actor distinto de la pagina (maximo 100 consultas, no una por fila). Si la cuenta ya no
 * existe, el nombre va nulo y el id se conserva: la auditoria debe sobrevivir al usuario
 * (ver el comentario de {@code auditoria.actor_id}).</p>
 */
@Service
public class ConsultarAuditoria {

    private final RepositorioAuditoria auditoria;
    private final UsuarioDirectorio directorio;

    public ConsultarAuditoria(RepositorioAuditoria auditoria, UsuarioDirectorio directorio) {
        this.auditoria = auditoria;
        this.directorio = directorio;
    }

    @Transactional(readOnly = true)
    public Pagina<Registro> ejecutar(String operacion, UUID actorId, String tabla,
                                     LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        // La validacion va primero: un rango invertido no debe llegar a la base.
        FiltroAuditoria filtro = FiltroAuditoria.de(operacion, actorId, tabla, new RangoFechas(desde, hasta));
        Paginacion paginacion = new Paginacion(pagina, tamano);

        Pagina<RegistroAuditado> filas = auditoria.buscar(filtro, paginacion);

        Map<UUID, String> nombres = nombresDe(filas);
        return filas.mapear(f -> new Registro(
                f.id(), f.creadoEn(), f.operacion(), f.tabla(), f.registroId(),
                f.actorId(), f.actorId() == null ? null : nombres.get(f.actorId()),
                f.datosAntes(), f.datosDespues(), f.traceId(), f.ip(), f.usuarioBd()));
    }

    private Map<UUID, String> nombresDe(Pagina<RegistroAuditado> filas) {
        Map<UUID, String> nombres = new HashMap<>();
        filas.contenido().stream()
                .map(RegistroAuditado::actorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .forEach(id -> directorio.buscar(id)
                        .ifPresent(u -> nombres.put(id, u.nombreUsuario())));
        return nombres;
    }

    /** Lo que ve el administrador: la fila de auditoria mas el nombre de quien actuo. */
    public record Registro(
            long id,
            Instant fecha,
            String accion,
            String tabla,
            String registroId,
            UUID actorId,
            String actorNombre,
            String datosAntes,
            String datosDespues,
            String traceId,
            String ip,
            String usuarioBd
    ) {
    }
}
