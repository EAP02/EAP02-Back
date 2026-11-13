# Guion de grabación — Sprint 2 (5 minutos)

Demostración en vídeo de **todo lo construido en el sprint 2**, contra producción y desde
Swagger UI. Cada paso dice **qué haces** y **qué dices**, para que la grabación salga a la
primera.

- **Swagger:** <https://eap02-back.onrender.com/swagger-ui.html>
- **Duración:** 5 minutos de grabación. La preparación (parte A) se hace antes y **no se
  graba**.
- **Qué se muestra:** HU-08 a HU-15, mensajería privada y, en el cierre, los entregables de
  Arquitectura y Bases de Datos.
- Para probar cada historia a fondo están [`pruebas-sprint2.md`](pruebas-sprint2.md) y
  [`pruebas-swagger.md`](pruebas-swagger.md). Este guion no prueba todo: elige **un momento
  por funcionalidad**, el que mejor muestra su valor.

---

## La idea que guía el vídeo

En cinco minutos no se lucen los CRUD; se lucen **las reglas que el servidor hace cumplir**.
Por eso casi todos los momentos clave son respuestas «de error» que demuestran una regla:
un `403` por no ser el autor, un `400` por no confirmar, un `422` por seguirse a uno mismo.
Cuando aparezcan, **detente un segundo en el código de error**: es lo que hay que ver.

---

## Parte A · Preparación (antes de grabar, ~10 min, NO se graba)

### A1. Despierta el servicio

Abre <https://eap02-back.onrender.com/actuator/health> y espera hasta ver
`{"status":"UP"}`. El plan gratuito de Render tarda hasta un minuto en despertar.

### A2. Abre dos pestañas de Swagger

Abre el enlace de Swagger **dos veces**, en dos pestañas del mismo navegador:

| Pestaña | Será | Para qué |
|---|---|---|
| **1** | **Ana** | Autora: publica, elimina, recibe mensajes |
| **2** | **Luis** | Visitante con cuenta: intenta editar lo ajeno, comenta, reacciona, envía mensajes |

> Cada pestaña guarda su propio token. Cambiar de usuario en la grabación es solo **cambiar
> de pestaña**, sin volver a iniciar sesión.

### A3. Registra e inicia sesión

Sección **Autenticacion**. Si un usuario ya existe, el registro responde `409`: salta al
inicio de sesión.

**Pestaña 1 — Ana**

`POST /api/v1/auth/registro`
```json
{"correo":"ana@devnet.test","clave":"Devnet2026!","nombreUsuario":"ana_dev"}
```

`POST /api/v1/auth/inicio-sesion`
```json
{"correo":"ana@devnet.test","clave":"Devnet2026!"}
```

Copia el `token` → botón **Authorize** (arriba a la derecha) → pégalo → **Authorize** →
**Close**. Apunta el `usuarioId`.

**Pestaña 2 — Luis**

`POST /api/v1/auth/registro`
```json
{"correo":"luis@devnet.test","clave":"Devnet2026!","nombreUsuario":"luis_dev"}
```

`POST /api/v1/auth/inicio-sesion`
```json
{"correo":"luis@devnet.test","clave":"Devnet2026!"}
```

Igual: **Authorize** con su token y apunta su `usuarioId`.

> ⚠️ **El token dura 15 minutos.** Haz este paso justo antes de grabar. Si te pasas, vuelve
> a iniciar sesión y a autorizar en esa pestaña.

### A4. Crea el contenido de la demo (pestaña 1, Ana)

**Proyecto P1** — sección **Proyectos** → `POST /api/v1/proyectos`
```json
{"titulo":"Motor de plantillas en Java","descripcion":"Un motor de plantillas minimalista escrito en Java 17 con soporte para expresiones.","tecnologias":[1,7],"estadoProyecto":"EN_DESARROLLO"}
```

**Proyecto P2**, el que se elimina en la grabación — mismo endpoint
```json
{"titulo":"Sistema de Gestión","descripcion":"Sistema de gestion de inventarios para pequenos comercios en Spring Boot.","tecnologias":[1]}
```

**Discusión D1** — sección **Discusiones** → `POST /api/v1/discusiones`
```json
{"titulo":"Spring Boot 4: vale la pena migrar ya?","cuerpo":"Estamos evaluando migrar desde Boot 3. Que problemas encontraron con Jackson 3?","tecnologias":[1,7]}
```

> Los números de `tecnologias` son del catálogo: `1` es Java y `7` es Spring Boot
> (`GET /api/v1/tecnologias`).

### A5. Hoja de identificadores

