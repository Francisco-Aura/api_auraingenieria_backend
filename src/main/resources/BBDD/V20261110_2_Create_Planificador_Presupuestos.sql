-- Esquema de presupuestos y propuestas del Planificador (Fase 3). MariaDB 10.11. Se aplica a mano.

CREATE TABLE IF NOT EXISTS pl_presupuestos (
    id                VARCHAR(64)  NOT NULL PRIMARY KEY,
    nombre            VARCHAR(255) NOT NULL DEFAULT '',
    propietario       VARCHAR(255) NOT NULL DEFAULT '',
    propietario_id    VARCHAR(64)  NOT NULL DEFAULT '',
    estado            VARCHAR(20)  NOT NULL DEFAULT 'borrador',
    oferta_id         VARCHAR(64)  NOT NULL DEFAULT '',
    cliente_id        INT          NULL,
    cliente_tipo      VARCHAR(20)  NOT NULL DEFAULT '',
    nombre_variante   VARCHAR(60)  NOT NULL DEFAULT '',
    datos             JSON         NOT NULL,
    kwp               DOUBLE       NOT NULL DEFAULT 0,
    paneles           INT          NOT NULL DEFAULT 0,
    total_presupuesto DOUBLE       NOT NULL DEFAULT 0,
    version           INT          NOT NULL DEFAULT 1,
    creado_el         DATETIME(6)  NOT NULL,
    actualizado_el    DATETIME(6)  NOT NULL,
    actualizado_por   VARCHAR(255) NOT NULL DEFAULT '',
    INDEX idx_pl_presupuestos_estado (estado),
    INDEX idx_pl_presupuestos_oferta (oferta_id),
    INDEX idx_pl_presupuestos_propietario (propietario_id),
    INDEX idx_pl_presupuestos_cliente (cliente_id),
    INDEX idx_pl_presupuestos_actualizado (actualizado_el)
) CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS pl_propuestas (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    presupuesto_id VARCHAR(64)  NOT NULL,
    version        INT          NOT NULL DEFAULT 1,
    estado         VARCHAR(20)  NOT NULL DEFAULT 'borrador',
    nota           TEXT         NOT NULL,
    creado_el      DATETIME(6)  NOT NULL,
    creado_por     VARCHAR(255) NOT NULL DEFAULT '',
    enviado_el     DATETIME(6)  NULL,
    enviado_por    VARCHAR(255) NOT NULL DEFAULT '',
    decidido_el    DATETIME(6)  NULL,
    decidido_por   VARCHAR(255) NOT NULL DEFAULT '',
    logo_cliente   LONGTEXT     NOT NULL,
    INDEX idx_pl_propuestas_presupuesto (presupuesto_id),
    INDEX idx_pl_propuestas_estado (estado),
    CONSTRAINT fk_pl_propuestas_presupuesto FOREIGN KEY (presupuesto_id) REFERENCES pl_presupuestos (id)
) CHARACTER SET utf8mb4;
