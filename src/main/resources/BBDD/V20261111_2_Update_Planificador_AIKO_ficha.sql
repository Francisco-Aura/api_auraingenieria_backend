-- Ficha técnica de los módulos AIKO Stellar 3N+72 (655 a 685 W). MariaDB 10.11. Se aplica a mano, DESPUÉS de V20261111_1:
--   mysql -u root -p aura < V20261111_2_Update_Planificador_AIKO_ficha.sql
-- El PDF ya está en Dropbox: /AURA INGENIERIA/FICHAS TECNICAS/AIKO/AIKO_TDS_655-685W_MDE72Dw_ES.pdf
-- La ruta es relativa a la carpeta de fichas (DROPBOX_DATASHEETS_ROOT = /AURA INGENIERIA/FICHAS TECNICAS en el servidor).

SET NAMES utf8mb4;

UPDATE pl_equipos
SET ficha_tecnica = 'AIKO/AIKO_TDS_655-685W_MDE72Dw_ES.pdf'
WHERE id IN ('mod-aiko-655', 'mod-aiko-660', 'mod-aiko-665', 'mod-aiko-670', 'mod-aiko-675', 'mod-aiko-680', 'mod-aiko-685')
  AND ficha_tecnica = '';  -- solo rellena las que no tienen ficha: si alguien ya la cambió a mano, no se pisa (se puede repetir sin riesgo)

-- Comprobación
SELECT id, modelo, ficha_tecnica FROM pl_equipos WHERE id LIKE 'mod-aiko-6%' ORDER BY posicion;
