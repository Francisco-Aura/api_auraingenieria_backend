CREATE TABLE clientes_notas_adjuntos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nota_id BIGINT NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    ruta_archivo VARCHAR(500) NOT NULL,
    tipo_mime VARCHAR(100),
    fecha_subida DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_nota_adjunto_nota
        FOREIGN KEY (nota_id)
        REFERENCES clientes_notas(id)
        ON DELETE CASCADE,

    INDEX idx_nota_adjunto_nota (nota_id)
)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;

INSERT INTO clientes_notas_adjuntos (
    nota_id,
    nombre_archivo,
    ruta_archivo,
    tipo_mime,
    fecha_subida
)
SELECT
    id,
    nombre_archivo,
    ruta_archivo,
    tipo_mime,
    COALESCE(fecha_creacion, NOW())
FROM clientes_notas
WHERE ruta_archivo IS NOT NULL;