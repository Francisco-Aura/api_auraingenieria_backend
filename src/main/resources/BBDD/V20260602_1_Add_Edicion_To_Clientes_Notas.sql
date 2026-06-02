-- ============================================================
-- Agregar trazabilidad de edicion a notas de cliente
-- ============================================================

ALTER TABLE clientes_notas
    ADD COLUMN IF NOT EXISTS fecha_edicion DATETIME NULL,
    ADD COLUMN IF NOT EXISTS editado BOOLEAN NOT NULL DEFAULT FALSE;
