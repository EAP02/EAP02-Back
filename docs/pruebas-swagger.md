# Demo desde Swagger UI

Guion para grabar la demostración contra el despliegue real:
**<https://eap02-back.onrender.com/swagger-ui.html>**

Swagger UI es la única superficie de interacción del producto —el perfil avanzado marca el
frontend como N/A— y está habilitado en producción por el
[ADR-007](adr/ADR-007-swagger-ui-habilitado-en-produccion.md).

Duración de la grabación: unos 8 minutos si la preparación está hecha.

---

## Antes de grabar

### 1. Despierta el servicio

El plan gratuito de Render suspende el servicio a los 15 minutos de inactividad y el
arranque en frío pasa del minuto. Dos minutos antes de grabar, abre:

```
https://eap02-back.onrender.com/actuator/health
```

Y no empieces hasta que responda `{"status":"UP"}`. Si grabas sin esto, el primer `POST`
se queda colgado un minuto en pantalla.

### 2. Deja el moderador listo

Esto **no se puede hacer durante la demo**: no hay endpoint para asignar roles, y los
permisos por rol se cachean 30 minutos en Caffeine. Hazlo antes y reinicia el servicio.

En el SQL Editor de Supabase:

```sql
-- Crea la cuenta desde Swagger primero (POST /auth/registro con moderador@devnet.test),
-- y después ejecuta esto:

UPDATE usuario SET mfa_habilitado = true WHERE correo = 'moderador@devnet.test';

DELETE FROM usuario_rol
 WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'moderador@devnet.test');

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT u.id, r.id FROM usuario u, rol r
 WHERE u.correo = 'moderador@devnet.test' AND r.codigo = 'MODERADOR';
```

**El `UPDATE` de `mfa_habilitado` no es opcional.** El rol `MODERADOR` exige segundo
factor: si queda en `false`, el usuario se autentica pero su token sale **sin ningún
permiso** y `mfaPendiente` viene en `true`. Es el comportamiento correcto y es la causa
más probable de que la parte de moderación falle en directo.

Después, en Render: **Manual Deploy → Deploy latest commit**, para vaciar la caché.

### 3. Ten los identificadores a mano

Apunta en un bloc, porque los vas a pegar varias veces:

- El `id` del desarrollador (lo devuelve el registro).
- El `id` del proyecto que publiques.
- Los identificadores de tecnología del catálogo:

```sql
SELECT id, nombre FROM tecnologia WHERE aprobada ORDER BY id LIMIT 12;
```

### 4. Deja abiertas dos pestañas

Swagger en una, el SQL Editor de Supabase en la otra. La demo gana mucho cuando cada
respuesta HTTP se contrasta con la fila que aparece en la base.

---

## Cómo se usa Swagger UI

- **Try it out** desdobla el formulario de cada endpoint. **Execute** lanza la petición.
- El botón **Authorize** (arriba a la derecha) guarda el token para todas las peticiones
  protegidas. Pega **solo el token**, sin la palabra `Bearer` delante: el esquema ya la
  añade.
- El token de acceso **dura 15 minutos**. Si a mitad de grabación empiezas a recibir
  `401`, vuelve a `/auth/inicio-sesion` y re-autoriza.
- Para cambiar de usuario: *Authorize → Logout*, y pegas el otro token.

---

## El guion

### 1 · Registro · `POST /api/v1/auth/registro`

```json
{
  "correo": "ana@devnet.test",
  "clave": "Devnet2026!",
  "nombreUsuario": "ana_dev"
}
```

**Esperado:** `201` con `id`, `correo` y `nombreUsuario`.

**Qué señalar:** que el perfil se creó solo, sin una segunda llamada. En Supabase:

```sql
SELECT u.nombre_usuario, p.usuario_id IS NOT NULL AS tiene_perfil
  FROM usuario u LEFT JOIN perfil p ON p.usuario_id = u.id
 WHERE u.correo = 'ana@devnet.test';
```

Es el desacoplamiento entre módulos: `identity` publica el evento `UsuarioRegistrado` y
`profile` lo recoge. Ninguno de los dos conoce las tripas del otro (ADR-006).

