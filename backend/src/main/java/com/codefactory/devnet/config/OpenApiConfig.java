package com.codefactory.devnet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Contrato OpenAPI 3.1 de la API.
 *
 * <p>ADR-002: el contrato se <b>genera</b> desde el codigo, no se escribe a mano.
 * Las anotaciones de los controladores y los DTOs son la fuente de verdad, y el
 * pipeline exporta {@code openapi-v1.<release>.json} como artefacto versionado en
 * cada entrega.</p>
 *
 * <p>Sin frontend propio, Swagger UI es la superficie de demostracion del proyecto
 * (lineamiento 1.3 marca el frontend como N/A para el perfil avanzado). Por eso la
 * calidad del contrato importa mas de lo habitual: es lo que se muestra en la
 * sustentacion.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearerAuth";

    @Value("${devnet.api.url-servidor:http://localhost:8080}")
    private String urlServidor;

    @Value("${devnet.api.version:v1}")
    private String version;

    @Bean
    public OpenAPI contratoDevNet() {
        // Declarado una sola vez y referenciado desde cada endpoint: el pipeline
        // falla si un controlador declara un esquema de error propio (ADR-005).
        Schema<?> esquemaError = new Schema<>().$ref("#/components/schemas/RespuestaError");
        Content contenidoError = new Content()
                .addMediaType("application/json", new MediaType().schema(esquemaError));

        return new OpenAPI()
                .info(new Info()
                        .title("DevNet API")
                        .version(version)
                        .description("""
                                Red social para desarrolladores. Caso 13, CodeF@ctory UdeA.

                                **Errores.** Toda respuesta de error usa el mismo cuerpo, con
                                `errorCode`, `message`, `details` y `traceId` (ADR-005).
                                Programa contra `errorCode`, que es estable; el texto de
                                `message` puede cambiar sin aviso.

                                **Correlacion.** La cabecera `X-Trace-Id` viaja en todas las
                                respuestas, incluidas las exitosas. Si algo falla, reporta ese
                                valor.

                                **Paginacion.** Las colecciones ordenadas por tiempo paginan por
                                cursor, no por desplazamiento. Devuelve `cursorSiguiente` tal
                                cual en la peticion siguiente, sin interpretarlo.

                                **Versionado.** Anadir campos opcionales y endpoints no rompe
                                `v1`. Eliminar o renombrar campos, volver obligatorio un campo
                                de entrada o cambiar un codigo HTTP de exito exige `v2`.
                                """)
                        .contact(new Contact().name("Equipo avanzado - Arquitectura y Bases de Datos"))
                        .license(new License().name("Uso academico")))
                .servers(List.of(new Server().url(urlServidor).description("Servidor activo")))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("""
                                        Token de acceso con vigencia de 15 minutos. Se obtiene en
                                        `/api/v1/auth/inicio-sesion` y se renueva en
                                        `/api/v1/auth/refresco`, que lee el token de refresco de
                                        una cookie HttpOnly.
                                        """))
                        .addResponses("NoAutenticado", new ApiResponse()
                                .description("Falta autenticacion o el token no es valido")
                                .content(contenidoError))
                        .addResponses("AccesoDenegado", new ApiResponse()
                                .description("Autenticado pero sin permiso para esta operacion")
                                .content(contenidoError))
                        .addResponses("NoEncontrado", new ApiResponse()
                                .description("El recurso no existe, o existe pero no es visible para quien pregunta")
                                .content(contenidoError))
                        .addResponses("Conflicto", new ApiResponse()
                                .description("Conflicto con el estado actual del recurso o con datos existentes")
                                .content(contenidoError))
                        .addResponses("ReglaNegocio", new ApiResponse()
                                .description("Regla de negocio incumplible con independencia del estado")
                                .content(contenidoError)))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}