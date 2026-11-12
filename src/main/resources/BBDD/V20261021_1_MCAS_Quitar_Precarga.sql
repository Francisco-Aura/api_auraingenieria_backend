-- ============================================================
-- MCAS - Quitar la precarga de comercializadoras (21/10/2026)
-- V20261021_1_MCAS_Quitar_Precarga.sql
--
-- POR QUÉ
--   El módulo de comisiones se entrega vacío: cada comercializadora la da de
--   alta el administrador desde Configuración MCAS. Las precargas que había
--   (V20260913_2_Seed_MCAS_Fixed_Confirmed.sql, V20260929_2_Seed_MCAS_Reglas_Hoja2.sql
--   y V20260930_2_Seed_MCAS_Nombres.sql) se han retirado del proyecto. Este
--   script quita lo que dejaron en una base de datos donde se ejecutaron.
--
-- CUÁNDO EJECUTARLO
--   Solo hace falta si en esa base de datos se ejecutó alguna de esas
--   precargas (la de desarrollo tiene V20260913_2 desde el 13/09/2026). Para
--   saberlo, antes de nada (dos consultas):
--     SELECT COUNT(*) FROM mcas_conjuntos_reglas
--      WHERE creado_por IN ('seed-controlado', 'seed-hoja2')
--     SELECT COUNT(*) FROM mcas_politicas_retrocesion
--      WHERE archivo_origen = 'COMISIONES NEXO A 16032026 1.xlsx'
--   Si las dos dan 0, no hay nada que quitar. Ejecutarlo de todos modos no
--   hace daño: no borra nada que no venga de la precarga.
--
-- CÓMO EJECUTARLO
--   1. Copia de seguridad de la base de datos.
--   2. Con V20260913_1, V20260929_1 y V20260930_1 ya ejecutados, lanzar este
--      script COMPLETO y en una sola conexión (usa tablas temporales).
--   3. Revisar las consultas del final (apartado 6).
--   Es idempotente: relanzarlo no borra nada más. Va en una transacción: si
--   una sentencia falla, ejecute ROLLBACK y no se habrá borrado nada.
--
-- QUÉ BORRA (solo lo que crearon las precargas)
--   * Tablas de comisiones con creado_por = 'seed-controlado' (V20260913_2) o
--     'seed-hoja2' (V20260929_2), con sus reglas y los tramos o celdas de esas
--     reglas (mcas_valores_rango). La aplicación pone en creado_por el nombre
--     de quien crea la tabla, así que una tabla creada por una persona nunca
--     lleva esos valores. Los nombres legibles de V20260930_2 estaban en esas
--     mismas filas y se van con ellas.
--   * Políticas de retrocesión con archivo_origen = 'COMISIONES NEXO A
--     16032026 1.xlsx' y sus tramos (la aplicación nunca rellena esa columna
--     en las políticas).
--   * Productos comerciales precargados: los de la lista del apartado 1
--     (comercializadora, código y fecha «activo desde» de la precarga).
--
-- QUÉ NO BORRA
--   * Nada que tenga propuestas (mcas_propuestas) y, por tanto, comisiones o
--     servicios ligados: una tabla con propuestas o con líneas de propuesta en
--     sus reglas, una política asociada a alguna propuesta, un producto que
--     usa un contrato o una regla que se queda. Todo eso sigue en su sitio y la
--     consulta 6.2 lo lista para que se decida a mano.
--   * Las tablas copiadas por una persona con «Nueva versión» a partir de una
--     tabla precargada (también salen en la consulta 6.2).
--   * Los interruptores (configuracion_modulos): no se tocan.
--
-- Nomenclatura: tablas en minúsculas y snake_case, como las crea Hibernate.
-- ============================================================

-- Los textos de las consultas llevan tildes
SET NAMES utf8mb4;

START TRANSACTION;