**Guarda el `id`.**

### 2 · El contrato de errores · `POST /api/v1/auth/registro`

Repite el mismo correo.

**Esperado:** `409` con `"errorCode":"AUTH_CORREO_YA_REGISTRADO"`.

**Qué señalar:** el cuerpo lleva `errorCode`, `message`, `details`, `traceId`, `timestamp`
y `path`, y la respuesta trae la cabecera `X-Trace-Id` con el mismo valor. Es el ADR-005:
un único formato para todo error, y un identificador que correlaciona lo que ve el usuario
con la línea del log.

Y `409`, no `400`: es un conflicto con el estado actual del recurso, no un problema de
formato. Distinguirlos evita el 400 para todo.

### 3 · Inicio de sesión · `POST /api/v1/auth/inicio-sesion`

```json
{ "correo": "ana@devnet.test", "clave": "Devnet2026!" }
```

**Esperado:** `200` con `token`, `expiraEnSegundos: 900`, `roles: ["DESARROLLADOR"]` y
`permisos`.

**Qué señalar, dos cosas:**

El token lleva **permisos, no roles**. Los roles viajan solo como información; la
autorización se decide con los permisos. Reasignar capacidades a un rol no obliga a tocar
ni una anotación del código.

Y en la pestaña *Network* del navegador, la cabecera `Set-Cookie`: el token de refresco
sale como cookie `HttpOnly`, `Secure`, `SameSite=Strict`, acotada a `/api/v1/auth`.
**Nunca aparece en el cuerpo JSON** — si apareciera, el `HttpOnly` no serviría de nada.

**Pulsa Authorize y pega el token.**

### 4 · Perfil técnico · `PUT /api/v1/perfiles/{id}`

Usa el `id` del paso 1.

```json
{
  "nombre": "Ana Restrepo",
  "biografia": "Backend developer con foco en sistemas distribuidos.",
  "tecnologias": [
    { "tecnologiaId": 1, "nivel": "AVANZADO", "anios": 5 },
    { "tecnologiaId": 7, "nivel": "INTERMEDIO", "anios": 2 }
  ],
  "githubUrl": "https://github.com/ana",
  "linkedinUrl": "https://linkedin.com/in/ana"
}
```

**Esperado:** `200`.

**Qué señalar:** las tecnologías van **por identificador de catálogo, no por texto libre**.
Es la decisión de la migración V4: con texto libre, `React`, `ReactJS` y `react.js` son
tres tecnologías distintas y el reporte de tecnologías más usadas deja de significar nada.
Solo el catálogo permite agregar (§5.1).

`githubUrl` y `linkedinUrl` son las columnas que añadió la V3.

Enséñalo con `GET /api/v1/perfiles/{id}`, que es **público** y no pide token: el perfil de
un desarrollador es su carta de presentación.

### 5 · Publicar un proyecto · `POST /api/v1/proyectos`

```json
{
  "titulo": "Motor de plantillas en Java",
  "descripcion": "Un motor de plantillas minimalista escrito en Java 17 con soporte para expresiones.",
  "tecnologias": [1, 7],
  "urlRepositorio": "https://github.com/ana/motor-plantillas",
  "estadoProyecto": "EN_DESARROLLO",
  "licencia": "MIT",
  "buscaColaboradores": true
}
```

**Esperado:** `201` con `id`, `titulo` y `publicadoEn`. **Guarda el `id`.**

Ahora repítelo con el título `"abc"`.

**Esperado:** `422` con el detalle señalando `titulo` y la regla incumplida.

**Qué señalar:** `422` y no `400`. El JSON estaba bien formado; lo que falla es una regla
de negocio. Y el `details` dice **qué campo y por qué**, que es lo que permite a un cliente
pintar el error junto al campo correcto.

`GET /api/v1/proyectos/autor/{autorId}` cierra el ciclo: lectura pública, paginada por
cursor y no por desplazamiento, para que insertar un proyecto nuevo no descoloque las
páginas siguientes.

