-- ============================================================
-- ADD: usuario_nombre en clientes_actividad + triggers actualizados
--      + nuevo trigger CAMBIO_POTENCIA en contratos
-- Fecha: 2026-06-04
-- ============================================================

-- 1) Añadir columna usuario_nombre
ALTER TABLE clientes_actividad
    ADD COLUMN IF NOT EXISTS usuario_nombre VARCHAR(100) NULL AFTER fecha_evento;

DELIMITER $$

-- ============================================================
-- CONTRATOS -> SW ACTIVO (recreado con usuario_nombre)
-- ============================================================
DROP TRIGGER IF EXISTS trg_clientes_actividad_contratos_ai $$
CREATE TRIGGER trg_clientes_actividad_contratos_ai
AFTER INSERT ON contratos
FOR EACH ROW
BEGIN
    DECLARE v_cups VARCHAR(100);

    IF NEW.id_cliente IS NOT NULL THEN
        SELECT COALESCE(NULLIF(TRIM(ps.cupsluz), ''), NULLIF(TRIM(ps.cupsgas), ''), 'GENERAL')
          INTO v_cups
          FROM punto_suministro ps
         WHERE ps.id = NEW.id_punto_suministro
         LIMIT 1;

        INSERT INTO clientes_actividad (
            cliente_id, punto_suministro_id, contrato_id, servicio_id,
            tipo_evento, descripcion, origen, cups, solo_nivel_uno, fecha_evento, usuario_nombre
        ) VALUES (
            NEW.id_cliente, NEW.id_punto_suministro, NEW.id, NULL,
            'SW_ACTIVO',
            CONCAT('SW ACTIVO - Contrato ', COALESCE(NULLIF(TRIM(NEW.contrato), ''), CONCAT('#', NEW.id))),
            'CONTRATO',
            COALESCE(v_cups, 'GENERAL'),
            b'0',
            NOW(),
            COALESCE(@aura_usuario_nombre, 'Sistema')
        );
    END IF;
END $$

-- ============================================================
-- CONTRATOS -> SW BORRADO (recreado con usuario_nombre)
-- ============================================================
DROP TRIGGER IF EXISTS trg_clientes_actividad_contratos_bd $$
CREATE TRIGGER trg_clientes_actividad_contratos_bd
BEFORE DELETE ON contratos
FOR EACH ROW
BEGIN
    DECLARE v_cups VARCHAR(100);

    IF OLD.id_cliente IS NOT NULL THEN
        SELECT COALESCE(NULLIF(TRIM(ps.cupsluz), ''), NULLIF(TRIM(ps.cupsgas), ''), 'GENERAL')
          INTO v_cups
          FROM punto_suministro ps
         WHERE ps.id = OLD.id_punto_suministro
         LIMIT 1;

        INSERT INTO clientes_actividad (
            cliente_id, punto_suministro_id, contrato_id, servicio_id,
            tipo_evento, descripcion, origen, cups, solo_nivel_uno, fecha_evento, usuario_nombre
        ) VALUES (
            OLD.id_cliente, OLD.id_punto_suministro, OLD.id, NULL,
            'SW_BORRADO',
            CONCAT('SW BORRADO - Contrato ', COALESCE(NULLIF(TRIM(OLD.contrato), ''), CONCAT('#', OLD.id))),
            'CONTRATO',
            COALESCE(v_cups, 'GENERAL'),
            b'0',
            NOW(),
            COALESCE(@aura_usuario_nombre, 'Sistema')
        );
    END IF;
END $$

