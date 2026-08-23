-- Añade campos de archivo adjunto a la tabla clientes_notas
ALTER TABLE clientes_notas ADD COLUMN nombre_archivo VARCHAR(255) NULL;
ALTER TABLE clientes_notas ADD COLUMN ruta_archivo VARCHAR(500) NULL;
ALTER TABLE clientes_notas ADD COLUMN tipo_mime VARCHAR(100) NULL;
