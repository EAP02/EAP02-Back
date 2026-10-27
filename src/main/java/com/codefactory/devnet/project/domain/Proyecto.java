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
 * Proyecto publicado por un desarrollador. Agregado de HU-07 (publicar) y HU-08 (editar
 * y eliminar).
 *
 * <p>Sin JPA ni Spring: todas las reglas de abajo se prueban con un test unitario
 * puro, sin contexto ni base de datos.</p>
 *
 * <p>Las cotas de longitud repiten a proposito las restricciones {@code CHECK} de
 * {@code V1__baseline.sql}. No es duplicacion ociosa: sin ellas, un titulo corto
 * llegaria a la base y volveria como violacion de integridad, que se traduce en un
 * 409 generico en vez del 400 con el campo culpable que pide HU-07 criterio 2. La
 * base es la ultima linea de defensa, no la primera.</p>
 *
 * <p>Es inmutable: editar no modifica la instancia, devuelve otra con el contenido
 * nuevo y la misma identidad. Asi un proyecto a medio validar nunca existe.</p>
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
    private final EstadoPublicacion estadoPublicacion;

    private Proyecto(UUID id, UUID autorId, String titulo, String descripcion, String resumen,
                     Set<Short> tecnologias, String urlRepositorio, EstadoProyecto estadoProyecto,
                     String licencia, String urlDemo, boolean buscaColaboradores, Instant publicadoEn,
                     EstadoPublicacion estadoPublicacion) {
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
        this.estadoPublicacion = estadoPublicacion;
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

        Contenido contenido = validar(titulo, descripcion, resumen, tecnologias,
                urlRepositorio, estadoProyecto, licencia, urlDemo);

        return new Proyecto(
                UUID.randomUUID(),
                autorId,
                contenido.titulo(),
                contenido.descripcion(),
                contenido.resumen(),
                contenido.tecnologias(),
                contenido.urlRepositorio(),
                contenido.estadoProyecto(),
                contenido.licencia(),
                contenido.urlDemo(),
                buscaColaboradores,
                Instant.now(),
                EstadoPublicacion.PUBLICADO);
    }

    /**
     * Reconstruye un proyecto ya persistido, para operar sobre el.
     *
     * <p><b>No valida</b>: los datos vienen de la base, donde ya pasaron por
     * {@link #publicar} y por las restricciones {@code CHECK}. Validar aqui haria que
     * endurecer una regla en el futuro impidiera <i>leer</i> los proyectos antiguos,
     * en lugar de exigir el cambio solo al editarlos.</p>
     *
     * <p>Solo debe usarlo el adaptador de persistencia.</p>
     */
    public static Proyecto rehidratar(UUID id,
                                      UUID autorId,
                                      String titulo,
                                      String descripcion,
                                      String resumen,
                                      Set<Short> tecnologias,
                                      String urlRepositorio,
                                      EstadoProyecto estadoProyecto,
                                      String licencia,
                                      String urlDemo,
                                      boolean buscaColaboradores,
                                      Instant publicadoEn,
                                      EstadoPublicacion estadoPublicacion) {
        return new Proyecto(id, autorId, titulo, descripcion, resumen, tecnologias,
                urlRepositorio, estadoProyecto, licencia, urlDemo, buscaColaboradores,
                publicadoEn, estadoPublicacion);
    }

    /**
     * HU-08, criterio 1: el autor cambia el contenido de su proyecto.
     *
     * <p>Reemplazo completo, coherente con {@code PUT}: lo que no se envia vuelve a su
     * valor por defecto, igual que al publicar. Las reglas son <b>exactamente</b> las
     * de {@link #publicar}, porque las comparten en {@link #validar}: un proyecto
     * editado no puede quedar en un estado que no se habria podido publicar.</p>
     *
     * <p>Conserva lo que define la identidad del proyecto y su historia: el
     * identificador, el autor, la fecha de publicacion y el estado de la publicacion.
     * Editar no es volver a publicar.</p>
     *
     * <p>Quien puede editar <b>no</b> se decide aqui sino en {@link PoliticaProyecto}:
     * esta operacion solo sabe de contenido y de estado.</p>
     *
     * @return un proyecto nuevo con el contenido cambiado; este no se modifica
     * @throws ExcepcionNegocio {@code PROYECTO_NO_EDITABLE} si el estado no lo admite,
     *                          o {@code PROYECTO_DATOS_INVALIDOS} con un detalle por
     *                          campo incumplido
     */
    public Proyecto editar(String titulo,
                           String descripcion,
                           String resumen,
                           Set<Short> tecnologias,
                           String urlRepositorio,
                           EstadoProyecto estadoProyecto,
                           String licencia,
                           String urlDemo,
                           boolean buscaColaboradores) {

        // El estado se comprueba antes que el contenido: si el proyecto no admite
        // cambios, enumerar los campos mal escritos solo invitaria a corregirlos y
        // reintentar algo que igual se va a rechazar.
        if (!estadoPublicacion.admiteEdicion()) {
            throw new ExcepcionNegocio(
                    CodigoErrorProyecto.PROYECTO_NO_EDITABLE,
                    CodigoErrorProyecto.PROYECTO_NO_EDITABLE.mensajePorDefecto(),
                    List.of(DetalleError.de("estado", estadoPublicacion.name(), "no_admite_edicion")));
        }

        Contenido contenido = validar(titulo, descripcion, resumen, tecnologias,
                urlRepositorio, estadoProyecto, licencia, urlDemo);

        return new Proyecto(
                id,
                autorId,
                contenido.titulo(),
                contenido.descripcion(),
                contenido.resumen(),
                contenido.tecnologias(),
                contenido.urlRepositorio(),
                contenido.estadoProyecto(),
                contenido.licencia(),
                contenido.urlDemo(),
                buscaColaboradores,
                publicadoEn,
                estadoPublicacion);
    }

    /**
     * HU-08.3: eliminar el proyecto, que en DevNet significa archivarlo (regla R3).
     *
     * <p>Archivar y no borrar: el proyecto desaparece de la vista publica pero su fila
     * se conserva, con la transicion registrada en el historial. Asi los comentarios,
     * reacciones y reputacion que lo referencien seguiran siendo coherentes.</p>
     *
     * <p><b>Confirmacion previa.</b> Hay que escribir el titulo exacto del proyecto,
     * como al borrar un repositorio en GitHub. Un clic accidental o un cliente que
     * repite la peticion no la lleva; quien la escribe sabe que esta eliminando
     * <i>ese</i> proyecto y no otro. Se compara sin espacios sobrantes pero
     * distinguiendo mayusculas: la confirmacion es deliberadamente exigente.</p>
     *
     * <p>El estado se comprueba antes que la confirmacion, por el mismo motivo que en
     * {@link #editar}: no tiene sentido pedir que confirme algo que no se puede hacer.</p>
     *
     * @return el mismo proyecto en estado {@code ARCHIVADO}; este no se modifica
     * @throws ExcepcionNegocio {@code PROYECTO_NO_EDITABLE} si ya esta archivado,
     *                          {@code PROYECTO_CONFIRMACION_REQUERIDA} si no se confirmo,
     *                          o {@code PROYECTO_CONFIRMACION_INVALIDA} si no coincide
     */
    public Proyecto archivar(String confirmacionTitulo) {
        if (!estadoPublicacion.admiteArchivado()) {
            throw new ExcepcionNegocio(
                    CodigoErrorProyecto.PROYECTO_NO_EDITABLE,
                    CodigoErrorProyecto.PROYECTO_NO_EDITABLE.mensajePorDefecto(),
                    List.of(DetalleError.de("estado", estadoPublicacion.name(), "no_admite_archivado")));
        }

        String confirmacion = recortar(confirmacionTitulo);
        if (confirmacion.isEmpty()) {
            throw new ExcepcionNegocio(
                    CodigoErrorProyecto.PROYECTO_CONFIRMACION_REQUERIDA,
                    CodigoErrorProyecto.PROYECTO_CONFIRMACION_REQUERIDA.mensajePorDefecto(),
                    List.of(DetalleError.de("confirmacionTitulo", "obligatorio")));
        }
        if (!confirmacion.equals(titulo)) {
            throw new ExcepcionNegocio(
                    CodigoErrorProyecto.PROYECTO_CONFIRMACION_INVALIDA,
                    CodigoErrorProyecto.PROYECTO_CONFIRMACION_INVALIDA.mensajePorDefecto(),
                    List.of(DetalleError.de("confirmacionTitulo", "no_coincide_con_el_titulo")));
        }

        return new Proyecto(id, autorId, titulo, descripcion, resumen, tecnologias,
                urlRepositorio, estadoProyecto, licencia, urlDemo, buscaColaboradores,
                publicadoEn, EstadoPublicacion.ARCHIVADO);
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

    // ------------------------------------------------------------------
    // Validacion compartida por publicar y editar
    // ------------------------------------------------------------------

    /**
     * Valida y normaliza el contenido editable de un proyecto.
     *
     * <p>Unico lugar donde viven las reglas de forma. Publicar y editar pasan por aqui,
     * asi que no pueden divergir: endurecer una regla la aplica a ambos a la vez.</p>
     *
     * @throws ExcepcionNegocio con {@code PROYECTO_DATOS_INVALIDOS} y todos los fallos
     */
    private static Contenido validar(String titulo,
                                     String descripcion,
                                     String resumen,
                                     Set<Short> tecnologias,
                                     String urlRepositorio,
                                     EstadoProyecto estadoProyecto,
                                     String licencia,
                                     String urlDemo) {

        List<DetalleError> fallos = new ArrayList<>();

        String tituloLimpio = validarTitulo(titulo, fallos);
        String descripcionLimpia = validarDescripcion(descripcion, fallos);
        String resumenFinal = resolverResumen(resumen, descripcionLimpia, fallos);
        Set<Short> stack = validarTecnologias(tecnologias, fallos);
        String repo = urlHttpsOpcional(urlRepositorio, "urlRepositorio", fallos);
        String demo = urlHttpsOpcional(urlDemo, "urlDemo", fallos);

        if (!fallos.isEmpty()) {
            throw new ExcepcionNegocio(CodigoErrorProyecto.PROYECTO_DATOS_INVALIDOS, fallos);
        }

        return new Contenido(
                tituloLimpio,
                descripcionLimpia,
                resumenFinal,
                stack,
                repo,
                estadoProyecto == null ? EstadoProyecto.IDEA : estadoProyecto,
                vacioANulo(licencia),
                demo);
    }

    // Cada regla agrega sus fallos a la lista comun en vez de lanzar: asi la respuesta
    // informa de todos los campos invalidos de una vez, no del primero que encuentre.

    private static String validarTitulo(String titulo, List<DetalleError> fallos) {
        String limpio = recortar(titulo);
        if (limpio.isEmpty()) {
            fallos.add(DetalleError.de("titulo", "obligatorio"));
        } else if (limpio.length() < TITULO_MIN || limpio.length() > TITULO_MAX) {
            fallos.add(DetalleError.de("titulo", limpio.length(),
                    "longitud_entre_" + TITULO_MIN + "_y_" + TITULO_MAX));
        }
        return limpio;
    }

    private static String validarDescripcion(String descripcion, List<DetalleError> fallos) {
        String limpia = recortar(descripcion);
        if (limpia.isEmpty()) {
            fallos.add(DetalleError.de("descripcion", "obligatorio"));
        } else if (limpia.length() < DESCRIPCION_MIN) {
            fallos.add(DetalleError.de("descripcion", limpia.length(), "longitud_minima_" + DESCRIPCION_MIN));
        }
        return limpia;
    }

    /**
     * El resumen no lo pide la HU, pero la columna es NOT NULL. Si no viene, se deriva
     * de la descripcion en vez de rechazar la peticion por un campo que el usuario no
     * sabe que existe.
     */
    private static String resolverResumen(String resumen, String descripcionLimpia, List<DetalleError> fallos) {
        String limpio = recortar(resumen);
        if (limpio.isEmpty() && !descripcionLimpia.isEmpty()) {
            return descripcionLimpia.length() > RESUMEN_MAX
                    ? descripcionLimpia.substring(0, RESUMEN_MAX - 3) + "..."
                    : descripcionLimpia;
        }
        if (limpio.length() > RESUMEN_MAX) {
            fallos.add(DetalleError.de("resumen", limpio.length(), "longitud_maxima_" + RESUMEN_MAX));
        }
        return limpio;
    }

    private static Set<Short> validarTecnologias(Set<Short> tecnologias, List<DetalleError> fallos) {
        Set<Short> stack = tecnologias == null ? Set.of() : new LinkedHashSet<>(tecnologias);
        if (stack.size() < TECNOLOGIAS_MIN || stack.size() > TECNOLOGIAS_MAX) {
            fallos.add(DetalleError.de("tecnologias", stack.size(),
                    "entre_" + TECNOLOGIAS_MIN + "_y_" + TECNOLOGIAS_MAX));
        }
        return stack;
    }

    /**
     * Un enlace opcional: ausente es valido, presente tiene que ser https. http
     * permitiria degradar la conexion de quien siga el enlace.
     *
     * @return el enlace limpio, o {@code null} si no vino
     */
    private static String urlHttpsOpcional(String valor, String campo, List<DetalleError> fallos) {
        String limpio = recortar(valor);
        if (limpio.isEmpty()) {
            return null;
        }
        if (!limpio.toLowerCase().startsWith("https://")) {
            fallos.add(DetalleError.de(campo, limpio, "debe_ser_https"));
        }
        return limpio;
    }

    /** Contenido ya validado y normalizado. Solo existe dentro de esta clase. */
    private record Contenido(
            String titulo,
            String descripcion,
            String resumen,
            Set<Short> tecnologias,
            String urlRepositorio,
            EstadoProyecto estadoProyecto,
            String licencia,
            String urlDemo
    ) { }

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

    public EstadoPublicacion estadoPublicacion() {
        return estadoPublicacion;
    }
}
