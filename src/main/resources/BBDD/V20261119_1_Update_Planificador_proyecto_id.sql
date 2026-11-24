-- Presupuestos y estudios del Planificador: la propuesta (pl_proyectos_comerciales) a la que pertenecen. Hasta ahora se agrupaban por el
-- texto «Cliente · Proyecto» del nombre; con este id el agrupado no depende de cómo esté escrito. Solo añade una columna vacía: los datos
-- que ya hay no se tocan (el backend rellena el id una sola vez al arrancar, anotado en pl_meta, con la misma comparación de nombres que ya usa).
-- MariaDB 10.11. Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts); se puede repetir sin riesgo.

ALTER TABLE pl_presupuestos
    ADD COLUMN IF NOT EXISTS proyecto_id VARCHAR(64) NOT NULL DEFAULT '' AFTER estudio_id;

ALTER TABLE pl_presupuestos
    ADD INDEX IF NOT EXISTS idx_pl_presupuestos_proyecto (proyecto_id);

ALTER TABLE pl_estudios
    ADD COLUMN IF NOT EXISTS proyecto_id VARCHAR(64) NOT NULL DEFAULT '';

ALTER TABLE pl_estudios
    ADD INDEX IF NOT EXISTS idx_pl_estudios_proyecto (proyecto_id);

-- Comprobación
SELECT (SELECT COUNT(*) FROM pl_presupuestos) AS presupuestos, (SELECT COUNT(*) FROM pl_estudios) AS estudios;
