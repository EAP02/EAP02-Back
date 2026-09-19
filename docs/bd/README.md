# Bases de datos — DevNet

Documentación del curso de **Bases de Datos**. Las decisiones que la sostienen están en
[docs/adr/](../adr/), principalmente
[ADR-003](../adr/ADR-003-persistencia-supabase-flyway.md).

## Contenido

| Archivo | Qué es | Entregable |
|---|---|---|
| [01-consultas-clave.md](01-consultas-clave.md) | Preguntas de negocio por actor, correspondencia con el caso, estimación de volumen | Sprint 1 (literal) |
| [02-modelo-logico.md](02-modelo-logico.md) | MER en cinco vistas, decisiones de modelado, análisis de normalización | Sprint 1 (literal) |
| [03-diccionario-datos.md](03-diccionario-datos.md) | Inventario de las 29 tablas, consultas para generar el diccionario, clasificación de datos | Sprint 1–2 |
| [04-indices.md](04-indices.md) | Justificación de cada índice, costo de escritura, procedimiento de verificación | Sprint 2 |
| [05-consultas-no-triviales.sql](05-consultas-no-triviales.sql) | Las nueve consultas clave, ejecutables, más tres verificaciones de integridad | Sprint 2 |
| [db-roles.sql](db-roles.sql) | Roles de base de datos y privilegios mínimos | Sprint 2 |
| [verificacion-instalacion.sql](verificacion-instalacion.sql) | Comprobaciones post-instalación con sus valores esperados | Sprint 2 |
| [`db/migration/`](../../src/main/resources/db/migration/) | **Modelo físico** (V1 a V5) | Sprint 1 (literal) |

## El modelo físico es la carpeta de migraciones

No hay un script de documentación aparte del esquema. La carpeta
`src/main/resources/db/migration/` es la única fuente de verdad, y Hibernate corre
siempre con `ddl-auto=validate`: si una entidad JPA no corresponde a lo que Flyway
construyó, la aplicación no arranca.

Mantener un `.sql` paralelo "de documentación" garantiza que en el segundo sprint el equipo
esté defendiendo un diagrama que no corresponde a la base real.

### Migraciones aplicadas

| Versión | Qué introduce | Por qué |
|---|---|---|
| `V1__baseline.sql` | Las 29 tablas con sus restricciones e índices, y los datos de referencia: roles, permisos y catálogo de tecnologías | Modelo físico inicial. Los datos de referencia no son de prueba: el sistema no arranca sin ellos |
| `V2__eventos_seguridad_en_auditoria.sql` | Amplía `ck_auditoria_operacion` con `ACCESO_DENEGADO`, `INICIO_SESION`, `INICIO_SESION_FALLIDO` y `CUENTA_BLOQUEADA`; añade `ix_auditoria_seguridad` | `auditoria.operacion` solo admitía `INSERT`/`UPDATE`/`DELETE`. Un acceso denegado no es ninguno de los tres: precisamente no cambió nada (HU-06) |
| `V3__perfil_enlaces_y_habilidades.sql` | `perfil.url_github` y `perfil.url_linkedin` con sus CHECK; tabla `perfil_habilidad` | El perfil técnico necesitaba enlaces externos, y el código ya usaba habilidades de texto libre |
| `V4__unificar_habilidades_en_perfil_tecnologia.sql` | Migra lo rescatable de `perfil_habilidad` a `perfil_tecnologia` y **elimina** `perfil_habilidad`; añade `ix_perfil_tecnologia_inverso` | Duplicidad semántica (§5.1). Con texto libre, `React`, `ReactJS` y `react.js` son tres tecnologías distintas y el reporte de tecnologías más discutidas deja de significar nada |
| `V5__eventos_de_refresco_en_auditoria.sql` | Añade `REFRESCO_ROTADO`, `REFRESCO_REUSADO` y `CIERRE_SESION`; recrea `ix_auditoria_seguridad` incluyendo el reuso | Sin esto el `INSERT` violaría el CHECK y el adaptador de auditoría se lo traga con un `log.error`: el evento de seguridad más importante del sistema se perdería en silencio (ADR-004) |

`V4` es la única destructiva. Migra solo las habilidades que coinciden con una tecnología
aprobada del catálogo, comparando sin distinguir mayúsculas contra el nombre o el *slug*;
**el resto se pierde a propósito**, porque era texto libre sin equivalencia e inventarle
una entrada de catálogo ensuciaría justo lo que se quiere limpiar.

## Orden de lectura recomendado

Es el orden en que se construyó, y el que hace que cada decisión se entienda:

1. **[Consultas clave](01-consultas-clave.md)** — qué tiene que responder la base.
2. **[Modelo lógico](02-modelo-logico.md)** — cómo se organizan los datos para responderlo.
3. **[`V1__baseline.sql`](../../src/main/resources/db/migration/V1__baseline.sql)** — la materialización.
4. **[Índices](04-indices.md)** — cómo se responde rápido.
5. **[Consultas no triviales](05-consultas-no-triviales.sql)** — las respuestas escritas.

## Lo que falta por sprint

| Sprint | Pendiente |
|---|---|
| 2 | Ejecutar `db-roles.sql` en ambos proyectos Supabase. Poblar con datos representativos y completar el registro de mediciones de [04-indices.md](04-indices.md) con `EXPLAIN ANALYZE` real |
| 3 | `V3`: triggers de auditoría sobre tablas críticas, triggers de mantenimiento de contadores, y el procedimiento almacenado `sp_consolidar_metricas_diarias`. Actualizar el análisis de volumen |

Los triggers y el procedimiento se dejan para el sprint 3 porque así lo pide el cronograma
de §3.7 ("triggers y procedimientos **coherentes con las HU**"), y porque para entonces las
historias que los justifican ya estarán implementadas. Un procedimiento escrito antes de la
historia que lo necesita es una suposición, no un diseño.

## Cómo levantar la base en local

El repositorio ya trae `docker-compose.yml` con PostgreSQL 16 y el mismo *collation* que
Supabase, para que un plan de ejecución medido en local signifique algo:

```bash
docker compose up -d
```

Las migraciones **no se aplican a mano**: las corre Flyway al arrancar la aplicación.

```bash
./mvnw spring-boot:run
```

Verificar que quedaron las 29 tablas:

```sql
SELECT count(*) FROM pg_tables  WHERE schemaname = 'public';
SELECT count(*) FROM pg_indexes WHERE schemaname = 'public';
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

El conteo de `pg_indexes` supera con creces los 22 declarados porque incluye los que
PostgreSQL crea solo por cada `PRIMARY KEY` y cada `UNIQUE`. Ver
[04-indices.md](04-indices.md), sección "Índices que existen sin declararse".

Para una comprobación completa —tablas, columnas, restricciones e índices parciales
contra sus valores esperados— está
[verificacion-instalacion.sql](verificacion-instalacion.sql). Lo mismo automatizado vive
en `EsquemaIT`.