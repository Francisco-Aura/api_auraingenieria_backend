-- Estudios fotovoltaicos del Planificador (registro con estado y observaciones, datos de entrada, resultado y curva de consumo).
-- MariaDB 10.11. Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts); se puede repetir sin riesgo.

CREATE TABLE IF NOT EXISTS pl_estudios (
    id              VARCHAR(64)  NOT NULL PRIMARY KEY,
    cliente         VARCHAR(255) NOT NULL DEFAULT '',
    cups            VARCHAR(40)  NOT NULL DEFAULT '',
    estado          VARCHAR(40)  NOT NULL DEFAULT 'en_estudio',
    observaciones   TEXT         NOT NULL,
    datos           JSON         NOT NULL,
    resultado       JSON         NOT NULL,
    curva           MEDIUMTEXT   NULL,
    curva_estimada  TINYINT(1)   NOT NULL DEFAULT 0,
    curva_origen    VARCHAR(255) NOT NULL DEFAULT '',
    creado_el       DATETIME(6)  NOT NULL,
    actualizado_el  DATETIME(6)  NOT NULL,
    actualizado_por VARCHAR(255) NOT NULL DEFAULT '',
    INDEX idx_pl_estudios_actualizado (actualizado_el),
    INDEX idx_pl_estudios_estado (estado)
) CHARACTER SET utf8mb4;
