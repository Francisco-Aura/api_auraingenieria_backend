-- Añadir campo fijado a la tabla de comentarios de tareas
-- Permite fijar comentarios importantes para que aparezcan arriba del listado

ALTER TABLE modulotareas_comentarios 
ADD COLUMN IF NOT EXISTS fijado BOOLEAN NOT NULL DEFAULT FALSE;

-- Crear índice para optimizar consultas de comentarios fijados
CREATE INDEX IF NOT EXISTS idx_comentarios_fijado ON modulotareas_comentarios(fijado);
