# Diccionario de datos

## Cómo está mantenido este diccionario

El detalle por columna **no se transcribe aquí**. Vive como `COMMENT ON` dentro de
[`V1__baseline.sql`](../../src/main/resources/db/migration/V1__baseline.sql) y de las
migraciones posteriores (V2–V11), y se extrae del catálogo de PostgreSQL cuando se
necesita.

La razón es la misma que llevó a que el modelo físico y el script de estructuras sean un
solo artefacto ([ADR-003](../adr/ADR-003-persistencia-supabase-flyway.md)): un diccionario
escrito a mano en paralelo al esquema se desincroniza en el segundo sprint, y entonces el
equipo termina defendiendo una documentación que no describe la base real.

Lo que sí se mantiene a mano es el **inventario de tablas** de más abajo: qué es cada una y
qué cardinalidad tiene. Eso es estable y es lo que alguien necesita para orientarse.

### Generar el diccionario completo

```sql
SELECT
    c.relname                                    AS tabla,
    obj_description(c.oid, 'pg_class')           AS descripcion_tabla,
    a.attnum                                     AS orden,
    a.attname                                    AS columna,
    format_type(a.atttypid, a.atttypmod)         AS tipo,
    a.attnotnull                                 AS obligatoria,
    pg_get_expr(d.adbin, d.adrelid)              AS valor_por_defecto,
    col_description(c.oid, a.attnum)             AS descripcion_columna
FROM pg_class c
JOIN pg_namespace n     ON n.oid = c.relnamespace
JOIN pg_attribute a     ON a.attrelid = c.oid
LEFT JOIN pg_attrdef d  ON d.adrelid = c.oid AND d.adnum = a.attnum
WHERE n.nspname = 'public'
  AND c.relkind = 'r'
  AND a.attnum > 0
  AND NOT a.attisdropped
ORDER BY c.relname, a.attnum;
```

Y las restricciones, que suelen ser lo que un evaluador quiere ver:

```sql
SELECT
    rel.relname                       AS tabla,
    con.conname                       AS restriccion,
    CASE con.contype
        WHEN 'p' THEN 'PRIMARY KEY'
        WHEN 'f' THEN 'FOREIGN KEY'
        WHEN 'u' THEN 'UNIQUE'
        WHEN 'c' THEN 'CHECK'
    END                               AS tipo,
    pg_get_constraintdef(con.oid)     AS definicion,
    obj_description(con.oid, 'pg_constraint') AS justificacion
FROM pg_constraint con
JOIN pg_class rel     ON rel.oid = con.conrelid
JOIN pg_namespace n   ON n.oid = rel.relnamespace
WHERE n.nspname = 'public'
ORDER BY rel.relname, con.contype, con.conname;
```

Exportar a CSV desde psql cuando haya que adjuntarlo en Azure DevOps:

```
\copy (<consulta>) TO 'diccionario.csv' WITH CSV HEADER
```

Y la lógica que vive en la base (sprint 3): triggers, funciones y el procedimiento, cada uno
con su `COMMENT ON` como justificación:

```sql
SELECT
    t.event_object_table                         AS tabla,
    t.trigger_name                               AS trigger,
    t.action_timing || ' ' || string_agg(t.event_manipulation, ' OR ') AS cuando,
    regexp_replace(t.action_statement, '^EXECUTE FUNCTION ', '') AS ejecuta
FROM information_schema.triggers t
WHERE t.trigger_schema = 'public'
GROUP BY t.event_object_table, t.trigger_name, t.action_timing, t.action_statement
ORDER BY 1, 2;

SELECT
    p.proname                                    AS rutina,
    CASE p.prokind WHEN 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END AS tipo,
    pg_get_function_identity_arguments(p.oid)    AS argumentos,
    obj_description(p.oid, 'pg_proc')            AS justificacion
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
ORDER BY 1;
```

---

## Inventario de tablas

**29 tablas.** La columna "Volumen" proyecta a un año según la estimación de
[01-consultas-clave.md](01-consultas-clave.md).

### Identidad y acceso — 8 tablas