Ya creados en producción para esta grabación. Los pasos de la parte B los traen escritos:
solo copia y pega.

| Qué | Identificador |
|---|---|
| **Ana** (`usuarioId`) | `7ea84926-4bc6-4cba-b22e-1643d54f204b` |
| **Luis** (`usuarioId`) | `7dcceb38-5409-4e11-b07e-bc1a82babaf7` |
| **P1** — Motor de plantillas en Java | `1cd97aae-410d-4b0a-93dd-a93ebbb6ff66` |
| **P2** — Sistema de Gestión (se elimina en el paso 3) | `e105db3b-55e0-4936-ae3f-8e100c5de793` |
| **D1** — Discusión «Spring Boot 4…» | `8cf80762-d377-43dc-82fb-e8d20b72cbd9` |

> Si repites la preparación (por ejemplo, porque P2 quedó archivado en un ensayo), los
> nuevos identificadores serán otros: actualiza esta tabla y los pasos que los usan.

### A6. Deja Swagger listo

- En las dos pestañas, **contrae todas las secciones** (clic en el nombre de cada una) para
  que la grabación empiece con la portada limpia.
- Ten abierta una tercera pestaña con **GitHub → Actions**, mostrando un check verde, para
  el cierre.
- Cierra notificaciones y sube el zoom del navegador a 110–125 % para que se lea en el
  vídeo.

---

## Parte B · Grabación (5 minutos)

Cómo leer cada paso:

- **Pestaña:** en cuál tienes que estar.
- **Haces:** dónde hacer clic y qué pegar.
- **Debes ver:** la respuesta correcta. Si sale otra cosa, mira la parte C.
- **Dices:** el texto para narrar. No hace falta decirlo literal; sí la idea.

Para ejecutar un endpoint en Swagger: clic en él → **Try it out** → rellena → **Execute** →
baja a **Responses**.

---

### ⏱ 0:00 – 0:30 · Apertura

**Pestaña:** 1 (Ana), en la portada de Swagger con las secciones contraídas.

**Haces:** baja despacio por la lista de secciones (Proyectos, Tecnologias, Discusiones,
Comentarios, Reacciones, Seguimiento, Mensajeria…).

**Dices:**
> «En este sprint DevNet pasó de tres a seis módulos: proyectos completos, búsqueda,
> discusiones, comentarios, reacciones, seguimiento y mensajería privada. Todo lo que van a
> ver corre en producción, en Render con base de datos en Supabase, y cada regla se verifica
> en el servidor, no en el cliente.»

---

### ⏱ 0:30 – 1:30 · Proyectos: editar y eliminar (HU-08) ⭐

#### Paso 1 · Luis intenta editar el proyecto de Ana

**Pestaña:** 2 (Luis)

**Haces:** **Proyectos** → `PUT /api/v1/proyectos/{proyectoId}` → en `proyectoId` pega
**P1**: `1cd97aae-410d-4b0a-93dd-a93ebbb6ff66` → en el cuerpo pega:
```json
{"titulo":"Proyecto secuestrado","descripcion":"Luis intenta cambiar el proyecto de otra persona.","tecnologias":[1]}
```

**Debes ver:** `403` con `"errorCode": "PROYECTO_AJENO"`.

**Dices:**
> «Luis tiene permiso para editar proyectos, pero no **este**, porque no es su autor. Hay
> dos capas de autorización: el rol, y la propiedad del recurso, que solo se conoce después
> de leerlo. Además, este intento quedó registrado en la auditoría.»

#### Paso 2 · Ana intenta eliminar sin confirmar

**Pestaña:** 1 (Ana)

**Haces:** **Proyectos** → `POST /api/v1/proyectos/{proyectoId}/archivado` → en
`proyectoId` pega **P2**: `e105db3b-55e0-4936-ae3f-8e100c5de793` → **borra el cuerpo de ejemplo**, deja `{}`.

**Debes ver:** `400` con `"errorCode": "PROYECTO_CONFIRMACION_REQUERIDA"`.

**Dices:**
> «Eliminar exige una confirmación previa: escribir el título exacto del proyecto, como hace
> GitHub al borrar un repositorio. Así un clic accidental no borra nada.»

#### Paso 3 · Ana confirma y elimina

**Pestaña:** 1 (Ana), mismo endpoint y mismo `proyectoId` (`e105db3b-55e0-4936-ae3f-8e100c5de793`).

**Haces:** cambia el cuerpo por:
```json
{"confirmacionTitulo":"Sistema de Gestión"}
```

