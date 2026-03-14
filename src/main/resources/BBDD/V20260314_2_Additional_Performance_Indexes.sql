-- ============================================================
-- ÍNDICES ADICIONALES DE RENDIMIENTO - MEJORA DEL LISTADO
-- Fecha: 2026-03-14
-- Base de datos: MariaDB
-- Descripción: Índices adicionales para optimizar el listado
--              de puntos de suministro (findAllPuntosSuministro)
--              Índices de cobertura para mejorar JOINs
-- ============================================================

-- ============================================================
-- TABLA: contratos
-- ============================================================

-- Índice compuesto PRIORITARIO para optimizar listado de puntos
-- Crítico para la query findAllPuntosSuministro
-- Permite evitar accesos a tabla principal mediante cobertura
CREATE INDEX IF NOT EXISTS IX_Contratos_PuntoSuministro_FechaFin
ON contratos(id_punto_suministro, fecha_fin);

-- ============================================================
-- TABLA: tbtarifas_luz
-- ============================================================

-- Índice de cobertura para mejorar JOINs con contratos
-- Incluye 'ID' y 'nombre' para evitar lookups adicionales
CREATE INDEX IF NOT EXISTS IX_TarifasLuz_IdNombre
ON tbtarifas_luz(ID, nombre);

-- ============================================================
-- TABLA: tbtarifas_gas
-- ============================================================

-- Índice de cobertura para mejorar JOINs con contratos
-- Incluye 'ID' y 'nombre' para evitar lookups adicionales
CREATE INDEX IF NOT EXISTS IX_TarifasGas_IdNombre
ON tbtarifas_gas(ID, nombre);

-- ============================================================
-- TABLA: tbcomercializadoras
-- ============================================================

-- Índice de cobertura para mejorar JOINs con contratos
-- Incluye 'ID' y 'nombre' para evitar lookups adicionales
CREATE INDEX IF NOT EXISTS IX_Comercializadoras_IdNombre
ON tbcomercializadoras(ID, nombre);

-- ============================================================
-- ACTUALIZAR ESTADÍSTICAS DE ÍNDICES
-- ============================================================
-- Ejecutar para optimizar el plan de ejecución de queries
-- que usan estos nuevos índices

ANALYZE TABLE contratos;
ANALYZE TABLE tbtarifas_luz;
ANALYZE TABLE tbtarifas_gas;
ANALYZE TABLE tbcomercializadoras;
