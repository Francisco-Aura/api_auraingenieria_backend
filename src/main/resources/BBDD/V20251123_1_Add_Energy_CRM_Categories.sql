-- Adición de categorías y subcategorías para CRM de Energía

-- Nuevas categorías
INSERT INTO modulotareas_categorias (nombre, codigo, descripcion, activo)
VALUES
    ('Cliente', 'CLI', 'Gestión de clientes y sus datos', 1),
    ('Contrato', 'CON', 'Administración de contratos de suministro', 1),
    ('Servicio', 'SER', 'Gestión de servicios contratados', 1),
    ('Punto de Suministro', 'PSU', 'Manejo de puntos de suministro (CUPS)', 1),
    ('Facturación', 'FAC', 'Procesos de facturación y cobros', 1),
    ('Incidencia', 'INC', 'Gestión de incidencias y problemas', 1),
    ('Instalación', 'INS', 'Actividades relacionadas con instalaciones', 1)
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion), activo = VALUES(activo);

-- Nuevas subcategorías
INSERT INTO modulotareas_subcategorias (nombre, codigo, activo)
VALUES
    -- Cliente
    ('Alta cliente', 'CLI_ALT', 1),
    ('Modificación datos cliente', 'CLI_MOD', 1),
    ('Baja cliente', 'CLI_BAJ', 1),
    ('Consulta cliente', 'CLI_CON', 1),
    ('Reclamación cliente', 'CLI_REC', 1),
    -- Contrato
    ('Nuevo contrato', 'CON_NUE', 1),
    ('Renovación contrato', 'CON_REN', 1),
    ('Modificación contrato', 'CON_MOD', 1),
    ('Cancelación contrato', 'CON_CAN', 1),
    ('Revisión contrato', 'CON_REV', 1),
    -- Servicio
    ('Alta servicio', 'SER_ALT', 1),
    ('Modificación servicio', 'SER_MOD', 1),
    ('Baja servicio', 'SER_BAJ', 1),
    ('Incidencia servicio', 'SER_INC', 1),
    ('Consulta servicio', 'SER_CON', 1),
    -- Punto de Suministro
    ('Alta CUPS', 'PSU_ALT', 1),
    ('Modificación CUPS', 'PSU_MOD', 1),
    ('Baja CUPS', 'PSU_BAJ', 1),
    ('Lectura contador', 'PSU_LEC', 1),
    ('Inspección punto suministro', 'PSU_INS', 1),
    -- Facturación
    ('Emisión factura', 'FAC_EMI', 1),
    ('Corrección factura', 'FAC_COR', 1),
    ('Pago pendiente', 'FAC_PAG', 1),
    ('Reclamación factura', 'FAC_REC', 1),
    ('Consulta facturación', 'FAC_CON', 1),
    -- Incidencia
    ('Reporte incidencia', 'INC_REP', 1),
    ('Seguimiento incidencia', 'INC_SEG', 1),
    ('Resolución incidencia', 'INC_RES', 1),
    ('Escalado incidencia', 'INC_ESC', 1),
    -- Instalación
    ('Nueva instalación', 'INS_NUE', 1),
    ('Mantenimiento instalación', 'INS_MAN', 1),
    ('Desinstalación', 'INS_DES', 1),
    ('Inspección instalación', 'INS_INS', 1)
ON DUPLICATE KEY UPDATE activo = VALUES(activo);

-- Relaciones categoría-subcategoría
INSERT INTO modulotareas_categorias_subcategorias (categoria_id, subcategoria_id)
SELECT c.id, s.id
FROM modulotareas_categorias c
JOIN modulotareas_subcategorias s ON (
    (c.nombre = 'Cliente' AND s.nombre IN ('Alta cliente', 'Modificación datos cliente', 'Baja cliente', 'Consulta cliente', 'Reclamación cliente'))
 OR (c.nombre = 'Contrato' AND s.nombre IN ('Nuevo contrato', 'Renovación contrato', 'Modificación contrato', 'Cancelación contrato', 'Revisión contrato'))
 OR (c.nombre = 'Servicio' AND s.nombre IN ('Alta servicio', 'Modificación servicio', 'Baja servicio', 'Incidencia servicio', 'Consulta servicio'))
 OR (c.nombre = 'Punto de Suministro' AND s.nombre IN ('Alta CUPS', 'Modificación CUPS', 'Baja CUPS', 'Lectura contador', 'Inspección punto suministro'))
 OR (c.nombre = 'Facturación' AND s.nombre IN ('Emisión factura', 'Corrección factura', 'Pago pendiente', 'Reclamación factura', 'Consulta facturación'))
 OR (c.nombre = 'Incidencia' AND s.nombre IN ('Reporte incidencia', 'Seguimiento incidencia', 'Resolución incidencia', 'Escalado incidencia'))
 OR (c.nombre = 'Instalación' AND s.nombre IN ('Nueva instalación', 'Mantenimiento instalación', 'Desinstalación', 'Inspección instalación'))
)
ON DUPLICATE KEY UPDATE categoria_id = categoria_id;

-- Estados por defecto para módulo de tareas (asegurando que existan)
INSERT INTO modulotareas_estados (nombre, color_hex, estado_final, orden, cuenta_tiempo, es_bloqueo)
VALUES 
    ('Pendiente', '#F39C12', 0, 1, 0, 0),
    ('En Progreso', '#2980B9', 0, 2, 1, 0),
    ('Bloqueada', '#C0392B', 0, 3, 0, 1),
    ('Completada', '#27AE60', 1, 4, 0, 0)
ON DUPLICATE KEY UPDATE 
    color_hex = VALUES(color_hex), 
    estado_final = VALUES(estado_final), 
    orden = VALUES(orden),
    cuenta_tiempo = VALUES(cuenta_tiempo),
    es_bloqueo = VALUES(es_bloqueo);