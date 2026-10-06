-- ============================================================
-- MCAS - Precarga de reglas y retrocesiones (Hoja2 del cliente + Excel de comisiones)
-- V20260929_2_Seed_MCAS_Reglas_Hoja2.sql
--
-- CÓMO EJECUTARLO
--   Después de V20260913_1, V20260913_2 y V20260929_1_MCAS_Conformidad.sql, contra la BBDD de
--   Aura (MariaDB). Solo DML: no crea ni altera tablas. Revise al final las consultas de
--   comprobación (qué comercializadoras se han encontrado y qué se ha sembrado).
--
-- PRINCIPIOS
--   * Idempotente: se puede relanzar. Todo se inserta con NOT EXISTS por código y nunca duplica.
--   * Todo queda en BORRADOR (creado_por = 'seed-hoja2'): nada se publica solo. Un administrador
--     prueba, valida y publica cada conjunto y cada política en Configuración MCAS.
--   * La comercializadora se busca por nombre y solo se siembra si el patrón encuentra EXACTAMENTE
--     una (ENDESA excluye ENDESA XXI). Si no, no inserta nada y la consulta final lo muestra.
--   * Nunca toca conjuntos PUBLICADO/VALIDADO/ARCHIVADO ni los creados por personas. Los conjuntos
--     IRIS-FIJO-20260304 y VISALIA-FIJO-20250612 de V20260913_2 solo se modifican si siguen en
--     BORRADOR y con creado_por = 'seed-controlado'.
--   * Si la Hoja2 (lo que escribió el cliente) contradice al Excel, manda la Hoja2 y el valor del
--     Excel queda anotado en localizador_origen. Importes del Excel redondeados a 2 decimales.
--   * Rangos del Excel: «5.001-10.000» = (5000, 10000], «0-5.000» = [0, 5000], «>800.001» =
--     (800000, ∞). Dentro de un conjunto, dos reglas activas nunca coinciden para el mismo contrato.
--   * politica_pago_json = 1 pago sugerido en todas (el número de pagos lo pone el usuario).
--   * GANA, REPSOL y HELIOELEC no tienen tablas: siguen en revisión manual («Sin conjunto publicado»).
--
-- NO EDITAR A MANO: se genera con backend/tools/mcas/generar_seed_hoja2.py
--   python tools/mcas/generar_seed_hoja2.py "<ruta>/COMISIONES NEXO A 16032026 1.xlsx" \
--       --listado "<ruta>/LISTADO DATOS AUTOMATIZACION SERVICIOS 1.xlsx"
--
-- Origen (SHA-256):
--   COMISIONES NEXO A 16032026 1.xlsx
--   654D4AF590BC084500EDD3F594862121601B00E9A84847E1FB78DEB695C5197D
--   LISTADO DATOS AUTOMATIZACION SERVICIOS 1.xlsx
--   4D5D5174AED1BDDF1CC167AA701159E6163E2F5D2798202AAE11B4057893CAC8
--   Textos de la Hoja2 verificados contra el LISTADO: 26 comprobaciones.
-- ============================================================


-- ======================================================================
-- ENDESA
-- Hoja2 K3 (cliente): pyme = matriz de la tarifa de acceso (potencia × consumo), residencial =
-- 110 € con menos de 10 kW y 120 € con 10 kW o más, renovación y gas = manual.
-- Excel hoja ENDESA, actualización F1 = 2026-03-09.
-- ======================================================================

-- Conjunto ENDESA-FIJO-20260309 (BORRADOR, fase FIJO, vigente desde 2026-03-09)
INSERT INTO mcas_conjuntos_reglas
    (codigo, version, id_comercializadora, id_gestora, fase_precio, vigencia_desde, vigencia_hasta,
     estado, archivo_origen, hoja_origen, fecha_interna_origen, hash_origen,
     creado_por, aprobado_por, fecha_creacion, fecha_aprobacion)
SELECT 'ENDESA-FIJO-20260309', '2026.03.09', c.id, NULL, 'FIJO', '2026-03-09', NULL,
       'BORRADOR', 'COMISIONES NEXO A 16032026 1.xlsx', 'ENDESA', '2026-03-09',
       '654D4AF590BC084500EDD3F594862121601B00E9A84847E1FB78DEB695C5197D',
       'seed-hoja2', NULL, CURRENT_TIMESTAMP, NULL
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'ENDESA%' AND UPPER(c.nombre) NOT LIKE '%XXI%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'ENDESA%' AND UPPER(x.nombre) NOT LIKE '%XXI%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_conjuntos_reglas x WHERE x.codigo = 'ENDESA-FIJO-20260309');

-- ENDESA_RENOVACION_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'ENDESA_RENOVACION_MANUAL', 10, 'MANUAL',
       '{"es_renovacion":true}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'Endesa: las renovaciones se introducen manualmente (Hoja2)',
       'Hoja2 K3 (cliente): renovación = manual. Excel ENDESA B83:I130 (renovación pymes) y F11 (B2C no comisiona) no se usan',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'ENDESA-FIJO-20260309' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'ENDESA_RENOVACION_MANUAL');

-- ENDESA_GAS_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'ENDESA_GAS_MANUAL', 20, 'MANUAL',
       '{"tipo_suministro":"GAS","es_renovacion":false}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'Endesa: el gas se introduce manualmente (Hoja2)',
       'Hoja2 K3 (cliente): gas = manual. Excel ENDESA C12:C14 (gas B2C) y B64:E74 (gas pymes) no se usan',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'ENDESA-FIJO-20260309' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'ENDESA_GAS_MANUAL');

-- ENDESA_RESIDENCIAL_MENOS_10KW · FIXED_AMOUNT 110 €
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'ENDESA_RESIDENCIAL_MENOS_10KW', 30, 'FIXED_AMOUNT',
       '{"tipo_suministro":"LUZ","segmento":"RESIDENCIAL","es_renovacion":false,"potencia_max_kw":10,"potencia_max_inc":false}',
       '{"importe":110}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K3 (cliente): menos de 10 kW = 110 €. Excel ENDESA C7:C11 indica 88 € ≤10 kW y 160 € >10 kW',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'ENDESA-FIJO-20260309' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'ENDESA_RESIDENCIAL_MENOS_10KW');

-- ENDESA_RESIDENCIAL_10KW_O_MAS · FIXED_AMOUNT 120 €
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'ENDESA_RESIDENCIAL_10KW_O_MAS', 40, 'FIXED_AMOUNT',
       '{"tipo_suministro":"LUZ","segmento":"RESIDENCIAL","es_renovacion":false,"potencia_min_kw":10,"potencia_min_inc":true}',
       '{"importe":120}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K3 (cliente): 10 kW o más = 120 €. Excel ENDESA C7:C11 indica 88 € ≤10 kW y 160 € >10 kW',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'ENDESA-FIJO-20260309' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'ENDESA_RESIDENCIAL_10KW_O_MAS');

-- ENDESA_PYME_LUZ_2_0TD · MATRIX_LOOKUP (22 celdas)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'ENDESA_PYME_LUZ_2_0TD', 50, 'MATRIX_LOOKUP',
       '{"tipo_suministro":"LUZ","segmento":"PYME","tarifa_acceso":"2.0TD","es_renovacion":false}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Excel ENDESA B18:D28 (potencias C17:D17), consumo kWh × potencia kW redondeado a 2 decimales. Hoja2 K3: pyme = tabla de la tarifa. Más de 15 kW: sin celda (revisión manual)',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'ENDESA-FIJO-20260309' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'ENDESA_PYME_LUZ_2_0TD');

-- ENDESA_PYME_LUZ_3_0TD · MATRIX_LOOKUP (105 celdas)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'ENDESA_PYME_LUZ_3_0TD', 60, 'MATRIX_LOOKUP',
       '{"tipo_suministro":"LUZ","segmento":"PYME","tarifa_acceso":"3.0TD","es_renovacion":false}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Excel ENDESA B31:I45 (potencias C30:I30), consumo kWh × potencia kW redondeado a 2 decimales. Hoja2 K3: pyme = tabla de la tarifa. Menos de 15 kW: sin celda (revisión manual)',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'ENDESA-FIJO-20260309' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'ENDESA_PYME_LUZ_3_0TD');

-- ENDESA_PYME_LUZ_6_1TD · MATRIX_LOOKUP (90 celdas)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'ENDESA_PYME_LUZ_6_1TD', 70, 'MATRIX_LOOKUP',
       '{"tipo_suministro":"LUZ","segmento":"PYME","tarifa_acceso":"6.1TD","es_renovacion":false}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Excel ENDESA B48:H62 (potencias C47:H47), consumo kWh × potencia kW redondeado a 2 decimales. Hoja2 K3: pyme = tabla de la tarifa',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'ENDESA-FIJO-20260309' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'ENDESA_PYME_LUZ_6_1TD');

-- Valores de ENDESA_PYME_LUZ_2_0TD: Pymes luz 2.0TD: ENDESA B17:D28. 22 celdas
-- (cada celda = un «orden» con 2 filas, una por dimensión, con el mismo importe). Se insertan solo si la regla aún no tiene valores.
INSERT INTO mcas_valores_rango
    (id_regla, dimension, valor_min, valor_max, min_inclusivo, max_inclusivo,
     valor_categoria, valor_resultado, unidad_resultado, orden)
SELECT r.id, v.dimension, v.valor_min, v.valor_max, v.min_inc, v.max_inc,
       v.categoria, v.importe, 'EUR', v.orden
FROM mcas_reglas_comision r
JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
CROSS JOIN (
    --               orden,     dimension, valor_min, valor_max, min_inc, max_inc, categoria, importe
              SELECT 1 AS orden, 'consumo_kwh' AS dimension, 0 AS valor_min, 5000 AS valor_max, TRUE AS min_inc, TRUE AS max_inc, NULL AS categoria, 46.80 AS importe  -- C18  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     1, 'potencia_kw',         0,        10,    TRUE,    TRUE,      NULL,   46.80  --      potencia_kw [0, 10] kW
    UNION ALL SELECT     2, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  115.44  -- D18  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     2, 'potencia_kw',        10,        15,   FALSE,    TRUE,      NULL,  115.44  --      potencia_kw (10, 15] kW
    UNION ALL SELECT     3, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,   43.68  -- C19  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT     3, 'potencia_kw',         0,        10,    TRUE,    TRUE,      NULL,   43.68  --      potencia_kw [0, 10] kW
    UNION ALL SELECT     4, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  115.44  -- D19  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT     4, 'potencia_kw',        10,        15,   FALSE,    TRUE,      NULL,  115.44  --      potencia_kw (10, 15] kW
    UNION ALL SELECT     5, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,   56.16  -- C20  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT     5, 'potencia_kw',         0,        10,    TRUE,    TRUE,      NULL,   56.16  --      potencia_kw [0, 10] kW
    UNION ALL SELECT     6, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  159.12  -- D20  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT     6, 'potencia_kw',        10,        15,   FALSE,    TRUE,      NULL,  159.12  --      potencia_kw (10, 15] kW
    UNION ALL SELECT     7, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,   56.16  -- C21  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT     7, 'potencia_kw',         0,        10,    TRUE,    TRUE,      NULL,   56.16  --      potencia_kw [0, 10] kW
    UNION ALL SELECT     8, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  190.32  -- D21  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT     8, 'potencia_kw',        10,        15,   FALSE,    TRUE,      NULL,  190.32  --      potencia_kw (10, 15] kW
    UNION ALL SELECT     9, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  117.00  -- C22  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT     9, 'potencia_kw',         0,        10,    TRUE,    TRUE,      NULL,  117.00  --      potencia_kw [0, 10] kW
    UNION ALL SELECT    10, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  249.60  -- D22  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    10, 'potencia_kw',        10,        15,   FALSE,    TRUE,      NULL,  249.60  --      potencia_kw (10, 15] kW
    UNION ALL SELECT    11, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  118.56  -- C23  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    11, 'potencia_kw',         0,        10,    TRUE,    TRUE,      NULL,  118.56  --      potencia_kw [0, 10] kW
    UNION ALL SELECT    12, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  343.20  -- D23  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    12, 'potencia_kw',        10,        15,   FALSE,    TRUE,      NULL,  343.20  --      potencia_kw (10, 15] kW
    UNION ALL SELECT    13, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  161.28  -- C24  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    13, 'potencia_kw',         0,        10,    TRUE,    TRUE,      NULL,  161.28  --      potencia_kw [0, 10] kW
    UNION ALL SELECT    14, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  403.20  -- D24  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    14, 'potencia_kw',        10,        15,   FALSE,    TRUE,      NULL,  403.20  --      potencia_kw (10, 15] kW
    UNION ALL SELECT    15, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  161.28  -- C25  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    15, 'potencia_kw',         0,        10,    TRUE,    TRUE,      NULL,  161.28  --      potencia_kw [0, 10] kW
    UNION ALL SELECT    16, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  403.20  -- D25  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    16, 'potencia_kw',        10,        15,   FALSE,    TRUE,      NULL,  403.20  --      potencia_kw (10, 15] kW
    UNION ALL SELECT    17, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  161.28  -- C26  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    17, 'potencia_kw',         0,        10,    TRUE,    TRUE,      NULL,  161.28  --      potencia_kw [0, 10] kW
    UNION ALL SELECT    18, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  403.20  -- D26  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    18, 'potencia_kw',        10,        15,   FALSE,    TRUE,      NULL,  403.20  --      potencia_kw (10, 15] kW
    UNION ALL SELECT    19, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  161.28  -- C27  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    19, 'potencia_kw',         0,        10,    TRUE,    TRUE,      NULL,  161.28  --      potencia_kw [0, 10] kW
    UNION ALL SELECT    20, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  615.44  -- D27  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    20, 'potencia_kw',        10,        15,   FALSE,    TRUE,      NULL,  615.44  --      potencia_kw (10, 15] kW
    UNION ALL SELECT    21, 'consumo_kwh',    300000,      NULL,   FALSE,   FALSE,      NULL,  146.83  -- C28  consumo_kwh (300.000, ∞) kWh
    UNION ALL SELECT    21, 'potencia_kw',         0,        10,    TRUE,    TRUE,      NULL,  146.83  --      potencia_kw [0, 10] kW
    UNION ALL SELECT    22, 'consumo_kwh',    300000,      NULL,   FALSE,   FALSE,      NULL,  534.52  -- D28  consumo_kwh (300.000, ∞) kWh
    UNION ALL SELECT    22, 'potencia_kw',        10,        15,   FALSE,    TRUE,      NULL,  534.52  --      potencia_kw (10, 15] kW
) v
WHERE cr.codigo = 'ENDESA-FIJO-20260309' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND r.codigo = 'ENDESA_PYME_LUZ_2_0TD'
  AND NOT EXISTS (SELECT 1 FROM mcas_valores_rango x WHERE x.id_regla = r.id);

