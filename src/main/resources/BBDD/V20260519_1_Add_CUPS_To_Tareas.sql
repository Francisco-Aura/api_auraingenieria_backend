-- Añade columna cups a la tabla de tareas para asociar una tarea a un punto de suministro concreto del cliente.
-- El valor NULL / vacío indica que la tarea es de carácter GENERAL (no vinculada a un suministro específico).
ALTER TABLE modulotareas_tareas
    ADD COLUMN cups VARCHAR(100) NULL AFTER cliente_potencial_nombre;
