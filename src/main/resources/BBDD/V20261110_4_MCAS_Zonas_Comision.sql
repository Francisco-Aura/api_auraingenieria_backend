-- ============================================================
-- MCAS - Zonas de comisión por provincia (10/11/2026)
-- V20261110_4_MCAS_Zonas_Comision.sql
--
-- CÓMO EJECUTARLO
--   1. Ejecutar este script completo contra la BBDD de Aura (MariaDB),
--      con los scripts MCAS anteriores ya ejecutados (el último,
--      V20261105_1_MCAS_Cobros_Y_Ajustes.sql).
--   Es idempotente: se puede relanzar sin riesgo.
--
-- IMPORTANTE: ejecutarlo ANTES de desplegar el backend de esta versión. La
-- entidad McasZonaComision ya mapea esta tabla.
--
-- Qué cambia (reunión del 04/11/2026 y respuesta del cliente a la duda 5):
--   - La zona de comisión de un contrato ya no se escribe: sale de la provincia
--     de su punto de suministro. Cada comercializadora que use zonas tiene aquí
--     las suyas (en Iberdrola, las provincias de la ZAR y la ZBR como zona del
--     resto). Se rellenan desde Configuración MCAS, pestaña Zonas: el script no
--     inserta ninguna.
--   - El «Ámbito» del contrato y de las reglas desaparece: era lo mismo que la
--     zona. Se quita de las condiciones de las reglas que lo tuvieran (en una
--     instalación nueva no hay ninguna).
--
-- Nomenclatura: tablas en minúsculas y snake_case, como las crea Hibernate.
-- ============================================================

-- -----------------------------------------------
-- 1. Zonas de comisión de cada comercializadora
-- -----------------------------------------------
-- Una fila por zona. «provincias» son los códigos de sus provincias (las dos
-- primeras cifras del código postal) separados por comas: 03,30,46. La zona
-- del resto (es_resto = 1) no lleva provincias: recoge todas las que no estén
-- en otra zona de la comercializadora.
CREATE TABLE IF NOT EXISTS mcas_zonas_comision (
    id                  INT          NOT NULL AUTO_INCREMENT,
    id_comercializadora INT          NOT NULL,
    zona                VARCHAR(20)  NOT NULL COMMENT 'El código que usan las reglas y las tablas de importes: ZAR, ZBR...',
    provincias          VARCHAR(200) NULL COMMENT 'Códigos de provincia separados por comas; vacío en la zona del resto',
    es_resto            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1: la zona de las provincias que no están en otra',
    actualizado_por     VARCHAR(100) NULL,
    actualizado_en      DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mzc_com_zona (id_comercializadora, zona),
    CONSTRAINT fk_mzc_com FOREIGN KEY (id_comercializadora) REFERENCES tbcomercializadoras(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------
-- 2. Reglas: fuera la condición «ámbito»
-- -----------------------------------------------
-- El motor ya no la mira. Se quita de las reglas que la tuvieran para que la
-- comprobación de la tabla no las dé por mal escritas.
UPDATE mcas_reglas_comision
   SET condiciones_json = JSON_REMOVE(condiciones_json, '$.ambito')
 WHERE condiciones_json IS NOT NULL
   AND JSON_VALID(condiciones_json)
   AND JSON_CONTAINS_PATH(condiciones_json, 'one', '$.ambito');
