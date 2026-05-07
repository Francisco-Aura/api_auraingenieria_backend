-- Añade campos para Instalación Fotovoltaica:
-- Ejecutado por Aura + Usuario + Contraseña

ALTER TABLE punto_suministro
    ADD COLUMN IF NOT EXISTS ejecutado_por_aura_fv BIT(1) NULL COMMENT 'Marca si la instalación FV fue ejecutada por Aura';

ALTER TABLE punto_suministro
    ADD COLUMN IF NOT EXISTS usuario_instalacion_fv VARCHAR(150) NULL COMMENT 'Usuario de acceso de la instalación fotovoltaica';

ALTER TABLE punto_suministro
    ADD COLUMN IF NOT EXISTS contrasena_instalacion_fv VARCHAR(150) NULL COMMENT 'Contraseña de acceso de la instalación fotovoltaica';
