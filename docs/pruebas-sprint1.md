# Pruebas de Sprint 1 — verificación manual extremo a extremo

Guion para comprobar que **todo lo construido funciona de verdad**, ejecutando la imagen
Docker contra PostgreSQL, que es como corre en producción.

No sustituye a las pruebas automáticas: las complementa. Lo que aquí se verifica es que el
artefacto desplegable arranca, aplica sus migraciones y responde según el contrato.

- Tiempo aproximado: 25 minutos.
- Requisitos: Docker, `curl` y `psql` (o cualquier cliente de PostgreSQL).
- Todo lo que hay que copiar está escrito para `bash`. En PowerShell, usa `curl.exe` en
  lugar de `curl`, que ahí es un alias de `Invoke-WebRequest`.

---

## 0 · Preparación

### 0.1 Levantar la base de datos

```bash
cd ~/Documents/integracion
docker compose up -d
docker compose ps        # debe decir "healthy"
```

### 0.2 Generar el par RSA

Si no lo hiciste ya. Fuera del repositorio o bórralo después.

```bash
openssl genpkey -algorithm RSA -out /tmp/privada.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in /tmp/privada.pem -out /tmp/publica.pem
```

### 0.3 Construir y arrancar la imagen

```bash
docker build -t devnet-api:local .

docker run --rm --name devnet-api \
  --network integracion_default \
  -p 8080:8080 \
  -e DATABASE_URL='jdbc:postgresql://devnet-db:5432/devnet' \
  -e DATABASE_USERNAME='postgres' \
  -e DATABASE_PASSWORD='postgres' \
  -e API_URL='http://localhost:8080' \
  -e CORS_ORIGENES='http://localhost:5173' \
  -e JWT_CLAVE_PRIVADA="$(cat /tmp/privada.pem)" \
  -e JWT_CLAVE_PUBLICA="$(cat /tmp/publica.pem)" \
  -e DEVNET_SEGURIDAD_COOKIESEGURA=false \
  devnet-api:local
```

Tres cosas que explicar de ese comando:

- **`--network integracion_default`** conecta el contenedor a la red que creó
  `docker compose`, donde la base responde por su nombre `devnet-db`. Si tu carpeta no se
  llama `integracion`, comprueba el nombre con `docker network ls`.
- **`DEVNET_SEGURIDAD_COOKIESEGURA=false`** es *solo para esta prueba local*. El perfil
  `prod` marca la cookie de refresco como `Secure`, y `curl` —igual que un navegador— se
  niega a enviar una cookie `Secure` por `http://`. En Render, sobre HTTPS, se queda en
  `true`. **Si la omites, los pasos 3 y 4 fallarán y no será culpa del código.**
- La imagen trae `SPRING_PROFILES_ACTIVE=prod`, así que esto ejercita el mismo perfil que
  se despliega: sin Swagger, con logs en WARN y sin ningún valor por defecto.

### 0.4 Comprobar el arranque

En el log del contenedor busca, en este orden:

| Qué buscar | Significa |
|---|---|
| `Migrating schema "public" to version "1 - baseline"` … hasta `"5 - eventos de refresco en auditoria"` | Flyway aplicó las cinco migraciones |
| `Tomcat started on port 8080` | Arrancó |
| ❌ `wrong column type encountered in column [ip_origen]` | **El mapeo de `inet` falló.** Es el único riesgo técnico conocido; anótalo y sigue: el resto de pruebas no depende de él |
| ❌ `No hay claves JWT configuradas` | Las variables `JWT_*` no llegaron |

```bash
export API=http://localhost:8080
curl -s $API/actuator/health
```

Esperado: `{"status":"UP"}`

---

## 1 · Registro

```bash
curl -s -o /tmp/r.json -w '%{http_code}\n' -X POST $API/api/v1/auth/registro \
  -H 'Content-Type: application/json' \
  -d '{"correo":"ana@devnet.test","clave":"Devnet2026!","nombreUsuario":"ana_dev"}'
cat /tmp/r.json
```

| | Esperado |
|---|---|
| Código | `201` |
| Cuerpo | `id`, `correo`, `nombreUsuario` |

**Verifica que el perfil se creó solo.** Al registrar, `identity` publica el evento
`UsuarioRegistrado` y `profile` lo recoge:

```bash
docker exec -i devnet-db psql -U postgres -d devnet \
  -c "SELECT u.nombre_usuario, p.usuario_id IS NOT NULL AS tiene_perfil
        FROM usuario u LEFT JOIN perfil p ON p.usuario_id = u.id;"
```

### 1.1 Correo duplicado

