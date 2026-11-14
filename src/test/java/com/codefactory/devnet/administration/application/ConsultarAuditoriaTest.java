package com.codefactory.devnet.administration.application;

import com.codefactory.devnet.administration.domain.CodigoErrorAdministracion;
import com.codefactory.devnet.administration.domain.FiltroAuditoria;
import com.codefactory.devnet.administration.domain.Pagina;
import com.codefactory.devnet.administration.domain.Paginacion;
import com.codefactory.devnet.administration.domain.RegistroAuditado;
import com.codefactory.devnet.administration.domain.RepositorioAuditoria;
import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("HU-20 ConsultarAuditoria")
class ConsultarAuditoriaTest {

    private RepositorioAuditoria repositorio;
    private UsuarioDirectorio directorio;
    private ConsultarAuditoria consultar;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioAuditoria.class);
        directorio = mock(UsuarioDirectorio.class);
        consultar = new ConsultarAuditoria(repositorio, directorio);
    }

    @Test
    @DisplayName("cada registro lleva quien, que, cuando y sobre que objeto")
    void incluye_quien_que_cuando_y_objeto() {
        // Arrange
        UUID ana = UUID.randomUUID();
        Instant cuando = Instant.parse("2026-11-10T15:00:00Z");
        when(repositorio.buscar(any(), any())).thenReturn(pagina(
                fila(1, "DELETE", "comentario", "c-1", ana, cuando)));
        when(directorio.buscar(ana)).thenReturn(Optional.of(resumen(ana, "ana_dev")));

        // Act
        Pagina<ConsultarAuditoria.Registro> resultado =
                consultar.ejecutar(null, null, null, null, null, 0, 20);

        // Assert
        ConsultarAuditoria.Registro r = resultado.contenido().get(0);
        assertThat(r.accion()).isEqualTo("DELETE");
        assertThat(r.tabla()).isEqualTo("comentario");
        assertThat(r.registroId()).isEqualTo("c-1");
        assertThat(r.actorId()).isEqualTo(ana);
        assertThat(r.actorNombre()).isEqualTo("ana_dev");
        assertThat(r.fecha()).isEqualTo(cuando);
    }

    @Test
    @DisplayName("pide el nombre una sola vez por actor, no una vez por fila")
    void una_consulta_por_actor_distinto() {
        // Arrange
        UUID ana = UUID.randomUUID();
        when(repositorio.buscar(any(), any())).thenReturn(pagina(
                fila(3, "INICIO_SESION", "usuario", "a", ana, Instant.now()),
                fila(2, "INICIO_SESION", "usuario", "a", ana, Instant.now()),
                fila(1, "INICIO_SESION", "usuario", "a", ana, Instant.now())));
        when(directorio.buscar(ana)).thenReturn(Optional.of(resumen(ana, "ana_dev")));

        // Act
        consultar.ejecutar(null, null, null, null, null, 0, 20);

        // Assert
        verify(directorio, times(1)).buscar(ana);
    }

    @Test
    @DisplayName("un actor anonimo (inicio de sesion fallido) sale sin actor y no consulta el directorio")
    void actor_anonimo() {
        // Arrange
        when(repositorio.buscar(any(), any())).thenReturn(pagina(
                fila(1, "INICIO_SESION_FALLIDO", "usuario", "alguien@correo.test", null, Instant.now())));

        // Act
        ConsultarAuditoria.Registro r =
                consultar.ejecutar(null, null, null, null, null, 0, 20).contenido().get(0);

        // Assert
        assertThat(r.actorId()).isNull();
        assertThat(r.actorNombre()).isNull();
        verify(directorio, never()).buscar(any());
    }

    @Test
    @DisplayName("si la cuenta del actor ya no existe, se conserva el id y el nombre va nulo")
    void actor_que_ya_no_existe() {
        // Arrange
        UUID borrado = UUID.randomUUID();
        when(repositorio.buscar(any(), any())).thenReturn(pagina(
                fila(1, "UPDATE", "usuario", "x", borrado, Instant.now())));
        when(directorio.buscar(borrado)).thenReturn(Optional.empty());

        // Act
        ConsultarAuditoria.Registro r =
                consultar.ejecutar(null, null, null, null, null, 0, 20).contenido().get(0);

        // Assert
        assertThat(r.actorId()).isEqualTo(borrado);
        assertThat(r.actorNombre()).isNull();
    }

    @Test
    @DisplayName("sin actividad devuelve una pagina vacia, no un error (estado vacio)")
    void sin_resultados_es_pagina_vacia() {
        // Arrange
        when(repositorio.buscar(any(), any())).thenReturn(new Pagina<>(List.of(), 0, 20, 0));

        // Act
        Pagina<ConsultarAuditoria.Registro> resultado =
                consultar.ejecutar(null, null, null, null, null, 0, 20);

        // Assert
        assertThat(resultado.contenido()).isEmpty();
        assertThat(resultado.total()).isZero();
    }

    @Test
    @DisplayName("un rango invertido se rechaza antes de tocar la base")
    void rango_invertido_no_llega_a_la_base() {
        assertThatThrownBy(() -> consultar.ejecutar(null, null, null,
                LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 1), 0, 20))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, ex ->
                        assertThat(ex.codigo()).isEqualTo(CodigoErrorAdministracion.RANGO_FECHAS_INVALIDO));

        verify(repositorio, never()).buscar(any(FiltroAuditoria.class), any(Paginacion.class));
    }

    // ------------------------------------------------------------------

    private static Pagina<RegistroAuditado> pagina(RegistroAuditado... filas) {
        return new Pagina<>(List.of(filas), 0, 20, filas.length);
    }

    private static RegistroAuditado fila(long id, String operacion, String tabla, String registroId,
                                         UUID actor, Instant cuando) {
        return new RegistroAuditado(id, cuando, operacion, tabla, registroId, actor,
                null, null, "trace-" + id, null, "devnet_app");
    }

    private static UsuarioDirectorio.UsuarioResumen resumen(UUID id, String nombre) {
        return new UsuarioDirectorio.UsuarioResumen(id, nombre, true, Set.of());
    }
}
