# Proceso principal: ciclo de vida de una publicación

El §2.2 pide documentar el proceso principal con **al menos tres estados y validaciones
de reglas de negocio**. En DevNet ese proceso es el recorrido de una publicación desde
que su autor la redacta hasta que deja de estar visible.

No es un CRUD: hay una máquina de estados con transiciones restringidas, reglas que
dependen del rol y de la propiedad del recurso, y un historial que debe escribirse en la
misma transacción que el cambio.

> **Nota de alcance (2026-09-18).** Este documento describía antes el ciclo completo de
> moderación —reportes, revisión, apelaciones y suspensiones automáticas, reglas R1 a
> R7—, que suponía un módulo `moderation` que no llegó a construirse. Lo que sí existe es
> el ciclo de la publicación y la acción de ocultar, implementada dentro de `project`. El
> resto se retiró en lugar de dejarlo descrito como si funcionara.

> Nomenclatura: *publicación* es el supertipo que abarca **proyectos** y **discusiones**.
> Los proyectos recorren todo el ciclo que describe este documento. Las discusiones
> (HU-11, módulo `discussion`) por ahora solo se crean ya publicadas; editarlas,
> ocultarlas o archivarlas no está implementado. La especialización está en el
> [modelo lógico](../bd/02-modelo-logico.md).

---

## Máquina de estados

```mermaid
stateDiagram-v2
    direction LR
    [*] --> BORRADOR : crear

    BORRADOR --> PUBLICADO : publicar (R1)
    BORRADOR --> [*] : eliminar (solo el autor)

    PUBLICADO --> OCULTO : ocultar (R2)
    PUBLICADO --> ARCHIVADO : archivar (R3)

    OCULTO --> PUBLICADO : restituir (R4)
    OCULTO --> ARCHIVADO : archivar (R3)

    ARCHIVADO --> [*]
```

Los estados son los que admite `ck_publicacion_estado` en
[`V1__baseline.sql`](../../src/main/resources/db/migration/V1__baseline.sql).

La restricción incluye además `EN_REVISION`, que pertenecía al flujo de reportes.
**Hoy ninguna transición lo alcanza.** Se conserva en el CHECK para no forzar una
migración que quitaría un valor que quizá vuelva a hacer falta, y se documenta aquí como
estado sin uso para que nadie lo busque en el código.

| Estado | Visible al público | Editable por el autor | Comentable |
|---|---|---|---|
| `BORRADOR` | No, solo su autor | Sí | No |
| `PUBLICADO` | Sí | Sí | Sí |
| `OCULTO` | No, solo su autor y los moderadores | No | No |
| `ARCHIVADO` | No | No | No |
| `EN_REVISION` | — | — | — *(sin uso)* |

---

## Reglas

### R1 · Publicar un borrador

