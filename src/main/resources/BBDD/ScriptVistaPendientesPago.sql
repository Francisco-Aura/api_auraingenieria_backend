GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[GetServiciosPendientesPago]') AND type in (N'FN', N'IF', N'TF', N'FS', N'FT'))
DROP FUNCTION [dbo].[GetServiciosPendientesPago]
GO

CREATE FUNCTION [dbo].[GetServiciosPendientesPago]
(
    @FechaDesde DATE = NULL,
    @FechaHasta DATE = NULL
)
RETURNS TABLE
AS
RETURN
(
    SELECT 
        s.Id as id,
        c.Nombre as cliente,
        s.Descripcion as servicio,
        s.FechaVencimiento,
        s.Importe,
        CASE 
            WHEN s.FechaVencimiento < GETDATE() THEN 'Vencido'
            WHEN s.FechaVencimiento <= DATEADD(day, 7, GETDATE()) THEN 'Próximo a vencer'
            ELSE 'Pendiente'
        END as estado
    FROM Servicios s
    INNER JOIN Clientes c ON s.ClienteId = c.Id
    WHERE s.EstadoPago = 'Pendiente'
    AND (@FechaDesde IS NULL OR s.FechaVencimiento >= @FechaDesde)
    AND (@FechaHasta IS NULL OR s.FechaVencimiento <= @FechaHasta)
    ORDER BY s.FechaVencimiento ASC
)
GO