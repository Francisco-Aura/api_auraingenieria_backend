-- Inventario del Planificador: las partidas de proyecto y trámites que el presupuestador marca por defecto al crear un presupuesto
-- (proyecto técnico o memoria técnica según la potencia, dirección de obra, plan de seguridad, legalización, registro de autoconsumo…).
-- Las reglas están en el presupuestador (tramitesPorDefecto.js) y enlazan con cada partida por su «Trámite»: no cambies ese valor.
-- MariaDB 10.11. Lo aplica solo el backend al arrancar (ScriptsBBDD) o a mano:  mysql -u root -p aura < V20261118_4_Insert_Planificador_Proyecto_tramites.sql
-- Se puede ejecutar más de una vez: INSERT IGNORE no repite los que ya existen (la clave es el id) ni toca los datos de los existentes.

SET NAMES utf8mb4;

SET @pos := (SELECT COALESCE(MAX(posicion), -1) FROM pl_equipos);

INSERT IGNORE INTO pl_equipos
    (id, posicion, categoria, partida, marca, modelo, proveedor, unidad, precio_unitario, por_modulo, cantidad_defecto,
     atributos, notas, muestra, stock, stock_minimo, ficha_tecnica, ofertas, precio_revisado_el)
VALUES
    ('pro-proyecto-tecnico', @pos + 1, 'proyecto', 'proyecto', '', 'Proyecto técnico', '', 'ud', 1500, 0, 1,
     '{"tramite": "Proyecto técnico"}', '', 0, 0, 0, '', '[]', NULL),
    ('pro-memoria-tecnica', @pos + 2, 'proyecto', 'proyecto', '', 'Memoria técnica', '', 'ud', 600, 0, 1,
     '{"tramite": "Memoria técnica"}', '', 0, 0, 0, '', '[]', NULL),
    ('pro-direccion-obra', @pos + 3, 'proyecto', 'proyecto', '', 'Dirección de obra', '', 'ud', 600, 0, 1,
     '{"tramite": "Dirección de obra"}', '', 0, 0, 0, '', '[]', NULL),
    ('pro-tramitacion', @pos + 4, 'proyecto', 'proyecto', '', 'Costes de tramitación', '', 'ud', 300, 0, 1,
     '{"tramite": "Tramitación"}', '', 0, 0, 0, '', '[]', NULL),
    ('pro-seguridad', @pos + 5, 'proyecto', 'proyecto', '', 'Plan de seguridad', '', 'ud', 500, 0, 1,
     '{"tramite": "Seguridad y salud"}', '', 0, 0, 0, '', '[]', NULL),
    ('pro-legalizacion', @pos + 6, 'proyecto', 'proyecto', '', 'Legalización y boletín (instalador autorizado)', '', 'ud', 250, 0, 1,
     '{"tramite": "Legalización (boletín)"}', '', 0, 0, 0, '', '[]', NULL),
    ('pro-registro-autoconsumo', @pos + 7, 'proyecto', 'proyecto', '', 'Registro de autoconsumo (comunidad autónoma)', '', 'ud', 120, 0, 1,
     '{"tramite": "Registro de autoconsumo"}', '', 0, 0, 0, '', '[]', NULL),
    ('pro-distribuidora', @pos + 8, 'proyecto', 'proyecto', '', 'Gestión con distribuidora y comercializadora (excedentes)', '', 'ud', 150, 0, 1,
     '{"tramite": "Excedentes"}', '', 0, 0, 0, '', '[]', NULL),
    ('pro-licencia', @pos + 9, 'proyecto', 'proyecto', '', 'Licencia o comunicación previa (ayuntamiento) y tasas', '', 'ud', 80, 0, 1,
     '{"tramite": "Licencia municipal"}', '', 0, 0, 0, '', '[]', NULL);
