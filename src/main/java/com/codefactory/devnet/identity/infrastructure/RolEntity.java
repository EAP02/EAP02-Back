package com.codefactory.devnet.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Rol del sistema: VISITANTE, DESARROLLADOR, MODERADOR o ADMIN.
 *
 * <p>Los cuatro se cargan como datos de referencia en V1__baseline.sql; no se crean
 * ni se borran en tiempo de ejecucion.</p>
 */
@Entity
@Table(name = "rol")
public class RolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Short id;

    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    /**
     * MODERADOR y ADMIN lo tienen en {@code true}.
     *
     * <p>Que sea dato y no un condicional del codigo permite ajustar la exigencia
     * sin desplegar, y deja la decision auditable en la base (lineamiento 3.4).</p>
     */
    @Column(name = "exige_mfa", nullable = false)
    private boolean exigeMfa;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "rol_permiso",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<PermisoEntity> permisos = new LinkedHashSet<>();

    protected RolEntity() {
        // exigido por JPA
    }

    public Short getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean exigeMfa() {
        return exigeMfa;
    }

    public Set<PermisoEntity> getPermisos() {
        return Set.copyOf(permisos);
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        return otro instanceof RolEntity r && id != null && id.equals(r.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}