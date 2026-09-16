package com.codefactory.devnet.project.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Fila de {@code publicacion}: el <b>supertipo</b> de proyectos y discusiones.
 *
 * <p>Esta tabla lleva lo que ambos comparten (autor, estado, texto, contadores,
 * vector de busqueda) y cada subtipo anade lo suyo en su propia tabla con la misma
 * clave primaria. Gracias a eso {@code comentario.publicacion_id} es una clave
 * foranea real y no una referencia polimorfica. Ver {@code docs/bd/02-modelo-logico.md}.</p>
 *
 * <p>La columna {@code busqueda_tsv} no se mapea a proposito: es
 * {@code GENERATED ALWAYS ... STORED} y la calcula PostgreSQL. Mapearla haria que
 * Hibernate intentara escribirla y la insercion fallaria.</p>
 */
@Entity
@Table(name = "publicacion")
public class PublicacionEntity {

    public static final String TIPO_PROYECTO = "PROYECTO";
    public static final String ESTADO_PUBLICADO = "PUBLICADO";

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "autor_id", nullable = false)
    private UUID autorId;

    /** Discriminador: PROYECTO o DISCUSION. */
    @Column(name = "tipo", nullable = false)
    private String tipo;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    /** La descripcion del proyecto, en markdown. */
    @Column(name = "contenido", nullable = false)
    private String contenido;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "contador_comentarios", nullable = false)
    private int contadorComentarios;

    @Column(name = "contador_reacciones", nullable = false)
    private int contadorReacciones;

    /** Bloqueo optimista. Produce el 409 CONFLICTO_CONCURRENCIA de ADR-005. */
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "publicacion_tecnologia",
            joinColumns = @JoinColumn(name = "publicacion_id"),
            inverseJoinColumns = @JoinColumn(name = "tecnologia_id"))
    private Set<TecnologiaEntity> tecnologias = new LinkedHashSet<>();

    protected PublicacionEntity() {
        // exigido por JPA
    }

    /**
     * Crea la fila del supertipo para un proyecto ya publicado.
     *
     * <p>{@code publicado_en} se fija aqui porque la restriccion
     * {@code ck_publicacion_publicado_en} exige que todo estado distinto de
     * {@code BORRADOR} lo tenga.</p>
     */
    public static PublicacionEntity proyectoPublicado(UUID id, UUID autorId, String titulo,
                                                      String contenido, Instant publicadoEn) {
        PublicacionEntity p = new PublicacionEntity();
        p.id = id;
        p.autorId = autorId;
        p.tipo = TIPO_PROYECTO;
        p.titulo = titulo;
        p.contenido = contenido;
        p.estado = ESTADO_PUBLICADO;
        p.publicadoEn = publicadoEn;
        p.creadoEn = publicadoEn;
        p.actualizadoEn = publicadoEn;
        return p;
    }

    public void agregarTecnologia(TecnologiaEntity tecnologia) {
        this.tecnologias.add(tecnologia);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAutorId() {
        return autorId;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getContenido() {
        return contenido;
    }

    public String getEstado() {
        return estado;
    }

    public Instant getPublicadoEn() {
        return publicadoEn;
    }

    public Set<TecnologiaEntity> getTecnologias() {
        return tecnologias;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        return otro instanceof PublicacionEntity p && id != null && id.equals(p.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}