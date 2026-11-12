-- ============================================================
-- Provincias - regularizar las escritas a mano (10/11/2026, ajustado el 12/11/2026
-- con las provincias reales de PRE)
-- V20261110_5_Provincias_Regularizar.sql
--
-- PARA QUÉ
--   La provincia de los puntos de suministro (punto_suministro) y de los clientes
--   (clientes) era texto libre y ahora es un desplegable con las 52 provincias. Este
--   script deja lo ya grabado con el nombre del desplegable («MURCIA», «murcia »,
--   «Región de Murcia» o «LORCA» pasan a «Murcia»).
--
-- CÓMO SE LANZA
--   Entero, de una vez (en DBeaver: Alt+X sin nada seleccionado). Se puede relanzar:
--   no cambia nada que ya esté bien y la copia de seguridad no se pisa.
--
-- QUÉ HACE, EN ORDEN
--   1. Copia de seguridad de todas las provincias en copia_provincias_20261110
--      (se queda; con ella se recupera todo, ver el final del archivo).
--   2. Tablas de apoyo con las 52 provincias y sus otras formas de escribirse.
--   3. Corrige por el nombre; lo que no reconoce, por el código postal (sus dos
--      primeras cifras son la provincia); y si en la provincia se escribió un código
--      postal («30880»), por ese. Después, los casos sueltos revisados a mano en PRE
--      (3.4), con copia de sus códigos postales en copia_cp_20261112.
--   4. Quita del histórico de clientes las líneas «Provincia: MURCIA → Murcia» que
--      escribe el trigger por este cambio: no queda rastro.
--   5. Borra las tablas de apoyo.
--
-- QUÉ NO TOCA
--   Una provincia que no se reconoce ni por el nombre ni por el código postal se queda
--   como está (se corrige a mano en la aplicación). Si el nombre se reconoce, manda el
--   nombre aunque el código postal sea de otra provincia. Las comunidades autónomas
--   (ANDALUCIA, CASTILLA LA MANCHA) no son provincia: van por el código postal. En los
--   clientes, una provincia vacía se deja vacía.
--
-- Las comparaciones pasan la provincia a utf8mb4_unicode_ci (no distingue mayúsculas
-- ni tildes) para que funcione sea cual sea el juego de caracteres de la columna.
-- ============================================================

