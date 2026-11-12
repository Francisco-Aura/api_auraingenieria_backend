-- ============================================================
-- Provincias - regularizar las escritas a mano (10/11/2026)
-- V20261110_5_Provincias_Regularizar.sql
--
-- PARA QUÉ
--   La provincia de los puntos de suministro y de los clientes era un campo de
--   texto libre y ahora es un desplegable con las 52 provincias. Este script
--   deja lo ya grabado con el nombre del desplegable («MURCIA», «murcia » o
--   «Región de Murcia» pasan a «Murcia»), para que los informes agrupen bien.
--
--   NO es obligatorio para desplegar: la aplicación reconoce igualmente esas
--   formas al calcular la zona de comisión, y al abrir un punto o un cliente
--   propone la provincia de la lista. Se puede lanzar cuando convenga.
--
-- CÓMO EJECUTARLO (por pasos; cada paso se puede lanzar suelto)
--   Paso 1  Crea dos tablas de apoyo (se borran en el paso 6).
--   Paso 2  SOLO CONSULTA: cuántas filas se arreglarían y de qué manera.
--           Mírelo antes de seguir.
--   Paso 3  Copia de seguridad de las provincias actuales.
--   Paso 4  Corrige por el nombre y, lo que no se reconozca, por el código
--           postal (sus dos primeras cifras son la provincia).
--   Paso 5  SOLO CONSULTA: lo que queda sin reconocer, para corregirlo a mano
--           desde la aplicación (abrir el punto o el cliente y elegir la
--           provincia de la lista).
--   Paso 6  Borra las tablas de apoyo. La copia de seguridad se queda.
--
--   Es idempotente: relanzarlo no cambia nada que ya esté bien.
--   Vuelta atrás: el UPDATE del final del archivo (comentado) restaura la copia.
--
-- QUÉ NO TOCA
--   Una provincia que no se reconoce por el nombre y cuyo código postal no
--   sirve (vacío, de menos de 5 cifras o de una provincia que no existe) se
--   queda como está. Tampoco se toca una que ya coincide con la lista.
--   Cuando el nombre se reconoce, manda el nombre aunque el código postal sea
--   de otra provincia: esos casos salen en la consulta del paso 5 para revisarlos.
--
-- Nomenclatura: tablas en minúsculas y snake_case, como las crea Hibernate.
-- ============================================================

-- -----------------------------------------------
-- Paso 1. Tablas de apoyo: las 52 provincias y sus otras formas de escribirse
-- -----------------------------------------------
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