```bash
curl -s -w '\n%{http_code}\n' -X POST $API/api/v1/auth/registro \
  -H 'Content-Type: application/json' \
  -d '{"correo":"ana@devnet.test","clave":"Devnet2026!","nombreUsuario":"otra"}'
```

Esperado: `409` con `"errorCode":"AUTH_CORREO_YA_REGISTRADO"`.

### 1.2 El contrato de errores (ADR-005)

```bash
curl -s -i -X POST $API/api/v1/auth/registro \
  -H 'Content-Type: application/json' \
  -d '{"correo":"no-es-un-correo","clave":"x"}'
```

Esperado: `400`, con `errorCode`, `message`, `details`, `traceId`, `timestamp` y `path` en
el cuerpo, y la cabecera **`X-Trace-Id`** en la respuesta. El `traceId` del cuerpo y el de
la cabecera deben coincidir: es lo que permite correlacionar un reporte de usuario con la
línea del log.

---

## 2 · Inicio de sesión

```bash
curl -s -c /tmp/cookies.txt -o /tmp/login.json -D /tmp/login.head \
  -X POST $API/api/v1/auth/inicio-sesion \
  -H 'Content-Type: application/json' \
  -d '{"correo":"ana@devnet.test","clave":"Devnet2026!"}'

grep -i 'set-cookie' /tmp/login.head
cat /tmp/login.json
```

| | Esperado |
|---|---|
| Código | `200` |
| Cuerpo | `token`, `tipo: Bearer`, `expiraEnSegundos: 900`, `roles: ["DESARROLLADOR"]`, `permisos`, `mfaPendiente: false` |
| `Set-Cookie` | `devnet_refresco=…; Path=/api/v1/auth; Max-Age=604800; HttpOnly; SameSite=Strict` |

**Comprueba que el refresco NO viene en el cuerpo.** Si apareciera, el `HttpOnly` no
serviría de nada: cualquier script de la página podría leerlo de la respuesta.

```bash
grep -c refresco /tmp/login.json     # debe imprimir 0
```

Guarda el token para los pasos siguientes:

```bash
export TOKEN=$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' /tmp/login.json)
export UID_ANA=$(sed -n 's/.*"usuarioId":"\([^"]*\)".*/\1/p' /tmp/login.json)
echo "${TOKEN:0:40}… / $UID_ANA"
```

### 2.1 Credenciales incorrectas

```bash
curl -s -w '\n%{http_code}\n' -X POST $API/api/v1/auth/inicio-sesion \
  -H 'Content-Type: application/json' \
  -d '{"correo":"ana@devnet.test","clave":"incorrecta"}'
```

Esperado: `401` `AUTH_CREDENCIALES_INVALIDAS`.

Prueba ahora con un correo **que no existe**. Debe devolver **exactamente el mismo código y
mensaje**: dos respuestas distintas permitirían enumerar cuentas registradas probando
correos.

### 2.2 Bloqueo por intentos fallidos

Cinco fallos seguidos bloquean 15 minutos:

```bash
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w "intento $i: %{http_code}\n" -X POST $API/api/v1/auth/inicio-sesion \
    -H 'Content-Type: application/json' \
    -d '{"correo":"ana@devnet.test","clave":"mal"}'
done
```

El quinto debe responder `AUTH_CUENTA_BLOQUEADA`. Desbloquea para seguir:

```bash
docker exec -i devnet-db psql -U postgres -d devnet \
  -c "UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL;"
```

> Vuelve a iniciar sesión (paso 2) para tener un `$TOKEN` fresco.

---

## 3 · Refresco de sesión

Esta es la parte nueva, y el punto 3.2 es el que justifica todo el mecanismo.

```bash
curl -s -b /tmp/cookies.txt -c /tmp/cookies.txt \
  -o /tmp/refresco.json -D /tmp/refresco.head \
  -X POST $API/api/v1/auth/refresco

head -1 /tmp/refresco.head
grep -i 'set-cookie' /tmp/refresco.head
```

Esperado: `200`, un `token` **distinto** del anterior, y una cookie nueva.

Comprueba en la base que el anterior quedó consumido y enlazado con su sucesor:

```bash
docker exec -i devnet-db psql -U postgres -d devnet -c "
  SELECT left(token_hash, 8) AS hash,
         consumido_en IS NOT NULL AS consumido,
         revocado_en  IS NOT NULL AS revocado,
         reemplazado_por IS NOT NULL AS enlazado
    FROM token_refresco ORDER BY emitido_en;"
```

Esperado: dos filas de la **misma familia**; la primera consumida y enlazada, la segunda
viva.

### 3.1 Sin cookie

