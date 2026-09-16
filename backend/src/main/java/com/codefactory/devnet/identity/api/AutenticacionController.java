package com.codefactory.devnet.identity.api;

import com.codefactory.devnet.identity.application.AutenticarUsuario;
import com.codefactory.devnet.shared.api.RespuestaError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Inicio de sesion con credenciales locales. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticacion", description = "Obtencion del token de acceso")
public class AutenticacionController {

    private final AutenticarUsuario autenticar;

    public AutenticacionController(AutenticarUsuario autenticar) {
        this.autenticar = autenticar;
    }

    @PostMapping("/inicio-sesion")
    @SecurityRequirements   // endpoint publico: no exige token
    @Operation(
            summary = "Iniciar sesion",
            description = """
                    Devuelve un token de acceso con vigencia de 15 minutos. Enviarlo en
                    cada peticion protegida como `Authorization: Bearer <token>`.

                    El token lleva los **permisos** efectivos del usuario, no sus roles.
                    Si el rol exige segundo factor y aun no esta inscrito, el token se
                    emite **sin permisos** y `mfaPendiente` viene en `true`.

                    Cinco intentos fallidos bloquean la cuenta 15 minutos. Todo intento,
                    exitoso o no, queda en el registro de auditoria.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticacion correcta"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas o cuenta bloqueada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "403", description = "Cuenta desactivada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<RespuestaInicioSesion> iniciarSesion(
            @Valid @RequestBody PeticionInicioSesion peticion,
            HttpServletRequest http) {

        AutenticarUsuario.Resultado resultado =
                autenticar.ejecutar(peticion.correo(), peticion.clave(), ipDe(http));

        return ResponseEntity.ok(new RespuestaInicioSesion(
                resultado.token().valor(),
                "Bearer",
                resultado.token().vigenciaSegundos(),
                resultado.usuario().id().toString(),
                resultado.usuario().nombreUsuario(),
                List.copyOf(resultado.usuario().codigosDeRol()),
                List.copyOf(resultado.usuario().permisosEfectivos()),
                resultado.mfaPendiente()));
    }

    /**
     * IP de origen.
     *
     * <p>Render termina TLS en su proxy, asi que la IP real llega en
     * {@code X-Forwarded-For}. Se toma solo el primer valor: el resto de la cadena lo
     * puede falsificar el cliente.</p>
     */
    private String ipDe(HttpServletRequest http) {
        String reenviada = http.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }

    // ------------------------------------------------------------------

    @Schema(name = "PeticionInicioSesion")
    public record PeticionInicioSesion(

            @NotBlank(message = "el correo es obligatorio")
            @Email(message = "el correo no tiene un formato valido")
            @Schema(example = "dev@devnet.test")
            String correo,

            @NotBlank(message = "la contrasena es obligatoria")
            @Schema(example = "Devnet2026!")
            String clave
    ) { }

    @Schema(name = "RespuestaInicioSesion")
    public record RespuestaInicioSesion(
            @Schema(description = "Token de acceso. Enviar como: Authorization: Bearer <token>")
            String token,
            String tipo,
            @Schema(description = "Segundos de vigencia", example = "900")
            long expiraEnSegundos,
            String usuarioId,
            String nombreUsuario,
            @Schema(description = "Informativo. La autorizacion NO se decide con esto.")
            List<String> roles,
            @Schema(description = "Permisos efectivos. Vacio si mfaPendiente es true.")
            List<String> permisos,
            @Schema(description = "El rol exige segundo factor y no esta inscrito")
            boolean mfaPendiente
    ) { }
}