-- -----------------------------------------------
-- 1. Productos comerciales que creó la precarga: comercializadora (por el
--    principio de su nombre, como la buscaba la precarga), código y fecha
--    «activo desde» que puso la precarga. Las tablas temporales guardan solo
--    ids: así no se mezclan juegos de caracteres al comparar.
-- -----------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_mcas_productos_precarga;
CREATE TEMPORARY TABLE tmp_mcas_productos_precarga AS
SELECT pc.id
  FROM mcas_productos_comerciales pc
  JOIN tbcomercializadoras c ON c.id = pc.id_comercializadora
 WHERE (UPPER(TRIM(c.nombre)) LIKE 'IRIS%'                                  -- V20260913_2
        AND pc.codigo = 'PLAN_PERSONALIZADO'
        AND pc.activo_desde = DATE '2026-03-04')
    OR (UPPER(TRIM(c.nombre)) LIKE 'VISALIA%'                               -- V20260913_2
        AND pc.codigo IN ('BASE', 'FIJO_COMERCIO', 'ESMERALDA', 'JADE', 'CUARZO')
        AND pc.activo_desde = DATE '2025-06-12')
    OR (UPPER(TRIM(c.nombre)) LIKE 'IBERDROLA%'                             -- V20260929_2
        AND pc.codigo IN ('OFERTA_0', 'OFERTA_1', 'OFERTA_2', 'OFERTA_3')
        AND pc.activo_desde = DATE '2026-01-01')
    OR (UPPER(TRIM(c.nombre)) LIKE 'NATURGY%'                               -- V20260929_2
        AND pc.codigo IN ('PF_LUZ_ONE', 'PF_LUZ', 'PF_LUZ_SUPRA', 'PV_LUZ_ONE', 'PV_LUZ', 'PV_SUPRA')
        AND pc.activo_desde = DATE '2025-10-03');

-- -----------------------------------------------
-- 2. Qué se va a quitar (nada con propuestas)
-- -----------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_mcas_quitar_conjuntos;
CREATE TEMPORARY TABLE tmp_mcas_quitar_conjuntos AS
SELECT cr.id
  FROM mcas_conjuntos_reglas cr
 WHERE cr.creado_por IN ('seed-controlado', 'seed-hoja2')
   AND NOT EXISTS (SELECT 1 FROM mcas_propuestas p WHERE p.id_conjunto_reglas = cr.id)
   AND NOT EXISTS (SELECT 1
                     FROM mcas_lineas_propuesta l
                     JOIN mcas_reglas_comision r ON r.id = l.id_regla
                    WHERE r.id_conjunto_reglas = cr.id);

DROP TEMPORARY TABLE IF EXISTS tmp_mcas_quitar_reglas;
CREATE TEMPORARY TABLE tmp_mcas_quitar_reglas AS
SELECT r.id
  FROM mcas_reglas_comision r
  JOIN tmp_mcas_quitar_conjuntos q ON q.id = r.id_conjunto_reglas;

DROP TEMPORARY TABLE IF EXISTS tmp_mcas_quitar_politicas;
CREATE TEMPORARY TABLE tmp_mcas_quitar_politicas AS
SELECT pr.id
  FROM mcas_politicas_retrocesion pr
 WHERE pr.archivo_origen = 'COMISIONES NEXO A 16032026 1.xlsx'
   AND NOT EXISTS (SELECT 1 FROM mcas_propuestas p WHERE p.id_politica_retrocesion = pr.id);

-- -----------------------------------------------
-- 3. Tablas de comisiones precargadas: tramos, reglas y tablas
-- -----------------------------------------------
DELETE FROM mcas_valores_rango
 WHERE id_regla IN (SELECT id FROM tmp_mcas_quitar_reglas);

DELETE FROM mcas_reglas_comision
 WHERE id IN (SELECT id FROM tmp_mcas_quitar_reglas);

DELETE FROM mcas_conjuntos_reglas
 WHERE id IN (SELECT id FROM tmp_mcas_quitar_conjuntos);

-- -----------------------------------------------
-- 4. Políticas de retrocesión precargadas: tramos y políticas
-- -----------------------------------------------
DELETE FROM mcas_tramos_retrocesion
 WHERE id_politica IN (SELECT id FROM tmp_mcas_quitar_politicas);

DELETE FROM mcas_politicas_retrocesion
 WHERE id IN (SELECT id FROM tmp_mcas_quitar_politicas);

-- -----------------------------------------------
-- 5. Productos comerciales precargados que nadie usa
--    (después de quitar las reglas precargadas: solo cuentan las que se quedan)
-- -----------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_mcas_quitar_productos;
CREATE TEMPORARY TABLE tmp_mcas_quitar_productos AS
SELECT pc.id
  FROM mcas_productos_comerciales pc
  JOIN tmp_mcas_productos_precarga s ON s.id = pc.id
 WHERE NOT EXISTS (SELECT 1 FROM contratos ct WHERE ct.mcas_id_producto_comercial = pc.id)
   AND NOT EXISTS (SELECT 1
                     FROM mcas_reglas_comision r
                     JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
                    WHERE cr.id_comercializadora = pc.id_comercializadora
                      AND r.condiciones_json LIKE CONCAT('%"', pc.codigo, '"%'))
   AND NOT EXISTS (SELECT 1
                     FROM mcas_valores_rango v
                     JOIN mcas_reglas_comision r ON r.id = v.id_regla
                     JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas
                    WHERE cr.id_comercializadora = pc.id_comercializadora
                      AND v.dimension = 'codigo_producto'
                      AND v.valor_categoria = pc.codigo);

