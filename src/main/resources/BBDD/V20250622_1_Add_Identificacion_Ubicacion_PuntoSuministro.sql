-- Migración para agregar campos identificacion y ubicacion a PuntoSuministro
-- Fecha: 22/06/2025

ALTER TABLE PuntoSuministro 
ADD COLUMN identificacion VARCHAR(255) NULL COMMENT 'Identificación del punto de suministro',
ADD COLUMN ubicacion VARCHAR(1000) NULL COMMENT 'Ubicación con enlace de Google Maps';
