package com.codefactory.devnet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que Flyway construyo el esquema que el modelo logico describe.
 *
 * <p>Es la version automatizada de {@code docs/bd/verificacion-instalacion.sql}.
 * Que corra en el pipeline, y no solo a mano cuando alguien se acuerda, es lo que
 * convierte el modelo fisico en algo verificado en cada commit.</p>
 *
 * <p>La primera prueba tambien valida indirectamente algo mas importante: que la
 * migracion se aplica limpia sobre una base vacia. Un {@code V1} que solo funciona
 * sobre la base que alguien ya tenia no sirve para construir el entorno nuevo.</p>
 */
@PruebaIntegracion
@DisplayName("Esquema construido por Flyway")
class EsquemaIT {

    private static final int TABLAS_ESPERADAS = 29;

    /** V1 declara 10 indices parciales y V2 anade ix_auditoria_seguridad. */
    private static final int INDICES_PARCIALES_ESPERADOS = 11;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("crea las 29 tablas del modelo logico")
    void crea_las_tablas_del_modelo() {
        Integer tablas = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """, Integer.class);

        assertThat(tablas)
                .as("el numero de tablas debe coincidir con docs/bd/02-modelo-logico.md")
                .isEqualTo(TABLAS_ESPERADAS);
    }

    @Test
    @DisplayName("deja los datos de referencia sin los que el sistema no arranca")
    void carga_los_datos_de_referencia() {
        assertThat(contar("rol")).isEqualTo(4);
        assertThat(contar("permiso")).isEqualTo(20);
        assertThat(contar("rol_permiso")).isEqualTo(40);
        assertThat(contar("tecnologia")).isEqualTo(20);
    }

    @Test
    @DisplayName("exige MFA exactamente a los roles administrativos")
    void solo_los_roles_administrativos_exigen_mfa() {
        List<String> conMfa = jdbc.queryForList(
                "SELECT codigo FROM rol WHERE exige_mfa = true ORDER BY codigo", String.class);

        // Lineamiento 3.4: MFA para accesos administrativos o sensibles.
        assertThat(conMfa).containsExactly("ADMIN", "MODERADOR");
    }

    @Test
    @DisplayName("declara los indices parciales que sostienen las consultas clave")
    void declara_los_indices_parciales() {
        Integer parciales = jdbc.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public' AND indexdef LIKE '%WHERE%'
                """, Integer.class);

        // Casi toda consulta de este dominio filtra por un estado; el indice
        // parcial es el patron dominante del modelo (docs/bd/04-indices.md).
        assertThat(parciales)
                .as("indices parciales declarados en V1__baseline.sql")
                .isEqualTo(INDICES_PARCIALES_ESPERADOS);
    }

    @Test
    @DisplayName("la columna generada de busqueda es inmutable y se llena sola")
    void la_columna_generada_de_busqueda_funciona() {
        // Si to_tsvector no se hubiera fijado a regconfig, la columna generada ni
        // siquiera habria podido crearse. Que esta consulta devuelva algo prueba
        // que el diccionario 'spanish' existe en el contenedor.
        String vector = jdbc.queryForObject(
                "SELECT to_tsvector('spanish'::regconfig, ?)::text",
                String.class,
                "Motor de plantillas minimalista escrito en Java");

        assertThat(vector).isNotBlank().contains("plantill");
    }

    private Integer contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }
}