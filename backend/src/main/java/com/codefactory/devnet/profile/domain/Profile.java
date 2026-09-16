package com.codefactory.devnet.profile.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reglas de negocio del perfil. El correo pertenece al modulo identity. */
public class Profile {
    private final Long id;
    private String displayName;
    private String bio;
    private String avatarUrl;
    private List<String> technologies;
    private String githubUrl;
    private String linkedinUrl;

    public Profile(Long id, String displayName, String bio, String avatarUrl, List<String> technologies,
                   String githubUrl, String linkedinUrl) {
        this.id = Objects.requireNonNull(id, "El id es obligatorio");
        update(displayName, bio, technologies, githubUrl, linkedinUrl);
        this.avatarUrl = avatarUrl;
    }

    public void update(String displayName, String bio, List<String> technologies, String githubUrl, String linkedinUrl) {
        this.displayName = required(displayName, "displayName");
        this.bio = required(bio, "bio");
        if (technologies == null || technologies.isEmpty()) {
            throw new InvalidProfileException("technologies", "Debes registrar al menos una tecnologia o lenguaje.");
        }
        this.technologies = technologies.stream().map(value -> required(value, "technologies")).toList();
        this.githubUrl = blankToNull(githubUrl);
        this.linkedinUrl = blankToNull(linkedinUrl);
    }

    public void updateAvatar(String avatarUrl) { this.avatarUrl = required(avatarUrl, "avatarUrl"); }
    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public String getAvatarUrl() { return avatarUrl; }
    public List<String> getTechnologies() { return new ArrayList<>(technologies); }
    public String getGithubUrl() { return githubUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new InvalidProfileException(field, "Este campo es obligatorio.");
        return value.trim();
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
