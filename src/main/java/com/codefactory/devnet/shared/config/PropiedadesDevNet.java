package com.codefactory.devnet.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuracion propia de DevNet, agrupada bajo el prefijo {@code devnet}.
 *
 * <p>Todo valor sensible llega por variable de entorno y nunca se versiona.
 * Ver {@code .env.example} y el lineamiento 7.3.</p>
 *
 * <p><b>Vive en {@code shared} y no en {@code config} a proposito.</b> Los modulos de
 * negocio necesitan leer estos valores, y la regla 4 de {@code FronterasModularesTest}
 * prohibe que {@code shared} dependa de un modulo: colgarla del kernel hace que la
 * dependencia solo pueda ir de los modulos hacia aqui. Cuando estaba en {@code config}
 * esa direccion era una costumbre, no un contrato, y ya se rompio una vez: un
 * {@code @Configuration} que inicializaba datos de referencia importo
 * {@code identity.infrastructure} y cerro el ciclo {@code config <-> identity}.</p>
 */
@ConfigurationProperties(prefix = "devnet")
public record PropiedadesDevNet(
        Seguridad seguridad,
        Reputacion reputacion,
        Almacenamiento almacenamiento
) {

    /**
     * @param origenesPermitidos lista blanca de CORS. Vacia en produccion si no hay
     *                           cliente web propio: el perfil avanzado no contempla
     *                           frontend.
     * @param vigenciaAcceso     duracion del JWT de acceso. Corta a proposito: no se
     *                           puede revocar, la revocacion opera sobre el refresco.
     * @param vigenciaRefresco   duracion del token de refresco.
     * @param intentosMaximos    fallos consecutivos antes de bloquear la cuenta.
     * @param bloqueo            cuanto dura el bloqueo. Sin expiracion periodica de
     *                           contrasena, conforme al lineamiento 3.4.
     */
    public record Seguridad(
            List<String> origenesPermitidos,
            Duration vigenciaAcceso,
            Duration vigenciaRefresco,
            int intentosMaximos,
            Duration bloqueo
    ) { }

    /**
     * Umbrales de reputacion que habilitan privilegios.
     *
     * <p>Son configuracion y no constantes porque la calibracion correcta solo se
     * conoce con datos de uso, y ajustarla no deberia exigir un despliegue.</p>
     */
    public record Reputacion(
            int minimaParaDiscusion,
            int minimaParaMensajeADesconocido
    ) { }

    /**
     * @param tamanoMaximoBytes  tope por archivo subido
     * @param tiposPermitidos    tipos MIME aceptados. Se valida el contenido, no la
     *                           extension del nombre.
     * @param vigenciaUrlFirmada cuanto vive una URL de lectura
     */
    public record Almacenamiento(
            long tamanoMaximoBytes,
            List<String> tiposPermitidos,
            Duration vigenciaUrlFirmada
    ) { }
}