package com.codefactory.devnet.profile.domain;

import java.util.List;
import java.util.UUID;

public class Perfil {
    private final UUID id;
    private String nombre;
    private String biografia;
    private String avatarUrl;
    private List<String> tecnologias;
    private String githubUrl;
    private String linkedinUrl;

    public Perfil(UUID id, String nombre, String biografia, String avatarUrl,
                  List<String> tecnologias, String githubUrl, String linkedinUrl) {
        this.id = id;
        this.avatarUrl = avatarUrl;
        actualizar(nombre, biografia, tecnologias, githubUrl, linkedinUrl);
    }

    public void actualizar(String nombre, String biografia, List<String> tecnologias,
                           String githubUrl, String linkedinUrl) {
        this.nombre = requerido(nombre, "nombre");
        this.biografia = requerido(biografia, "biografia");
        if (tecnologias == null || tecnologias.isEmpty()) {
            throw new PerfilInvalidoException("tecnologias", "Debes registrar al menos una tecnologia.");
        }
        this.tecnologias = tecnologias.stream().map(v -> requerido(v, "tecnologias")).distinct().toList();
        this.githubUrl = opcional(githubUrl);
        this.linkedinUrl = opcional(linkedinUrl);
    }

    public void actualizarAvatar(String avatarUrl) {
        this.avatarUrl = requerido(avatarUrl, "avatarUrl");
    }

    private String requerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new PerfilInvalidoException(campo, "Este campo es obligatorio.");
        }
        return valor.strip();
    }

    private String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    public UUID id() { return id; }
    public String nombre() { return nombre; }
    public String biografia() { return biografia; }
    public String avatarUrl() { return avatarUrl; }
    public List<String> tecnologias() { return List.copyOf(tecnologias); }
    public String githubUrl() { return githubUrl; }
    public String linkedinUrl() { return linkedinUrl; }
}
