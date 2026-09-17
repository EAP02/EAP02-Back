package com.codefactory.devnet.project.domain;

import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Proyecto publicado por un desarrollador. Agregado de HU-07.
 *
 * <p>Sin JPA ni Spring: todas las reglas de abajo se prueban con un test unitario
 * puro, sin contexto ni base de datos.</p>
 *
 * <p>Las cotas de longitud repiten a proposito las restricciones {@code CHECK} de
 * {@code V1__baseline.sql}. No es duplicacion ociosa: sin ellas, un titulo corto
 * llegaria a la base y volveria como violacion de integridad, que se traduce en un
 * 409 generico en vez del 400 con el campo culpable que pide HU-07 criterio 2. La
 * base es la ultima linea de defensa, no la primera.</p>
 */
public class Proyecto {

    public static final int TITULO_MIN = 5;
    public static final int TITULO_MAX = 200;
    public static final int DESCRIPCION_MIN = 20;
    public static final int RESUMEN_MAX = 300;
    public static final int TECNOLOGIAS_MIN = 1;
    public static final int TECNOLOGIAS_MAX = 5;

    private final UUID id;
    private final UUID autorId;
    private final String titulo;
    private final String descripcion;
    private final String resumen;
    private final Set<Short> tecnologias;
    private final String urlRepositorio;
    private final EstadoProyecto estadoProyecto;
    private final String licencia;
    private final String urlDemo;
    private final boolean buscaColaboradores;
    private final Instant publicadoEn;

    private Proyecto(UUID id, UUID autorId, String titulo, String descripcion, String resumen,
                     Set<Short> tecnologias, String urlRepositorio, EstadoProyecto estadoProyecto,
                     String licencia, String urlDemo, boolean buscaColaboradores, Instant publicadoEn) {
        this.id = id;
        this.autorId = autorId;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.resumen = resumen;
        this.tecnologias = Set.copyOf(tecnologias);
        this.urlRepositorio = urlRepositorio;
        this.estadoProyecto = estadoProyecto;
        this.licencia = licencia;
        this.urlDemo = urlDemo;
        this.buscaColaboradores = buscaColaboradores;
        this.publicadoEn = publicadoEn;
    }

