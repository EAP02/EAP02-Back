# ADR-004: Identidad propia en Spring Security, con GitHub como proveedor de identidad federado

- **Estado:** Aceptada
- **Fecha:** 2026-09-13
- **Responsable:** _[completar nombre]_ — Arquitectura de Software
- **Historias relacionadas:** épica de Identidad y acceso

## Contexto

§3.4 impone al proyecto avanzado una lista de controles que no son negociables:

- MFA para accesos administrativos o sensibles.
- Políticas de contraseña seguras, bloqueo ante intentos fallidos, controles contra
  credenciales comprometidas, y **no** exigir cambios periódicos sin indicio de compromiso.
- Revocación, expiración y **rotación segura** de tokens; cookies `SameSite`, `HttpOnly`
  y `Secure` cuando corresponda.
- OIDC u OAuth 2.0 cuando se integre un proveedor de identidad autorizado.
- Mínimo privilegio, **RBAC por endpoint** y reglas ABAC simples "cuando la propiedad o
  el contexto determinen el acceso".

§6.2 refuerza: "centralizar la decisión de autorización y **verificarla en el servidor**
para cada operación protegida".

El dominio del caso agrega un requisito propio: es una red social **para
desarrolladores**. La identidad de GitHub no es solo un método de acceso cómodo, es
parte del perfil técnico del usuario y la fuente de los repositorios que publica.

## Alternativas consideradas

### A. Supabase Auth (GoTrue)

Ya que la base de datos está en Supabase ([ADR-003](ADR-003-persistencia-supabase-flyway.md)),
usar su servicio de autenticación parece la vía de menor esfuerzo: registro, inicio de
sesión, OAuth con GitHub y MFA vienen resueltos.

Se descarta por una razón de fondo: **partiría la autorización en dos**. Los usuarios y
sus roles vivirían en el esquema `auth` de Supabase, mientras las reglas de negocio que
dependen de esos roles (quién modera, quién publica, quién puede escribir a quién) viven
en el backend. Las reglas ABAC del caso —"solo el autor edita lo suyo", "el moderador que
resuelve debe ser distinto del que reportó"— necesitan consultar el grafo del dominio, no
solo un token. Terminaríamos con dos fuentes de verdad sobre la identidad y con un
`usuario_id` que hay que sincronizar entre dos sistemas. §6.2 pide exactamente lo
contrario.

Además, el requisito de MFA obligatorio para moderadores y administradores es una regla
de negocio nuestra ("este rol exige MFA"), no una preferencia del usuario; imponerla
desde fuera del proveedor es más frágil que decidirla en el backend.

### B. Keycloak como servidor de identidad dedicado

Estándar, completo, OIDC de verdad, MFA incluido. Es la respuesta correcta en un sistema
de producción con varios clientes.

Se descarta por costo operativo: es un contenedor más que desplegar, monitorear y
respaldar, con su propia base de datos, para un equipo de tres personas que despliega en
el plan mínimo de Render. El lineamiento mismo advierte contra incorporar componentes sin
capacidad de soporte (§7.3, a propósito de Kubernetes; el principio aplica igual).

### C. Identidad propia en Spring Security, federando GitHub por OIDC/OAuth 2.0

## Decisión

Se adopta la **alternativa C**.

### Dos vías de acceso, un solo usuario

| Vía | Descripción |
|---|---|
| Credenciales locales | Correo y contraseña, con Argon2id como función de derivación |
| GitHub | Flujo *Authorization Code* con PKCE, mediante `spring-boot-starter-oauth2-client` |

Ambas convergen en la misma entidad `usuario`. La tabla `identidad_externa` enlaza el
`sub` de GitHub con el usuario local, de modo que una persona puede tener ambas vías
sobre una sola cuenta. Un usuario creado por GitHub puede añadir contraseña después, y
uno local puede vincular GitHub después.

**GitHub cumple además una función de negocio:** tener la identidad de GitHub vinculada
es lo que marca un perfil como *verificado*, y ser verificado es requisito para publicar
(ver el proceso principal). Esto sustituye por completo la verificación por correo, lo
que elimina la dependencia de un proveedor de correo del alcance del proyecto y encaja
mejor con el dominio. La decisión es deliberada, no una omisión.

### Tokens

| Token | Formato | Vigencia | Transporte |
|---|---|---|---|
| Acceso | JWT firmado con RS256 | 15 minutos | Cabecera `Authorization: Bearer` |
| Refresco | Opaco, aleatorio de 256 bits, **solo el hash se almacena** | 7 días | Cookie `HttpOnly`, `Secure`, `SameSite=Strict` |

**Rotación con detección de reuso.** Cada refresco pertenece a una *familia*
(`token_refresco.familia`). Al usarse, el token se marca como consumido y se emite uno
nuevo de la misma familia. Si llega un token ya consumido, se asume robo y se **revoca
la familia completa**, forzando un nuevo inicio de sesión. Esto es lo que convierte la
"rotación segura" de §3.4 en un control real y no en un cambio cosmético de cadena.

El JWT de acceso es de vida corta precisamente porque no se puede revocar; la revocación
opera sobre el refresco. Cerrar sesión revoca la familia.

#### Estado de la implementación (2026-09-18)

Implementado en `identity`: `POST /api/v1/auth/refresco` y `POST /api/v1/auth/cierre-sesion`,
con rotación por familia, detección de reuso y revocación en cascada. El hash es **SHA-256,
no Argon2id**: debe ser determinista para poder buscar por el índice único de `token_hash`,
y un valor de 256 bits de entropía real no tiene diccionario que atacar.

