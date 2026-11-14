# Pruebas de Sprint 3 — registro de auditoría (HU-20)

Complementa a [`pruebas-sprint3.md`](pruebas-sprint3.md), que cubre HU-21 (logging estructurado).

Guion para comprobar desde **Swagger UI**, contra el despliegue real, que HU-20 cumple sus
criterios: **<https://eap02-back.onrender.com/swagger-ui.html>**

Mismo método que [`pruebas-sprint2.md`](pruebas-sprint2.md): cada respuesta se contrasta con
la fila de la base. Cómo se usa Swagger y cómo cambiar de usuario:
[`pruebas-swagger.md`](pruebas-swagger.md#cómo-se-usa-swagger-ui). El token dura 15 minutos.

- Duración: unos 15 minutos.
- Necesitas: `luis@devnet.test` (desarrollador) y `admin@devnet.test` (**ADMIN con MFA
  inscrito**, preparado como en el
  [paso 3 de `pruebas-sprint2.md`](pruebas-sprint2.md#3-deja-listo-al-administrador); si ya lo
  hiciste, reutilízalo).
- Esta versión incluye la migración **V12**: Flyway la aplica sola al desplegar.

---

## 1 · Solo los administradores consultan · criterio 5

| # | Acción | Resultado esperado |
|---|---|---|
| 1.1 | `GET /api/v1/auditoria` **sin** token (botón *Authorize* vacío) | `401 AUTH_REQUERIDA` |
| 1.2 | Inicia sesión como **Luis** y repite | `403 ACCESO_DENEGADO` |
| 1.3 | Inicia sesión como **admin** y repite | `200` con `contenido`, `pagina`, `tamano`, `total`, `totalPaginas` |

**Contrasta 1.2 en la base** (SQL Editor): el intento de Luis quedó registrado.

```sql
SELECT a.creado_en, a.operacion, a.registro_id, u.nombre_usuario AS actor
  FROM auditoria a LEFT JOIN usuario u ON u.id = a.actor_id
 WHERE a.operacion = 'ACCESO_DENEGADO' AND a.registro_id = 'GET /api/v1/auditoria'
 ORDER BY a.creado_en DESC LIMIT 3;
```

## 2 · Inicios de sesión, exitosos y fallidos · criterio 1

1. En `POST /api/v1/auth/inicio-sesion` intenta entrar como `luis@devnet.test` con una clave
   **equivocada** → `401`. Después entra con la clave correcta → `200`.
2. Como admin: `GET /api/v1/auditoria?operacion=INICIO_SESION_FALLIDO`.
3. Y luego `?operacion=INICIO_SESION&actorId=<id de Luis>`.

**Qué debe verse:** el fallido trae `objeto.registroId = luis@devnet.test` y **`actor` nulo**
(si el correo no existiera, la fila sería indistinguible: no se puede enumerar cuentas leyendo
la auditoría). El exitoso trae `actor.nombreUsuario = luis_dev`, `accion`, `fecha` y
`objeto.tabla = usuario`.

## 3 · Cambios de rol y eliminación de contenido · criterio 2

**3.1 Un cambio de rol.** No hay endpoint para asignar roles: se hace por consola, y lo
captura el trigger de la V10. En el SQL Editor:

```sql
INSERT INTO usuario_rol (usuario_id, rol_id, asignado_por)
SELECT (SELECT id FROM usuario WHERE correo = 'luis@devnet.test'), r.id,
       (SELECT id FROM usuario WHERE correo = 'admin@devnet.test')
  FROM rol r WHERE r.codigo = 'MODERADOR';
```

Como admin: `GET /api/v1/auditoria?tabla=usuario_rol`. Debe aparecer un `INSERT` con
`objeto.registroId = <id de Luis>:<id del rol>`, `actor` = el admin y `datosDespues`. Mira
también `usuarioBd`: una fila hecha por consola trae el rol de base de datos que la hizo (no
`devnet_app`, que es la API). **Revierte** con
`DELETE FROM usuario_rol WHERE usuario_id = <id de Luis> AND rol_id = (SELECT id FROM rol WHERE codigo='MODERADOR');`
(queda registrado también).

**3.2 Eliminaciones de contenido.** Con los pasos 1.1–1.7 de `pruebas-sprint2.md` (Luis
comenta y elimina lo suyo; el admin elimina el comentario de Ana), después:
`GET /api/v1/auditoria?operacion=DELETE&tabla=comentario`.

**Qué debe verse:** **dos** filas. Una con `actor` = Luis (el autor retira lo suyo,
`datosAntes` nulo) y otra con `actor` = admin y `datosAntes.autor_id` = Ana: la intervención
sobre contenido ajeno dice de quién era.

## 4 · Quién, qué, cuándo y sobre qué · criterio 3

Abre cualquier fila de las anteriores. Debe tener los cuatro: `actor` (quién), `accion`
(qué), `fecha` (cuándo) y `objeto` (`tabla` + `registroId`). Además trae `traceId`: el mismo
de la cabecera `x-trace-id` de la petición original, para saltar al log (HU-21).

## 5 · Sin contraseñas ni tokens · criterio 4

Recorre la respuesta de los pasos 2 y 3: no debe aparecer ninguna clave ni ningún token. Y en
la base, la tabla entera:

```sql
SELECT count(*) FROM auditoria
 WHERE registro_id ILIKE '%Devnet2026%' OR datos_antes::text ILIKE '%Devnet2026%'
    OR datos_despues::text ILIKE '%Devnet2026%'
    OR registro_id LIKE 'eyJ%' OR datos_antes::text LIKE '%eyJ%' OR datos_despues::text LIKE '%eyJ%';
```

Debe dar `0`. (`eyJ` es como empieza todo JWT; `Devnet2026` es la clave de prueba.)

## 6 · Paginación, rango y errores

| # | Petición (como admin) | Resultado esperado |
|---|---|---|
| 6.1 | `?tamano=2` | 2 registros, `total` mayor, `totalPaginas` > 1; el primero es el más reciente |
| 6.2 | `?tamano=2&pagina=1` | Los dos siguientes, sin repetir ninguno |
| 6.3 | `?desde=2099-01-01&hasta=2099-01-31` | `200`, `contenido` vacío, `total 0` (estado vacío) |
| 6.4 | `?desde=2026-11-13&hasta=2026-11-01` | `400 RANGO_FECHAS_INVALIDO` |
| 6.5 | `?operacion=BORRAR_TODO` | `400 AUDITORIA_OPERACION_INVALIDA`, el mensaje lista las válidas |
| 6.6 | `?tamano=101` | `400 PAGINACION_INVALIDA` |
| 6.7 | `?actorId=abc` | `400 PARAMETRO_INVALIDO` |
| 6.8 | `DELETE /api/v1/auditoria` | `405`: el registro es de solo lectura |

---

## Pruebas automáticas

| Historia | Unitarias | Integración | Qué cubren |
|---|---|---|---|
| HU-20 | `RangoFechasTest` (5), `FiltroAuditoriaTest` (5), `PaginacionTest` (7), `ConsultarAuditoriaTest` (6) | `AuditoriaIT` (22) | Inicio exitoso y fallido; cambio de rol por trigger; eliminación por el autor y por un administrador; quién/qué/cuándo/objeto/traza; sin secretos; solo ADMIN con MFA (desarrollador, moderador, visitante, admin sin MFA, sin token); solo lectura; paginación, rango, vacío y los cuatro errores 400 |

Ejecutarlas: `.\mvnw.cmd -B verify` (requiere Docker para las `*IT`).

## Limitaciones conocidas

- **H-26** (OWASP): consultar la auditoría no deja rastro. Requiere una migración.
- **H-27**: el registro muestra IP y correos intentados a los administradores; es lo que
  permite investigar y se acepta con el acceso restringido a ADMIN con MFA.
- **H-22**: la tabla no tiene retención; crece con cada inicio de sesión.
- No hay endpoint para asignar roles: los cambios de rol se hacen por consola y por eso
  los captura un trigger y no la API (HU-20 no los pide, solo que queden registrados).
