-- Tracking avanzado para módulo de tareas

ALTER TABLE modulotareas_tareas
    ADD COLUMN duracion_activa_segundos BIGINT NOT NULL DEFAULT 0 AFTER fecha_actualizacion,
    ADD COLUMN fecha_inicio_progreso DATETIME NULL AFTER duracion_activa_segundos,
    ADD COLUMN tiempo_bloqueado_segundos BIGINT NOT NULL DEFAULT 0 AFTER fecha_inicio_progreso,
    ADD COLUMN total_bloqueos INT NOT NULL DEFAULT 0 AFTER tiempo_bloqueado_segundos,
    ADD COLUMN motivo_bloqueo VARCHAR(255) NULL AFTER total_bloqueos,
    ADD COLUMN fecha_bloqueo DATETIME NULL AFTER motivo_bloqueo,
    ADD COLUMN usuario_bloqueo_id INT NULL AFTER fecha_bloqueo,
    ADD CONSTRAINT fk_tarea_usuario_bloqueo FOREIGN KEY (usuario_bloqueo_id) REFERENCES Usuarios(id);

ALTER TABLE modulotareas_estados
    ADD COLUMN cuenta_tiempo TINYINT(1) NOT NULL DEFAULT 0 AFTER estado_final,
    ADD COLUMN es_bloqueo TINYINT(1) NOT NULL DEFAULT 0 AFTER cuenta_tiempo;

UPDATE modulotareas_estados
SET cuenta_tiempo = CASE WHEN LOWER(nombre) LIKE 'en progreso%' THEN 1 ELSE cuenta_tiempo END,
    es_bloqueo = CASE WHEN LOWER(nombre) LIKE 'bloqueada%' THEN 1 ELSE es_bloqueo END;

CREATE TABLE IF NOT EXISTS modulotareas_tarea_historial (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tarea_id BIGINT NOT NULL,
    estado_id BIGINT NOT NULL,
    usuario_id INT NULL,
    fecha_inicio DATETIME NOT NULL,
    fecha_fin DATETIME NULL,
    duracion_activa_segundos BIGINT NULL,
    tiempo_bloqueo_segundos BIGINT NULL,
    motivo_bloqueo VARCHAR(255) NULL,
    comentario VARCHAR(255) NULL,
    CONSTRAINT fk_th_tarea FOREIGN KEY (tarea_id) REFERENCES modulotareas_tareas(id),
    CONSTRAINT fk_th_estado FOREIGN KEY (estado_id) REFERENCES modulotareas_estados(id),
    CONSTRAINT fk_th_usuario FOREIGN KEY (usuario_id) REFERENCES Usuarios(id)
) ENGINE=InnoDB;

CREATE INDEX idx_th_tarea ON modulotareas_tarea_historial (tarea_id);
CREATE INDEX idx_th_estado ON modulotareas_tarea_historial (estado_id);
CREATE INDEX idx_th_fecha ON modulotareas_tarea_historial (fecha_inicio);

-- Asegurar que la columna tipo es VARCHAR y no ENUM (Hibernate la maneja como VARCHAR con @Enumerated(EnumType.STRING))
ALTER TABLE modulotareas_notificaciones MODIFY COLUMN tipo VARCHAR(30) NOT NULL;