-- Valores de ENDESA_PYME_LUZ_3_0TD: Pymes luz 3.0TD: ENDESA B30:I45. 105 celdas
-- (cada celda = un «orden» con 2 filas, una por dimensión, con el mismo importe). Se insertan solo si la regla aún no tiene valores.
INSERT INTO mcas_valores_rango
    (id_regla, dimension, valor_min, valor_max, min_inclusivo, max_inclusivo,
     valor_categoria, valor_resultado, unidad_resultado, orden)
SELECT r.id, v.dimension, v.valor_min, v.valor_max, v.min_inc, v.max_inc,
       v.categoria, v.importe, 'EUR', v.orden
FROM mcas_reglas_comision r
JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
CROSS JOIN (
    --               orden,     dimension, valor_min, valor_max, min_inc, max_inc, categoria, importe
              SELECT 1 AS orden, 'consumo_kwh' AS dimension, 0 AS valor_min, 5000 AS valor_max, TRUE AS min_inc, TRUE AS max_inc, NULL AS categoria, 212.16 AS importe  -- C31  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     1, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  212.16  --      potencia_kw (15, 30] kW
    UNION ALL SELECT     2, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  258.96  -- D31  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     2, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  258.96  --      potencia_kw (30, 50] kW
    UNION ALL SELECT     3, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  349.44  -- E31  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     3, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  349.44  --      potencia_kw (50, 75] kW
    UNION ALL SELECT     4, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  396.24  -- F31  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     4, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  396.24  --      potencia_kw (75, 100] kW
    UNION ALL SELECT     5, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  424.32  -- G31  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     5, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  424.32  --      potencia_kw (100, 150] kW
    UNION ALL SELECT     6, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  446.16  -- H31  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     6, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  446.16  --      potencia_kw (150, 200] kW
    UNION ALL SELECT     7, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  561.60  -- I31  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     7, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  561.60  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT     8, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  212.16  -- C32  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT     8, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  212.16  --      potencia_kw (15, 30] kW
    UNION ALL SELECT     9, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  258.96  -- D32  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT     9, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  258.96  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    10, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  349.44  -- E32  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT    10, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  349.44  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    11, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  396.24  -- F32  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT    11, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  396.24  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    12, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  424.32  -- G32  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT    12, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  424.32  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    13, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  446.16  -- H32  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT    13, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  446.16  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    14, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  561.60  -- I32  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT    14, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  561.60  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    15, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  212.16  -- C33  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    15, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  212.16  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    16, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  258.96  -- D33  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    16, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  258.96  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    17, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  349.44  -- E33  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    17, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  349.44  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    18, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  396.24  -- F33  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    18, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  396.24  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    19, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  424.32  -- G33  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    19, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  424.32  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    20, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  446.16  -- H33  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    20, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  446.16  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    21, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  561.60  -- I33  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    21, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  561.60  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    22, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  212.16  -- C34  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    22, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  212.16  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    23, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  258.96  -- D34  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    23, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  258.96  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    24, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  349.44  -- E34  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    24, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  349.44  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    25, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  396.24  -- F34  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    25, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  396.24  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    26, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  424.32  -- G34  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    26, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  424.32  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    27, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  446.16  -- H34  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    27, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  446.16  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    28, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  561.60  -- I34  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    28, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  561.60  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    29, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  243.36  -- C35  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    29, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  243.36  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    30, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  315.12  -- D35  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    30, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  315.12  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    31, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  380.64  -- E35  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    31, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  380.64  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    32, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  427.44  -- F35  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    32, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  427.44  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    33, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  455.52  -- G35  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    33, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  455.52  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    34, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  477.36  -- H35  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    34, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  477.36  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    35, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  592.80  -- I35  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    35, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  592.80  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    36, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  411.84  -- C36  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    36, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  411.84  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    37, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  411.84  -- D36  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    37, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  411.84  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    38, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  411.84  -- E36  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    38, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  411.84  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    39, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  458.64  -- F36  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    39, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  458.64  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    40, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  486.72  -- G36  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    40, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  486.72  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    41, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  508.56  -- H36  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    41, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  508.56  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    42, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  624.00  -- I36  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    42, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  624.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    43, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  527.52  -- C37  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    43, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  527.52  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    44, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  527.52  -- D37  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    44, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  527.52  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    45, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  527.52  -- E37  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    45, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  527.52  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    46, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  527.52  -- F37  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    46, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  527.52  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    47, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  557.76  -- G37  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    47, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  557.76  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    48, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  581.28  -- H37  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    48, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  581.28  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    49, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  705.60  -- I37  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    49, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  705.60  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    50, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  557.76  -- C38  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    50, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  557.76  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    51, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  557.76  -- D38  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    51, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  557.76  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    52, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  557.76  -- E38  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    52, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  557.76  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    53, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  557.76  -- F38  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    53, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  557.76  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    54, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  557.76  -- G38  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    54, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  557.76  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    55, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  581.28  -- H38  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    55, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  581.28  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    56, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  705.60  -- I38  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    56, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  705.60  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    57, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  581.28  -- C39  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    57, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  581.28  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    58, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  581.28  -- D39  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    58, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  581.28  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    59, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  581.28  -- E39  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    59, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  581.28  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    60, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  581.28  -- F39  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    60, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  581.28  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    61, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  581.28  -- G39  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    61, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  581.28  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    62, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  581.28  -- H39  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    62, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  581.28  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    63, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  705.60  -- I39  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    63, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  705.60  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    64, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  705.60  -- C40  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    64, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  705.60  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    65, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  705.60  -- D40  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    65, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  705.60  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    66, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  705.60  -- E40  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    66, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  705.60  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    67, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  705.60  -- F40  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    67, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  705.60  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    68, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  705.60  -- G40  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    68, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  705.60  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    69, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  705.60  -- H40  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    69, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  705.60  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    70, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  705.60  -- I40  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    70, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  705.60  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    71, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  643.84  -- C41  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    71, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  643.84  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    72, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  645.04  -- D41  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    72, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  645.04  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    73, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  647.36  -- E41  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    73, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  647.36  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    74, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  648.56  -- F41  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    74, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  648.56  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    75, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  649.28  -- G41  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    75, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  649.28  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    76, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  649.84  -- H41  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    76, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  649.84  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    77, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  652.80  -- I41  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    77, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  652.80  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    78, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  661.44  -- C42  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    78, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  661.44  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    79, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  662.64  -- D42  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    79, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  662.64  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    80, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  664.96  -- E42  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    80, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  664.96  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    81, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  666.16  -- F42  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    81, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  666.16  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    82, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  666.88  -- G42  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    82, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  666.88  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    83, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  667.44  -- H42  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    83, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  667.44  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    84, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  670.40  -- I42  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    84, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  670.40  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    85, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  705.44  -- C43  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    85, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  705.44  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    86, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  706.64  -- D43  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    86, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  706.64  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    87, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  708.96  -- E43  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    87, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  708.96  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    88, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  710.16  -- F43  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    88, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  710.16  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    89, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  710.88  -- G43  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    89, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  710.88  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    90, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  711.44  -- H43  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    90, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  711.44  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    91, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  714.40  -- I43  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    91, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  714.40  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    92, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  771.44  -- C44  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    92, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  771.44  --      potencia_kw (15, 30] kW
    UNION ALL SELECT    93, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  772.64  -- D44  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    93, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  772.64  --      potencia_kw (30, 50] kW
    UNION ALL SELECT    94, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  774.96  -- E44  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    94, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  774.96  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    95, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  776.16  -- F44  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    95, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  776.16  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    96, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  776.88  -- G44  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    96, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  776.88  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    97, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  777.44  -- H44  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    97, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  777.44  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    98, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  780.40  -- I44  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    98, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  780.40  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    99, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  881.44  -- C45  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT    99, 'potencia_kw',        15,        30,   FALSE,    TRUE,      NULL,  881.44  --      potencia_kw (15, 30] kW
    UNION ALL SELECT   100, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  882.64  -- D45  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT   100, 'potencia_kw',        30,        50,   FALSE,    TRUE,      NULL,  882.64  --      potencia_kw (30, 50] kW
    UNION ALL SELECT   101, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  884.96  -- E45  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT   101, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  884.96  --      potencia_kw (50, 75] kW
    UNION ALL SELECT   102, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  886.16  -- F45  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT   102, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  886.16  --      potencia_kw (75, 100] kW
    UNION ALL SELECT   103, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  886.88  -- G45  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT   103, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  886.88  --      potencia_kw (100, 150] kW
    UNION ALL SELECT   104, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  887.44  -- H45  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT   104, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  887.44  --      potencia_kw (150, 200] kW
    UNION ALL SELECT   105, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  890.40  -- I45  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT   105, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  890.40  --      potencia_kw (200, ∞) kW
) v
WHERE cr.codigo = 'ENDESA-FIJO-20260309' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND r.codigo = 'ENDESA_PYME_LUZ_3_0TD'
  AND NOT EXISTS (SELECT 1 FROM mcas_valores_rango x WHERE x.id_regla = r.id);

-- Valores de ENDESA_PYME_LUZ_6_1TD: Pymes luz 6.1TD: ENDESA B47:H62. 90 celdas
-- (cada celda = un «orden» con 2 filas, una por dimensión, con el mismo importe). Se insertan solo si la regla aún no tiene valores.
INSERT INTO mcas_valores_rango
    (id_regla, dimension, valor_min, valor_max, min_inclusivo, max_inclusivo,
     valor_categoria, valor_resultado, unidad_resultado, orden)
SELECT r.id, v.dimension, v.valor_min, v.valor_max, v.min_inc, v.max_inc,
       v.categoria, v.importe, 'EUR', v.orden
FROM mcas_reglas_comision r
JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
CROSS JOIN (
    --               orden,     dimension, valor_min, valor_max, min_inc, max_inc, categoria, importe
              SELECT 1 AS orden, 'consumo_kwh' AS dimension, 0 AS valor_min, 5000 AS valor_max, TRUE AS min_inc, TRUE AS max_inc, NULL AS categoria, 212.16 AS importe  -- C48  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     1, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  212.16  --      potencia_kw (0, 50] kW
    UNION ALL SELECT     2, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  260.00  -- D48  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     2, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  260.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT     3, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  325.00  -- E48  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     3, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  325.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT     4, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  338.00  -- F48  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     4, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  338.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT     5, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  358.80  -- G48  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     5, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  358.80  --      potencia_kw (150, 200] kW
    UNION ALL SELECT     6, 'consumo_kwh',         0,      5000,    TRUE,    TRUE,      NULL,  429.00  -- H48  consumo_kwh [0, 5.000] kWh
    UNION ALL SELECT     6, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  429.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT     7, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  212.16  -- C49  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT     7, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  212.16  --      potencia_kw (0, 50] kW
    UNION ALL SELECT     8, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  260.00  -- D49  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT     8, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  260.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT     9, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  325.00  -- E49  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT     9, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  325.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    10, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  338.00  -- F49  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT    10, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  338.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    11, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  358.80  -- G49  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT    11, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  358.80  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    12, 'consumo_kwh',      5000,     10000,   FALSE,    TRUE,      NULL,  429.00  -- H49  consumo_kwh (5.000, 10.000] kWh
    UNION ALL SELECT    12, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  429.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    13, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  212.16  -- C50  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    13, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  212.16  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    14, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  260.00  -- D50  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    14, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  260.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    15, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  325.00  -- E50  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    15, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  325.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    16, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  338.00  -- F50  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    16, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  338.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    17, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  358.80  -- G50  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    17, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  358.80  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    18, 'consumo_kwh',     10000,     20000,   FALSE,    TRUE,      NULL,  429.00  -- H50  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    18, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  429.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    19, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  212.16  -- C51  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    19, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  212.16  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    20, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  260.00  -- D51  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    20, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  260.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    21, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  325.00  -- E51  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    21, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  325.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    22, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  338.00  -- F51  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    22, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  338.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    23, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  358.80  -- G51  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    23, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  358.80  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    24, 'consumo_kwh',     20000,     30000,   FALSE,    TRUE,      NULL,  429.00  -- H51  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    24, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  429.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    25, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  243.36  -- C52  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    25, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  243.36  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    26, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  286.00  -- D52  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    26, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  286.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    27, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  351.00  -- E52  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    27, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  351.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    28, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  364.00  -- F52  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    28, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  364.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    29, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  384.80  -- G52  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    29, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  384.80  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    30, 'consumo_kwh',     30000,     50000,   FALSE,    TRUE,      NULL,  455.00  -- H52  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    30, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  455.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    31, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  343.20  -- C53  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    31, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  343.20  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    32, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  343.20  -- D53  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    32, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  343.20  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    33, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  377.00  -- E53  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    33, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  377.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    34, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  390.00  -- F53  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    34, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  390.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    35, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  410.80  -- G53  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    35, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  410.80  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    36, 'consumo_kwh',     50000,    100000,   FALSE,    TRUE,      NULL,  481.00  -- H53  consumo_kwh (50.000, 100.000] kWh
    UNION ALL SELECT    36, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  481.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    37, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  434.00  -- C54  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    37, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  434.00  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    38, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  434.00  -- D54  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    38, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  434.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    39, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  434.00  -- E54  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    39, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  434.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    40, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  448.00  -- F54  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    40, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  448.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    41, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  470.40  -- G54  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    41, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  470.40  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    42, 'consumo_kwh',    100000,    150000,   FALSE,    TRUE,      NULL,  546.00  -- H54  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    42, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  546.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    43, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  448.00  -- C55  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    43, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  448.00  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    44, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  448.00  -- D55  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    44, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  448.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    45, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  448.00  -- E55  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    45, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  448.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    46, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  448.00  -- F55  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    46, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  448.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    47, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  470.40  -- G55  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    47, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  470.40  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    48, 'consumo_kwh',    150000,    200000,   FALSE,    TRUE,      NULL,  546.00  -- H55  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    48, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  546.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    49, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  470.40  -- C56  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    49, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  470.40  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    50, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  470.40  -- D56  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    50, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  470.40  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    51, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  470.40  -- E56  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    51, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  470.40  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    52, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  470.40  -- F56  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    52, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  470.40  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    53, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  470.40  -- G56  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    53, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  470.40  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    54, 'consumo_kwh',    200000,    250000,   FALSE,    TRUE,      NULL,  546.00  -- H56  consumo_kwh (200.000, 250.000] kWh
    UNION ALL SELECT    54, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  546.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    55, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  616.00  -- C57  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    55, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  616.00  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    56, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  616.00  -- D57  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    56, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  616.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    57, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  616.00  -- E57  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    57, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  616.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    58, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  616.00  -- F57  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    58, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  616.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    59, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  616.00  -- G57  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    59, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  616.00  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    60, 'consumo_kwh',    250000,    300000,   FALSE,    TRUE,      NULL,  616.00  -- H57  consumo_kwh (250.000, 300.000] kWh
    UNION ALL SELECT    60, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  616.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    61, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  606.00  -- C58  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    61, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  606.00  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    62, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  606.00  -- D58  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    62, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  606.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    63, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  606.00  -- E58  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    63, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  606.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    64, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  606.00  -- F58  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    64, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  606.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    65, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  606.00  -- G58  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    65, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  606.00  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    66, 'consumo_kwh',    300000,    400000,   FALSE,    TRUE,      NULL,  606.00  -- H58  consumo_kwh (300.000, 400.000] kWh
    UNION ALL SELECT    66, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  606.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    67, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  650.00  -- C59  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    67, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  650.00  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    68, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  650.00  -- D59  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    68, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  650.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    69, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  650.00  -- E59  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    69, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  650.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    70, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  650.00  -- F59  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    70, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  650.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    71, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  650.00  -- G59  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    71, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  650.00  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    72, 'consumo_kwh',    400000,    500000,   FALSE,    TRUE,      NULL,  650.00  -- H59  consumo_kwh (400.000, 500.000] kWh
    UNION ALL SELECT    72, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  650.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    73, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  694.00  -- C60  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    73, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  694.00  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    74, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  694.00  -- D60  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    74, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  694.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    75, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  694.00  -- E60  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    75, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  694.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    76, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  694.00  -- F60  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    76, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  694.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    77, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  694.00  -- G60  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    77, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  694.00  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    78, 'consumo_kwh',    500000,    600000,   FALSE,    TRUE,      NULL,  694.00  -- H60  consumo_kwh (500.000, 600.000] kWh
    UNION ALL SELECT    78, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  694.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    79, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  760.00  -- C61  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    79, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  760.00  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    80, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  760.00  -- D61  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    80, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  760.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    81, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  760.00  -- E61  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    81, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  760.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    82, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  760.00  -- F61  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    82, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  760.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    83, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  760.00  -- G61  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    83, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  760.00  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    84, 'consumo_kwh',    600000,    800000,   FALSE,    TRUE,      NULL,  760.00  -- H61  consumo_kwh (600.000, 800.000] kWh
    UNION ALL SELECT    84, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  760.00  --      potencia_kw (200, ∞) kW
    UNION ALL SELECT    85, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  870.00  -- C62  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT    85, 'potencia_kw',         0,        50,   FALSE,    TRUE,      NULL,  870.00  --      potencia_kw (0, 50] kW
    UNION ALL SELECT    86, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  870.00  -- D62  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT    86, 'potencia_kw',        50,        75,   FALSE,    TRUE,      NULL,  870.00  --      potencia_kw (50, 75] kW
    UNION ALL SELECT    87, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  870.00  -- E62  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT    87, 'potencia_kw',        75,       100,   FALSE,    TRUE,      NULL,  870.00  --      potencia_kw (75, 100] kW
    UNION ALL SELECT    88, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  870.00  -- F62  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT    88, 'potencia_kw',       100,       150,   FALSE,    TRUE,      NULL,  870.00  --      potencia_kw (100, 150] kW
    UNION ALL SELECT    89, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  870.00  -- G62  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT    89, 'potencia_kw',       150,       200,   FALSE,    TRUE,      NULL,  870.00  --      potencia_kw (150, 200] kW
    UNION ALL SELECT    90, 'consumo_kwh',    800000,      NULL,   FALSE,   FALSE,      NULL,  870.00  -- H62  consumo_kwh (800.000, ∞) kWh
    UNION ALL SELECT    90, 'potencia_kw',       200,      NULL,   FALSE,   FALSE,      NULL,  870.00  --      potencia_kw (200, ∞) kW
) v
WHERE cr.codigo = 'ENDESA-FIJO-20260309' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND r.codigo = 'ENDESA_PYME_LUZ_6_1TD'
  AND NOT EXISTS (SELECT 1 FROM mcas_valores_rango x WHERE x.id_regla = r.id);

