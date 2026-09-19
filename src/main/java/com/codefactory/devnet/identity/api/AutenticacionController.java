package com.codefactory.devnet.identity.api;

import com.codefactory.devnet.identity.application.AutenticarUsuario;
import com.codefactory.devnet.identity.application.CerrarSesion;
import com.codefactory.devnet.identity.application.RefrescarSesion;
import com.codefactory.devnet.identity.application.RegistrarUsuario;
import com.codefactory.devnet.identity.domain.EmisorTokens;
import com.codefactory.devnet.identity.domain.Usuario;
import com.codefactory.devnet.shared.api.IpCliente;
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
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private final RegistrarUsuario registrar;
    private final RefrescarSesion refrescar;
    private final CerrarSesion cerrar;
    private final CookieRefresco cookies;

    public AutenticacionController(AutenticarUsuario autenticar,
                                   RegistrarUsuario registrar,
                                   RefrescarSesion refrescar,
                                   CerrarSesion cerrar,
                                   CookieRefresco cookies) {
        this.autenticar = autenticar;
        this.registrar = registrar;
        this.refrescar = refrescar;
        this.cerrar = cerrar;
        this.cookies = cookies;
    }

    @PostMapping("/registro")
    @SecurityRequirements
    @Operation(summary = "Registrar una cuenta")
    public ResponseEntity<RegistrarUsuario.Resultado> registrar(
            @Valid @RequestBody PeticionRegistro peticion) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrar.ejecutar(peticion.correo(), peticion.clave(), peticion.nombreUsuario()));
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

                    Cinco intentos fallidos bloquean la cuenta 15 minutos.
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

        AutenticarUsuario.Resultado resultado = autenticar.ejecutar(
                peticion.correo(),
                peticion.clave(),
                IpCliente.de(http),
                http.getHeader(HttpHeaders.USER_AGENT));

        return conCookieDeRefresco(resultado.refrescoEnClaro())
                .body(cuerpo(resultado.token(), resultado.usuario(), resultado.mfaPendiente()));
    }

    @PostMapping("/refresco")
    @SecurityRequirements   // endpoint publico: la credencial es la cookie, no el Bearer
    @Operation(
            summary = "Renovar el token de acceso",
            description = """
                    Consume el token de refresco de la cookie `devnet_refresco` y devuelve
                    un token de acceso nuevo. **No lleva cuerpo de peticion**: la
                    credencial viaja en la cookie, que el navegador envia sola.

                    Cada llamada rota el refresco: el anterior queda consumido y se emite
                    otro de la misma familia. Un refresco se usa **una sola vez**.

                    Si llega uno ya consumido se revoca la familia entera y se responde
                    `AUTH_REFRESCO_REUSADO`. Es deliberado: no hay forma de distinguir una
                    peticion repetida de una cookie robada, asi que se cierra la sesion y
                    se obliga a volver a autenticarse.

                    Es tambien el punto donde se reevalua el estado de la cuenta. Una
                    cuenta desactivada deja de renovar aqui, como mucho quince minutos
                    despues de desactivarla.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token renovado"),
            @ApiResponse(responseCode = "401",
                    description = "Refresco ausente, invalido, expirado, revocado o reutilizado",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "403", description = "Cuenta desactivada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<RespuestaInicioSesion> refrescar(HttpServletRequest http) {

        RefrescarSesion.Resultado resultado = refrescar.ejecutar(
                cookies.leer(http).orElse(null),
                IpCliente.de(http),
                http.getHeader(HttpHeaders.USER_AGENT));

        return conCookieDeRefresco(resultado.refrescoEnClaro())
                .body(cuerpo(resultado.token(), resultado.usuario(), resultado.mfaPendiente()));
    }

    @PostMapping("/cierre-sesion")
    @SecurityRequirements
    @Operation(
            summary = "Cerrar sesion",
            description = """
                    Revoca la familia completa del token de refresco presentado y borra la
                    cookie. Las sesiones abiertas en otros dispositivos no se tocan.

                    **Siempre responde 204**, tambien si no habia cookie o si la sesion ya
                    estaba cerrada: en los tres casos el resultado deseado ya se cumple, y
                    responder distinto segun el caso revelaria si un token existe.

                    No exige token de acceso a proposito. Quien vuelve pasados quince
                    minutos lo tiene caducado y aun asi debe poder cerrar su sesion.
                    """)
    @ApiResponse(responseCode = "204", description = "Sesion cerrada")
    public ResponseEntity<Void> cerrarSesion(HttpServletRequest http) {
        cerrar.ejecutar(cookies.leer(http).orElse(null), IpCliente.de(http));

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expirar().toString())
                .build();
    }

    // ------------------------------------------------------------------

    /**
     * El refresco sale <b>solo</b> por {@code Set-Cookie}.
     *
     * <p>Devolverlo tambien en el JSON anularia el {@code HttpOnly}: cualquier script
     * de la pagina podria leerlo de la respuesta y guardarlo donde un XSS lo alcance.</p>
     */
    private ResponseEntity.BodyBuilder conCookieDeRefresco(String refrescoEnClaro) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.emitir(refrescoEnClaro).toString());
    }

    private RespuestaInicioSesion cuerpo(EmisorTokens.TokenAcceso token,
                                         Usuario usuario,
                                         boolean mfaPendiente) {
        return new RespuestaInicioSesion(
                token.valor(),
                "Bearer",
                token.vigenciaSegundos(),
                usuario.id().toString(),
                usuario.nombreUsuario(),
                List.copyOf(usuario.codigosDeRol()),
                List.copyOf(usuario.permisosEfectivos()),
                mfaPendiente);
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

    @Schema(name = "PeticionRegistro")
    public record PeticionRegistro(
            @NotBlank @Email String correo,
            @NotBlank @Size(min = 8, max = 100) String clave,
            @Size(min = 3, max = 40) String nombreUsuario
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
