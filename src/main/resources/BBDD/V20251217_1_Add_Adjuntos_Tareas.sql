-- Script de migración para agregar funcionalidad de adjuntos en comentarios de tareas
-- Fecha: 2025-12-17

-- Crear tabla de adjuntos
CREATE TABLE IF NOT EXISTS modulotareas_adjuntos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comentario_id BIGINT NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    ruta_archivo VARCHAR(500) NOT NULL,
    tipo_mime VARCHAR(100),
    tamanio BIGINT NOT NULL COMMENT 'Tamaño en bytes',
    fecha_subida DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_adjunto_comentario FOREIGN KEY (comentario_id) 
        REFERENCES modulotareas_comentarios(id) ON DELETE CASCADE,
    INDEX idx_comentario_id (comentario_id),
    INDEX idx_fecha_subida (fecha_subida)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Tabla para almacenar archivos adjuntos de comentarios en tareas';
