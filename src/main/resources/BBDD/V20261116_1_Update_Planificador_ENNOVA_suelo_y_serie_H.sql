-- Inventario del Planificador, Ennova Metal 2026: los bloques Ennovabloc serie R pasan de cubierta «Plana» a «Suelo» (criterio de Ingeniería: son
-- estructuras de suelo) y se da de alta el último producto de la serie H (ENBH350 para módulos de 1300 mm). MariaDB 10.11.
-- Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts); también se puede aplicar a mano:
--   mysql -u root -p aura < V20261116_1_Update_Planificador_ENNOVA_suelo_y_serie_H.sql
-- Se puede repetir sin riesgo: el UPDATE deja el mismo resultado y el INSERT IGNORE no repite el producto (la clave es el id).
-- Pendiente: el resto de la serie H (ENBH050 y ENBH350 de 1150 mm) no se da de alta todavía; el proveedor queda vacío.

SET NAMES utf8mb4;

UPDATE pl_equipos
SET atributos = JSON_SET(atributos, '$.cubierta', 'Suelo', '$.montaje', 'Bloques (suelo)'),
    notas = REPLACE(notas, 'para cubierta plana', 'para suelo')
WHERE id IN ('est-ennova-enb005r', 'est-ennova-enb010r', 'est-ennova-enb015r', 'est-ennova-enb020r');

SET @pos := (SELECT COALESCE(MAX(posicion), -1) FROM pl_equipos);

INSERT IGNORE INTO pl_equipos
    (id, posicion, categoria, partida, marca, modelo, proveedor, unidad, precio_unitario, por_modulo, cantidad_defecto, atributos, notas,
     muestra, stock, stock_minimo, ficha_tecnica, ofertas, precio_revisado_el)
VALUES
    ('est-ennova-enbh350-1300', @pos + 1, 'estructura', 'modulos', 'ENNOVA', 'ENBH350 – Ennovabloc H200/H350 1300', '', 'ud', 53.89, 0, 1,
     '{"x:Referencia": "ENBH350", "x:Tarifa": "Ennova Metal 2026", "cubierta": "Suelo", "montaje": "Bloques (suelo)", "componente": "Lastre / anclaje", "x:Serie": "Ennovabloc serie H", "x:Peso (kg)": "200/350", "x:Medidas LxAxH (mm)": "1200 x 360 x 400"}',
     'Precio de la tarifa Ennova Metal 2026 (variante de 1300 mm). La referencia ENBH350 se repite en la hoja para las variantes de 1150 y de 1300 mm; pesa 200 o 350 kg según el modelo.', 0, 0, 0, '', '[]', NULL);

-- Comprobación
SELECT COUNT(*) AS ennova_en_suelo FROM pl_equipos WHERE id LIKE 'est-ennova-%' AND JSON_VALUE(atributos, '$.cubierta') = 'Suelo';
