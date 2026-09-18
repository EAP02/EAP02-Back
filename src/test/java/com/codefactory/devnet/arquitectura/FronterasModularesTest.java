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
 * Convierte las fronteras entre modulos en un test que rompe el build.
 *
 * <p>Un diagrama de paquetes que nadie verifica se erosiona en el segundo sprint,
 * normalmente bajo presion de entrega y con la mejor intencion. Estas reglas son la
 * diferencia entre una arquitectura documentada y una arquitectura real.</p>
 *
 * <h2>Nota sobre donde viven los contratos</h2>
 *
 * <p>ADR-001 planteaba publicar los puertos entre modulos en {@code shared.integration}.
 * La integracion de modulos adopto otra convencion: <b>cada modulo publica su contrato
 * en su propio paquete {@code api}</b>, y los demas solo pueden alcanzarlo por ahi.</p>
 *
 * <p>Las dos son defendibles y esta segunda es la que esta en el codigo, asi que las
 * reglas la verifican. Queda registrada en el <b>ADR-006</b>, que supersede la regla 1
 * del ADR-001.</p>
 *
 * <p>Si una regla estorba, la respuesta no es relajarla: es revisar si la decision
 * sigue siendo la correcta y, si no lo es, escribir el ADR que la reemplace.</p>
 */
@AnalyzeClasses(
        packages = "com.codefactory.devnet",
        importOptions = ImportOption.DoNotIncludeTests.class)
class FronterasModularesTest {

    private static final String RAIZ = "com.codefactory.devnet";

    /** Modulos de negocio con codigo. Los vacios se anaden al implementarlos. */
    private static final String[] MODULOS = {"identity", "profile", "project"};

    // ------------------------------------------------------------------
    // Regla 1 - Un modulo solo alcanza a otro por su paquete api
    // ------------------------------------------------------------------

    /**
     * La regla central. Un modulo puede consumir el contrato publicado por otro, pero
     * nunca sus entidades, sus casos de uso ni sus adaptadores.
     *
     * <p>Es lo que permite cambiar por dentro un modulo sin romper a los demas: si
     * {@code project} pudiera tocar {@code identity.infrastructure}, renombrar una
     * columna de {@code UsuarioEntity} rompería proyectos.</p>
     *
     * <p>Se comprueba por pares en vez de con una unica expresion porque el mensaje de
     * fallo nombra el par concreto que la viola, que es lo que alguien necesita leer a
     * las dos de la manana.</p>
     */
    @ArchTest
    static void un_modulo_solo_alcanza_a_otro_por_su_api(JavaClasses clases) {
        for (String origen : MODULOS) {
            for (String destino : MODULOS) {
                if (origen.equals(destino)) {
                    continue;
                }
                noClasses()
                        .that().resideInAPackage(RAIZ + "." + origen + "..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(
                                RAIZ + "." + destino + ".domain..",
                                RAIZ + "." + destino + ".application..",
                                RAIZ + "." + destino + ".infrastructure..")
                        .because("'%s' solo puede consumir el contrato publicado en '%s.%s.api', nunca sus tripas"
                                .formatted(origen, RAIZ, destino))
                        .check(clases);
            }
        }
    }

    // ------------------------------------------------------------------
    // Regla 2 - El dominio no conoce el framework
    // ------------------------------------------------------------------

    /**
     * Mantener el dominio libre de Spring y de JPA es lo que permite probar las reglas
     * de negocio sin levantar contexto ni base de datos, como hacen {@code PerfilTest},
     * {@code UsuarioTest} y {@code ProyectoTest}.
     *
     * <p>Fue esta regla la que detecto que los {@code enum} de codigos de error
     * llevaban {@code HttpStatus} de Spring dentro de {@code domain}. Hoy declaran el
     * estado como {@code int}.</p>
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
                    .because("la capa domain debe poder probarse sin contexto de Spring ni base de datos");

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
     * La dependencia es siempre hacia adentro. Si {@code shared} llegara a importar un
     * modulo, dejaria de ser un kernel y se convertiria en un modulo mas, con lo que el
     * grafo perderia su raiz.
     *
     * <p>Por esta regla el manejador global de errores lee el actor del
     * {@code SecurityContext} en vez de inyectar {@code UsuarioDirectorio}.</p>
     */
    @ArchTest
    static void shared_no_depende_de_ningun_modulo(JavaClasses clases) {
        for (String modulo : MODULOS) {
            noClasses()
                    .that().resideInAPackage(RAIZ + ".shared..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(RAIZ + "." + modulo + "..")
                    .because("shared es el kernel: la dependencia va de los modulos hacia el, nunca al reves")
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
    // Regla 6 - Un solo lugar construye respuestas de error
    // ------------------------------------------------------------------

    /**
     * Comprueba la <b>construccion</b>, no la mencion: los controladores si pueden
     * nombrar {@code RespuestaError} en sus anotaciones {@code @Schema}, porque eso es
     * documentar el contrato, que es justo lo que queremos que hagan.
     *
     * <p>La firma se enumera explicitamente porque {@code callConstructor} la exige. Si
     * {@code RespuestaError} gana o pierde un campo, hay que actualizarla aqui o la
     * regla dejara de vigilar nada.</p>
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
                    .because("toda respuesta de error sale del manejador global; un modulo que la construya por su cuenta rompe la uniformidad del contrato");

    // ------------------------------------------------------------------
    // Regla 7 - Ningun modulo de negocio depende de config
    // ------------------------------------------------------------------

    /**
     * {@code config} cablea el arranque: la cadena de seguridad, el gestor de caches, el
     * par RSA, el contrato OpenAPI. Para eso necesita conocer piezas de todas partes, asi
     * que es el candidato natural a cerrar un ciclo.
     *
     * <p>Ya ocurrio: un {@code @Configuration} que sembraba datos de referencia importaba
     * {@code identity.infrastructure} y {@code project.infrastructure}, y como
     * {@code identity} leia a su vez las propiedades desde {@code config}, el grafo quedo
     * con el ciclo {@code config <-> identity}. Se resolvio moviendo esos datos a la
     * migracion {@code V1__baseline.sql}.</p>
     *
     * <p>La regla 5 detecta el ciclo cuando ya esta formado. Esta lo previene: cortando
     * la direccion {@code modulo -> config} el ciclo no se puede cerrar por mucho que
     * {@code config} mire hacia abajo. Lo que un modulo necesite de la configuracion se
     * publica en {@code shared} —{@code PropiedadesDevNet}, {@code CachesDevNet}—, donde
     * la regla 4 garantiza que no hay camino de vuelta.</p>
     */
    @ArchTest
    static void ningun_modulo_depende_de_config(JavaClasses clases) {
        for (String modulo : MODULOS) {
            noClasses()
                    .that().resideInAPackage(RAIZ + "." + modulo + "..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(RAIZ + ".config..")
                    .because("config es quien cablea el arranque y mira hacia todos los modulos; "
                            + "si un modulo mira de vuelta, el ciclo esta servido. "
                            + "Lo que el modulo necesite va en shared")
                    .check(clases);
        }
    }
}