-- Política RET-ENDESA-RESIDENCIAL-20260309 (PORCENTAJE, segmento RESIDENCIAL)
INSERT INTO mcas_politicas_retrocesion
    (codigo, descripcion, id_comercializadora, id_gestora, version, vigencia_desde, vigencia_hasta,
     tipo_suministro, segmento, tarifa_acceso, metodo, limite_importe_pagado, archivo_origen, estado)
SELECT 'RET-ENDESA-RESIDENCIAL-20260309',
       'Retrocomisión Endesa doméstico por meses desde la activación (Excel ENDESA B76:E78).',
       c.id, NULL, '2026.03.09', '2026-03-09', NULL,
       NULL, 'RESIDENCIAL', NULL, 'PORCENTAJE', TRUE,
       'COMISIONES NEXO A 16032026 1.xlsx', 'BORRADOR'
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'ENDESA%' AND UPPER(c.nombre) NOT LIKE '%XXI%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'ENDESA%' AND UPPER(x.nombre) NOT LIKE '%XXI%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_politicas_retrocesion x WHERE x.codigo = 'RET-ENDESA-RESIDENCIAL-20260309');

-- Tramos de RET-ENDESA-RESIDENCIAL-20260309 (solo si la política sigue en BORRADOR y aún no tiene tramos)
INSERT INTO mcas_tramos_retrocesion
    (id_politica, unidad, desde_valor, hasta_valor, desde_inclusivo, hasta_inclusivo,
     valor, tipo_valor, prioridad)
SELECT p.id, t.unidad, t.desde, t.hasta, t.desde_inc, t.hasta_inc, t.valor, t.tipo_valor, t.prioridad
FROM mcas_politicas_retrocesion p
CROSS JOIN (
    --                unidad, desde, hasta, desde_inc, hasta_inc, valor,   tipo_valor, prioridad
              SELECT 'MESES' AS unidad, 0 AS desde, 3 AS hasta, TRUE AS desde_inc, FALSE AS hasta_inc, 1.00 AS valor, 'PORCENTAJE' AS tipo_valor, 10 AS prioridad  -- D78 «0 a 3»: [0, 3) meses = 100 %
    UNION ALL SELECT 'MESES',     3,    12,      TRUE,     FALSE,  0.10, 'PORCENTAJE',        20  -- E78 «3 a 12»: [3, 12) meses = 10 %
) t
WHERE p.codigo = 'RET-ENDESA-RESIDENCIAL-20260309' AND p.estado = 'BORRADOR'
  AND NOT EXISTS (SELECT 1 FROM mcas_tramos_retrocesion x WHERE x.id_politica = p.id);

-- Política RET-ENDESA-PYME-20260309 (PORCENTAJE, segmento PYME)
INSERT INTO mcas_politicas_retrocesion
    (codigo, descripcion, id_comercializadora, id_gestora, version, vigencia_desde, vigencia_hasta,
     tipo_suministro, segmento, tarifa_acceso, metodo, limite_importe_pagado, archivo_origen, estado)
SELECT 'RET-ENDESA-PYME-20260309',
       'Retrocomisión Endesa pymes y AFF por meses desde la activación (Excel ENDESA B80:G81).',
       c.id, NULL, '2026.03.09', '2026-03-09', NULL,
       NULL, 'PYME', NULL, 'PORCENTAJE', TRUE,
       'COMISIONES NEXO A 16032026 1.xlsx', 'BORRADOR'
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'ENDESA%' AND UPPER(c.nombre) NOT LIKE '%XXI%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'ENDESA%' AND UPPER(x.nombre) NOT LIKE '%XXI%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_politicas_retrocesion x WHERE x.codigo = 'RET-ENDESA-PYME-20260309');

-- Tramos de RET-ENDESA-PYME-20260309 (solo si la política sigue en BORRADOR y aún no tiene tramos)
INSERT INTO mcas_tramos_retrocesion
    (id_politica, unidad, desde_valor, hasta_valor, desde_inclusivo, hasta_inclusivo,
     valor, tipo_valor, prioridad)
SELECT p.id, t.unidad, t.desde, t.hasta, t.desde_inc, t.hasta_inc, t.valor, t.tipo_valor, t.prioridad
FROM mcas_politicas_retrocesion p
CROSS JOIN (
    --                unidad, desde, hasta, desde_inc, hasta_inc, valor,   tipo_valor, prioridad
              SELECT 'MESES' AS unidad, 0 AS desde, 3 AS hasta, TRUE AS desde_inc, FALSE AS hasta_inc, 1.00 AS valor, 'PORCENTAJE' AS tipo_valor, 10 AS prioridad  -- D81 «0 a 3»: [0, 3) meses = 100 %
    UNION ALL SELECT 'MESES',     3,     6,      TRUE,     FALSE,  0.60, 'PORCENTAJE',        20  -- E81 «3 a 6»: [3, 6) meses = 60 %
    UNION ALL SELECT 'MESES',     6,     9,      TRUE,     FALSE,  0.40, 'PORCENTAJE',        30  -- F81 «6 a 9»: [6, 9) meses = 40 %
    UNION ALL SELECT 'MESES',     9,    12,      TRUE,     FALSE,  0.20, 'PORCENTAJE',        40  -- G81 «9 a 12»: [9, 12) meses = 20 %
) t
WHERE p.codigo = 'RET-ENDESA-PYME-20260309' AND p.estado = 'BORRADOR'
  AND NOT EXISTS (SELECT 1 FROM mcas_tramos_retrocesion x WHERE x.id_politica = p.id);


-- ======================================================================
-- IBERDROLA
-- Hoja2 K9 (cliente): dentro de ámbito, indexado y gas = manual. Fuera de ámbito = tabla por potencia
-- (y por consumo si Pc > 20 kW), zona ZBR/ZAR y oferta (tarifa de precios del contrato).
-- Excel hoja IBERDROLA, actualización F2 = 2026-01-01. Indexado: sin conjunto INDEXADO (queda manual).
-- ======================================================================

-- Productos (tarifa de precios del contrato): OFERTA_0, OFERTA_1, OFERTA_2, OFERTA_3
INSERT INTO mcas_productos_comerciales
    (id_comercializadora, codigo, nombre, tipo_precio, tipo_suministro, activo_desde, activo)
SELECT c.id, s.codigo, s.nombre, s.tipo_precio, s.tipo_suministro, s.activo_desde, TRUE
FROM tbcomercializadoras c
CROSS JOIN (
    SELECT 'OFERTA_0' AS codigo, 'Oferta 0 — Plan exclusivo 15%, OMIE CCPP (2.0TD) · OMIE 0, Plan exclusivo 15%, Plan Regantes, Comunidad Exclusivo (3.0TD/6.1TD)' AS nombre,
           'FIJO' AS tipo_precio, 'LUZ' AS tipo_suministro,
           '2026-01-01' AS activo_desde   -- IBERDROLA C86,C89
    UNION ALL SELECT 'OFERTA_1', 'Oferta 1 — Planes IMPULSA, Planes a Tu Medida, Plan CCPP Estable, Plan Hogar (2.0TD) · OMIE 1, Planes IMPULSA, Planes a tu Medida, Plan exclusivo 26%, Comunidad Impulsa (3.0TD/6.1TD)',
           'FIJO', 'LUZ', '2026-01-01'   -- IBERDROLA C87,C90
    UNION ALL SELECT 'OFERTA_2', 'Oferta 2 — Plan especial PLUS 15%, Plan Ahora, Plan Contigo (2.0TD) · OMIE 2, Empresas 10%, Plan exclusivo 30%, plan verde/empresa verde, Empresas Plus, Comunidad Óptima (3.0TD/6.1TD)',
           'FIJO', 'LUZ', '2026-01-01'   -- IBERDROLA C88,C91
    UNION ALL SELECT 'OFERTA_3', 'Oferta 3 — OMIE 3, Plan Especial 15% (3.0TD/6.1TD)',
           'FIJO', 'LUZ', '2026-01-01'   -- IBERDROLA C92
) s
WHERE UPPER(TRIM(c.nombre)) LIKE 'IBERDROLA%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'IBERDROLA%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_productos_comerciales p
                  WHERE p.id_comercializadora = c.id AND p.codigo = s.codigo);

-- Conjunto IBERDROLA-FIJO-20260101 (BORRADOR, fase FIJO, vigente desde 2026-01-01)
INSERT INTO mcas_conjuntos_reglas
    (codigo, version, id_comercializadora, id_gestora, fase_precio, vigencia_desde, vigencia_hasta,
     estado, archivo_origen, hoja_origen, fecha_interna_origen, hash_origen,
     creado_por, aprobado_por, fecha_creacion, fecha_aprobacion)
SELECT 'IBERDROLA-FIJO-20260101', '2026.01.01', c.id, NULL, 'FIJO', '2026-01-01', NULL,
       'BORRADOR', 'COMISIONES NEXO A 16032026 1.xlsx', 'IBERDROLA', '2026-01-01',
       '654D4AF590BC084500EDD3F594862121601B00E9A84847E1FB78DEB695C5197D',
       'seed-hoja2', NULL, CURRENT_TIMESTAMP, NULL
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'IBERDROLA%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'IBERDROLA%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_conjuntos_reglas x WHERE x.codigo = 'IBERDROLA-FIJO-20260101');

-- IBERDROLA_RENOVACION_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'IBERDROLA_RENOVACION_MANUAL', 10, 'MANUAL',
       '{"es_renovacion":true}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'Iberdrola: las renovaciones se introducen manualmente (el Excel no tiene tabla de renovaciones)',
       'Sin tabla de renovaciones en el Excel IBERDROLA ni regla en la Hoja2 K9',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'IBERDROLA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'IBERDROLA_RENOVACION_MANUAL');

-- IBERDROLA_DENTRO_AMBITO_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'IBERDROLA_DENTRO_AMBITO_MANUAL', 20, 'MANUAL',
       '{"tipo_suministro":"LUZ","ambito":"DENTRO","es_renovacion":false}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'Iberdrola: dentro de ámbito se introduce manualmente (Hoja2)',
       'Hoja2 K9: dentro de ámbito manual. Excel IBERDROLA A42:J77 (dentro de ámbito) no se usa',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'IBERDROLA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'IBERDROLA_DENTRO_AMBITO_MANUAL');

-- IBERDROLA_GAS_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'IBERDROLA_GAS_MANUAL', 30, 'MANUAL',
       '{"tipo_suministro":"GAS","es_renovacion":false}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'Iberdrola: el gas se introduce manualmente (Hoja2)',
       'Hoja2 K9: gas manual, dentro o fuera de ámbito. Excel IBERDROLA A28:F34 y A65:F71 (gas) no se usan',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'IBERDROLA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'IBERDROLA_GAS_MANUAL');

-- IBERDROLA_FUERA_HASTA_20KW · MULTI_DIMENSION_LOOKUP (26 celdas)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'IBERDROLA_FUERA_HASTA_20KW', 40, 'MULTI_DIMENSION_LOOKUP',
       '{"tipo_suministro":"LUZ","ambito":"FUERA","es_renovacion":false,"potencia_max_kw":20,"potencia_max_inc":true}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Excel IBERDROLA A8:J13: potencia × zona (ZBR C:F, ZAR G:J) × oferta. «N. Aplica» (F10:F12, J10:J12) sin celda = revisión manual. Hoja2 K9: fuera de ámbito por tabla',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'IBERDROLA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'IBERDROLA_FUERA_HASTA_20KW');

