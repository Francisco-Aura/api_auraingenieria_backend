-- Proyectos comerciales del Planificador: un cliente y el nombre de su proyecto, creados antes que sus estudios y presupuestos. Estudios y
-- presupuestos del mismo cliente y proyecto cuelgan de él en la lista «Presupuestos y estudios». No toca ninguna tabla existente (los
-- proyectos en marcha, con contrato, siguen en pl_proyectos).
-- MariaDB 10.11. Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts); se puede repetir sin riesgo.

CREATE TABLE IF NOT EXISTS pl_proyectos_comerciales (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    clave          VARCHAR(255) NOT NULL,
    cliente_tipo   VARCHAR(20)  NOT NULL DEFAULT 'potencial',
    cliente_id     INT          NULL,
    cliente_nombre VARCHAR(255) NOT NULL DEFAULT '',
    cliente        JSON         NOT NULL,
    nombre         VARCHAR(80)  NOT NULL DEFAULT '',
    creado_el      DATETIME(6)  NOT NULL,
    creado_por     VARCHAR(255) NOT NULL DEFAULT '',
    UNIQUE KEY uk_pl_proyectos_comerciales_clave (clave)
);

-- Comprobación
SELECT COUNT(*) AS proyectos_comerciales FROM pl_proyectos_comerciales;
