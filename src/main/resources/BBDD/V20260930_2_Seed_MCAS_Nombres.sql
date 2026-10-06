-- ============================================================
-- MCAS - Nombres legibles de las tablas de comisiones y de sus reglas
-- V20260930_2_Seed_MCAS_Nombres.sql
--
-- CÓMO EJECUTARLO
--   Después de V20260929_2_Seed_MCAS_Reglas_Hoja2.sql y de V20260930_1_MCAS_Usabilidad.sql (que añade
--   las columnas nombre y descripcion), contra la BBDD de Aura (MariaDB), ANTES de desplegar el
--   backend de esta versión. Solo DML: no crea ni altera tablas. Revise al final la consulta de
--   comprobación (debe dar 0 tablas y 0 reglas sin nombre).
--
-- PRINCIPIOS
--   * Idempotente: se puede relanzar. Solo rellena lo que está vacío: el nombre si es NULL y la
--     descripción si también lo es (COALESCE), así que nunca pisa un nombre ni una descripción que
--     haya escrito el administrador.
--   * Los nombres no llevan importes ni porcentajes (una «Nueva versión» copia el nombre y el
--     importe puede cambiar): los importes van en la descripción, con su origen (Hoja2, Excel).
--   * La tabla se localiza por su código y la regla por su código dentro de esa tabla (subconsulta,
--     sin UPDATE con JOIN). Las copias hechas con «Nueva versión» tienen otro código: no se tocan.
--   * Solo cambia textos para el administrador: el estado (borrador, activa...), las condiciones y los
--     importes no se modifican, así que también se aplica a tablas ya activas.
--   * 8 tablas y 36 reglas (también las de V20260913_2 que no crea el generador).
--
-- NO EDITAR A MANO: se genera con backend/tools/mcas/generar_seed_hoja2.py (mismo comando que
--   V20260929_2_Seed_MCAS_Reglas_Hoja2.sql).
-- ============================================================


-- ======================================================================
-- ENDESA
-- ======================================================================

-- Tabla ENDESA-FIJO-20260309
UPDATE mcas_conjuntos_reglas
SET nombre = 'Endesa 2026-03',
    descripcion = COALESCE(descripcion, 'Endesa a precio fijo desde el 09/03/2026. Pymes de luz por tabla de consumo y potencia, residencial con importe fijo. Las renovaciones y el gas los completa usted a mano.')
WHERE codigo = 'ENDESA-FIJO-20260309' AND nombre IS NULL;

-- ENDESA_RENOVACION_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'Endesa · renovaciones (a mano)',
    descripcion = COALESCE(descripcion, 'Las renovaciones de Endesa no se calculan: complete usted la comisión a mano.')
WHERE codigo = 'ENDESA_RENOVACION_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'ENDESA-FIJO-20260309');

-- ENDESA_GAS_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'Endesa · gas (a mano)',
    descripcion = COALESCE(descripcion, 'El gas de Endesa no se calcula: complete usted la comisión a mano.')
WHERE codigo = 'ENDESA_GAS_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'ENDESA-FIJO-20260309');

-- ENDESA_RESIDENCIAL_MENOS_10KW
UPDATE mcas_reglas_comision
SET nombre = 'Endesa · residencial menos de 10 kW',
    descripcion = COALESCE(descripcion, 'Importe fijo de 110 € (Hoja2) para luz residencial con menos de 10 kW de potencia.')
WHERE codigo = 'ENDESA_RESIDENCIAL_MENOS_10KW' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'ENDESA-FIJO-20260309');

-- ENDESA_RESIDENCIAL_10KW_O_MAS
UPDATE mcas_reglas_comision
SET nombre = 'Endesa · residencial 10 kW o más',
    descripcion = COALESCE(descripcion, 'Importe fijo de 120 € (Hoja2) para luz residencial con 10 kW de potencia o más.')