-- IBERDROLA_FUERA_MAS_20KW · MULTI_DIMENSION_LOOKUP (64 celdas)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'IBERDROLA_FUERA_MAS_20KW', 50, 'MULTI_DIMENSION_LOOKUP',
       '{"tipo_suministro":"LUZ","ambito":"FUERA","es_renovacion":false,"potencia_min_kw":20,"potencia_min_inc":false}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Excel IBERDROLA A15:J24: consumo × zona × oferta. A25 «>200.000 kWh Consultar» sin celda = revisión manual. A26 (1 MWh por kW sin consumo) fuera de alcance. Hoja2 K9',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'IBERDROLA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'IBERDROLA_FUERA_MAS_20KW');

-- Valores de IBERDROLA_FUERA_HASTA_20KW: Fuera de ámbito, Pc ≤ 20 kW: IBERDROLA A8:J13. 26 celdas
-- (cada celda = un «orden» con 3 filas, una por dimensión, con el mismo importe). Se insertan solo si la regla aún no tiene valores.
--   Sin celda (revisión manual): F10 = 'N. Aplica'
--   Sin celda (revisión manual): J10 = 'N. Aplica'
--   Sin celda (revisión manual): F11 = 'N. Aplica'
--   Sin celda (revisión manual): J11 = 'N. Aplica'
--   Sin celda (revisión manual): F12 = 'N. Aplica'
--   Sin celda (revisión manual): J12 = 'N. Aplica'
INSERT INTO mcas_valores_rango
    (id_regla, dimension, valor_min, valor_max, min_inclusivo, max_inclusivo,
     valor_categoria, valor_resultado, unidad_resultado, orden)
SELECT r.id, v.dimension, v.valor_min, v.valor_max, v.min_inc, v.max_inc,
       v.categoria, v.importe, 'EUR', v.orden
FROM mcas_reglas_comision r
JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
CROSS JOIN (
    --               orden,         dimension, valor_min, valor_max, min_inc, max_inc,  categoria, importe
              SELECT 1 AS orden, 'potencia_kw' AS dimension, 0 AS valor_min, 5 AS valor_max, TRUE AS min_inc, TRUE AS max_inc, NULL AS categoria, 74.40 AS importe  -- C10  potencia_kw [0, 5] kW
    UNION ALL SELECT     1,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',   74.40  --      zona_comision = ZBR
    UNION ALL SELECT     1, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',   74.40  --      codigo_producto = OFERTA_0
    UNION ALL SELECT     2,     'potencia_kw',         0,         5,    TRUE,    TRUE,       NULL,   57.60  -- D10  potencia_kw [0, 5] kW
    UNION ALL SELECT     2,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',   57.60  --      zona_comision = ZBR
    UNION ALL SELECT     2, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',   57.60  --      codigo_producto = OFERTA_1
    UNION ALL SELECT     3,     'potencia_kw',         0,         5,    TRUE,    TRUE,       NULL,   42.40  -- E10  potencia_kw [0, 5] kW
    UNION ALL SELECT     3,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',   42.40  --      zona_comision = ZBR
    UNION ALL SELECT     3, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',   42.40  --      codigo_producto = OFERTA_2
    UNION ALL SELECT     4,     'potencia_kw',         0,         5,    TRUE,    TRUE,       NULL,   48.00  -- G10  potencia_kw [0, 5] kW
    UNION ALL SELECT     4,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',   48.00  --      zona_comision = ZAR
    UNION ALL SELECT     4, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',   48.00  --      codigo_producto = OFERTA_0
    UNION ALL SELECT     5,     'potencia_kw',         0,         5,    TRUE,    TRUE,       NULL,   37.60  -- H10  potencia_kw [0, 5] kW
    UNION ALL SELECT     5,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',   37.60  --      zona_comision = ZAR
    UNION ALL SELECT     5, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',   37.60  --      codigo_producto = OFERTA_1
    UNION ALL SELECT     6,     'potencia_kw',         0,         5,    TRUE,    TRUE,       NULL,   28.00  -- I10  potencia_kw [0, 5] kW
    UNION ALL SELECT     6,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',   28.00  --      zona_comision = ZAR
    UNION ALL SELECT     6, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',   28.00  --      codigo_producto = OFERTA_2
    UNION ALL SELECT     7,     'potencia_kw',         5,        10,   FALSE,    TRUE,       NULL,   81.60  -- C11  potencia_kw (5, 10] kW
    UNION ALL SELECT     7,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',   81.60  --      zona_comision = ZBR
    UNION ALL SELECT     7, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',   81.60  --      codigo_producto = OFERTA_0
    UNION ALL SELECT     8,     'potencia_kw',         5,        10,   FALSE,    TRUE,       NULL,   64.00  -- D11  potencia_kw (5, 10] kW
    UNION ALL SELECT     8,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',   64.00  --      zona_comision = ZBR
    UNION ALL SELECT     8, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',   64.00  --      codigo_producto = OFERTA_1
    UNION ALL SELECT     9,     'potencia_kw',         5,        10,   FALSE,    TRUE,       NULL,   47.20  -- E11  potencia_kw (5, 10] kW
    UNION ALL SELECT     9,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',   47.20  --      zona_comision = ZBR
    UNION ALL SELECT     9, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',   47.20  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    10,     'potencia_kw',         5,        10,   FALSE,    TRUE,       NULL,   52.80  -- G11  potencia_kw (5, 10] kW
    UNION ALL SELECT    10,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',   52.80  --      zona_comision = ZAR
    UNION ALL SELECT    10, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',   52.80  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    11,     'potencia_kw',         5,        10,   FALSE,    TRUE,       NULL,   40.00  -- H11  potencia_kw (5, 10] kW
    UNION ALL SELECT    11,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',   40.00  --      zona_comision = ZAR
    UNION ALL SELECT    11, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',   40.00  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    12,     'potencia_kw',         5,        10,   FALSE,    TRUE,       NULL,   30.40  -- I11  potencia_kw (5, 10] kW
    UNION ALL SELECT    12,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',   30.40  --      zona_comision = ZAR
    UNION ALL SELECT    12, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',   30.40  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    13,     'potencia_kw',        10,        15,   FALSE,    TRUE,       NULL,  104.80  -- C12  potencia_kw (10, 15] kW
    UNION ALL SELECT    13,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  104.80  --      zona_comision = ZBR
    UNION ALL SELECT    13, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  104.80  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    14,     'potencia_kw',        10,        15,   FALSE,    TRUE,       NULL,   88.00  -- D12  potencia_kw (10, 15] kW
    UNION ALL SELECT    14,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',   88.00  --      zona_comision = ZBR
    UNION ALL SELECT    14, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',   88.00  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    15,     'potencia_kw',        10,        15,   FALSE,    TRUE,       NULL,   67.20  -- E12  potencia_kw (10, 15] kW
    UNION ALL SELECT    15,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',   67.20  --      zona_comision = ZBR
    UNION ALL SELECT    15, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',   67.20  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    16,     'potencia_kw',        10,        15,   FALSE,    TRUE,       NULL,   88.00  -- G12  potencia_kw (10, 15] kW
    UNION ALL SELECT    16,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',   88.00  --      zona_comision = ZAR
    UNION ALL SELECT    16, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',   88.00  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    17,     'potencia_kw',        10,        15,   FALSE,    TRUE,       NULL,   70.40  -- H12  potencia_kw (10, 15] kW
    UNION ALL SELECT    17,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',   70.40  --      zona_comision = ZAR
    UNION ALL SELECT    17, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',   70.40  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    18,     'potencia_kw',        10,        15,   FALSE,    TRUE,       NULL,   57.60  -- I12  potencia_kw (10, 15] kW
    UNION ALL SELECT    18,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',   57.60  --      zona_comision = ZAR
    UNION ALL SELECT    18, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',   57.60  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    19,     'potencia_kw',        15,        20,   FALSE,    TRUE,       NULL,  244.00  -- C13  potencia_kw (15, 20] kW
    UNION ALL SELECT    19,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  244.00  --      zona_comision = ZBR
    UNION ALL SELECT    19, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  244.00  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    20,     'potencia_kw',        15,        20,   FALSE,    TRUE,       NULL,  189.60  -- D13  potencia_kw (15, 20] kW
    UNION ALL SELECT    20,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  189.60  --      zona_comision = ZBR
    UNION ALL SELECT    20, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  189.60  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    21,     'potencia_kw',        15,        20,   FALSE,    TRUE,       NULL,  128.80  -- E13  potencia_kw (15, 20] kW
    UNION ALL SELECT    21,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  128.80  --      zona_comision = ZBR
    UNION ALL SELECT    21, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  128.80  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    22,     'potencia_kw',        15,        20,   FALSE,    TRUE,       NULL,   91.20  -- F13  potencia_kw (15, 20] kW
    UNION ALL SELECT    22,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',   91.20  --      zona_comision = ZBR
    UNION ALL SELECT    22, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',   91.20  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    23,     'potencia_kw',        15,        20,   FALSE,    TRUE,       NULL,  203.20  -- G13  potencia_kw (15, 20] kW
    UNION ALL SELECT    23,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  203.20  --      zona_comision = ZAR
    UNION ALL SELECT    23, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  203.20  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    24,     'potencia_kw',        15,        20,   FALSE,    TRUE,       NULL,  156.00  -- H13  potencia_kw (15, 20] kW
    UNION ALL SELECT    24,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  156.00  --      zona_comision = ZAR
    UNION ALL SELECT    24, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  156.00  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    25,     'potencia_kw',        15,        20,   FALSE,    TRUE,       NULL,  104.80  -- I13  potencia_kw (15, 20] kW
    UNION ALL SELECT    25,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  104.80  --      zona_comision = ZAR
    UNION ALL SELECT    25, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  104.80  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    26,     'potencia_kw',        15,        20,   FALSE,    TRUE,       NULL,   77.60  -- J13  potencia_kw (15, 20] kW
    UNION ALL SELECT    26,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',   77.60  --      zona_comision = ZAR
    UNION ALL SELECT    26, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',   77.60  --      codigo_producto = OFERTA_3
) v
WHERE cr.codigo = 'IBERDROLA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND r.codigo = 'IBERDROLA_FUERA_HASTA_20KW'
  AND NOT EXISTS (SELECT 1 FROM mcas_valores_rango x WHERE x.id_regla = r.id);

-- Valores de IBERDROLA_FUERA_MAS_20KW: Fuera de ámbito, Pc > 20 kW: IBERDROLA A15:J25. 64 celdas
-- (cada celda = un «orden» con 3 filas, una por dimensión, con el mismo importe). Se insertan solo si la regla aún no tiene valores.
--   Sin celda (revisión manual): A25:C25 >200.000 kWh = 'Consultar'
INSERT INTO mcas_valores_rango
    (id_regla, dimension, valor_min, valor_max, min_inclusivo, max_inclusivo,
     valor_categoria, valor_resultado, unidad_resultado, orden)
SELECT r.id, v.dimension, v.valor_min, v.valor_max, v.min_inc, v.max_inc,
       v.categoria, v.importe, 'EUR', v.orden
