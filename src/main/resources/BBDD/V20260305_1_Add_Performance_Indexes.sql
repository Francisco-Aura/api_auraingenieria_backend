-- ============================================================
-- ÍNDICES DE RENDIMIENTO PARA OPTIMIZACIÓN DE CONSULTAS
-- Fecha: 2026-03-05
-- Base de datos: MariaDB
-- Descripción: Índices para mejorar el rendimiento de las 
--              consultas más frecuentes en el módulo CRM
-- ============================================================

-- ============================================================
-- TABLA: contratos
-- ============================================================

-- Índice para búsqueda de contratos por punto de suministro
-- Usado en: /api/contratos/punto-suministro/{id}
CREATE INDEX IF NOT EXISTS IX_Contratos_IdPuntoSuministro 
ON contratos(idPuntoSuministro);

-- Índice para búsqueda de contratos vigentes (por fechas)
-- Usado en: Cálculo de contrato actual y tarifas activas
CREATE INDEX IF NOT EXISTS IX_Contratos_Fechas_Vigencia 
ON contratos(fechaFin, fechaInicio);

-- Índice compuesto para búsqueda de contratos vigentes por punto de suministro
-- Optimiza la consulta de contrato actual
CREATE INDEX IF NOT EXISTS IX_Contratos_PuntoSuministro_Vigencia 
ON contratos(idPuntoSuministro, fechaInicio, fechaFin);

-- ============================================================
-- TABLA: puntos_suministro
-- ============================================================

-- Índice para búsqueda de puntos por cliente
-- Usado en: /api/puntos-suministro/cliente/{id}/cartera
CREATE INDEX IF NOT EXISTS IX_PuntosSuministro_IdCliente 
ON puntos_suministro(idCliente);

-- Índice para filtrar puntos activos vs dados de baja
CREATE INDEX IF NOT EXISTS IX_PuntosSuministro_FechaBaja 
ON puntos_suministro(fechaBaja);

-- ============================================================
-- TABLA: servicios
-- ============================================================

-- Índice para búsqueda de servicios por punto de suministro
-- Usado en: /api/servicios/punto-suministro/{id}
CREATE INDEX IF NOT EXISTS IX_Servicios_IdPuntoSuministro 
ON servicios(idPuntoSuministro);

-- Índice para búsqueda de servicios vigentes
CREATE INDEX IF NOT EXISTS IX_Servicios_Fechas_Vigencia 
ON servicios(fechaFinServicio, fechaInicioServicio);

-- ============================================================
-- TABLA: servicios_pagos
-- ============================================================

-- Índice para búsqueda de pagos por servicio
-- Usado en: /api/servicios-pagos/servicio/{id}
CREATE INDEX IF NOT EXISTS IX_ServiciosPagos_IdServicio 
ON servicios_pagos(idServicio);

-- ============================================================
-- TABLA: clientes
-- ============================================================

-- Índice para búsqueda de clientes por DNI/CIF
CREATE INDEX IF NOT EXISTS IX_Clientes_DniCif 
ON clientes(dniCif);

-- ============================================================
-- TABLA: tarifas_luz y tarifas_gas
-- ============================================================

-- Estos índices ayudan en los JOINs con contratos
CREATE INDEX IF NOT EXISTS IX_TarifasLuz_Nombre 
ON tarifas_luz(nombre);

CREATE INDEX IF NOT EXISTS IX_TarifasGas_Nombre 
ON tarifas_gas(nombre);

-- ============================================================
-- TABLA: tareas (si existe)
-- ============================================================

-- Índice para búsqueda de tareas por cliente
CREATE INDEX IF NOT EXISTS IX_Tareas_IdCliente 
ON tareas(idCliente);

-- Índice para búsqueda de tareas por punto de suministro
CREATE INDEX IF NOT EXISTS IX_Tareas_IdPuntoSuministro 
ON tareas(idPuntoSuministro);

-- Índice para filtrar tareas por estado
CREATE INDEX IF NOT EXISTS IX_Tareas_Estado 
ON tareas(estado);

-- Índice compuesto para tareas por usuario asignado y estado
CREATE INDEX IF NOT EXISTS IX_Tareas_UsuarioAsignado_Estado 
ON tareas(idUsuarioAsignado, estado);

-- ============================================================
-- ANALIZAR TABLAS (Actualizar estadísticas)
-- ============================================================

ANALYZE TABLE contratos;
ANALYZE TABLE puntos_suministro;
ANALYZE TABLE servicios;
ANALYZE TABLE servicios_pagos;
ANALYZE TABLE clientes;
ANALYZE TABLE tarifas_luz;
ANALYZE TABLE tarifas_gas;
ANALYZE TABLE tareas;