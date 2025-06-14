CREATE TABLE IF NOT EXISTS documentos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_contrato INT NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    ruta_dropbox VARCHAR(255) NOT NULL,
    tipo_documento VARCHAR(50) NOT NULL,
    fecha_subida TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_subida VARCHAR(100) NOT NULL,
    activo BOOLEAN DEFAULT TRUE,
    observaciones VARCHAR(500),
    FOREIGN KEY (id_contrato) REFERENCES contratos(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_spanish_ci;