FROM mcas_reglas_comision r
JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
CROSS JOIN (
    --               orden,         dimension, valor_min, valor_max, min_inc, max_inc,  categoria, importe
              SELECT 1 AS orden, 'consumo_kwh' AS dimension, 0 AS valor_min, 10000 AS valor_max, TRUE AS min_inc, TRUE AS max_inc, NULL AS categoria, 305.60 AS importe  -- C17  consumo_kwh [0, 10.000] kWh
    UNION ALL SELECT     1,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  305.60  --      zona_comision = ZBR
    UNION ALL SELECT     1, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  305.60  --      codigo_producto = OFERTA_0
    UNION ALL SELECT     2,     'consumo_kwh',         0,     10000,    TRUE,    TRUE,       NULL,  230.40  -- D17  consumo_kwh [0, 10.000] kWh
    UNION ALL SELECT     2,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  230.40  --      zona_comision = ZBR
    UNION ALL SELECT     2, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  230.40  --      codigo_producto = OFERTA_1
    UNION ALL SELECT     3,     'consumo_kwh',         0,     10000,    TRUE,    TRUE,       NULL,  183.20  -- E17  consumo_kwh [0, 10.000] kWh
    UNION ALL SELECT     3,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  183.20  --      zona_comision = ZBR
    UNION ALL SELECT     3, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  183.20  --      codigo_producto = OFERTA_2
    UNION ALL SELECT     4,     'consumo_kwh',         0,     10000,    TRUE,    TRUE,       NULL,  121.60  -- F17  consumo_kwh [0, 10.000] kWh
    UNION ALL SELECT     4,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  121.60  --      zona_comision = ZBR
    UNION ALL SELECT     4, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  121.60  --      codigo_producto = OFERTA_3
    UNION ALL SELECT     5,     'consumo_kwh',         0,     10000,    TRUE,    TRUE,       NULL,  254.40  -- G17  consumo_kwh [0, 10.000] kWh
    UNION ALL SELECT     5,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  254.40  --      zona_comision = ZAR
    UNION ALL SELECT     5, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  254.40  --      codigo_producto = OFERTA_0
    UNION ALL SELECT     6,     'consumo_kwh',         0,     10000,    TRUE,    TRUE,       NULL,  193.60  -- H17  consumo_kwh [0, 10.000] kWh
    UNION ALL SELECT     6,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  193.60  --      zona_comision = ZAR
    UNION ALL SELECT     6, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  193.60  --      codigo_producto = OFERTA_1
    UNION ALL SELECT     7,     'consumo_kwh',         0,     10000,    TRUE,    TRUE,       NULL,  152.80  -- I17  consumo_kwh [0, 10.000] kWh
    UNION ALL SELECT     7,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  152.80  --      zona_comision = ZAR
    UNION ALL SELECT     7, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  152.80  --      codigo_producto = OFERTA_2
    UNION ALL SELECT     8,     'consumo_kwh',         0,     10000,    TRUE,    TRUE,       NULL,  101.60  -- J17  consumo_kwh [0, 10.000] kWh
    UNION ALL SELECT     8,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  101.60  --      zona_comision = ZAR
    UNION ALL SELECT     8, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  101.60  --      codigo_producto = OFERTA_3
    UNION ALL SELECT     9,     'consumo_kwh',     10000,     20000,   FALSE,    TRUE,       NULL,  339.20  -- C18  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT     9,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  339.20  --      zona_comision = ZBR
    UNION ALL SELECT     9, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  339.20  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    10,     'consumo_kwh',     10000,     20000,   FALSE,    TRUE,       NULL,  248.00  -- D18  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    10,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  248.00  --      zona_comision = ZBR
    UNION ALL SELECT    10, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  248.00  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    11,     'consumo_kwh',     10000,     20000,   FALSE,    TRUE,       NULL,  196.80  -- E18  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    11,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  196.80  --      zona_comision = ZBR
    UNION ALL SELECT    11, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  196.80  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    12,     'consumo_kwh',     10000,     20000,   FALSE,    TRUE,       NULL,  126.40  -- F18  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    12,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  126.40  --      zona_comision = ZBR
    UNION ALL SELECT    12, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  126.40  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    13,     'consumo_kwh',     10000,     20000,   FALSE,    TRUE,       NULL,  292.00  -- G18  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    13,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  292.00  --      zona_comision = ZAR
    UNION ALL SELECT    13, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  292.00  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    14,     'consumo_kwh',     10000,     20000,   FALSE,    TRUE,       NULL,  210.40  -- H18  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    14,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  210.40  --      zona_comision = ZAR
    UNION ALL SELECT    14, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  210.40  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    15,     'consumo_kwh',     10000,     20000,   FALSE,    TRUE,       NULL,  162.40  -- I18  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    15,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  162.40  --      zona_comision = ZAR
    UNION ALL SELECT    15, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  162.40  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    16,     'consumo_kwh',     10000,     20000,   FALSE,    TRUE,       NULL,  108.80  -- J18  consumo_kwh (10.000, 20.000] kWh
    UNION ALL SELECT    16,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  108.80  --      zona_comision = ZAR
    UNION ALL SELECT    16, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  108.80  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    17,     'consumo_kwh',     20000,     30000,   FALSE,    TRUE,       NULL,  384.00  -- C19  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    17,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  384.00  --      zona_comision = ZBR
    UNION ALL SELECT    17, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  384.00  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    18,     'consumo_kwh',     20000,     30000,   FALSE,    TRUE,       NULL,  268.00  -- D19  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    18,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  268.00  --      zona_comision = ZBR
    UNION ALL SELECT    18, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  268.00  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    19,     'consumo_kwh',     20000,     30000,   FALSE,    TRUE,       NULL,  204.00  -- E19  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    19,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  204.00  --      zona_comision = ZBR
    UNION ALL SELECT    19, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  204.00  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    20,     'consumo_kwh',     20000,     30000,   FALSE,    TRUE,       NULL,  136.00  -- F19  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    20,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  136.00  --      zona_comision = ZBR
    UNION ALL SELECT    20, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  136.00  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    21,     'consumo_kwh',     20000,     30000,   FALSE,    TRUE,       NULL,  332.80  -- G19  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    21,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  332.80  --      zona_comision = ZAR
    UNION ALL SELECT    21, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  332.80  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    22,     'consumo_kwh',     20000,     30000,   FALSE,    TRUE,       NULL,  230.40  -- H19  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    22,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  230.40  --      zona_comision = ZAR
    UNION ALL SELECT    22, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  230.40  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    23,     'consumo_kwh',     20000,     30000,   FALSE,    TRUE,       NULL,  172.80  -- I19  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    23,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  172.80  --      zona_comision = ZAR
    UNION ALL SELECT    23, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  172.80  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    24,     'consumo_kwh',     20000,     30000,   FALSE,    TRUE,       NULL,  115.20  -- J19  consumo_kwh (20.000, 30.000] kWh
    UNION ALL SELECT    24,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  115.20  --      zona_comision = ZAR
    UNION ALL SELECT    24, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  115.20  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    25,     'consumo_kwh',     30000,     50000,   FALSE,    TRUE,       NULL,  424.80  -- C20  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    25,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  424.80  --      zona_comision = ZBR
    UNION ALL SELECT    25, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  424.80  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    26,     'consumo_kwh',     30000,     50000,   FALSE,    TRUE,       NULL,  305.60  -- D20  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    26,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  305.60  --      zona_comision = ZBR
    UNION ALL SELECT    26, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  305.60  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    27,     'consumo_kwh',     30000,     50000,   FALSE,    TRUE,       NULL,  224.00  -- E20  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    27,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  224.00  --      zona_comision = ZBR
    UNION ALL SELECT    27, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  224.00  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    28,     'consumo_kwh',     30000,     50000,   FALSE,    TRUE,       NULL,  145.60  -- F20  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    28,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  145.60  --      zona_comision = ZBR
    UNION ALL SELECT    28, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  145.60  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    29,     'consumo_kwh',     30000,     50000,   FALSE,    TRUE,       NULL,  380.00  -- G20  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    29,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  380.00  --      zona_comision = ZAR
    UNION ALL SELECT    29, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  380.00  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    30,     'consumo_kwh',     30000,     50000,   FALSE,    TRUE,       NULL,  264.80  -- H20  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    30,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  264.80  --      zona_comision = ZAR
    UNION ALL SELECT    30, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  264.80  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    31,     'consumo_kwh',     30000,     50000,   FALSE,    TRUE,       NULL,  190.40  -- I20  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    31,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  190.40  --      zona_comision = ZAR
    UNION ALL SELECT    31, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  190.40  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    32,     'consumo_kwh',     30000,     50000,   FALSE,    TRUE,       NULL,  121.60  -- J20  consumo_kwh (30.000, 50.000] kWh
    UNION ALL SELECT    32,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  121.60  --      zona_comision = ZAR
    UNION ALL SELECT    32, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  121.60  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    33,     'consumo_kwh',     50000,     75000,   FALSE,    TRUE,       NULL,  512.80  -- C21  consumo_kwh (50.000, 75.000] kWh
    UNION ALL SELECT    33,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  512.80  --      zona_comision = ZBR
    UNION ALL SELECT    33, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  512.80  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    34,     'consumo_kwh',     50000,     75000,   FALSE,    TRUE,       NULL,  352.80  -- D21  consumo_kwh (50.000, 75.000] kWh
    UNION ALL SELECT    34,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  352.80  --      zona_comision = ZBR
    UNION ALL SELECT    34, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  352.80  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    35,     'consumo_kwh',     50000,     75000,   FALSE,    TRUE,       NULL,  254.40  -- E21  consumo_kwh (50.000, 75.000] kWh
    UNION ALL SELECT    35,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  254.40  --      zona_comision = ZBR
    UNION ALL SELECT    35, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  254.40  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    36,     'consumo_kwh',     50000,     75000,   FALSE,    TRUE,       NULL,  159.20  -- F21  consumo_kwh (50.000, 75.000] kWh
    UNION ALL SELECT    36,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  159.20  --      zona_comision = ZBR
    UNION ALL SELECT    36, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  159.20  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    37,     'consumo_kwh',     50000,     75000,   FALSE,    TRUE,       NULL,  458.40  -- G21  consumo_kwh (50.000, 75.000] kWh
    UNION ALL SELECT    37,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  458.40  --      zona_comision = ZAR
    UNION ALL SELECT    37, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  458.40  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    38,     'consumo_kwh',     50000,     75000,   FALSE,    TRUE,       NULL,  308.80  -- H21  consumo_kwh (50.000, 75.000] kWh
    UNION ALL SELECT    38,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  308.80  --      zona_comision = ZAR
    UNION ALL SELECT    38, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  308.80  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    39,     'consumo_kwh',     50000,     75000,   FALSE,    TRUE,       NULL,  220.80  -- I21  consumo_kwh (50.000, 75.000] kWh
    UNION ALL SELECT    39,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  220.80  --      zona_comision = ZAR
    UNION ALL SELECT    39, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  220.80  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    40,     'consumo_kwh',     50000,     75000,   FALSE,    TRUE,       NULL,  135.20  -- J21  consumo_kwh (50.000, 75.000] kWh
    UNION ALL SELECT    40,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  135.20  --      zona_comision = ZAR
    UNION ALL SELECT    40, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  135.20  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    41,     'consumo_kwh',     75000,    100000,   FALSE,    TRUE,       NULL,  608.00  -- C22  consumo_kwh (75.000, 100.000] kWh
    UNION ALL SELECT    41,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  608.00  --      zona_comision = ZBR
    UNION ALL SELECT    41, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  608.00  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    42,     'consumo_kwh',     75000,    100000,   FALSE,    TRUE,       NULL,  407.20  -- D22  consumo_kwh (75.000, 100.000] kWh
    UNION ALL SELECT    42,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  407.20  --      zona_comision = ZBR
    UNION ALL SELECT    42, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  407.20  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    43,     'consumo_kwh',     75000,    100000,   FALSE,    TRUE,       NULL,  284.00  -- E22  consumo_kwh (75.000, 100.000] kWh
    UNION ALL SELECT    43,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  284.00  --      zona_comision = ZBR
    UNION ALL SELECT    43, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  284.00  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    44,     'consumo_kwh',     75000,    100000,   FALSE,    TRUE,       NULL,  174.40  -- F22  consumo_kwh (75.000, 100.000] kWh
    UNION ALL SELECT    44,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  174.40  --      zona_comision = ZBR
    UNION ALL SELECT    44, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  174.40  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    45,     'consumo_kwh',     75000,    100000,   FALSE,    TRUE,       NULL,  544.00  -- G22  consumo_kwh (75.000, 100.000] kWh
    UNION ALL SELECT    45,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  544.00  --      zona_comision = ZAR
    UNION ALL SELECT    45, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  544.00  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    46,     'consumo_kwh',     75000,    100000,   FALSE,    TRUE,       NULL,  356.00  -- H22  consumo_kwh (75.000, 100.000] kWh
    UNION ALL SELECT    46,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  356.00  --      zona_comision = ZAR
    UNION ALL SELECT    46, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  356.00  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    47,     'consumo_kwh',     75000,    100000,   FALSE,    TRUE,       NULL,  245.60  -- I22  consumo_kwh (75.000, 100.000] kWh
    UNION ALL SELECT    47,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  245.60  --      zona_comision = ZAR
    UNION ALL SELECT    47, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  245.60  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    48,     'consumo_kwh',     75000,    100000,   FALSE,    TRUE,       NULL,  149.60  -- J22  consumo_kwh (75.000, 100.000] kWh
    UNION ALL SELECT    48,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  149.60  --      zona_comision = ZAR
    UNION ALL SELECT    48, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  149.60  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    49,     'consumo_kwh',    100000,    150000,   FALSE,    TRUE,       NULL,  748.00  -- C23  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    49,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  748.00  --      zona_comision = ZBR
    UNION ALL SELECT    49, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  748.00  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    50,     'consumo_kwh',    100000,    150000,   FALSE,    TRUE,       NULL,  484.00  -- D23  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    50,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  484.00  --      zona_comision = ZBR
    UNION ALL SELECT    50, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  484.00  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    51,     'consumo_kwh',    100000,    150000,   FALSE,    TRUE,       NULL,  328.80  -- E23  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    51,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  328.80  --      zona_comision = ZBR
    UNION ALL SELECT    51, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  328.80  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    52,     'consumo_kwh',    100000,    150000,   FALSE,    TRUE,       NULL,  196.80  -- F23  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    52,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  196.80  --      zona_comision = ZBR
    UNION ALL SELECT    52, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  196.80  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    53,     'consumo_kwh',    100000,    150000,   FALSE,    TRUE,       NULL,  679.20  -- G23  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    53,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  679.20  --      zona_comision = ZAR
    UNION ALL SELECT    53, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  679.20  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    54,     'consumo_kwh',    100000,    150000,   FALSE,    TRUE,       NULL,  432.00  -- H23  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    54,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  432.00  --      zona_comision = ZAR
    UNION ALL SELECT    54, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  432.00  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    55,     'consumo_kwh',    100000,    150000,   FALSE,    TRUE,       NULL,  288.80  -- I23  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    55,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  288.80  --      zona_comision = ZAR
    UNION ALL SELECT    55, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  288.80  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    56,     'consumo_kwh',    100000,    150000,   FALSE,    TRUE,       NULL,  176.00  -- J23  consumo_kwh (100.000, 150.000] kWh
    UNION ALL SELECT    56,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  176.00  --      zona_comision = ZAR
    UNION ALL SELECT    56, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  176.00  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    57,     'consumo_kwh',    150000,    200000,   FALSE,    TRUE,       NULL,  917.60  -- C24  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    57,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  917.60  --      zona_comision = ZBR
    UNION ALL SELECT    57, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  917.60  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    58,     'consumo_kwh',    150000,    200000,   FALSE,    TRUE,       NULL,  588.80  -- D24  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    58,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  588.80  --      zona_comision = ZBR
    UNION ALL SELECT    58, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  588.80  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    59,     'consumo_kwh',    150000,    200000,   FALSE,    TRUE,       NULL,  388.80  -- E24  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    59,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  388.80  --      zona_comision = ZBR
    UNION ALL SELECT    59, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  388.80  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    60,     'consumo_kwh',    150000,    200000,   FALSE,    TRUE,       NULL,  226.40  -- F24  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    60,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZBR',  226.40  --      zona_comision = ZBR
    UNION ALL SELECT    60, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  226.40  --      codigo_producto = OFERTA_3
    UNION ALL SELECT    61,     'consumo_kwh',    150000,    200000,   FALSE,    TRUE,       NULL,  863.20  -- G24  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    61,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  863.20  --      zona_comision = ZAR
    UNION ALL SELECT    61, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_0',  863.20  --      codigo_producto = OFERTA_0
    UNION ALL SELECT    62,     'consumo_kwh',    150000,    200000,   FALSE,    TRUE,       NULL,  543.20  -- H24  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    62,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  543.20  --      zona_comision = ZAR
    UNION ALL SELECT    62, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_1',  543.20  --      codigo_producto = OFERTA_1
    UNION ALL SELECT    63,     'consumo_kwh',    150000,    200000,   FALSE,    TRUE,       NULL,  353.60  -- I24  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    63,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  353.60  --      zona_comision = ZAR
    UNION ALL SELECT    63, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_2',  353.60  --      codigo_producto = OFERTA_2
    UNION ALL SELECT    64,     'consumo_kwh',    150000,    200000,   FALSE,    TRUE,       NULL,  204.00  -- J24  consumo_kwh (150.000, 200.000] kWh
    UNION ALL SELECT    64,   'zona_comision',      NULL,      NULL,    TRUE,   FALSE,      'ZAR',  204.00  --      zona_comision = ZAR
    UNION ALL SELECT    64, 'codigo_producto',      NULL,      NULL,    TRUE,   FALSE, 'OFERTA_3',  204.00  --      codigo_producto = OFERTA_3
) v
WHERE cr.codigo = 'IBERDROLA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND r.codigo = 'IBERDROLA_FUERA_MAS_20KW'
  AND NOT EXISTS (SELECT 1 FROM mcas_valores_rango x WHERE x.id_regla = r.id);

-- Política RET-IBERDROLA-2_0TD-20260101 (PORCENTAJE, suministro LUZ, tarifa 2.0TD)
INSERT INTO mcas_politicas_retrocesion
    (codigo, descripcion, id_comercializadora, id_gestora, version, vigencia_desde, vigencia_hasta,
     tipo_suministro, segmento, tarifa_acceso, metodo, limite_importe_pagado, archivo_origen, estado)
