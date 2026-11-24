-- Datos de ejemplo del Planificador: «propuestas» (proyectos comerciales) en todas las fases del proceso. MariaDB 10.11. Se aplica a mano.
--
-- Esquema de la rama develop: necesita pl_estudios, pl_proyectos_comerciales y pl_presupuestos.estudio_id / proyecto_id ya creados, y al menos
-- UN estudio real en pl_estudios: de él se copian los datos, el resultado y la curva de consumo (los estudios demo salen con su cálculo).
-- Todos los ids empiezan por «demo-»: el script borra primero los suyos y los vuelve a crear, así que se puede repetir. No toca nada más.
--
-- Cada fila de la lista es un proyecto comercial (la «Propuesta»), con su estudio, su presupuesto y su propuesta final, siempre coherentes:
--   * el estudio avanza solo: en_estudio -> pte_doc -> entregado -> pte_cliente (al entregar la propuesta) -> aceptado (al validarla)
--   * la propuesta solo existe con presupuesto validado, y para validarla el estudio no puede estar rechazado ni cancelado
--
--   Caso                          Estudio        Presupuesto   Propuesta final           Lo que se ve en la lista
--    1 Clínica Dental Mar         —              —             —                         proyecto vacío
--    2 Hermanos Ruiz              en_estudio     —             —                         solo el estudio, en curso
--    3 Cerámicas Ibáñez           pte_doc        —             —                         estudio a la espera de documentación del cliente
--    4 Gimnasio Sprint            rechazado      —             —                         estudio rechazado por el cliente
--    5 Bodega Campo Alto          entregado      borrador      —                         presupuesto en preparación
--    6 Cooperativa San Blas       entregado      enviado       —                         presupuesto pendiente de validar
--    7 Talleres Egea              entregado      validado      —                         «Falta propuesta»
--    8 Solar Vega Lorca           entregado      validado      v1 borrador               «Propuesta en borrador»
--    9 Almazara Los Olivos        pte_cliente    validado      v1 rechazada + v2 enviada «Propuesta entregada» (con una anulada antes)
--   10 Hotel Costa Cálida         pte_cliente    validado      v1 rechazada              «Anulada»
--   11 Logística Segura           aceptado       validado      v1 aprobada               «Validada»: el proyecto entero, ya cerrado
--   12 Panadería Horno Viejo      entregado      enviado       —                         estudio hecho y presupuesto hecho, ambos sin validar (el 6 es igual)

-- ---------------------------------------------------------------------------------------------------------------------
-- Limpieza de una ejecución anterior (primero las propuestas, por la clave foránea)
-- ---------------------------------------------------------------------------------------------------------------------
DELETE FROM pl_propuestas WHERE id LIKE 'demo-%';
DELETE FROM pl_presupuestos WHERE id LIKE 'demo-%';
DELETE FROM pl_estudios WHERE id LIKE 'demo-%';
DELETE FROM pl_proyectos_comerciales WHERE id LIKE 'demo-%';

