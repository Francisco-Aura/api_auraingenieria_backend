-- Propuestas al cliente del Planificador: encaje de los paneles en el proyecto, obligatorio para descargar la propuesta (como el logo).
-- Si la propuesta no lo trae, se toma el de los parámetros del presupuesto. MariaDB 10.11. Lo aplica solo el backend al arrancar
-- (una vez por entorno, queda anotado en pl_scripts); se puede repetir sin riesgo.

ALTER TABLE pl_propuestas
    ADD COLUMN IF NOT EXISTS encaje_cliente LONGTEXT NOT NULL DEFAULT '' AFTER logo_cliente;

-- Comprobación
SELECT COUNT(*) AS propuestas, SUM(encaje_cliente <> '') AS con_encaje FROM pl_propuestas;