SELECT 'RET-IBERDROLA-2_0TD-20260101',
       'Unidades del Excel sin confirmar (v2 §11.2): confirmar con el cliente antes de publicar. Excel IBERDROLA A80:F84 (días desde la activación). El Excel no cubre el día 90 («< 90» y «91-180»). A84: la decomisión nunca supera la comisión liquidada. Fila A82 (2.0TD): 20 / 12 / 8 / 4 sin unidad.',
       c.id, NULL, '2026.01.01', '2026-01-01', NULL,
       'LUZ', NULL, '2.0TD', 'PORCENTAJE', TRUE,
       'COMISIONES NEXO A 16032026 1.xlsx', 'BORRADOR'
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'IBERDROLA%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'IBERDROLA%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_politicas_retrocesion x WHERE x.codigo = 'RET-IBERDROLA-2_0TD-20260101');

-- Tramos de RET-IBERDROLA-2_0TD-20260101 (solo si la política sigue en BORRADOR y aún no tiene tramos)
INSERT INTO mcas_tramos_retrocesion
    (id_politica, unidad, desde_valor, hasta_valor, desde_inclusivo, hasta_inclusivo,
     valor, tipo_valor, prioridad)
SELECT p.id, t.unidad, t.desde, t.hasta, t.desde_inc, t.hasta_inc, t.valor, t.tipo_valor, t.prioridad
FROM mcas_politicas_retrocesion p
CROSS JOIN (
    --               unidad, desde, hasta, desde_inc, hasta_inc, valor,      tipo_valor, prioridad
              SELECT 'DIAS' AS unidad, 0 AS desde, 90 AS hasta, TRUE AS desde_inc, FALSE AS hasta_inc, 1.00 AS valor, 'PORCENTAJE' AS tipo_valor, 10 AS prioridad  -- B82 «< 90»: [0, 90) días = 100 %
    UNION ALL SELECT 'DIAS',    90,   180,     FALSE,      TRUE, 20.00, 'SIN_CONFIRMAR',        20  -- C82 «91-180»: (90, 180] días = 20 (sin unidad confirmada)
    UNION ALL SELECT 'DIAS',   180,   270,     FALSE,      TRUE, 12.00, 'SIN_CONFIRMAR',        30  -- D82 «181-270»: (180, 270] días = 12 (sin unidad confirmada)
    UNION ALL SELECT 'DIAS',   270,   330,     FALSE,      TRUE,  8.00, 'SIN_CONFIRMAR',        40  -- E82 «271-330»: (270, 330] días = 8 (sin unidad confirmada)
    UNION ALL SELECT 'DIAS',   330,   365,     FALSE,      TRUE,  4.00, 'SIN_CONFIRMAR',        50  -- F82 «331-365»: (330, 365] días = 4 (sin unidad confirmada)
) t
WHERE p.codigo = 'RET-IBERDROLA-2_0TD-20260101' AND p.estado = 'BORRADOR'
  AND NOT EXISTS (SELECT 1 FROM mcas_tramos_retrocesion x WHERE x.id_politica = p.id);

-- Política RET-IBERDROLA-3_0TD-20260101 (PORCENTAJE, suministro LUZ, tarifa 3.0TD)
INSERT INTO mcas_politicas_retrocesion
    (codigo, descripcion, id_comercializadora, id_gestora, version, vigencia_desde, vigencia_hasta,
     tipo_suministro, segmento, tarifa_acceso, metodo, limite_importe_pagado, archivo_origen, estado)
SELECT 'RET-IBERDROLA-3_0TD-20260101',
       'Unidades del Excel sin confirmar (v2 §11.2): confirmar con el cliente antes de publicar. Excel IBERDROLA A80:F84 (días desde la activación). El Excel no cubre el día 90 («< 90» y «91-180»). A84: la decomisión nunca supera la comisión liquidada. Fila A83 (3.0TD y 6.1TD), política para 3.0TD: C83:F83 sin unidad.',
       c.id, NULL, '2026.01.01', '2026-01-01', NULL,
       'LUZ', NULL, '3.0TD', 'PORCENTAJE', TRUE,
       'COMISIONES NEXO A 16032026 1.xlsx', 'BORRADOR'
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'IBERDROLA%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'IBERDROLA%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_politicas_retrocesion x WHERE x.codigo = 'RET-IBERDROLA-3_0TD-20260101');

-- Tramos de RET-IBERDROLA-3_0TD-20260101 (solo si la política sigue en BORRADOR y aún no tiene tramos)
INSERT INTO mcas_tramos_retrocesion
    (id_politica, unidad, desde_valor, hasta_valor, desde_inclusivo, hasta_inclusivo,
     valor, tipo_valor, prioridad)
SELECT p.id, t.unidad, t.desde, t.hasta, t.desde_inc, t.hasta_inc, t.valor, t.tipo_valor, t.prioridad
FROM mcas_politicas_retrocesion p
CROSS JOIN (
    --               unidad, desde, hasta, desde_inc, hasta_inc, valor,      tipo_valor, prioridad
              SELECT 'DIAS' AS unidad, 0 AS desde, 90 AS hasta, TRUE AS desde_inc, FALSE AS hasta_inc, 1.00 AS valor, 'PORCENTAJE' AS tipo_valor, 10 AS prioridad  -- B83 «< 90»: [0, 90) días = 100 %
    UNION ALL SELECT 'DIAS',    90,   180,     FALSE,      TRUE,  0.75, 'SIN_CONFIRMAR',        20  -- C83 «91-180»: (90, 180] días = 0,75 (sin unidad confirmada)
    UNION ALL SELECT 'DIAS',   180,   270,     FALSE,      TRUE,  0.25, 'SIN_CONFIRMAR',        30  -- D83 «181-270»: (180, 270] días = 0,25 (sin unidad confirmada)
    UNION ALL SELECT 'DIAS',   270,   330,     FALSE,      TRUE,  0.15, 'SIN_CONFIRMAR',        40  -- E83 «271-330»: (270, 330] días = 0,15 (sin unidad confirmada)
    UNION ALL SELECT 'DIAS',   330,   365,     FALSE,      TRUE,  0.10, 'SIN_CONFIRMAR',        50  -- F83 «331-365»: (330, 365] días = 0,1 (sin unidad confirmada)
) t
WHERE p.codigo = 'RET-IBERDROLA-3_0TD-20260101' AND p.estado = 'BORRADOR'
  AND NOT EXISTS (SELECT 1 FROM mcas_tramos_retrocesion x WHERE x.id_politica = p.id);

-- Política RET-IBERDROLA-6_1TD-20260101 (PORCENTAJE, suministro LUZ, tarifa 6.1TD)
INSERT INTO mcas_politicas_retrocesion
    (codigo, descripcion, id_comercializadora, id_gestora, version, vigencia_desde, vigencia_hasta,
     tipo_suministro, segmento, tarifa_acceso, metodo, limite_importe_pagado, archivo_origen, estado)
SELECT 'RET-IBERDROLA-6_1TD-20260101',
       'Unidades del Excel sin confirmar (v2 §11.2): confirmar con el cliente antes de publicar. Excel IBERDROLA A80:F84 (días desde la activación). El Excel no cubre el día 90 («< 90» y «91-180»). A84: la decomisión nunca supera la comisión liquidada. Fila A83 (3.0TD y 6.1TD), política para 6.1TD: C83:F83 sin unidad.',
       c.id, NULL, '2026.01.01', '2026-01-01', NULL,
       'LUZ', NULL, '6.1TD', 'PORCENTAJE', TRUE,
       'COMISIONES NEXO A 16032026 1.xlsx', 'BORRADOR'
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'IBERDROLA%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'IBERDROLA%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_politicas_retrocesion x WHERE x.codigo = 'RET-IBERDROLA-6_1TD-20260101');

-- Tramos de RET-IBERDROLA-6_1TD-20260101 (solo si la política sigue en BORRADOR y aún no tiene tramos)
INSERT INTO mcas_tramos_retrocesion
    (id_politica, unidad, desde_valor, hasta_valor, desde_inclusivo, hasta_inclusivo,
     valor, tipo_valor, prioridad)
SELECT p.id, t.unidad, t.desde, t.hasta, t.desde_inc, t.hasta_inc, t.valor, t.tipo_valor, t.prioridad
FROM mcas_politicas_retrocesion p
CROSS JOIN (
    --               unidad, desde, hasta, desde_inc, hasta_inc, valor,      tipo_valor, prioridad
              SELECT 'DIAS' AS unidad, 0 AS desde, 90 AS hasta, TRUE AS desde_inc, FALSE AS hasta_inc, 1.00 AS valor, 'PORCENTAJE' AS tipo_valor, 10 AS prioridad  -- B83 «< 90»: [0, 90) días = 100 %
    UNION ALL SELECT 'DIAS',    90,   180,     FALSE,      TRUE,  0.75, 'SIN_CONFIRMAR',        20  -- C83 «91-180»: (90, 180] días = 0,75 (sin unidad confirmada)
    UNION ALL SELECT 'DIAS',   180,   270,     FALSE,      TRUE,  0.25, 'SIN_CONFIRMAR',        30  -- D83 «181-270»: (180, 270] días = 0,25 (sin unidad confirmada)
    UNION ALL SELECT 'DIAS',   270,   330,     FALSE,      TRUE,  0.15, 'SIN_CONFIRMAR',        40  -- E83 «271-330»: (270, 330] días = 0,15 (sin unidad confirmada)
    UNION ALL SELECT 'DIAS',   330,   365,     FALSE,      TRUE,  0.10, 'SIN_CONFIRMAR',        50  -- F83 «331-365»: (330, 365] días = 0,1 (sin unidad confirmada)
) t
WHERE p.codigo = 'RET-IBERDROLA-6_1TD-20260101' AND p.estado = 'BORRADOR'
  AND NOT EXISTS (SELECT 1 FROM mcas_tramos_retrocesion x WHERE x.id_politica = p.id);


-- ======================================================================
-- IRIS
-- Hoja2 K15 (cliente): pyme = consumo (MWh) × FEE × 44 %, residencial = 40 € (< 5.000 kWh) o 64 €
-- (5.000 kWh o más), gas = manual. Hoja1 E42: indexado manual (sin conjunto INDEXADO).
-- Se amplía el conjunto IRIS-FIJO-20260304 de V20260913_2 solo si sigue en BORRADOR y lo creó seed-controlado.
-- ======================================================================

-- Conjunto existente IRIS-FIJO-20260304 (V20260913_2): se amplía solo si sigue en BORRADOR y creado_por = 'seed-controlado'.

-- Desactivar IRIS_PLAN_PERSONALIZADO_PYME (la Hoja2 no la contempla)
UPDATE mcas_reglas_comision
SET activo = FALSE,
    localizador_origen = 'Sustituida por IRIS_PYME_LUZ (Hoja2 K15: pyme = consumo × FEE × 44 % sin depender del producto)'
WHERE codigo = 'IRIS_PLAN_PERSONALIZADO_PYME'
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IRIS-FIJO-20260304' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-controlado');

-- IRIS_PYME_LUZ · FEE_PERCENTAGE 44 %, FEE mínimo 5 €/MWh
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'IRIS_PYME_LUZ', 20, 'FEE_PERCENTAGE',
       '{"tipo_suministro":"LUZ","segmento":"PYME","es_renovacion":false}',
       '{"porcentaje_aplicacion_fee":0.44,"fee_minimo_eur_mwh":5}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K15: pyme = consumo (MWh) × FEE × 44 %. Excel IRIS Z27:AA27: 44 % siempre que FEE ≥ 5',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'IRIS-FIJO-20260304' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-controlado'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'IRIS_PYME_LUZ');

-- IRIS_RESIDENCIAL_MENOS_5000 · FIXED_AMOUNT 40 €
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'IRIS_RESIDENCIAL_MENOS_5000', 30, 'FIXED_AMOUNT',
       '{"tipo_suministro":"LUZ","segmento":"RESIDENCIAL","es_renovacion":false,"consumo_max_kwh":5000,"consumo_max_inclusivo":false}',
       '{"importe":40}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K15: residencial < 5.000 kWh = 40 €. Excel IRIS F8:F9 (NEW RESIDENC PROMO) = 40 € hasta 4.999 kWh',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'IRIS-FIJO-20260304' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-controlado'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'IRIS_RESIDENCIAL_MENOS_5000');

-- IRIS_RESIDENCIAL_5000_O_MAS · FIXED_AMOUNT 64 €
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'IRIS_RESIDENCIAL_5000_O_MAS', 40, 'FIXED_AMOUNT',
       '{"tipo_suministro":"LUZ","segmento":"RESIDENCIAL","es_renovacion":false,"consumo_min_kwh":5000,"consumo_min_inclusivo":true}',
       '{"importe":64}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K15: residencial 5.000 kWh o más = 64 €. Excel IRIS G10:G12 (NEW PROMOCIONAL) = 64 € solo de 5.000 a 14.999 kWh',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'IRIS-FIJO-20260304' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-controlado'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'IRIS_RESIDENCIAL_5000_O_MAS');

-- IRIS_GAS_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'IRIS_GAS_MANUAL', 50, 'MANUAL',
       '{"tipo_suministro":"GAS","es_renovacion":false}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'Iris: el gas se introduce manualmente (Hoja2)',
       'Hoja2 K15 (cliente): gas = manual. Excel IRIS X28:AA28 indica gas 44 % con FEE ≥ 5 y B71:R115 tablas de gas: no se usan',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'IRIS-FIJO-20260304' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-controlado'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'IRIS_GAS_MANUAL');

-- IRIS_RENOVACION_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'IRIS_RENOVACION_MANUAL', 60, 'MANUAL',
       '{"es_renovacion":true}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'Iris: las renovaciones se introducen manualmente',
       'Excel IRIS W45:W53: las renovaciones dependen del mes y de si se firma contrato nuevo. Hoja2 K15 no las calcula: se completan a mano',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'IRIS-FIJO-20260304' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-controlado'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'IRIS_RENOVACION_MANUAL');

-- Política RET-IRIS-20260304 (PRORRATEO)
INSERT INTO mcas_politicas_retrocesion
    (codigo, descripcion, id_comercializadora, id_gestora, version, vigencia_desde, vigencia_hasta,
     tipo_suministro, segmento, tarifa_acceso, metodo, limite_importe_pagado, archivo_origen, estado)
SELECT 'RET-IRIS-20260304',
       'Retrocomisión prorrateada a 365 días (Excel IRIS W58).',
       c.id, NULL, '2026.03.04', '2026-03-04', NULL,
       NULL, NULL, NULL, 'PRORRATEO', TRUE,
       'COMISIONES NEXO A 16032026 1.xlsx', 'BORRADOR'
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'IRIS%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'IRIS%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_politicas_retrocesion x WHERE x.codigo = 'RET-IRIS-20260304');

-- RET-IRIS-20260304: sin tramos (PRORRATEO).


