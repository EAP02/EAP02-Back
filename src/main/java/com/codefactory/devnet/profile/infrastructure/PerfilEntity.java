package com.codefactory.devnet.profile.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fila de {@code perfil}: datos publicos del desarrollador.
 *
 * <p>Relacion 1:1 identificante con {@code usuario}: comparten clave primaria. No es
 * normalizacion redundante, separa el dato sensible y de acceso restringido
 * (credenciales) del publico que se lee en cada elemento del feed.</p>
 */
@Entity
@Table(name = "perfil")
public class PerfilEntity {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "nombre_completo", nullable = false)
    private String nombreCompleto;

    @Column(name = "titular")
    private String titular;

    @Column(name = "biografia")
    private String biografia;

    @Column(name = "ubicacion")
    private String ubicacion;

    @Column(name = "url_avatar")
    private String urlAvatar;

    @Column(name = "url_sitio_web")
    private String urlSitioWeb;

    @Column(name = "url_github")
    private String urlGithub;

    @Column(name = "url_linkedin")
    private String urlLinkedin;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "perfil_habilidad", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "habilidad", nullable = false, length = 80)
    private List<String> habilidades = new ArrayList<>();

    @Column(name = "anios_experiencia")
    private Short aniosExperiencia;

    @Column(name = "disponible_colaborar", nullable = false)
    private boolean disponibleColaborar;

    /**
     * Saldo denormalizado del libro mayor {@code evento_reputacion}.
     *
     * <p>Justificado por lectura: se consulta en cada elemento del feed y en cada
     * validacion de privilegio. Siempre reconstruible sumando el libro mayor, y la
     * consulta de integridad I1 lo verifica.</p>
     */
    @Column(name = "reputacion", nullable = false)
    private int reputacion;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected PerfilEntity() {
        // exigido por JPA
    }

    public static PerfilEntity nuevo(UUID usuarioId, String nombreCompleto, String titular) {
        PerfilEntity p = new PerfilEntity();
        p.usuarioId = usuarioId;
        p.nombreCompleto = nombreCompleto;
        p.titular = titular;
        p.reputacion = 0;
        p.disponibleColaborar = false;
        return p;
    }

    @PrePersist
    void alPersistir() {
        Instant ahora = Instant.now();
        if (creadoEn == null) {
            creadoEn = ahora;
        }
        actualizadoEn = ahora;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getUrlAvatar() {
        return urlAvatar;
    }

    public String getBiografia() {
        return biografia;
    }

    public String getUrlGithub() {
        return urlGithub;
    }

    public String getUrlLinkedin() {
        return urlLinkedin;
    }

    public List<String> getHabilidades() {
        return List.copyOf(habilidades);
    }

    public void actualizar(String nombreCompleto, String biografia, List<String> habilidades,
                           String urlGithub, String urlLinkedin) {
        this.nombreCompleto = nombreCompleto;
        this.biografia = biografia;
        this.habilidades = new ArrayList<>(habilidades);
        this.urlGithub = urlGithub;
        this.urlLinkedin = urlLinkedin;
        this.actualizadoEn = Instant.now();
    }

    public void actualizarAvatar(String urlAvatar) {
        this.urlAvatar = urlAvatar;
        this.actualizadoEn = Instant.now();
    }

    public int getReputacion() {
        return reputacion;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        return otro instanceof PerfilEntity p && usuarioId != null && usuarioId.equals(p.usuarioId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(usuarioId);
    }
}