-- ============================================================
-- CONTRATOS -> DENUNCIA (recreado con usuario_nombre)
-- ============================================================
DROP TRIGGER IF EXISTS trg_clientes_actividad_contratos_au_denuncia $$
CREATE TRIGGER trg_clientes_actividad_contratos_au_denuncia
AFTER UPDATE ON contratos
FOR EACH ROW
BEGIN
    DECLARE v_cups VARCHAR(100);

    IF IFNULL(OLD.denuncia_contrato, 0) <> 1 AND IFNULL(NEW.denuncia_contrato, 0) = 1 AND NEW.id_cliente IS NOT NULL THEN
        SELECT COALESCE(NULLIF(TRIM(ps.cupsluz), ''), NULLIF(TRIM(ps.cupsgas), ''), 'GENERAL')
          INTO v_cups
          FROM punto_suministro ps
         WHERE ps.id = NEW.id_punto_suministro
         LIMIT 1;

        INSERT INTO clientes_actividad (
            cliente_id, punto_suministro_id, contrato_id, servicio_id,
            tipo_evento, descripcion, origen, cups, solo_nivel_uno, fecha_evento, usuario_nombre
        ) VALUES (
            NEW.id_cliente, NEW.id_punto_suministro, NEW.id, NULL,
            'DENUNCIA_CONTRATO_REALIZADA',
            CONCAT(
                'DENUNCIA DE CONTRATO REALIZADA - Fecha ',
                COALESCE(DATE_FORMAT(NEW.fecha_denuncia_contrato, '%d/%m/%Y'), DATE_FORMAT(CURDATE(), '%d/%m/%Y'))
            ),
            'CONTRATO',
            COALESCE(v_cups, 'GENERAL'),
            b'0',
            NOW(),
            COALESCE(@aura_usuario_nombre, 'Sistema')
        );
    END IF;
END $$

-- ============================================================
-- CONTRATOS -> CAMBIO POTENCIAS (NUEVO)
-- ============================================================
DROP TRIGGER IF EXISTS trg_clientes_actividad_contratos_au_potencia $$
CREATE TRIGGER trg_clientes_actividad_contratos_au_potencia
AFTER UPDATE ON contratos
FOR EACH ROW
BEGIN
    DECLARE v_cups VARCHAR(100);
    DECLARE v_cambios TEXT DEFAULT '';

    IF NEW.id_cliente IS NOT NULL AND (
        NOT (OLD.potencia1 <=> NEW.potencia1) OR
        NOT (OLD.potencia2 <=> NEW.potencia2) OR
        NOT (OLD.potencia3 <=> NEW.potencia3) OR
        NOT (OLD.potencia4 <=> NEW.potencia4) OR
        NOT (OLD.potencia5 <=> NEW.potencia5) OR
        NOT (OLD.potencia6 <=> NEW.potencia6)
    ) THEN
        SELECT COALESCE(NULLIF(TRIM(ps.cupsluz), ''), NULLIF(TRIM(ps.cupsgas), ''), 'GENERAL')
          INTO v_cups
          FROM punto_suministro ps
         WHERE ps.id = NEW.id_punto_suministro
         LIMIT 1;

        IF NOT (OLD.potencia1 <=> NEW.potencia1) THEN
            SET v_cambios = CONCAT(v_cambios, IF(LENGTH(v_cambios)>0,', ',''),
                'P1: ', IFNULL(OLD.potencia1,'—'), '→', IFNULL(NEW.potencia1,'—'));
        END IF;
        IF NOT (OLD.potencia2 <=> NEW.potencia2) THEN
            SET v_cambios = CONCAT(v_cambios, IF(LENGTH(v_cambios)>0,', ',''),
                'P2: ', IFNULL(OLD.potencia2,'—'), '→', IFNULL(NEW.potencia2,'—'));
        END IF;
        IF NOT (OLD.potencia3 <=> NEW.potencia3) THEN
            SET v_cambios = CONCAT(v_cambios, IF(LENGTH(v_cambios)>0,', ',''),
                'P3: ', IFNULL(OLD.potencia3,'—'), '→', IFNULL(NEW.potencia3,'—'));
        END IF;
        IF NOT (OLD.potencia4 <=> NEW.potencia4) THEN
            SET v_cambios = CONCAT(v_cambios, IF(LENGTH(v_cambios)>0,', ',''),
                'P4: ', IFNULL(OLD.potencia4,'—'), '→', IFNULL(NEW.potencia4,'—'));
        END IF;
        IF NOT (OLD.potencia5 <=> NEW.potencia5) THEN
            SET v_cambios = CONCAT(v_cambios, IF(LENGTH(v_cambios)>0,', ',''),
                'P5: ', IFNULL(OLD.potencia5,'—'), '→', IFNULL(NEW.potencia5,'—'));
        END IF;
        IF NOT (OLD.potencia6 <=> NEW.potencia6) THEN
            SET v_cambios = CONCAT(v_cambios, IF(LENGTH(v_cambios)>0,', ',''),
                'P6: ', IFNULL(OLD.potencia6,'—'), '→', IFNULL(NEW.potencia6,'—'));
        END IF;

        INSERT INTO clientes_actividad (
            cliente_id, punto_suministro_id, contrato_id, servicio_id,
            tipo_evento, descripcion, origen, cups, solo_nivel_uno, fecha_evento, usuario_nombre
        ) VALUES (
            NEW.id_cliente, NEW.id_punto_suministro, NEW.id, NULL,
            'CAMBIO_POTENCIA',
            CONCAT('CAMBIO DE POTENCIAS - ', v_cambios),
            'CONTRATO',
            COALESCE(v_cups, 'GENERAL'),
            b'1',
            NOW(),
            COALESCE(@aura_usuario_nombre, 'Sistema')
        );
    END IF;
