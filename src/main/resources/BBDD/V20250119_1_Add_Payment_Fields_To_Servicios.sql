-- Migración para agregar campos de pago a la tabla Servicios
-- Fecha: 19/01/2025
-- Descripción: Agregar campos importe, descuento, importeFinal e idPagoPlazos

ALTER TABLE Servicios 
ADD COLUMN importe DECIMAL(10,2) NULL,
ADD COLUMN descuento DECIMAL(10,2) NULL,
ADD COLUMN importeFinal DECIMAL(10,2) NULL,
ADD COLUMN idPagoPlazos INT NULL;

-- Agregar comentarios descriptivos
ALTER TABLE Servicios 
MODIFY COLUMN importe DECIMAL(10,2) NULL COMMENT 'Importe original del servicio',
MODIFY COLUMN descuento DECIMAL(10,2) NULL COMMENT 'Descuento aplicado al servicio',
MODIFY COLUMN importeFinal DECIMAL(10,2) NULL COMMENT 'Importe final después del descuento',
MODIFY COLUMN idPagoPlazos INT NULL COMMENT 'ID de la modalidad de pago (número de cuotas)';

-- Actualizar servicios existentes que tengan pagos asociados
UPDATE Servicios s 
SET 
    importe = (
        SELECT sp.importe 
        FROM ServiciosPagos sp 
        WHERE sp.idServicio = s.id 
        LIMIT 1
    ),
    descuento = (
        SELECT sp.descuento 
        FROM ServiciosPagos sp 
        WHERE sp.idServicio = s.id 
        LIMIT 1
    ),
    importeFinal = (
        SELECT sp.importeFinal 
        FROM ServiciosPagos sp 
        WHERE sp.idServicio = s.id 
        LIMIT 1
    ),
    idPagoPlazos = (
        SELECT sp.idPagoPlazos 
        FROM ServiciosPagos sp 
        WHERE sp.idServicio = s.id 
        LIMIT 1
    )
WHERE EXISTS (
    SELECT 1 
    FROM ServiciosPagos sp 
    WHERE sp.idServicio = s.id
);