WHERE codigo = 'ENDESA_RESIDENCIAL_10KW_O_MAS' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'ENDESA-FIJO-20260309');

-- ENDESA_PYME_LUZ_2_0TD
UPDATE mcas_reglas_comision
SET nombre = 'Endesa · pyme luz 2.0TD',
    descripcion = COALESCE(descripcion, 'Tabla por consumo anual y potencia para la tarifa 2.0TD. Si el contrato no encaja en ninguna casilla, la comisión se revisa a mano.')
WHERE codigo = 'ENDESA_PYME_LUZ_2_0TD' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'ENDESA-FIJO-20260309');

-- ENDESA_PYME_LUZ_3_0TD
UPDATE mcas_reglas_comision
SET nombre = 'Endesa · pyme luz 3.0TD',
    descripcion = COALESCE(descripcion, 'Tabla por consumo anual y potencia para la tarifa 3.0TD. Si el contrato no encaja en ninguna casilla, la comisión se revisa a mano.')
WHERE codigo = 'ENDESA_PYME_LUZ_3_0TD' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'ENDESA-FIJO-20260309');

-- ENDESA_PYME_LUZ_6_1TD
UPDATE mcas_reglas_comision
SET nombre = 'Endesa · pyme luz 6.1TD',
    descripcion = COALESCE(descripcion, 'Tabla por consumo anual y potencia para la tarifa 6.1TD. Si el contrato no encaja en ninguna casilla, la comisión se revisa a mano.')
WHERE codigo = 'ENDESA_PYME_LUZ_6_1TD' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'ENDESA-FIJO-20260309');


-- ======================================================================
-- IBERDROLA
-- ======================================================================

-- Tabla IBERDROLA-FIJO-20260101
UPDATE mcas_conjuntos_reglas
SET nombre = 'Iberdrola 2026-01',
    descripcion = COALESCE(descripcion, 'Iberdrola a precio fijo desde el 01/01/2026. Luz fuera de ámbito por tabla de potencia o consumo, zona y oferta. Dentro de ámbito, renovaciones y gas los completa usted a mano.')
WHERE codigo = 'IBERDROLA-FIJO-20260101' AND nombre IS NULL;

-- IBERDROLA_RENOVACION_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'Iberdrola · renovaciones (a mano)',
    descripcion = COALESCE(descripcion, 'El Excel de Iberdrola no trae tabla de renovaciones: complete usted la comisión a mano.')
WHERE codigo = 'IBERDROLA_RENOVACION_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IBERDROLA-FIJO-20260101');

-- IBERDROLA_DENTRO_AMBITO_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'Iberdrola · dentro de ámbito (a mano)',
    descripcion = COALESCE(descripcion, 'Los contratos de luz dentro de ámbito no se calculan: complete usted la comisión a mano.')
WHERE codigo = 'IBERDROLA_DENTRO_AMBITO_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IBERDROLA-FIJO-20260101');

-- IBERDROLA_GAS_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'Iberdrola · gas (a mano)',
    descripcion = COALESCE(descripcion, 'El gas de Iberdrola, dentro o fuera de ámbito, no se calcula: complete usted la comisión a mano.')
WHERE codigo = 'IBERDROLA_GAS_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IBERDROLA-FIJO-20260101');

-- IBERDROLA_FUERA_HASTA_20KW
UPDATE mcas_reglas_comision
SET nombre = 'Iberdrola · fuera de ámbito hasta 20 kW',
    descripcion = COALESCE(descripcion, 'Tabla por potencia, zona (ZBR o ZAR) y oferta. Las casillas «N. Aplica» quedan vacías: esos contratos se revisan a mano.')
WHERE codigo = 'IBERDROLA_FUERA_HASTA_20KW' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IBERDROLA-FIJO-20260101');

