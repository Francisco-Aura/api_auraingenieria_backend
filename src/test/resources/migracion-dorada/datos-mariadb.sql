-- Datos del Planificador (generado por sqlite_a_mariadb.py). Cargar con las tablas pl_* ya creadas y vacías.
SET NAMES utf8mb4;
SET SESSION sql_mode = CONCAT_WS(',', @@SESSION.sql_mode, 'NO_BACKSLASH_ESCAPES');
SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO pl_marcas (nombre, logo) VALUES
('Huawei', 'data:image/png;base64,iVBORw0KGgo=');

INSERT INTO pl_proveedores (nombre) VALUES
('SUMEBA');

INSERT INTO pl_equipos (id, posicion, categoria, partida, marca, modelo, proveedor, unidad, precio_unitario, por_modulo, cantidad_defecto, atributos, notas, muestra, stock, stock_minimo, ficha_tecnica, ofertas, precio_revisado_el) VALUES
('e1', 0, 'modulos', 'modulos', 'Aiko', 'AIKO 615 Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €', 'SUMEBA', 'módulo', 76.15, 1, 1.0, '{"potencia_wp": 615, "nota": "Comilla '' y \"dobles\", barra \\ y \\n literal, salto\nde l\u00ednea, tab\t, emoji \ud83d\ude00, \u00f1and\u00fa \u00abx\u00bb \u20ac"}', 'Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €', 1, 3.5, 1.0, 'Aiko/ficha.pdf', '[{"supplier": "SUMEBA", "price": 76.15}]', '2026-10-20'),
('e2', 1, 'inversores', 'inversores', '', '', '', 'ud', 0.0, 0, 1.0, '{}', '', 0, 0.0, 0.0, '', '[]', NULL);

INSERT INTO pl_historial_precios (id, equipo_id, precio, proveedor, ofertas, por, fecha) VALUES
('h1', 'e1', 76.15, 'SUMEBA', '[{"supplier": "SUMEBA", "price": 76.15}]', 'Ana', '2026-10-01 08:30:15.123456');

INSERT INTO pl_meta (clave, valor) VALUES
('price_review_baseline', '2026-10-01'),
('price_review_notified', '2026-10-20');

INSERT INTO pl_presupuestos (id, nombre, propietario, propietario_id, estado, oferta_id, cliente_id, cliente_tipo, nombre_variante, datos, kwp, paneles, total_presupuesto, version, creado_el, actualizado_el, actualizado_por) VALUES
('b1', 'Presupuesto Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €', 'Ana', '7', 'validated', 'o1', 12, 'client', 'Con batería', '{"rows": [{"a": "Comilla '' y \"dobles\", barra \\ y \\n literal, salto\nde l\u00ednea, tab\t, emoji \ud83d\ude00, \u00f1and\u00fa \u00abx\u00bb \u20ac", "n": 1.5}], "x": null}', 51.6, 80, 38700.5, 3, '2026-09-01 10:00:00.000000', '2026-09-02 11:00:01.000005', 'Luis');

INSERT INTO pl_propuestas (id, presupuesto_id, version, estado, nota, creado_el, creado_por, enviado_el, enviado_por, decidido_el, decidido_por, logo_cliente) VALUES
('pr1', 'b1', 2, 'sent', 'Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €', '2026-09-03 00:00:00.000000', 'Ana', '2026-09-04 00:00:00.000000', 'Ana', NULL, '', 'data:image/png;base64,AAAA');

INSERT INTO pl_proyectos (id, propietario_id, nombre, descripcion, fecha_inicio, documentos, linea_base, cambios, presupuesto, partes_contrato, contrato_generado_el, archivo_contrato, plan_pagos, creado_el, ruta_dropbox, eliminado_el) VALUES
('p1', 'local', 'Instalación Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €', 'Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €', '2026-10-01', '[{"title": "Gu\u00eda", "body": "Comilla '' y \"dobles\", barra \\ y \\n literal, salto\nde l\u00ednea, tab\t, emoji \ud83d\ude00, \u00f1and\u00fa \u00abx\u00bb \u20ac"}]', '{"importe_total": 38700}', '[{"id": "c1", "set": {"paneles_uds": 90}}]', '{"total": 1}', '{"cliente": "Comilla '' y \"dobles\", barra \\ y \\n literal, salto\nde l\u00ednea, tab\t, emoji \ud83d\ude00, \u00f1and\u00fa \u00abx\u00bb \u20ac"}', '2026-10-02 09:00:00.000000', 'Contrato.pdf', '{"stages": []}', '2026-10-01 07:00:00.000000', '/Proyectos/X', NULL),
('p2', 'local', 'Borrado', '', NULL, '[]', '{}', '[]', '{}', '{}', NULL, '', '{}', '2026-11-06 10:47:34.819200', '', '2026-10-05 00:00:00.000000');

