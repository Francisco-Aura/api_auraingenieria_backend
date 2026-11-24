package api.aura.dto.mcas;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class McasPropuestaDTO {
    private Long id;
    private Integer idContrato;
    private String tipoEvento;
    private String estado;
    private Integer idConjuntoReglas;
    private Integer idPoliticaRetrocesion;
    private String hashContrato;
    /** importeAutomatico = resultado del motor (NUNCA sobrescribir en DB = importe_calculado) */
    private BigDecimal importeAutomatico;
    private BigDecimal importeDefinitivo;
    private Integer numeroPagos;
    private LocalDate fechaPrimeraAplicacion;
    private String motivoRechazo;
    private String advertenciasJson;
    /** Advertencias en texto para el usuario (también las de propuestas antiguas, traducidas). */
    private List<String> advertencias;
    /** Nombre legible de la regla aplicada; null si no tiene o no hubo regla */
    private String nombreRegla;
    /** Nombre legible del conjunto (tabla de comisiones); null si no tiene o no hubo conjunto */
    private String nombreConjunto;
    // Campos grandes: null en listados, populados en detalle
    private String trazaCalculoJson;
    private String instantaneaJson;
    private String datosNormalizadosJson;
    private String datosEditadosJson;
    private Integer idServicioCreado;
    private String creadoPor;
    private LocalDateTime creadoEn;
    private String aprobadoPor;
    private LocalDateTime aprobadoEn;
    private Integer versionOptimista;
    private List<McasLineaDTO> lineas;
    private List<McasCalendarioDTO> calendario;

    // Datos del contrato (instantánea del cálculo) y de la regla (traza), para el
    // listado y el detalle. Los nombres se resuelven por lotes, una vez por petición.
    private String numeroContrato;
    private Integer idCliente;
    private String nombreCliente;
    /** CUPS de luz o, si no hay, el de gas */
    private String cups;
    private Integer idComercializadora;
    private String nombreComercializadora;
    private Integer idGestora;
    private String nombreGestora;
    private String tipoPrecio;
    private String tipoSuministro;
    private String segmento;
    private Boolean esRenovacion;
    private String tarifaAcceso;
    /** Tarifa de precios (producto comercial MCAS) */
    private String productoComercial;
    private BigDecimal consumoAnualKwh;
    private BigDecimal potenciaCalculoKw;
    /** CONTRATO, MAYOR_CONTRATADA o null */
    private String potenciaOrigen;
    private BigDecimal feeEurMwh;
    /** Factor aplicado al FEE (0.44 = 44 %); null si la regla no usa FEE */
    private BigDecimal porcentajeAplicacionFee;
    private String codigoRegla;
    private String metodoCalculo;
    private String codigoConjunto;
    private String versionConjunto;
    /** Política de retrocesión asociada, en palabras; nunca se descuenta en el alta */
    private String retrocesionPotencial;
    private String actualizadoPor;
    private LocalDateTime actualizadoEn;
    /** Rechazada o sin cálculo automático: se puede completar a mano y crear el servicio */
    private boolean admiteEdicionManual;
    /** Es la propuesta más reciente de su contrato (mayor creadoEn; a igualdad, mayor id) */
    private boolean ultimaDelContrato;
}
