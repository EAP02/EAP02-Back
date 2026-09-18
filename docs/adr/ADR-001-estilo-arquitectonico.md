# ADR-001: Monolito modular con separación hexagonal por módulo

- **Estado:** Aceptada — con la **regla 1 reemplazada por
  [ADR-006](ADR-006-contratos-entre-modulos-en-api.md)**
- **Fecha:** 2026-09-13
- **Responsable:** _[completar nombre]_ — Arquitectura de Software
- **Historias relacionadas:** transversal a todo el backlog

> **Nota de vigencia (2026-09-18).** La decisión de fondo —monolito modular, cuatro
> paquetes por módulo, reglas 2 a 5— sigue siendo la que está en el código. Cambió
> **dónde se publica el contrato entre módulos**: no en el `domain` del destino, sino en
> su paquete `api`. Ver [ADR-006](ADR-006-contratos-entre-modulos-en-api.md).
>
> Este ADR describe además **nueve módulos**. En el código existen tres de negocio
> (`identity`, `profile`, `project`) más `shared` y `config`. `discussion`,
> `interaction`, `messaging`, `moderation` y `analytics` quedaron fuera del alcance; la
> parte de moderación que sí se implementó —ocultar una publicación con motivo— vive
> dentro de `project`. Los diagramas de `docs/arquitectura/` reflejan lo construido.

## Contexto

El lineamiento (§3.1) exige "una separación explícita mediante arquitectura limpia,
hexagonal, por capas, monolito modular o un microservicio claramente delimitado", y
además pide que el backend esté "organizado en dominios o módulos de negocio y, al
menos, un módulo transversal".

Las restricciones reales del equipo condicionan la elección más que la teoría:

- **Tres personas** para cuatro cursos. Calidad de Software la carga una sola persona,
  lo que hace del costo de prueba y de operación un criterio de primer orden.
- **Tres sprints**. El tiempo perdido en infraestructura no se recupera en funcionalidad.
- El caso tiene **seis áreas funcionales** (perfiles, proyectos, discusiones,
  interacción, mensajería, reportes) que sí son dominios distintos con reglas propias.
  No es un CRUD único.
- El despliegue mínimo aprobado es **Render con contenedores** (§1.3). Kubernetes es
  evolución opcional y el lineamiento advierte que "no debe incorporarse sin capacidad
  de monitoreo y soporte" (§7.3).

La tensión es clara: los dominios están genuinamente separados, pero la capacidad
operativa del equipo no da para separarlos también en tiempo de ejecución.

## Alternativas consideradas

### A. Monolito por capas tradicional (`controller` / `service` / `repository`)

Tres paquetes horizontales y dentro de cada uno todas las clases de todos los dominios.
Es lo más rápido de arrancar y lo que la mayoría de tutoriales de Spring Boot enseña.

El problema es que la separación por capas **no separa dominios**: `PublicacionService`
puede inyectar `MensajeRepository` sin que nada lo impida, y al tercer sprint el grafo
de dependencias es completo. No hay frontera que un evaluador pueda verificar ni que un
test pueda proteger. Satisface la letra de "por capas" pero no el propósito de §3.1.

### B. Microservicios

Un servicio por dominio, cada uno con su base de datos, comunicándose por HTTP o
mensajería.

Es la alternativa que mejor se ve en un diagrama y la que peor se sostiene con este
equipo. Implica: N pipelines, N despliegues en Render, descubrimiento de servicios,
consistencia eventual entre `publicacion` y `reputacion`, trazas distribuidas para
cumplir la correlación por `traceId`, y pruebas de integración que ya no pueden
levantar un solo Testcontainer. El costo cae desproporcionadamente sobre la persona de
Calidad. Con tres sprints, el sprint 3 se consumiría entero en infraestructura y el
proyecto llegaría a la sustentación con funcionalidad incompleta.

### C. Monolito modular con separación hexagonal ligera dentro de cada módulo

Un solo artefacto desplegable, un solo pipeline, una sola base de datos; pero
internamente organizado en módulos de negocio con fronteras explícitas, donde cada
módulo expone puertos (interfaces) y oculta su infraestructura.

## Decisión

Se adopta la **alternativa C**.

El backend es un único Spring Boot organizado así:

```
com.codefactory.devnet
├── shared/          kernel compartido: errores, traceId, auditoría, paginación, seguridad
├── identity/        MÓDULO TRANSVERSAL: autenticación, RBAC, MFA, sesiones
├── profile/         perfiles técnicos, tecnologías, seguimiento
├── project/         proyectos, repositorios, colaboraciones
├── discussion/      discusiones y sus categorías
├── interaction/     comentarios, reacciones, reputación
├── messaging/       conversaciones y mensajes privados
├── moderation/      reportes, revisión, sanciones
└── analytics/       métricas de actividad y crecimiento
```

