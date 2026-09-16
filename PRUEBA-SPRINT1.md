# Guía de prueba — Sprint 1 (HU-06 y HU-07)

Paso a paso con lo que debe aparecer en cada punto. Si algo no coincide, ahí está el fallo.

---

## 0. Verificar Java — **hazlo primero**

```powershell
java -version
```

**Debe decir 17.** Si dice 11 o 21, nada de lo demás va a compilar:

```
openjdk version "17.0.x" ...
```

```powershell
mvn -version
docker ps
```

`docker ps` debe devolver una tabla vacía con encabezados. Si dice *"cannot connect to the Docker daemon"*, abre Docker Desktop y espera a que el icono deje de girar.

---

## 1. Generar el wrapper de Maven (una sola vez)

```powershell
cd backend
mvn -N wrapper:wrapper -Dmaven=3.9.9
```

Crea `mvnw`, `mvnw.cmd` y `.mvn/`. **Commitea esos tres** — desde ahí todo el equipo usa `.\mvnw` y da igual qué Maven tenga cada uno.

---

## 2. Compilar

```powershell
.\mvnw clean compile
```

**Esperado:** `BUILD SUCCESS`.

**Dos fallos que espero y cómo salir de ellos:**

| Error | Causa | Solución |
|---|---|---|
| `springdoc-openapi ... requires Java 21` o error al resolver `3.1.1` | La línea 3.1.1 está anunciada para Java 21+ | En `pom.xml`, baja `<springdoc.version>` a la última `3.0.x` |
| Conflicto de clases `com.fasterxml.jackson` vs `tools.jackson` | `logstash-logback-encoder` aún usa Jackson 2 | Quita esa dependencia del `pom.xml` y borra el bloque `<springProfile name="!local">` de `logback-spring.xml` |

Ninguno de los dos afecta la lógica de las HU.

---

## 3. Levantar la base de datos

```powershell
docker compose up -d
docker compose ps
```

**Esperado:** `devnet-db` en estado `Up` y `(healthy)`. Si dice `starting`, espera 10 segundos y repite.

---

## 4. Arrancar la aplicación

```powershell
.\mvnw spring-boot:run
```

**Debe aparecer, en este orden:**

1. Un `WARN` diciendo que **no hay claves JWT configuradas y se genera un par RSA efímero**. Es correcto en local — significa que los tokens dejan de valer al reiniciar.
2. Flyway aplicando **dos** migraciones:
   ```
   Successfully applied 2 migrations to schema "public"
   ```
3. Este bloque:
   ```
   ==========================================================
     DATOS DE DEMOSTRACION CREADOS  (solo perfil local)
     Clave para todos: Devnet2026!

       dev@devnet.test           DESARROLLADOR
       mod@devnet.test           MODERADOR  (MFA inscrito)
       mod-sin-mfa@devnet.test   MODERADOR  (MFA pendiente)
       admin@devnet.test         ADMIN
   ==========================================================
   ```
4. `Started DevNetApplication in X seconds`

**Si la app NO arranca y dice `Schema-validation: missing column` o similar**, es que una entidad JPA no corresponde al esquema. Eso es `ddl-auto=validate` haciendo su trabajo: el mensaje dice exactamente qué columna falla.

Abre <http://localhost:8080/swagger-ui.html> — deben verse dos grupos: **Autenticacion** y **Proyectos**.

---

## 5. HU-06 — Control de acceso por rol

### 5.1 Login como desarrollador

```powershell
curl.exe -s -X POST http://localhost:8080/api/v1/auth/inicio-sesion `
  -H "Content-Type: application/json" `
  -d "{\"correo\":\"dev@devnet.test\",\"clave\":\"Devnet2026!\"}"
```

**Esperado:** `roles: ["DESARROLLADOR"]`, `mfaPendiente: false`, y en `permisos` debe estar `publicacion:crear` **pero NO** `publicacion:moderar`.

Guarda el token:
```powershell
$dev = (curl.exe -s -X POST http://localhost:8080/api/v1/auth/inicio-sesion -H "Content-Type: application/json" -d "{\"correo\":\"dev@devnet.test\",\"clave\":\"Devnet2026!\"}" | ConvertFrom-Json).token
```

### 5.2 Criterio 1 — el desarrollador intenta moderar

