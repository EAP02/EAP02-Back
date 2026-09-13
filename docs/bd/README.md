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
| [`V1__baseline.sql`](../../backend/src/main/resources/db/migration/V1__baseline.sql) | **Modelo físico** | Sprint 1 (literal) |

## El modelo físico es la carpeta de migraciones

No hay un script de documentación aparte del esquema. La carpeta
`backend/src/main/resources/db/migration/` es la única fuente de verdad, y Hibernate corre
siempre con `ddl-auto=validate`: si una entidad JPA no corresponde a lo que Flyway
construyó, la aplicación no arranca.

Mantener un `.sql` paralelo "de documentación" garantiza que en el segundo sprint el equipo
esté defendiendo un diagrama que no corresponde a la base real.

## Orden de lectura recomendado

Es el orden en que se construyó, y el que hace que cada decisión se entienda:

1. **[Consultas clave](01-consultas-clave.md)** — qué tiene que responder la base.
2. **[Modelo lógico](02-modelo-logico.md)** — cómo se organizan los datos para responderlo.
3. **[`V1__baseline.sql`](../../backend/src/main/resources/db/migration/V1__baseline.sql)** — la materialización.
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

Mientras no exista el `docker-compose.yml` del backend:

```bash
docker run --name devnet-db -e POSTGRES_PASSWORD=devnet \
  -e POSTGRES_DB=devnet -p 5432:5432 -d postgres:16

psql -h localhost -U postgres -d devnet \
  -f backend/src/main/resources/db/migration/V1__baseline.sql
```

Verificar que quedaron las 26 tablas y los 20 índices:

```sql
SELECT count(*) FROM pg_tables  WHERE schemaname = 'public';
SELECT count(*) FROM pg_indexes WHERE schemaname = 'public';
```

El conteo de índices supera 20 porque incluye los creados automáticamente por cada
`PRIMARY KEY` y cada `UNIQUE`. Ver [04-indices.md](04-indices.md), sección "Índices que
existen sin declararse".

> Requiere Docker, que aún no está instalado en la máquina de trabajo. Ver la lista de
> instalación en el [README raíz](../../README.md).