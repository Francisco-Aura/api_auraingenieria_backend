package api.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuotaServicioDTO {
    private Integer id;
    private Integer idServicio;
    private Integer numeroPago;
    private Date fechaPago;
    private Double importePago;
    private Boolean pagado;
}