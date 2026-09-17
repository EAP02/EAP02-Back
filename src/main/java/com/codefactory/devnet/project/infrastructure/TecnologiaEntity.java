package com.codefactory.devnet.project.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Fila de {@code tecnologia}: catalogo curado de lenguajes, frameworks, bases,
 * herramientas y nube.
 *
 * <p>{@code aprobada} existe porque el catalogo lo cura un moderador. Sin curaduria
 * aparecerian React, ReactJS y react.js como tres tecnologias distintas, y el reporte
 * de tecnologias mas discutidas dejaria de significar nada.</p>
 */
@Entity
@Table(name = "tecnologia")
public class TecnologiaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Short id;

    @Column(name = "nombre", nullable = false, unique = true)
    private String nombre;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "categoria", nullable = false)
    private String categoria;

    @Column(name = "aprobada", nullable = false)
    private boolean aprobada;

    protected TecnologiaEntity() {
        // exigido por JPA
    }

    public static TecnologiaEntity nueva(String nombre, String slug, String categoria) {
        TecnologiaEntity tecnologia = new TecnologiaEntity();
        tecnologia.nombre = nombre;
        tecnologia.slug = slug;
        tecnologia.categoria = categoria;
        tecnologia.aprobada = true;
        return tecnologia;
    }

    public Short getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getSlug() {
        return slug;
    }

    public String getCategoria() {
        return categoria;
    }

    public boolean isAprobada() {
        return aprobada;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        return otro instanceof TecnologiaEntity t && id != null && id.equals(t.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