END $$

-- ============================================================
-- CLIENTES -> MODIFICACION DATOS PERSONALES (recreado con usuario_nombre)
-- ============================================================
DROP TRIGGER IF EXISTS trg_clientes_actividad_clientes_au $$
CREATE TRIGGER trg_clientes_actividad_clientes_au
AFTER UPDATE ON clientes
FOR EACH ROW
BEGIN
    DECLARE v_campos TEXT;

    SET v_campos = CONCAT_WS(', ',
        IF(NOT (OLD.nombre <=> NEW.nombre), 'Nombre', NULL),
        IF(NOT (OLD.cif <=> NEW.cif), 'CIF', NULL),
        IF(NOT (OLD.direccion <=> NEW.direccion), 'Direccion', NULL),
        IF(NOT (OLD.codigo_postal <=> NEW.codigo_postal), 'Codigo postal', NULL),
        IF(NOT (OLD.poblacion <=> NEW.poblacion), 'Poblacion', NULL),
        IF(NOT (OLD.provincia <=> NEW.provincia), 'Provincia', NULL),
        IF(NOT (OLD.nombre_comercial <=> NEW.nombre_comercial), 'Grupo empresarial', NULL),
        IF(NOT (OLD.iban <=> NEW.iban), 'IBAN', NULL),
        IF(NOT (OLD.actividad <=> NEW.actividad), 'Actividad', NULL),
        IF(NOT (OLD.codigo_sector <=> NEW.codigo_sector), 'Sector', NULL),
        IF(NOT (OLD.codigo_subtipologia <=> NEW.codigo_subtipologia), 'Subtipologia', NULL),
        IF(NOT (OLD.nombre_autorizado <=> NEW.nombre_autorizado), 'Nombre autorizado', NULL),
        IF(NOT (OLD.telefono_autorizado <=> NEW.telefono_autorizado), 'Telefono autorizado', NULL),
        IF(NOT (OLD.email_autorizado <=> NEW.email_autorizado), 'Email autorizado', NULL),
        IF(NOT (OLD.dni_autorizado <=> NEW.dni_autorizado), 'DNI autorizado', NULL),
        IF(NOT (OLD.nombre_contacto1 <=> NEW.nombre_contacto1), 'Nombre contacto 1', NULL),
        IF(NOT (OLD.cargo_contacto1 <=> NEW.cargo_contacto1), 'Cargo contacto 1', NULL),
        IF(NOT (OLD.telefono_contacto1 <=> NEW.telefono_contacto1), 'Telefono contacto 1', NULL),
        IF(NOT (OLD.email_contacto1 <=> NEW.email_contacto1), 'Email contacto 1', NULL),
        IF(NOT (OLD.nombre_contacto2 <=> NEW.nombre_contacto2), 'Nombre contacto 2', NULL),
        IF(NOT (OLD.cargo_contacto2 <=> NEW.cargo_contacto2), 'Cargo contacto 2', NULL),
        IF(NOT (OLD.telefono_contacto2 <=> NEW.telefono_contacto2), 'Telefono contacto 2', NULL),
        IF(NOT (OLD.email_contacto2 <=> NEW.email_contacto2), 'Email contacto 2', NULL),
        IF(NOT (OLD.nombre_contacto3 <=> NEW.nombre_contacto3), 'Nombre contacto 3', NULL),
        IF(NOT (OLD.cargo_contacto3 <=> NEW.cargo_contacto3), 'Cargo contacto 3', NULL),
        IF(NOT (OLD.telefono_contacto3 <=> NEW.telefono_contacto3), 'Telefono contacto 3', NULL),
        IF(NOT (OLD.email_contacto3 <=> NEW.email_contacto3), 'Email contacto 3', NULL),
        IF(NOT (OLD.user_telemedida <=> NEW.user_telemedida), 'Usuario telemedida', NULL),
        IF(NOT (OLD.pass_telemedida <=> NEW.pass_telemedida), 'Password telemedida', NULL),
        IF(NOT (OLD.id_usuario <=> NEW.id_usuario), 'Usuario asignado', NULL)
    );

    IF v_campos IS NOT NULL AND CHAR_LENGTH(TRIM(v_campos)) > 0 THEN
        INSERT INTO clientes_actividad (
            cliente_id, punto_suministro_id, contrato_id, servicio_id,
            tipo_evento, descripcion, origen, cups, solo_nivel_uno, fecha_evento, usuario_nombre
        ) VALUES (
            NEW.id, NULL, NULL, NULL,
            'MODIFICACION_DATOS_PERSONALES',
            CONCAT('MODIFICACION DE DATOS PERSONALES: ', LEFT(v_campos, 900)),
            'CLIENTE',
            NULL,
            b'0',
            NOW(),
            COALESCE(@aura_usuario_nombre, 'Sistema')
        );
    END IF;
