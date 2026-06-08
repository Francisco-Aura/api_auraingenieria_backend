-- ============================================================
-- ADD: columna fijado en clientes_notas
-- Fecha: 2026-06-04
-- ============================================================

ALTER TABLE clientes_notas
    ADD COLUMN IF NOT EXISTS fijado TINYINT(1) NOT NULL DEFAULT 0;