DELETE FROM mcas_productos_comerciales
 WHERE id IN (SELECT id FROM tmp_mcas_quitar_productos);

COMMIT;

-- -----------------------------------------------
-- 6. Comprobación
-- -----------------------------------------------

-- 6.1 Lo que se ha quitado en esta ejecución (relanzado, todo sale a 0)
SELECT 'tablas de comisiones quitadas' AS comprobacion, COUNT(*) AS total FROM tmp_mcas_quitar_conjuntos
UNION ALL
SELECT 'reglas quitadas', COUNT(*) FROM tmp_mcas_quitar_reglas
UNION ALL
SELECT 'políticas de retrocesión quitadas', COUNT(*) FROM tmp_mcas_quitar_politicas
UNION ALL
SELECT 'productos comerciales quitados', COUNT(*) FROM tmp_mcas_quitar_productos;

-- 6.2 Lo que viene de la precarga y se queda. Lo correcto es que no salga
--     ninguna fila. Si sale alguna, tiene comisiones (propuestas o servicios)
--     o la usa un contrato o una regla: decidan qué hacer con ella desde la
--     aplicación (archivar la tabla o la política, desactivar el producto) o
--     avisen al programador.
SELECT 'tabla de comisiones' AS tipo, cr.codigo, COALESCE(cr.nombre, '') AS nombre, cr.estado,
       (SELECT COUNT(*) FROM mcas_propuestas p WHERE p.id_conjunto_reglas = cr.id) AS propuestas,
       (SELECT COUNT(*) FROM mcas_propuestas p
         WHERE p.id_conjunto_reglas = cr.id AND p.id_servicio_creado IS NOT NULL) AS con_servicio,
       'Se queda: tiene propuestas' AS motivo
  FROM mcas_conjuntos_reglas cr
 WHERE cr.creado_por IN ('seed-controlado', 'seed-hoja2')
UNION ALL
SELECT 'tabla copiada de la precarga', cr.codigo, COALESCE(cr.nombre, ''), cr.estado,
       (SELECT COUNT(*) FROM mcas_propuestas p WHERE p.id_conjunto_reglas = cr.id),
       (SELECT COUNT(*) FROM mcas_propuestas p
         WHERE p.id_conjunto_reglas = cr.id AND p.id_servicio_creado IS NOT NULL),
       CONCAT('Se queda: la creó ', COALESCE(cr.creado_por, 'una persona'), ' con «Nueva versión»')
  FROM mcas_conjuntos_reglas cr
 WHERE cr.archivo_origen = 'COMISIONES NEXO A 16032026 1.xlsx'
   AND (cr.creado_por IS NULL OR cr.creado_por NOT IN ('seed-controlado', 'seed-hoja2'))
UNION ALL
SELECT 'política de retrocesión', COALESCE(pr.codigo, ''), COALESCE(pr.descripcion, ''), pr.estado,
       (SELECT COUNT(*) FROM mcas_propuestas p WHERE p.id_politica_retrocesion = pr.id),
       (SELECT COUNT(*) FROM mcas_propuestas p
         WHERE p.id_politica_retrocesion = pr.id AND p.id_servicio_creado IS NOT NULL),
       'Se queda: tiene propuestas'
  FROM mcas_politicas_retrocesion pr
 WHERE pr.archivo_origen = 'COMISIONES NEXO A 16032026 1.xlsx'
UNION ALL
SELECT 'producto comercial', pc.codigo, pc.nombre, CASE WHEN pc.activo THEN 'ACTIVO' ELSE 'INACTIVO' END,
       (SELECT COUNT(*) FROM contratos ct WHERE ct.mcas_id_producto_comercial = pc.id),
       0,
       'Se queda: lo usa un contrato o una regla'
  FROM mcas_productos_comerciales pc
  JOIN tmp_mcas_productos_precarga s ON s.id = pc.id;

-- 6.3 Interruptores (este script no los toca). Al subir una versión nueva
--     tienen que estar apagados (activo = 0) hasta que el cliente haya
--     configurado y activado sus tablas.
SELECT codigo, activo
  FROM configuracion_modulos
 WHERE codigo LIKE 'MCAS\_%'
 ORDER BY codigo;

DROP TEMPORARY TABLE IF EXISTS tmp_mcas_quitar_productos;
DROP TEMPORARY TABLE IF EXISTS tmp_mcas_quitar_politicas;
DROP TEMPORARY TABLE IF EXISTS tmp_mcas_quitar_reglas;
DROP TEMPORARY TABLE IF EXISTS tmp_mcas_quitar_conjuntos;
DROP TEMPORARY TABLE IF EXISTS tmp_mcas_productos_precarga;
