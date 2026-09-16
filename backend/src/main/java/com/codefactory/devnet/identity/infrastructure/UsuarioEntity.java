package com.codefactory.devnet.identity.infrastructure;

import com.codefactory.devnet.identity.domain.EstadoUsuario;
import com.codefactory.devnet.identity.domain.Rol;
import com.codefactory.devnet.identity.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fila de {@code usuario}. Adaptador de persistencia, no modelo de dominio: las
 * reglas viven en {@link Usuario}.
 *
 * <p>Separada de {@code perfil} a proposito: esta fila lleva credenciales, se consulta
 * en cada autenticacion y su acceso esta restringido; la de perfil es publica y se lee
 * en cada elemento del feed.</p>
 */
@Entity
@Table(name = "usuario")
public class UsuarioEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "correo", nullable = false)
    private String correo;

    @Column(name = "nombre_usuario", nullable = false)
    private String nombreUsuario;

    /** Argon2id. Nulo si la cuenta se creo por GitHub y aun no fijo contrasena. */
    @Column(name = "clave_hash")
    private String claveHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoUsuario estado = EstadoUsuario.ACTIVO;

    @Column(name = "mfa_habilitado", nullable = false)
    private boolean mfaHabilitado;

    /** Secreto TOTP cifrado a nivel de columna. Nunca se expone ni se registra. */
    @Column(name = "mfa_secreto")
    private String mfaSecreto;

    @Column(name = "intentos_fallidos", nullable = false)
    private short intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    @Column(name = "ultimo_acceso_en")
    private Instant ultimoAccesoEn;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /**
     * EAGER a proposito: los permisos se necesitan en cada autenticacion y son cuatro
     * roles con veinte permisos en total. Una consulta perezosa aqui solo anadiria un
     * viaje mas a la base sin ahorrar nada.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<RolEntity> roles = new LinkedHashSet<>();

    protected UsuarioEntity() {
        // exigido por JPA
    }

    public static UsuarioEntity nueva(String correo, String nombreUsuario, String claveHash) {
        UsuarioEntity u = new UsuarioEntity();
        u.id = UUID.randomUUID();
        u.correo = correo;
        u.nombreUsuario = nombreUsuario;
        u.claveHash = claveHash;
        u.estado = EstadoUsuario.ACTIVO;
        return u;
    }

    @PrePersist
    void alPersistir() {
        Instant ahora = Instant.now();
        if (creadoEn == null) {
            creadoEn = ahora;
        }
        actualizadoEn = ahora;
    }

    // ------------------------------------------------------------------
    // Mapeo hacia el dominio
    // ------------------------------------------------------------------

    public Usuario aDominio() {
        Set<Rol> rolesDominio = roles.stream()
                .map(r -> new Rol(
                        r.getCodigo(),
                        r.exigeMfa(),
                        r.getPermisos().stream()
                                .map(PermisoEntity::getCodigo)
                                .collect(Collectors.toUnmodifiableSet())))
                .collect(Collectors.toUnmodifiableSet());

        return new Usuario(id, nombreUsuario, claveHash, estado, mfaHabilitado,
                intentosFallidos, bloqueadoHasta, rolesDominio);
    }

    /** Vuelca a la fila lo que el dominio cambio durante la autenticacion. */
    public void aplicarEstadoAcceso(Usuario usuario) {
        this.intentosFallidos = usuario.intentosFallidos();
        this.bloqueadoHasta = usuario.bloqueadoHasta();
        if (usuario.ultimoAccesoEn() != null) {
            this.ultimoAccesoEn = usuario.ultimoAccesoEn();
        }
    }

    // ------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getCorreo() {
        return correo;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public EstadoUsuario getEstado() {
        return estado;
    }

    public void asignarRol(RolEntity rol) {
        this.roles.add(rol);
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        return otro instanceof UsuarioEntity u && id != null && id.equals(u.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** Sin credenciales: esta clase puede acabar en un log o en una traza. */
    @Override
    public String toString() {
        return "UsuarioEntity{id=%s, nombreUsuario=%s, estado=%s}".formatted(id, nombreUsuario, estado);
    }
}