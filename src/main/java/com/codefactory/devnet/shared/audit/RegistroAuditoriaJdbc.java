package com.codefactory.devnet.shared.audit;

import com.codefactory.devnet.shared.api.FiltroTraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementacion sobre JDBC directo, no JPA.
 *
 * <p>Dos razones. La tabla usa {@code jsonb} e {@code inet}, que JPA no mapea sin
 * un convertidor propio; y la auditoria no es una entidad del dominio: es un
 * apendice tecnico que nadie consulta por identificador ni modifica.</p>
 *
 * <p>{@code REQUIRES_NEW} es lo importante aqui. Sin ello, el registro de un intento
 * denegado viajaria en la transaccion de la peticion y desapareceria con el rollback,
 * justo en el caso que HU-06 exige dejar registrado.</p>
 */
@Component
public class RegistroAuditoriaJdbc implements RegistroAuditoria {

    private static final Logger log = LoggerFactory.getLogger(RegistroAuditoriaJdbc.class);

    private static final String INSERTAR = """
            INSERT INTO auditoria
                (tabla, operacion, registro_id, actor_id, datos_antes, datos_despues, trace_id, ip)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::inet)
            """;

    private final JdbcTemplate jdbc;

    public RegistroAuditoriaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(EventoAuditoria evento) {
        try {
            jdbc.update(INSERTAR,
                    evento.tabla(),
                    evento.operacion().name(),
                    evento.registroId(),
                    evento.actorId(),
                    evento.datosAntes(),
                    evento.datosDespues(),
                    FiltroTraceId.actual(),
                    evento.ip());
        } catch (RuntimeException ex) {
            // Un fallo auditando no puede tumbar la operacion del usuario, pero
            // tampoco puede pasar en silencio: si la auditoria deja de escribir,
            // el equipo tiene que enterarse por el log y por la alerta.
            log.error("No se pudo registrar el evento de auditoria {} sobre {}",
                    evento.operacion(), evento.registroId(), ex);
        }
    }
}