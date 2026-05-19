-- ============================================================
-- Índices de rendimiento 
-- Fecha: 2026-05-06.
-- ============================================================

-- contratos: búsquedas por punto de suministro ordenadas por fecha_fin
CREATE INDEX IF NOT EXISTS IX_Contratos_PuntoSuministro_FechaFin
    ON contratos(id_punto_suministro, fecha_fin DESC, id);

-- contratos: búsquedas por cliente
CREATE INDEX IF NOT EXISTS IX_Contratos_Cliente
    ON contratos(id_cliente);

-- contratos: búsquedas generales por fecha_fin (e.g. contratos activos)
CREATE INDEX IF NOT EXISTS IX_Contratos_FechaFin
    ON contratos(fecha_fin DESC);

-- contratos: búsquedas simples por punto de suministro
CREATE INDEX IF NOT EXISTS IX_Contratos_PuntoSuministro
    ON contratos(id_punto_suministro);

-- contratos: filtrado de bajas
CREATE INDEX IF NOT EXISTS IX_Contratos_FechaBaja
    ON contratos(fecha_baja, id);

-- punto_suministro: filtrado de bajas
CREATE INDEX IF NOT EXISTS IX_PuntosSuministro_FechaBaja
    ON punto_suministro(fecha_baja, id);

-- servicios: filtrado de bajas
CREATE INDEX IF NOT EXISTS IX_Servicios_FechaBaja
    ON servicios(fecha_baja, id);

-- servicios: búsquedas por cliente
CREATE INDEX IF NOT EXISTS IX_Servicios_Cliente
    ON servicios(id_cliente);

-- historial de tareas: consultas por tarea ordenadas por fecha de inicio
CREATE INDEX IF NOT EXISTS IX_TareaHistorial_TareaFecha
    ON modulotareas_tarea_historial(tarea_id, fecha_inicio DESC);

-- Actualizar estadísticas del optimizador para las tablas afectadas
ANALYZE TABLE contratos;
ANALYZE TABLE punto_suministro;
ANALYZE TABLE servicios;
ANALYZE TABLE servicios_pagos;
ANALYZE TABLE modulotareas_tarea_historial;
