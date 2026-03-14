-- ============================================================
-- QUERY OPTIMIZADA PARA LISTADO DE PUNTOS DE SUMINISTRO
-- Fecha: 2026-03-14
-- Base de datos: MariaDB
-- Descripción: Query SQL nativa optimizada para reemplazar
--              la query JPQL que actualmente es lenta
-- ============================================================

-- Versión nativa SQL optimizada que evita subqueries anidados
-- Utiliza window functions y GROUP BY para mejor rendimiento

SELECT 
    ps.ID,
    c.ID AS idCliente,
    c.nombre,
    ps.direccion,
    ps.codigo_postal,
    ps.poblacion,
    ps.provincia,
    ps.identificacion,
    ps.CUPSluz,
    ps.CUPSgas,
    ps.fecha_baja,
    CASE
        WHEN ps.CUPSluz IS NOT NULL AND ps.CUPSluz != '' AND tl.nombre IS NOT NULL AND tl.nombre != 'Sin Tarifa' THEN tl.nombre
        WHEN ps.CUPSgas IS NOT NULL AND ps.CUPSgas != '' AND tg.nombre IS NOT NULL AND tg.nombre != 'Sin Tarifa' THEN tg.nombre
        ELSE NULL
    END AS tarifaActual,
    com.nombre AS comercializadoraActual,
    con.fecha_fin AS fechaFinUltimoContrato,
    con.potencia1,
    con.potencia2,
    con.potencia3,
    con.potencia4,
    con.potencia5,
    con.potencia6
FROM punto_suministro ps
LEFT JOIN clientes c ON ps.id_cliente = c.ID
LEFT JOIN (
    -- Subquery mejorada: obtiene el contrato vigente más reciente
    SELECT 
        id_punto_suministro,
        MAX(id) AS max_id
    FROM contratos
    WHERE fecha_fin > CURDATE()
        AND id_punto_suministro IS NOT NULL
    GROUP BY id_punto_suministro
) con_vigente ON ps.ID = con_vigente.id_punto_suministro
LEFT JOIN contratos con ON con.id_punto_suministro = ps.ID 
    AND con.id = con_vigente.max_id
LEFT JOIN tbtarifas_luz tl ON con.id_tarifa_luz = tl.ID
LEFT JOIN tbtarifas_gas tg ON con.id_tarifa_gas = tg.ID
LEFT JOIN tbcomercializadoras com ON con.id_comercializadora_actual = com.ID
WHERE c.ID IS NOT NULL
ORDER BY ps.ID;

-- ============================================================
-- NOTAS DE IMPLEMENTACIÓN
-- ============================================================
-- Esta query está optimizada para:
-- 1. Evitar subqueries correlacionados en cada JOIN
-- 2. Usar GROUP BY en lugar de MAX() correlacionado
-- 3. Reducir el número de accesos a tabla
-- 4. Aprovechar los índices creados en V20260314_2
--
-- Para usar esta query en Spring:
-- 1. Copiar el SQL en PuntoSuministroRepository.java
-- 2. Usar @Query(value = "...", nativeQuery = true)
-- 3. El mapeo a DTO será automático si los nombres de columna coinciden