| Tabla | Propósito | Cardinalidad | Volumen |
|---|---|---|---|
| `usuario` | Credenciales, estado de la cuenta, MFA y bloqueo por intentos | 1:1 con `perfil` | 5 000 |
| `rol` | Catálogo de los cuatro roles, con la marca `exige_mfa` | N:M con `permiso` | 4 |
| `permiso` | Catálogo de capacidades evaluadas por `@PreAuthorize` | N:M con `rol` | 22 |
| `rol_permiso` | Tabla puente rol ↔ permiso | Puente | 43 |
| `usuario_rol` | Asignación de roles, con actor y fecha | Puente | 6 000 |
| `identidad_externa` | Vínculo con GitHub. Su existencia marca el perfil como verificado | N:1 con `usuario` | 4 000 |
| `token_refresco` | Sesiones activas, con familia para rotación y detección de reuso | N:1 con `usuario` | 50 000 |
| `suspension` | Sanciones manuales y automáticas (regla R6) | N:1 con `usuario` | 500 |

### Perfiles y grafo social — 4 tablas

| Tabla | Propósito | Cardinalidad | Volumen |
|---|---|---|---|
| `perfil` | Datos públicos del desarrollador, enlaces externos (`url_github`, `url_linkedin`, añadidos en V3) y saldo de reputación | 1:1 con `usuario` | 5 000 |
| `tecnologia` | Catálogo curado de lenguajes, frameworks, bases, herramientas y nube | N:M con `perfil` y `publicacion` | 300 |
| `perfil_tecnologia` | **Única fuente de verdad del stack declarado**, con nivel y años. Sustituyó a `perfil_habilidad` en V4 | Puente | 25 000 |
| `seguimiento` | Grafo social reflexivo no simétrico | Puente reflexiva | 40 000 |

### Contenido — 7 tablas

| Tabla | Propósito | Cardinalidad | Volumen |
|---|---|---|---|
| `publicacion` | **Supertipo** de proyecto y discusión: autor, estado, texto, contadores, vector de búsqueda | 1:0..1 con cada subtipo | 40 000 |
| `publicacion_proyecto` | Subtipo proyecto: resumen, licencia, cupo de colaboradores | 1:1 con `publicacion` | 15 000 |
| `publicacion_discusion` | Subtipo discusión: categoría y respuesta aceptada | 1:1 con `publicacion` | 25 000 |
| `repositorio` | Repositorios enlazados a un proyecto | N:1 con `publicacion_proyecto` | 18 000 |
| `publicacion_tecnologia` | Etiquetas de tecnología, entre 1 y 5 por publicación | Puente | 100 000 |
| `historial_estado_publicacion` | Registro de toda transición del proceso principal (regla R7) | N:1 con `publicacion` | 80 000 |
| `colaboracion` | Ciclo de vida de las solicitudes de colaboración (reglas C1–C4) | N:M proyecto ↔ usuario | 20 000 |

### Interacción — 4 tablas

| Tabla | Propósito | Cardinalidad | Volumen |
|---|---|---|---|
| `comentario` | Comentarios anidados, hasta 5 niveles | Auto-referenciada | 250 000 |
| `reaccion_publicacion` | Una reacción por usuario y publicación | Puente | **600 000** |
| `reaccion_comentario` | Una reacción por usuario y comentario | Puente | 200 000 |
| `evento_reputacion` | Libro mayor append-only de la reputación | N:1 con `usuario` | 900 000 |

### Mensajería — 3 tablas

| Tabla | Propósito | Cardinalidad | Volumen |
|---|---|---|---|
| `conversacion` | Hilo privado; `clave_directa` deduplica las conversaciones uno a uno | 1:N con `mensaje` | 15 000 |
| `participante_conversacion` | Miembros y su marca de último leído | Puente | 30 000 |
| `mensaje` | Mensajes con borrado lógico | N:1 con `conversacion` | 400 000 |

### Moderación, auditoría y analítica — 3 tablas

| Tabla | Propósito | Cardinalidad | Volumen |
|---|---|---|---|
| `reporte_moderacion` | Reportes y apelaciones; apunta a una publicación **o** a un comentario | N:1 con el objetivo | 8 000 |
| `auditoria` | Eventos de seguridad y cambios en tablas críticas, con `jsonb` antes/después, `trace_id` y, desde V10, `usuario_bd`. Dos escritores: la aplicación (lo que pasa por la API) y los triggers de V10 (lo que no) | Transversal, sin FK | 500 000 |
| `metricas_diarias` | Tabla de hechos preagregada, una fila por día de `America/Bogota`. La llena `sp_consolidar_metricas_diarias` (V11) | Independiente | 365 |