INSERT INTO pl_tareas_proyecto (id, proyecto_id, nombre, descripcion, fase, departamento, posicion, hito, inicio, fin, fin_previsto, progreso, estado, motivo_estado, nota_estado, completada_el, nota_cierre, entregables, adjuntos, usa, cambios_revisados, etapa_pago, importe_pago, etiqueta_pago, carpeta_dropbox, avisadas, marcas_vencimiento, depende_de, lista, tipo, creado_el) VALUES
('t1', 'p1', 'Redactar Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €', 'Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €', 'Inicio', 'ADMINISTRACIÓN', 0, 1, '2026-10-01', '2026-10-03', '2026-10-03', 100, 'done', '', '', '2026-10-03', 'Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €', '[{"key": "k", "label": "Comilla '' y \"dobles\", barra \\ y \\n literal, salto\nde l\u00ednea, tab\t, emoji \ud83d\ude00, \u00f1and\u00fa \u00abx\u00bb \u20ac", "na": false}]', '[{"id": "a", "kind": "note", "text": "Comilla '' y \"dobles\", barra \\ y \\n literal, salto\nde l\u00ednea, tab\t, emoji \ud83d\ude00, \u00f1and\u00fa \u00abx\u00bb \u20ac"}]', '["paneles_uds"]', '["c1"]', 0, 1000.5, '30 %', '01', '["t2"]', '["due:2026-10-03"]', '[]', '[{"text": "Comilla '' y \"dobles\", barra \\ y \\n literal, salto\nde l\u00ednea, tab\t, emoji \ud83d\ude00, \u00f1and\u00fa \u00abx\u00bb \u20ac", "done": true}]', 'material', '2026-10-01 07:00:00.000000'),
('t2', 'p1', 'Segunda', '', '', '', 0, 0, '2026-10-04', '2026-10-06', NULL, 0, 'todo', '', '', NULL, '', '[]', '[]', '[]', '[]', NULL, NULL, '', '', '[]', '[]', '["t1"]', '[]', '', '2026-10-01 07:01:00.000000');

INSERT INTO pl_actividad (id, proyecto_id, tarea_id, nombre_tarea, fecha, actor, tipo, resumen, detalle) VALUES
('ac1', 'p1', 't1', 'Redactar', '2026-10-03 12:00:00.000001', 'Ana', 'status', 'Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €', 'Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €');

INSERT INTO pl_borradores (id, nombre, datos, actualizado_el, actualizado_por) VALUES
('d1', 'Borrador', '{"form": {"a": "Comilla '' y \"dobles\", barra \\ y \\n literal, salto\nde l\u00ednea, tab\t, emoji \ud83d\ude00, \u00f1and\u00fa \u00abx\u00bb \u20ac"}}', '2026-10-06 00:00:00.000000', 'Ana');

INSERT INTO pl_devoluciones_material (id, proyecto_id, equipo_id, etiqueta, unidad, cantidad, nota, por, fecha) VALUES
('m1', 'p1', 'e1', 'AIKO', 'módulo', 2.5, 'Comilla '' y "dobles", barra \ y \n literal, salto
de línea, tab	, emoji 😀, ñandú «x» €', 'Ana', '2026-10-07 00:00:00.000000');

SET FOREIGN_KEY_CHECKS = 1;

-- Recuento por tabla: debe coincidir con el que informó el programa
SELECT 'pl_marcas' AS tabla, COUNT(*) AS filas FROM pl_marcas;
SELECT 'pl_proveedores' AS tabla, COUNT(*) AS filas FROM pl_proveedores;
SELECT 'pl_equipos' AS tabla, COUNT(*) AS filas FROM pl_equipos;
SELECT 'pl_historial_precios' AS tabla, COUNT(*) AS filas FROM pl_historial_precios;
SELECT 'pl_meta' AS tabla, COUNT(*) AS filas FROM pl_meta;
SELECT 'pl_presupuestos' AS tabla, COUNT(*) AS filas FROM pl_presupuestos;
SELECT 'pl_propuestas' AS tabla, COUNT(*) AS filas FROM pl_propuestas;
SELECT 'pl_proyectos' AS tabla, COUNT(*) AS filas FROM pl_proyectos;
SELECT 'pl_tareas_proyecto' AS tabla, COUNT(*) AS filas FROM pl_tareas_proyecto;
SELECT 'pl_actividad' AS tabla, COUNT(*) AS filas FROM pl_actividad;
SELECT 'pl_borradores' AS tabla, COUNT(*) AS filas FROM pl_borradores;
SELECT 'pl_devoluciones_material' AS tabla, COUNT(*) AS filas FROM pl_devoluciones_material;
