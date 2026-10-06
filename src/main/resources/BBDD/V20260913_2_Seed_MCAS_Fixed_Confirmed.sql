-- ============================================================
-- MCAS - Datos iniciales de reglas confirmadas (precio fijo)
-- V20260913_2_Seed_MCAS_Fixed_Confirmed.sql   (sustituye a V20260731_2)
--
-- Ejecutar DESPUÉS de V20260913_1_Create_MCAS_Module.sql.
-- Idempotente: se puede relanzar. Nunca activa indicadores y deja los
-- conjuntos en BORRADOR para que un administrador los revise, valide y publique.
--
-- La comercializadora se localiza por nombre en tbcomercializadoras. Si no
-- existe (p. ej. VISALIA no está dada de alta), sus inserciones no hacen nada:
-- la consulta final lo muestra.
--
-- Origen (SHA-256):
--   LISTADO DATOS AUTOMATIZACION SERVICIOS 1.xlsx
--   4D5D5174AED1BDDF1CC167AA701159E6163E2F5D2798202AAE11B4057893CAC8
--   COMISIONES NEXO A 16032026 1.xlsx
--   654D4AF590BC084500EDD3F594862121601B00E9A84847E1FB78DEB695C5197D
-- ============================================================

-- -----------------------------------------------
-- IRIS: Plan Personalizado, 44 % sobre el FEE del contrato, FEE mínimo 5.
-- -----------------------------------------------
INSERT INTO mcas_productos_comerciales
    (id_comercializadora, codigo, nombre, tipo_precio, tipo_suministro,
     activo_desde, activo)
SELECT c.id, 'PLAN_PERSONALIZADO', 'Plan Personalizado', 'FIJO', 'LUZ',
       '2026-03-04', 1
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'IRIS%'
  AND NOT EXISTS (
      SELECT 1 FROM mcas_productos_comerciales p
      WHERE p.id_comercializadora = c.id AND p.codigo = 'PLAN_PERSONALIZADO')
LIMIT 1;

INSERT INTO mcas_conjuntos_reglas
    (codigo, version, id_comercializadora, fase_precio, vigencia_desde,
     estado, archivo_origen, hoja_origen, fecha_interna_origen, hash_origen,
     creado_por, aprobado_por, fecha_aprobacion)
SELECT 'IRIS-FIJO-20260304', '2026.03.04', c.id, 'FIJO', '2026-03-04',
       'BORRADOR', 'COMISIONES NEXO A 16032026 1.xlsx', 'IRIS', '2026-03-04',
       '654D4AF590BC084500EDD3F594862121601B00E9A84847E1FB78DEB695C5197D',
       'seed-controlado', NULL, NULL
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'IRIS%'
  AND NOT EXISTS (SELECT 1 FROM mcas_conjuntos_reglas x WHERE x.codigo = 'IRIS-FIJO-20260304')
LIMIT 1;

INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json,
     calculo_json, motivo_manual, localizador_origen, activo)
SELECT cr.id, 'IRIS_PLAN_PERSONALIZADO_PYME', 10, 'FEE_PERCENTAGE',
       '{"tipo_suministro":"LUZ","segmento":"PYME","codigo_producto":"PLAN_PERSONALIZADO","es_renovacion":false}',
       '{"porcentaje_aplicacion_fee":0.44,"fee_minimo_eur_mwh":5}',
       NULL, 'IRIS: Plan Personalizado / FEE minimo 5 / 44%', 1
FROM mcas_conjuntos_reglas cr
WHERE cr.codigo = 'IRIS-FIJO-20260304'
  AND NOT EXISTS (
      SELECT 1 FROM mcas_reglas_comision r
      WHERE r.id_conjunto_reglas = cr.id AND r.codigo = 'IRIS_PLAN_PERSONALIZADO_PYME');

-- -----------------------------------------------
-- VISALIA: productos fijos confirmados por nombre. Gas, potencia y residencial
-- quedan fuera a propósito hasta que el cliente los confirme.
-- -----------------------------------------------
INSERT INTO mcas_productos_comerciales
    (id_comercializadora, codigo, nombre, tipo_precio, tipo_suministro,
     activo_desde, activo)
SELECT c.id, s.codigo, s.nombre, 'FIJO', 'LUZ', '2025-06-12', 1
FROM tbcomercializadoras c
CROSS JOIN (
    SELECT 'BASE' codigo, 'Base' nombre
    UNION ALL SELECT 'FIJO_COMERCIO', 'Fijo Comercio'
    UNION ALL SELECT 'ESMERALDA', 'Esmeralda'
    UNION ALL SELECT 'JADE', 'Jade'
    UNION ALL SELECT 'CUARZO', 'Cuarzo'
) s
WHERE UPPER(TRIM(c.nombre)) LIKE 'VISALIA%'
  AND NOT EXISTS (
      SELECT 1 FROM mcas_productos_comerciales p
      WHERE p.id_comercializadora = c.id AND p.codigo = s.codigo);

