package com.codefactory.devnet.messaging.api;

import com.codefactory.devnet.messaging.application.ConsultarMensajeria;
import com.codefactory.devnet.messaging.application.EnviarMensaje;
import com.codefactory.devnet.messaging.domain.Mensaje;
import com.codefactory.devnet.messaging.domain.RepositorioMensajeria;
import com.codefactory.devnet.messaging.domain.CodigoErrorMensajeria;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.shared.api.IpCliente;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Mensajeria", description = "Mensajes privados y bandeja de entrada")
@SecurityRequirement(name = "bearerAuth")
public class MensajeriaController {
    private final EnviarMensaje enviar;
    private final ConsultarMensajeria consultar;

    public MensajeriaController(EnviarMensaje enviar, ConsultarMensajeria consultar) {
        this.enviar = enviar;
        this.consultar = consultar;
    }

    @PostMapping("/mensajes")
    @PreAuthorize("hasAuthority('mensaje:enviar')")
    @Operation(summary = "Enviar mensaje privado", description = "El remitente sale del token. La pareja comparte una unica conversacion directa.")
    public ResponseEntity<Mensaje> enviar(@RequestBody PeticionEnviar peticion) {
        if (peticion == null) {
            throw new ExcepcionNegocio(CodigoErrorMensajeria.MENSAJE_DESTINATARIO_INVALIDO);
        }
        Mensaje mensaje = enviar.ejecutar(peticion.destinatarioId(), peticion.contenido());
        return ResponseEntity.created(URI.create("/api/v1/conversaciones/" + mensaje.conversacionId() + "/mensajes"))
                .body(mensaje);
    }

    @GetMapping("/conversaciones")
    @Operation(summary = "Mi bandeja", description = "Solo conversaciones propias, ordenadas por ultimo mensaje descendente. Una bandeja vacia devuelve [].")
    public List<RepositorioMensajeria.ConversacionResumen> bandeja() {
        return consultar.bandeja();
    }

    @GetMapping("/conversaciones/{id}/mensajes")
    @Operation(summary = "Historial privado", description = "Solo participantes. Un tercero recibe 404 y el intento queda auditado.")
    public List<Mensaje> historial(@PathVariable UUID id, HttpServletRequest request) {
        return consultar.historial(id, IpCliente.de(request));
    }

    public record PeticionEnviar(UUID destinatarioId, String contenido) { }
}
