-- ============================================================================
--  V9 - Contador de comentarios e integridad del contenido
--
--  Entregable de Bases de Datos del sprint 3 (lineamientos §3.7): "triggers y
--  procedimientos coherentes con las HU". V1 los anunciaba como "V3"; el
--  numero cambio porque V3-V8 se usaron antes, el contenido es el previsto.
--
--  §5.4 admite triggers "para integridad, auditoria o procesamiento cercano a
--  los datos", siempre que no dupliquen reglas sin una fuente de verdad. Cada
--  objeto de este archivo tiene una sola fuente de verdad: la base.
--
--  1. tg_comentario_contador        HU-13. publicacion.contador_comentarios
--                                   existe desde V1 y nadie lo mantenia: HU-13
--                                   escribe comentarios y el contador seguia en 0.
--  2. Recalculo del contador        Corrige los ceros que ya estan en produccion.
--  3. tg_publicacion_actualizado    Contar no es editar: un cambio que solo toca
--                                   contadores deja de mover actualizado_en.
--  4. Integridad del discriminador  publicacion tiene dos escritores (project con
--                                   JPA, discussion con JDBC; ADR-006). El tipo
--                                   del supertipo y la tabla del subtipo deben
--                                   coincidir aunque nadie lo recuerde en codigo.
--  5. FK padre-misma-publicacion    Una respuesta no puede colgar de un
--                                   comentario de otra publicacion (P02).
--
--  contador_reacciones NO se toca aqui: lo mantiene RepositorioReaccionesJdbc
--  (HU-14). Un trigger ademas lo contaria dos veces. Un contador, una fuente.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. Contador de comentarios visibles
-- ----------------------------------------------------------------------------
-- Cuenta solo los comentarios PUBLICADO: es lo que muestra el listado de HU-13.
-- Eliminar es logico (estado ELIMINADO), asi que el caso normal es un UPDATE.

CREATE OR REPLACE FUNCTION fn_comentario_contador()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    antes   integer := 0;
    despues integer := 0;
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') AND OLD.estado = 'PUBLICADO' THEN
        antes := 1;
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') AND NEW.estado = 'PUBLICADO' THEN
        despues := 1;
    END IF;

    -- Un UPDATE que no cambia la visibilidad (p. ej. editar el texto) no hace nada.
    IF despues - antes <> 0 THEN
        -- GREATEST: nunca por debajo de cero, aunque el contador se hubiera
        -- desajustado por una carga manual (ck_publicacion_contadores).
        UPDATE publicacion
           SET contador_comentarios = GREATEST(contador_comentarios + (despues - antes), 0)
         WHERE id = COALESCE(NEW.publicacion_id, OLD.publicacion_id);
    END IF;

    RETURN NULL;   -- AFTER: el valor de retorno se ignora
END;
$$;

COMMENT ON FUNCTION fn_comentario_contador() IS
    'HU-13. Mantiene publicacion.contador_comentarios (solo comentarios PUBLICADO). Desnormalizacion del feed (P01); la base es su unica fuente de verdad.';

CREATE TRIGGER tg_comentario_contador
    AFTER INSERT OR DELETE OR UPDATE OF estado ON comentario
    FOR EACH ROW EXECUTE FUNCTION fn_comentario_contador();


-- ----------------------------------------------------------------------------
-- 2. Recalculo de los contadores existentes
-- ----------------------------------------------------------------------------
-- Antes de V9 nadie actualizaba el contador. Se recalcula una sola vez desde
-- la tabla de comentarios, que es la verdad.

UPDATE publicacion p
   SET contador_comentarios = c.total
  FROM (SELECT publicacion_id, count(*) AS total
          FROM comentario
         WHERE estado = 'PUBLICADO'
         GROUP BY publicacion_id) c
 WHERE p.id = c.publicacion_id
   AND p.contador_comentarios <> c.total;


-- ----------------------------------------------------------------------------
-- 3. Un cambio de contadores no es una edicion
-- ----------------------------------------------------------------------------
-- tg_publicacion_actualizado (V1) movia actualizado_en con cualquier UPDATE,
-- incluidos los de contadores: cada reaccion y cada comentario "actualizaban"
-- la publicacion. Se recrea con una funcion propia que ignora las dos columnas
-- de contadores. Se comparan las filas completas sin ellas, asi una columna
-- nueva en el futuro queda cubierta sin tocar este trigger.
--
-- La comparacion va dentro de la funcion y no en un WHEN: PostgreSQL no admite
-- que el WHEN de un trigger BEFORE lea NEW entero cuando la tabla tiene una
-- columna generada (busqueda_tsv), porque aun no esta calculada. Por lo mismo
-- se excluye busqueda_tsv de la comparacion: deriva de titulo y contenido, que
-- ya se comparan.