-- La comparación utf8mb4_unicode_ci no distingue mayúsculas ni tildes: «ALMERIA» = «Almería».
-- Aquí van el nombre de la lista y las demás formas habituales.
CREATE TABLE tmp_provincias_alias (
    alias  VARCHAR(60) NOT NULL PRIMARY KEY,
    codigo CHAR(2)     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_provincias_alias (alias, codigo) SELECT nombre, codigo FROM tmp_provincias;
INSERT IGNORE INTO tmp_provincias_alias (alias, codigo) VALUES
 ('Araba','01'),('Araba/Álava','01'),('Vitoria','01'),('Vitoria-Gasteiz','01'),
 ('Alacant','03'),('Alicante/Alacant','03'),
 ('Illes Balears','07'),('Islas Baleares','07'),('Balears','07'),('Mallorca','07'),('Palma de Mallorca','07'),('Palma','07'),
 ('Castelló','12'),('Castellón de la Plana','12'),('Castellón/Castelló','12'),
 ('La Coruña','15'),('Coruña','15'),
 ('Gerona','17'),
 ('Guipúzcoa','20'),('San Sebastián','20'),('Donostia','20'),
 ('Lérida','25'),
 ('Rioja','26'),('Logroño','26'),
 ('Comunidad de Madrid','28'),
 ('Región de Murcia','30'),('Murcia (Región de)','30'),
 ('Nafarroa','31'),('Comunidad Foral de Navarra','31'),('Pamplona','31'),
 ('Orense','32'),
 ('Principado de Asturias','33'),('Oviedo','33'),
 ('Palmas','35'),('Las Palmas de Gran Canaria','35'),('Gran Canaria','35'),
 ('Tenerife','38'),('S.C. Tenerife','38'),('Sta. Cruz de Tenerife','38'),
 ('Santander','39'),
 ('València','46'),('Valencia/València','46'),
 ('Vizcaya','48'),('Bilbao','48');

-- -----------------------------------------------
-- Paso 2. SOLO CONSULTA: qué pasaría
-- -----------------------------------------------
-- ya_bien: coincide letra a letra con la lista. por_nombre: se reconoce el nombre.
-- por_codigo_postal: no se reconoce el nombre, pero sí la provincia del código postal.
-- sin_reconocer: se queda como está.
SELECT 'punto_suministro' AS tabla,
       SUM(a.codigo IS NOT NULL AND BINARY TRIM(t.provincia) = BINARY p.nombre)                 AS ya_bien,
       SUM(a.codigo IS NOT NULL AND NOT (BINARY TRIM(t.provincia) = BINARY p.nombre))           AS por_nombre,
       SUM(a.codigo IS NULL AND cp.codigo IS NOT NULL)                                          AS por_codigo_postal,
       SUM(a.codigo IS NULL AND cp.codigo IS NULL)                                              AS sin_reconocer
  FROM punto_suministro t
  LEFT JOIN tmp_provincias_alias a ON a.alias = TRIM(t.provincia)
  LEFT JOIN tmp_provincias p       ON p.codigo = a.codigo
  LEFT JOIN tmp_provincias cp      ON TRIM(t.codigo_postal) REGEXP '^[0-9]{5}$' AND cp.codigo = LEFT(TRIM(t.codigo_postal), 2)
UNION ALL
SELECT 'clientes',
       SUM(a.codigo IS NOT NULL AND BINARY TRIM(t.provincia) = BINARY p.nombre),
       SUM(a.codigo IS NOT NULL AND NOT (BINARY TRIM(t.provincia) = BINARY p.nombre)),
       SUM(a.codigo IS NULL AND cp.codigo IS NOT NULL AND TRIM(COALESCE(t.provincia, '')) <> ''),
       SUM(a.codigo IS NULL AND (cp.codigo IS NULL OR TRIM(COALESCE(t.provincia, '')) = ''))
  FROM clientes t
  LEFT JOIN tmp_provincias_alias a ON a.alias = TRIM(t.provincia)
  LEFT JOIN tmp_provincias p       ON p.codigo = a.codigo
  LEFT JOIN tmp_provincias cp      ON TRIM(t.codigo_postal) REGEXP '^[0-9]{5}$' AND cp.codigo = LEFT(TRIM(t.codigo_postal), 2);

-- -----------------------------------------------
-- Paso 3. Copia de seguridad (una sola vez: si ya existe, no se pisa)
-- -----------------------------------------------
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

-- -----------------------------------------------
-- Paso 4. Corregir
-- -----------------------------------------------
-- 4.1 Por el nombre
UPDATE punto_suministro t
  JOIN tmp_provincias_alias a ON a.alias = TRIM(t.provincia)
  JOIN tmp_provincias p       ON p.codigo = a.codigo
   SET t.provincia = p.nombre
 WHERE NOT (BINARY t.provincia = BINARY p.nombre);

UPDATE clientes t
  JOIN tmp_provincias_alias a ON a.alias = TRIM(t.provincia)
  JOIN tmp_provincias p       ON p.codigo = a.codigo
   SET t.provincia = p.nombre
 WHERE NOT (BINARY t.provincia = BINARY p.nombre);

-- 4.2 Lo que no se reconoce por el nombre, por el código postal.
-- En los puntos la provincia es obligatoria, así que también se rellena si está vacía.
-- En los clientes solo se corrige si tenía algo escrito: una provincia vacía se deja vacía.
UPDATE punto_suministro t
  JOIN tmp_provincias cp ON cp.codigo = LEFT(TRIM(t.codigo_postal), 2)
  LEFT JOIN tmp_provincias_alias a ON a.alias = TRIM(t.provincia)
   SET t.provincia = cp.nombre
 WHERE a.codigo IS NULL
   AND TRIM(t.codigo_postal) REGEXP '^[0-9]{5}$';

UPDATE clientes t
  JOIN tmp_provincias cp ON cp.codigo = LEFT(TRIM(t.codigo_postal), 2)
  LEFT JOIN tmp_provincias_alias a ON a.alias = TRIM(t.provincia)
   SET t.provincia = cp.nombre
 WHERE a.codigo IS NULL
   AND TRIM(COALESCE(t.provincia, '')) <> ''
   AND TRIM(t.codigo_postal) REGEXP '^[0-9]{5}$';

-- -----------------------------------------------
-- Paso 5. SOLO CONSULTA: lo que queda para revisar a mano
-- -----------------------------------------------
-- motivo «sin reconocer»: ni el nombre ni el código postal sirven.
-- motivo «el código postal es de otra provincia»: se ha respetado el nombre.
SELECT 'punto_suministro' AS tabla, t.id, t.provincia, t.codigo_postal, t.poblacion,
       IF(p.codigo IS NULL, 'sin reconocer', 'el código postal es de otra provincia') AS motivo
  FROM punto_suministro t
  LEFT JOIN tmp_provincias p ON p.nombre = t.provincia
 WHERE p.codigo IS NULL
    OR (TRIM(t.codigo_postal) REGEXP '^[0-9]{5}$' AND LEFT(TRIM(t.codigo_postal), 2) <> p.codigo)
UNION ALL
SELECT 'clientes', t.id, t.provincia, t.codigo_postal, t.poblacion,
       IF(p.codigo IS NULL, 'sin reconocer', 'el código postal es de otra provincia')
  FROM clientes t
  LEFT JOIN tmp_provincias p ON p.nombre = t.provincia
 WHERE TRIM(COALESCE(t.provincia, '')) <> ''
   AND (p.codigo IS NULL
        OR (TRIM(t.codigo_postal) REGEXP '^[0-9]{5}$' AND LEFT(TRIM(t.codigo_postal), 2) <> p.codigo))
 ORDER BY 1, 6, 3;

-- -----------------------------------------------
-- Paso 6. Borrar las tablas de apoyo (la copia de seguridad se queda)
-- -----------------------------------------------
DROP TABLE IF EXISTS tmp_provincias_alias;
DROP TABLE IF EXISTS tmp_provincias;

-- -----------------------------------------------
-- VUELTA ATRÁS (solo si hiciera falta): devuelve cada provincia a como estaba
-- -----------------------------------------------
-- UPDATE punto_suministro t JOIN copia_provincias_20261110 c ON c.tabla = 'punto_suministro' AND c.id = t.id SET t.provincia = c.provincia;
-- UPDATE clientes         t JOIN copia_provincias_20261110 c ON c.tabla = 'clientes'         AND c.id = t.id SET t.provincia = c.provincia;