**Debes ver:** `204` (sin cuerpo).

**Dices:**
> «Y no lo borramos de la base: lo archivamos. Desaparece de toda lectura pública, pero la
> fila y su historial se conservan, con quién lo eliminó y cuándo.»

---

### ⏱ 1:30 – 2:15 · Explorar y buscar proyectos (HU-09) ⭐

#### Paso 4 · Búsqueda en español

**Pestaña:** cualquiera. Es público, no necesita token.

**Haces:** **Proyectos** → `GET /api/v1/proyectos` → en `q` escribe `plantilla` (deja el
resto vacío).

**Debes ver:** `200`, y en `contenido` el proyecto **«Motor de plantillas en Java»**.

**Dices:**
> «Busqué "plantilla" en singular y encontró "plantillas". Es búsqueda de texto completo en
> español, hecha por PostgreSQL con un índice especializado. Y se puede leer sin iniciar
> sesión, porque los visitantes también exploran.»

#### Paso 5 · Estado vacío con mensaje

**Haces:** mismo endpoint → `q` = `plantilla`, `tecnologia` = `rust`.

**Debes ver:** `200`, `"contenido": []` y un `"mensaje"` que sugiere quitar el filtro.

**Dices:**
> «Cuando no hay resultados no devolvemos un error, sino un estado vacío con un mensaje que
> orienta al usuario. Aquí le sugiere quitar el filtro de tecnología. El mensaje cambia según
> lo que se haya buscado.»

---

### ⏱ 2:15 – 3:15 · Comunidad: discusiones, comentarios y reacciones (HU-11 a HU-14) ⭐

#### Paso 6 · Luis comenta la discusión de Ana

**Pestaña:** 2 (Luis)

**Haces:** **Comentarios** → `POST /api/v1/publicaciones/{publicacionId}/comentarios` → en
`publicacionId` pega **D1**: `8cf80762-d377-43dc-82fb-e8d20b72cbd9` → cuerpo:
```json
{"contenido":"Migramos la semana pasada: el mayor cambio fue Jackson 3."}
```

**Debes ver:** `201`, y en el cuerpo un `autorId` que es el de Luis.

**Dices** (corto, sigue rápido al paso 7):
> «Luis comenta. Fíjense en que no le dijimos quién es el autor: sale del token.»

#### Paso 7 · El hilo de la discusión ⭐⭐ (el mejor momento del vídeo)

**Pestaña:** cualquiera (público).

**Haces:** **Discusiones** → `GET /api/v1/discusiones/{discusionId}` → pega **D1**:
`8cf80762-d377-43dc-82fb-e8d20b72cbd9` → baja hasta `"hilo"`.

**Debes ver:** `"autor"` con `ana_dev`, y en `"hilo" → "participantes"` a **`luis_dev`**
con `"comentarios": 1`.

**Dices:**
> «Este es el momento que mejor muestra la arquitectura. La discusión pertenece a un módulo
> y los comentarios a otro, y los hicieron personas distintas del equipo. El hilo de
> participantes se diseñó antes de que existieran los comentarios, y se llenó solo cuando
> llegaron, sin cambiar una línea. Los módulos se comunican por contratos, no por dentro.»

#### Paso 8 · Una reacción por persona

**Pestaña:** 2 (Luis)

**Haces:** **Reacciones** → `PUT /api/v1/publicaciones/{publicacionId}/reaccion` → pega
**D1**: `8cf80762-d377-43dc-82fb-e8d20b72cbd9` → cuerpo:
```json
{"tipo":"ME_GUSTA"}
```
Pulsa **Execute**, mira el total, y **pulsa Execute otra vez**.

**Debes ver:** las dos veces `"total": 1`. No sube a 2.

**Dices:**
> «Reacciono dos veces y el total sigue en uno: cada persona tiene una sola reacción por
> publicación. No lo garantiza solo el código, sino la clave primaria de la tabla.»

> **Si vas con tiempo:** **Discusiones** → `GET /api/v1/discusiones` con `tecnologia` = `java`
> y `limite` = `1`. Muestra `"hayMas": true` y un `"cursorSiguiente"`: es la paginación que
> nunca repite elementos entre páginas (HU-12).

---

### ⏱ 3:15 – 4:15 · Seguimiento y mensajería privada (HU-15)

#### Paso 9 · Nadie se sigue a sí mismo

**Pestaña:** 2 (Luis)

**Haces:** **Seguimiento** → `PUT /api/v1/perfiles/{id}/seguimiento` → en `id` pega el
**ID de Luis** (el suyo propio): `7dcceb38-5409-4e11-b07e-bc1a82babaf7`

