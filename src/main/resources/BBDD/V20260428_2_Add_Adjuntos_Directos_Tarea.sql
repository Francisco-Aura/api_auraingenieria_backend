-- V20260428_2 – Adjuntos directos en tareas
--               Permite subir adjuntos directamente a una tarea en el momento
--               de su creación, sin necesitar crear un comentario primero.
--
-- Cambios:
--   · comentario_id pasa a ser nullable (adjunto puede no tener comentario)
--   · Se añade tarea_id como FK directa para adjuntos de nivel tarea

ALTER TABLE modulotareas_adjuntos
  MODIFY COLUMN comentario_id BIGINT NULL,
  ADD COLUMN tarea_id BIGINT NULL
    COMMENT 'FK directa a la tarea (para adjuntos subidos al crear la tarea)'
    AFTER comentario_id,
  ADD CONSTRAINT fk_adjunto_tarea
    FOREIGN KEY (tarea_id) REFERENCES modulotareas_tareas(id)
    ON DELETE CASCADE;
