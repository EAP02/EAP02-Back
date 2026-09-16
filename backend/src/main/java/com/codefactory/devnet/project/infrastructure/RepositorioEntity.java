package com.codefactory.devnet.project.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Fila de {@code repositorio}: enlace a GitHub, GitLab u otro proveedor.
 *
 * <p>Tabla aparte y no una columna en {@code publicacion_proyecto} porque un proyecto
 * puede tener varios repositorios (backend, frontend, infraestructura), que es lo
 * normal en este dominio.</p>
 */
@Entity
@Table(name = "repositorio")
public class RepositorioEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "publicacion_proyecto_id", nullable = false)
    private UUID publicacionProyectoId;

    @Column(name = "proveedor", nullable = false)
    private String proveedor;

    @Column(name = "url", nullable = false, unique = true)
    private String url;

    @Column(name = "rama_principal", nullable = false)
    private String ramaPrincipal;

    @Column(name = "estrellas", nullable = false)
    private int estrellas;

    protected RepositorioEntity() {
        // exigido por JPA
    }

    public static RepositorioEntity de(UUID publicacionProyectoId, String url) {
        RepositorioEntity r = new RepositorioEntity();
        r.id = UUID.randomUUID();
        r.publicacionProyectoId = publicacionProyectoId;
        r.url = url;
        r.proveedor = proveedorSegunUrl(url);
        r.ramaPrincipal = "main";
        r.estrellas = 0;
        return r;
    }

    /**
     * Deduce el proveedor del dominio de la URL.
     *
     * <p>Debe devolver uno de los valores de {@code ck_repositorio_proveedor}; ante
     * cualquier otro host se usa OTRO en vez de fallar, porque enlazar un repositorio
     * autoalojado es legitimo.</p>
     */
    private static String proveedorSegunUrl(String url) {
        String minuscula = url.toLowerCase();
        if (minuscula.contains("github.com")) {
            return "GITHUB";
        }
        if (minuscula.contains("gitlab.com")) {
            return "GITLAB";
        }
        return "OTRO";
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getProveedor() {
        return proveedor;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        return otro instanceof RepositorioEntity r && id != null && id.equals(r.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}