# Requisitos no funcionales

Los lineamientos (§4.3) fijan una línea base académica y piden explícitamente que "el
equipo debe definir objetivos más exigentes por operación y validarlos con pruebas
representativas". Esta tabla hace eso: toma la línea base como piso y define presupuestos
por operación.

Cada RNF es verificable. Un requisito no funcional que no se puede medir es una aspiración.

---

## Línea base y objetivos por operación

| ID | Atributo | Línea base (§4.3) | Objetivo de DevNet | Cómo se mide |
|---|---|---|---|---|
| RNF-01 | Rendimiento — carga | 200 solicitudes/min | 300 solicitudes/min sostenidas sin degradación | k6 o JMeter contra el entorno de integración, sprint 3 |
| RNF-02 | Rendimiento — latencia | ≤ 30 s | Ver presupuestos por operación, abajo | Percentil 95 en Micrometer, tablero de Grafana |
| RNF-03 | Disponibilidad | Definir objetivo | 99,0 % mensual en horario académico | Sonda externa cada 5 min contra `/actuator/health` |
| RNF-04 | Escalabilidad | Componentes sin estado | API sin estado de sesión; todo el estado en PostgreSQL | Dos instancias en paralelo responden idéntico |
| RNF-05 | Mantenibilidad | Modularidad y pruebas | Cobertura ≥ 65 %, complejidad ciclomática < 50, deuda ≤ 2 días | Quality Gate de SonarCloud, bloqueante |
| RNF-06 | Interoperabilidad | Contratos versionados | OpenAPI 3.1 publicado por release, política de retiro de un sprint | Comparación de esquema en el pipeline |
| RNF-07 | Trazabilidad | Correlacionar todo | `traceId` presente en el 100 % de respuestas, logs y filas de auditoría | Prueba de integración sobre toda respuesta |
| RNF-08 | Seguridad | §3.4 completo | Cero vulnerabilidades críticas abiertas al liberar | SCA, SAST y detección de secretos, bloqueantes |
| RNF-09 | Usabilidad y accesibilidad | WCAG 2.2 AA | **No aplica**: el perfil avanzado no contempla frontend (§1.3). Se traslada a la claridad del contrato OpenAPI | Todo endpoint con descripción, ejemplo y errores declarados |

### Presupuestos de latencia por operación (RNF-02)

El techo de 30 s de la línea base es un piso muy holgado que no distingue una consulta
trivial de un reporte agregado. Estos son los objetivos reales que el equipo se impone,
medidos como percentil 95 sin contar el arranque en frío:

| Operación | Objetivo p95 | Razón |
|---|---|---|
| Autenticación (emisión de token) | ≤ 800 ms | Argon2id domina el tiempo; es costo deliberado |
| Lectura de perfil o publicación por id | ≤ 200 ms | Acceso por clave primaria con índice |
| Feed personalizado, página de 20 | ≤ 600 ms | Join de seguimiento y tecnologías, paginación por keyset |
| Hilo de comentarios anidados | ≤ 500 ms | CTE recursivo con profundidad acotada a 5 |
| Bandeja de conversaciones | ≤ 400 ms | `DISTINCT ON` sobre índice compuesto |
| Búsqueda por texto completo | ≤ 900 ms | Índice GIN sobre `tsvector` |
| Reporte de crecimiento, 90 días | ≤ 2 s | Lee `metricas_diarias` preconsolidada, no agrega en vivo |
| Escritura (publicar, comentar, enviar mensaje) | ≤ 400 ms | Transacción local, sin llamadas externas |

Si una operación excede su presupuesto, se analiza con `EXPLAIN ANALYZE` antes de añadir
caché. La caché no corrige un plan de ejecución malo, solo lo esconde.

---

## Decisiones sobre las que descansan estos números

**El reporte de crecimiento lee datos preconsolidados, no agrega en vivo.** El
procedimiento almacenado `sp_consolidar_metricas_diarias` escribe en `metricas_diarias`
una vez al día; el endpoint de reportes solo lee esa tabla. Es lo que hace posible un
objetivo de 2 s en un rango de 90 días sobre el plan gratuito de una base administrada.
Ver [04-indices.md](../bd/04-indices.md).

**No hay caché distribuida en el alcance.** Se usa Caffeine en memoria para el catálogo de
tecnologías y para la resolución de permisos por rol, que son datos de lectura intensa y
escritura rara. §5.2 condiciona la incorporación de Redis a que "el caso de uso y la
capacidad operativa lo justifiquen"; con un solo contenedor y 300 solicitudes por minuto,
no lo justifican. Si en el sprint 3 la medición muestra lo contrario, se documenta en un
ADR nuevo y se incorpora.

**La API es sin estado de sesión.** No hay `HttpSession`. El JWT de acceso lleva la
identidad y los permisos; el refresco vive en cookie y su estado está en la base de datos.
Esto es lo que permite correr dos instancias sin sesión pegajosa (RNF-04) aunque el
alcance académico no llegue a necesitarlo.

**El arranque en frío de Render es una amenaza directa al RNF-02.** El plan gratuito
suspende el servicio por inactividad y el arranque supera con holgura los 30 s de la línea
base. Mitigación acordada: ping programado cada 10 minutos a `/actuator/health`, y
promoción al plan Starter durante la semana de sustentación. Está anotado también en el
[diagrama de despliegue](uml-despliegue.puml) para que no se pierda.

---

## Observabilidad que respalda la medición

Instrumentación desde el sprint 1, tableros desde el sprint 3 (§3.7).

| Señal | Instrumento | Dónde se ve |
|---|---|---|
| Latencia por endpoint (p50, p95, p99) | Micrometer `http.server.requests` | Grafana |
| Tasa de error por `errorCode` | Contador propio en el manejador global | Grafana |
| Saturación del pool de conexiones | Métricas de HikariCP | Grafana |
| Consultas lentas | `log_min_duration_statement = 500ms` en PostgreSQL | Logs de Supabase |
| Eventos de seguridad | Tabla `auditoria` + log JSON | Consulta SQL y logs |
| Salud del servicio | `/actuator/health` con sondas de BD y almacenamiento | Sonda externa |

Alertas mínimas al llegar el sprint 3: p95 por encima del presupuesto durante 10 minutos,
tasa de `ERROR_INTERNO` sobre el 1 %, pool de conexiones por encima del 80 %, y servicio
caído en dos sondas consecutivas.

---

## Restricciones que no son negociables

Estas vienen de los lineamientos y se listan aparte porque no admiten objetivo propio: se
cumplen o no se cumplen.

- Cobertura unitaria **≥ 65 %** (§3.5). El pipeline pide 40 % como mínimo general, pero
  §1.2 ordena adoptar el umbral más exigente cuando dos requisitos cuantitativos difieren.
- Complejidad ciclomática **< 50**, revisando además la cognitiva.
- Deuda técnica con tiempo de remediación **≤ 2 días**.
- Severidad **minor o mejor**.
- **Cero** vulnerabilidades críticas abiertas para liberar (§6.3).
- Pruebas de integración **contra base de datos real o en contenedor** (§3.5): se usa
  Testcontainers con PostgreSQL 16 y las mismas migraciones de Flyway.