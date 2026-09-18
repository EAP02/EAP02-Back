package com.codefactory.devnet.profile.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class Perfil {

    /** Igual que en las publicaciones: un perfil con veinte tecnologias no informa de nada. */
    public static final int TECNOLOGIAS_MAX = 15;

    private final UUID id;
    private String nombre;
    private String biografia;
    private String avatarUrl;
    private List<TecnologiaDeclarada> tecnologias;
    private String githubUrl;
    private String linkedinUrl;

    /**
     * Rehidrata un perfil desde persistencia.
     *
     * <p><b>No exige tecnologias.</b> Un perfil nace vacio al registrarse el usuario
     * y se completa despues; si el constructor las exigiera, leer el perfil recien
     * creado reventaria. La regla de "al menos una" pertenece a la <i>edicion</i>, no
     * a la existencia.</p>
     */
    public Perfil(UUID id, String nombre, String biografia, String avatarUrl,
                  List<TecnologiaDeclarada> tecnologias, String githubUrl, String linkedinUrl) {
        this.id = id;
        this.nombre = nombre;
        this.biografia = biografia;
        this.avatarUrl = avatarUrl;
        this.tecnologias = tecnologias == null ? List.of() : List.copyOf(tecnologias);
        this.githubUrl = githubUrl;
        this.linkedinUrl = linkedinUrl;
    }

    /**
     * Edicion del perfil por su titular. Aqui si se exigen todas las reglas: un
     * perfil que alguien edita a conciencia debe quedar completo.
     */
    public void actualizar(String nombre, String biografia, List<TecnologiaDeclarada> tecnologias,
                           String githubUrl, String linkedinUrl) {
        this.nombre = requerido(nombre, "nombre");
        this.biografia = requerido(biografia, "biografia");
        this.tecnologias = normalizar(tecnologias);
        this.githubUrl = urlOpcional(githubUrl, "githubUrl");
        this.linkedinUrl = urlOpcional(linkedinUrl, "linkedinUrl");
    }

    /** {@code true} cuando el titular ya lo completo. */
    public boolean estaCompleto() {
        return nombre != null && !nombre.isBlank() && !tecnologias.isEmpty();
    }

    public void actualizarAvatar(String avatarUrl) {
        this.avatarUrl = requerido(avatarUrl, "avatarUrl");
    }

    /**
     * Valida y deduplica el stack declarado.
     *
     * <p>Deduplica por identificador de tecnologia, no por el trio completo: declarar
     * Java en nivel basico y tambien en experto no son dos hechos, es una
     * contradiccion. Se conserva la primera aparicion y se ordena por id para que el
     * orden de la respuesta no dependa del orden de la peticion.</p>
     */
    private List<TecnologiaDeclarada> normalizar(List<TecnologiaDeclarada> declaradas) {
        if (declaradas == null || declaradas.isEmpty()) {
            throw new PerfilInvalidoException("tecnologias", "Debes registrar al menos una tecnologia.");
        }

        Map<Short, TecnologiaDeclarada> porId = new LinkedHashMap<>();
        for (TecnologiaDeclarada t : declaradas) {
            if (t == null) {
                throw new PerfilInvalidoException("tecnologias", "Hay una tecnologia vacia en la lista.");
            }
            porId.putIfAbsent(t.tecnologiaId(), t);
        }

        if (porId.size() > TECNOLOGIAS_MAX) {
            throw new PerfilInvalidoException("tecnologias",
                    "Puedes declarar como maximo " + TECNOLOGIAS_MAX + " tecnologias.");
        }

        return porId.values().stream()
                .sorted(Comparator.comparingInt(TecnologiaDeclarada::tecnologiaId))
                .toList();
    }

    private String requerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new PerfilInvalidoException(campo, "Este campo es obligatorio.");
        }
        return valor.strip();
    }

    /**
     * Un enlace ausente es valido; uno presente debe ser https.
     *
     * <p>http permitiria degradar la conexion de quien lo siga desde la plataforma, y
     * el {@code CHECK} de la V3 lo rechazaria de todos modos: mejor un 400 con el
     * campo senalado que un error de integridad.</p>
     */
    private String urlOpcional(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.strip();
        if (!limpio.toLowerCase().startsWith("https://")) {
            throw new PerfilInvalidoException(campo, "El enlace debe empezar por https://");
        }
        return limpio;
    }

    public UUID id() { return id; }
    public String nombre() { return nombre; }
    public String biografia() { return biografia; }
    public String avatarUrl() { return avatarUrl; }
    public List<TecnologiaDeclarada> tecnologias() { return List.copyOf(tecnologias); }
    public String githubUrl() { return githubUrl; }
    public String linkedinUrl() { return linkedinUrl; }

    /** Identificadores del stack, para validarlos de una sola consulta. */
    public Set<Short> idsDeTecnologias() {
        return tecnologias.stream()
                .map(TecnologiaDeclarada::tecnologiaId)
                .collect(Collectors.toUnmodifiableSet());
    }
}