```powershell
curl.exe -s -i -X POST http://localhost:8080/api/v1/proyectos/00000000-0000-0000-0000-000000000000/ocultamiento `
  -H "Authorization: Bearer $dev" -H "Content-Type: application/json" `
  -d "{\"motivo\":\"Contenido duplicado de otro proyecto ya publicado aqui.\"}"
```

**Esperado: `HTTP/1.1 403`** con este cuerpo:

```json
{
  "errorCode": "ACCESO_DENEGADO",
  "message": "No tienes permiso para realizar esta accion.",
  "details": [],
  "traceId": "4b1e9f2a7c3d5e80",
  "timestamp": "...",
  "path": "/api/v1/proyectos/.../ocultamiento"
}
```

Y una cabecera **`X-Trace-Id`** con el mismo valor que `traceId`.

### 5.3 Criterio 1 — comprobar que quedó registrado

```powershell
docker exec -it devnet-db psql -U devnet -d devnet -c "SELECT operacion, registro_id, actor_id, trace_id FROM auditoria WHERE operacion='ACCESO_DENEGADO' ORDER BY creado_en DESC LIMIT 3;"
```

**Esperado:** una fila con `ACCESO_DENEGADO`, la ruta en `registro_id`, el id del desarrollador en `actor_id`, y **el mismo `trace_id` que te devolvió la respuesta HTTP**. Esa coincidencia es la trazabilidad completa.

### 5.4 Criterio 2 — el moderador sí puede

```powershell
$mod = (curl.exe -s -X POST http://localhost:8080/api/v1/auth/inicio-sesion -H "Content-Type: application/json" -d "{\"correo\":\"mod@devnet.test\",\"clave\":\"Devnet2026!\"}" | ConvertFrom-Json).token
```

**Esperado:** `permisos` incluye `publicacion:moderar`, `mfaPendiente: false`.

```powershell
curl.exe -s -i -X POST http://localhost:8080/api/v1/proyectos/00000000-0000-0000-0000-000000000000/ocultamiento `
  -H "Authorization: Bearer $mod" -H "Content-Type: application/json" `
  -d "{\"motivo\":\"Contenido duplicado de otro proyecto ya publicado aqui.\"}"
```

**Esperado: `HTTP/1.1 404`** con `PROYECTO_NO_ENCONTRADO`.

> **Esto es lo importante:** el 404 demuestra que **pasó el control de acceso** y falló después, al no encontrar ese UUID. Si saliera 403, la autorización habría fallado.

### 5.5 El moderador sin MFA

```powershell
curl.exe -s -X POST http://localhost:8080/api/v1/auth/inicio-sesion `
  -H "Content-Type: application/json" `
  -d "{\"correo\":\"mod-sin-mfa@devnet.test\",\"clave\":\"Devnet2026!\"}"
```

**Esperado:** `roles: ["MODERADOR"]` pero **`permisos: []`** y **`mfaPendiente: true`**.

Mismo rol, cero capacidades. Es el lineamiento 3.4 funcionando.

### 5.6 Criterio 3 — sin token y con token manipulado

```powershell
curl.exe -s -i -X POST http://localhost:8080/api/v1/proyectos/00000000-0000-0000-0000-000000000000/ocultamiento -H "Content-Type: application/json" -d "{\"motivo\":\"Contenido duplicado de otro proyecto ya publicado aqui.\"}"
```
**Esperado: `401`** con `AUTH_REQUERIDA`.

```powershell
curl.exe -s -i -X POST http://localhost:8080/api/v1/proyectos/00000000-0000-0000-0000-000000000000/ocultamiento -H "Authorization: Bearer $($mod.Substring(0,$mod.Length-6))AAAAAA" -H "Content-Type: application/json" -d "{\"motivo\":\"Contenido duplicado de otro proyecto ya publicado aqui.\"}"
```
**Esperado: `401`.** La firma RS256 hace imposible fabricarse permisos.

---

## 6. HU-07 — Publicar un proyecto

### 6.1 Criterio 1 — publicar

```powershell
curl.exe -s -i -X POST http://localhost:8080/api/v1/proyectos `
  -H "Authorization: Bearer $dev" -H "Content-Type: application/json" `
  -d "{\"titulo\":\"Motor de plantillas en Java\",\"descripcion\":\"Un motor de plantillas minimalista escrito en Java 17 con soporte para expresiones.\",\"tecnologias\":[1,7],\"urlRepositorio\":\"https://github.com/ana/motor-plantillas\",\"estadoProyecto\":\"EN_DESARROLLO\"}"
```