-- IBERDROLA_FUERA_MAS_20KW
UPDATE mcas_reglas_comision
SET nombre = 'Iberdrola · fuera de ámbito más de 20 kW',
    descripcion = COALESCE(descripcion, 'Tabla por consumo anual, zona (ZBR o ZAR) y oferta. Con más de 200.000 kWh («Consultar») la comisión se revisa a mano.')
WHERE codigo = 'IBERDROLA_FUERA_MAS_20KW' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IBERDROLA-FIJO-20260101');


-- ======================================================================
-- IRIS
-- ======================================================================

-- Tabla IRIS-FIJO-20260304
UPDATE mcas_conjuntos_reglas
SET nombre = 'Iris 2026-03',
    descripcion = COALESCE(descripcion, 'Iris a precio fijo desde el 04/03/2026. Pymes de luz: 44 % del FEE. Residencial: 40 € o 64 € según el consumo. Las renovaciones y el gas los completa usted a mano.')
WHERE codigo = 'IRIS-FIJO-20260304' AND nombre IS NULL;

-- IRIS_PLAN_PERSONALIZADO_PYME (creada por V20260913_2)
UPDATE mcas_reglas_comision
SET nombre = 'Iris · Plan Personalizado',
    descripcion = COALESCE(descripcion, 'Regla anterior, desactivada: 44 % del FEE con un FEE mínimo de 5 €/MWh, solo para la tarifa Plan Personalizado. La sustituye «Iris · pyme luz», que no depende de la tarifa de precios.')
WHERE codigo = 'IRIS_PLAN_PERSONALIZADO_PYME' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IRIS-FIJO-20260304');

-- IRIS_PYME_LUZ
UPDATE mcas_reglas_comision
SET nombre = 'Iris · pyme luz',
    descripcion = COALESCE(descripcion, 'Consumo anual (MWh) × FEE del contrato × 44 % (Hoja2). Con un FEE inferior a 5 €/MWh la comisión se revisa a mano.')
WHERE codigo = 'IRIS_PYME_LUZ' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IRIS-FIJO-20260304');

-- IRIS_RESIDENCIAL_MENOS_5000
UPDATE mcas_reglas_comision
SET nombre = 'Iris · residencial menos de 5.000 kWh',
    descripcion = COALESCE(descripcion, 'Importe fijo de 40 € (Hoja2) para luz residencial con menos de 5.000 kWh al año.')
WHERE codigo = 'IRIS_RESIDENCIAL_MENOS_5000' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IRIS-FIJO-20260304');

-- IRIS_RESIDENCIAL_5000_O_MAS
UPDATE mcas_reglas_comision
SET nombre = 'Iris · residencial 5.000 kWh o más',
    descripcion = COALESCE(descripcion, 'Importe fijo de 64 € (Hoja2) para luz residencial con 5.000 kWh al año o más.')
WHERE codigo = 'IRIS_RESIDENCIAL_5000_O_MAS' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IRIS-FIJO-20260304');

-- IRIS_GAS_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'Iris · gas (a mano)',
    descripcion = COALESCE(descripcion, 'El gas de Iris no se calcula: complete usted la comisión a mano.')
WHERE codigo = 'IRIS_GAS_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IRIS-FIJO-20260304');

-- IRIS_RENOVACION_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'Iris · renovaciones (a mano)',
    descripcion = COALESCE(descripcion, 'Las renovaciones de Iris dependen del mes y de si se firma contrato nuevo: complete usted la comisión a mano.')
WHERE codigo = 'IRIS_RENOVACION_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'IRIS-FIJO-20260304');


-- ======================================================================
-- UNIELÉCTRICA
-- ======================================================================

-- Tabla UNIELECTRICA-FIJO-20260101
UPDATE mcas_conjuntos_reglas
SET nombre = 'Unieléctrica 2026-01',
    descripcion = COALESCE(descripcion, 'Unieléctrica a precio fijo desde el 01/01/2026. Pymes de luz: 55 % del FEE. Residencial: 50 € u 80 € según el consumo. Las renovaciones y el gas los completa usted a mano.')
