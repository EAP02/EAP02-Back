package com.codefactory.devnet.discussion.domain;

import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tema de discusion abierto por un miembro. Agregado de HU-11.
 *
 * <p>Sin JPA ni Spring, como {@code Proyecto}: todas las reglas se prueban con un test
 * unitario puro.</p>
 *
 * <p>Una discusion es, como un proyecto, una especializacion de {@code publicacion}.
 * Por eso sus cotas coinciden con las de la publicacion de un proyecto: las imponen las
 * mismas restricciones {@code CHECK} de la tabla comun ({@code ck_publicacion_titulo},
 * {@code ck_publicacion_contenido}) y el limite de etiquetas de
 * {@code publicacion_tecnologia}. Se repiten aqui para responder un 400 con el campo
 * culpable en vez de una violacion de integridad generica.</p>
 */
public class Discusion {

    public static final int TITULO_MIN = 5;
    public static final int TITULO_MAX = 200;
    public static final int CUERPO_MIN = 20;
    public static final int TECNOLOGIAS_MIN = 1;
    public static final int TECNOLOGIAS_MAX = 5;

    private final UUID id;
    private final UUID autorId;
    private final String titulo;
    private final String cuerpo;
    private final CategoriaDiscusion categoria;
    private final Set<Short> tecnologias;
    private final Instant publicadoEn;

    private Discusion(UUID id, UUID autorId, String titulo, String cuerpo,
                      CategoriaDiscusion categoria, Set<Short> tecnologias, Instant publicadoEn) {
        this.id = id;
        this.autorId = autorId;
        this.titulo = titulo;
        this.cuerpo = cuerpo;
        this.categoria = categoria;
        this.tecnologias = Set.copyOf(tecnologias);
        this.publicadoEn = publicadoEn;
    }

    /**
     * HU-11: abre una discusion ya publicada.
     *
     * <p>Acumula <b>todos</b> los fallos y los reporta juntos, como al publicar un
     * proyecto: quien envia tres campos mal se entera de los tres en una respuesta.</p>
     *
     * @param autorId    quien la abre. Lo fija el caso de uso desde el token, nunca el
     *                   cuerpo de la peticion: la discusion queda asociada al miembro
     *                   que la creo (criterio 3).
     * @param categoria  opcional; si falta, {@link CategoriaDiscusion#DEBATE}
     * @throws ExcepcionNegocio con {@code DISCUSION_DATOS_INVALIDOS} y un detalle por
     *                          campo incumplido (criterio 2)
     */
    public static Discusion abrir(UUID autorId,
                                  String titulo,
                                  String cuerpo,
                                  CategoriaDiscusion categoria,
                                  Set<Short> tecnologias) {

        List<DetalleError> fallos = new ArrayList<>();

        String tituloLimpio = recortar(titulo);
        if (tituloLimpio.isEmpty()) {
            fallos.add(DetalleError.de("titulo", "obligatorio"));
        } else if (tituloLimpio.length() < TITULO_MIN || tituloLimpio.length() > TITULO_MAX) {
            fallos.add(DetalleError.de("titulo", tituloLimpio.length(),
                    "longitud_entre_" + TITULO_MIN + "_y_" + TITULO_MAX));
        }

        String cuerpoLimpio = recortar(cuerpo);
        if (cuerpoLimpio.isEmpty()) {
            fallos.add(DetalleError.de("cuerpo", "obligatorio"));
        } else if (cuerpoLimpio.length() < CUERPO_MIN) {
            fallos.add(DetalleError.de("cuerpo", cuerpoLimpio.length(), "longitud_minima_" + CUERPO_MIN));
        }

        // Las etiquetas son las que hacen que la discusion trate "sobre una tecnologia"
        // y la vuelven encontrable: sin al menos una no cumple lo que pide la HU.
        Set<Short> etiquetas = tecnologias == null ? Set.of() : new LinkedHashSet<>(tecnologias);
        if (etiquetas.size() < TECNOLOGIAS_MIN || etiquetas.size() > TECNOLOGIAS_MAX) {
            fallos.add(DetalleError.de("tecnologias", etiquetas.size(),
                    "entre_" + TECNOLOGIAS_MIN + "_y_" + TECNOLOGIAS_MAX));
        }

        if (!fallos.isEmpty()) {
            throw new ExcepcionNegocio(CodigoErrorDiscusion.DISCUSION_DATOS_INVALIDOS, fallos);
        }

        return new Discusion(
                UUID.randomUUID(),
                autorId,
                tituloLimpio,
                cuerpoLimpio,
                categoria == null ? CategoriaDiscusion.DEBATE : categoria,
                etiquetas,
                Instant.now());
    }

    private static String recortar(String valor) {
        return valor == null ? "" : valor.strip();
    }

    // ------------------------------------------------------------------

    public UUID id() {
        return id;
    }

    public UUID autorId() {
        return autorId;
    }

    public String titulo() {
        return titulo;
    }

    public String cuerpo() {
        return cuerpo;
    }

    public CategoriaDiscusion categoria() {
        return categoria;
    }

    public Set<Short> tecnologias() {
        return tecnologias;
    }

    public Instant publicadoEn() {
        return publicadoEn;
    }
}
