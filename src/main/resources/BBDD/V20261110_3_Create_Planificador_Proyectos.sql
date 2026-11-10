-- Esquema de proyectos y tareas del Planificador (Fase 4). MariaDB 10.11. Se aplica a mano.

CREATE TABLE IF NOT EXISTS pl_proyectos (
    id                    VARCHAR(64)  NOT NULL PRIMARY KEY,
    propietario_id        VARCHAR(64)  NOT NULL DEFAULT 'local',
    nombre                VARCHAR(255) NOT NULL,
    descripcion           TEXT         NOT NULL,
    fecha_inicio          DATE         NULL,
    documentos            JSON         NOT NULL,
    linea_base            JSON         NOT NULL,
    cambios               JSON         NOT NULL,
    presupuesto           JSON         NOT NULL,
    partes_contrato       JSON         NOT NULL,
    contrato_generado_el  DATETIME(6)  NULL,
    archivo_contrato      VARCHAR(500) NOT NULL DEFAULT '',
    plan_pagos            JSON         NOT NULL,
    creado_el             DATETIME(6)  NOT NULL,
    ruta_dropbox          VARCHAR(500) NOT NULL DEFAULT '',
    eliminado_el          DATETIME(6)  NULL,
    INDEX idx_pl_proyectos_propietario (propietario_id)
) CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS pl_tareas_proyecto (
    id                 VARCHAR(64)  NOT NULL PRIMARY KEY,
    proyecto_id        VARCHAR(64)  NOT NULL,
    nombre             VARCHAR(255) NOT NULL,
    descripcion        TEXT         NOT NULL,
    fase               VARCHAR(255) NOT NULL DEFAULT '',
    departamento       VARCHAR(255) NOT NULL DEFAULT '',
    posicion           INT          NOT NULL DEFAULT 0,
    hito               TINYINT(1)   NOT NULL DEFAULT 0,
    inicio             DATE         NOT NULL,
    fin                DATE         NOT NULL,
    fin_previsto       DATE         NULL,
    progreso           INT          NOT NULL DEFAULT 0,
    estado             VARCHAR(20)  NOT NULL DEFAULT 'todo',
    motivo_estado      VARCHAR(255) NOT NULL DEFAULT '',
    nota_estado        TEXT         NOT NULL,
    completada_el      DATE         NULL,
    nota_cierre        TEXT         NOT NULL,
    entregables        JSON         NOT NULL,
    adjuntos           JSON         NOT NULL,
    usa                JSON         NOT NULL,
    cambios_revisados  JSON         NOT NULL,
    etapa_pago         INT          NULL,
    importe_pago       DOUBLE       NULL,
    etiqueta_pago      VARCHAR(255) NOT NULL DEFAULT '',
    carpeta_dropbox    VARCHAR(255) NOT NULL DEFAULT '',
    avisadas           JSON         NOT NULL,
    marcas_vencimiento JSON         NOT NULL,
    depende_de         JSON         NOT NULL,
    lista              JSON         NOT NULL,
    tipo               VARCHAR(40)  NOT NULL DEFAULT '',
    creado_el          DATETIME(6)  NOT NULL,
    INDEX idx_pl_tareas_proyecto (proyecto_id),
    CONSTRAINT fk_pl_tareas_proyecto FOREIGN KEY (proyecto_id) REFERENCES pl_proyectos (id)
) CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS pl_actividad (
    id           VARCHAR(64)  NOT NULL PRIMARY KEY,
    proyecto_id  VARCHAR(64)  NOT NULL,
    tarea_id     VARCHAR(64)  NULL,           -- sin clave foránea: el registro sobrevive a la tarea eliminada
    nombre_tarea VARCHAR(255) NOT NULL DEFAULT '',
    fecha        DATETIME(6)  NOT NULL,
    actor        VARCHAR(255) NOT NULL DEFAULT '',
    tipo         VARCHAR(30)  NOT NULL DEFAULT '',
    resumen      TEXT         NOT NULL,
    detalle      TEXT         NOT NULL,
    INDEX idx_pl_actividad_proyecto (proyecto_id),
    INDEX idx_pl_actividad_fecha (fecha)
) CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS pl_borradores (
    id              VARCHAR(64)  NOT NULL PRIMARY KEY,
    nombre          VARCHAR(255) NOT NULL DEFAULT '',
    datos           JSON         NOT NULL,
    actualizado_el  DATETIME(6)  NOT NULL,
    actualizado_por VARCHAR(255) NOT NULL DEFAULT '',
    INDEX idx_pl_borradores_actualizado (actualizado_el)
) CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS pl_devoluciones_material (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    proyecto_id VARCHAR(64)  NOT NULL,
    equipo_id   VARCHAR(64)  NOT NULL,
    etiqueta    VARCHAR(255) NOT NULL DEFAULT '',
    unidad      VARCHAR(40)  NOT NULL DEFAULT 'ud',
    cantidad    DOUBLE       NOT NULL,
    nota        VARCHAR(300) NOT NULL DEFAULT '',
    por         VARCHAR(255) NOT NULL DEFAULT '',
    fecha       DATETIME(6)  NOT NULL,
    INDEX idx_pl_devoluciones_proyecto (proyecto_id),
    INDEX idx_pl_devoluciones_equipo (equipo_id)
) CHARACTER SET utf8mb4;
