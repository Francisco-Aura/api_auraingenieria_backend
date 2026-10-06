package api.aura.dto.mcas;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Política de retrocesión para la pantalla de Configuración MCAS. Es informativa:
 * se muestra como «retrocesión potencial» y nunca se descuenta en el alta.
 */
@Data
public class McasPoliticaRetrocesionDTO {
    private Integer id;
    private String codigo;
    private Integer idComercializadora;
    private String nombreComercializadora;
    private Integer idGestora;
    private String nombreGestora;
    private String version;
    private LocalDate vigenciaDesde;
    private LocalDate vigenciaHasta;
    private String tipoSuministro;
    private String segmento;
    private String tarifaAcceso;
    /** PORCENTAJE, FIJO, PRORRATEO o NO_APLICA */
    private String metodo;
    private Boolean limiteImportePagado;
    /** BORRADOR, PUBLICADO o ARCHIVADO */
    private String estado;
    private String descripcion;
    /** Tramos en lenguaje natural (solo lectura) */
    private String resumen;
    private List<McasTramoRetrocesionDTO> tramos = new ArrayList<>();
}