```bash
curl -s -w '\n%{http_code}\n' -X POST $API/api/v1/auth/refresco
```

Esperado: `401` `AUTH_REFRESCO_AUSENTE`.

### 3.2 Detección de reuso ⭐

**La prueba que importa.** Simula una cookie robada: guardamos el refresco vigente, lo
usamos una vez con normalidad, y después presentamos la copia ya gastada.

```bash
# Copia del refresco actual (el "robado")
cp /tmp/cookies.txt /tmp/robado.txt

# El titular rota con normalidad
curl -s -o /dev/null -b /tmp/cookies.txt -c /tmp/cookies.txt \
  -X POST $API/api/v1/auth/refresco

# Alguien presenta la copia ya usada
curl -s -w '\n%{http_code}\n' -b /tmp/robado.txt -X POST $API/api/v1/auth/refresco
```

Esperado: `401` con **`"errorCode":"AUTH_REFRESCO_REUSADO"`**.

Ahora lo decisivo: **la familia entera cae, incluido el refresco legítimo del titular.**

```bash
curl -s -w '\n%{http_code}\n' -b /tmp/cookies.txt -X POST $API/api/v1/auth/refresco
```

Esperado: `401` `AUTH_REFRESCO_REVOCADO`. El titular también pierde la sesión, y es lo
correcto: no hay forma de saber cuál de los dos es el impostor.

```bash
docker exec -i devnet-db psql -U postgres -d devnet -c "
  SELECT count(*) FILTER (WHERE revocado_en IS NULL) AS vivos,
         count(*)                                    AS total
    FROM token_refresco;"
```

Esperado: `vivos = 0`.

Y que quedó constancia — esto es lo que hizo posible la migración V5:

```bash
docker exec -i devnet-db psql -U postgres -d devnet -c "
  SELECT operacion, count(*) FROM auditoria
   WHERE operacion LIKE 'REFRESCO%' OR operacion = 'CIERRE_SESION'
   GROUP BY operacion;"
```

Esperado: al menos un `REFRESCO_ROTADO` y exactamente un `REFRESCO_REUSADO`.

### 3.3 Cierre de sesión

```bash
curl -s -o /dev/null -c /tmp/cookies.txt -X POST $API/api/v1/auth/inicio-sesion \
  -H 'Content-Type: application/json' \
  -d '{"correo":"ana@devnet.test","clave":"Devnet2026!"}'

curl -s -i -b /tmp/cookies.txt -X POST $API/api/v1/auth/cierre-sesion | head -6
```

Esperado: `204`, y un `Set-Cookie` con `Max-Age=0`.

Sin cookie también debe dar `204`: es idempotente a propósito, porque responder distinto
según si el token existe permitiría sondear la tabla.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST $API/api/v1/auth/cierre-sesion
```

> Vuelve a iniciar sesión y reexporta `$TOKEN` antes de continuar.

---

## 4 · Perfil

El catálogo de tecnologías se consulta directamente en la base: **`GET /api/v1/tecnologias`
está permitido en la configuración de seguridad pero no tiene controlador.** Es un endpoint
fantasma pendiente, del mismo tipo que era `/auth/refresco`.

```bash
docker exec -i devnet-db psql -U postgres -d devnet \
  -c "SELECT id, nombre FROM tecnologia WHERE aprobada ORDER BY id LIMIT 12;"
```

```bash
curl -s -w '\n%{http_code}\n' -X PUT $API/api/v1/perfiles/$UID_ANA \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
        "nombre": "Ana Restrepo",
        "biografia": "Backend developer con foco en sistemas distribuidos.",
        "tecnologias": [
          {"tecnologiaId": 1, "nivel": "AVANZADO", "anios": 5},
          {"tecnologiaId": 7, "nivel": "INTERMEDIO", "anios": 2}
        ],
        "githubUrl": "https://github.com/ana",
        "linkedinUrl": "https://linkedin.com/in/ana"
      }'
```

Esperado: `200`. Los `githubUrl` y `linkedinUrl` son las columnas que añadió la **V3**, y
las tecnologías van por identificador de catálogo, no por texto libre — que es justo lo que
unificó la **V4**.

```bash
curl -s $API/api/v1/perfiles/$UID_ANA      # lectura pública, sin token
```

### 4.1 Nadie edita el perfil ajeno

Crea una segunda cuenta y prueba a editar el perfil de Ana con su token:

```bash
curl -s -o /dev/null -X POST $API/api/v1/auth/registro \
  -H 'Content-Type: application/json' \
  -d '{"correo":"beto@devnet.test","clave":"Devnet2026!","nombreUsuario":"beto_dev"}'

