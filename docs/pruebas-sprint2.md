# Pruebas de Sprint 2 — comentarios, reacciones y seguimiento

Guion para comprobar desde **Swagger UI**, contra el despliegue real, que HU-13, HU-14 y
HU-15 cumplen sus criterios de aceptación:
**<https://eap02-back.onrender.com/swagger-ui.html>**

Complementa a las pruebas automáticas (tabla al final) y sigue el mismo método que
[`pruebas-swagger.md`](pruebas-swagger.md): cada respuesta HTTP se contrasta con la fila que
aparece en la base.

- Duración: unos 20 minutos si la preparación está hecha.
- Historias cubiertas: **HU-13** (comentar en proyectos y discusiones), **HU-14**
  (reaccionar) y **HU-15** (seguir usuarios).
- Cómo se usa Swagger UI, qué hacer con el token y cómo cambiar de usuario:
  [`pruebas-swagger.md`](pruebas-swagger.md#cómo-se-usa-swagger-ui). El token dura 15
  minutos; si aparece un `401`, vuelve a iniciar sesión y re-autoriza.

---

## Antes de empezar

### 1. Despierta el servicio

El plan gratuito de Render lo suspende tras 15 minutos de inactividad. Abre
`https://eap02-back.onrender.com/actuator/health` y espera `{"status":"UP"}`.

### 2. Crea tres desarrolladores

Con `POST /api/v1/auth/registro`, uno por uno. La clave puede ser la misma
(`Devnet2026!`). Todos nacen con el rol `DESARROLLADOR`.

| Quién | Correo | Nombre de usuario |
|---|---|---|
| **Ana** (autora del contenido) | `ana@devnet.test` | `ana_dev` |
| **Luis** (comenta, reacciona, sigue) | `luis@devnet.test` | `luis_dev` |
| **Eva** (otra persona) | `eva@devnet.test` | `eva_dev` |

**Apunta el `id` de cada uno**; los necesitas en la parte de seguimiento. Si ya los creaste
en otra prueba, reutilízalos.

### 3. Deja listo al administrador

Solo hace falta para el paso **1.7** (un administrador elimina un comentario ajeno). No se
puede hacer desde la API: no hay endpoint para asignar roles ni para inscribir MFA. Hazlo
**antes** y en el SQL Editor de Supabase, y después reinicia el servicio (los permisos por
rol se cachean 30 minutos).

```sql
-- Primero registra admin@devnet.test desde Swagger, y luego:

UPDATE usuario SET mfa_habilitado = true WHERE correo = 'admin@devnet.test';

DELETE FROM usuario_rol
 WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'admin@devnet.test');

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT u.id, r.id FROM usuario u, rol r
 WHERE u.correo = 'admin@devnet.test' AND r.codigo = 'ADMIN';
```

**El `UPDATE` de `mfa_habilitado` no es opcional.** `ADMIN` exige segundo factor: si queda
en `false`, el usuario inicia sesión pero su token sale **sin ningún permiso**.

Después, en Render: **Manual Deploy → Deploy latest commit**.

### 4. Publica algo sobre lo que interactuar

Inicia sesión como **Ana**, pulsa *Authorize* con su token y crea una discusión con
`POST /api/v1/discusiones`:

```json
{
  "titulo": "Spring Boot 4: vale la pena migrar ya?",
  "cuerpo": "Estamos evaluando migrar desde Boot 3. Que problemas encontraron con Jackson 3?",
  "tecnologias": [1, 7]
}
```

**Esperado:** `201` con `id`. **Apúntalo como `DISCUSION`.** Los ids de tecnología salen de
`GET /api/v1/tecnologias`.

Opcional, para el paso 1.1: publica también un proyecto con `POST /api/v1/proyectos` (el
cuerpo está en [`pruebas-swagger.md`](pruebas-swagger.md), paso 5) y apunta su `id` como
`PROYECTO`.

---

## HU-13 · Comentar en proyectos y discusiones

### 1.1 · Un comentario válido se publica · `POST /api/v1/publicaciones/{publicacionId}/comentarios`

Inicia sesión como **Luis** y autoriza. Usa `DISCUSION` como `publicacionId`.

```json
{ "contenido": "Buen aporte, probe tu solucion y me funciono." }
```

**Esperado:** `201`, con la cabecera `Location`, y el cuerpo con `id`, `publicacionId`,
`autorId`, `contenido` y `creadoEn`. **Apunta el `id` como `COMENTARIO`.**

**Qué señalar:** el `autorId` es el de Luis **sin que el cuerpo lo diga**. El autor sale
siempre del token; la petición ni siquiera tiene un campo para declararlo.

```sql
SELECT c.id, u.nombre_usuario AS autor, c.publicacion_id, c.estado
  FROM comentario c JOIN usuario u ON u.id = c.autor_id
 WHERE c.id = '<COMENTARIO>';
```

Debe mostrar a `luis_dev`, la discusión y estado `PUBLICADO`.

Si publicaste un proyecto, repite con `PROYECTO` como `publicacionId`: **el mismo endpoint
sirve para los dos**, porque en el modelo ambos son una publicación.

### 1.2 · Un comentario vacío se rechaza · mismo endpoint

```json
{ "contenido": "   " }
```

**Esperado:** `400` con `"errorCode":"VALIDACION_FALLIDA"` y, en `details`, una entrada
con `campo: "contenido"`.

**Qué señalar:** el rechazo es de forma, por eso `400` y no `422`. Y no se guardó nada:

```sql
SELECT count(*) FROM comentario WHERE publicacion_id = '<DISCUSION>';
```

El conteo no cambia respecto al paso anterior.

### 1.3 · La lectura es pública · `GET /api/v1/publicaciones/{publicacionId}/comentarios`

Pulsa *Authorize → Logout* para quitar el token y ejecuta con `DISCUSION`.

**Esperado:** `200` con la lista, del más antiguo al más reciente, y en cada elemento
`autorNombreUsuario: "luis_dev"`.

**Qué señalar:** funciona **sin token**. El caso contempla visitantes, que pueden leer pero
no comentar.

### 1.4 · El autor elimina el suyo · `DELETE /api/v1/comentarios/{comentarioId}`

Inicia sesión como **Luis**, autoriza y usa `COMENTARIO`.

**Esperado:** `204`.

Vuelve a ejecutar el `GET` del paso 1.3: **el comentario deja de mostrarse**. En la base
la fila se conserva:

```sql
SELECT estado FROM comentario WHERE id = '<COMENTARIO>';
```

Debe decir `ELIMINADO`. **Qué señalar:** se elimina cambiando el estado, no borrando la
fila; no se pierde el historial.

Repite el `DELETE` sobre el mismo comentario. **Esperado:** `404`
(`COMENTARIO_NO_ENCONTRADO`): ya no está disponible.

### 1.5 · Nadie más puede eliminar el comentario de otro · `DELETE /api/v1/comentarios/{comentarioId}`

Como **Luis**, publica un comentario nuevo (paso 1.1) y apunta su `id`. Luego inicia sesión
como **Eva**, autoriza y ejecuta el `DELETE` con ese `id`.

**Esperado:** `403` con `"errorCode":"COMENTARIO_AJENO"`.

**Qué señalar:** el intento queda registrado en auditoría.

```sql
SELECT a.tabla, a.operacion, a.registro_id, u.nombre_usuario AS actor, a.creado_en
  FROM auditoria a LEFT JOIN usuario u ON u.id = a.actor_id
 WHERE a.operacion = 'ACCESO_DENEGADO'
 ORDER BY a.creado_en DESC LIMIT 3;
```

Debe aparecer la fila más reciente con `operacion = ACCESO_DENEGADO`, `actor = eva_dev` y,
en `registro_id`, la ruta `DELETE /api/v1/comentarios/<id>`.

### 1.6 · Ni siquiera un moderador elimina comentarios ajenos

Es una **decisión de diseño**, no una carencia: el permiso es propio
(`comentario:eliminar-ajeno`, migración V6) y solo lo tiene ADMIN, porque la historia dice
*«administradores»*. Se verifica en `ComentarioIT` (ver la tabla del final); desde Swagger
exigiría preparar otro usuario con rol `MODERADOR` como en el paso 3. Si el equipo decide
que los moderadores también deben poder, basta una fila más en `rol_permiso`.

### 1.7 · Un administrador elimina el comentario de otro · `DELETE /api/v1/comentarios/{comentarioId}`

Inicia sesión como **admin@devnet.test**, comprueba que la respuesta trae
`permisos` con `comentario:eliminar-ajeno`, autoriza y usa el `id` del comentario de Luis
del paso 1.5.

**Esperado:** `204`, y el comentario deja de aparecer en el `GET`.

**Qué señalar:** la intervención de un administrador sobre contenido ajeno **queda
auditada** como una eliminación. Si el autor elimina lo suyo, en cambio, no se registra
nada: no hay intervención que rendir.

```sql
SELECT a.tabla, a.operacion, a.registro_id, u.nombre_usuario AS actor, a.creado_en
  FROM auditoria a LEFT JOIN usuario u ON u.id = a.actor_id
 WHERE a.tabla = 'comentario' AND a.operacion = 'DELETE'
 ORDER BY a.creado_en DESC LIMIT 3;
```

Debe aparecer `actor = admin...`, y en `registro_id` el `id` del comentario eliminado.

---

## HU-14 · Reaccionar

La reacción del usuario es **un solo recurso**, `.../reaccion` en singular: se crea o
reemplaza con `PUT` y se quita con `DELETE`.

### 2.1 · Al reaccionar, el contador sube en uno · `PUT /api/v1/publicaciones/{publicacionId}/reaccion`

Primero, como **Eva**, mira cómo está la discusión sin reacciones con
`GET /api/v1/publicaciones/{publicacionId}/reacciones` (`DISCUSION`).

**Esperado:** `200` con `total: 0`.

Inicia sesión como **Luis**, autoriza y ejecuta el `PUT` con `DISCUSION`:

```json
{ "tipo": "ME_GUSTA" }
```

**Esperado:** `200` con `"tipo":"ME_GUSTA"` y `"total":1`.

```sql
SELECT p.contador_reacciones,
       (SELECT count(*) FROM reaccion_publicacion r WHERE r.publicacion_id = p.id) AS filas
  FROM publicacion p WHERE p.id = '<DISCUSION>';
```

Debe dar `1` y `1`. **Qué señalar:** el contador de la publicación y las filas de la tabla
coinciden.

Haz que **Eva** también reaccione (con tipo `UTIL`). **Esperado:** `total: 2`.

### 2.2 · No se duplica · mismo endpoint

Como **Luis**, ejecuta de nuevo el `PUT` con `ME_GUSTA`.

**Esperado:** `200` y `total` **sigue en 2**, no en 3. Una sola reacción por usuario y
publicación.

**Qué señalar:** la garantía no depende solo del código: la clave primaria de
`reaccion_publicacion` es `(usuario_id, publicacion_id)`.

Ahora, como Luis, envía el `PUT` con `"tipo": "INNOVADOR"`. **Esperado:** `200`, el tipo
cambia a `INNOVADOR` y `total` sigue en 2: **cambiar de tipo reemplaza la reacción**.

### 2.3 · Un tipo desconocido se rechaza · mismo endpoint

```json
{ "tipo": "ODIO" }
```

**Esperado:** `400` con `"errorCode":"REACCION_TIPO_INVALIDO"`. No se crea ninguna fila.

### 2.4 · Al quitar la reacción, el contador baja en uno · `DELETE /api/v1/publicaciones/{publicacionId}/reaccion`

Como **Luis**, ejecuta el `DELETE` con `DISCUSION`.

**Esperado:** `200` con `total: 1` (queda la de Eva).

Repite el mismo `DELETE`. **Esperado:** `200` y el total **no baja de 1**: quitar una
reacción que no existe no es un error ni resta nada.

### 2.5 · Lectura pública · `GET /api/v1/publicaciones/{publicacionId}/reacciones`

Sin token. **Esperado:** `200` con `total: 1` y `porTipo` con la reacción de Eva
(`UTIL: 1`).

### 2.6 · Sin token y sin permiso

Sin autorizar, ejecuta el `PUT`. **Esperado:** `401`. Con una publicación inexistente
(cambia el último dígito del `id`) y el token de Luis: **`404`**.

---

## HU-15 · Seguir usuarios

El `id` de un perfil es el de su usuario, así que el seguimiento cuelga de
`/api/v1/perfiles/{id}`. Quien sigue sale del token; el `{id}` de la ruta es **a quién se
sigue**.

### 3.1 · Al seguir, aparece en la lista de seguidos · `PUT /api/v1/perfiles/{id}/seguimiento`

Inicia sesión como **Luis**, autoriza y ejecuta con el `id` de **Ana**.

**Esperado:** `204`.

Sin token, ejecuta `GET /api/v1/perfiles/{id}/seguidos` con el `id` de **Luis**.

**Esperado:** `200`, y la lista contiene a Ana (`nombreUsuario: "ana_dev"`).

```sql
SELECT s.creado_en, a.nombre_usuario AS seguidor, b.nombre_usuario AS seguido
  FROM seguimiento s
  JOIN usuario a ON a.id = s.seguidor_id
  JOIN usuario b ON b.id = s.seguido_id;
```

**Qué señalar, dos cosas:** el seguidor sale del token, no de la ruta; y la relación **no es
recíproca**: consulta `GET /api/v1/perfiles/{id}/seguidos` de Ana, que sigue vacía. En
cambio `GET /api/v1/perfiles/{id}/seguidores` de Ana sí muestra a Luis.

Ejecuta el mismo `PUT` otra vez. **Esperado:** `204` y la base sigue con **una sola fila**
para esa pareja.

### 3.2 · Un usuario no puede seguirse a sí mismo · `PUT /api/v1/perfiles/{id}/seguimiento`

Como **Luis**, ejecuta con **su propio** `id`.

**Esperado:** `422` con `"errorCode":"SEGUIMIENTO_PROPIO"`.

**Qué señalar:** es una regla de negocio, por eso `422`. La base también la impone
(`ck_seguimiento_no_reflexivo`), pero la API responde primero con un error claro y no con
una violación de `CHECK`.

### 3.3 · Al dejar de seguir, desaparece de la lista · `DELETE /api/v1/perfiles/{id}/seguimiento`

Como **Luis**, ejecuta con el `id` de Ana.

**Esperado:** `204`. Vuelve a consultar `.../seguidos` de Luis: **Ana ya no aparece**.

Repite el `DELETE`. **Esperado:** `204`: dejar de seguir a quien no se seguía no es un
error.

### 3.4 · Seguir a alguien que no existe, y sin permiso

Como Luis, ejecuta el `PUT` con un `id` inventado. **Esperado:** `404`. Sin token:
**`401`**.

El permiso `usuario:seguir` (migración V7) lo tienen DESARROLLADOR y MODERADOR. Un
`VISITANTE` recibe `403`; se verifica en `SeguimientoIT`.

---

## Pruebas automáticas

Lo que Swagger no puede probar (roles que no se pueden asignar por API, concurrencia, el
estado exacto de la base) está en las pruebas del repositorio. Las unitarias las ejecuta
Surefire; las de integración, que usan PostgreSQL real con Testcontainers, las ejecuta
Failsafe en `./mvnw verify`.

| Historia | Unitarias | Integración | Qué cubren |
|---|---|---|---|
| HU-13 | `ComentarioTest` (10) | `ComentarioIT` (15) | Comentario válido, vacío y demasiado largo; autor del token; listado; el autor elimina el suyo; un ajeno recibe 403 y queda auditado; el administrador sí elimina y queda auditado; el moderador no |
| HU-14 | `TipoReaccionTest` (3) | `ReaccionIT` (14) | El contador sube y baja en uno; una sola reacción por usuario; cambiar de tipo reemplaza; quitar es idempotente; tipo inválido; sin token; visitante sin permiso |
| HU-15 | `SeguimientoTest` (3) | `SeguimientoIT` (14) | Seguir y que aparezca en seguidos y seguidores; no es recíproco; no se duplica; dejar de seguir; no seguirse a uno mismo; usuario inexistente; el permiso se reparte solo a DESARROLLADOR y MODERADOR |

Además, `EsquemaIT` comprueba el catálogo de permisos tras las migraciones V6 y V7
(22 permisos, 43 asignaciones) y `FronterasModularesTest` verifica que el módulo
`interaction` no rompa las fronteras entre módulos.

## Limitaciones conocidas

Registradas en [`seguridad/owasp-top-10.md`](seguridad/owasp-top-10.md):

- **H-24:** los listados de comentarios, seguidos y seguidores no paginan.
- **H-25:** no hay límite de frecuencia al comentar, reaccionar y seguir.
- Se reacciona a **publicaciones**, no a comentarios, aunque la tabla `reaccion_comentario`
  ya existe.
- No hay endpoint para editar un comentario: solo publicar, listar y eliminar.