WHERE codigo = 'UNIELECTRICA-FIJO-20260101' AND nombre IS NULL;

-- UNIELECTRICA_RENOVACION_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'Unieléctrica · renovaciones (a mano)',
    descripcion = COALESCE(descripcion, 'La Hoja2 no define comisión de renovación para Unieléctrica: complete usted la comisión a mano.')
WHERE codigo = 'UNIELECTRICA_RENOVACION_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'UNIELECTRICA-FIJO-20260101');

-- UNIELECTRICA_GAS_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'Unieléctrica · gas (a mano)',
    descripcion = COALESCE(descripcion, 'El gas de Unieléctrica no se calcula: complete usted la comisión a mano.')
WHERE codigo = 'UNIELECTRICA_GAS_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'UNIELECTRICA-FIJO-20260101');

-- UNIELECTRICA_PYME_LUZ
UPDATE mcas_reglas_comision
SET nombre = 'Unieléctrica · pyme luz',
    descripcion = COALESCE(descripcion, 'Consumo anual (MWh) × FEE del contrato × 55 % (Hoja2).')
WHERE codigo = 'UNIELECTRICA_PYME_LUZ' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'UNIELECTRICA-FIJO-20260101');

-- UNIELECTRICA_RESIDENCIAL_MENOS_5000
UPDATE mcas_reglas_comision
SET nombre = 'Unieléctrica · residencial menos de 5.000 kWh',
    descripcion = COALESCE(descripcion, 'Importe fijo de 50 € (Hoja2) para luz residencial con menos de 5.000 kWh al año.')
WHERE codigo = 'UNIELECTRICA_RESIDENCIAL_MENOS_5000' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'UNIELECTRICA-FIJO-20260101');

-- UNIELECTRICA_RESIDENCIAL_5000_O_MAS
UPDATE mcas_reglas_comision
SET nombre = 'Unieléctrica · residencial 5.000 kWh o más',
    descripcion = COALESCE(descripcion, 'Importe fijo de 80 € (Hoja2) para luz residencial con 5.000 kWh al año o más.')
WHERE codigo = 'UNIELECTRICA_RESIDENCIAL_5000_O_MAS' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'UNIELECTRICA-FIJO-20260101');


-- ======================================================================
-- NATURGY
-- ======================================================================

-- Tabla NATURGY-FIJO-20251003
UPDATE mcas_conjuntos_reglas
SET nombre = 'Naturgy 2025-10',
    descripcion = COALESCE(descripcion, 'Naturgy a precio fijo desde el 03/10/2025. Residencial: 50,63 €. Pymes de luz: 40,50 € con menos de 10.000 kWh y 4,05 € por MWh a partir de ahí. Las renovaciones y el gas de pymes los completa usted a mano.')
WHERE codigo = 'NATURGY-FIJO-20251003' AND nombre IS NULL;

-- NATURGY_RENOVACION_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'Naturgy · renovaciones (a mano)',
    descripcion = COALESCE(descripcion, 'Ni la Hoja2 ni el Excel de Naturgy definen comisión de renovación: complete usted la comisión a mano.')
WHERE codigo = 'NATURGY_RENOVACION_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'NATURGY-FIJO-20251003');

-- NATURGY_RESIDENCIAL
UPDATE mcas_reglas_comision
SET nombre = 'Naturgy · residencial',
    descripcion = COALESCE(descripcion, 'Importe fijo de 50,63 € (Hoja2) para residencial, de luz o de gas.')
WHERE codigo = 'NATURGY_RESIDENCIAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'NATURGY-FIJO-20251003');

-- NATURGY_PYME_LUZ_MENOS_10MWH
UPDATE mcas_reglas_comision
SET nombre = 'Naturgy · pyme luz menos de 10.000 kWh',
    descripcion = COALESCE(descripcion, 'Importe fijo de 40,50 € (Hoja2) para pymes de luz con menos de 10.000 kWh al año.')
