-- Ficha técnica de los productos HUAWEI del inventario (inversores, medida, monitorización y baterías). MariaDB 10.11.
-- Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts); también se puede aplicar a mano:
--   mysql -u root -p aura < V20261112_1_Update_Planificador_HUAWEI_fichas.sql
-- IMPORTANTE: subir ANTES los PDF a Dropbox: /AURA INGENIERIA/FICHAS TECNICAS/HUAWEI/ (todos en la misma carpeta, sin subcarpetas).
-- La ruta es relativa a la carpeta de fichas (DROPBOX_DATASHEETS_ROOT = /AURA INGENIERIA/FICHAS TECNICAS en el servidor).
-- Solo rellena los productos que no tienen ficha (si alguien la cambió a mano, no se pisa) y se puede repetir sin riesgo.
-- Sin producto en el inventario (sus PDF se suben igual, pero no se enlazan): 11_SUN2000-12-15-17-20-25k-mb0-datasheet.pdf, 13_SUN2000-30-40K-MC0.pdf, 15_SUN2000-450W-P2_SUN2000-600W-P.pdf, ESS-LUNA2000-215-2S10.pdf, ESS-LUNA2000-241-2S1.pdf, Smartlogger3000C_EN.pdf.

SET NAMES utf8mb4;

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/01_SUN2000_5_8_10KTL_M1_Alta_Corriente_EN.pdf'
WHERE id IN ('inv-huawei-sun2000-5ktl-m1-hc', 'inv-huawei-sun2000-8ktl-m1-hc', 'inv-huawei-sun2000-10ktl-m1-hc') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/02_SUN2000_5_8_10K_MAP0.pdf'
WHERE id IN ('inv-huawei-sun2000-5ktl-map0', 'inv-huawei-sun2000-8ktl-map0', 'inv-huawei-10k-map0') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/03b_SUN2000_20KTL_M2_Alta_Corriente_ES.pdf'
WHERE id IN ('inv-huawei-sun2000-20ktl-m2-hc') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/03_SUN2000_20KTL_M2_ES.pdf'
WHERE id IN ('inv-huawei-sun2000-20ktl-m2') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/04_SUN2000_30KTL_M3_ES.pdf'
WHERE id IN ('inv-huawei-sun2000-30ktl-m3') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/05_SUN2000_50KTL_M3_ES.pdf'
WHERE id IN ('inv-huawei-sun2000-50ktl-m3') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/06_SUN2000_100KTL_M1_ES.pdf'
WHERE id IN ('inv-huawei-sun2000-100ktl-m1') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/07_SUN2000_150K_MG0.pdf'
WHERE id IN ('inv-huawei-sun2000-150ktl-mg0') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/08_SUN2000_100KTL_M2_ES.pdf'
WHERE id IN ('inv-huawei-sun2000-100ktl-m2') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/09_SUN2000_8_10K_LC0_monofasico.pdf'
WHERE id IN ('inv-huawei-sun2000-8ktl-lc0', 'inv-huawei-sun2000-10ktl-lc0') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/10a_SUN2000_5KTL_L1_monofasico_ES.pdf'
WHERE id IN ('inv-huawei-sun2000-5ktl-l1') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/12_SUN2000-3-3-68-4-4-6-5-6k-lb0-datasheet.pdf'
WHERE id IN ('inv-huawei-5k') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/14_SUN2000-50K-MC0.pdf'
WHERE id IN ('52bea8da-38ba-4772-b03d-e24cd636f54b') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/DDSU666H_DTSU666H_medida_indirecta_EN.pdf'
WHERE id IN ('cnt-huawei-dtsu666-h-250a') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/SmartPowerSensor_DDSU666H_DTSU666H_CT_ES.pdf'
WHERE id IN ('cnt-huawei-ddsu666-h', 'cnt-huawei-dtsu666-h') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/SmartPS_80AI_T0_DTSU666HW_directo_y_CT_EN.pdf'
WHERE id IN ('cnt-huawei-dtsu666-hw') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/SmartLogger3000A_ES.pdf'
WHERE id IN ('mon-smartlogger', 'mon-smartlogger-3000a03eu', 'mon-smartlogger-3000a01eu') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/SmartLogger3000B_ES.pdf'
WHERE id IN ('mon-smartlogger-3000b', 'mon-smartlogger-3000b-smartmodule') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/Smart_Dongle_4G_ES.pdf'
WHERE id IN ('mon-dongle-4g') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/Smart_Dongle_WLAN_FE_ES.pdf'
WHERE id IN ('mon-dongle-wlan') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/LUNA2000_S0_5kWh_Controller_C0.pdf'
WHERE id IN ('bat-luna-5kw-c0', 'bat-luna-5-e0', 'bat-luna-5') AND ficha_tecnica = '';

UPDATE pl_equipos SET ficha_tecnica = 'HUAWEI/LUNA2000_S1_7kWh_Controller_C1.pdf'
WHERE id IN ('bat-luna-5-e1', 'bat-luna-7', 'bat-luna-10c1') AND ficha_tecnica = '';

-- Comprobación
SELECT id, modelo, ficha_tecnica FROM pl_equipos WHERE marca = 'HUAWEI' ORDER BY categoria, posicion;