CREATE OR REPLACE FUNCTION fn_publicacion_actualizado()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF (to_jsonb(OLD) - 'contador_comentarios' - 'contador_reacciones' - 'busqueda_tsv')
       IS DISTINCT FROM
       (to_jsonb(NEW) - 'contador_comentarios' - 'contador_reacciones' - 'busqueda_tsv') THEN
        NEW.actualizado_en := now();
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION fn_publicacion_actualizado() IS
    'Variante de fn_set_actualizado_en para publicacion (V9): un UPDATE que solo toca contadores no mueve actualizado_en.';

DROP TRIGGER tg_publicacion_actualizado ON publicacion;

CREATE TRIGGER tg_publicacion_actualizado
    BEFORE UPDATE ON publicacion
    FOR EACH ROW EXECUTE FUNCTION fn_publicacion_actualizado();

COMMENT ON TRIGGER tg_publicacion_actualizado ON publicacion IS
    'Mueve actualizado_en solo cuando cambia algo distinto de los contadores (V9): contar no es editar.';


-- ----------------------------------------------------------------------------
-- 4. Integridad del discriminador supertipo / subtipo
-- ----------------------------------------------------------------------------
-- Un publicacion_proyecto solo puede colgar de una publicacion tipo PROYECTO, y
-- un publicacion_discusion de una tipo DISCUSION. Un CHECK no puede mirar otra
-- tabla; un trigger si. Que exista el subtipo lo sigue vigilando la consulta de
-- integridad I2 (docs/bd/05-consultas-no-triviales.sql).

CREATE OR REPLACE FUNCTION fn_subtipo_coincide_con_tipo()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    esperado text := TG_ARGV[0];
    real     text;
BEGIN
    SELECT tipo INTO real FROM publicacion WHERE id = NEW.publicacion_id;

    -- Si la publicacion no existe, la clave foranea dara el error correcto.
    IF real IS NOT NULL AND real <> esperado THEN
        RAISE EXCEPTION 'La publicacion % es de tipo % y no admite un subtipo %',
                        NEW.publicacion_id, real, esperado
              USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION fn_subtipo_coincide_con_tipo() IS
    'Integridad de la especializacion publicacion -> proyecto/discusion: el subtipo debe coincidir con publicacion.tipo.';

CREATE TRIGGER tg_proyecto_tipo_coherente
    BEFORE INSERT OR UPDATE OF publicacion_id ON publicacion_proyecto
    FOR EACH ROW EXECUTE FUNCTION fn_subtipo_coincide_con_tipo('PROYECTO');

CREATE TRIGGER tg_discusion_tipo_coherente
    BEFORE INSERT OR UPDATE OF publicacion_id ON publicacion_discusion
    FOR EACH ROW EXECUTE FUNCTION fn_subtipo_coincide_con_tipo('DISCUSION');

-- El otro lado: una publicacion con subtipo no puede cambiar de tipo.
CREATE OR REPLACE FUNCTION fn_publicacion_tipo_inmutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM publicacion_proyecto  WHERE publicacion_id = NEW.id)
    OR EXISTS (SELECT 1 FROM publicacion_discusion WHERE publicacion_id = NEW.id) THEN
        RAISE EXCEPTION 'La publicacion % ya tiene subtipo %; su tipo no puede cambiar',
                        NEW.id, OLD.tipo
              USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION fn_publicacion_tipo_inmutable() IS
    'Complementa fn_subtipo_coincide_con_tipo: una vez creado el subtipo, publicacion.tipo queda fijo.';

CREATE TRIGGER tg_publicacion_tipo_inmutable
    BEFORE UPDATE OF tipo ON publicacion
    FOR EACH ROW
    WHEN (OLD.tipo IS DISTINCT FROM NEW.tipo)
    EXECUTE FUNCTION fn_publicacion_tipo_inmutable();


-- ----------------------------------------------------------------------------
-- 5. Una respuesta cuelga de un comentario de la misma publicacion
-- ----------------------------------------------------------------------------
-- Propuesta de 02-modelo-logico.md ("Lo que el uso enseño", punto 2). La
-- consulta del hilo (P02) filtra el paso recursivo por publicacion y solo es
-- correcta si esto se cumple. Hoy no hay respuestas anidadas (HU-13 crea solo
-- comentarios raiz), asi que ninguna fila existente la incumple.
--
-- MATCH SIMPLE (el defecto): con comentario_padre_id NULL la FK no se evalua,
-- que es exactamente un comentario raiz.

ALTER TABLE comentario
    ADD CONSTRAINT ux_comentario_id_publicacion UNIQUE (id, publicacion_id);

ALTER TABLE comentario
    ADD CONSTRAINT fk_comentario_padre_misma_publicacion
    FOREIGN KEY (comentario_padre_id, publicacion_id)
    REFERENCES comentario (id, publicacion_id) ON DELETE CASCADE;

COMMENT ON CONSTRAINT fk_comentario_padre_misma_publicacion ON comentario IS
    'Una respuesta solo puede colgar de un comentario de su misma publicacion. Sostiene el filtro del CTE recursivo de P02.';
