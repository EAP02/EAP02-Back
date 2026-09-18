package com.codefactory.devnet.profile.infrastructure;

import com.codefactory.devnet.profile.domain.NivelTecnologia;
import com.codefactory.devnet.profile.domain.TecnologiaDeclarada;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/**
 * Fila de {@code perfil_tecnologia}, vista como elemento de coleccion.
 *
 * <p>Se mapea con {@code @ElementCollection} y no como entidad propia porque no tiene
 * identidad fuera de su perfil: nadie consulta una declaracion de tecnologia por si
 * misma, siempre se lee el stack completo de alguien.</p>
 *
 * <p>{@code usuario_id} no aparece aqui: lo aporta el {@code @CollectionTable} del
 * perfil. La clave primaria compuesta de la tabla queda cubierta por el par
 * (usuario_id, tecnologia_id), y por eso la coleccion es un {@code Set}.</p>
 */
@Embeddable
public class TecnologiaDeclaradaEmbeddable {

    @Column(name = "tecnologia_id", nullable = false)
    private short tecnologiaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", nullable = false)
    private NivelTecnologia nivel;

    @Column(name = "anios")
    private Short anios;

    protected TecnologiaDeclaradaEmbeddable() {
        // exigido por JPA
    }

    static TecnologiaDeclaradaEmbeddable de(TecnologiaDeclarada t) {
        TecnologiaDeclaradaEmbeddable e = new TecnologiaDeclaradaEmbeddable();
        e.tecnologiaId = t.tecnologiaId();
        e.nivel = t.nivel();
        e.anios = t.anios();
        return e;
    }

    TecnologiaDeclarada aDominio() {
        return new TecnologiaDeclarada(tecnologiaId, nivel, anios);
    }

    /**
     * La igualdad va solo por {@code tecnologiaId}: es la clave del elemento dentro
     * del perfil. Incluir nivel o anios haria que cambiar el nivel se viera como
     * borrar una fila e insertar otra.
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        return otro instanceof TecnologiaDeclaradaEmbeddable t && tecnologiaId == t.tecnologiaId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tecnologiaId);
    }
}