-- ============================================================================
--  V4 - Unificar las habilidades del perfil en perfil_tecnologia
--
--  La V3 creo perfil_habilidad (texto libre) porque el codigo ya la usaba, y dejo
--  anotado que convivir con perfil_tecnologia era duplicidad semantica. El equipo
--  decidio unificar en perfil_tecnologia, que es la catalogada.
--
--  Motivo: solo el catalogo permite agregar. Con texto libre, React, ReactJS y
--  react.js son tres tecnologias distintas y el reporte de tecnologias mas
--  discutidas (P14) deja de significar nada. El lineamiento 5.1 pide justamente
--  "evitar duplicidad semantica y garantizar una fuente de verdad coherente".
--
--  Ademas, perfil_tecnologia aporta nivel y anios, que es el dato que hace util
--  buscar colaboradores: no es lo mismo haber tocado Java que llevar cinco anios.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Rescatar lo que se pueda antes de borrar
--
--    Se migran solo las habilidades que coinciden con una tecnologia aprobada del
--    catalogo, comparando sin distinguir mayusculas contra el nombre o el slug.
--    El resto se pierde a proposito: eran texto libre sin equivalencia, y
--    inventarles una entrada de catalogo seria ensuciar justo lo que se quiere
--    limpiar.
--
--    Nivel BASICO por defecto: es el unico honesto cuando el dato no existia.
--    El usuario lo corrige la primera vez que edite su perfil.
-- ----------------------------------------------------------------------------

INSERT INTO perfil_tecnologia (usuario_id, tecnologia_id, nivel, anios)
SELECT DISTINCT ON (ph.usuario_id, t.id)
       ph.usuario_id,
       t.id,
       'BASICO',
       NULL
FROM perfil_habilidad ph
JOIN tecnologia t
  ON t.aprobada = true
 AND (lower(btrim(ph.habilidad)) = lower(t.nombre)
      OR lower(btrim(ph.habilidad)) = lower(t.slug))
-- No pisar lo que el usuario ya haya declarado por la via catalogada.
WHERE NOT EXISTS (
    SELECT 1 FROM perfil_tecnologia pt
    WHERE pt.usuario_id = ph.usuario_id AND pt.tecnologia_id = t.id
);

-- ----------------------------------------------------------------------------
-- 2. Retirar la tabla de texto libre
-- ----------------------------------------------------------------------------

DROP INDEX IF EXISTS ix_perfil_habilidad_usuario;
DROP TABLE IF EXISTS perfil_habilidad;

COMMENT ON TABLE perfil_tecnologia IS
    'Unica fuente de verdad del stack declarado por un desarrollador. Referencia al catalogo curado; sustituyo a perfil_habilidad en V4.';

-- ----------------------------------------------------------------------------
-- 3. Indice para el sentido inverso
--
--    La clave primaria (usuario_id, tecnologia_id) resuelve "que declara este
--    usuario". Este indice resuelve "quien sabe esta tecnologia", que es la
--    consulta detras de buscar colaboradores por stack (P03). No es redundante:
--    es el otro sentido.
-- ----------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS ix_perfil_tecnologia_inverso
    ON perfil_tecnologia (tecnologia_id, usuario_id);