    /**
     * Crea un proyecto ya publicado, validando todas las reglas de HU-07.
     *
     * <p>Acumula <b>todos</b> los fallos y los reporta juntos, en vez de abortar en el
     * primero. Un cliente que envia tres campos mal debe enterarse de los tres en una
     * respuesta, no descubrirlos de uno en uno.</p>
     *
     * @param autorId quien publica. Lo fija el caso de uso desde el token, nunca el
     *                cuerpo de la peticion (criterio 3).
     * @throws ExcepcionNegocio con {@code PROYECTO_DATOS_INVALIDOS} y un detalle por
     *                          campo incumplido
     */
    public static Proyecto publicar(UUID autorId,
                                    String titulo,
                                    String descripcion,
                                    String resumen,
                                    Set<Short> tecnologias,
                                    String urlRepositorio,
                                    EstadoProyecto estadoProyecto,
                                    String licencia,
                                    String urlDemo,
                                    boolean buscaColaboradores) {

        List<DetalleError> fallos = new ArrayList<>();

        String tituloLimpio = recortar(titulo);
        if (tituloLimpio.isEmpty()) {
            fallos.add(DetalleError.de("titulo", "obligatorio"));
        } else if (tituloLimpio.length() < TITULO_MIN || tituloLimpio.length() > TITULO_MAX) {
            fallos.add(DetalleError.de("titulo", tituloLimpio.length(),
                    "longitud_entre_" + TITULO_MIN + "_y_" + TITULO_MAX));
        }

        String descripcionLimpia = recortar(descripcion);
        if (descripcionLimpia.isEmpty()) {
            fallos.add(DetalleError.de("descripcion", "obligatorio"));
        } else if (descripcionLimpia.length() < DESCRIPCION_MIN) {
            fallos.add(DetalleError.de("descripcion", descripcionLimpia.length(),
                    "longitud_minima_" + DESCRIPCION_MIN));
        }

        // El resumen no lo pide la HU, pero la columna es NOT NULL. Si no viene, se
        // deriva de la descripcion en vez de rechazar la peticion por un campo que el
        // usuario no sabe que existe.
        String resumenFinal = recortar(resumen);
        if (resumenFinal.isEmpty() && !descripcionLimpia.isEmpty()) {
            resumenFinal = descripcionLimpia.length() > RESUMEN_MAX
                    ? descripcionLimpia.substring(0, RESUMEN_MAX - 3) + "..."
                    : descripcionLimpia;
        } else if (resumenFinal.length() > RESUMEN_MAX) {
            fallos.add(DetalleError.de("resumen", resumenFinal.length(), "longitud_maxima_" + RESUMEN_MAX));
        }

        Set<Short> stack = tecnologias == null ? Set.of() : new LinkedHashSet<>(tecnologias);
        if (stack.size() < TECNOLOGIAS_MIN || stack.size() > TECNOLOGIAS_MAX) {
            fallos.add(DetalleError.de("tecnologias", stack.size(),
                    "entre_" + TECNOLOGIAS_MIN + "_y_" + TECNOLOGIAS_MAX));
        }

        // El enlace al repositorio es opcional, pero si viene tiene que ser https.
        // http permitiria degradar la conexion de quien siga el enlace.
        String repo = recortar(urlRepositorio);
        if (!repo.isEmpty() && !repo.toLowerCase().startsWith("https://")) {
            fallos.add(DetalleError.de("urlRepositorio", repo, "debe_ser_https"));
        }

        String demo = recortar(urlDemo);
        if (!demo.isEmpty() && !demo.toLowerCase().startsWith("https://")) {
            fallos.add(DetalleError.de("urlDemo", demo, "debe_ser_https"));
        }

        if (!fallos.isEmpty()) {
            throw new ExcepcionNegocio(CodigoErrorProyecto.PROYECTO_DATOS_INVALIDOS, fallos);
        }

        return new Proyecto(
                UUID.randomUUID(),
                autorId,
                tituloLimpio,
                descripcionLimpia,
                resumenFinal,
                stack,
                repo.isEmpty() ? null : repo,
                estadoProyecto == null ? EstadoProyecto.IDEA : estadoProyecto,
                vacioANulo(licencia),
                demo.isEmpty() ? null : demo,
                buscaColaboradores,
                Instant.now());
    }

    /**
     * HU-07, criterio 3: solo el autor puede publicar en su nombre.
     *
     * <p>La comprobacion vive en el dominio y no solo en el controlador, para que
     * ninguna ruta futura pueda saltarsela.</p>
     */
    public static void exigirAutoriaPropia(UUID autorDeclarado, UUID autenticado) {
        if (autorDeclarado != null && !autorDeclarado.equals(autenticado)) {
            throw new ExcepcionNegocio(
                    CodigoErrorProyecto.PROYECTO_AUTOR_AJENO,
                    CodigoErrorProyecto.PROYECTO_AUTOR_AJENO.mensajePorDefecto(),
                    List.of(DetalleError.de("autorId", "no_coincide_con_el_autenticado")));
        }
    }

    private static String recortar(String valor) {
        return valor == null ? "" : valor.strip();
    }

    private static String vacioANulo(String valor) {
        String limpio = recortar(valor);
        return limpio.isEmpty() ? null : limpio;
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

    public String descripcion() {
        return descripcion;
    }

    public String resumen() {
        return resumen;
    }

    public Set<Short> tecnologias() {
        return tecnologias;
    }

    public String urlRepositorio() {
        return urlRepositorio;
    }

    public EstadoProyecto estadoProyecto() {
        return estadoProyecto;
    }

    public String licencia() {
        return licencia;
    }

    public String urlDemo() {
        return urlDemo;
    }

    public boolean buscaColaboradores() {
        return buscaColaboradores;
    }

    public Instant publicadoEn() {
        return publicadoEn;
    }
}