---

## Lógica en la base de datos (sprint 3)

| Objeto | Tipo | Sobre | Qué hace | Migración |
|---|---|---|---|---|
| `fn_set_actualizado_en` | Función de trigger | `usuario`, `perfil`, `comentario` | Mantiene `actualizado_en` | V1 |
| `fn_publicacion_actualizado` | Función de trigger | `publicacion` | Igual, pero un cambio que solo toca contadores no cuenta como edición | V9 |
| `fn_comentario_contador` | Función de trigger | `comentario` | Mantiene `publicacion.contador_comentarios` (solo `PUBLICADO`) | V9 |
| `fn_subtipo_coincide_con_tipo` | Función de trigger | `publicacion_proyecto`, `publicacion_discusion` | Rechaza un subtipo que no coincide con `publicacion.tipo` | V9 |
| `fn_publicacion_tipo_inmutable` | Función de trigger | `publicacion` | Rechaza cambiar `tipo` cuando ya hay subtipo | V9 |
| `fn_auditar_cambio` | Función de trigger | `usuario_rol`, `rol_permiso`, `suspension`, `usuario` | Escribe en `auditoria`, con lista blanca de columnas | V10 |
| `sp_consolidar_metricas_diarias(p_desde, p_hasta)` | Procedimiento (`CALL`) | `metricas_diarias` | Agrega la actividad de un rango de días; idempotente | V11 |

Restricción nueva del sprint 3: `fk_comentario_padre_misma_publicacion`, con su
`UNIQUE (id, publicacion_id)` de apoyo (`ux_comentario_id_publicacion`).

---

## Datos de referencia

`V1__baseline.sql` inserta datos que **no son de prueba**: el sistema no arranca sin ellos.

| Tabla | Filas | Contenido |
|---|---|---|
| `rol` | 4 | `VISITANTE`, `DESARROLLADOR`, `MODERADOR` y `ADMIN`; los dos últimos con `exige_mfa = true` |
| `permiso` | 22 | Códigos con formato `recurso:accion` (20 en V1; V6 y V7 añadieron `comentario:eliminar-ajeno` y `usuario:seguir`) |
| `rol_permiso` | 43 | Asignación de capacidades (40 en V1; +1 en V6, +2 en V7) |
| `tecnologia` | 20 | Catálogo semilla aprobado |

---

## Clasificación de datos

§6.1 exige clasificar los datos en la fase de definición. Esta tabla determina qué se
cifra, qué se puede registrar en logs y qué se puede exponer en la API.

| Clasificación | Dónde vive | Tratamiento |
|---|---|---|
| **Secreto** | `usuario.clave_hash`, `usuario.mfa_secreto`, `token_refresco.token_hash` | Nunca se devuelve por la API. Nunca se escribe en logs. La contraseña se deriva con Argon2id; del token solo se guarda el hash; el secreto TOTP se cifra a nivel de columna |
| **Personal** | `usuario.correo`, `usuario.ultimo_acceso_en`, `auditoria.ip`, `token_refresco.ip_origen`, `mensaje.contenido` | Acceso restringido al titular y a administradores. No se incluye en reportes agregados. Los mensajes solo son legibles por participantes de la conversación |
| **Interno** | `auditoria`, `reporte_moderacion`, `suspension`, `historial_estado_publicacion` | Visible para moderación y administración, nunca públicamente. Los triggers de V10 copian a `auditoria` solo columnas en lista blanca: de `usuario`, únicamente `id`, `estado` y `mfa_habilitado` |
| **Público** | `perfil`, `publicacion` en estado `PUBLICADO`, `comentario`, `tecnologia`, `seguimiento` | Expuesto por la API sin autenticación para operaciones de lectura |

La regla operativa derivada, que además es exigencia de §6.2: **ningún campo secreto o
personal aparece en un log**. La prueba que lo verifica está descrita en
[ADR-005](../adr/ADR-005-contrato-errores-traceid.md).