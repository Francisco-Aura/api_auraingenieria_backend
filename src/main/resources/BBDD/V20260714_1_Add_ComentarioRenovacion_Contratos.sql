-- Añade campo comentario_renovacion a la tabla contratos
-- Permite registrar comentarios/observaciones sobre la renovación pendiente
ALTER TABLE contratos ADD COLUMN comentario_renovacion VARCHAR(10000) NULL;
