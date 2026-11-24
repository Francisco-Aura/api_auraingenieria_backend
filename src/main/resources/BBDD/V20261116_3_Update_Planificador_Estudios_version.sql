-- Estudios del Planificador: se guardan como en el presupuestador. Un estudio nuevo vive solo en el navegador (local) hasta que se
-- pulsa «Guardar y compartir»; desde ahí es un borrador del servidor. «version» sube con cada guardado para avisar si otra persona
-- guardó mientras se tenía abierto (409), y «creado_por» es quien lo compartió. MariaDB 10.11. Lo aplica solo el backend al arrancar
-- (una vez por entorno, queda anotado en pl_scripts); se puede repetir sin riesgo.

ALTER TABLE pl_estudios
    ADD COLUMN IF NOT EXISTS version    INT          NOT NULL DEFAULT 1  AFTER estado,
    ADD COLUMN IF NOT EXISTS creado_por VARCHAR(255) NOT NULL DEFAULT '' AFTER creado_el;

-- Los que ya había: los creó quien los modificó por última vez
UPDATE pl_estudios SET creado_por = actualizado_por WHERE creado_por = '';

-- Comprobación
SELECT COUNT(*) AS estudios, MAX(version) AS version_maxima FROM pl_estudios;
