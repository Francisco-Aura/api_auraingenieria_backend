-- V20260428_1 – Clientes potenciales en tareas
--               Añade un campo de texto libre para poder asociar una tarea
--               a un cliente potencial que no esté registrado en la BD,
--               sin necesidad de crear un registro de cliente formal.
--
-- Coexiste con cliente_id (FK a clientes):
--   · Si cliente_id tiene valor → cliente real registrado
--   · Si cliente_potencial_nombre tiene valor → cliente potencial (texto libre)

ALTER TABLE modulotareas_tareas
  ADD COLUMN cliente_potencial_nombre VARCHAR(200) NULL
    COMMENT 'Nombre libre para clientes potenciales no registrados en BD'
    AFTER cliente_id;
