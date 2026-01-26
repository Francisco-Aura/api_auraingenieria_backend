-- Reinsertar estados del módulo de tareas

INSERT INTO modulotareas_estados (nombre, color_hex, estado_final, orden, cuenta_tiempo, es_bloqueo)
VALUES 
    ('Pendiente', '#F39C12', 0, 1, 0, 0),
    ('En Progreso', '#2980B9', 0, 2, 1, 0),
    ('Bloqueada', '#C0392B', 0, 3, 0, 1),
    ('Completada', '#27AE60', 1, 4, 0, 0)
ON DUPLICATE KEY UPDATE 
    color_hex = VALUES(color_hex), 
    estado_final = VALUES(estado_final), 
    orden = VALUES(orden),
    cuenta_tiempo = VALUES(cuenta_tiempo),
    es_bloqueo = VALUES(es_bloqueo);
