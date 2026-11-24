-- Estudios del Planificador: el cliente se elige como en el presupuestador, uno de la intranet (vinculado por su id) o un cliente
-- potencial (sin id). MariaDB 10.11. Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts);
-- se puede repetir sin riesgo. Los estudios que ya hay se quedan como clientes potenciales con su nombre.

ALTER TABLE pl_estudios
    ADD COLUMN IF NOT EXISTS cliente_id   INT         NULL AFTER cliente,
    ADD COLUMN IF NOT EXISTS cliente_tipo VARCHAR(20) NOT NULL DEFAULT 'potencial' AFTER cliente_id;

-- Comprobación
SELECT cliente_tipo, COUNT(*) AS estudios FROM pl_estudios GROUP BY cliente_tipo;