-- ---------------------------------------------------------------------------------------------------------------------
-- Proyectos comerciales (la clave es tipo|cliente|proyecto, en minúsculas y sin tildes)
-- ---------------------------------------------------------------------------------------------------------------------
INSERT INTO pl_proyectos_comerciales (id, clave, cliente_tipo, cliente_id, cliente_nombre, cliente, nombre, logo, encaje, creado_el, creado_por)
VALUES
    ('demo-proy-1', 'potencial|clinica dental mar, s.l.p.|autoconsumo cartagena', 'potencial', NULL, 'Clínica Dental Mar, S.L.P.',
     JSON_OBJECT('kind', 'potencial', 'name', 'Clínica Dental Mar, S.L.P.', 'cif', 'B99100001', 'phone', '', 'email', ''), 'Autoconsumo Cartagena', '', '', '2026-11-20 10:00:00.000000', 'Demo'),
    ('demo-proy-2', 'potencial|hermanos ruiz, s.l.|autoconsumo molina de segura', 'potencial', NULL, 'Hermanos Ruiz, S.L.',
     JSON_OBJECT('kind', 'potencial', 'name', 'Hermanos Ruiz, S.L.', 'cif', 'B99100002', 'phone', '', 'email', ''), 'Autoconsumo Molina de Segura', '', '', '2026-11-18 09:00:00.000000', 'Demo'),
    ('demo-proy-3', 'potencial|ceramicas ibanez, s.l.|autoconsumo totana', 'potencial', NULL, 'Cerámicas Ibáñez, S.L.',
     JSON_OBJECT('kind', 'potencial', 'name', 'Cerámicas Ibáñez, S.L.', 'cif', 'B99100003', 'phone', '', 'email', ''), 'Autoconsumo Totana', '', '', '2026-11-12 09:00:00.000000', 'Demo'),
    ('demo-proy-4', 'potencial|gimnasio sprint, s.l.|autoconsumo murcia', 'potencial', NULL, 'Gimnasio Sprint, S.L.',
     JSON_OBJECT('kind', 'potencial', 'name', 'Gimnasio Sprint, S.L.', 'cif', 'B99100004', 'phone', '', 'email', ''), 'Autoconsumo Murcia', '', '', '2026-10-28 09:00:00.000000', 'Demo'),
    ('demo-proy-5', 'potencial|bodega campo alto, s.a.|autoconsumo jumilla', 'potencial', NULL, 'Bodega Campo Alto, S.A.',
     JSON_OBJECT('kind', 'potencial', 'name', 'Bodega Campo Alto, S.A.', 'cif', 'A99100005', 'phone', '', 'email', ''), 'Autoconsumo Jumilla', '', '', '2026-11-05 09:00:00.000000', 'Demo'),
    ('demo-proy-6', 'potencial|cooperativa agricola san blas|autoconsumo yecla', 'potencial', NULL, 'Cooperativa Agrícola San Blas',
     JSON_OBJECT('kind', 'potencial', 'name', 'Cooperativa Agrícola San Blas', 'cif', 'F99100006', 'phone', '', 'email', ''), 'Autoconsumo Yecla', '', '', '2026-10-30 09:00:00.000000', 'Demo'),
    ('demo-proy-7', 'potencial|talleres egea, s.l.|autoconsumo cieza', 'potencial', NULL, 'Talleres Egea, S.L.',
     JSON_OBJECT('kind', 'potencial', 'name', 'Talleres Egea, S.L.', 'cif', 'B99100007', 'phone', '', 'email', ''), 'Autoconsumo Cieza', '', '', '2026-10-05 09:00:00.000000', 'Demo'),
    ('demo-proy-8', 'potencial|solar vega, s.l.u.|autoconsumo lorca', 'potencial', NULL, 'Solar Vega, S.L.U.',
     JSON_OBJECT('kind', 'potencial', 'name', 'Solar Vega, S.L.U.', 'cif', 'B99100008', 'phone', '', 'email', ''), 'Autoconsumo Lorca', '', '', '2026-09-14 09:00:00.000000', 'Demo'),
    ('demo-proy-9', 'potencial|almazara los olivos, s.c.|autoconsumo mula', 'potencial', NULL, 'Almazara Los Olivos, S.C.',
     JSON_OBJECT('kind', 'potencial', 'name', 'Almazara Los Olivos, S.C.', 'cif', 'F99100009', 'phone', '', 'email', ''), 'Autoconsumo Mula', '', '', '2026-09-20 09:00:00.000000', 'Demo'),
    ('demo-proy-10', 'potencial|hotel costa calida, s.a.|autoconsumo la manga', 'potencial', NULL, 'Hotel Costa Cálida, S.A.',
     JSON_OBJECT('kind', 'potencial', 'name', 'Hotel Costa Cálida, S.A.', 'cif', 'A99100010', 'phone', '', 'email', ''), 'Autoconsumo La Manga', '', '', '2026-09-25 09:00:00.000000', 'Demo'),
    ('demo-proy-11', 'potencial|logistica segura, s.l.|autoconsumo alcantarilla', 'potencial', NULL, 'Logística Segura, S.L.',
     JSON_OBJECT('kind', 'potencial', 'name', 'Logística Segura, S.L.', 'cif', 'B99100011', 'phone', '', 'email', ''), 'Autoconsumo Alcantarilla', '', '', '2026-08-18 09:00:00.000000', 'Demo'),
    ('demo-proy-12', 'potencial|panaderia horno viejo, s.l.|autoconsumo alhama de murcia', 'potencial', NULL, 'Panadería Horno Viejo, S.L.',
     JSON_OBJECT('kind', 'potencial', 'name', 'Panadería Horno Viejo, S.L.', 'cif', 'B99100012', 'phone', '', 'email', ''), 'Autoconsumo Alhama de Murcia', '', '', '2026-11-16 09:00:00.000000', 'Demo');

