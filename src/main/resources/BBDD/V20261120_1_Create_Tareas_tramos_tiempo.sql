-- Control de tiempo por tarea: cada tramo es un periodo en el que un usuario ha trabajado en una tarea (inicio y fin).
-- Un tramo sin fin es el que está corriendo. La columna generada abierto_usuario impide, desde la propia base, que un usuario
-- tenga dos tramos abiertos a la vez (los NULL no chocan entre sí en un índice único).
-- No toca modulotareas_tareas: su contador duracion_activa_segundos sigue como estaba.
-- MariaDB 10.11. Lo aplica solo el backend al arrancar; se puede repetir sin riesgo.

CREATE TABLE IF NOT EXISTS modulotareas_tramos_tiempo (
    id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tarea_id       BIGINT      NOT NULL,
    usuario_id     INT         NOT NULL,
    inicio         DATETIME(3) NOT NULL,
    fin            DATETIME(3) NULL,
    cierre         VARCHAR(20) NULL,
    abierto_usuario INT GENERATED ALWAYS AS (IF(fin IS NULL, usuario_id, NULL)) VIRTUAL,
    UNIQUE KEY uk_tramo_abierto_usuario (abierto_usuario),
    KEY idx_tramo_usuario_inicio (usuario_id, inicio),
    KEY idx_tramo_tarea (tarea_id),
    CONSTRAINT fk_tramo_tarea FOREIGN KEY (tarea_id) REFERENCES modulotareas_tareas (id) ON DELETE CASCADE,
    CONSTRAINT fk_tramo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
);

-- Comprobación
SELECT COUNT(*) AS tramos FROM modulotareas_tramos_tiempo;
