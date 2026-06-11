-- ============================================================
-- FIX: SERVICIO_PAGADO debe ser solo_nivel_uno = 1
-- Fecha: 2026-06-04
-- Problema: Los triggers trg_clientes_actividad_servicios_pagos_ai y _au
--           tenían solo_nivel_uno = b'0', exponiendo datos financieros
--           a usuarios de nivel < 1.
-- Solución: Corregir triggers + actualizar registros existentes
-- ============================================================

-- Corregir datos históricos
UPDATE clientes_actividad
   SET solo_nivel_uno = b'1'
 WHERE tipo_evento = 'SERVICIO_PAGADO';

DELIMITER $$

-- ============================================================
-- Recrear trigger AFTER INSERT on servicios_pagos (corregido)
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
        SELECT s.id_cliente,
               s.id_punto_suministro,
               COALESCE(NULLIF(TRIM(s.cups), ''), NULLIF(TRIM(ps.cupsluz), ''), NULLIF(TRIM(ps.cupsgas), ''), 'GENERAL')
          INTO v_cliente_id, v_punto_suministro_id, v_cups
          FROM servicios s
          LEFT JOIN punto_suministro ps ON ps.id = s.id_punto_suministro
         WHERE s.id = NEW.id_servicio
         LIMIT 1;

        IF v_cliente_id IS NOT NULL THEN
            INSERT INTO clientes_actividad (
                cliente_id, punto_suministro_id, contrato_id, servicio_id,
                tipo_evento, descripcion, origen, cups, solo_nivel_uno, fecha_evento
            ) VALUES (
                v_cliente_id, v_punto_suministro_id, NULL, NEW.id_servicio,
                'SERVICIO_PAGADO',
                CONCAT('SERVICIO PAGADO - Importe final ', IFNULL(ROUND(NEW.importe_final, 2), 0), ' EUR'),
                'SERVICIO_PAGO',
                v_cups,
                b'1',   -- CORREGIDO: era b'0'
                NOW()
            );
        END IF;
    END IF;
END $$

-- ============================================================
-- Recrear trigger AFTER UPDATE on servicios_pagos (corregido)
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
        SELECT s.id_cliente,
               s.id_punto_suministro,
               COALESCE(NULLIF(TRIM(s.cups), ''), NULLIF(TRIM(ps.cupsluz), ''), NULLIF(TRIM(ps.cupsgas), ''), 'GENERAL')
          INTO v_cliente_id, v_punto_suministro_id, v_cups
          FROM servicios s
          LEFT JOIN punto_suministro ps ON ps.id = s.id_punto_suministro
         WHERE s.id = NEW.id_servicio
         LIMIT 1;

        IF v_cliente_id IS NOT NULL THEN
            INSERT INTO clientes_actividad (
                cliente_id, punto_suministro_id, contrato_id, servicio_id,
                tipo_evento, descripcion, origen, cups, solo_nivel_uno, fecha_evento
            ) VALUES (
                v_cliente_id, v_punto_suministro_id, NULL, NEW.id_servicio,
                'SERVICIO_PAGADO',
                CONCAT('SERVICIO PAGADO - Importe final ', IFNULL(ROUND(NEW.importe_final, 2), 0), ' EUR'),
                'SERVICIO_PAGO',
                v_cups,
                b'1',   -- CORREGIDO: era b'0'
                NOW()
            );
        END IF;
    END IF;
END $$

DELIMITER ;
