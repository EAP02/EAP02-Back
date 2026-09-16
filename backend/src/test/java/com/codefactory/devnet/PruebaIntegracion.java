package com.codefactory.devnet;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca una clase como prueba de integracion.
 *
 * <p>Levanta el contexto completo de Spring contra un PostgreSQL 16 real con las
 * migraciones de Flyway ya aplicadas, tal como exige el lineamiento 3.5
 * ("integracion con base de datos real o contenedor").</p>
 *
 * <pre>
 * &#64;PruebaIntegracion
 * class RegistrarUsuarioIT {
 *
 *     &#64;Autowired MockMvc mvc;
 *
 *     &#64;Test
 *     void registra_un_usuario_con_perfil_tecnico() throws Exception { ... }
 * }
 * </pre>
 *
 * <p>{@code @Transactional} revierte cada prueba al terminar, de modo que el orden
 * de ejecucion no importa y no hay que limpiar tablas a mano. Si una prueba
 * necesita verificar el commit real, se anota con
 * {@code @Transactional(propagation = NOT_SUPPORTED)} y limpia lo suyo.</p>
 *
 * <p><b>Requiere Docker en ejecucion.</b> Las pruebas unitarias del dominio no
 * dependen de esto y siguen corriendo sin el: esa separacion evita que un fallo de
 * infraestructura se lea como un fallo de logica.</p>
 *
 * <p>Convencion de nombres: las clases que usan esta anotacion terminan en
 * {@code IT}, para que el perfil {@code ci} las ejecute con Failsafe y separadas de
 * las unitarias.</p>
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
// Boot 4 ya no expone MockMvc solo con @SpringBootTest: hay que pedirlo
// explicitamente. Se incluye aqui para que ninguna prueba tenga que acordarse.
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfiguracionContenedores.class)
@Transactional
public @interface PruebaIntegracion {
}