WHERE codigo = 'NATURGY_PYME_LUZ_MENOS_10MWH' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'NATURGY-FIJO-20251003');

-- NATURGY_PYME_LUZ_10MWH_O_MAS
UPDATE mcas_reglas_comision
SET nombre = 'Naturgy · pyme luz 10.000 kWh o más',
    descripcion = COALESCE(descripcion, 'Consumo anual (MWh) × 4,05 € (Hoja2) para pymes de luz con 10.000 kWh al año o más.')
WHERE codigo = 'NATURGY_PYME_LUZ_10MWH_O_MAS' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'NATURGY-FIJO-20251003');

-- NATURGY_PYME_GAS_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'Naturgy · pyme gas (a mano)',
    descripcion = COALESCE(descripcion, 'El gas de pymes de Naturgy depende del FEE de gas: complete usted la comisión a mano.')
WHERE codigo = 'NATURGY_PYME_GAS_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'NATURGY-FIJO-20251003');


-- ======================================================================
-- VISALIA
-- ======================================================================

-- Tabla VISALIA-FIJO-20250612
UPDATE mcas_conjuntos_reglas
SET nombre = 'Visalia 2025-06',
    descripcion = COALESCE(descripcion, 'Visalia a precio fijo desde el 12/06/2025. Solo la tarifa Base de pymes se calcula sola (52 % del FEE). El resto de tarifas están desactivadas y las completa usted a mano.')
WHERE codigo = 'VISALIA-FIJO-20250612' AND nombre IS NULL;

-- VISALIA_BASE_PYME (creada por V20260913_2)
UPDATE mcas_reglas_comision
SET nombre = 'Visalia · pyme Base',
    descripcion = COALESCE(descripcion, 'Consumo anual (MWh) × FEE del contrato × 52 % para la tarifa Base (Excel de comisiones). Con un FEE inferior a 5 €/MWh la comisión se revisa a mano.')
WHERE codigo = 'VISALIA_BASE_PYME' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'VISALIA-FIJO-20250612');

-- VISALIA_FIJO_COMERCIO_PYME (creada por V20260913_2)
UPDATE mcas_reglas_comision
SET nombre = 'Visalia · pyme Fijo Comercio',
    descripcion = COALESCE(descripcion, 'Desactivada: la Hoja2 indica que esta tarifa se completa a mano. Importe del Excel de comisiones: 52 % del FEE. Actívela solo si el cliente lo confirma.')
WHERE codigo = 'VISALIA_FIJO_COMERCIO_PYME' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'VISALIA-FIJO-20250612');

-- VISALIA_ESMERALDA_PYME (creada por V20260913_2)
UPDATE mcas_reglas_comision
SET nombre = 'Visalia · pyme Esmeralda',
    descripcion = COALESCE(descripcion, 'Desactivada: la Hoja2 indica que esta tarifa se completa a mano. Importe del Excel de comisiones: 16 €/MWh. Actívela solo si el cliente lo confirma.')
WHERE codigo = 'VISALIA_ESMERALDA_PYME' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'VISALIA-FIJO-20250612');

-- VISALIA_JADE_PYME (creada por V20260913_2)
UPDATE mcas_reglas_comision
SET nombre = 'Visalia · pyme Jade',
    descripcion = COALESCE(descripcion, 'Desactivada: la Hoja2 indica que esta tarifa se completa a mano. Importe del Excel de comisiones: 8 €/MWh. Actívela solo si el cliente lo confirma.')
WHERE codigo = 'VISALIA_JADE_PYME' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'VISALIA-FIJO-20250612');

