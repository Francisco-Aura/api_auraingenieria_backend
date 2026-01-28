-- =====================================================
-- Script para insertar Categorías y Subcategorías
-- del Módulo de Tareas según especificación del cliente
-- Fecha: 2026-01-19
-- =====================================================

-- NOTA: Este script usa ON DUPLICATE KEY UPDATE para evitar duplicados
-- y obtiene los IDs dinámicamente para las relaciones.

-- =====================================================
-- 1. LIMPIAR DATOS EXISTENTES 
-- =====================================================
-- DELETE FROM modulotareas_categorias_subcategorias;
-- DELETE FROM modulotareas_subcategorias;
-- DELETE FROM modulotareas_categorias WHERE codigo IN ('GE', 'FV', 'TM', 'GEN');

-- =====================================================
-- 2. INSERTAR CATEGORÍAS
-- =====================================================
INSERT INTO modulotareas_categorias (nombre, codigo, descripcion, activo)
VALUES
    ('Gestión Energética', 'GE', 'Gestión de contratos y servicios energéticos', 1),
    ('Fotovoltaicas', 'FV', 'Instalaciones y proyectos fotovoltaicos', 1),
    ('Telemedida', 'TM', 'Sistemas de telemedida y monitorización', 1),
    ('Generales', 'GEN', 'Tareas administrativas y generales', 1)
ON DUPLICATE KEY UPDATE 
    nombre = VALUES(nombre),
    descripcion = VALUES(descripcion), 
    activo = VALUES(activo);

-- =====================================================
-- 3. INSERTAR SUBCATEGORÍAS
-- =====================================================
INSERT INTO modulotareas_subcategorias (nombre, codigo, activo)
VALUES
    ('Estudios Comercializados', 'EST_COM', 1),
    ('Switching', 'SWITCH', 1),
    ('Incidencias', 'INCID', 1),
    ('Llamadas Telefónicas', 'LLAM', 1),
    ('Estudios Potencia', 'EST_POT', 1),
    ('Revisión Facturas', 'REV_FAC', 1),
    ('Revisión Contrato', 'REV_CTO', 1),
    ('Revisión Informes', 'REV_INF', 1),
    ('Revisión Alarmas', 'REV_ALM', 1),
    ('Tareas Varias', 'TAR_VAR', 1),
    ('Modificación Potencia', 'MOD_POT', 1),
    ('Gestión Expediente Distribuidora', 'GEST_EXP', 1),
    ('Configuración de Plataforma', 'CONF_PLAT', 1),
    ('Gestión Vertido Excedente', 'GEST_VERT', 1),
    ('Gestión Excedente IE', 'GEST_IE', 1),
    ('Resolución dudas a cliente', 'RES_DUD', 1),
    ('Facturación', 'FACT', 1),
    ('Denuncia de Contrato', 'DEN_CTO', 1),
    ('Estudio/Encaje', 'EST_ENC', 1),
    ('Presupuesto/Contrato', 'PRES_CTO', 1),
    ('Cotización/Proveedores', 'COT_PROV', 1),
    ('Pedido', 'PEDIDO', 1),
    ('Dirección de Obra', 'DIR_OBRA', 1),
    ('Proyecto', 'PROY', 1),
    ('Legalización', 'LEGAL', 1),
    ('Licencias', 'LIC', 1),
    ('Final de obra/OCA', 'FIN_OCA', 1),
    ('Administración general', 'ADM_GEN', 1),
    ('Bancos', 'BANCOS', 1),
    ('Facturación proveedores', 'FAC_PROV', 1),
    ('Facturación clientes', 'FAC_CLI', 1),
    ('Servicios/Liquidaciones', 'SERV_LIQ', 1),
    ('Trabajadores empresa', 'TRAB_EMP', 1),
    ('Reuniones', 'REUNION', 1),
    ('Formaciones', 'FORM', 1),
    ('Visitas', 'VISITAS', 1)
