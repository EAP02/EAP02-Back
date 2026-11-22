# Pruebas de Sprint 3 — reporte de actividad y crecimiento (HU-19)

Complementa a [`pruebas-sprint3.md`](pruebas-sprint3.md) (HU-21) y
[`pruebas-sprint3-hu20.md`](pruebas-sprint3-hu20.md) (HU-20).

Guion para comprobar desde **Swagger UI**, contra el despliegue real, que HU-19 cumple sus
criterios: **<https://eap02-back.onrender.com/swagger-ui.html>**

Mismo método que [`pruebas-sprint2.md`](pruebas-sprint2.md): cada respuesta se contrasta con
la base. Uso de Swagger y cambio de usuario:
[`pruebas-swagger.md`](pruebas-swagger.md#cómo-se-usa-swagger-ui). El token dura 15 minutos.

- Duración: unos 15 minutos.
- Necesitas: `luis@devnet.test` (desarrollador) y `admin@devnet.test` (**ADMIN con MFA
  inscrito**, preparado como en el
  [paso 3 de `pruebas-sprint2.md`](pruebas-sprint2.md#3-deja-listo-al-administrador)).
- Esta versión incluye la migración **V13**: Flyway la aplica sola al desplegar. **Si el admin
  ya tenía sesión, ciérrala y entra de nuevo**: el permiso `metricas:consultar` viaja en el
  token y la caché de permisos dura 30 minutos.
- Usa fechas de **hoy hacia atrás** (por ejemplo, los últimos 30 días): es donde hay datos de
  tus pruebas anteriores.

Endpoint: `GET /api/v1/reportes/actividad?desde=AAAA-MM-DD&hasta=AAAA-MM-DD&agrupacion=SEMANA|MES`

---

## 1 · Solo los administradores · criterio 4

| # | Acción | Resultado esperado |
|---|---|---|
| 1.1 | Sin token, `desde`/`hasta` de los últimos 30 días | `401 AUTH_REQUERIDA` |
| 1.2 | Como **Luis** (desarrollador) | `403 ACCESO_DENEGADO` |
| 1.3 | Como **admin** | `200` |

**Contrasta 1.2 en la base:** el intento quedó auditado.

```sql
SELECT creado_en, operacion, registro_id
  FROM auditoria
 WHERE operacion = 'ACCESO_DENEGADO' AND registro_id LIKE '%reportes/actividad%'
 ORDER BY creado_en DESC LIMIT 3;
```

## 2 · Rango válido · criterios 1 y 2

Como admin, `desde` = hace 30 días, `hasta` = hoy, `agrupacion` = `SEMANA`.

**Qué debe verse:** `usuariosActivos`, `proyectos`, `discusiones`, `comentarios`,
`nuevosUsuarios` y un `crecimiento` con un punto por semana (`periodo` = lunes de la semana,
`nuevos`, `acumulado`). `sinActividad` en `false`.

**Contrasta en la base** (reemplaza las fechas):

```sql
SELECT sum(proyectos) AS proyectos, sum(discusiones) AS discusiones,
       sum(comentarios) AS comentarios, sum(usuarios_nuevos) AS nuevos
  FROM metricas_diarias
 WHERE fecha BETWEEN '2026-10-15' AND '2026-11-14';

SELECT fn_usuarios_activos(DATE '2026-10-15', DATE '2026-11-14') AS activos;
```

Los números deben coincidir con la respuesta. El acumulado del último punto debe ser igual a
`nuevosUsuarios`.

Repite con `agrupacion=MES`: un punto por mes calendario. Sin `agrupacion` se usa `SEMANA`.

## 3 · Rango inválido · criterio 3

| # | Parámetros | Resultado esperado |
|---|---|---|
| 3.1 | `desde` posterior a `hasta` | `400 RANGO_FECHAS_INVALIDO` |
| 3.2 | sin `desde` ni `hasta` | `400 RANGO_FECHAS_REQUERIDO` |
| 3.3 | `desde=2026-01-01`, `hasta=2026-11-14` | `400 RANGO_FECHAS_DEMASIADO_AMPLIO` (máximo 92 días) |
| 3.4 | `agrupacion=ANIO` | `400 AGRUPACION_INVALIDA` |
| 3.5 | `desde=ayer` | `400 PARAMETRO_INVALIDO` |

En todos, el cuerpo trae `errorCode`, `message` y `traceId`.

## 4 · Estado vacío · criterio 5

Como admin, consulta un rango sin actividad (por ejemplo, enero de 2020).

**Qué debe verse:** `200`, `sinActividad: true`, todos los totales en `0` y `crecimiento: []`.
Es un estado vacío, no un error.

## 5 · Cálculo bajo demanda (ADR-009)

Los días cerrados se consolidan la primera vez que se consultan y no se vuelven a calcular.

```sql
SELECT fecha, calculado_en FROM metricas_diarias
 WHERE fecha BETWEEN '2026-10-15' AND '2026-11-14' ORDER BY fecha;
```

1. Tras la consulta del paso 2, deben existir filas para todos los días del rango hasta hoy.
2. Repite la consulta y vuelve a ejecutar el SQL: `calculado_en` **no cambia** en los días
   cerrados. El de **hoy** sí se recalcula, porque el día no ha terminado.

## Limitaciones conocidas

- Rango máximo de 92 días por consulta (límite a consultas costosas, §5.3).
- Los usuarios activos se calculan en vivo con `fn_usuarios_activos` (un distinto por rango no
  se puede sumar desde las cifras diarias); su definición es la misma de V11. Riesgo H-28 en
  [`seguridad/owasp-top-10.md`](seguridad/owasp-top-10.md).
- La primera consulta de un rango largo es más lenta, pues consolida los días pendientes.
