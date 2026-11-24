-- Presupuestos del Planificador: estudio al que están asociados (opcional). Solo se asocian presupuesto y estudio del mismo cliente y
-- proyecto. MariaDB 10.11. Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts); se puede repetir
-- sin riesgo. Los presupuestos que ya hay se quedan sin estudio.

ALTER TABLE pl_presupuestos
    ADD COLUMN IF NOT EXISTS estudio_id VARCHAR(64) NOT NULL DEFAULT '' AFTER oferta_id;

-- Comprobación
SELECT COUNT(*) AS presupuestos, SUM(estudio_id <> '') AS con_estudio FROM pl_presupuestos;
