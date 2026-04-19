-- V20260418_1 – Baja en cascada para clientes
--               que ya estaban dados de baja antes de la
--               implementación de la baja en cascada automática.
--
-- Propaga la fecha_baja del cliente a todos sus:
--   · puntos_suministro activos
--   · servicios activos
--   · contratos activos


-- Puntos de suministro
UPDATE punto_suministro ps
INNER JOIN clientes c ON ps.id_cliente = c.id
SET ps.fecha_baja = c.fecha_baja
WHERE c.fecha_baja IS NOT NULL
  AND ps.fecha_baja IS NULL;

-- Servicios
UPDATE servicios s
INNER JOIN clientes c ON s.id_cliente = c.id
SET s.fecha_baja = c.fecha_baja
WHERE c.fecha_baja IS NOT NULL
  AND s.fecha_baja IS NULL;

-- Contratos
UPDATE contratos co
INNER JOIN clientes c ON co.id_cliente = c.id
SET co.fecha_baja = c.fecha_baja
WHERE c.fecha_baja IS NOT NULL
  AND co.fecha_baja IS NULL;
