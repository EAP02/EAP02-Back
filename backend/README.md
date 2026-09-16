# DevNet — backend

Spring Boot 4.0.8 · Java 17 · PostgreSQL 16. Monolito modular con siete módulos de
negocio y un kernel compartido ([ADR-001](../docs/adr/ADR-001-estilo-arquitectonico.md)).

---

## Arrancar

```bash
# 1. Base de datos local
docker compose up -d

# 2. Aplicación
./mvnw spring-boot:run
```

- API: <http://localhost:8080/api/v1>
- Contrato: <http://localhost:8080/swagger-ui.html>
- Salud: <http://localhost:8080/actuator/health>

**Requisitos:** JDK 17 y Docker. Todo el equipo con la **misma** versión mayor: con
JDK 11 o 21 en su lugar, el build falla o genera bytecode distinto.

```powershell
winget install Microsoft.OpenJDK.17
winget install Docker.DockerDesktop
```

> Java 17 es el baseline de Spring Boot 4.0 y la versión acordada por el equipo.
> Boot 4 recomienda 21 por los virtual threads, que aquí están desactivados de todas
> formas — ver [`application.yml`](src/main/resources/application.yml).

## Comandos

| Qué | Comando |
|---|---|
| Solo unitarias (sin Docker) | `./mvnw test` |
| Todo, con cobertura y umbral | `./mvnw verify` |
| Una clase | `./mvnw test -Dtest=PaginaKeysetTest` |
| Solo las reglas de arquitectura | `./mvnw test -Dtest=FronterasModularesTest` |
| Empaquetar | `./mvnw clean package` |
| Imagen como se despliega | `docker compose --profile completo up --build` |

El reporte de cobertura queda en `target/site/jacoco/index.html`.

---

## Estructura

```
com.codefactory.devnet
├── config/                 SeguridadConfig, OpenApiConfig, CacheConfig, PropiedadesDevNet
├── shared/
│   ├── api/                RespuestaError, ManejadorGlobalErrores, FiltroTraceId, PaginaKeyset
│   ├── integration/        ← contratos ENTRE módulos (los puertos del diagrama)
│   └── audit/              registro de auditoría
├── identity/               transversal: auth, tokens, MFA, RBAC
├── profile/                perfiles, tecnologías, seguimiento
├── project/                proyectos, repositorios, colaboraciones
├── discussion/             hilos y categorías
├── interaction/            comentarios, reacciones, reputación
├── messaging/              conversaciones privadas
└── analytics/              reportes (solo lectura, sin capa domain)
```

Cada módulo de negocio tiene cuatro capas:

```
api/            controladores REST, DTOs, mapeadores   → application, domain
application/    casos de uso, transacciones            → domain
domain/         entidades, reglas, políticas           → nada del framework
infrastructure/ adaptadores JPA, clientes externos     → domain
```

### La regla que no se negocia

**Ningún módulo importa otro módulo.** `interaction` no conoce
`com.codefactory.devnet.project`; conoce `IContenidoInteractuable`, que vive en
`shared.integration`.

Esto refina lo que decía ADR-001 (publicar cada puerto en el `domain` del módulo
destino). Se rompía en cuanto dos módulos debían ofrecer la misma capacidad:
`interaction` comenta proyectos **y** discusiones, y el puerto habría tenido que
declararse dos veces con el mismo nombre y tipos incompatibles. Centralizarlos hace
además que la verificación sea una sola prohibición en vez de una lista de pares
permitidos.

Los cuatro contratos, los del diagrama de componentes:

| Contrato | Lo provee | Lo consume |
|---|---|---|
| `IUsuarioDirectorio` | `identity` | todos |
| `IPerfilConsulta` | `profile` | `project`, `discussion`, `interaction`, `messaging` |
| `IContenidoInteractuable` | `project` y `discussion` | `interaction` |
| `IAlmacenArchivos` | `shared` (adaptador Supabase) | `profile` |

`FronterasModularesTest` rompe el build si alguna se viola. Si una regla estorba, la
respuesta no es relajarla: es escribir un ADR que reemplace la decisión.

---

## Cómo añadir una historia de usuario

Rebanada vertical, siempre en este orden. Empezar por la entidad JPA lleva a un
dominio anémico con la lógica repartida por los servicios.

1. **`domain/`** — la entidad con sus reglas y las excepciones que lanza. Sin Spring
   ni JPA. Prueba unitaria pura, sin contexto.
2. **`domain/`** — el puerto de repositorio que el caso de uso necesita (interfaz).
3. **`application/`** — el caso de uso. `@Service`, `@Transactional`, orquesta y
   valida autorización. Prueba con el repositorio simulado.
