-- =====================================================================
-- MCAS: retrocesiones automáticas (duda 20 del cliente, 11/11/2026)
-- Ejecutar ANTES de desplegar el backend. Se puede lanzar más de una vez.
--
-- Cuando un contrato con la comisión aprobada se da de baja (él o su punto)
-- o cambia de comercializadora antes de 365 días, la app deja aquí una
-- retrocesión PENDIENTE. No mueve nada hasta que un administrador la
-- aprueba en Comisiones MCAS.
-- =====================================================================

CREATE TABLE IF NOT EXISTS mcas_retrocesiones (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_propuesta             BIGINT        NOT NULL,
    id_contrato              INT           NOT NULL,
    id_punto_suministro      INT           NULL,
    id_comercializadora      INT           NULL,
    motivo                   VARCHAR(30)   NOT NULL,
    id_contrato_nuevo        INT           NULL,
    fecha_inicio             DATE          NOT NULL,
    fecha_fin                DATE          NOT NULL,
    dias_activo              INT           NOT NULL,
    id_politica              INT           NULL,
    importe_comision         DECIMAL(18,2) NULL,
    cuotas_anuladas          VARCHAR(60)   NULL,
    importe_cuotas_anuladas  DECIMAL(18,2) NULL,
    importe_calculado        DECIMAL(18,2) NULL,
    importe_definitivo       DECIMAL(18,2) NULL,
    detalle                  VARCHAR(500)  NULL,
    estado                   VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    motivo_descarte          VARCHAR(500)  NULL,
    id_servicio_creado       INT           NULL,
    creado_en                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resuelto_por             VARCHAR(250)  NULL,
    resuelto_en              DATETIME      NULL,
    UNIQUE KEY uk_mcas_retrocesiones_propuesta (id_propuesta),
    KEY idx_mcas_retrocesiones_estado (estado),
    KEY idx_mcas_retrocesiones_punto (id_punto_suministro, estado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- El interruptor ya existía sin efecto: se le pone su descripción de ahora (no se enciende ni se apaga).
INSERT INTO configuracion_modulos (codigo, activo, descripcion) VALUES
    ('MCAS_RETROCESSION_ENABLED', 0, 'MCAS - Retrocesiones automaticas por baja o cambio de comercializadora antes del año')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- Comprobación: la tabla existe y el interruptor sale con su valor (0 hasta que se encienda).
SELECT COUNT(*) AS retrocesiones FROM mcas_retrocesiones;
SELECT codigo, activo FROM configuracion_modulos WHERE codigo = 'MCAS_RETROCESSION_ENABLED';
