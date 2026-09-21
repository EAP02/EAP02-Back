# ADR-003: PostgreSQL administrado en Supabase, con el esquema versionado en Flyway como única fuente de verdad

- **Estado:** Aceptada
- **Fecha:** 2026-09-13
- **Responsable:** _[completar nombre]_ — Bases de Datos
- **Historias relacionadas:** transversal a todo el backlog

## Contexto

§1.3 fija PostgreSQL como base de datos del proyecto avanzado, con "Supabase o Neon
como opción de servicio administrado". §5.3 exige "respaldo, restauración, migraciones
versionadas y pruebas de recuperación", y §7.3 obliga a "separar desarrollo, pruebas y
producción; no reutilizar datos sensibles ni credenciales entre entornos".

El equipo ya tiene experiencia operando Supabase en un proyecto anterior, lo cual no es
un argumento técnico pero sí una restricción de tiempo real: tres sprints no dan para
aprender una plataforma nueva de datos.

Hay además un requisito de almacenamiento que el modelo por capas de §4.1 nombra como
"repositorio de recursos": avatares de perfil e imágenes de proyecto, con la instrucción
expresa de "evitar incluir binarios innecesarios en el código".

## Alternativas consideradas

### A. PostgreSQL en contenedor dentro de Render

Un servicio de base de datos junto al backend en el mismo `docker-compose`. Máximo
control y cero dependencia externa.

Se descarta: el almacenamiento de Render en el plan gratuito no es duradero entre
despliegues, no hay respaldo automático, y el equipo tendría que construir respaldo,
restauración y monitoreo de la base a mano. Eso es exactamente el trabajo que un
servicio administrado elimina, y §5.3 lo exige como resultado, no como ejercicio.

### B. Neon

PostgreSQL puro, administrado, con *branching* de base de datos: cada rama de Git puede
tener su propia copia del esquema y de los datos. Es la opción más elegante para
resolver la separación de entornos de §7.3.

Contras: no ofrece almacenamiento de objetos, así que habría que incorporar un segundo
proveedor (Cloudinary, S3) para las imágenes. Dos proveedores significan dos juegos de
credenciales, dos consolas y dos puntos de falla operativa para un equipo de tres.

### C. Supabase

PostgreSQL administrado más almacenamiento de objetos en el mismo proveedor y en el
mismo plan gratuito.

## Decisión

Se adopta la **alternativa C**, con tres restricciones de uso que forman parte de la
decisión:

1. **De Supabase se usan exactamente dos cosas:** la conexión PostgreSQL estándar y el
   almacenamiento de objetos. Nada más.
2. **No se usa Supabase Auth.** Ver [ADR-004](ADR-004-identidad-propia-github-oidc.md).
3. **No se usa Row Level Security como mecanismo de autorización.** La autorización se
   decide en el backend, donde vive el RBAC y el ABAC y donde se puede auditar (§6.2
   exige "centralizar la decisión de autorización y verificarla en el servidor"). Poner
   parte de la autorización en políticas RLS y parte en Spring Security crearía dos
   fuentes de verdad sobre quién puede ver qué, que es justo lo que §5.4 advierte evitar.

Con esas restricciones, Supabase es intercambiable por cualquier PostgreSQL 15+ y la
dependencia de proveedor queda contenida.

### Esquema: Flyway como única fuente de verdad

- Las migraciones viven en `src/main/resources/db/migration/` con la convención
  `V<n>__<descripcion>.sql`. A la fecha son cinco, de `V1` a `V5`; qué introduce cada una
  y por qué está en la tabla de [`docs/bd/README.md`](../bd/README.md).
- **`spring.jpa.hibernate.ddl-auto=validate`** en todos los entornos, siempre. Hibernate
  nunca crea ni modifica el esquema; solo verifica que las entidades coincidan con lo que
  Flyway construyó. Si no coinciden, la aplicación no arranca.
