-- Creación de tablas para el módulo de tareas corporativo

CREATE TABLE IF NOT EXISTS configuracion_modulos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(100) NOT NULL UNIQUE,
    activo TINYINT(1) NOT NULL DEFAULT 0,
    descripcion VARCHAR(255)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS modulotareas_estados (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    color_hex VARCHAR(7),
    estado_final TINYINT(1) NOT NULL DEFAULT 0,
    orden INT NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS modulotareas_categorias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    codigo VARCHAR(10) NOT NULL UNIQUE,
    descripcion VARCHAR(255),
    activo TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS modulotareas_subcategorias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    codigo VARCHAR(10),
    activo TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS modulotareas_categorias_subcategorias (
    categoria_id BIGINT NOT NULL,
    subcategoria_id BIGINT NOT NULL,
    PRIMARY KEY (categoria_id, subcategoria_id),
    CONSTRAINT fk_cat_sub_cat FOREIGN KEY (categoria_id) REFERENCES modulotareas_categorias(id),
    CONSTRAINT fk_cat_sub_sub FOREIGN KEY (subcategoria_id) REFERENCES modulotareas_subcategorias(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS modulotareas_tareas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    tipo VARCHAR(50),
    prioridad INT NOT NULL DEFAULT 2,
    notificar TINYINT(1) NOT NULL DEFAULT 0,
    enviar_correo TINYINT(1) NOT NULL DEFAULT 0,
    repetir VARCHAR(20),
    fecha_inicio DATETIME,
    fecha_vencimiento DATETIME,
    fecha_finalizacion DATETIME,
    fecha_creacion DATETIME NOT NULL,
    fecha_actualizacion DATETIME,
    usuario_asignado_id INT,
    usuario_creador_id INT,
    cliente_id INT,
    estado_id BIGINT,
    categoria_id BIGINT,
    subcategoria_id BIGINT,
    CONSTRAINT fk_tarea_usuario_asignado FOREIGN KEY (usuario_asignado_id) REFERENCES Usuarios(id),
    CONSTRAINT fk_tarea_usuario_creador FOREIGN KEY (usuario_creador_id) REFERENCES Usuarios(id),
    CONSTRAINT fk_tarea_cliente FOREIGN KEY (cliente_id) REFERENCES Clientes(id),
    CONSTRAINT fk_tarea_estado FOREIGN KEY (estado_id) REFERENCES modulotareas_estados(id),
    CONSTRAINT fk_tarea_categoria FOREIGN KEY (categoria_id) REFERENCES modulotareas_categorias(id),
    CONSTRAINT fk_tarea_subcategoria FOREIGN KEY (subcategoria_id) REFERENCES modulotareas_subcategorias(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS modulotareas_comentarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tarea_id BIGINT NOT NULL,
    usuario_id INT NOT NULL,
    comentario TEXT NOT NULL,
    fecha_creacion DATETIME NOT NULL,
    CONSTRAINT fk_comentario_tarea FOREIGN KEY (tarea_id) REFERENCES modulotareas_tareas(id),
    CONSTRAINT fk_comentario_usuario FOREIGN KEY (usuario_id) REFERENCES Usuarios(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS modulotareas_notificaciones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tarea_id BIGINT NOT NULL,
    usuario_id INT NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    mensaje VARCHAR(255) NOT NULL,
    leida TINYINT(1) NOT NULL DEFAULT 0,
    fecha_creacion DATETIME NOT NULL,
    CONSTRAINT fk_notif_tarea FOREIGN KEY (tarea_id) REFERENCES modulotareas_tareas(id),
    CONSTRAINT fk_notif_usuario FOREIGN KEY (usuario_id) REFERENCES Usuarios(id)
) ENGINE=InnoDB;

-- Estados por defecto
INSERT INTO modulotareas_estados (nombre, color_hex, estado_final, orden)
VALUES 
    ('Pendiente', '#F39C12', 0, 1),
    ('En Progreso', '#2980B9', 0, 2),
    ('Bloqueada', '#C0392B', 0, 3),
    ('Completada', '#27AE60', 1, 4)
ON DUPLICATE KEY UPDATE color_hex = VALUES(color_hex), estado_final = VALUES(estado_final), orden = VALUES(orden);

-- Categorías y subcategorías por defecto
INSERT INTO modulotareas_categorias (nombre, codigo, descripcion, activo)
VALUES
    ('Operaciones', 'OPR', 'Gestión diaria de clientes y contratos', 1),
    ('Comunicaciones', 'COM', 'Seguimiento de comunicaciones entrantes/salientes', 1),
    ('Administración', 'ADM', 'Tareas administrativas internas', 1),
    ('Mantenimiento', 'MTO', 'Actividades de mantenimiento o incidencias técnicas', 1)
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion), activo = VALUES(activo);

INSERT INTO modulotareas_subcategorias (nombre, codigo, activo)
VALUES
    ('Contactar cliente', 'CNTCL', 1),
    ('Contrato pendiente', 'CTOPD', 1),
    ('Documentación', 'DOC', 1),
    ('Revisión técnica', 'TEC', 1),
    ('Visita programada', 'VIS', 1),
    ('Reclamación', 'REC', 1)
ON DUPLICATE KEY UPDATE activo = VALUES(activo);

INSERT INTO modulotareas_categorias_subcategorias (categoria_id, subcategoria_id)
SELECT c.id, s.id
FROM modulotareas_categorias c
JOIN modulotareas_subcategorias s ON (
        (c.codigo = 'OPR' AND s.codigo IN ('CNTCL', 'CTOPD', 'DOC'))
     OR (c.codigo = 'COM' AND s.codigo IN ('CNTCL', 'REC'))
     OR (c.codigo = 'ADM' AND s.codigo IN ('DOC'))
     OR (c.codigo = 'MTO' AND s.codigo IN ('TEC', 'VIS'))
)
ON DUPLICATE KEY UPDATE categoria_id = categoria_id;

-- Activar módulo Gestor de Tareas por defecto
INSERT INTO configuracion_modulos (codigo, activo, descripcion)
VALUES ('GESTOR_TAREAS', 1, 'Módulo de gestión de tareas corporativo')
ON DUPLICATE KEY UPDATE activo = VALUES(activo);