END $$

-- ============================================================
-- SERVICIOS -> SERVICIO CREADO (recreado con usuario_nombre)
-- ============================================================
DROP TRIGGER IF EXISTS trg_clientes_actividad_servicios_ai $$
CREATE TRIGGER trg_clientes_actividad_servicios_ai
AFTER INSERT ON servicios
FOR EACH ROW
BEGIN
    DECLARE v_cups VARCHAR(100);

    IF NEW.id_cliente IS NOT NULL THEN
        SELECT COALESCE(NULLIF(TRIM(ps.cupsluz), ''), NULLIF(TRIM(ps.cupsgas), ''), 'GENERAL')
          INTO v_cups
          FROM punto_suministro ps
         WHERE ps.id = NEW.id_punto_suministro
         LIMIT 1;

        SET v_cups = COALESCE(NULLIF(TRIM(NEW.cups), ''), v_cups, 'GENERAL');

        INSERT INTO clientes_actividad (
            cliente_id, punto_suministro_id, contrato_id, servicio_id,
            tipo_evento, descripcion, origen, cups, solo_nivel_uno, fecha_evento, usuario_nombre
        ) VALUES (
            NEW.id_cliente, NEW.id_punto_suministro, NULL, NEW.id,
            'SERVICIO_CREADO',
            CONCAT('SERVICIO CREADO - ', COALESCE(NULLIF(TRIM(NEW.descripcion), ''), CONCAT('Servicio #', NEW.id))),
            'SERVICIO',
            COALESCE(v_cups, 'GENERAL'),
            b'1',
            NOW(),
            COALESCE(@aura_usuario_nombre, 'Sistema')
        );
    END IF;
