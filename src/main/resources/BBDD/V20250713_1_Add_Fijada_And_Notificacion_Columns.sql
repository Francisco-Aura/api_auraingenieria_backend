-- Migración: Añadir columnas para funcionalidad de fijar tareas y notificación próximo vencimiento
-- Fecha: 2025-07-13

-- Añadir columna fijada
ALTER TABLE modulotareas_tareas 
ADD COLUMN IF NOT EXISTS fijada BOOLEAN NOT NULL DEFAULT FALSE;

-- Añadir columna notificado_proximo_vencimiento para evitar notificaciones duplicadas
ALTER TABLE modulotareas_tareas 
ADD COLUMN IF NOT EXISTS notificado_proximo_vencimiento BOOLEAN NOT NULL DEFAULT FALSE;

-- Comentarios descriptivos
COMMENT ON COLUMN modulotareas_tareas.fijada IS 'Indica si la tarea está fijada/anclada para mostrarla primero en la lista';
COMMENT ON COLUMN modulotareas_tareas.notificado_proximo_vencimiento IS 'Flag para evitar enviar múltiples notificaciones de vencimiento próximo (1h antes)';
