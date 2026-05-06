-- para notificaciones de menciones:
-- asegura que modulotareas_notificaciones.tipo acepte valores nuevos

ALTER TABLE modulotareas_notificaciones
    MODIFY COLUMN tipo VARCHAR(30) NOT NULL;

-- Normalizar datos 
UPDATE modulotareas_notificaciones
SET tipo = 'COMENTARIO'
WHERE tipo IS NULL
   OR TRIM(tipo) = ''
   OR tipo NOT IN ('ASIGNADA', 'CAMBIO_ESTADO', 'VENCIMIENTO', 'COMENTARIO', 'COMPLETADA', 'MENCION');
