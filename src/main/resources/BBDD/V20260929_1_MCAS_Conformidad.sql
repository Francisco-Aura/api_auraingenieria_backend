-- ============================================================
-- MCAS - Conformidad con lo pedido por el cliente (29/09/2026)
-- V20260929_1_MCAS_Conformidad.sql
--
-- CÓMO EJECUTARLO
--   1. Ejecutar este script completo contra la BBDD de Aura (MariaDB),
--      con V20260913_1 y V20260913_2 ya ejecutados.
--   2. Después, ejecutar V20260929_2_Seed_MCAS_Reglas_Hoja2.sql.
--   Es idempotente: se puede relanzar sin riesgo (IF NOT EXISTS en todo),
--   también si un intento anterior se quedó a medias.
--
-- IMPORTANTE: ejecutarlo ANTES de desplegar el backend de esta versión. La
-- entidad McasPoliticaRetrocesion ya mapea las columnas codigo y descripcion;
-- sin ellas, el listado de Comisiones MCAS y la pestaña Retrocesiones de
-- Configuración MCAS devuelven error 500.
--
-- Qué cambia en el esquema (lo demás de esta versión es solo código):
--   - Políticas de retrocesión con clave estable (codigo) y un texto para el
--     operador (descripcion), para sembrarlas y gestionarlas desde la pantalla.
--   - Los tramos admiten tipo_valor = 'SIN_CONFIRMAR' (valor del Excel con la
--     unidad pendiente de confirmar por el cliente). La columna ya es
--     VARCHAR(20): solo se actualiza su comentario.
--
-- Nomenclatura: tablas en minúsculas y snake_case, como las crea Hibernate.
-- ============================================================

-- -----------------------------------------------
-- 1. Políticas de retrocesión: código y descripción
-- -----------------------------------------------
ALTER TABLE mcas_politicas_retrocesion
    ADD COLUMN IF NOT EXISTS codigo      VARCHAR(100) NULL COMMENT 'Clave estable (semillas y administración)' AFTER id,
    ADD COLUMN IF NOT EXISTS descripcion VARCHAR(500) NULL COMMENT 'Texto para el operador';

-- Único: varias políticas sin código (NULL) siguen siendo válidas.
CREATE UNIQUE INDEX IF NOT EXISTS uq_mpr_codigo ON mcas_politicas_retrocesion(codigo);

-- -----------------------------------------------
-- 2. Tramos: tipo de valor SIN_CONFIRMAR (mismo tipo de columna)
-- -----------------------------------------------
ALTER TABLE mcas_tramos_retrocesion
    MODIFY COLUMN tipo_valor VARCHAR(20) NOT NULL COMMENT 'PORCENTAJE|IMPORTE_FIJO|FACTOR|SIN_CONFIRMAR';

-- -----------------------------------------------
-- 3. Comprobación: politicas_codigo_descripcion debe ser 2 e indice_codigo 1
-- -----------------------------------------------
SELECT 'politicas_codigo_descripcion' AS comprobacion, COUNT(*) AS total
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'mcas_politicas_retrocesion'
   AND column_name IN ('codigo', 'descripcion')
UNION ALL
SELECT 'indice_codigo', COUNT(DISTINCT index_name)
  FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'mcas_politicas_retrocesion'
   AND index_name = 'uq_mpr_codigo';
