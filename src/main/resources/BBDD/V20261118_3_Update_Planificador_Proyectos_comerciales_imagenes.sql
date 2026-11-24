-- Propuestas (proyectos comerciales) del Planificador: logo de la empresa y encaje de los paneles, que heredan los presupuestos que se
-- crean desde ellas (el CIF va dentro del cliente, ya guardado). MariaDB 10.11. Lo aplica solo el backend al arrancar (una vez por
-- entorno, queda anotado en pl_scripts); se puede repetir sin riesgo.

ALTER TABLE pl_proyectos_comerciales
    ADD COLUMN IF NOT EXISTS logo   MEDIUMTEXT NOT NULL AFTER nombre,
    ADD COLUMN IF NOT EXISTS encaje MEDIUMTEXT NOT NULL AFTER logo;

-- Comprobación
SELECT COUNT(*) AS propuestas, SUM(logo <> '') AS con_logo, SUM(encaje <> '') AS con_encaje FROM pl_proyectos_comerciales;