**Debes ver:** `422` con `"errorCode": "SEGUIMIENTO_PROPIO"`.

**Dices:**
> «Una regla de negocio, por eso es un 422. La base de datos también lo impide con una
> restricción, pero la API responde antes y con un mensaje claro.»

#### Paso 10 · Luis escribe a Ana

**Pestaña:** 2 (Luis)

**Haces:** **Mensajeria** → `POST /api/v1/mensajes` → cuerpo (ya lleva el ID de Ana):
```json
{"destinatarioId":"7ea84926-4bc6-4cba-b22e-1643d54f204b","contenido":"Hola Ana, ¿colaboramos en el motor de plantillas?"}
```

**Debes ver:** `201`.

#### Paso 11 · Ana ve su bandeja

**Pestaña:** 1 (Ana)

**Haces:** **Mensajeria** → `GET /api/v1/conversaciones`.

**Debes ver:** `200` con la conversación con Luis y su último mensaje.

**Dices:**
> «Ana ve su bandeja, y solo la suya. Si una tercera persona intenta leer esta
> conversación recibe un 404, no un 403, para no confirmar siquiera que existe. Y el
> intento queda auditado.»

---

### ⏱ 4:15 – 5:00 · Cierre: lo que no se ve en Swagger

**Pestaña:** GitHub → **Actions**, con un check verde a la vista.

**Haces:** nada en Swagger. Solo muestra la pantalla mientras hablas.

**Dices:**
> «Detrás de lo que acaban de ver hay tres cosas que no se ven en Swagger.
>
> **Integración continua:** cada cambio pasa por un pipeline que ejecuta más de doscientas
> pruebas, unitarias y de integración contra PostgreSQL real. Si algo falla, no se puede
> integrar, y a producción solo llega lo que pasó las pruebas.
>
> **Base de datos:** la aplicación ya no se conecta como administrador, sino con un usuario
> que no puede modificar el esquema. Medimos las consultas con el volumen proyectado a un
> año y encontramos una que recorría doscientos cincuenta mil comentarios: la bajamos de 62
> a 0,3 milisegundos sin crear ningún índice.
>
> **Seguridad:** analizamos la API contra el OWASP Top 10 de 2025, sin vulnerabilidades
> críticas, y cada hallazgo tiene plazo de corrección.
>
> Eso fue el sprint 2 de DevNet.»

---

## Parte C · Si algo sale mal

| Ves esto | Por qué | Qué haces |
|---|---|---|
| La primera petición tarda un minuto | Render estaba dormido | Repite el paso A1 justo antes de grabar |
| `401 AUTH_REQUERIDA` | El token caducó (15 min) | En esa pestaña: `inicio-sesion` → **Authorize** de nuevo |
| `409 PROYECTO_NO_EDITABLE` en el paso 3 | P2 ya se archivó en un ensayo | Crea otro «Sistema de Gestión» (A4) y usa su id |
| `422 PROYECTO_CONFIRMACION_INVALIDA` en el paso 3 | El título no es idéntico (tilde, mayúscula) | Copia el título tal cual: `Sistema de Gestión` |
| El paso 4 no encuentra el proyecto | P1 no existe o se escribió distinto | Revisa con `GET /api/v1/proyectos/autor/{autorId}` y el ID de Ana (`7ea84926-4bc6-4cba-b22e-1643d54f204b`) |
| En el paso 7 el hilo sale vacío | El paso 6 se hizo sobre otra publicación | Repite el paso 6 con **D1** |
| En el paso 8 el total es 2 | Luis o Ana ya habían reaccionado en un ensayo | No pasa nada: lo que importa es que **no sube** al repetir |
| `400` en el paso 10 | Falta el `destinatarioId` o tiene comillas de más | Pega solo el UUID entre las comillas |

## Parte D · Lista de comprobación antes de pulsar «Grabar»

- [ ] `/actuator/health` responde `UP`.
- [ ] Pestaña 1 autorizada como Ana; pestaña 2 como Luis. Hace menos de 10 minutos.
- [ ] Los identificadores de la tabla A5 son los de esta preparación.
- [ ] P2 **no** está archivado. Si ensayaste, crea uno nuevo.
- [ ] Secciones de Swagger contraídas en las dos pestañas.
- [ ] GitHub → Actions abierto con un check verde.
- [ ] Zoom del navegador al 110–125 % y notificaciones silenciadas.
- [ ] Un ensayo completo con cronómetro. Si pasas de 5 minutos, omite los pasos 5 y 9.