-- ======================================================================
-- UNIELÉCTRICA
-- Hoja2 K20 (cliente): pyme = consumo (MWh) × FEE × 55 %, residencial = 50 € (< 5.000 kWh) u 80 €
-- (5.000 kWh o más), gas = manual. Sin hoja en el Excel de comisiones: origen LISTADO / Hoja2.
-- Hoja1 E53: indexado manual (sin conjunto INDEXADO).
-- ======================================================================

-- Conjunto UNIELECTRICA-FIJO-20260101 (BORRADOR, fase FIJO, vigente desde 2026-01-01)
INSERT INTO mcas_conjuntos_reglas
    (codigo, version, id_comercializadora, id_gestora, fase_precio, vigencia_desde, vigencia_hasta,
     estado, archivo_origen, hoja_origen, fecha_interna_origen, hash_origen,
     creado_por, aprobado_por, fecha_creacion, fecha_aprobacion)
SELECT 'UNIELECTRICA-FIJO-20260101', '2026.01.01', c.id, NULL, 'FIJO', '2026-01-01', NULL,
       'BORRADOR', 'LISTADO DATOS AUTOMATIZACION SERVICIOS 1.xlsx', 'Hoja2', NULL,
       '4D5D5174AED1BDDF1CC167AA701159E6163E2F5D2798202AAE11B4057893CAC8',
       'seed-hoja2', NULL, CURRENT_TIMESTAMP, NULL
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'UNIEL%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'UNIEL%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_conjuntos_reglas x WHERE x.codigo = 'UNIELECTRICA-FIJO-20260101');

-- UNIELECTRICA_RENOVACION_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'UNIELECTRICA_RENOVACION_MANUAL', 10, 'MANUAL',
       '{"es_renovacion":true}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'Unieléctrica: las renovaciones se introducen manualmente (sin regla en la Hoja2)',
       'Hoja2 K20 no define comisión de renovación: se completa a mano',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'UNIELECTRICA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'UNIELECTRICA_RENOVACION_MANUAL');

-- UNIELECTRICA_GAS_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'UNIELECTRICA_GAS_MANUAL', 20, 'MANUAL',
       '{"tipo_suministro":"GAS","es_renovacion":false}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'Unieléctrica: el gas se introduce manualmente (Hoja2)',
       'Hoja2 K20 (cliente): gas = manual. Sin hoja en el Excel de comisiones',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'UNIELECTRICA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'UNIELECTRICA_GAS_MANUAL');

-- UNIELECTRICA_PYME_LUZ · FEE_PERCENTAGE 55 %
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'UNIELECTRICA_PYME_LUZ', 30, 'FEE_PERCENTAGE',
       '{"tipo_suministro":"LUZ","segmento":"PYME","es_renovacion":false}',
       '{"porcentaje_aplicacion_fee":0.55}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K20: pyme = consumo (MWh) × FEE × 55 %. Sin FEE mínimo en la Hoja2',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'UNIELECTRICA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'UNIELECTRICA_PYME_LUZ');

-- UNIELECTRICA_RESIDENCIAL_MENOS_5000 · FIXED_AMOUNT 50 €
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'UNIELECTRICA_RESIDENCIAL_MENOS_5000', 40, 'FIXED_AMOUNT',
       '{"tipo_suministro":"LUZ","segmento":"RESIDENCIAL","es_renovacion":false,"consumo_max_kwh":5000,"consumo_max_inclusivo":false}',
       '{"importe":50}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K20: residencial < 5.000 kWh = 50 €',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'UNIELECTRICA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'UNIELECTRICA_RESIDENCIAL_MENOS_5000');

-- UNIELECTRICA_RESIDENCIAL_5000_O_MAS · FIXED_AMOUNT 80 €
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'UNIELECTRICA_RESIDENCIAL_5000_O_MAS', 50, 'FIXED_AMOUNT',
       '{"tipo_suministro":"LUZ","segmento":"RESIDENCIAL","es_renovacion":false,"consumo_min_kwh":5000,"consumo_min_inclusivo":true}',
       '{"importe":80}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K20: residencial 5.000 kWh o más = 80 €',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'UNIELECTRICA-FIJO-20260101' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'UNIELECTRICA_RESIDENCIAL_5000_O_MAS');


-- ======================================================================
-- NATURGY
-- Hoja2 K26 (cliente): residencial = 50,63 € (luz y gas), pyme luz < 10.000 kWh = 40,50 €,
-- pyme luz 10.000 kWh o más = consumo (MWh) × 4,05 €. Hoja1 E66: FEE de gas pyme manual.
-- Excel hoja NATURGY, vigencia J2 = 2025-10-03. Productos E8:J8 para el desplegable del contrato.
-- ======================================================================

-- Productos (tarifa de precios del contrato): PF_LUZ_ONE, PF_LUZ, PF_LUZ_SUPRA, PV_LUZ_ONE, PV_LUZ, PV_SUPRA
INSERT INTO mcas_productos_comerciales
    (id_comercializadora, codigo, nombre, tipo_precio, tipo_suministro, activo_desde, activo)
SELECT c.id, s.codigo, s.nombre, s.tipo_precio, s.tipo_suministro, s.activo_desde, TRUE
FROM tbcomercializadoras c
CROSS JOIN (
    SELECT 'PF_LUZ_ONE' AS codigo, 'PF LUZ ONE' AS nombre,
           'FIJO' AS tipo_precio, 'LUZ' AS tipo_suministro,
           '2025-10-03' AS activo_desde   -- NATURGY E8
    UNION ALL SELECT 'PF_LUZ', 'PF LUZ',
           'FIJO', 'LUZ', '2025-10-03'   -- NATURGY F8
    UNION ALL SELECT 'PF_LUZ_SUPRA', 'PF LUZ SUPRA',
           'FIJO', 'LUZ', '2025-10-03'   -- NATURGY G8
    UNION ALL SELECT 'PV_LUZ_ONE', 'PV LUZ ONE',
           'INDEXADO', 'LUZ', '2025-10-03'   -- NATURGY H8
    UNION ALL SELECT 'PV_LUZ', 'PV LUZ',
           'INDEXADO', 'LUZ', '2025-10-03'   -- NATURGY I8
    UNION ALL SELECT 'PV_SUPRA', 'PV SUPRA',
           'INDEXADO', 'LUZ', '2025-10-03'   -- NATURGY J8
) s
WHERE UPPER(TRIM(c.nombre)) LIKE 'NATURGY%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'NATURGY%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_productos_comerciales p
                  WHERE p.id_comercializadora = c.id AND p.codigo = s.codigo);

-- Conjunto NATURGY-FIJO-20251003 (BORRADOR, fase FIJO, vigente desde 2025-10-03)
INSERT INTO mcas_conjuntos_reglas
    (codigo, version, id_comercializadora, id_gestora, fase_precio, vigencia_desde, vigencia_hasta,
     estado, archivo_origen, hoja_origen, fecha_interna_origen, hash_origen,
     creado_por, aprobado_por, fecha_creacion, fecha_aprobacion)
SELECT 'NATURGY-FIJO-20251003', '2025.10.03', c.id, NULL, 'FIJO', '2025-10-03', NULL,
       'BORRADOR', 'COMISIONES NEXO A 16032026 1.xlsx', 'NATURGY', '2025-10-03',
       '654D4AF590BC084500EDD3F594862121601B00E9A84847E1FB78DEB695C5197D',
       'seed-hoja2', NULL, CURRENT_TIMESTAMP, NULL
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'NATURGY%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'NATURGY%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_conjuntos_reglas x WHERE x.codigo = 'NATURGY-FIJO-20251003');

-- NATURGY_RENOVACION_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'NATURGY_RENOVACION_MANUAL', 10, 'MANUAL',
       '{"es_renovacion":true}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'Naturgy: las renovaciones se introducen manualmente (sin regla en la Hoja2)',
       'Hoja2 K26 y el Excel NATURGY no definen comisión de renovación: se completa a mano',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'NATURGY-FIJO-20251003' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'NATURGY_RENOVACION_MANUAL');

-- NATURGY_RESIDENCIAL · FIXED_AMOUNT 50,63 €
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'NATURGY_RESIDENCIAL', 20, 'FIXED_AMOUNT',
       '{"segmento":"RESIDENCIAL","es_renovacion":false}',
       '{"importe":50.63}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K26 (cliente): residencial = 50,63 € luz y gas (Hoja1 D60). Excel NATURGY C30 (luz) y C32 (gas) = 50,625 € y 50,625 €',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'NATURGY-FIJO-20251003' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'NATURGY_RESIDENCIAL');

-- NATURGY_PYME_LUZ_MENOS_10MWH · FIXED_AMOUNT 40,50 €
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'NATURGY_PYME_LUZ_MENOS_10MWH', 30, 'FIXED_AMOUNT',
       '{"tipo_suministro":"LUZ","segmento":"PYME","es_renovacion":false,"consumo_max_kwh":10000,"consumo_max_inclusivo":false}',
       '{"importe":40.5}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K26 (cliente): pyme < 10.000 kWh = 40,50 €. Excel NATURGY E9:J9 varía por producto (40,5 / 50,625 / 57,375 €)',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'NATURGY-FIJO-20251003' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'NATURGY_PYME_LUZ_MENOS_10MWH');

-- NATURGY_PYME_LUZ_10MWH_O_MAS · RATE_PER_MWH 4,05 €/MWh
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'NATURGY_PYME_LUZ_10MWH_O_MAS', 40, 'RATE_PER_MWH',
       '{"tipo_suministro":"LUZ","segmento":"PYME","es_renovacion":false,"consumo_min_kwh":10000,"consumo_min_inclusivo":true}',
       '{"tasa_eur_mwh":4.05}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K26 (cliente): pyme 10.000 kWh o más = consumo (MWh) × 4,05 €. Excel NATURGY E10:J10 varía por producto (4,05 / 9,45 / 16,875 €/MWh)',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'NATURGY-FIJO-20251003' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'NATURGY_PYME_LUZ_10MWH_O_MAS');

-- NATURGY_PYME_GAS_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'NATURGY_PYME_GAS_MANUAL', 50, 'MANUAL',
       '{"tipo_suministro":"GAS","segmento":"PYME","es_renovacion":false}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'Naturgy: la comisión de gas pyme se introduce manualmente (Hoja1)',
       'Hoja1 C66:E66 «fee gas … manual». Excel NATURGY B12:J16 (gas pymes) no se usa',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'NATURGY-FIJO-20251003' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'NATURGY_PYME_GAS_MANUAL');

-- Política RET-NATURGY-PYME-20251003 (PORCENTAJE, segmento PYME)
INSERT INTO mcas_politicas_retrocesion
    (codigo, descripcion, id_comercializadora, id_gestora, version, vigencia_desde, vigencia_hasta,
     tipo_suministro, segmento, tarifa_acceso, metodo, limite_importe_pagado, archivo_origen, estado)
SELECT 'RET-NATURGY-PYME-20251003',
       'Cancelación anticipada Naturgy pymes por meses desde la activación (Excel NATURGY B21:D24).',
       c.id, NULL, '2025.10.03', '2025-10-03', NULL,
       NULL, 'PYME', NULL, 'PORCENTAJE', TRUE,
       'COMISIONES NEXO A 16032026 1.xlsx', 'BORRADOR'
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'NATURGY%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'NATURGY%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_politicas_retrocesion x WHERE x.codigo = 'RET-NATURGY-PYME-20251003');

-- Tramos de RET-NATURGY-PYME-20251003 (solo si la política sigue en BORRADOR y aún no tiene tramos)
INSERT INTO mcas_tramos_retrocesion
    (id_politica, unidad, desde_valor, hasta_valor, desde_inclusivo, hasta_inclusivo,
     valor, tipo_valor, prioridad)
SELECT p.id, t.unidad, t.desde, t.hasta, t.desde_inc, t.hasta_inc, t.valor, t.tipo_valor, t.prioridad
FROM mcas_politicas_retrocesion p
CROSS JOIN (
    --                unidad, desde, hasta, desde_inc, hasta_inc, valor,   tipo_valor, prioridad
              SELECT 'MESES' AS unidad, 0 AS desde, 6 AS hasta, TRUE AS desde_inc, FALSE AS hasta_inc, 1.00 AS valor, 'PORCENTAJE' AS tipo_valor, 10 AS prioridad  -- D22 «<6 meses»: [0, 6) meses = 100 %
    UNION ALL SELECT 'MESES',     6,     9,      TRUE,     FALSE,  0.50, 'PORCENTAJE',        20  -- D23 «6-9 meses»: [6, 9) meses = 50 %
    UNION ALL SELECT 'MESES',     9,    12,      TRUE,     FALSE,  0.25, 'PORCENTAJE',        30  -- D24 «9-12 meses»: [9, 12) meses = 25 %
) t
WHERE p.codigo = 'RET-NATURGY-PYME-20251003' AND p.estado = 'BORRADOR'
  AND NOT EXISTS (SELECT 1 FROM mcas_tramos_retrocesion x WHERE x.id_politica = p.id);

-- Política RET-NATURGY-RESIDENCIAL-20251003 (PORCENTAJE, segmento RESIDENCIAL)
INSERT INTO mcas_politicas_retrocesion
    (codigo, descripcion, id_comercializadora, id_gestora, version, vigencia_desde, vigencia_hasta,
     tipo_suministro, segmento, tarifa_acceso, metodo, limite_importe_pagado, archivo_origen, estado)
SELECT 'RET-NATURGY-RESIDENCIAL-20251003',
       'Cancelación anticipada Naturgy residencial (Excel NATURGY E29:G32). El Excel no define el tramo de 2 a 3 meses: confirmar con el cliente antes de publicar.',
       c.id, NULL, '2025.10.03', '2025-10-03', NULL,
       NULL, 'RESIDENCIAL', NULL, 'PORCENTAJE', TRUE,
       'COMISIONES NEXO A 16032026 1.xlsx', 'BORRADOR'
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'NATURGY%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'NATURGY%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_politicas_retrocesion x WHERE x.codigo = 'RET-NATURGY-RESIDENCIAL-20251003');

-- Tramos de RET-NATURGY-RESIDENCIAL-20251003 (solo si la política sigue en BORRADOR y aún no tiene tramos)
INSERT INTO mcas_tramos_retrocesion
    (id_politica, unidad, desde_valor, hasta_valor, desde_inclusivo, hasta_inclusivo,
     valor, tipo_valor, prioridad)
