-- ============================================================
-- Índices de rendimiento: servicios y contratos
-- Fecha: 2026-10-06
-- Base de datos: MariaDB
-- Solo crea índices (IF NOT EXISTS): no cambia datos ni resultados y se
-- puede ejecutar más de una vez.
-- ============================================================

-- ------------------------------------------------------------
-- servicios: vigencia por fechas
-- ------------------------------------------------------------
-- V20260305_1 intentó crear este índice con los nombres de columna en
-- camelCase (fechaFinServicio, fechaInicioServicio), que no existen en la
-- tabla, así que nunca llegó a crearse. Lo usan:
--   * ServicioRepository.countServiciosActivos: total de servicios activos
--     (fecha_inicio_servicio <= hoy AND fecha_fin_servicio > hoy).
--   * ServicioRepository.findIdsPuntosSuministroConServicioActivo: listado de
--     puntos de suministro (mismas condiciones de fecha).
--   * Informes «Servicios vigentes» y «Servicios pendientes de pago» con
--     filtro de fechas (s.fecha_fin_servicio >= :desde / <= :hasta).
CREATE INDEX IF NOT EXISTS IX_Servicios_FechaFin_FechaInicio
    ON servicios(fecha_fin_servicio, fecha_inicio_servicio);

-- ------------------------------------------------------------
-- contratos: renovación dentro del mismo punto de suministro
-- ------------------------------------------------------------
-- Los informes «Contratos pendientes de renovación» y «Histórico de
-- contratos» buscan, por cada contrato, otro del mismo punto que empiece en o
-- después de su fin:
--     renovacion.id_punto_suministro = contratos.id_punto_suministro
--     AND renovacion.fecha_inicio >= contratos.fecha_fin
--     ORDER BY renovacion.fecha_inicio LIMIT 1
-- Los índices actuales empiezan por punto y fecha_fin; con este la búsqueda es
-- un rango directo y ya ordenado. (V20260305_1 lo intentó como
-- idPuntoSuministro/fechaInicio y tampoco se creó.)
CREATE INDEX IF NOT EXISTS IX_Contratos_PuntoSuministro_FechaInicio
    ON contratos(id_punto_suministro, fecha_inicio);

-- ------------------------------------------------------------
-- contratos: número de contrato
-- ------------------------------------------------------------
-- La búsqueda de contratos de MCAS (McasContratoLocator: número exacto y
-- «empieza por», findTop10ByContrato... / findFirstByContrato...) y la
-- simulación recorrían la tabla entera en cada búsqueda.
CREATE INDEX IF NOT EXISTS IX_Contratos_Contrato
    ON contratos(contrato);

-- Actualizar estadísticas del optimizador para las tablas afectadas
ANALYZE TABLE servicios;
ANALYZE TABLE contratos;
