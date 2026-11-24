-- Precios regulados de la electricidad («a BOE») para los estudios del Planificador: peajes de transporte y distribución (CNMC) y
-- cargos del sistema (Ministerio), por año, tarifa de acceso y periodo. Una fila por año, tarifa, término (energía en €/kWh o potencia en
-- €/kW·año) y concepto (peaje o cargo). Los periodos que la tarifa no tiene van a NULL (2.0TD: potencia P1-P2 y energía P1-P3).
-- MariaDB 10.11. Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts); se puede repetir sin riesgo.
-- Cada año nuevo va en un script nuevo con las mismas sentencias INSERT.

CREATE TABLE IF NOT EXISTS pl_precios_regulados (
    id         INT            NOT NULL AUTO_INCREMENT PRIMARY KEY,
    anio       SMALLINT       NOT NULL,
    tarifa     VARCHAR(10)    NOT NULL,
    termino    VARCHAR(10)    NOT NULL,
    concepto   VARCHAR(10)    NOT NULL,
    p1         DECIMAL(12, 6) NULL,
    p2         DECIMAL(12, 6) NULL,
    p3         DECIMAL(12, 6) NULL,
    p4         DECIMAL(12, 6) NULL,
    p5         DECIMAL(12, 6) NULL,
    p6         DECIMAL(12, 6) NULL,
    referencia VARCHAR(255)   NOT NULL DEFAULT '',
    UNIQUE KEY uk_pl_precios_regulados (anio, tarifa, termino, concepto)
) CHARACTER SET utf8mb4;

-- 2026. Peajes: Resolución de la CNMC de 18 de diciembre de 2025 (BOE-A-2025-26348, BOE núm. 306 de 22/12/2025), anexo I.
INSERT INTO pl_precios_regulados (anio, tarifa, termino, concepto, p1, p2, p3, p4, p5, p6, referencia) VALUES
    (2026, '2.0TD', 'potencia', 'peaje', 23.324952, 0.443770, NULL, NULL, NULL, NULL, 'BOE-A-2025-26348'),
    (2026, '3.0TD', 'potencia', 'peaje', 14.935084, 7.894323, 2.502996, 1.907795, 0.535313, 0.535313, 'BOE-A-2025-26348'),
    (2026, '6.1TD', 'potencia', 'peaje', 23.946498, 12.687713, 4.747747, 3.339695, 0.070979, 0.062703, 'BOE-A-2025-26348'),
    (2026, '6.2TD', 'potencia', 'peaje', 16.786379, 9.455297, 2.502855, 1.521894, 0.059359, 0.052513, 'BOE-A-2025-26348'),
    (2026, '6.3TD', 'potencia', 'peaje', 10.397365, 6.258717, 2.096386, 1.366437, 0.044362, 0.038723, 'BOE-A-2025-26348'),
    (2026, '6.4TD', 'potencia', 'peaje', 6.606205, 3.935625, 0.987554, 0.686109, 0.020376, 0.013971, 'BOE-A-2025-26348'),
    (2026, '2.0TD', 'energia', 'peaje', 0.033261, 0.016409, 0.000077, NULL, NULL, NULL, 'BOE-A-2025-26348'),
    (2026, '3.0TD', 'energia', 'peaje', 0.027511, 0.012376, 0.004943, 0.002627, 0.000111, 0.000031, 'BOE-A-2025-26348'),
    (2026, '6.1TD', 'energia', 'peaje', 0.026785, 0.012281, 0.005133, 0.002780, 0.000120, 0.000029, 'BOE-A-2025-26348'),
    (2026, '6.2TD', 'energia', 'peaje', 0.014736, 0.007202, 0.002542, 0.001254, 0.000062, 0.000020, 'BOE-A-2025-26348'),
    (2026, '6.3TD', 'energia', 'peaje', 0.011279, 0.005324, 0.001994, 0.000995, 0.000048, 0.000014, 'BOE-A-2025-26348'),
    (2026, '6.4TD', 'energia', 'peaje', 0.008427, 0.003946, 0.001458, 0.000716, 0.000038, 0.000004, 'BOE-A-2025-26348')
