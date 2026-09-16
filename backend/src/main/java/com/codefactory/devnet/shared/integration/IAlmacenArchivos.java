package com.codefactory.devnet.shared.integration;

import java.io.InputStream;
import java.time.Duration;

/**
 * Contrato de almacenamiento de objetos. Lo consume {@code profile} para avatares e
 * imagenes de proyecto.
 *
 * <p>Es la frontera que contiene la dependencia de Supabase (ADR-003). La
 * implementacion de produccion habla con Supabase Storage; en local y en pruebas se
 * usa una que escribe en disco temporal. Ningun modulo de negocio sabe cual esta
 * activa.</p>
 *
 * <p>Corresponde al componente "Repositorio de recursos" del lineamiento 4.1, cuya
 * razon de ser es evitar binarios innecesarios en el repositorio de codigo.</p>
 */
public interface IAlmacenArchivos {

    /** Contenedores logicos. Separarlos permite politicas de acceso distintas. */
    enum Bucket {
        AVATARES("avatares"),
        PROYECTOS("proyectos");

        private final String nombre;

        Bucket(String nombre) {
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }
    }

    /**
     * @param ruta   clave del objeto dentro del bucket
     * @param bucket contenedor donde quedo
     * @param bytes  tamano almacenado
     */
    record ArchivoGuardado(String ruta, Bucket bucket, long bytes) { }

    /**
     * Sube un archivo y devuelve su ubicacion.
     *
     * <p>La implementacion valida tipo de contenido y tamano antes de escribir: un
     * archivo subido por un usuario es entrada no confiable, y confiar en la
     * extension del nombre no es validacion.</p>
     *
     * @param nombreOriginal solo para derivar la extension. Nunca se usa tal cual
     *                       como ruta: permitiria atravesar directorios.
     */
    ArchivoGuardado subir(Bucket bucket, String nombreOriginal, String tipoContenido, InputStream contenido);

    /**
     * URL temporal de lectura.
     *
     * <p>Firmada y con vencimiento en vez de publica permanente, para que retirar un
     * contenido de la plataforma lo retire de verdad y no deje un enlace vivo.</p>
     */
    String urlFirmada(Bucket bucket, String ruta, Duration vigencia);

    /** Idempotente: borrar algo que no existe no es un error. */
    void eliminar(Bucket bucket, String ruta);
}