4. **`infrastructure/`** — la entidad JPA y el adaptador que implementa el puerto.
5. **`api/`** — controlador, DTOs de petición y respuesta, `@PreAuthorize`.
   Prueba con `@PruebaIntegracion` y `MockMvc`.
6. **Migración** — si hace falta esquema nuevo, un `V<n>__<descripcion>.sql`.
   **Nunca** se edita una migración ya aplicada; se corrige con la siguiente.

### Códigos de error

Cada módulo declara su `enum` de códigos implementando `CodigoError`, con prefijo
propio y el estado HTTP decidido ahí mismo:

```java
public enum CodigoErrorProyecto implements CodigoError {

    PROYECTO_CUPO_LLENO(HttpStatus.UNPROCESSABLE_ENTITY,
        "Este proyecto ya alcanzo su numero maximo de colaboradores."),

    PROYECTO_NO_BUSCA_COLABORADORES(HttpStatus.CONFLICT,
        "Este proyecto no esta buscando colaboradores en este momento.");
    ...
}
```

Y se lanza así:

```java
throw new ExcepcionNegocio(CodigoErrorProyecto.PROYECTO_CUPO_LLENO);
```

Nunca se construye una `RespuestaError` a mano: la arma el manejador global, y hay
una regla de ArchUnit que lo impide.

**409 vs 422:** `409` es conflicto con el *estado actual* del recurso (la máquina de
estados no admite la transición). `422` es una regla de negocio que la petición
incumple con independencia del estado (cupo lleno, reputación insuficiente).
Distinguirlos evita el `400` para todo. Mapa completo en
[ADR-005](../docs/adr/ADR-005-contrato-errores-traceid.md).

---

## Base de datos

El esquema lo construye **solo Flyway**, desde `src/main/resources/db/migration/`.
Hibernate corre siempre con `ddl-auto=validate`: si una entidad JPA no corresponde a
lo que Flyway construyó, la aplicación no arranca.

Los tests de integración levantan PostgreSQL 16 real con Testcontainers y aplican las
mismas migraciones. Nada de H2: el modelo usa columnas generadas `tsvector`, índices
parciales y CTE recursivos, y otro dialecto escondería justo los errores que estas
pruebas existen para encontrar.

---

## Estado actual

| Listo | Pendiente |
|---|---|
| Estructura de los 7 módulos y el kernel | Emisión y validación de JWT |
| Contrato de errores con `traceId` | Login con GitHub |
| Los 4 contratos de `shared.integration` | MFA TOTP |
| Reglas de ArchUnit | Adaptador real de Supabase Storage |
| OpenAPI, CORS, Argon2id, caché | Las entidades JPA de cada módulo |
| Docker, compose, pipeline | Primera HU de punta a punta |

La cadena de seguridad está montada pero **la validación de JWT todavía no está
conectada**: los endpoints públicos responden y el resto devuelve `401` a través del
manejador global. Es intencional — permite trabajar sobre los demás módulos sin
esperar a la historia de autenticación.

El umbral de cobertura arranca en `0.00` y sube por sprint (`0.40` en el 2, `0.65` en
el 3). Está explicado en el `pom.xml`, junto a la propiedad.

---

## Notas de Spring Boot 4

Si buscas ayuda en tutoriales o respuestas escritas para Boot 3, esto es lo que cambió
y te va a morder:

| Boot 3 | Boot 4 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `flyway-core` suelto | `spring-boot-starter-flyway` (el starter es obligatorio) |
| `spring-boot-starter-oauth2-client` | `spring-boot-starter-security-oauth2-client` |
| `@SpringBootTest` traía `MockMvc` | hay que añadir `@AutoConfigureMockMvc` |
| `@MockBean` / `@SpyBean` | `@MockitoBean` / `@MockitoSpyBean` (los viejos ya no existen) |
| `spring.jackson.serialization.*` | `spring.jackson.json.write.*` |
| `spring.jackson.deserialization.*` | `spring.jackson.json.read.*` |
| Jackson 2 (`com.fasterxml.jackson`) | Jackson 3 (`tools.jackson`) |

`@AutoConfigureMockMvc` ya viene dentro de `@PruebaIntegracion`, así que tus pruebas
no tienen que acordarse.

**Dos cosas a verificar en el primer build**, que no puedo comprobar sin JDK instalado:

1. `logstash-logback-encoder` todavía depende de Jackson 2 mientras Boot 4 gestiona
   Jackson 3. Conviven, pero dejan dos Jackson en el classpath. Si da guerra, la
   salida es el soporte de logging estructurado que Boot trae de serie, ajustando
   `logback-spring.xml`.
2. `springdoc-openapi` 3.1.1 es la línea que acompaña a Boot 4, pero está anunciada
   para Java 21+. Si rechaza el 17, hay que bajar a la última 3.0.x.