TOKEN_BETO=$(curl -s -X POST $API/api/v1/auth/inicio-sesion \
  -H 'Content-Type: application/json' \
  -d '{"correo":"beto@devnet.test","clave":"Devnet2026!"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

curl -s -w '\n%{http_code}\n' -X PUT $API/api/v1/perfiles/$UID_ANA \
  -H "Authorization: Bearer $TOKEN_BETO" \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Secuestrado","biografia":"No deberia poder.","tecnologias":[{"tecnologiaId":1,"nivel":"BASICO"}]}'
```

Esperado: `403`. La autorización se verifica **en el servidor**, no en el cliente.

### 4.2 Avatar

```bash
curl -s -w '\n%{http_code}\n' -X PATCH $API/api/v1/perfiles/$UID_ANA/avatar \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"avatarUrl":"https://cdn.devnet.test/avatares/ana.png"}'
```

---

## 5 · Proyectos

```bash
curl -s -w '\n%{http_code}\n' -X POST $API/api/v1/proyectos \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
        "titulo": "Motor de plantillas en Java",
        "descripcion": "Un motor de plantillas minimalista escrito en Java 17 con soporte para expresiones.",
        "tecnologias": [1, 7],
        "urlRepositorio": "https://github.com/ana/motor-plantillas",
        "estadoProyecto": "EN_DESARROLLO",
        "licencia": "MIT",
        "buscaColaboradores": true
      }'
```

Esperado: `201` con `id`, `titulo` y `publicadoEn`. Guarda el identificador:

```bash
export PROYECTO=<el id devuelto>
curl -s "$API/api/v1/proyectos/autor/$UID_ANA"     # público, paginado por cursor
```

### 5.1 Validaciones

| Prueba | Esperado |
|---|---|
| `"titulo": "abc"` (menos de 5) | `422`, detalle sobre `titulo` |
| `"descripcion": "corta"` (menos de 20) | `422`, detalle sobre `descripcion` |
| `"tecnologias": []` | `422` |
| Sin cabecera `Authorization` | `401` |
| `"autorId"` de otro usuario | `403` — solo el autor publica en su nombre |

### 5.2 Un desarrollador no puede moderar

```bash
curl -s -w '\n%{http_code}\n' -X POST $API/api/v1/proyectos/$PROYECTO/ocultamiento \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"motivo":"Contenido duplicado de otro proyecto ya publicado en la plataforma."}'
```

Esperado: `403` `ACCESO_DENEGADO`. **Y el intento queda registrado** (HU-06, criterio 1):

```bash
docker exec -i devnet-db psql -U postgres -d devnet -c "
  SELECT operacion, registro_id, ip FROM auditoria
   WHERE operacion = 'ACCESO_DENEGADO' ORDER BY creado_en DESC LIMIT 3;"
```

---

## 6 · Moderación

Hace falta un usuario con el permiso `publicacion:moderar`. No hay endpoint para asignar
roles, así que se hace por SQL.

**Atención al segundo `UPDATE`:** el rol `MODERADOR` exige MFA. Si `mfa_habilitado` queda en
`false`, el usuario se autentica pero **su token sale sin ningún permiso** y `mfaPendiente`
viene en `true`. Es el comportamiento correcto, y es la causa más probable de que esta
sección "no funcione".

```bash
docker exec -i devnet-db psql -U postgres -d devnet <<'SQL'
UPDATE usuario SET mfa_habilitado = true WHERE nombre_usuario = 'beto_dev';

DELETE FROM usuario_rol
 WHERE usuario_id = (SELECT id FROM usuario WHERE nombre_usuario = 'beto_dev');

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT u.id, r.id FROM usuario u, rol r
 WHERE u.nombre_usuario = 'beto_dev' AND r.codigo = 'MODERADOR';
