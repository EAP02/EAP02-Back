# ADR-006: El contrato entre módulos se publica en `<módulo>.api`

- **Estado:** Aceptada
- **Fecha:** 2026-09-18
- **Responsable:** _[completar nombre]_ — Arquitectura de Software
- **Historias relacionadas:** transversal; surge de la integración de módulos

**Supersede la regla 1 del [ADR-001](ADR-001-estilo-arquitectonico.md).** El resto de
aquella decisión —monolito modular, cuatro paquetes por módulo, reglas 2 a 5— sigue
íntegra.

## Contexto

El ADR-001 fijó cinco reglas de dependencia. La primera decía:

> Un módulo **solo** puede alcanzar a otro a través de una interfaz publicada en el
> `domain` del módulo destino, o mediante un evento de aplicación de Spring.

Al integrar los módulos apareció el problema. Un puerto en `domain` sirve para que el
módulo **dueño** invierta una dependencia hacia fuera: `RepositorioUsuarios` vive en
`identity.domain` porque es `identity` quien lo necesita y `identity.infrastructure`
quien lo implementa. Esa es su razón de ser y está bien donde está.

Pero un contrato **para terceros** es otra cosa. Cuando `project` necesita saber quién
está autenticado, lo que consume no es una dependencia invertida de `identity`: es una
capacidad que `identity` ofrece. Ponerla en `identity.domain` obliga a que `project`
importe el dominio ajeno, y con él quedan a la vista las entidades, los objetos de valor
y los puertos internos que no son asunto suyo. La frontera pasa a depender de que nadie
toque lo que no debe.

Hay un segundo efecto, más concreto. El tipo que cruza la frontera acaba siendo el del
dominio ajeno —`Usuario`, con sus reglas y su estado—, y entonces renombrar un método
interno de `identity` rompe la compilación de `project`. El acoplamiento que la regla
pretendía evitar se cuela por la puerta que ella misma abre.

## Alternativas consideradas

### A. Mantener los puertos en `<módulo>.domain`

Lo que decía el ADR-001. La regla es fácil de enunciar, pero no es verificable de forma
útil: no existe manera de distinguir automáticamente «esta interfaz de `domain` es
pública» de «esta es interna». Una regla de ArchUnit solo puede permitir el paquete
entero o prohibirlo entero, y permitirlo entero no protege nada.

### B. Un paquete común `shared.integration`

Todos los contratos entre módulos en un único sitio del kernel. Tiene la ventaja de que
el catálogo de lo que se puede consumir se lee de un vistazo.

El problema es que convierte `shared` en el punto por el que pasa todo. Cada módulo
nuevo añade sus interfaces ahí y sus DTOs con ellas, y el kernel —que debe ser pequeño y
estable— crece con cada funcionalidad. Además rompe la localidad: para entender qué
ofrece `identity` hay que ir a mirar a otra parte.

### C. El contrato en `<módulo>.api`, junto a los controladores

Cada módulo publica en su propio paquete `api` tanto lo que ofrece por HTTP como lo que
ofrece a los demás módulos, con los tipos de datos que viajan en esa frontera.

## Decisión

Se adopta la **alternativa C**. La regla 1 del ADR-001 queda redactada así:

> 1. Un módulo **solo** puede alcanzar a otro a través de su paquete `api`. Los paquetes
>    `domain`, `application` e `infrastructure` de un módulo son internos y ningún otro
>    módulo puede importarlos.

Tres razones, por orden de peso:

**Es verificable sin ambigüedad.** `api` es público, todo lo demás es interno. Eso se
escribe en ArchUnit en cinco líneas y el build lo comprueba en cada compilación. La regla
del ADR-001 no admitía una comprobación equivalente, y una regla de arquitectura que no
se puede verificar dura lo que dura la disciplina del equipo.

**Coincide con lo que `api` ya significa.** Ese paquete era ya la cara pública del módulo
hacia el exterior por HTTP. Que sea también la cara pública hacia los demás módulos es
una sola idea en lugar de dos, y deja el contrato donde alguien lo busca.

**Obliga a definir tipos de frontera.** Al no poder devolver `Usuario`, `identity`
publica `UsuarioResumen` con lo que los demás necesitan y nada más. Ese DTO es
exactamente el contrato: mientras se respete, `identity` puede reescribirse por dentro.

Contratos publicados a la fecha:

| Módulo | Contrato en `api` | Lo consume |
|---|---|---|
| `identity` | `UsuarioDirectorio`, `UsuarioResumen`, `UsuarioRegistrado` | `project`, `profile` |
| `profile` | `PerfilConsulta`, `PerfilPublicoConsulta` | — |
| `project` | `ProyectoPublicoConsulta`, `PaginaKeyset` | — |

Los eventos de aplicación de Spring siguen siendo una vía válida, como en el ADR-001.
`CrearPerfilAlRegistrarUsuario` los usa para desacoplar el alta de perfil del registro.

## Consecuencias

### Positivas

- La frontera es comprobable en cada build y el mensaje de fallo nombra el par de
  módulos que la viola.
- El dominio de cada módulo queda libre para cambiar sin coordinación con nadie.
- El contrato entre módulos y el contrato HTTP conviven y se leen juntos.

### Negativas y cómo se mitigan

| Riesgo | Mitigación |
|---|---|
| Un DTO más por capacidad publicada | Es el precio de la frontera; sin él el acoplamiento vuelve por el tipo que cruza |
| `api` mezcla contrato HTTP y contrato entre módulos | Aceptado: ambos son lo público del módulo. Si un `api` crece de más, es señal de que el módulo hace demasiado |
| Los contratos quedan repartidos, no en un índice | Se mantiene la tabla de arriba y el diagrama de paquetes |

## Verificación

La regla 1 de
[`FronterasModularesTest`](../../src/test/java/com/codefactory/devnet/arquitectura/FronterasModularesTest.java)
recorre los pares de módulos y comprueba que ninguno alcanza `domain`, `application` ni
`infrastructure` del otro. Rompe el build, no avisa.

```java
noClasses()
    .that().resideInAPackage(RAIZ + "." + origen + "..")
    .should().dependOnClassesThat()
    .resideInAnyPackage(
            RAIZ + "." + destino + ".domain..",
            RAIZ + "." + destino + ".application..",
            RAIZ + "." + destino + ".infrastructure..")
```

El mismo test añadió después la **regla 7**, que prohíbe a los módulos de negocio
depender del paquete `config`. No estaba en el ADR-001 y se incorpora aquí por el mismo
razonamiento: `config` cablea el arranque y mira hacia todos los módulos, así que una
dependencia de vuelta cierra un ciclo. Ya ocurrió una vez, con un `@Configuration` que
sembraba datos de referencia importando `identity.infrastructure`; se resolvió moviendo
esos datos a `V1__baseline.sql` y publicando en `shared` lo que los módulos necesitaban
de la configuración.