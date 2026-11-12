-- ============================================================
-- MCAS - Condiciones de cobro y porcentaje sobre el resultado (05/11/2026)
-- V20261105_1_MCAS_Cobros_Y_Ajustes.sql
--
-- CÓMO EJECUTARLO
--   1. Ejecutar este script completo contra la BBDD de Aura (MariaDB),
--      con los scripts MCAS anteriores ya ejecutados.
--   Solo esquema: no inserta ni cambia datos.
--   Es idempotente: se puede relanzar sin riesgo (IF NOT EXISTS en todo).
--
-- IMPORTANTE: ejecutarlo ANTES de desplegar el backend de esta versión. Las
-- entidades McasCondicionCobro y McasReglaComision ya mapean estas columnas.
--
-- Qué cambia (respuestas del cliente a las dudas, 05/11/2026):
--   - Condiciones de cobro de cada comercializadora, y si hace falta de cada
--     gestora dentro de ella (duda 8 y 9): en cuántos pagos se cobra la
--     comisión y cuándo llega el primer cobro (meses después de la fecha de
--     inicio del contrato y qué día). Sustituye al «Nº de pagos» del contrato.
--   - Reglas con un porcentaje sobre el resultado (duda 19): p. ej. las
--     renovaciones tácitas que se cobran al 80 % de lo calculado.
--
-- Nomenclatura: tablas en minúsculas y snake_case, como las crea Hibernate.
-- ============================================================

-- -----------------------------------------------
-- 1. Condiciones de cobro por comercializadora (y gestora)
-- -----------------------------------------------
-- Una fila por comercializadora sin gestora (las condiciones generales) y,
-- opcionalmente, una por cada gestora que cobre distinto. La unicidad
-- (comercializadora, gestora) la garantiza id_gestora_clave: MariaDB admite
-- varias filas con NULL en un índice único.
CREATE TABLE IF NOT EXISTS mcas_condiciones_cobro (
    id                       INT          NOT NULL AUTO_INCREMENT,
    id_comercializadora      INT          NOT NULL,
    id_gestora               INT          NULL COMMENT 'Vacío: condiciones generales de la comercializadora',
    id_gestora_clave         INT          AS (COALESCE(id_gestora, 0)) PERSISTENT,
    numero_pagos             INT          NULL COMMENT '1 a 12; vacío: lo que diga la regla o 1 pago',
    meses_hasta_primer_cobro INT          NOT NULL DEFAULT 0 COMMENT '0 a 24 meses después de la fecha de inicio',
    dia_primer_cobro         VARCHAR(20)  NOT NULL DEFAULT 'MISMO_DIA' COMMENT 'MISMO_DIA, ULTIMO_DIA_MES o DIA_1',
    observaciones            VARCHAR(500) NULL,
    actualizado_por          VARCHAR(100) NULL,
    actualizado_en           DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcc_com_ges (id_comercializadora, id_gestora_clave),
    CONSTRAINT fk_mcc_com FOREIGN KEY (id_comercializadora) REFERENCES tbcomercializadoras(id),
    CONSTRAINT fk_mcc_ges FOREIGN KEY (id_gestora)          REFERENCES tbgestoras(id),
    CONSTRAINT ck_mcc_pagos CHECK (numero_pagos IS NULL OR numero_pagos BETWEEN 1 AND 12),
    CONSTRAINT ck_mcc_meses CHECK (meses_hasta_primer_cobro BETWEEN 0 AND 24),
    CONSTRAINT ck_mcc_dia   CHECK (dia_primer_cobro IN ('MISMO_DIA', 'ULTIMO_DIA_MES', 'DIA_1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------
-- 2. Reglas: porcentaje que se cobra de lo calculado
-- -----------------------------------------------
ALTER TABLE mcas_reglas_comision
    ADD COLUMN IF NOT EXISTS porcentaje_resultado DECIMAL(7,4) NULL
        COMMENT 'Vacío o 100: todo lo calculado; 80: el 80 % (p. ej. renovaciones tácitas)';
