package com.codefactory.devnet.project.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Fila de {@code publicacion_proyecto}: la <b>especializacion</b> proyecto.
 *
 * <p>Comparte clave primaria con {@link PublicacionEntity} mediante {@code @MapsId}.
 * Esa es la traduccion a JPA del patron supertipo/subtipo del modelo entidad-relacion:
 * un solo identificador, integridad referencial real, y ningun campo repetido entre
 * las dos tablas.</p>
 */
@Entity
@Table(name = "publicacion_proyecto")
public class PublicacionProyectoEntity {

    @Id
    @Column(name = "publicacion_id")
    private UUID publicacionId;

    /**
     * La misma clave primaria que el supertipo.
     *
     * <p>{@code @MapsId} evita tener que asignar el id a mano y garantiza que no
     * puedan divergir.</p>
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "publicacion_id")
    private PublicacionEntity publicacion;

    @Column(name = "resumen", nullable = false)
    private String resumen;

    @Column(name = "licencia")
    private String licencia;

    @Column(name = "estado_proyecto", nullable = false)
    private String estadoProyecto;

    @Column(name = "busca_colaboradores", nullable = false)
    private boolean buscaColaboradores;

    @Column(name = "max_colaboradores", nullable = false)
    private short maxColaboradores = 5;

    @Column(name = "url_demo")
    private String urlDemo;

    protected PublicacionProyectoEntity() {
        // exigido por JPA
    }

    public static PublicacionProyectoEntity de(PublicacionEntity publicacion,
                                               String resumen,
                                               String estadoProyecto,
                                               String licencia,
                                               String urlDemo,
                                               boolean buscaColaboradores) {
        PublicacionProyectoEntity p = new PublicacionProyectoEntity();
        p.publicacion = publicacion;
        p.resumen = resumen;
        p.estadoProyecto = estadoProyecto;
        p.licencia = licencia;
        p.urlDemo = urlDemo;
        p.buscaColaboradores = buscaColaboradores;
        return p;
    }

    public UUID getPublicacionId() {
        return publicacionId;
    }

    public PublicacionEntity getPublicacion() {
        return publicacion;
    }

    public String getResumen() {
        return resumen;
    }

    public String getEstadoProyecto() {
        return estadoProyecto;
    }

    public boolean isBuscaColaboradores() {
        return buscaColaboradores;
    }

    public String getLicencia() {
        return licencia;
    }

    public String getUrlDemo() {
        return urlDemo;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        return otro instanceof PublicacionProyectoEntity p
                && publicacionId != null && publicacionId.equals(p.publicacionId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(publicacionId);
    }
}