package com.codefactory.devnet.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Capacidad concreta que un rol puede conceder, en formato {@code recurso:accion}.
 *
 * <p>HU-06: la autorizacion evalua permisos, no nombres de rol. Cambiar lo que puede
 * hacer un moderador es una fila en {@code rol_permiso}, no un despliegue.</p>
 */
@Entity
@Table(name = "permiso")
public class PermisoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Short id;

    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    protected PermisoEntity() {
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

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        return otro instanceof PermisoEntity p && id != null && id.equals(p.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}