-- ---------------------------------------------------------------------------------------------------------------------
-- Estudios (casos 2 a 12). Los datos, el resultado y la curva de consumo se copian de un estudio real; cambian cliente, proyecto y CUPS
-- ---------------------------------------------------------------------------------------------------------------------
INSERT INTO pl_estudios (id, cliente, cliente_id, cliente_tipo, proyecto_id, cups, estado, version, observaciones, datos, resultado, curva,
                         curva_estimada, curva_origen, creado_el, creado_por, actualizado_el, actualizado_por)
SELECT v.id, v.cliente, NULL, 'potencial', v.proyecto_id, v.cups, v.estado, 1, '',
       JSON_SET(t.datos, '$.cliente', v.cliente, '$.proyecto', v.proyecto, '$.cups', v.cups, '$.localidad', v.localidad),
       t.resultado, t.curva, t.curva_estimada, t.curva_origen, v.creado, 'Demo', v.actualizado, 'Demo'
FROM (
    SELECT 'demo-est-2' AS id, 'Hermanos Ruiz, S.L.' AS cliente, 'demo-proy-2' AS proyecto_id, 'Autoconsumo Molina de Segura' AS proyecto, 'ES0000000000000002DM' AS cups, 'Molina de Segura' AS localidad,
           'en_estudio' AS estado, '2026-11-18 09:30:00' AS creado, '2026-11-18 09:30:00' AS actualizado
    UNION ALL SELECT 'demo-est-3', 'Cerámicas Ibáñez, S.L.', 'demo-proy-3', 'Autoconsumo Totana', 'ES0000000000000003DM', 'Totana',
           'pte_doc', '2026-11-12 09:30:00', '2026-11-14 12:00:00'
    UNION ALL SELECT 'demo-est-4', 'Gimnasio Sprint, S.L.', 'demo-proy-4', 'Autoconsumo Murcia', 'ES0000000000000004DM', 'Murcia',
           'rechazado', '2026-10-28 09:30:00', '2026-11-09 16:00:00'
    UNION ALL SELECT 'demo-est-5', 'Bodega Campo Alto, S.A.', 'demo-proy-5', 'Autoconsumo Jumilla', 'ES0000000000000005DM', 'Jumilla',
           'entregado', '2026-11-05 09:30:00', '2026-11-10 11:00:00'
    UNION ALL SELECT 'demo-est-6', 'Cooperativa Agrícola San Blas', 'demo-proy-6', 'Autoconsumo Yecla', 'ES0000000000000006DM', 'Yecla',
           'entregado', '2026-10-30 09:30:00', '2026-11-04 10:30:00'
    UNION ALL SELECT 'demo-est-7', 'Talleres Egea, S.L.', 'demo-proy-7', 'Autoconsumo Cieza', 'ES0000000000000007DM', 'Cieza',
           'entregado', '2026-10-05 09:30:00', '2026-10-12 10:00:00'
    UNION ALL SELECT 'demo-est-8', 'Solar Vega, S.L.U.', 'demo-proy-8', 'Autoconsumo Lorca', 'ES0000000000000008DM', 'Lorca',
           'entregado', '2026-09-14 09:30:00', '2026-09-20 10:00:00'
    UNION ALL SELECT 'demo-est-9', 'Almazara Los Olivos, S.C.', 'demo-proy-9', 'Autoconsumo Mula', 'ES0000000000000009DM', 'Mula',
           'pte_cliente', '2026-09-20 09:30:00', '2026-11-11 12:00:00'
    UNION ALL SELECT 'demo-est-10', 'Hotel Costa Cálida, S.A.', 'demo-proy-10', 'Autoconsumo La Manga', 'ES0000000000000010DM', 'Cartagena',
           'pte_cliente', '2026-09-25 09:30:00', '2026-10-20 12:00:00'
    UNION ALL SELECT 'demo-est-11', 'Logística Segura, S.L.', 'demo-proy-11', 'Autoconsumo Alcantarilla', 'ES0000000000000011DM', 'Alcantarilla',
           'aceptado', '2026-08-18 09:30:00', '2026-09-30 12:00:00'
    UNION ALL SELECT 'demo-est-12', 'Panadería Horno Viejo, S.L.', 'demo-proy-12', 'Autoconsumo Alhama de Murcia', 'ES0000000000000012DM', 'Alhama de Murcia',
           'entregado', '2026-11-16 09:30:00', '2026-11-19 11:00:00'
) v
CROSS JOIN (
    SELECT datos, resultado, curva, curva_estimada, curva_origen FROM pl_estudios WHERE id NOT LIKE 'demo-%' ORDER BY creado_el LIMIT 1
) t;

