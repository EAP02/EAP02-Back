package com.codefactory.devnet.arquitectura;

import com.codefactory.devnet.shared.api.RespuestaError;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.time.Instant;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Convierte las fronteras de ADR-001 en un test que rompe el build.
 *
 * <p>Un diagrama de paquetes que nadie verifica se erosiona en el segundo sprint,
 * normalmente bajo presion de entrega y con la mejor intencion. Estas reglas son la
 * diferencia entre una arquitectura documentada y una arquitectura real.</p>
 *
 * <p>Si una regla estorba, la respuesta no es relajarla: es revisar si la decision
 * de ADR-001 sigue siendo la correcta y, si no lo es, escribir un ADR que la
 * reemplace. El registro de decisiones existe justamente para eso.</p>
 */
@AnalyzeClasses(
        packages = "com.codefactory.devnet",
        importOptions = ImportOption.DoNotIncludeTests.class)
class FronterasModularesTest {

    private static final String RAIZ = "com.codefactory.devnet";

    /** Los siete modulos de negocio del diagrama de paquetes. */
    private static final String[] MODULOS = {
            "identity", "profile", "project", "discussion", "interaction", "messaging", "analytics"
    };

    // ------------------------------------------------------------------
    // Regla 1 - Ningun modulo de negocio importa otro modulo de negocio
    // ------------------------------------------------------------------

    /**
     * La regla central. Los modulos se comunican exclusivamente por los contratos de
     * {@code shared.integration}, nunca por importacion directa.
     *
     * <p>Se comprueba por pares en vez de con una unica expresion porque el mensaje
     * de fallo nombra el par concreto que la viola, que es lo que alguien necesita
     * leer a las dos de la manana.</p>
     */
    @ArchTest
    static void ningun_modulo_importa_otro_modulo(JavaClasses clases) {
        for (String origen : MODULOS) {
            for (String destino : MODULOS) {
                if (origen.equals(destino)) {
                    continue;
                }
                noClasses()
                        .that().resideInAPackage(RAIZ + "." + origen + "..")
                        .should().dependOnClassesThat()
                        .resideInAPackage(RAIZ + "." + destino + "..")
                        .because("'%s' debe hablar con '%s' a traves de una interfaz de %s.shared.integration, no importandolo (ADR-001)"
                                .formatted(origen, destino, RAIZ))
                        .check(clases);
            }
        }
    }

    // ------------------------------------------------------------------
    // Regla 2 - El dominio no conoce el framework
    // ------------------------------------------------------------------

    /**
     * Mantener el dominio libre de Spring y de JPA es lo que permite probar las
     * reglas de negocio sin levantar un contexto ni una base de datos. Es tambien lo
     * que hace creible la palabra "hexagonal" en el ADR.
     *
     * <p>Se admite {@code shared.api} porque {@code ExcepcionNegocio} vive alli, y
     * se admiten las anotaciones de validacion de Jakarta, que son declarativas y no
     * arrastran infraestructura.</p>
     */
    @ArchTest
    static final ArchRule el_dominio_no_depende_del_framework =
            noClasses()
                    .that().resideInAPackage(RAIZ + "..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.servlet..",
                            // Jackson 3 (Boot 4) publica bajo tools.jackson; se deja
                            // tambien com.fasterxml por si alguna libreria arrastra
                            // la linea 2.x al classpath.
                            "tools.jackson..",
                            "com.fasterxml.jackson..")
                    .because("la capa domain debe poder probarse sin contexto de Spring ni base de datos (ADR-001)");

    // ------------------------------------------------------------------
    // Regla 3 - Direccion de las capas dentro de cada modulo
    // ------------------------------------------------------------------