SQL
```

Los permisos por rol se cachean en Caffeine 30 minutos. Reinicia el contenedor para
vaciarla, o espera:

```bash
docker restart devnet-api
```

```bash
TOKEN_MOD=$(curl -s -X POST $API/api/v1/auth/inicio-sesion \
  -H 'Content-Type: application/json' \
  -d '{"correo":"beto@devnet.test","clave":"Devnet2026!"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

curl -s -w '\n%{http_code}\n' -X POST $API/api/v1/proyectos/$PROYECTO/ocultamiento \
  -H "Authorization: Bearer $TOKEN_MOD" \
  -H 'Content-Type: application/json' \
  -d '{"motivo":"Contenido duplicado de otro proyecto ya publicado en la plataforma."}'
```

Esperado: `204`.

### 6.1 El motivo es obligatorio

```bash
curl -s -w '\n%{http_code}\n' -X POST $API/api/v1/proyectos/$PROYECTO/ocultamiento \
  -H "Authorization: Bearer $TOKEN_MOD" \
  -H 'Content-Type: application/json' -d '{"motivo":"spam"}'
```

Esperado: `422`. Menos de 20 caracteres no vale: retirar contenido ajeno se justifica por
escrito, y ese texto queda en el historial.

### 6.2 La transición dejó rastro (R5)

```bash
docker exec -i devnet-db psql -U postgres -d devnet -c "
  SELECT estado_anterior, estado_nuevo, motivo, creado_en
    FROM historial_estado_publicacion ORDER BY creado_en DESC LIMIT 3;"
```

Esperado: una fila `PUBLICADO → OCULTO` con el motivo completo y el `actor_id` del
moderador. El historial se escribe **en la misma transacción** que el cambio de estado: si
no se puede escribir, la transición no ocurre.

---

## 7 · Verificación del esquema

```bash
docker exec -i devnet-db psql -U postgres -d devnet -c "
  SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Esperado: cinco filas, todas con `success = t`.

```bash
docker exec -i devnet-db psql -U postgres -d devnet -c "
  SELECT count(*) AS tablas FROM pg_tables WHERE schemaname = 'public';
  SELECT count(*) AS parciales FROM pg_indexes
   WHERE schemaname = 'public' AND indexdef LIKE '%WHERE%';
  SELECT to_regclass('public.perfil_habilidad') AS debe_ser_null;"
```

| Consulta | Esperado |
|---|---|
| `tablas` | 29 |
| `parciales` | 11 |
| `debe_ser_null` | vacío — la V4 eliminó `perfil_habilidad` |

Para una comprobación exhaustiva de columnas y restricciones está
[`bd/verificacion-instalacion.sql`](bd/verificacion-instalacion.sql).

---

## 8 · Seguridad del despliegue

| Comprobación | Comando | Esperado |
|---|---|---|
| Swagger abierto (ADR-007) | `curl -s -o /dev/null -w '%{http_code}\n' $API/swagger-ui.html` | `302` — redirige a `/swagger-ui/index.html`. Con `-L` da `200` |
| Contrato OpenAPI accesible | `curl -s -o /dev/null -w '%{http_code}\n' $API/v3/api-docs` | `200` |
| Métricas cerradas | `curl -s -o /dev/null -w '%{http_code}\n' $API/actuator/metrics` | `401` |
| Salud abierta | `curl -s $API/actuator/health` | `{"status":"UP"}` |
| CORS restringido | `curl -s -i -H 'Origin: https://sitio-no-permitido.test' $API/api/v1/perfiles/$UID_ANA \| grep -i access-control-allow-origin` | sin cabecera |
| El contenedor no corre como root | `docker exec devnet-api whoami` | `devnet` |

---

## Resumen

| # | Área | Resultado |
|---|---|---|
| 0 | Arranque y migraciones V1–V5 | ☐ |
| 1 | Registro, duplicados y contrato de errores | ☐ |
| 2 | Login, antienumeración y bloqueo | ☐ |
| 3 | Refresco, **detección de reuso** y cierre | ☐ |
| 4 | Perfil, autorización y enlaces de la V3 | ☐ |
| 5 | Publicar proyecto y validaciones | ☐ |
| 6 | Moderación, motivo obligatorio e historial | ☐ |
| 7 | Esquema: 29 tablas, 11 parciales, sin `perfil_habilidad` | ☐ |
| 8 | Endurecimiento del despliegue | ☐ |

### Limpieza

```bash
docker stop devnet-api
docker compose down -v      # el -v borra el volumen y con él los datos de prueba
rm /tmp/privada.pem /tmp/publica.pem /tmp/cookies.txt /tmp/robado.txt
```

### Lo que este guion NO cubre

- **Concurrencia en la rotación.** Que dos peticiones simultáneas con el mismo refresco
  solo dejen ganar a una depende del `UPDATE ... WHERE consumido_en IS NULL`. Eso lo
  verifica `RefrescoIT`, que hoy **no lo ejecuta nadie** porque el `maven-failsafe-plugin`
  no está en el `pom.xml`.
- **Rendimiento.** El §4.3 fija una línea base de 200 solicitudes por minuto con respuesta
  bajo 30 s. Hace falta una prueba de carga aparte.
- **El despliegue real en Render.** Los mismos pasos 1 a 8 sirven cambiando `$API` por la
  URL del servicio; ten en cuenta que allí la cookie va con `Secure` y `curl` la enviará
  sin problema porque la conexión es HTTPS.