- No se mantiene un script de documentación aparte. **El modelo físico es la carpeta de
  migraciones.** Un `.sql` de documentación paralelo se desincroniza en el segundo
  sprint y deja al equipo defendiendo un diagrama que no corresponde al esquema real.
- Las migraciones son **acumulativas y no se editan una vez aplicadas** en un entorno
  compartido. Un error en `V3` se corrige con `V4`.

### Entornos

| Entorno | Base de datos | Propósito |
|---|---|---|
| Local | PostgreSQL 16 en `docker-compose` | Desarrollo diario, sin datos reales |
| Pruebas | Testcontainers, efímero por ejecución | Pruebas de integración en el pipeline |
| Integración | Proyecto Supabase `devnet-test` | Despliegue de la rama `main`, datos sintéticos |
| Producción | Proyecto Supabase `devnet-prod` | Sustentación y demo |

Son **dos proyectos Supabase distintos**, no dos esquemas del mismo, para cumplir la
prohibición de reutilizar credenciales entre entornos. El plan gratuito permite dos
proyectos activos.

Las credenciales no se versionan: variables de entorno en Render y GitHub Secrets en el
pipeline; `.env` local fuera del control de versiones y `.env.example` dentro.

### Cuentas de base de datos con mínimo privilegio

§5.3 pide "cuentas de servicio con mínimo privilegio, separar funciones
administrativas". Se crean tres roles:

| Rol | Privilegios | Usado por |
|---|---|---|
| `devnet_migrador` | `CREATE`, `ALTER`, `DROP` sobre el esquema | Solo Flyway al arrancar |
| `devnet_app` | `SELECT`, `INSERT`, `UPDATE`, `DELETE` sobre tablas; `EXECUTE` sobre funciones | La aplicación en ejecución |
| `devnet_lectura` | `SELECT` | Consultas de análisis y sustentación |

La aplicación **no** se conecta con el rol que puede modificar el esquema.

## Consecuencias

### Positivas

- Respaldo automático, restauración puntual y TLS vienen dados por el proveedor.
- Un solo proveedor para datos y archivos: una consola, un juego de credenciales.
- `ddl-auto=validate` convierte cualquier divergencia entre el modelo lógico y el código
  en un fallo de arranque, no en un error silencioso en producción.
- Testcontainers levanta PostgreSQL real y aplica las mismas migraciones, así que las
  pruebas de integración corren contra el esquema verdadero (§3.5 pide "integración con
  base de datos real o contenedor").

### Negativas y cómo se mitigan

| Riesgo | Mitigación |
|---|---|
| **El plan gratuito de Supabase pausa el proyecto tras ~7 días de inactividad** | Consulta programada de mantenimiento semanal; verificar el estado 48 h antes de cada sustentación |
| Dependencia de proveedor | Uso restringido a PostgreSQL estándar; el almacenamiento se accede tras una interfaz `AlmacenArchivos` en `shared`, con un adaptador local para desarrollo |
| Límite de conexiones simultáneas en el plan gratuito | Pool HikariCP acotado (`maximum-pool-size: 5`) y uso del *pooler* de Supabase en modo transacción |
| Latencia añadida por estar la base en otra región que Render | Se mide en el sprint 2 con los RNF; si excede el presupuesto, se alinean las regiones |
| Una migración mal hecha rompe el entorno compartido | Toda migración corre primero en local y en Testcontainers dentro del pipeline antes de tocar integración |

## Verificación

- El pipeline ejecuta `flyway:validate` y falla si hay migraciones aplicadas con
  *checksum* alterado.
- Las pruebas de integración arrancan con `ddl-auto=validate`; si una entidad JPA no
  corresponde al esquema, el contexto de Spring no levanta y el build falla.
- Prueba de recuperación documentada una vez por sprint: restaurar un respaldo sobre un
  proyecto desechable y ejecutar la suite de integración contra él.