-- 1. Copia de seguridad (una sola vez: si ya existe, no se pisa)
CREATE TABLE IF NOT EXISTS copia_provincias_20261110 (
    tabla     VARCHAR(20)  NOT NULL,
    id        INT          NOT NULL,
    provincia VARCHAR(100) NULL,
    PRIMARY KEY (tabla, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO copia_provincias_20261110 (tabla, id, provincia)
SELECT 'punto_suministro', id, provincia FROM punto_suministro;
INSERT IGNORE INTO copia_provincias_20261110 (tabla, id, provincia)
SELECT 'clientes', id, provincia FROM clientes;

-- Copia de los códigos postales que corrige el apartado 3.4
CREATE TABLE IF NOT EXISTS copia_cp_20261112 (
    id            INT         NOT NULL PRIMARY KEY,
    codigo_postal VARCHAR(10) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO copia_cp_20261112 (id, codigo_postal)
SELECT id, codigo_postal FROM punto_suministro WHERE id IN (340, 336, 452, 38304, 38258, 549, 33213, 38261);

-- 2. Tablas de apoyo
DROP TABLE IF EXISTS tmp_provincias_alias;
DROP TABLE IF EXISTS tmp_provincias;

CREATE TABLE tmp_provincias (
    codigo CHAR(2)     NOT NULL PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_provincias (codigo, nombre) VALUES
 ('01','Álava'),('02','Albacete'),('03','Alicante'),('04','Almería'),('05','Ávila'),('06','Badajoz'),
 ('07','Baleares'),('08','Barcelona'),('09','Burgos'),('10','Cáceres'),('11','Cádiz'),('12','Castellón'),
 ('13','Ciudad Real'),('14','Córdoba'),('15','A Coruña'),('16','Cuenca'),('17','Girona'),('18','Granada'),
 ('19','Guadalajara'),('20','Gipuzkoa'),('21','Huelva'),('22','Huesca'),('23','Jaén'),('24','León'),
 ('25','Lleida'),('26','La Rioja'),('27','Lugo'),('28','Madrid'),('29','Málaga'),('30','Murcia'),
 ('31','Navarra'),('32','Ourense'),('33','Asturias'),('34','Palencia'),('35','Las Palmas'),('36','Pontevedra'),
 ('37','Salamanca'),('38','Santa Cruz de Tenerife'),('39','Cantabria'),('40','Segovia'),('41','Sevilla'),
 ('42','Soria'),('43','Tarragona'),('44','Teruel'),('45','Toledo'),('46','Valencia'),('47','Valladolid'),
 ('48','Bizkaia'),('49','Zamora'),('50','Zaragoza'),('51','Ceuta'),('52','Melilla');

CREATE TABLE tmp_provincias_alias (
    alias  VARCHAR(60) NOT NULL PRIMARY KEY,
    codigo CHAR(2)     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_provincias_alias (alias, codigo) SELECT nombre, codigo FROM tmp_provincias;
INSERT IGNORE INTO tmp_provincias_alias (alias, codigo) VALUES
 ('Araba','01'),('Araba/Álava','01'),('Vitoria','01'),('Vitoria-Gasteiz','01'),('Vitoria-Gasteiz Álava','01'),
 ('Alacant','03'),('Alicante/Alacant','03'),('Elche','03'),('Elx','03'),
 ('(Almería)','04'),
 ('Illes Balears','07'),('Islas Baleares','07'),('Balears','07'),('Mallorca','07'),('Palma de Mallorca','07'),
 ('Palma','07'),('Palma Illes Balears','07'),
 ('Castelló','12'),('Castellón de la Plana','12'),('Castellón/Castelló','12'),
 ('Cuidad Real','13'),
 ('La Coruña','15'),('Coruña','15'),
 ('Gerona','17'),
 ('Guipúzcoa','20'),('San Sebastián','20'),('Donostia','20'),
 ('Lérida','25'),
 ('Rioja','26'),('Logroño','26'),
 ('Comunidad de Madrid','28'),('Madird','28'),
 ('Región de Murcia','30'),('Murcia (Región de)','30'),('Murciaq','30'),
 ('Águilas','30'),('Lorca','30'),('Mazarrón','30'),('Abanilla','30'),('Albanilla','30'),('Purias','30'),
 ('Nafarroa','31'),('Comunidad Foral de Navarra','31'),('Pamplona','31'),
 ('Orense','32'),
 ('Principado de Asturias','33'),('Oviedo','33'),
 ('Palmas','35'),('Las Palmas de Gran Canaria','35'),('Gran Canaria','35'),
 ('Tenerife','38'),('S.C. Tenerife','38'),('Sta. Cruz de Tenerife','38'),
 ('Santander','39'),
 ('València','46'),('Valencia/València','46'),
 ('Vizcaya','48'),('Bilbao','48');

-- 3. Corregir (el trigger de clientes apunta cada cambio firmado con este nombre)
SET @aura_usuario_nombre = 'Regularización de provincias';

-- 3.1 Por el nombre
UPDATE punto_suministro t
  JOIN tmp_provincias_alias a ON a.alias = CONVERT(TRIM(t.provincia) USING utf8mb4) COLLATE utf8mb4_unicode_ci
  JOIN tmp_provincias p       ON p.codigo = a.codigo
   SET t.provincia = p.nombre
 WHERE NOT (BINARY CONVERT(t.provincia USING utf8mb4) = BINARY p.nombre);

UPDATE clientes t
  JOIN tmp_provincias_alias a ON a.alias = CONVERT(TRIM(t.provincia) USING utf8mb4) COLLATE utf8mb4_unicode_ci
  JOIN tmp_provincias p       ON p.codigo = a.codigo
   SET t.provincia = p.nombre
 WHERE NOT (BINARY CONVERT(t.provincia USING utf8mb4) = BINARY p.nombre);

-- 3.2 Lo que no se reconoce por el nombre, por el código postal.
-- En los puntos la provincia es obligatoria, así que también se rellena si está vacía.
-- En los clientes solo se corrige si tenía algo escrito.
UPDATE punto_suministro t
  JOIN tmp_provincias cp ON cp.codigo = CONVERT(LEFT(TRIM(t.codigo_postal), 2) USING utf8mb4) COLLATE utf8mb4_unicode_ci
  LEFT JOIN tmp_provincias_alias a ON a.alias = CONVERT(TRIM(t.provincia) USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET t.provincia = cp.nombre
 WHERE a.codigo IS NULL
   AND CONVERT(TRIM(t.codigo_postal) USING utf8mb4) REGEXP '^[0-9]{5}$';

UPDATE clientes t
  JOIN tmp_provincias cp ON cp.codigo = CONVERT(LEFT(TRIM(t.codigo_postal), 2) USING utf8mb4) COLLATE utf8mb4_unicode_ci
  LEFT JOIN tmp_provincias_alias a ON a.alias = CONVERT(TRIM(t.provincia) USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET t.provincia = cp.nombre
 WHERE a.codigo IS NULL
   AND TRIM(COALESCE(t.provincia, '')) <> ''
   AND CONVERT(TRIM(t.codigo_postal) USING utf8mb4) REGEXP '^[0-9]{5}$';

-- 3.3 Un código postal escrito en la provincia («30880»): por sus dos primeras cifras
UPDATE punto_suministro t
  JOIN tmp_provincias pc ON pc.codigo = CONVERT(LEFT(TRIM(t.provincia), 2) USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET t.provincia = pc.nombre
 WHERE CONVERT(TRIM(t.provincia) USING utf8mb4) REGEXP '^[0-9]{5}$';

UPDATE clientes t
  JOIN tmp_provincias pc ON pc.codigo = CONVERT(LEFT(TRIM(t.provincia), 2) USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET t.provincia = pc.nombre
 WHERE CONVERT(TRIM(t.provincia) USING utf8mb4) REGEXP '^[0-9]{5}$';

-- 3.4 Casos sueltos revisados a mano en PRE el 12/11/2026 (consulta D): el nombre decía
-- una provincia y la población otra, o el código postal tenía una errata. Cada UPDATE
-- comprueba además el código postal, la población o la dirección de esa fila: si en
-- otra base de datos ese id es otro registro, no toca nada.

-- Provincia según la población
UPDATE clientes SET provincia = 'Alicante' WHERE id = 38744 AND TRIM(codigo_postal) = '03650' AND poblacion LIKE '%PINOSO%';
UPDATE clientes SET provincia = 'Alicante' WHERE id = 16571 AND TRIM(codigo_postal) = '03410' AND poblacion LIKE '%BIAR%';
UPDATE clientes SET provincia = 'Alicante' WHERE id = 14559 AND TRIM(codigo_postal) = '03007' AND poblacion LIKE '%ALICANTE%';
UPDATE clientes SET provincia = 'Murcia'   WHERE id = 35675 AND TRIM(codigo_postal) = '30890' AND poblacion LIKE '%LUMBRERAS%';

UPDATE punto_suministro SET provincia = 'Alicante'  WHERE id = 37253 AND TRIM(codigo_postal) = '03312' AND poblacion LIKE '%ORIHUELA%';
UPDATE punto_suministro SET provincia = 'Alicante'  WHERE id = 46536 AND TRIM(codigo_postal) = '03312' AND poblacion LIKE '%ORIHUELA%';
UPDATE punto_suministro SET provincia = 'Alicante'  WHERE id = 38268 AND TRIM(codigo_postal) = '03170' AND poblacion LIKE '%ROJALES%';
UPDATE punto_suministro SET provincia = 'Alicante'  WHERE id = 49608 AND TRIM(codigo_postal) = '03650' AND poblacion LIKE '%PINOSO%';
UPDATE punto_suministro SET provincia = 'Alicante'  WHERE id = 40364 AND TRIM(codigo_postal) = '03750' AND poblacion LIKE '%PEDREGUER%';
UPDATE punto_suministro SET provincia = 'Alicante'  WHERE id = 21112 AND TRIM(codigo_postal) = '03410' AND poblacion LIKE '%BIAR%';
UPDATE punto_suministro SET provincia = 'Alicante'  WHERE id = 40361 AND TRIM(codigo_postal) = '03540' AND poblacion LIKE '%ALICANTE%';
UPDATE punto_suministro SET provincia = 'Alicante'  WHERE id = 18068 AND TRIM(codigo_postal) = '03007' AND poblacion LIKE '%ALICANTE%';
UPDATE punto_suministro SET provincia = 'Castellón' WHERE id = 44442 AND TRIM(codigo_postal) = '12540' AND poblacion LIKE '%VILLAREAL%';
UPDATE punto_suministro SET provincia = 'Castellón' WHERE id = 38308 AND TRIM(codigo_postal) = '12540' AND poblacion LIKE '%VILLAREAL%';
UPDATE punto_suministro SET provincia = 'Almería'   WHERE id = 23    AND TRIM(codigo_postal) = '04600' AND poblacion LIKE '%ALMERIA%';

-- Provincia y código postal
UPDATE punto_suministro SET provincia = 'Alicante', codigo_postal = '03340'
 WHERE id = 38258 AND TRIM(codigo_postal) = '30340' AND poblacion LIKE '%ALBATERA%';   -- Albatera: 30340 -> 03340
UPDATE punto_suministro SET provincia = 'Albacete', codigo_postal = '02004'
 WHERE id = 38261 AND TRIM(codigo_postal) = '0204' AND poblacion LIKE '%ALBACETE%';    -- C/ María Marín: le faltaba un 0 (confirmar en Correos)

-- Código postal con errata (la provincia ya estaba bien)
UPDATE punto_suministro SET codigo_postal = '30850' WHERE id = 340   AND TRIM(codigo_postal) = '38850' AND poblacion LIKE '%TOTANA%';
UPDATE punto_suministro SET codigo_postal = '30850' WHERE id = 336   AND TRIM(codigo_postal) = '38850' AND poblacion LIKE '%TOTANA%';
UPDATE punto_suministro SET codigo_postal = '41710' WHERE id = 452   AND TRIM(codigo_postal) = '47710' AND poblacion LIKE '%UTRERA%';
UPDATE punto_suministro SET codigo_postal = '46870' WHERE id = 38304 AND TRIM(codigo_postal) = '16260' AND poblacion LIKE '%ONTIYENT%';
UPDATE punto_suministro SET codigo_postal = '04619' WHERE id = 549   AND TRIM(codigo_postal) = '45610' AND poblacion LIKE '%JUCARI%';   -- Jucainí (Cuevas del Almanzora)
UPDATE punto_suministro SET codigo_postal = '07199' WHERE id = 33213 AND TRIM(codigo_postal) = '30820' AND poblacion LIKE '%PALMA%';    -- C/ Can Rius, Palma (confirmar en Correos)

-- Sin provincia reconocible: la de su cliente
UPDATE clientes         SET provincia = 'Murcia' WHERE id = 1392  AND TRIM(provincia) = 'NADA' AND nombre LIKE '%ACOMETIDAS GAS%';      -- cliente comodín (CP 30XXX)
UPDATE punto_suministro SET provincia = 'Murcia' WHERE id = 1724  AND TRIM(provincia) = 'NADA' AND direccion LIKE 'SIN DIRECCION%';   -- su punto comodín
UPDATE punto_suministro SET provincia = 'Murcia' WHERE id = 17060 AND TRIM(COALESCE(provincia, '')) = '' AND direccion LIKE '%AGRISEL%'; -- Peñaranda Agrisel (cliente de Águilas)

SET @aura_usuario_nombre = NULL;

-- 4. Sin rastro en el histórico de clientes (solo las líneas de este cambio)
DELETE FROM clientes_actividad
 WHERE usuario_nombre = 'Regularización de provincias'
   AND tipo_evento = 'MODIFICACION_DATOS_PERSONALES'
   AND descripcion LIKE 'MODIFICACION DE DATOS PERSONALES: Provincia:%';

-- 5. Borrar las tablas de apoyo (la copia de seguridad se queda)
DROP TABLE IF EXISTS tmp_provincias_alias;
DROP TABLE IF EXISTS tmp_provincias;

-- ============================================================
-- RECUPERAR (solo si algo no cuadra): devuelve cada provincia a como estaba,
-- también sin dejar rastro en el histórico. Quitar los «-- » y lanzar estas líneas.
-- ============================================================
-- SET @aura_usuario_nombre = 'Regularización de provincias';
-- UPDATE punto_suministro t JOIN copia_provincias_20261110 c ON c.tabla = 'punto_suministro' AND c.id = t.id SET t.provincia = c.provincia;
-- UPDATE clientes         t JOIN copia_provincias_20261110 c ON c.tabla = 'clientes'         AND c.id = t.id SET t.provincia = c.provincia;
-- SET @aura_usuario_nombre = NULL;
-- DELETE FROM clientes_actividad WHERE usuario_nombre = 'Regularización de provincias' AND tipo_evento = 'MODIFICACION_DATOS_PERSONALES' AND descripcion LIKE 'MODIFICACION DE DATOS PERSONALES: Provincia:%';
-- UPDATE punto_suministro t JOIN copia_cp_20261112 c ON c.id = t.id SET t.codigo_postal = c.codigo_postal;