SELECT p.id, t.unidad, t.desde, t.hasta, t.desde_inc, t.hasta_inc, t.valor, t.tipo_valor, t.prioridad
FROM mcas_politicas_retrocesion p
CROSS JOIN (
    --                unidad, desde, hasta, desde_inc, hasta_inc, valor,   tipo_valor, prioridad
              SELECT 'MESES' AS unidad, 0 AS desde, 2 AS hasta, TRUE AS desde_inc, FALSE AS hasta_inc, 1.00 AS valor, 'PORCENTAJE' AS tipo_valor, 10 AS prioridad  -- G30 «<2 meses»: [0, 2) meses = 100 %
    UNION ALL SELECT 'MESES',     3,     4,      TRUE,      TRUE,  0.50, 'PORCENTAJE',        20  -- G31 «3-4 meses»: [3, 4] meses = 50 %
    UNION ALL SELECT 'MESES',     4,  NULL,     FALSE,     FALSE,  0.00, 'PORCENTAJE',        30  -- G32 «>4 meses»: (4, ∞) meses = 0 %
) t
WHERE p.codigo = 'RET-NATURGY-RESIDENCIAL-20251003' AND p.estado = 'BORRADOR'
  AND NOT EXISTS (SELECT 1 FROM mcas_tramos_retrocesion x WHERE x.id_politica = p.id);


-- ======================================================================
-- VISALIA
-- Hoja2 K32 (cliente): «Pyme tarifa base e indexado» = consumo (MWh) × FEE × 52 %, cualquier otra cosa manual.
-- Se mantiene VISALIA_BASE_PYME (V20260913_2) y se desactivan Fijo Comercio, Esmeralda, Jade y Cuarzo
-- (solo si VISALIA-FIJO-20250612 sigue en BORRADOR y lo creó seed-controlado). Nuevo conjunto INDEXADO
-- (no calcula hasta activar MCAS_INDEXED_ENABLED).
-- ======================================================================

-- Conjunto existente VISALIA-FIJO-20250612 (V20260913_2): se amplía solo si sigue en BORRADOR y creado_por = 'seed-controlado'.

-- Desactivar VISALIA_FIJO_COMERCIO_PYME (la Hoja2 no la contempla)
UPDATE mcas_reglas_comision
SET activo = FALSE,
    localizador_origen = 'Desactivada: Hoja2 K32 indica manual (solo Base pyme es automática). Excel VISALIA A12:B12 FIJO COMERCIO = 52 % del FEE. Activar si el cliente lo confirma'
WHERE codigo = 'VISALIA_FIJO_COMERCIO_PYME'
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'VISALIA-FIJO-20250612' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-controlado');

-- Desactivar VISALIA_ESMERALDA_PYME (la Hoja2 no la contempla)
UPDATE mcas_reglas_comision
SET activo = FALSE,
    localizador_origen = 'Desactivada: Hoja2 K32 indica manual (solo Base pyme es automática). Excel VISALIA A9:B9 ESMERALDA = 16 €/MWh. Activar si el cliente lo confirma'
WHERE codigo = 'VISALIA_ESMERALDA_PYME'
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'VISALIA-FIJO-20250612' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-controlado');

-- Desactivar VISALIA_JADE_PYME (la Hoja2 no la contempla)
UPDATE mcas_reglas_comision
SET activo = FALSE,
    localizador_origen = 'Desactivada: Hoja2 K32 indica manual (solo Base pyme es automática). Excel VISALIA A10:B10 JADE = 8 €/MWh. Activar si el cliente lo confirma'
WHERE codigo = 'VISALIA_JADE_PYME'
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'VISALIA-FIJO-20250612' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-controlado');

-- Desactivar VISALIA_CUARZO_PYME (la Hoja2 no la contempla)
UPDATE mcas_reglas_comision
SET activo = FALSE,
    localizador_origen = 'Desactivada: Hoja2 K32 indica manual (solo Base pyme es automática). Excel VISALIA A11:B11 CUARZO = 4 €/MWh. Activar si el cliente lo confirma'
WHERE codigo = 'VISALIA_CUARZO_PYME'
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'VISALIA-FIJO-20250612' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-controlado');

-- Conjunto VISALIA-INDEX-20250612 (BORRADOR, fase INDEXADO, vigente desde 2025-06-12)
INSERT INTO mcas_conjuntos_reglas
    (codigo, version, id_comercializadora, id_gestora, fase_precio, vigencia_desde, vigencia_hasta,
     estado, archivo_origen, hoja_origen, fecha_interna_origen, hash_origen,
     creado_por, aprobado_por, fecha_creacion, fecha_aprobacion)
SELECT 'VISALIA-INDEX-20250612', '2025.06.12', c.id, NULL, 'INDEXADO', '2025-06-12', NULL,
       'BORRADOR', 'COMISIONES NEXO A 16032026 1.xlsx', 'VISALIA', '2025-06-12',
       '654D4AF590BC084500EDD3F594862121601B00E9A84847E1FB78DEB695C5197D',
       'seed-hoja2', NULL, CURRENT_TIMESTAMP, NULL
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'VISALIA%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'VISALIA%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_conjuntos_reglas x WHERE x.codigo = 'VISALIA-INDEX-20250612');

-- VISALIA_INDEX_PYME · FEE_PERCENTAGE 52 %, FEE mínimo 1,5 €/MWh
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'VISALIA_INDEX_PYME', 10, 'FEE_PERCENTAGE',
       '{"tipo_suministro":"LUZ","segmento":"PYME","es_renovacion":false}',
       '{"porcentaje_aplicacion_fee":0.52,"fee_minimo_eur_mwh":1.5}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K32: pyme indexado = consumo (MWh) × FEE × 52 %. Excel VISALIA C19:C23 = 52 %, A29: FEE mínimo 1,5 €/MWh (indexado base)',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'VISALIA-INDEX-20250612' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'VISALIA_INDEX_PYME');


-- ======================================================================
-- TOTALENERGIES
-- Hoja2 K44 (cliente): consumo (MWh) × FEE × 45 %, renovación = manual, cualquier otra cosa manual.
-- Excel hoja TOTAL, versión F1 = 2025-05-20.
-- ======================================================================

-- Conjunto TOTAL-FIJO-20250520 (BORRADOR, fase FIJO, vigente desde 2025-05-20)
INSERT INTO mcas_conjuntos_reglas
    (codigo, version, id_comercializadora, id_gestora, fase_precio, vigencia_desde, vigencia_hasta,
     estado, archivo_origen, hoja_origen, fecha_interna_origen, hash_origen,
     creado_por, aprobado_por, fecha_creacion, fecha_aprobacion)
SELECT 'TOTAL-FIJO-20250520', '2025.05.20', c.id, NULL, 'FIJO', '2025-05-20', NULL,
       'BORRADOR', 'COMISIONES NEXO A 16032026 1.xlsx', 'TOTAL', '2025-05-20',
       '654D4AF590BC084500EDD3F594862121601B00E9A84847E1FB78DEB695C5197D',
       'seed-hoja2', NULL, CURRENT_TIMESTAMP, NULL
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'TOTAL%'
  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'TOTAL%') = 1
  AND NOT EXISTS (SELECT 1 FROM mcas_conjuntos_reglas x WHERE x.codigo = 'TOTAL-FIJO-20250520');

-- TOTAL_RENOVACION_MANUAL · MANUAL (revisión manual)
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'TOTAL_RENOVACION_MANUAL', 10, 'MANUAL',
       '{"es_renovacion":true}',
       NULL,
       '{"numero_pagos_sugerido":1}',
       'TotalEnergies: las renovaciones se introducen manualmente (Hoja2)',
       'Hoja2 K44 (cliente): renovación = manual. Excel TOTAL O20:O21 (renovaciones tácitas a ROCK TOTAL) no se usa',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'TOTAL-FIJO-20250520' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'TOTAL_RENOVACION_MANUAL');

-- TOTAL_LUZ · FEE_PERCENTAGE 45 %
INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,
     politica_pago_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'TOTAL_LUZ', 20, 'FEE_PERCENTAGE',
       '{"tipo_suministro":"LUZ","es_renovacion":false}',
       '{"porcentaje_aplicacion_fee":0.45}',
       '{"numero_pagos_sugerido":1}',
       NULL,
       'Hoja2 K44: consumo (MWh) × FEE × 45 %. Excel TOTAL O6:Q6 (CO Referencia) = 0,45',
       TRUE
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'TOTAL-FIJO-20250520' AND cr.estado = 'BORRADOR' AND cr.creado_por = 'seed-hoja2'
  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x
                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = 'TOTAL_LUZ');


-- ======================================================================
-- COMPROBACIÓN FINAL
-- ======================================================================

-- 1. Qué comercializadoras ha encontrado cada patrón (solo se siembra si hay exactamente una).
SELECT 'ENDESA% (excepto ENDESA XXI)' AS patron, COUNT(*) AS encontradas,
       CASE WHEN COUNT(*) = 1 THEN 'SE SIEMBRA' ELSE 'NO SE SIEMBRA (debe haber exactamente una)' END AS resultado
FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'ENDESA%' AND UPPER(x.nombre) NOT LIKE '%XXI%'
UNION ALL
SELECT 'IBERDROLA%' AS patron, COUNT(*) AS encontradas,
       CASE WHEN COUNT(*) = 1 THEN 'SE SIEMBRA' ELSE 'NO SE SIEMBRA (debe haber exactamente una)' END AS resultado
FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'IBERDROLA%'
UNION ALL
SELECT 'IRIS%' AS patron, COUNT(*) AS encontradas,
       CASE WHEN COUNT(*) = 1 THEN 'SE SIEMBRA' ELSE 'NO SE SIEMBRA (debe haber exactamente una)' END AS resultado
FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'IRIS%'
UNION ALL
SELECT 'UNIEL%' AS patron, COUNT(*) AS encontradas,
       CASE WHEN COUNT(*) = 1 THEN 'SE SIEMBRA' ELSE 'NO SE SIEMBRA (debe haber exactamente una)' END AS resultado
FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'UNIEL%'
UNION ALL
SELECT 'NATURGY%' AS patron, COUNT(*) AS encontradas,
       CASE WHEN COUNT(*) = 1 THEN 'SE SIEMBRA' ELSE 'NO SE SIEMBRA (debe haber exactamente una)' END AS resultado
FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'NATURGY%'
UNION ALL
SELECT 'VISALIA%' AS patron, COUNT(*) AS encontradas,
       CASE WHEN COUNT(*) = 1 THEN 'SE SIEMBRA' ELSE 'NO SE SIEMBRA (debe haber exactamente una)' END AS resultado
FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'VISALIA%'
UNION ALL
SELECT 'TOTAL%' AS patron, COUNT(*) AS encontradas,
       CASE WHEN COUNT(*) = 1 THEN 'SE SIEMBRA' ELSE 'NO SE SIEMBRA (debe haber exactamente una)' END AS resultado
FROM tbcomercializadoras x WHERE UPPER(TRIM(x.nombre)) LIKE 'TOTAL%';

-- 2. Recuento por comercializadora (incluye lo sembrado antes por V20260913_2).
SELECT c.nombre AS comercializadora,
       (SELECT COUNT(*) FROM mcas_productos_comerciales p WHERE p.id_comercializadora = c.id) AS productos,
       (SELECT COUNT(*) FROM mcas_conjuntos_reglas cr WHERE cr.id_comercializadora = c.id) AS conjuntos,
       (SELECT COUNT(*) FROM mcas_reglas_comision r JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
         WHERE cr.id_comercializadora = c.id AND r.activo = TRUE) AS reglas_activas,
       (SELECT COUNT(*) FROM mcas_reglas_comision r JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
         WHERE cr.id_comercializadora = c.id AND r.activo = FALSE) AS reglas_inactivas,
       (SELECT COUNT(*) FROM mcas_valores_rango v JOIN mcas_reglas_comision r ON r.id = v.id_regla
          JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
         WHERE cr.id_comercializadora = c.id) AS valores_rango,
       (SELECT COUNT(*) FROM mcas_politicas_retrocesion pr WHERE pr.id_comercializadora = c.id) AS politicas,
       (SELECT COUNT(*) FROM mcas_tramos_retrocesion t JOIN mcas_politicas_retrocesion pr ON pr.id = t.id_politica
         WHERE pr.id_comercializadora = c.id) AS tramos
FROM tbcomercializadoras c
WHERE (UPPER(TRIM(c.nombre)) LIKE 'ENDESA%' AND UPPER(c.nombre) NOT LIKE '%XXI%')
   OR (UPPER(TRIM(c.nombre)) LIKE 'IBERDROLA%')
   OR (UPPER(TRIM(c.nombre)) LIKE 'IRIS%')
   OR (UPPER(TRIM(c.nombre)) LIKE 'UNIEL%')
   OR (UPPER(TRIM(c.nombre)) LIKE 'NATURGY%')
   OR (UPPER(TRIM(c.nombre)) LIKE 'VISALIA%')
   OR (UPPER(TRIM(c.nombre)) LIKE 'TOTAL%')
ORDER BY c.nombre;

-- 3. Conjuntos sembrados o ampliados: estado (debe ser BORRADOR) y reglas.
SELECT cr.codigo, cr.estado, cr.fase_precio, cr.creado_por, c.nombre AS comercializadora,
       (SELECT COUNT(*) FROM mcas_reglas_comision r WHERE r.id_conjunto_reglas = cr.id AND r.activo = TRUE) AS reglas_activas,
       (SELECT COUNT(*) FROM mcas_reglas_comision r WHERE r.id_conjunto_reglas = cr.id AND r.activo = FALSE) AS reglas_inactivas
FROM mcas_conjuntos_reglas cr
JOIN tbcomercializadoras c ON c.id = cr.id_comercializadora
WHERE cr.codigo IN ('ENDESA-FIJO-20260309', 'IBERDROLA-FIJO-20260101', 'IRIS-FIJO-20260304', 'UNIELECTRICA-FIJO-20260101', 'NATURGY-FIJO-20251003', 'VISALIA-FIJO-20250612', 'VISALIA-INDEX-20250612', 'TOTAL-FIJO-20250520')
ORDER BY cr.codigo;

-- 4. Políticas de retrocesión sembradas (todas en BORRADOR, las de Iberdrola no se pueden publicar
--    hasta confirmar la unidad de los tramos SIN_CONFIRMAR).
SELECT p.codigo, p.estado, p.metodo, p.segmento, p.tipo_suministro, p.tarifa_acceso, c.nombre AS comercializadora,
       (SELECT COUNT(*) FROM mcas_tramos_retrocesion t WHERE t.id_politica = p.id) AS tramos,
       (SELECT COUNT(*) FROM mcas_tramos_retrocesion t WHERE t.id_politica = p.id AND t.tipo_valor = 'SIN_CONFIRMAR') AS tramos_sin_confirmar
FROM mcas_politicas_retrocesion p
JOIN tbcomercializadoras c ON c.id = p.id_comercializadora
WHERE p.codigo IN ('RET-ENDESA-RESIDENCIAL-20260309', 'RET-ENDESA-PYME-20260309', 'RET-IBERDROLA-2_0TD-20260101', 'RET-IBERDROLA-3_0TD-20260101', 'RET-IBERDROLA-6_1TD-20260101', 'RET-IRIS-20260304', 'RET-NATURGY-PYME-20251003', 'RET-NATURGY-RESIDENCIAL-20251003')
ORDER BY p.codigo;