### 6 · Autorización denegada · `POST /api/v1/proyectos/{proyectoId}/ocultamiento`

Con el token de **ana**, que es `DESARROLLADOR`:

```json
{ "motivo": "Contenido duplicado de otro proyecto ya publicado en la plataforma." }
```

**Esperado:** `403` con `"errorCode":"ACCESO_DENEGADO"`.

**Y esto es lo importante — el intento queda registrado.** En Supabase:

```sql
SELECT operacion, registro_id, actor_id, ip, creado_en
  FROM auditoria
 WHERE operacion = 'ACCESO_DENEGADO'
 ORDER BY creado_en DESC LIMIT 3;
```

Es el criterio 1 de HU-06 literal: «un usuario sin permiso recibe error 403 **y queda
registrado el intento**». La verificación ocurre en el servidor, no en el cliente.

### 7 · Moderación · el mismo endpoint, otro rol

*Authorize → Logout*. Inicia sesión como `moderador@devnet.test` y autoriza con su token.

Mismo `POST`, mismo cuerpo. **Esperado:** `204`.

Antes, enseña el rechazo por motivo corto: `{ "motivo": "spam" }` → **`422`**. Retirar
contenido ajeno exige justificarlo por escrito, mínimo 20 caracteres, y ese texto queda
en el historial.

Y el rastro de la transición:

```sql
SELECT estado_anterior, estado_nuevo, motivo, actor_id, creado_en
  FROM historial_estado_publicacion
 ORDER BY creado_en DESC LIMIT 3;
```

Una fila `PUBLICADO → OCULTO` con el motivo completo. Se escribe **en la misma transacción
que el cambio de estado**: si no se puede escribir el historial, la transición no ocurre.

### 8 · Renovación de sesión · `POST /api/v1/auth/refresco`

Sin cuerpo: la credencial viaja en la cookie.

**Esperado:** `200` con un token de acceso **distinto**.

**Qué señalar:** el JWT dura 15 minutos y no se puede revocar; la revocación opera sobre el
refresco. Cada llamada lo **rota**: el anterior queda consumido y se emite otro de la misma
familia.

```sql
SELECT left(token_hash, 8) AS hash,
       familia,
       consumido_en IS NOT NULL AS consumido,
       reemplazado_por IS NOT NULL AS enlazado
  FROM token_refresco ORDER BY emitido_en DESC LIMIT 4;
```

Misma familia, el anterior consumido y enlazado con su sucesor.

Si te sobra tiempo, el remate: **ejecuta el refresco dos veces seguidas sin recargar**. La
segunda da `401 AUTH_REFRESCO_REUSADO` y revoca la familia entera, incluida la sesión
legítima. No hay forma de distinguir una petición repetida de una cookie robada, así que se
asume lo peor. Queda en auditoría como `REFRESCO_REUSADO`.

---

## Si algo falla en directo

| Síntoma | Causa | Arreglo |
|---|---|---|
| La primera petición tarda un minuto | Arranque en frío de Render | Despertar el servicio antes (paso 1 de la preparación) |
| `401 AUTH_REQUERIDA` de repente | El token caducó a los 15 min | `/auth/inicio-sesion` y re-autorizar |
| El moderador recibe `403` | `mfa_habilitado = false`, o la caché de permisos | Preparación paso 2, y redesplegar |
| `403` al editar un perfil ajeno | Correcto: solo el titular edita su perfil | Es la demostración, no un fallo |
| El refresco da `401 AUTH_REFRESCO_AUSENTE` | Swagger no envió la cookie | Lánzalo con `curl -b`, o desde la pestaña *Network* |
| `500` en cualquier sitio | Mira el log de Render y busca el `traceId` de la respuesta | Es justo para lo que existe el `traceId` |

## Al terminar

```sql
DELETE FROM usuario WHERE correo LIKE '%@devnet.test';
```

Borra en cascada perfiles, tokens y publicaciones. La auditoría conserva las filas con
`actor_id` nulo, que es lo correcto: un registro de auditoría que se borra con su sujeto no
sirve de nada.
