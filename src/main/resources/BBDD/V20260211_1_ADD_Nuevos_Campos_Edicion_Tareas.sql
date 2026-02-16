-- Agregar nuevos campos a la tabla de comentarios para registrar ediciones
-- fecha_edicion: guarda cuándo fue editado el comentario
-- editado: indica si el comentario ha sido modificado después de su creación

ALTER TABLE modulotareas_comentarios 
ADD COLUMN IF NOT EXISTS fecha_edicion DATETIME NULL;

ALTER TABLE modulotareas_comentarios 
ADD COLUMN IF NOT EXISTS editado BOOLEAN NOT NULL DEFAULT FALSE;