Y cada módulo se estructura internamente en cuatro paquetes:

| Paquete | Responsabilidad | Puede depender de |
|---|---|---|
| `api` | Controladores REST, DTOs, mapeadores | `application`, `domain` |
| `application` | Casos de uso, orquestación, transacciones | `domain` |
| `domain` | Entidades, objetos de valor, reglas, **puertos** | nada del framework |
| `infrastructure` | Adaptadores JPA, clientes externos, implementación de puertos | `domain` |

**Reglas de dependencia que se consideran parte de la decisión, no recomendaciones:**

1. ~~Un módulo **solo** puede alcanzar a otro a través de una interfaz publicada en el
   `domain` del módulo destino~~, o mediante un evento de aplicación de Spring.
   **Reemplazada por [ADR-006](ADR-006-contratos-entre-modulos-en-api.md):** el contrato
   se publica en `<módulo>.api`; `domain`, `application` e `infrastructure` son internos.
   Los eventos de aplicación siguen siendo una vía válida.
2. Ningún módulo importa el paquete `infrastructure` de otro. Nunca.
3. Las entidades JPA no salen de su módulo ni cruzan la frontera `api`.
4. `shared` no depende de ningún módulo de negocio. La dependencia es siempre hacia adentro.
5. Las dependencias entre módulos son acíclicas.
6. Ningún módulo de negocio depende del paquete `config`. Añadida en el
   [ADR-006](ADR-006-contratos-entre-modulos-en-api.md), después de que un ciclo real
   demostrara que la regla 5 solo detecta el problema cuando ya está formado.

Se elige hexagonal **ligero**: puertos e inversión de dependencia sí, pero sin duplicar
un modelo de dominio anémico junto a uno de persistencia cuando no aporta. La ceremonia
se paga donde hay reglas (`project`, `moderation`, `interaction`) y se reduce donde el
módulo es mayoritariamente lectura (`analytics`).

## Consecuencias

### Positivas

- Un solo pipeline, un solo contenedor, un solo despliegue en Render. La persona que
  carga Calidad puede levantar el sistema completo con un `docker compose up` y un
  único Testcontainer de PostgreSQL.
- Las fronteras son **verificables automáticamente** (ver Verificación), lo que
  convierte el diagrama de paquetes en un contrato ejecutable en vez de un dibujo.
- Transacciones locales: la moderación puede ocultar una publicación y registrar el
  historial de estado en la misma transacción, sin sagas ni compensaciones.
- Deja abierta la ruta de extracción a microservicio sin reescribir: como los módulos ya
  se comunican por puertos, sustituir el adaptador local por uno HTTP es un cambio
  contenido. `messaging` y `analytics` son los candidatos naturales por tener el menor
  acoplamiento de escritura con el resto.

### Negativas y cómo se mitigan

| Riesgo | Mitigación |
|---|---|
| Erosión de las fronteras bajo presión de entrega | Tests de ArchUnit en el pipeline que **rompen el build**, no que avisan |
| Más interfaces y DTOs que en un monolito por capas | Se limita la ceremonia a los módulos con reglas; `analytics` usa proyecciones de lectura directas |
| Escalado solo en bloque: no se puede escalar `messaging` aparte | Aceptado. La línea base académica es de 200 solicitudes por minuto (§4.3); el escalado selectivo no está justificado por evidencia |
| Una sola base de datos, un solo punto de falla | Aceptado para el alcance académico; se documenta el respaldo y la restauración en el plan de operación |

## Verificación

La decisión se comprueba con **ArchUnit** ejecutándose en cada build:

```java
@AnalyzeClasses(packages = "com.codefactory.devnet")
class ArquitecturaTest {

    @ArchTest
    static final ArchRule los_modulos_no_acceden_a_infraestructura_ajena =
        noClasses().that().resideInAPackage("..devnet.(*)..")
            .should().dependOnClassesThat()
            .resideInAPackage("..devnet.(*).infrastructure..");

    @ArchTest
    static final ArchRule el_dominio_no_depende_del_framework =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule sin_ciclos_entre_modulos =
        slices().matching("..devnet.(*)..").should().beFreeOfCycles();
}
```

Si el diagrama de paquetes de `docs/arquitectura/uml-paquetes.puml` deja de coincidir
con lo que estas reglas permiten, uno de los dos está mal y hay que corregirlo en el
mismo sprint (§4.4).