-- ---------------------------------------------------------------------------------------------------------------------
-- Presupuestos (casos 5 a 12). El nombre es «Cliente · Proyecto»; cuelgan del proyecto y del estudio de su caso
-- ---------------------------------------------------------------------------------------------------------------------
INSERT INTO pl_presupuestos
    (id, nombre, propietario, propietario_id, estado, oferta_id, estudio_id, proyecto_id, cliente_id, cliente_tipo, nombre_variante, observaciones, datos,
     kwp, paneles, total_presupuesto, version, creado_el, actualizado_el, actualizado_por)
VALUES
    ('demo-pres-5', 'Bodega Campo Alto, S.A. · Autoconsumo Jumilla', 'Demo', '', 'borrador', 'demo-oferta-5', 'demo-est-5', 'demo-proy-5', NULL, 'potencial', '', '',
     '{"client":"Bodega Campo Alto, S.A.","customer":{"kind":"potencial","name":"Bodega Campo Alto, S.A.","cif":"","phone":"","email":""},"reference":"Autoconsumo Jumilla","cif":"","clientLogo":"","encaje":"","strings":null,"moduleWp":550,"moduleCount":180,"saleRatio":1.1,"lines":[],"survey":{"roof":"plana","roofs":["plana"],"roofLayouts":{},"roofPanels":{},"targetKwp":0,"cableMeters":0,"acMeters":0,"futureBattery":false,"supply":"trifasico"},"order":"insercion"}',
     99.0, 180, 71500, 1, '2026-11-11 09:00:00.000000', '2026-11-11 09:00:00.000000', 'Demo'),
    ('demo-pres-6', 'Cooperativa Agrícola San Blas · Autoconsumo Yecla', 'Demo', '', 'enviado', 'demo-oferta-6', 'demo-est-6', 'demo-proy-6', NULL, 'potencial', '', '',
     '{"client":"Cooperativa Agrícola San Blas","customer":{"kind":"potencial","name":"Cooperativa Agrícola San Blas","cif":"","phone":"","email":""},"reference":"Autoconsumo Yecla","cif":"","clientLogo":"","encaje":"","strings":null,"moduleWp":600,"moduleCount":200,"saleRatio":1.1,"lines":[],"survey":{"roof":"teja","roofs":["teja"],"roofLayouts":{},"roofPanels":{},"targetKwp":0,"cableMeters":0,"acMeters":0,"futureBattery":false,"supply":"trifasico"},"order":"insercion"}',
     120.0, 200, 94200, 1, '2026-11-05 09:00:00.000000', '2026-11-08 13:10:00.000000', 'Demo'),
    ('demo-pres-7', 'Talleres Egea, S.L. · Autoconsumo Cieza', 'Demo', '', 'validado', 'demo-oferta-7', 'demo-est-7', 'demo-proy-7', NULL, 'potencial', '', '',
     '{"client":"Talleres Egea, S.L.","customer":{"kind":"potencial","name":"Talleres Egea, S.L.","cif":"","phone":"","email":""},"reference":"Autoconsumo Cieza","cif":"","clientLogo":"","encaje":"","strings":null,"moduleWp":625,"moduleCount":48,"saleRatio":1.1,"lines":[],"survey":{"roof":"chapa","roofs":["chapa"],"roofLayouts":{},"roofPanels":{},"targetKwp":0,"cableMeters":0,"acMeters":0,"futureBattery":false,"supply":"trifasico"},"order":"insercion"}',
     30.0, 48, 24900, 1, '2026-10-13 11:30:00.000000', '2026-10-20 09:40:00.000000', 'Demo'),
    ('demo-pres-8', 'Solar Vega, S.L.U. · Autoconsumo Lorca', 'Demo', '', 'validado', 'demo-oferta-8', 'demo-est-8', 'demo-proy-8', NULL, 'potencial', '', '',
     '{"client":"Solar Vega, S.L.U.","customer":{"kind":"potencial","name":"Solar Vega, S.L.U.","cif":"","phone":"","email":""},"reference":"Autoconsumo Lorca","cif":"","clientLogo":"","encaje":"","strings":null,"moduleWp":645,"moduleCount":80,"saleRatio":1.1,"lines":[],"survey":{"roof":"plana","roofs":["plana"],"roofLayouts":{},"roofPanels":{},"targetKwp":0,"cableMeters":0,"acMeters":0,"futureBattery":false,"supply":"trifasico"},"order":"insercion"}',
     51.6, 80, 38700, 1, '2026-09-21 08:45:00.000000', '2026-09-28 11:20:00.000000', 'Demo'),
    ('demo-pres-9', 'Almazara Los Olivos, S.C. · Autoconsumo Mula', 'Demo', '', 'validado', 'demo-oferta-9', 'demo-est-9', 'demo-proy-9', NULL, 'potencial', '', '',
     '{"client":"Almazara Los Olivos, S.C.","customer":{"kind":"potencial","name":"Almazara Los Olivos, S.C.","cif":"","phone":"","email":""},"reference":"Autoconsumo Mula","cif":"","clientLogo":"","encaje":"","strings":null,"moduleWp":625,"moduleCount":120,"saleRatio":1.1,"lines":[],"survey":{"roof":"plana","roofs":["plana"],"roofLayouts":{},"roofPanels":{},"targetKwp":0,"cableMeters":0,"acMeters":0,"futureBattery":false,"supply":"trifasico"},"order":"insercion"}',
     75.0, 120, 55300, 1, '2026-09-27 10:00:00.000000', '2026-10-02 16:30:00.000000', 'Demo'),
    ('demo-pres-10', 'Hotel Costa Cálida, S.A. · Autoconsumo La Manga', 'Demo', '', 'validado', 'demo-oferta-10', 'demo-est-10', 'demo-proy-10', NULL, 'potencial', '', '',
     '{"client":"Hotel Costa Cálida, S.A.","customer":{"kind":"potencial","name":"Hotel Costa Cálida, S.A.","cif":"","phone":"","email":""},"reference":"Autoconsumo La Manga","cif":"","clientLogo":"","encaje":"","strings":null,"moduleWp":625,"moduleCount":96,"saleRatio":1.1,"lines":[],"survey":{"roof":"plana","roofs":["plana"],"roofLayouts":{},"roofPanels":{},"targetKwp":0,"cableMeters":0,"acMeters":0,"futureBattery":false,"supply":"trifasico"},"order":"insercion"}',
     60.0, 96, 47800, 1, '2026-10-02 10:00:00.000000', '2026-10-08 16:30:00.000000', 'Demo'),
    ('demo-pres-11', 'Logística Segura, S.L. · Autoconsumo Alcantarilla', 'Demo', '', 'validado', 'demo-oferta-11', 'demo-est-11', 'demo-proy-11', NULL, 'potencial', '', '',
     '{"client":"Logística Segura, S.L.","customer":{"kind":"potencial","name":"Logística Segura, S.L.","cif":"","phone":"","email":""},"reference":"Autoconsumo Alcantarilla","cif":"","clientLogo":"","encaje":"","strings":null,"moduleWp":625,"moduleCount":240,"saleRatio":1.1,"lines":[],"survey":{"roof":"plana","roofs":["plana"],"roofLayouts":{},"roofPanels":{},"targetKwp":0,"cableMeters":0,"acMeters":0,"futureBattery":false,"supply":"trifasico"},"order":"insercion"}',
     150.0, 240, 112400, 1, '2026-08-25 09:00:00.000000', '2026-09-05 12:00:00.000000', 'Demo'),
    ('demo-pres-12', 'Panadería Horno Viejo, S.L. · Autoconsumo Alhama de Murcia', 'Demo', '', 'enviado', 'demo-oferta-12', 'demo-est-12', 'demo-proy-12', NULL, 'potencial', '', '',
     '{"client":"Panadería Horno Viejo, S.L.","customer":{"kind":"potencial","name":"Panadería Horno Viejo, S.L.","cif":"","phone":"","email":""},"reference":"Autoconsumo Alhama de Murcia","cif":"","clientLogo":"","encaje":"","strings":null,"moduleWp":611,"moduleCount":36,"saleRatio":1.1,"lines":[],"survey":{"roof":"chapa","roofs":["chapa"],"roofLayouts":{},"roofPanels":{},"targetKwp":0,"cableMeters":0,"acMeters":0,"futureBattery":false,"supply":"trifasico"},"order":"insercion"}',
     22.0, 36, 18600, 1, '2026-11-19 12:00:00.000000', '2026-11-21 09:30:00.000000', 'Demo');

