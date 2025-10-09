-- ====================================================================
-- SCRIPT DE MIGRACIÓN: Sincronización de Exención Impuesto Eléctrico y Autoconsumo
-- Entre tablas: contratos <-> punto_suministro
-- Fecha: 2025-01-20
-- Descripción: Migración simple para homogeneizar datos entre contratos y puntos de suministro
-- ====================================================================

-- PASO 1: Migrar desde contratos hacia punto_suministro
-- Si algún contrato de un punto de suministro tiene exención/autoconsumo = 1, 
-- actualizar el punto de suministro con esos valores

-- Actualizar exención de impuesto eléctrico en punto_suministro
UPDATE punto_suministro 
SET exencion_impuesto_electrico = 1
WHERE id IN (
    SELECT DISTINCT id_punto_suministro
    FROM contratos 
    WHERE exencion_impuesto_electricidad = 1
);

-- Actualizar autoconsumo en punto_suministro
-- Si algún contrato tiene autoconsumo = 1, marcar el punto como 'Autoconsumo sin excedentes'
UPDATE punto_suministro 
SET autoconsumo = 'Autoconsumo sin excedentes'
WHERE id IN (
    SELECT DISTINCT id_punto_suministro
    FROM contratos 
    WHERE autoconsumo = 1
);

-- PASO 2: Sincronizar desde punto_suministro hacia contratos
-- Actualizar todos los contratos con los valores actuales del punto de suministro

-- Sincronizar exención de impuesto eléctrico desde punto_suministro hacia contratos
UPDATE contratos c
INNER JOIN punto_suministro ps ON c.id_punto_suministro = ps.id
SET c.exencion_impuesto_electricidad = ps.exencion_impuesto_electrico;

-- Sincronizar autoconsumo desde punto_suministro hacia contratos
-- Convertir valores de texto a BIT(1): cualquier valor de autoconsumo = 1, null/vacío = 0
UPDATE contratos c
INNER JOIN punto_suministro ps ON c.id_punto_suministro = ps.id
SET c.autoconsumo = CASE 
    WHEN ps.autoconsumo IS NOT NULL AND ps.autoconsumo != '' THEN 1
    ELSE 0
END;

-- ====================================================================
-- NOTAS IMPORTANTES:
-- ====================================================================
-- 1. Ejecutar este script ANTES de la nueva versión de la aplicación
-- 2. Hacer backup de las tablas antes de ejecutar
-- 3. Campo exencion: BIT(1) en ambas tablas (1/0)
-- 4. Campo autoconsumo: 
--    - punto_suministro: TEXT (valores como 'Autoconsumo sin excedentes', 'Compensación de excedentes simplificada', 'Compensación de excedentes colectiva')
--    - contratos: BIT(1) (1/0)
-- 5. Conversión: cualquier valor de texto no vacío en punto_suministro = 1 en contratos
-- 6. Valor por defecto para migración: 'Autoconsumo sin excedentes'
-- ====================================================================
