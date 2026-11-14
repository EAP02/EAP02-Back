-- ============================================================================
--  V12 - Indices para la consulta del registro de auditoria (HU-20)
--
--  §5.3: "crear indices a partir de patrones reales de consulta; evitar indices
--  redundantes y validar su costo de escritura".
--
--  Patron real. GET /api/v1/auditoria lista del mas reciente al mas antiguo,
--  con filtros opcionales por actor, operacion, tabla y rango de fechas:
--
--      ORDER BY creado_en DESC, id DESC  LIMIT n OFFSET m
--
--  Lo que ya existia no sirve para esto: ix_auditoria_registro (tabla,
--  registro_id, creado_en) exige conocer el registro, e ix_auditoria_trace
--  busca por traza. Sin indice, cada pagina ordena toda la tabla, y la tabla
--  crece con cada inicio de sesion (exitoso o fallido).
--
--  1. ix_auditoria_creado  (creado_en DESC, id DESC)
--       Sostiene el orden por defecto y el filtro por rango de fechas. Las
--       dos columnas, en el mismo sentido que el ORDER BY, permiten leer las
--       primeras filas sin ordenar: el desempate por id es lo que hace la
--       paginacion determinista.
--  2. ix_auditoria_actor   (actor_id, creado_en DESC)
--       "Que hizo este usuario". Es la pregunta tipica de una investigacion.
--
--  Que NO se indexa: operacion (solo 11 valores; un indice no discrimina lo
--  bastante y la consulta combinada ya la acota el rango de fechas) ni tabla
--  (misma razon). Costo de escritura: dos indices mas sobre una tabla de solo
--  inserciones; cada fila de auditoria paga dos entradas de b-tree, aceptable
--  frente a que la API la escribe fuera de la transaccion del usuario
--  (REQUIRES_NEW) y no en su camino critico.
--
--  No son indices parciales a proposito: EsquemaIT cuenta los parciales (10 de
--  V1 mas ix_auditoria_seguridad de V2) y estos no cambian ese numero.
-- ============================================================================

CREATE INDEX ix_auditoria_creado ON auditoria (creado_en DESC, id DESC);

CREATE INDEX ix_auditoria_actor  ON auditoria (actor_id, creado_en DESC);