-- VISALIA_CUARZO_PYME (creada por V20260913_2)
UPDATE mcas_reglas_comision
SET nombre = 'Visalia · pyme Cuarzo',
    descripcion = COALESCE(descripcion, 'Desactivada: la Hoja2 indica que esta tarifa se completa a mano. Importe del Excel de comisiones: 4 €/MWh. Actívela solo si el cliente lo confirma.')
WHERE codigo = 'VISALIA_CUARZO_PYME' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'VISALIA-FIJO-20250612');

-- Tabla VISALIA-INDEX-20250612
UPDATE mcas_conjuntos_reglas
SET nombre = 'Visalia indexado 2025-06',
    descripcion = COALESCE(descripcion, 'Visalia indexado desde el 12/06/2025. Pymes de luz: 52 % del FEE. Solo se puede activar con el interruptor Indexado encendido.')
WHERE codigo = 'VISALIA-INDEX-20250612' AND nombre IS NULL;

-- VISALIA_INDEX_PYME
UPDATE mcas_reglas_comision
SET nombre = 'Visalia · pyme indexado',
    descripcion = COALESCE(descripcion, 'Consumo anual (MWh) × FEE del contrato × 52 % (Hoja2). Con un FEE inferior a 1,5 €/MWh la comisión se revisa a mano.')
WHERE codigo = 'VISALIA_INDEX_PYME' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'VISALIA-INDEX-20250612');


-- ======================================================================
-- TOTALENERGIES
-- ======================================================================

-- Tabla TOTAL-FIJO-20250520
UPDATE mcas_conjuntos_reglas
SET nombre = 'TotalEnergies 2025-05',
    descripcion = COALESCE(descripcion, 'TotalEnergies a precio fijo desde el 20/05/2025. Luz: 45 % del FEE. Las renovaciones las completa usted a mano.')
WHERE codigo = 'TOTAL-FIJO-20250520' AND nombre IS NULL;

-- TOTAL_RENOVACION_MANUAL
UPDATE mcas_reglas_comision
SET nombre = 'TotalEnergies · renovaciones (a mano)',
    descripcion = COALESCE(descripcion, 'Las renovaciones de TotalEnergies no se calculan: complete usted la comisión a mano.')
WHERE codigo = 'TOTAL_RENOVACION_MANUAL' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'TOTAL-FIJO-20250520');

-- TOTAL_LUZ
UPDATE mcas_reglas_comision
SET nombre = 'TotalEnergies · luz',
    descripcion = COALESCE(descripcion, 'Consumo anual (MWh) × FEE del contrato × 45 % (Hoja2).')
WHERE codigo = 'TOTAL_LUZ' AND nombre IS NULL
  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr
                             WHERE cr.codigo = 'TOTAL-FIJO-20250520');


-- ======================================================================
-- COMPROBACIÓN FINAL: tablas y reglas sembradas que siguen sin nombre
-- (0 y 0 si se han encontrado todas las comercializadoras)
-- ======================================================================

SELECT 'tablas_sin_nombre' AS comprobacion, COUNT(*) AS total
FROM mcas_conjuntos_reglas
WHERE codigo IN ('ENDESA-FIJO-20260309', 'IBERDROLA-FIJO-20260101', 'IRIS-FIJO-20260304', 'UNIELECTRICA-FIJO-20260101', 'NATURGY-FIJO-20251003', 'VISALIA-FIJO-20250612', 'VISALIA-INDEX-20250612', 'TOTAL-FIJO-20250520') AND nombre IS NULL
UNION ALL
SELECT 'reglas_sin_nombre', COUNT(*)
FROM mcas_reglas_comision r
JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
WHERE cr.codigo IN ('ENDESA-FIJO-20260309', 'IBERDROLA-FIJO-20260101', 'IRIS-FIJO-20260304', 'UNIELECTRICA-FIJO-20260101', 'NATURGY-FIJO-20251003', 'VISALIA-FIJO-20250612', 'VISALIA-INDEX-20250612', 'TOTAL-FIJO-20250520') AND r.nombre IS NULL;