-- ---------------------------------------------------------------------------------------------------------------------
-- Propuestas finales (casos 8 a 11; el 7 queda sin propuesta a propósito: «Falta propuesta»)
-- ---------------------------------------------------------------------------------------------------------------------
INSERT INTO pl_propuestas
    (id, presupuesto_id, version, estado, nota, creado_el, creado_por, enviado_el, enviado_por, decidido_el, decidido_por, logo_cliente)
VALUES
    -- 8. Borrador: creada, sin entregar
    ('demo-prop-8', 'demo-pres-8', 1, 'borrador', '',
     '2026-11-23 10:00:00.000000', 'Demo', NULL, '', NULL, '', ''),
    -- 9. La primera se anuló y la segunda está entregada, a la espera del cliente (por eso el estudio está pendiente de su respuesta)
    ('demo-prop-9-v1', 'demo-pres-9', 1, 'rechazada', 'El cliente pidió ajustar la potencia a su contrato.',
     '2026-10-05 09:00:00.000000', 'Demo', '2026-10-06 09:30:00.000000', 'Demo', '2026-10-14 10:10:00.000000', 'Demo', ''),
    ('demo-prop-9-v2', 'demo-pres-9', 2, 'enviada', '',
     '2026-10-27 09:00:00.000000', 'Demo', '2026-11-11 12:00:00.000000', 'Demo', NULL, '', ''),
    -- 10. Anulada, sin versión nueva todavía
    ('demo-prop-10', 'demo-pres-10', 1, 'rechazada', 'Precio por encima de otra oferta.',
     '2026-10-10 09:00:00.000000', 'Demo', '2026-10-20 12:00:00.000000', 'Demo', '2026-11-03 15:20:00.000000', 'Demo', ''),
    -- 11. Validada: el estudio queda aceptado y bloqueado
    ('demo-prop-11', 'demo-pres-11', 1, 'aprobada', 'Aprobada con la forma de pago 30 / 40 / 20 / 10 %.',
     '2026-09-08 09:00:00.000000', 'Demo', '2026-09-10 10:15:00.000000', 'Demo', '2026-09-30 12:00:00.000000', 'Demo', '');

-- Para quitar solo los datos de ejemplo, ejecuta las cuatro sentencias DELETE del principio.
