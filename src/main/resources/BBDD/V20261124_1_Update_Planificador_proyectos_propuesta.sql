-- Proyectos del Planificador: la propuesta validada de la que se crearon. Cada propuesta da como mucho un proyecto activo; el enlace
-- permite avisarlo en la propuesta y no crear el mismo proyecto dos veces. Solo añade una columna vacía: los proyectos que ya hay no se tocan.
-- MariaDB 10.11. Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts); se puede repetir sin riesgo.

ALTER TABLE pl_proyectos
    ADD COLUMN IF NOT EXISTS propuesta_id VARCHAR(64) NOT NULL DEFAULT '' AFTER plan_pagos;

ALTER TABLE pl_proyectos
    ADD INDEX IF NOT EXISTS idx_pl_proyectos_propuesta (propuesta_id);

-- Comprobación
SELECT COUNT(*) AS proyectos FROM pl_proyectos;
