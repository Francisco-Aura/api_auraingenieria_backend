ALTER TABLE contratos
    ADD COLUMN nombre_archivo VARCHAR(255) NULL,
    ADD COLUMN tipo_mime_archivo VARCHAR(100) NULL,
    ADD COLUMN tamanio_archivo BIGINT NULL;