Cuatro cosas que el ADR daba por hechas y conviene precisar:

1. **La revocación no es inmediata, es diferida.** No existe ningún caso de uso que cambie
   `EstadoUsuario`, así que desactivar una cuenta no revoca sus familias en el acto. Lo que
   sí ocurre es que `RefrescarSesion` reevalúa el estado de la cuenta en cada rotación, de
   modo que la ventana máxima es la vigencia del token de acceso: **quince minutos**. El
   puerto expone `revocarDeUsuario` para cuando exista ese caso de uso.

2. **`SameSite=Strict` supone un cliente del mismo sitio.** En local lo es:
   `localhost:5173` y `localhost:8080` solo difieren en el puerto, que no cuenta. **Si el
   frontend se despliega en otro dominio, la cookie dejará de viajar** y habrá que pasar a
   `SameSite=None; Secure`, lo que reabre el vector CSRF que hoy `Strict` cierra. Decidirlo
   antes de desplegar, no durante.

3. **`token_refresco` crece sin techo.** Con siete días de vigencia y una rotación por cada
   refresco, una sesión activa genera del orden de 96 filas diarias y nada las borra. Hace
   falta un borrado periódico de las filas con `expira_en < now() - 30 días`. Es deuda que
   nace con el endpoint.

4. **El límite de tasa en `/auth/**` con Bucket4j sigue pendiente**, y ahora importa más:
   `/refresco` es un endpoint público que escribe en cada llamada.

### MFA

TOTP (RFC 6238), con códigos de recuperación de un solo uso. **Obligatorio** para los
roles `MODERADOR` y `ADMIN`: un usuario al que se le asigna uno de esos roles queda
obligado a inscribir su segundo factor antes de poder ejercer cualquier permiso del rol.
Opcional, pero disponible, para `DESARROLLADOR`.

### Autorización en dos niveles

1. **RBAC por endpoint**, basado en permisos y no en nombres de rol:

   ```java
   @PreAuthorize("hasAuthority('publicacion:moderar')")
   ```

   Los permisos se resuelven desde `rol_permiso` al emitir el token. Usar permisos en vez
   de roles permite reasignar capacidades sin tocar código.

2. **ABAC sobre el recurso**, cuando la propiedad o el contexto deciden:

   ```java
   @PreAuthorize("@politicaPublicacion.puedeEditar(#id, authentication)")
   ```

   Reglas ABAC del caso: solo el autor edita o borra lo propio; el moderador que resuelve
   un reporte debe ser distinto del reportante y del autor; solo un participante de una
   conversación lee sus mensajes; solo el dueño de un proyecto acepta colaboraciones.

Las políticas ABAC viven en el `domain` de cada módulo, no en los controladores, para
que sean unitariamente comprobables sin levantar el contexto web.

### Controles complementarios

- Bloqueo progresivo: 5 intentos fallidos bloquean 15 minutos (`usuario.intentos_fallidos`,
  `usuario.bloqueado_hasta`). Sin expiración periódica de contraseña, conforme a §3.4.
- Contraseñas comprometidas: verificación contra la API de Have I Been Pwned por
  *k-anonymity* en el registro y en el cambio de contraseña. No se envía la contraseña,
  solo los cinco primeros caracteres del hash SHA-1.
- Límite de tasa en `/auth/**` con Bucket4j.
- CORS restringido por lista blanca configurable por entorno.
- Los eventos de seguridad (inicio de sesión, fallo, bloqueo, cambio de rol, inscripción
  de MFA) se registran en `auditoria`. Nunca se registran contraseñas ni tokens (§6.2).

## Consecuencias

### Positivas

- Una sola fuente de verdad para identidad, roles y reglas de negocio. El `usuario_id`
  del dominio es el mismo del token.
- Las reglas ABAC pueden consultar el grafo del dominio sin salir del proceso.
- Todo el material de §3.4 queda evidenciable en código y en pruebas, que es lo que se
  evalúa.
- El inicio de sesión con GitHub refuerza el posicionamiento del producto y abre la
  importación de repositorios sin un segundo flujo de autorización.

### Negativas y cómo se mitigan

| Riesgo | Mitigación |
|---|---|
| Se asume responsabilidad sobre código de seguridad crítico | No se escribe criptografía propia: Spring Security, Nimbus JOSE, Argon2 de Spring Security Crypto, librería TOTP establecida |
| Más superficie que auditar | SAST y SCA obligatorios en el pipeline; los flujos de autenticación llevan pruebas negativas explícitas |
| El secreto TOTP es dato sensible en reposo | Cifrado a nivel de columna con clave en variable de entorno, nunca en el repositorio |
| Mayor esfuerzo inicial que usar Supabase Auth | Se concentra en el sprint 1 como rebanada vertical; es el módulo transversal que §3.1 exige de todos modos |

## Verificación

- Pruebas de integración por cada control: bloqueo tras 5 fallos, rechazo de token
  expirado, revocación de familia ante reuso de refresco, `403` sin el permiso requerido,
  `403` para el autor ajeno, y acceso denegado a rol con MFA pendiente de inscripción.
- Test de arquitectura: ningún endpoint fuera de la lista blanca pública puede carecer de
  anotación de autorización.
- Revisión de que ningún registro de log contenga `password`, `token` o `secret`,
  automatizada como prueba sobre el `appender` de pruebas.