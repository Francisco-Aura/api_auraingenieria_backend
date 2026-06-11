-- ============================================================
-- Notas de cliente independientes del modulo de tareas
-- ============================================================

CREATE TABLE IF NOT EXISTS clientes_notas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cliente_id INT NOT NULL,
    usuario_id INT NOT NULL,
    cups VARCHAR(50) NULL,
    comentario TEXT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_clientes_notas_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_clientes_notas_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB;

CREATE INDEX idx_clientes_notas_cliente_fecha
    ON clientes_notas (cliente_id, fecha_creacion);

CREATE INDEX idx_clientes_notas_usuario
    ON clientes_notas (usuario_id);

-- Migracion de notas antiguas creadas con tarea ancla "NOTAS CLIENTE"
INSERT INTO clientes_notas (cliente_id, usuario_id, cups, comentario, fecha_creacion)
SELECT
    t.cliente_id,
    c.usuario_id,
    NULLIF(TRIM(t.cups), ''),
    c.comentario,
    c.fecha_creacion
FROM modulotareas_comentarios c
JOIN modulotareas_tareas t ON t.id = c.tarea_id
WHERE t.titulo = 'NOTAS CLIENTE'
    AND t.cliente_id IS NOT NULL
    AND NOT EXISTS (
            SELECT 1
            FROM clientes_notas n
            WHERE n.cliente_id = t.cliente_id
                AND n.usuario_id = c.usuario_id
                AND n.fecha_creacion = c.fecha_creacion
                AND n.comentario = c.comentario
                AND COALESCE(n.cups, '') = COALESCE(NULLIF(TRIM(t.cups), ''), '')
    );
