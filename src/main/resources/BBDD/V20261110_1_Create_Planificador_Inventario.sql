-- Esquema del inventario del Planificador (Fase 2). MariaDB 10.11. Se aplica a mano (la intranet no usa Flyway).

CREATE TABLE IF NOT EXISTS pl_marcas (
    nombre VARCHAR(60)  NOT NULL PRIMARY KEY,
    logo   LONGTEXT     NOT NULL
) CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS pl_proveedores (
    nombre VARCHAR(80) NOT NULL PRIMARY KEY
) CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS pl_equipos (
    id                VARCHAR(64)  NOT NULL PRIMARY KEY,
    posicion          INT          NOT NULL,
    categoria         VARCHAR(40)  NOT NULL,
    partida           VARCHAR(40)  NOT NULL,
    marca             VARCHAR(255) NOT NULL DEFAULT '',
    modelo            VARCHAR(255) NOT NULL DEFAULT '',
    proveedor         VARCHAR(255) NOT NULL DEFAULT '',
    unidad            VARCHAR(40)  NOT NULL DEFAULT 'ud',
    precio_unitario   DOUBLE       NOT NULL DEFAULT 0,
    por_modulo        TINYINT(1)   NOT NULL DEFAULT 0,
    cantidad_defecto  DOUBLE       NOT NULL DEFAULT 1,
    atributos         JSON         NOT NULL,
    notas             TEXT         NOT NULL,
    muestra           TINYINT(1)   NOT NULL DEFAULT 0,
    stock             DOUBLE       NOT NULL DEFAULT 0,
    stock_minimo      DOUBLE       NOT NULL DEFAULT 0,
    ficha_tecnica     VARCHAR(500) NOT NULL DEFAULT '',
    ofertas           JSON         NOT NULL,
    precio_revisado_el DATE        NULL,
    INDEX idx_pl_equipos_posicion (posicion),
    INDEX idx_pl_equipos_categoria (categoria)
) CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS pl_historial_precios (
    id        VARCHAR(64)  NOT NULL PRIMARY KEY,
    equipo_id VARCHAR(64)  NOT NULL,
    precio    DOUBLE       NOT NULL DEFAULT 0,
    proveedor VARCHAR(255) NOT NULL DEFAULT '',
    ofertas   JSON         NOT NULL,
    por       VARCHAR(255) NOT NULL DEFAULT '',
    fecha     DATETIME(6)  NOT NULL,
    INDEX idx_pl_historial_equipo (equipo_id),
    INDEX idx_pl_historial_fecha (fecha)
) CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS pl_meta (
    clave VARCHAR(100) NOT NULL PRIMARY KEY,
    valor VARCHAR(500) NULL
) CHARACTER SET utf8mb4;
