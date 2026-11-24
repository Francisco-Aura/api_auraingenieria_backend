package api.aura.dto.mcas;

import lombok.Data;

import java.math.BigDecimal;

/** Tramo de retrocesión. PORCENTAJE y FACTOR van como factor (0,6 = 60 %). */
@Data
public class McasTramoRetrocesionDTO {
    private Integer id;
    /** DIAS o MESES desde la activación */
    private String unidad;
    private Integer desdeValor;
    private Integer hastaValor;
    private Boolean desdeInclusivo;
    private Boolean hastaInclusivo;
    private BigDecimal valor;
    /** PORCENTAJE, IMPORTE_FIJO, FACTOR o SIN_CONFIRMAR */
    private String tipoValor;
    private Integer prioridad;
}