**Esperado: `HTTP/1.1 201`**, cabecera `Location: /api/v1/proyectos/<uuid>`, y cuerpo con `id`, `titulo` y `publicadoEn`.

### 6.2 Criterio 1 — visible en su lista

Toma el `usuarioId` del login del desarrollador:

```powershell
curl.exe -s "http://localhost:8080/api/v1/proyectos/autor/<usuarioId>"
```

**Esperado**, y **sin token** (lectura pública):

```json
{
  "contenido": [{
    "titulo": "Motor de plantillas en Java",
    "autorNombreUsuario": "dev_ana",
    "tecnologias": ["Java", "Spring Boot"],
    "urlRepositorio": "https://github.com/ana/motor-plantillas",
    "estadoProyecto": "EN_DESARROLLO"
  }],
  "cursorSiguiente": null,
  "hayMas": false
}
```

### 6.3 Criterio 2 — faltan obligatorios

```powershell
curl.exe -s -i -X POST http://localhost:8080/api/v1/proyectos `
  -H "Authorization: Bearer $dev" -H "Content-Type: application/json" `
  -d "{\"descripcion\":\"corta\",\"tecnologias\":[]}"
```

**Esperado: `400`** con `VALIDACION_FALLIDA` y un `details` que trae **los tres campos a la vez**: `titulo`, `descripcion` y `tecnologias`. No de uno en uno.

### 6.4 Criterio 3 — publicar a nombre ajeno

```powershell
curl.exe -s -i -X POST http://localhost:8080/api/v1/proyectos `
  -H "Authorization: Bearer $dev" -H "Content-Type: application/json" `
  -d "{\"autorId\":\"11111111-1111-1111-1111-111111111111\",\"titulo\":\"Proyecto ajeno\",\"descripcion\":\"Intentando publicar en nombre de otra persona distinta.\",\"tecnologias\":[1]}"
```

**Esperado: `403`** con `PROYECTO_AUTOR_AJENO`.

> No se ignora en silencio a propósito: si lo hiciera, el cliente creería estar publicando por otro.

### 6.5 El cruce de las dos HU

```powershell
$admin = (curl.exe -s -X POST http://localhost:8080/api/v1/auth/inicio-sesion -H "Content-Type: application/json" -d "{\"correo\":\"admin@devnet.test\",\"clave\":\"Devnet2026!\"}" | ConvertFrom-Json).token

curl.exe -s -i -X POST http://localhost:8080/api/v1/proyectos -H "Authorization: Bearer $admin" -H "Content-Type: application/json" -d "{\"titulo\":\"Proyecto del admin\",\"descripcion\":\"Un administrador no tiene el permiso de crear publicaciones.\",\"tecnologias\":[1]}"
```

**Esperado: `403`.** El ADMIN administra, pero no tiene `publicacion:crear`. Los roles están genuinamente diferenciados, no son etiquetas.

---

## 7. Las pruebas automatizadas

Detén la app (`Ctrl+C`) y deja Docker corriendo:

```powershell
.\mvnw verify
```

**Esperado:**

```
Tests run: 30+, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Con cuatro clases:

| Clase | Qué prueba |
|---|---|
| `ProyectoTest` | HU-07, reglas de dominio, sin base de datos |
| `UsuarioTest` | HU-06, permisos y MFA, sin base de datos |
| `PublicarProyectoIT` | HU-07 por HTTP, con PostgreSQL real |
| `ControlAccesoPorRolIT` | HU-06 por HTTP, con PostgreSQL real |
| `FronterasModularesTest` | Que ningún módulo importe a otro |
| `EsquemaIT` | Que Flyway construyó las 29 tablas |

Verás Testcontainers descargando `postgres:16-alpine` la primera vez — tarda un par de minutos.

> `EsquemaIT` afirma que `MODERADOR` y `ADMIN` exigen MFA. Si algún día quitan el rol moderador, **ese test se pone rojo a propósito**: es la señal de que la migración funcionó.

---

## 8. Reportar un fallo

Si algo no coincide, mándame:

1. El comando que ejecutaste
2. La respuesta completa, **incluido el `traceId`**
3. Las últimas 30 líneas del log de la aplicación

Con el `traceId` llego a la línea exacta sin buscar a ciegas. Para eso existe.