END $$

-- ============================================================
-- SERVICIOS PAGOS -> SERVICIO PAGADO AI (recreado con usuario_nombre, solo_nivel_uno=b'1')
-- ============================================================
DROP TRIGGER IF EXISTS trg_clientes_actividad_servicios_pagos_ai $$
CREATE TRIGGER trg_clientes_actividad_servicios_pagos_ai
AFTER INSERT ON servicios_pagos
FOR EACH ROW
BEGIN
    DECLARE v_numero_cuotas INT DEFAULT 12;
    DECLARE v_pagado_new BOOLEAN DEFAULT FALSE;
    DECLARE v_cliente_id INT;
    DECLARE v_punto_suministro_id INT;
    DECLARE v_cups VARCHAR(100);

    SET v_numero_cuotas = IFNULL(
        (SELECT pp.numero_cuotas FROM tbpago_plazos pp WHERE pp.id = NEW.id_pago_plazos LIMIT 1),
        12);

    SET v_pagado_new =
        (CASE WHEN v_numero_cuotas >= 1  THEN IFNULL(NEW.primer_pago_pagado,        b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 2  THEN IFNULL(NEW.segundo_pago_pagado,   b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 3  THEN IFNULL(NEW.tercer_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 4  THEN IFNULL(NEW.cuarto_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 5  THEN IFNULL(NEW.quinto_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 6  THEN IFNULL(NEW.sexto_pago_pagado,     b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 7  THEN IFNULL(NEW.septimo_pago_pagado,   b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 8  THEN IFNULL(NEW.octavo_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 9  THEN IFNULL(NEW.noveno_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 10 THEN IFNULL(NEW.decimo_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 11 THEN IFNULL(NEW.decimoprimer_pago_pagado,  b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 12 THEN IFNULL(NEW.decimosegundo_pago_pagado, b'0') = b'1' ELSE TRUE END);

    IF v_pagado_new THEN
        SELECT s.id_cliente, s.id_punto_suministro,
               COALESCE(NULLIF(TRIM(s.cups), ''), NULLIF(TRIM(ps.cupsluz), ''), NULLIF(TRIM(ps.cupsgas), ''), 'GENERAL')
          INTO v_cliente_id, v_punto_suministro_id, v_cups
          FROM servicios s
          LEFT JOIN punto_suministro ps ON ps.id = s.id_punto_suministro
         WHERE s.id = NEW.id_servicio
         LIMIT 1;

        IF v_cliente_id IS NOT NULL THEN
            INSERT INTO clientes_actividad (
                cliente_id, punto_suministro_id, contrato_id, servicio_id,
                tipo_evento, descripcion, origen, cups, solo_nivel_uno, fecha_evento, usuario_nombre
            ) VALUES (
                v_cliente_id, v_punto_suministro_id, NULL, NEW.id_servicio,
                'SERVICIO_PAGADO',
                CONCAT('SERVICIO PAGADO - Importe final ', IFNULL(ROUND(NEW.importe_final, 2), 0), ' EUR'),
                'SERVICIO_PAGO',
                v_cups,
                b'1',
                NOW(),
                COALESCE(@aura_usuario_nombre, 'Sistema')
            );
        END IF;
    END IF;
END $$

-- ============================================================
-- SERVICIOS PAGOS -> SERVICIO PAGADO AU (recreado con usuario_nombre, solo_nivel_uno=b'1')
-- ============================================================
DROP TRIGGER IF EXISTS trg_clientes_actividad_servicios_pagos_au $$
CREATE TRIGGER trg_clientes_actividad_servicios_pagos_au
AFTER UPDATE ON servicios_pagos
FOR EACH ROW
BEGIN
    DECLARE v_numero_cuotas INT DEFAULT 12;
    DECLARE v_pagado_old BOOLEAN DEFAULT FALSE;
    DECLARE v_pagado_new BOOLEAN DEFAULT FALSE;
    DECLARE v_cliente_id INT;
    DECLARE v_punto_suministro_id INT;
    DECLARE v_cups VARCHAR(100);

    SET v_numero_cuotas = IFNULL(
        (SELECT pp.numero_cuotas FROM tbpago_plazos pp WHERE pp.id = NEW.id_pago_plazos LIMIT 1),
        12);

    SET v_pagado_old =
        (CASE WHEN v_numero_cuotas >= 1  THEN IFNULL(OLD.primer_pago_pagado,        b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 2  THEN IFNULL(OLD.segundo_pago_pagado,   b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 3  THEN IFNULL(OLD.tercer_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 4  THEN IFNULL(OLD.cuarto_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 5  THEN IFNULL(OLD.quinto_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 6  THEN IFNULL(OLD.sexto_pago_pagado,     b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 7  THEN IFNULL(OLD.septimo_pago_pagado,   b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 8  THEN IFNULL(OLD.octavo_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 9  THEN IFNULL(OLD.noveno_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 10 THEN IFNULL(OLD.decimo_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 11 THEN IFNULL(OLD.decimoprimer_pago_pagado,  b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 12 THEN IFNULL(OLD.decimosegundo_pago_pagado, b'0') = b'1' ELSE TRUE END);

    SET v_pagado_new =
        (CASE WHEN v_numero_cuotas >= 1  THEN IFNULL(NEW.primer_pago_pagado,        b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 2  THEN IFNULL(NEW.segundo_pago_pagado,   b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 3  THEN IFNULL(NEW.tercer_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 4  THEN IFNULL(NEW.cuarto_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 5  THEN IFNULL(NEW.quinto_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 6  THEN IFNULL(NEW.sexto_pago_pagado,     b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 7  THEN IFNULL(NEW.septimo_pago_pagado,   b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 8  THEN IFNULL(NEW.octavo_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 9  THEN IFNULL(NEW.noveno_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 10 THEN IFNULL(NEW.decimo_pago_pagado,    b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 11 THEN IFNULL(NEW.decimoprimer_pago_pagado,  b'0') = b'1' ELSE TRUE END)
        AND (CASE WHEN v_numero_cuotas >= 12 THEN IFNULL(NEW.decimosegundo_pago_pagado, b'0') = b'1' ELSE TRUE END);

    IF v_pagado_new = TRUE AND v_pagado_old = FALSE THEN
        SELECT s.id_cliente, s.id_punto_suministro,
               COALESCE(NULLIF(TRIM(s.cups), ''), NULLIF(TRIM(ps.cupsluz), ''), NULLIF(TRIM(ps.cupsgas), ''), 'GENERAL')
          INTO v_cliente_id, v_punto_suministro_id, v_cups
          FROM servicios s
          LEFT JOIN punto_suministro ps ON ps.id = s.id_punto_suministro
         WHERE s.id = NEW.id_servicio
         LIMIT 1;

        IF v_cliente_id IS NOT NULL THEN
            INSERT INTO clientes_actividad (
                cliente_id, punto_suministro_id, contrato_id, servicio_id,
                tipo_evento, descripcion, origen, cups, solo_nivel_uno, fecha_evento, usuario_nombre
            ) VALUES (
                v_cliente_id, v_punto_suministro_id, NULL, NEW.id_servicio,
                'SERVICIO_PAGADO',
                CONCAT('SERVICIO PAGADO - Importe final ', IFNULL(ROUND(NEW.importe_final, 2), 0), ' EUR'),
                'SERVICIO_PAGO',
                v_cups,
                b'1',
                NOW(),
                COALESCE(@aura_usuario_nombre, 'Sistema')
            );
        END IF;
    END IF;
END $$

DELIMITER ;
