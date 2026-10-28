-- ============================================================
-- MCAS - Módulo de Creación Automática de Servicios
-- V20260913_1_Create_MCAS_Module.sql   (sustituye a V20260731_1)
--
-- CÓMO EJECUTARLO
--   1. Ejecutar este script completo contra la BBDD de Aura (MariaDB).
--   2. Después, V20260929_1_MCAS_Conformidad.sql y V20260930_1_MCAS_Usabilidad.sql.
--   Solo esquema: no carga tablas de comisiones, reglas, productos ni
--   políticas. El módulo arranca vacío y el administrador da de alta cada
--   comercializadora desde Configuración MCAS.
--   Es idempotente: se puede relanzar sin riesgo (IF NOT EXISTS en todo),
--   también si un intento anterior se quedó a medias.
--
-- IMPORTANTE: ejecutarlo ANTES de desplegar el backend con MCAS. La entidad
-- Contrato ya mapea las columnas mcas_*; sin ellas, todas las pantallas de
-- contratos devuelven error 500.
--
-- Nomenclatura: tablas en minúsculas y snake_case (contratos, servicios,
-- tbcomercializadoras, tbgestoras...), como las crea Hibernate en Aura.
-- MariaDB en Linux distingue mayúsculas en los nombres de tabla.
-- ============================================================

-- -----------------------------------------------
-- 1. Nuevos campos MCAS en contratos (todos opcionales)
-- -----------------------------------------------
ALTER TABLE contratos
    ADD COLUMN IF NOT EXISTS mcas_fee_eur_mwh      DECIMAL(18,6) NULL         COMMENT 'FEE manual introducido (EUR/MWh u otra unidad)',
    ADD COLUMN IF NOT EXISTS mcas_fee_unidad        VARCHAR(20)   NULL DEFAULT 'EUR_MWH',
    ADD COLUMN IF NOT EXISTS mcas_tipo_precio       VARCHAR(10)   NULL         COMMENT 'FIJO o INDEXADO',
    ADD COLUMN IF NOT EXISTS mcas_tipo_suministro   VARCHAR(5)    NULL         COMMENT 'LUZ o GAS',
    ADD COLUMN IF NOT EXISTS mcas_segmento          VARCHAR(20)   NULL         COMMENT 'RESIDENCIAL o PYME',
    ADD COLUMN IF NOT EXISTS mcas_es_renovacion     TINYINT(1)    NULL,
    ADD COLUMN IF NOT EXISTS mcas_tipo_renovacion   VARCHAR(20)   NULL         COMMENT 'FIRMADA, TACITA, OTRA',
    ADD COLUMN IF NOT EXISTS mcas_ambito            VARCHAR(10)   NULL         COMMENT 'DENTRO, FUERA, NA',
    ADD COLUMN IF NOT EXISTS mcas_id_gestora        INT           NULL,
    ADD COLUMN IF NOT EXISTS mcas_id_producto_comercial INT       NULL,
    ADD COLUMN IF NOT EXISTS mcas_zona_comision     VARCHAR(50)   NULL,
    ADD COLUMN IF NOT EXISTS mcas_potencia_calculo  DECIMAL(18,6) NULL         COMMENT 'kW para el cálculo de comisión',
    ADD COLUMN IF NOT EXISTS mcas_numero_pagos      INT           NULL         COMMENT 'Introducido por el usuario; prioridad sobre la regla',
    ADD COLUMN IF NOT EXISTS mcas_fecha_primera_aplicacion DATE   NULL,
    ADD COLUMN IF NOT EXISTS mcas_alta_nueva        TINYINT(1)    NULL,
    ADD COLUMN IF NOT EXISTS mcas_fecha_ultima_relacion_comercializadora DATE NULL;