Implementada en
[`PublicarProyecto`](../../src/main/java/com/codefactory/devnet/project/application/PublicarProyecto.java)
(HU-07, AB#17).

Condiciones, todas verificadas en el servidor:

1. Hay sesión activa. Sin ella, `ACCESO_DENEGADO`.
2. **Solo el autor publica en su nombre.** El identificador del autor sale del token, no
   del cuerpo de la petición: aceptarlo del cliente permitiría publicar suplantando a
   otro.
3. El título mide entre 5 y 200 caracteres una vez recortado.
4. La descripción tiene al menos 20 caracteres.
5. El permiso `publicacion:crear` está en el token. Lo conceden `DESARROLLADOR`,
   `MODERADOR` y `ADMIN`.
6. Las tecnologías declaradas existen en el catálogo y están aprobadas.

Si algo falla se responde con el cuerpo de error del ADR-005, con el detalle por campo, y
no se escribe nada.

### R2 · Ocultar una publicación

Implementada en
[`OcultarPublicacion`](../../src/main/java/com/codefactory/devnet/project/application/OcultarPublicacion.java)
(HU-06, AB#15, criterio 2).

- Exige el permiso `publicacion:moderar`, que solo tienen `MODERADOR` y `ADMIN`. Un
  desarrollador recibe 403 **y el intento queda registrado en auditoría**.
- El motivo es obligatorio y necesita **al menos 20 caracteres**. No hay resolución
  silenciosa: retirar contenido ajeno se justifica por escrito, y ese texto queda en el
  historial por si más adelante hay que revisarlo.
- La transición y la escritura del historial ocurren en la misma transacción (R5).

Esta regla es todo lo que sobrevive del módulo de moderación previsto. Reportar
contenido, revisar una cola de reportes, apelar una ocultación y suspender cuentas
automáticamente **quedaron fuera del alcance**; el javadoc de `OcultarPublicacion` lo
dice explícitamente.

### R3 · Archivar

El autor retira definitivamente su publicación. Es **terminal e irreversible**: de
`ARCHIVADO` no se sale. Se distingue del borrado en que conserva el rastro —comentarios,
reacciones y reputación acreditada siguen siendo coherentes— sin dejar el contenido
visible.

Implementada para proyectos en
[`ArchivarProyecto`](../../src/main/java/com/codefactory/devnet/project/application/ArchivarProyecto.java)
(HU-08, AB#18: tareas HU-08.3 y HU-08.4), con `POST /api/v1/proyectos/{id}/archivado`,
la ruta que fija el ADR-002. **Es lo que la HU llama «eliminar»**: el equipo decidió que
eliminar un proyecto es archivarlo y no borrar la fila, porque el borrado en cascada se
llevaría también el historial que explica qué pasó.

Condiciones, en este orden:

1. El token trae `publicacion:archivar` (`DESARROLLADOR`, `MODERADOR`) o
   `publicacion:moderar` (`MODERADOR`, `ADMIN`). Un `VISITANTE` recibe `403 ACCESO_DENEGADO`.
2. **Solo el autor o un moderador**, con la misma
   [`PoliticaProyecto`](../../src/main/java/com/codefactory/devnet/project/domain/PoliticaProyecto.java)
   que la edición (R6). Cualquier otro recibe `403 PROYECTO_AJENO` y el intento queda en
   `auditoria`.
3. Se archiva desde `PUBLICADO` u `OCULTO`. Sobre algo ya archivado, `409 PROYECTO_NO_EDITABLE`.
4. **Confirmación previa** (HU-08.3): el cuerpo lleva `confirmacionTitulo` con el título
   exacto del proyecto, como al borrar un repositorio en GitHub. Sin ella,
   `400 PROYECTO_CONFIRMACION_REQUERIDA`; si no coincide (distingue mayúsculas),
   `422 PROYECTO_CONFIRMACION_INVALIDA`. Una API sin frontend no puede mostrar un diálogo
   de «¿estás seguro?»; exigir el título es su equivalente en el contrato, y evita que un
   clic o un reintento accidental borre nada.

La transición escribe en `historial_estado_publicacion` (R5) con el actor y el motivo
`Eliminado por su autor.` o `Eliminado por moderacion.`. Si la hizo un moderador sobre
un proyecto ajeno, queda además en `auditoria` como `UPDATE`.

El proyecto desaparece de toda lectura pública sin tocar ninguna consulta: el listado del
autor y el perfil ya filtran por `PUBLICADO`.

**Pendiente:** archivar discusiones, cuando exista el módulo.

### R4 · Restituir

Un moderador devuelve a `PUBLICADO` algo que se ocultó por error. Mismo permiso que R2 y
también con motivo.

### R5 · Toda transición deja rastro

Cada cambio de estado escribe una fila en `historial_estado_publicacion` con el estado
anterior, el nuevo, quién lo hizo y por qué, **en la misma transacción que el cambio**.
Si el historial no se puede escribir, la transición no ocurre. El comentario de la tabla
en `V1__baseline.sql` lo declara así.

`ck_historial_estados_distintos` impide registrar una transición de un estado a sí mismo,
que sería ruido.

Es lo que exige el §3.2 —«implementar auditoría y trazabilidad de cambios relevantes»— y
lo que permite contestar la pregunta que se hace cuando alguien reclama: quién ocultó
esto, cuándo y con qué motivo.

### R6 · Editar el contenido

Implementada en
[`EditarProyecto`](../../src/main/java/com/codefactory/devnet/project/application/EditarProyecto.java)
(HU-08, AB#18: tareas HU-08.1 · AB#39 y HU-08.2 · AB#40), con `PUT /api/v1/proyectos/{id}`.
La eliminación de la misma HU es la regla R3.

No es una transición: el estado de la publicación no cambia, así que no escribe en
`historial_estado_publicacion`. Pero depende del estado y de quién la pide.

Condiciones, todas verificadas en el servidor y en este orden:

1. Hay sesión activa y el token trae `publicacion:editar` (`DESARROLLADOR`, `MODERADOR`) o
   `publicacion:moderar` (`MODERADOR`, `ADMIN`). Sin ninguno, `403 ACCESO_DENEGADO`.
2. **Solo el autor o un moderador** modifica el proyecto
   ([`PoliticaProyecto`](../../src/main/java/com/codefactory/devnet/project/domain/PoliticaProyecto.java)).
   Cualquier otro recibe `403 PROYECTO_AJENO` y **el intento queda en `auditoria`** como
   `ACCESO_DENEGADO`, igual que exige HU-06. Si el proyecto no es público, la respuesta es
   `404`: un `403` confirmaría que existe algo que no debería poder ver (ADR-005).
3. **Solo se edita un proyecto `PUBLICADO`.** Uno `OCULTO` u `ARCHIVADO` responde
   `409 PROYECTO_NO_EDITABLE`: editar algo oculto permitiría al autor cambiar el contenido
   que motivó la ocultación y borrar la evidencia.
4. El contenido cumple **las mismas reglas que al publicar** (R1, puntos 3, 4 y 6), porque
   `Proyecto.publicar()` y `Proyecto.editar()` comparten la validación. Un proyecto editado
   no puede quedar en un estado que no se habría podido publicar.
5. El repositorio enlazado no pertenece a **otro** proyecto. Conservar el propio no es un
   duplicado.

Lo que **no** cambia al editar: el identificador, el autor, la fecha de publicación y el
estado. Editar no es volver a publicar.

Cuando quien edita es un moderador y no el autor, la intervención queda en `auditoria`
como `UPDATE` sobre `publicacion`, con su identificador como actor. Cambiar el trabajo de
otra persona es un poder que debe dejar rastro.

**Concurrencia.** Dos ediciones simultáneas del mismo proyecto no se pisan: la segunda
recibe `409 CONFLICTO_CONCURRENCIA` por el bloqueo optimista de `publicacion.version`.
`PublicacionEntity` usa `@DynamicUpdate` para que editar no reescriba la columna `estado`
y no pueda deshacer una ocultación hecha en ese mismo instante.

---

## Colaboración en un proyecto

> **Modelado en base de datos, sin implementación.** La tabla `colaboracion` está en
> `V1__baseline.sql` con su máquina de estados y su restricción de unicidad, y cuenta
> como entregable de modelado (§3.2). No hay código que la use.

```mermaid
stateDiagram-v2
    direction LR
    [*] --> SOLICITADA
    SOLICITADA --> ACEPTADA : el dueño acepta
    SOLICITADA --> RECHAZADA : el dueño rechaza
    ACEPTADA --> ACTIVA : el solicitante confirma
    ACTIVA --> RETIRADA : cualquiera de los dos
    RETIRADA --> SOLICITADA : reingreso
```

El detalle que importa: `ux_colaboracion_proyecto_usuario` es única sobre
`(publicacion_proyecto_id, usuario_id)`, de modo que **el reingreso tras un retiro
transiciona la fila existente en vez de crear otra**. Sin esa unicidad, alguien podría
acumular solicitudes repetidas sobre el mismo proyecto y el dueño recibiría la misma
petición una y otra vez.

---

## Trazabilidad

| Regla | Historia | Implementación | Verificación |
|---|---|---|---|
| R1 | HU-07 · AB#17 | `PublicarProyecto` | `ProyectoTest`, `PublicarProyectoIT` |
| R2 | HU-06 · AB#15 | `OcultarPublicacion` | `ControlAccesoPorRolIT` |
| R3 | HU-08 · AB#18 (HU-08.3, HU-08.4) | `ArchivarProyecto`, `Proyecto.archivar()` | `ArchivarProyectoTest`, `ArchivarProyectoIT` |
| R4 | — | No implementada | — |
| R5 | HU-06 · AB#15 | `historial_estado_publicacion` | `ControlAccesoPorRolIT` |
| R6 | HU-08 · AB#18 (AB#39, AB#40) | `EditarProyecto`, `PoliticaProyecto`, `Proyecto.editar()` | `EditarProyectoTest`, `EditarProyectoIT` |