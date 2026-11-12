-- ============================================================
-- MCAS - Usabilidad para usuario no técnico (30/09/2026)
-- V20260930_1_MCAS_Usabilidad.sql
--
-- CÓMO EJECUTARLO
--   1. Ejecutar este script completo contra la BBDD de Aura (MariaDB),
--      con V20260913_1 y V20260929_1 ya ejecutados.
--   Solo esquema: no inserta ni cambia datos.
--   Es idempotente: se puede relanzar sin riesgo (IF NOT EXISTS en todo),
--   también si un intento anterior se quedó a medias.
--
-- IMPORTANTE: ejecutarlo ANTES de desplegar el backend de esta versión. Las
-- entidades McasConjuntoReglas y McasReglaComision ya mapean estas columnas;
-- sin ellas, Configuración MCAS, Comisiones MCAS y el cálculo de comisiones
-- al guardar un contrato devuelven error 500.
--
-- Qué cambia en el esquema (lo demás de esta versión es solo código):
--   - Tablas de comisiones (mcas_conjuntos_reglas) y reglas
--     (mcas_reglas_comision) con un nombre y una descripción legibles para el
--     administrador. El código sigue siendo la clave; el nombre es opcional.
--   - Tablas de comisiones con el origen de «Nueva versión»
--     (id_conjunto_origen). Sin clave foránea: un borrador copiado se puede
--     eliminar y el origen no queda atado a sus copias.
--
-- Nomenclatura: tablas en minúsculas y snake_case, como las crea Hibernate.
-- ============================================================

-- -----------------------------------------------
-- 1. Tablas de comisiones: nombre, descripción y versión de origen
-- -----------------------------------------------
ALTER TABLE mcas_conjuntos_reglas
    ADD COLUMN IF NOT EXISTS nombre             VARCHAR(150)  NULL COMMENT 'Nombre legible (Endesa 2026-10)' AFTER codigo,
    ADD COLUMN IF NOT EXISTS descripcion        VARCHAR(1000) NULL COMMENT 'Texto para el administrador',
    ADD COLUMN IF NOT EXISTS id_conjunto_origen INT           NULL COMMENT 'Conjunto copiado con Nueva versión';

-- -----------------------------------------------
-- 2. Reglas: nombre y descripción
-- -----------------------------------------------
ALTER TABLE mcas_reglas_comision
    ADD COLUMN IF NOT EXISTS nombre      VARCHAR(150)  NULL COMMENT 'Nombre legible (Iris · pyme luz · 44 % del FEE)' AFTER codigo,
    ADD COLUMN IF NOT EXISTS descripcion VARCHAR(1000) NULL COMMENT 'Texto para el administrador';

-- -----------------------------------------------
-- 3. Comprobación: conjuntos debe ser 3 y reglas 2
-- -----------------------------------------------
SELECT 'conjuntos_nombre_descripcion_origen' AS comprobacion, COUNT(*) AS total
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'mcas_conjuntos_reglas'
   AND column_name IN ('nombre', 'descripcion', 'id_conjunto_origen')
UNION ALL
SELECT 'reglas_nombre_descripcion', COUNT(*)
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'mcas_reglas_comision'
   AND column_name IN ('nombre', 'descripcion');