ON DUPLICATE KEY UPDATE 
    nombre = VALUES(nombre),
    activo = VALUES(activo);

-- =====================================================
-- 4. INSERTAR RELACIONES CATEGORÍA-SUBCATEGORÍA
-- =====================================================

-- Primero eliminar relaciones existentes para las nuevas categorías
DELETE cs FROM modulotareas_categorias_subcategorias cs
INNER JOIN modulotareas_categorias c ON cs.categoria_id = c.id
WHERE c.codigo IN ('GE', 'FV', 'TM', 'GEN');

-- GE (Gestión Energética)
INSERT INTO modulotareas_categorias_subcategorias (categoria_id, subcategoria_id)
SELECT c.id, s.id
FROM modulotareas_categorias c
CROSS JOIN modulotareas_subcategorias s
WHERE c.codigo = 'GE' 
  AND s.codigo IN ('EST_COM', 'SWITCH', 'INCID', 'LLAM', 'EST_POT', 'REV_FAC', 
                   'REV_CTO', 'REV_INF', 'REV_ALM', 'TAR_VAR', 'MOD_POT', 
                   'GEST_EXP', 'GEST_VERT', 'GEST_IE', 'RES_DUD', 'FACT', 'DEN_CTO')
ON DUPLICATE KEY UPDATE categoria_id = categoria_id;

-- FV (Fotovoltaicas)
INSERT INTO modulotareas_categorias_subcategorias (categoria_id, subcategoria_id)
SELECT c.id, s.id
FROM modulotareas_categorias c
CROSS JOIN modulotareas_subcategorias s
WHERE c.codigo = 'FV' 
  AND s.codigo IN ('INCID', 'LLAM', 'TAR_VAR', 'GEST_VERT', 'RES_DUD', 'FACT',
                   'EST_ENC', 'PRES_CTO', 'COT_PROV', 'PEDIDO', 'DIR_OBRA', 
                   'PROY', 'LEGAL', 'LIC', 'FIN_OCA')
ON DUPLICATE KEY UPDATE categoria_id = categoria_id;

-- TM (Telemedida)
INSERT INTO modulotareas_categorias_subcategorias (categoria_id, subcategoria_id)
SELECT c.id, s.id
FROM modulotareas_categorias c
CROSS JOIN modulotareas_subcategorias s
WHERE c.codigo = 'TM' 
  AND s.codigo IN ('INCID', 'LLAM', 'REV_CTO', 'TAR_VAR', 'CONF_PLAT', 'RES_DUD', 'FACT')
ON DUPLICATE KEY UPDATE categoria_id = categoria_id;

-- GEN (Generales)
INSERT INTO modulotareas_categorias_subcategorias (categoria_id, subcategoria_id)
SELECT c.id, s.id
FROM modulotareas_categorias c
CROSS JOIN modulotareas_subcategorias s
WHERE c.codigo = 'GEN' 
  AND s.codigo IN ('ADM_GEN', 'BANCOS', 'FAC_PROV', 'FAC_CLI', 'SERV_LIQ', 
                   'TRAB_EMP', 'REUNION', 'FORM', 'VISITAS')
ON DUPLICATE KEY UPDATE categoria_id = categoria_id;

-- =====================================================
-- 5. VERIFICACIÓN (SELECT para comprobar resultado)
-- =====================================================
-- SELECT 
--     c.nombre AS categoria, 
--     c.codigo AS cod_cat,
--     s.nombre AS subcategoria,
--     s.codigo AS cod_sub
-- FROM modulotareas_categorias c
-- JOIN modulotareas_categorias_subcategorias cs ON c.id = cs.categoria_id
-- JOIN modulotareas_subcategorias s ON cs.subcategoria_id = s.id
-- WHERE c.codigo IN ('GE', 'FV', 'TM', 'GEN')
-- ORDER BY c.codigo, s.nombre;