ON DUPLICATE KEY UPDATE p1 = VALUES(p1), p2 = VALUES(p2), p3 = VALUES(p3), p4 = VALUES(p4), p5 = VALUES(p5), p6 = VALUES(p6),
    referencia = VALUES(referencia);

-- 2026. Cargos: Orden TED/1524/2025, de 23 de diciembre (BOE-A-2025-26705, BOE núm. 312 de 27/12/2025). La orden usa segmentos
-- tarifarios: 1 = 2.0TD, 2 = 3.0TD, 3 = 6.1TD, 4 = 6.2TD, 5 = 6.3TD, 6 = 6.4TD (art. 3.2 del Real Decreto 148/2021).
INSERT INTO pl_precios_regulados (anio, tarifa, termino, concepto, p1, p2, p3, p4, p5, p6, referencia) VALUES
    (2026, '2.0TD', 'potencia', 'cargo', 4.379461, 0.281653, NULL, NULL, NULL, NULL, 'BOE-A-2025-26705'),
    (2026, '3.0TD', 'potencia', 'cargo', 5.441843, 2.723298, 1.978538, 1.978538, 1.978538, 0.906974, 'BOE-A-2025-26705'),
    (2026, '6.1TD', 'potencia', 'cargo', 5.648870, 2.826996, 2.054134, 2.054134, 2.054134, 0.941478, 'BOE-A-2025-26705'),
    (2026, '6.2TD', 'potencia', 'cargo', 3.317209, 1.660371, 1.206258, 1.206258, 1.206258, 0.552868, 'BOE-A-2025-26705'),
    (2026, '6.3TD', 'potencia', 'cargo', 2.656027, 1.329146, 0.965679, 0.965679, 0.965679, 0.442671, 'BOE-A-2025-26705'),
    (2026, '6.4TD', 'potencia', 'cargo', 1.299240, 0.650162, 0.472451, 0.472451, 0.472451, 0.216540, 'BOE-A-2025-26705'),
    (2026, '2.0TD', 'energia', 'cargo', 0.064292, 0.012858, 0.003215, NULL, NULL, NULL, 'BOE-A-2025-26705'),
    (2026, '3.0TD', 'energia', 'cargo', 0.035841, 0.026538, 0.014336, 0.007168, 0.004595, 0.002867, 'BOE-A-2025-26705'),
    (2026, '6.1TD', 'energia', 'cargo', 0.019489, 0.014436, 0.007795, 0.003898, 0.002499, 0.001559, 'BOE-A-2025-26705'),
    (2026, '6.2TD', 'energia', 'cargo', 0.009144, 0.006773, 0.003658, 0.001829, 0.001172, 0.000732, 'BOE-A-2025-26705'),
    (2026, '6.3TD', 'energia', 'cargo', 0.007496, 0.005552, 0.002998, 0.001499, 0.000961, 0.000600, 'BOE-A-2025-26705'),
    (2026, '6.4TD', 'energia', 'cargo', 0.002848, 0.002109, 0.001139, 0.000570, 0.000365, 0.000228, 'BOE-A-2025-26705')
ON DUPLICATE KEY UPDATE p1 = VALUES(p1), p2 = VALUES(p2), p3 = VALUES(p3), p4 = VALUES(p4), p5 = VALUES(p5), p6 = VALUES(p6),
    referencia = VALUES(referencia);

-- Comprobación: peajes + cargos de 2026 (3.0TD: potencia P1 20,376927 €/kW·año; energía P1 0,063352 €/kWh)
SELECT tarifa, termino, SUM(p1) AS p1, SUM(p2) AS p2, SUM(p3) AS p3, SUM(p4) AS p4, SUM(p5) AS p5, SUM(p6) AS p6
FROM pl_precios_regulados WHERE anio = 2026 GROUP BY tarifa, termino ORDER BY tarifa, termino;