-- -----------------------------------------------
-- 2. Productos comerciales por comercializadora
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS mcas_productos_comerciales (
    id                  INT           AUTO_INCREMENT PRIMARY KEY,
    id_comercializadora INT           NOT NULL,
    id_gestora          INT           NULL,
    codigo              VARCHAR(100)  NOT NULL                   COMMENT 'Usado en condiciones_json de reglas',
    nombre              VARCHAR(250)  NOT NULL,
    tipo_precio         VARCHAR(10)   NOT NULL                   COMMENT 'FIJO o INDEXADO',
    tipo_suministro     VARCHAR(5)    NOT NULL                   COMMENT 'LUZ o GAS',
    codigo_zona         VARCHAR(50)   NULL,
    codigo_go           VARCHAR(50)   NULL,
    activo_desde        DATE          NULL,
    activo_hasta        DATE          NULL,
    activo              TINYINT(1)    NOT NULL DEFAULT 1,
    CONSTRAINT uq_mpc_codigo UNIQUE (id_comercializadora, codigo),
    CONSTRAINT fk_mpc_com FOREIGN KEY (id_comercializadora) REFERENCES tbcomercializadoras(id),
    CONSTRAINT fk_mpc_ges FOREIGN KEY (id_gestora)          REFERENCES tbgestoras(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_spanish_ci;

CREATE INDEX IF NOT EXISTS idx_mpc_com ON mcas_productos_comerciales(id_comercializadora, tipo_precio, tipo_suministro);

-- -----------------------------------------------
-- 3. Conjuntos de reglas (versionados)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS mcas_conjuntos_reglas (
    id                   INT           AUTO_INCREMENT PRIMARY KEY,
    codigo               VARCHAR(100)  NOT NULL UNIQUE,
    version              VARCHAR(50)   NOT NULL DEFAULT '1.0',
    id_comercializadora  INT           NOT NULL,
    id_gestora           INT           NULL,
    fase_precio          VARCHAR(10)   NOT NULL DEFAULT 'FIJO'     COMMENT 'FIJO, INDEXADO, AMBOS',
    vigencia_desde       DATE          NOT NULL,
    vigencia_hasta       DATE          NULL,
    estado               VARCHAR(20)   NOT NULL DEFAULT 'BORRADOR' COMMENT 'BORRADOR, VALIDADO, PUBLICADO, ARCHIVADO',
    archivo_origen       VARCHAR(250)  NULL,
    hoja_origen          VARCHAR(100)  NULL,
    fecha_interna_origen DATE          NULL,
    hash_origen          VARCHAR(64)   NULL,
    creado_por           VARCHAR(250)  NULL,
    aprobado_por         VARCHAR(250)  NULL,
    fecha_creacion       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_aprobacion     DATETIME      NULL,
    CONSTRAINT fk_mcr_com FOREIGN KEY (id_comercializadora) REFERENCES tbcomercializadoras(id),
    CONSTRAINT fk_mcr_ges FOREIGN KEY (id_gestora)          REFERENCES tbgestoras(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_spanish_ci;

CREATE INDEX IF NOT EXISTS idx_mcr_com ON mcas_conjuntos_reglas(id_comercializadora, vigencia_desde, estado);

-- -----------------------------------------------
-- 4. Reglas individuales de comisión
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS mcas_reglas_comision (
    id                 INT           AUTO_INCREMENT PRIMARY KEY,
    id_conjunto_reglas INT           NOT NULL,
    codigo             VARCHAR(100)  NOT NULL,
    prioridad          INT           NOT NULL DEFAULT 100,
    metodo_calculo     VARCHAR(50)   NOT NULL
        COMMENT 'FIXED_AMOUNT|RANGE_LOOKUP|MATRIX_LOOKUP|MULTI_DIMENSION_LOOKUP|RATE_PER_MWH|FEE_PERCENTAGE|COMPOSITE_SUM|MANUAL|NOT_COMMISSIONABLE',
    condiciones_json   TEXT          NULL     COMMENT 'JSON con condiciones de selección',
    calculo_json       TEXT          NULL     COMMENT 'JSON con parámetros del método',
    politica_pago_json TEXT          NULL     COMMENT 'JSON con sugerencia de pagos',
    motivo_manual      VARCHAR(500)  NULL,
    localizador_origen VARCHAR(250)  NULL,
    activo             TINYINT(1)    NOT NULL DEFAULT 1,
    CONSTRAINT uq_mrc_codigo UNIQUE (id_conjunto_reglas, codigo),
    CONSTRAINT fk_mrc_con FOREIGN KEY (id_conjunto_reglas) REFERENCES mcas_conjuntos_reglas(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_spanish_ci;

CREATE INDEX IF NOT EXISTS idx_mrc_con ON mcas_reglas_comision(id_conjunto_reglas, prioridad, activo);

-- -----------------------------------------------
-- 5. Valores de rango (RANGE_LOOKUP / MATRIX_LOOKUP / MULTI_DIMENSION_LOOKUP)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS mcas_valores_rango (
    id               INT           AUTO_INCREMENT PRIMARY KEY,
    id_regla         INT           NOT NULL,
    dimension        VARCHAR(50)   NOT NULL COMMENT 'consumo_kwh|consumo_mwh|potencia_kw|tarifa_acceso|codigo_producto|segmento|tipo_suministro|zona_comision',
    valor_min        DECIMAL(18,6) NULL,
    valor_max        DECIMAL(18,6) NULL,
    min_inclusivo    TINYINT(1)    NOT NULL DEFAULT 1,
    max_inclusivo    TINYINT(1)    NOT NULL DEFAULT 0,
    valor_categoria  VARCHAR(100)  NULL     COMMENT 'Para dimensiones de texto',
    valor_resultado  DECIMAL(18,6) NOT NULL,
    unidad_resultado VARCHAR(20)   NULL,
    orden            INT           NOT NULL DEFAULT 1,
    CONSTRAINT fk_mvr_reg FOREIGN KEY (id_regla) REFERENCES mcas_reglas_comision(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_spanish_ci;

CREATE INDEX IF NOT EXISTS idx_mvr_reg ON mcas_valores_rango(id_regla, dimension);

-- -----------------------------------------------
-- 6. Políticas de retrocesión (informativas: «retrocesión potencial»)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS mcas_politicas_retrocesion (
    id                    INT           AUTO_INCREMENT PRIMARY KEY,
    id_comercializadora   INT           NOT NULL,
    id_gestora            INT           NULL,
    version               VARCHAR(50)   NOT NULL DEFAULT '1.0',
    vigencia_desde        DATE          NOT NULL,
    vigencia_hasta        DATE          NULL,
    tipo_suministro       VARCHAR(5)    NULL,
    segmento              VARCHAR(20)   NULL,
    tarifa_acceso         VARCHAR(20)   NULL,
    metodo                VARCHAR(30)   NOT NULL COMMENT 'PORCENTAJE|FIJO|PRORRATEO|NO_APLICA',
    limite_importe_pagado TINYINT(1)    NOT NULL DEFAULT 1,
    archivo_origen        VARCHAR(250)  NULL,
    estado                VARCHAR(20)   NOT NULL DEFAULT 'BORRADOR',
    CONSTRAINT fk_mpr_com FOREIGN KEY (id_comercializadora) REFERENCES tbcomercializadoras(id),
    CONSTRAINT fk_mpr_ges FOREIGN KEY (id_gestora)          REFERENCES tbgestoras(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_spanish_ci;

CREATE TABLE IF NOT EXISTS mcas_tramos_retrocesion (
    id              INT           AUTO_INCREMENT PRIMARY KEY,
    id_politica     INT           NOT NULL,
    unidad          VARCHAR(10)   NOT NULL COMMENT 'DIAS o MESES',
    desde_valor     INT           NOT NULL,
    hasta_valor     INT           NULL,
    desde_inclusivo TINYINT(1)    NOT NULL DEFAULT 1,
    hasta_inclusivo TINYINT(1)    NOT NULL DEFAULT 0,
    valor           DECIMAL(18,6) NOT NULL,
    tipo_valor      VARCHAR(20)   NOT NULL COMMENT 'PORCENTAJE|IMPORTE_FIJO|FACTOR',
    prioridad       INT           NOT NULL DEFAULT 100,
    CONSTRAINT fk_mtr_pol FOREIGN KEY (id_politica) REFERENCES mcas_politicas_retrocesion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_spanish_ci;

-- -----------------------------------------------
-- 7. Propuestas MCAS
--    Al borrar un contrato se borran sus propuestas (y en cascada sus líneas
--    y calendario); al borrar un servicio, la propuesta conserva su historia y
--    solo pierde el enlace. La auditoría (tabla 10) no tiene claves foráneas y
--    se conserva siempre.
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS mcas_propuestas (
    id                       BIGINT        AUTO_INCREMENT PRIMARY KEY,
    id_contrato              INT           NOT NULL,
    hash_contrato            VARCHAR(64)   NULL,
    tipo_evento              VARCHAR(50)   NOT NULL   COMMENT 'NUEVO_CONTRATO|RENOVACION',
    clave_idempotencia       VARCHAR(190)  NOT NULL   COMMENT 'Único; <=190 para no superar el límite de índice',
    estado                   VARCHAR(30)   NOT NULL DEFAULT 'PENDIENTE_CALCULO',
    id_conjunto_reglas       INT           NULL,
    id_politica_retrocesion  INT           NULL,
    instantanea_json         MEDIUMTEXT    NULL       COMMENT 'Fotografía inmutable de los datos al calcular',
    datos_normalizados_json  TEXT          NULL,
    traza_calculo_json       MEDIUMTEXT    NULL,
    importe_calculado        DECIMAL(18,6) NULL       COMMENT 'Resultado automático; nunca se sobrescribe',
    importe_definitivo       DECIMAL(18,6) NULL,
    numero_pagos             INT           NULL,
    fecha_primera_aplicacion DATE          NULL,
    motivo_rechazo           TEXT          NULL,
    datos_editados_json      TEXT          NULL,
    advertencias_json        TEXT          NULL,
    id_servicio_creado       INT           NULL,
    creado_por               VARCHAR(250)  NULL,
    creado_en                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por          VARCHAR(250)  NULL,
    actualizado_en           DATETIME      NULL,
    aprobado_por             VARCHAR(250)  NULL,
    aprobado_en              DATETIME      NULL,
    version_optimista        INT           NOT NULL DEFAULT 0,
    CONSTRAINT uq_mp_clave UNIQUE (clave_idempotencia),
    CONSTRAINT fk_mp_con FOREIGN KEY (id_contrato)             REFERENCES contratos(id)                  ON DELETE CASCADE,
    CONSTRAINT fk_mp_cr  FOREIGN KEY (id_conjunto_reglas)      REFERENCES mcas_conjuntos_reglas(id),
    CONSTRAINT fk_mp_pr  FOREIGN KEY (id_politica_retrocesion) REFERENCES mcas_politicas_retrocesion(id),
    CONSTRAINT fk_mp_sv  FOREIGN KEY (id_servicio_creado)      REFERENCES servicios(id)                  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_spanish_ci;

CREATE INDEX IF NOT EXISTS idx_mp_con ON mcas_propuestas(id_contrato, estado);
CREATE INDEX IF NOT EXISTS idx_mp_est ON mcas_propuestas(estado, creado_en);

-- -----------------------------------------------
-- 8. Líneas de desglose
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS mcas_lineas_propuesta (
    id                 BIGINT        AUTO_INCREMENT PRIMARY KEY,
    id_propuesta       BIGINT        NOT NULL,
    tipo_linea         VARCHAR(50)   NOT NULL,
    descripcion        VARCHAR(500)  NULL,
    metodo             VARCHAR(50)   NULL,
    valor_base         DECIMAL(18,6) NULL,
    valor_tasa         DECIMAL(18,6) NULL,
    valor_porcentaje   DECIMAL(18,6) NULL,
    importe_calculado  DECIMAL(18,6) NOT NULL,
    importe_definitivo DECIMAL(18,6) NULL,
    id_regla           INT           NULL,
    orden              INT           NOT NULL DEFAULT 1,
    CONSTRAINT fk_mlp_pro FOREIGN KEY (id_propuesta) REFERENCES mcas_propuestas(id) ON DELETE CASCADE,
    CONSTRAINT fk_mlp_reg FOREIGN KEY (id_regla)     REFERENCES mcas_reglas_comision(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_spanish_ci;

-- -----------------------------------------------
-- 9. Calendario de pagos
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS mcas_calendario_pagos (
    id           BIGINT        AUTO_INCREMENT PRIMARY KEY,
    id_propuesta BIGINT        NOT NULL,
    numero_pago  INT           NOT NULL,
    fecha_pago   DATE          NULL,
    importe      DECIMAL(18,6) NOT NULL,
    estado       VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    naturaleza   VARCHAR(20)   NOT NULL DEFAULT 'DEFINITIVO',
    CONSTRAINT fk_mcp_pro FOREIGN KEY (id_propuesta) REFERENCES mcas_propuestas(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_spanish_ci;

-- -----------------------------------------------
-- 10. Auditoría inmutable (sin claves foráneas a propósito)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS mcas_auditoria (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    id_propuesta    BIGINT        NULL,
    id_contrato     INT           NULL,
    tipo_evento     VARCHAR(100)  NOT NULL,
    estado_anterior VARCHAR(30)   NULL,
    estado_nuevo    VARCHAR(30)   NULL,
    datos_json      MEDIUMTEXT    NULL,
    usuario         VARCHAR(250)  NULL,
    fecha_evento    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_correlacion  VARCHAR(100)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_spanish_ci;

CREATE INDEX IF NOT EXISTS idx_ma_pro ON mcas_auditoria(id_propuesta);
CREATE INDEX IF NOT EXISTS idx_ma_con ON mcas_auditoria(id_contrato);
CREATE INDEX IF NOT EXISTS idx_ma_fec ON mcas_auditoria(fecha_evento);

-- -----------------------------------------------
-- 11. Interruptores (MCAS desactivado por defecto; aquí nunca se encienden)
-- -----------------------------------------------
INSERT INTO configuracion_modulos (codigo, activo, descripcion) VALUES
    ('MCAS_ENABLED',              0, 'MCAS - Modulo habilitado'),
    ('MCAS_FIXED_ENABLED',        0, 'MCAS - Fase precio fijo'),
    ('MCAS_INDEXED_ENABLED',      0, 'MCAS - Fase precio indexado (NO activar hasta validar fijos)'),
    ('MCAS_RETROCESSION_ENABLED', 0, 'MCAS - Fase retrocesiones (NO activar hasta validar fijos)')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- -----------------------------------------------
-- 12. Comprobación: debe listar 10 tablas mcas_* y 16 columnas mcas_* en contratos
-- -----------------------------------------------
SELECT 'tablas_mcas' AS comprobacion, COUNT(*) AS total
  FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name LIKE 'mcas\_%'
UNION ALL
SELECT 'columnas_mcas_en_contratos', COUNT(*)
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'contratos' AND column_name LIKE 'mcas\_%'
UNION ALL
SELECT 'indicadores_mcas', COUNT(*)
  FROM configuracion_modulos
 WHERE codigo LIKE 'MCAS\_%';