    @ArchTest
    static final ArchRule la_capa_api_no_toca_infraestructura =
            noClasses()
                    .that().resideInAPackage(RAIZ + "..api..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(RAIZ + "..infrastructure..")
                    .because("un controlador orquesta casos de uso, no adaptadores de persistencia");

    @ArchTest
    static final ArchRule la_capa_application_no_toca_infraestructura =
            noClasses()
                    .that().resideInAPackage(RAIZ + "..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(RAIZ + "..infrastructure..")
                    .because("la inversion de dependencia exige que application dependa de los puertos del dominio, no de sus implementaciones");

    // ------------------------------------------------------------------
    // Regla 4 - El kernel compartido no conoce el negocio
    // ------------------------------------------------------------------

    /**
     * La dependencia es siempre hacia adentro. Si {@code shared} llegara a importar
     * un modulo, dejaria de ser un kernel y se convertiria en un modulo mas, con lo
     * que el grafo perderia su raiz.
     */
    @ArchTest
    static void shared_no_depende_de_ningun_modulo(JavaClasses clases) {
        for (String modulo : MODULOS) {
            noClasses()
                    .that().resideInAPackage(RAIZ + ".shared..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(RAIZ + "." + modulo + "..")
                    .because("shared es el kernel: la dependencia va de los modulos hacia el, nunca al reves (ADR-001)")
                    .check(clases);
        }
    }

    // ------------------------------------------------------------------
    // Regla 5 - Sin ciclos
    // ------------------------------------------------------------------

    @ArchTest
    static final ArchRule el_grafo_de_modulos_es_aciclico =
            slices()
                    .matching(RAIZ + ".(*)..")
                    .should().beFreeOfCycles()
                    .because("un ciclo entre modulos hace imposible extraer cualquiera de ellos y convierte el monolito modular en un monolito a secas");

    // ------------------------------------------------------------------
    // Regla 6 - Contratos de integracion limpios
    // ------------------------------------------------------------------

    /**
     * Lo que cruza la frontera son copias inmutables, no filas de la base. Si una
     * entidad JPA se filtrara por aqui, dos modulos compartirian el mismo objeto
     * gestionado y un cambio en uno se propagaria al otro sin que nadie lo pidiera.
     */
    @ArchTest
    static final ArchRule los_contratos_no_exponen_entidades_jpa =
            noClasses()
                    .that().resideInAPackage(RAIZ + ".shared.integration..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.persistence..", "org.springframework..")
                    .because("shared.integration solo declara interfaces y records inmutables");

    // ------------------------------------------------------------------
    // Regla 7 - Un solo lugar construye respuestas de error
    // ------------------------------------------------------------------

    /**
     * Respalda la exigencia de ADR-005 de que el formato de error sea uniforme. Sin
     * esta regla basta con que un modulo tenga prisa para que aparezca un segundo
     * formato, y el contrato deja de ser uno.
     */
    /**
     * Comprueba la <b>construccion</b>, no la mencion.
     *
     * <p>La version anterior usaba {@code dependOnClassesThat}, y eso marcaba como
     * violacion los {@code @ApiResponse(content = @Content(schema = @Schema(
     * implementation = RespuestaError.class)))} de los controladores. Pero eso es
     * documentacion del contrato, que es justo lo que queremos que hagan: declarar
     * que sus errores tienen la forma comun. Lo que debe prohibirse es que un modulo
     * <i>fabrique</i> el objeto.</p>
     *
     * <p>La firma se enumera explicitamente porque {@code callConstructor} la exige.
     * Si {@code RespuestaError} gana o pierde un campo, hay que actualizarla aqui o
     * la regla dejara de vigilar nada.</p>
     */
    @ArchTest
    static final ArchRule solo_el_kernel_construye_respuestas_de_error =
            noClasses()
                    .that().resideOutsideOfPackage(RAIZ + ".shared.api..")
                    .should().callConstructor(
                            RespuestaError.class,
                            String.class,     // errorCode
                            String.class,     // message
                            List.class,       // details
                            String.class,     // traceId
                            Instant.class,    // timestamp
                            String.class)     // path
                    .because("toda respuesta de error sale del manejador global; un modulo que la construya por su cuenta rompe la uniformidad del contrato (ADR-005)");
}