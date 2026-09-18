-- ============================================================================
--  V3 - Enlaces sociales y habilidades declaradas en el perfil
--
--  Lo exige la HU de edicion de perfil: PerfilEntity mapea url_github,
--  url_linkedin y una coleccion de habilidades que V1 no contemplaba.
--
--  Sin esta migracion la aplicacion no arranca, porque corre con
--  ddl-auto=validate y Hibernate detecta las columnas ausentes. Ese fallo
--  ruidoso es exactamente lo que queremos: el esquema y el codigo no pueden
--  divergir en silencio (ADR-003).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Enlaces sociales
--
--    V1 solo tenia url_sitio_web. GitHub y LinkedIn se separan en columnas
--    propias, y no como filas de una tabla de enlaces, porque son dos y estan
--    fijados por el dominio: el perfil de un desarrollador se presenta con su
--    codigo y su trayectoria. Una tabla generica solo aportaria complejidad.
-- ----------------------------------------------------------------------------

ALTER TABLE perfil ADD COLUMN url_github   text;
ALTER TABLE perfil ADD COLUMN url_linkedin text;

-- Se validan como https por la misma razon que en repositorio.url: un enlace
-- http permitiria degradar la conexion de quien lo siga desde la plataforma.
ALTER TABLE perfil ADD CONSTRAINT ck_perfil_url_github
    CHECK (url_github IS NULL OR url_github ~* '^https://');

ALTER TABLE perfil ADD CONSTRAINT ck_perfil_url_linkedin
    CHECK (url_linkedin IS NULL OR url_linkedin ~* '^https://');

COMMENT ON COLUMN perfil.url_github IS
    'Enlace al perfil de GitHub. Distinto de identidad_externa: aquel es la identidad verificada para autenticar, este es solo un enlace que el usuario declara.';


-- ----------------------------------------------------------------------------
-- 2. Habilidades declaradas en texto libre
--
--    ATENCION, DECISION DE MODELADO PENDIENTE.
--
--    Esta tabla convive con perfil_tecnologia, y las dos describen "lo que sabe
--    un desarrollador" con enfoques opuestos:
--
--      perfil_tecnologia   FK al catalogo curado de tecnologia, con nivel y anios.
--                          Es lo que hace que el reporte de tecnologias mas
--                          discutidas (P14) signifique algo.
--
--      perfil_habilidad    texto libre, sin catalogo ni nivel. Permite declarar
--                          cualquier cosa, y por eso apareceran React, ReactJS y
--                          react.js como tres habilidades distintas.
--
--    Se crea porque el codigo ya la usa y el objetivo inmediato es que la
--    aplicacion arranque con validate. Pero mantener las dos es duplicidad
--    semantica, justo lo que el lineamiento 5.1 pide evitar.
--
--    El equipo debe decidir en el sprint 2 cual sobrevive. La recomendacion es
--    unificar en perfil_tecnologia y permitir proponer tecnologias nuevas con
--    aprobacion, que es el mecanismo que ya existe en el catalogo.
-- ----------------------------------------------------------------------------

CREATE TABLE perfil_habilidad (
    usuario_id uuid NOT NULL REFERENCES perfil(usuario_id) ON DELETE CASCADE,
    habilidad  varchar(80) NOT NULL,

    CONSTRAINT ck_perfil_habilidad_no_vacia CHECK (length(btrim(habilidad)) > 0)
);

COMMENT ON TABLE perfil_habilidad IS
    'Habilidades en texto libre. Duplica semanticamente a perfil_tecnologia; ver la nota de V3 y resolver en el sprint 2.';

-- Es una coleccion de JPA (@ElementCollection): siempre se lee entera por
-- usuario y nunca por habilidad, asi que el indice va por usuario_id.
CREATE INDEX ix_perfil_habilidad_usuario ON perfil_habilidad (usuario_id);

-- Sin clave primaria a proposito: Hibernate mapea List<String> como bag y no
-- garantiza unicidad. Evita que una habilidad repetida rompa la insercion; la
-- deduplicacion se hace en el dominio, donde se puede dar un mensaje util.