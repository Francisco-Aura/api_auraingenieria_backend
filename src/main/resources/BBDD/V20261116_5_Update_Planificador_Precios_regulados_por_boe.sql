-- Peajes y cargos del BOE de los estudios: cada conjunto de precios es un «BOE» propio, con su nombre y la fecha desde la que vale, y
-- ya no va ligado a un año (puede haber varios en el mismo año, o uno cada dos). Los estudios usan el vigente hoy, salvo que se elija
-- uno. MariaDB 10.11. Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts); se puede repetir sin
-- riesgo si falla a medias (cada sentencia comprueba antes si ya está hecha).

CREATE TABLE IF NOT EXISTS pl_boes (
    id            INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nombre        VARCHAR(255) NOT NULL,
    vigente_desde DATE         NOT NULL,
    INDEX idx_pl_boes_vigente (vigente_desde)
) CHARACTER SET utf8mb4;

-- Los precios que ya había, uno por año, pasan a ser un BOE cada uno, vigente desde el 1 de enero de ese año
INSERT INTO pl_boes (nombre, vigente_desde)
SELECT CONCAT('Peajes y cargos ', a.anio), MAKEDATE(a.anio, 1)
FROM (SELECT DISTINCT anio FROM pl_precios_regulados) a
WHERE NOT EXISTS (SELECT 1 FROM pl_boes b WHERE b.vigente_desde = MAKEDATE(a.anio, 1))
ORDER BY a.anio;

ALTER TABLE pl_precios_regulados ADD COLUMN IF NOT EXISTS boe_id INT NULL AFTER id;

UPDATE pl_precios_regulados p JOIN pl_boes b ON b.vigente_desde = MAKEDATE(p.anio, 1) SET p.boe_id = b.id WHERE p.boe_id IS NULL;

ALTER TABLE pl_precios_regulados DROP INDEX IF EXISTS uk_pl_precios_regulados;

ALTER TABLE pl_precios_regulados
    MODIFY COLUMN boe_id INT NOT NULL,
    DROP COLUMN IF EXISTS anio,
    ADD UNIQUE KEY uk_pl_precios_regulados (boe_id, tarifa, termino, concepto);

-- Comprobación
SELECT b.id, b.nombre, b.vigente_desde, COUNT(p.id) AS precios FROM pl_boes b LEFT JOIN pl_precios_regulados p ON p.boe_id = b.id GROUP BY b.id ORDER BY b.vigente_desde;
