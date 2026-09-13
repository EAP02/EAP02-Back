# Diccionario de datos

## Cómo está mantenido este diccionario

El detalle por columna **no se transcribe aquí**. Vive como `COMMENT ON` dentro de
[`V1__baseline.sql`](../../backend/src/main/resources/db/migration/V1__baseline.sql) y se
extrae del catálogo de PostgreSQL cuando se necesita.

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

---

## Inventario de tablas

**29 tablas.** La columna "Volumen" proyecta a un año según la estimación de
[01-consultas-clave.md](01-consultas-clave.md).

### Identidad y acceso — 8 tablas

| Tabla | Propósito | Cardinalidad | Volumen |
|---|---|---|---|
| `usuario` | Credenciales, estado de la cuenta, MFA y bloqueo por intentos | 1:1 con `perfil` | 5 000 |
| `rol` | Catálogo de los cuatro roles, con la marca `exige_mfa` | N:M con `permiso` | 4 |
| `permiso` | Catálogo de capacidades evaluadas por `@PreAuthorize` | N:M con `rol` | 20 |
| `rol_permiso` | Tabla puente rol ↔ permiso | Puente | 40 |
| `usuario_rol` | Asignación de roles, con actor y fecha | Puente | 6 000 |
| `identidad_externa` | Vínculo con GitHub. Su existencia marca el perfil como verificado | N:1 con `usuario` | 4 000 |
| `token_refresco` | Sesiones activas, con familia para rotación y detección de reuso | N:1 con `usuario` | 50 000 |
| `suspension` | Sanciones manuales y automáticas (regla R6) | N:1 con `usuario` | 500 |

### Perfiles y grafo social — 4 tablas

| Tabla | Propósito | Cardinalidad | Volumen |
|---|---|---|---|
| `perfil` | Datos públicos del desarrollador y saldo de reputación | 1:1 con `usuario` | 5 000 |
| `tecnologia` | Catálogo curado de lenguajes, frameworks, bases, herramientas y nube | N:M con `perfil` y `publicacion` | 300 |
| `perfil_tecnologia` | Tecnologías declaradas, con nivel y años | Puente | 25 000 |
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
| `auditoria` | Cambios en tablas críticas, con `jsonb` antes/después y `trace_id` | Transversal, sin FK | 500 000 |
| `metricas_diarias` | Tabla de hechos preagregada, una fila por día | Independiente | 365 |

---

## Datos de referencia

`V1__baseline.sql` inserta datos que **no son de prueba**: el sistema no arranca sin ellos.

| Tabla | Filas | Contenido |
|---|---|---|
| `rol` | 4 | `VISITANTE`, `DESARROLLADOR`, `MODERADOR` y `ADMIN`; los dos últimos con `exige_mfa = true` |
| `permiso` | 20 | Códigos con formato `recurso:accion` |
| `rol_permiso` | 40 | Asignación inicial de capacidades |
| `tecnologia` | 20 | Catálogo semilla aprobado |

---

## Clasificación de datos

§6.1 exige clasificar los datos en la fase de definición. Esta tabla determina qué se
cifra, qué se puede registrar en logs y qué se puede exponer en la API.

| Clasificación | Dónde vive | Tratamiento |
|---|---|---|
| **Secreto** | `usuario.clave_hash`, `usuario.mfa_secreto`, `token_refresco.token_hash` | Nunca se devuelve por la API. Nunca se escribe en logs. La contraseña se deriva con Argon2id; del token solo se guarda el hash; el secreto TOTP se cifra a nivel de columna |
| **Personal** | `usuario.correo`, `usuario.ultimo_acceso_en`, `auditoria.ip`, `token_refresco.ip_origen`, `mensaje.contenido` | Acceso restringido al titular y a administradores. No se incluye en reportes agregados. Los mensajes solo son legibles por participantes de la conversación |
| **Interno** | `auditoria`, `reporte_moderacion`, `suspension`, `historial_estado_publicacion` | Visible para moderación y administración, nunca públicamente |
| **Público** | `perfil`, `publicacion` en estado `PUBLICADO`, `comentario`, `tecnologia`, `seguimiento` | Expuesto por la API sin autenticación para operaciones de lectura |

La regla operativa derivada, que además es exigencia de §6.2: **ningún campo secreto o
personal aparece en un log**. La prueba que lo verifica está descrita en
[ADR-005](../adr/ADR-005-contrato-errores-traceid.md).