INSERT INTO mcas_conjuntos_reglas
    (codigo, version, id_comercializadora, fase_precio, vigencia_desde,
     estado, archivo_origen, hoja_origen, fecha_interna_origen, hash_origen,
     creado_por, aprobado_por, fecha_aprobacion)
SELECT 'VISALIA-FIJO-20250612', '2025.06.12', c.id, 'FIJO', '2025-06-12',
       'BORRADOR', 'COMISIONES NEXO A 16032026 1.xlsx', 'VISALIA', '2025-06-12',
       '654D4AF590BC084500EDD3F594862121601B00E9A84847E1FB78DEB695C5197D',
       'seed-controlado', NULL, NULL
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'VISALIA%'
  AND NOT EXISTS (SELECT 1 FROM mcas_conjuntos_reglas x WHERE x.codigo = 'VISALIA-FIJO-20250612')
LIMIT 1;

INSERT INTO mcas_reglas_comision
    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json,
     calculo_json, localizador_origen, activo)
SELECT cr.id, s.codigo_regla, s.prioridad, s.metodo, s.condiciones,
       s.calculo, s.localizador, 1
FROM mcas_conjuntos_reglas cr
CROSS JOIN (
    SELECT 'VISALIA_BASE_PYME' codigo_regla, 10 prioridad, 'FEE_PERCENTAGE' metodo,
           '{"tipo_suministro":"LUZ","segmento":"PYME","codigo_producto":"BASE","es_renovacion":false}' condiciones,
           '{"porcentaje_aplicacion_fee":0.52,"fee_minimo_eur_mwh":5}' calculo,
           'VISALIA: Base / FEE minimo 5 / 52%' localizador
    UNION ALL SELECT 'VISALIA_FIJO_COMERCIO_PYME', 11, 'FEE_PERCENTAGE',
           '{"tipo_suministro":"LUZ","segmento":"PYME","codigo_producto":"FIJO_COMERCIO","es_renovacion":false}',
           '{"porcentaje_aplicacion_fee":0.52,"fee_minimo_eur_mwh":5}',
           'VISALIA: Fijo Comercio / FEE minimo 5 / 52%'
    UNION ALL SELECT 'VISALIA_ESMERALDA_PYME', 12, 'RATE_PER_MWH',
           '{"tipo_suministro":"LUZ","segmento":"PYME","codigo_producto":"ESMERALDA","es_renovacion":false}',
           '{"tasa_eur_mwh":16}', 'VISALIA: Esmeralda / 16 EUR-MWh'
    UNION ALL SELECT 'VISALIA_JADE_PYME', 13, 'RATE_PER_MWH',
           '{"tipo_suministro":"LUZ","segmento":"PYME","codigo_producto":"JADE","es_renovacion":false}',
           '{"tasa_eur_mwh":8}', 'VISALIA: Jade / 8 EUR-MWh'
    UNION ALL SELECT 'VISALIA_CUARZO_PYME', 14, 'RATE_PER_MWH',
           '{"tipo_suministro":"LUZ","segmento":"PYME","codigo_producto":"CUARZO","es_renovacion":false}',
           '{"tasa_eur_mwh":4}', 'VISALIA: Cuarzo / 4 EUR-MWh'
) s
WHERE cr.codigo = 'VISALIA-FIJO-20250612'
  AND NOT EXISTS (
      SELECT 1 FROM mcas_reglas_comision r
      WHERE r.id_conjunto_reglas = cr.id AND r.codigo = s.codigo_regla);

-- -----------------------------------------------
-- Seguridad: si una revisión anterior de este seed publicó los conjuntos,
-- se devuelven a BORRADOR. Nunca toca versiones publicadas por una persona.
-- -----------------------------------------------
UPDATE mcas_conjuntos_reglas
SET estado = 'BORRADOR', aprobado_por = NULL, fecha_aprobacion = NULL
WHERE codigo IN ('IRIS-FIJO-20260304', 'VISALIA-FIJO-20250612')
  AND creado_por = 'seed-controlado'
  AND aprobado_por = 'seed-controlado';

-- -----------------------------------------------
-- Comprobación: qué se ha cargado por comercializadora.
-- Esperado con los datos actuales: IRIS -> 1 producto, 1 conjunto, 1 regla.
-- VISALIA solo aparece si existe en tbcomercializadoras.
-- -----------------------------------------------
SELECT c.nombre AS comercializadora,
       (SELECT COUNT(*) FROM mcas_productos_comerciales p WHERE p.id_comercializadora = c.id) AS productos,
       (SELECT COUNT(*) FROM mcas_conjuntos_reglas cr WHERE cr.id_comercializadora = c.id) AS conjuntos,
       (SELECT COUNT(*) FROM mcas_reglas_comision r
          JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
         WHERE cr.id_comercializadora = c.id) AS reglas
FROM tbcomercializadoras c
WHERE UPPER(TRIM(c.nombre)) LIKE 'IRIS%' OR UPPER(TRIM(c.nombre)) LIKE 'VISALIA%';
