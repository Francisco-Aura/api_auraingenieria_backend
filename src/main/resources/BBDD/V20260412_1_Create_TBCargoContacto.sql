-- Creación de la tabla de Cargos de Contacto para clientes
CREATE TABLE IF NOT EXISTS TBCargoContacto (
    id   INT AUTO_INCREMENT PRIMARY KEY,
    nombre      VARCHAR(250) NOT NULL,
    descripcion VARCHAR(250) NULL
);

-- Valores originales (los que estaban hardcodeados en el formulario)
INSERT INTO TBCargoContacto (nombre) VALUES
('REPRESENTANTE LEGAL'),
('ADMINISTRACION'),
('GERENTE'),
('ASESORIA'),
('FACTURAS'),
('ENCARGADO SUM. ELECTRICO');
