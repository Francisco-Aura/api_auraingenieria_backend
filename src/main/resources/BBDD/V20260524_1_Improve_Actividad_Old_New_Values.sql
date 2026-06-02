-- ============================================================
-- Mejora del trigger clientes_au: muestra valor antiguo → nuevo
-- Amplía descripcion a VARCHAR(2000) para soportar más detalle
-- ============================================================

ALTER TABLE clientes_actividad
    MODIFY COLUMN descripcion VARCHAR(2000) NOT NULL;

DELIMITER $$

DROP TRIGGER IF EXISTS trg_clientes_actividad_clientes_au $$
CREATE TRIGGER trg_clientes_actividad_clientes_au
AFTER UPDATE ON clientes
FOR EACH ROW
BEGIN
    DECLARE v_campos TEXT;

    SET v_campos = CONCAT_WS(' | ',
        IF(NOT (OLD.nombre <=> NEW.nombre),
           CONCAT('Nombre: "', COALESCE(LEFT(NULLIF(TRIM(OLD.nombre),''),40),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.nombre),''),40),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.cif <=> NEW.cif),
           CONCAT('CIF: "', COALESCE(NULLIF(TRIM(OLD.cif),''),'[vacío]'),
                  '" → "', COALESCE(NULLIF(TRIM(NEW.cif),''),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.direccion <=> NEW.direccion),
           CONCAT('Dirección: "', COALESCE(LEFT(NULLIF(TRIM(OLD.direccion),''),40),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.direccion),''),40),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.codigo_postal <=> NEW.codigo_postal),
           CONCAT('C.P.: "', COALESCE(NULLIF(TRIM(OLD.codigo_postal),''),'[vacío]'),
                  '" → "', COALESCE(NULLIF(TRIM(NEW.codigo_postal),''),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.poblacion <=> NEW.poblacion),
           CONCAT('Población: "', COALESCE(LEFT(NULLIF(TRIM(OLD.poblacion),''),35),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.poblacion),''),35),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.provincia <=> NEW.provincia),
           CONCAT('Provincia: "', COALESCE(LEFT(NULLIF(TRIM(OLD.provincia),''),30),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.provincia),''),30),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.nombre_comercial <=> NEW.nombre_comercial),
           CONCAT('Grupo empresarial: "', COALESCE(LEFT(NULLIF(TRIM(OLD.nombre_comercial),''),40),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.nombre_comercial),''),40),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.iban <=> NEW.iban),
           CONCAT('IBAN: "', COALESCE(NULLIF(TRIM(OLD.iban),''),'[vacío]'),
                  '" → "', COALESCE(NULLIF(TRIM(NEW.iban),''),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.actividad <=> NEW.actividad),
           CONCAT('Actividad: "', COALESCE(LEFT(NULLIF(TRIM(OLD.actividad),''),40),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.actividad),''),40),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.codigo_sector <=> NEW.codigo_sector),
           CONCAT('Sector: "', COALESCE(NULLIF(TRIM(OLD.codigo_sector),''),'[vacío]'),
                  '" → "', COALESCE(NULLIF(TRIM(NEW.codigo_sector),''),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.codigo_subtipologia <=> NEW.codigo_subtipologia),
           CONCAT('Subtipología: "', COALESCE(NULLIF(TRIM(OLD.codigo_subtipologia),''),'[vacío]'),
                  '" → "', COALESCE(NULLIF(TRIM(NEW.codigo_subtipologia),''),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.nombre_autorizado <=> NEW.nombre_autorizado),
           CONCAT('Nombre autorizado: "', COALESCE(LEFT(NULLIF(TRIM(OLD.nombre_autorizado),''),35),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.nombre_autorizado),''),35),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.telefono_autorizado <=> NEW.telefono_autorizado),
           CONCAT('Teléfono autorizado: "', COALESCE(NULLIF(TRIM(OLD.telefono_autorizado),''),'[vacío]'),
                  '" → "', COALESCE(NULLIF(TRIM(NEW.telefono_autorizado),''),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.email_autorizado <=> NEW.email_autorizado),
           CONCAT('Email autorizado: "', COALESCE(LEFT(NULLIF(TRIM(OLD.email_autorizado),''),40),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.email_autorizado),''),40),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.dni_autorizado <=> NEW.dni_autorizado),
           CONCAT('DNI autorizado: "', COALESCE(NULLIF(TRIM(OLD.dni_autorizado),''),'[vacío]'),
                  '" → "', COALESCE(NULLIF(TRIM(NEW.dni_autorizado),''),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.nombre_contacto1 <=> NEW.nombre_contacto1),
           CONCAT('Nombre contacto 1: "', COALESCE(LEFT(NULLIF(TRIM(OLD.nombre_contacto1),''),35),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.nombre_contacto1),''),35),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.cargo_contacto1 <=> NEW.cargo_contacto1),
           CONCAT('Cargo contacto 1: "', COALESCE(LEFT(NULLIF(TRIM(OLD.cargo_contacto1),''),30),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.cargo_contacto1),''),30),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.telefono_contacto1 <=> NEW.telefono_contacto1),
           CONCAT('Teléfono contacto 1: "', COALESCE(NULLIF(TRIM(OLD.telefono_contacto1),''),'[vacío]'),
                  '" → "', COALESCE(NULLIF(TRIM(NEW.telefono_contacto1),''),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.email_contacto1 <=> NEW.email_contacto1),
           CONCAT('Email contacto 1: "', COALESCE(LEFT(NULLIF(TRIM(OLD.email_contacto1),''),40),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.email_contacto1),''),40),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.nombre_contacto2 <=> NEW.nombre_contacto2),
           CONCAT('Nombre contacto 2: "', COALESCE(LEFT(NULLIF(TRIM(OLD.nombre_contacto2),''),35),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.nombre_contacto2),''),35),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.cargo_contacto2 <=> NEW.cargo_contacto2),
           CONCAT('Cargo contacto 2: "', COALESCE(LEFT(NULLIF(TRIM(OLD.cargo_contacto2),''),30),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.cargo_contacto2),''),30),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.telefono_contacto2 <=> NEW.telefono_contacto2),
           CONCAT('Teléfono contacto 2: "', COALESCE(NULLIF(TRIM(OLD.telefono_contacto2),''),'[vacío]'),
                  '" → "', COALESCE(NULLIF(TRIM(NEW.telefono_contacto2),''),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.email_contacto2 <=> NEW.email_contacto2),
           CONCAT('Email contacto 2: "', COALESCE(LEFT(NULLIF(TRIM(OLD.email_contacto2),''),40),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.email_contacto2),''),40),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.nombre_contacto3 <=> NEW.nombre_contacto3),
           CONCAT('Nombre contacto 3: "', COALESCE(LEFT(NULLIF(TRIM(OLD.nombre_contacto3),''),35),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.nombre_contacto3),''),35),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.cargo_contacto3 <=> NEW.cargo_contacto3),
           CONCAT('Cargo contacto 3: "', COALESCE(LEFT(NULLIF(TRIM(OLD.cargo_contacto3),''),30),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.cargo_contacto3),''),30),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.telefono_contacto3 <=> NEW.telefono_contacto3),
           CONCAT('Teléfono contacto 3: "', COALESCE(NULLIF(TRIM(OLD.telefono_contacto3),''),'[vacío]'),
                  '" → "', COALESCE(NULLIF(TRIM(NEW.telefono_contacto3),''),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.email_contacto3 <=> NEW.email_contacto3),
           CONCAT('Email contacto 3: "', COALESCE(LEFT(NULLIF(TRIM(OLD.email_contacto3),''),40),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.email_contacto3),''),40),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.user_telemedida <=> NEW.user_telemedida),
           CONCAT('Usuario telemedida: "', COALESCE(LEFT(NULLIF(TRIM(OLD.user_telemedida),''),30),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.user_telemedida),''),30),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.pass_telemedida <=> NEW.pass_telemedida),
           CONCAT('Password telemedida: "', COALESCE(LEFT(NULLIF(TRIM(OLD.pass_telemedida),''),40),'[vacío]'),
                  '" → "', COALESCE(LEFT(NULLIF(TRIM(NEW.pass_telemedida),''),40),'[vacío]'), '"'), NULL),
        IF(NOT (OLD.id_usuario <=> NEW.id_usuario),
           CONCAT('Usuario asignado: "',
                  COALESCE((SELECT nombre FROM usuarios WHERE id = OLD.id_usuario LIMIT 1), '[sin asignar]'),
                  '" → "',
                  COALESCE((SELECT nombre FROM usuarios WHERE id = NEW.id_usuario LIMIT 1), '[sin asignar]'),
                  '"'), NULL)
    );

    IF v_campos IS NOT NULL AND CHAR_LENGTH(TRIM(v_campos)) > 0 THEN
        INSERT INTO clientes_actividad (
            cliente_id, punto_suministro_id, contrato_id, servicio_id,
            tipo_evento, descripcion, origen, cups, solo_nivel_uno, fecha_evento
        ) VALUES (
            NEW.id, NULL, NULL, NULL,
            'MODIFICACION_DATOS_PERSONALES',
            CONCAT('MODIFICACION DE DATOS PERSONALES: ', LEFT(v_campos, 1960)),
            'CLIENTE', NULL, b'0', NOW()
        );
    END IF;
END $$

DELIMITER ;
