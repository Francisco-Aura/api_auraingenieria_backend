package api.aura.service.mcas.engine;

import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos de entrada normalizados para el motor MCAS */
@Value @Builder
public class ContextoCalculo {
    Integer idContrato;
    Integer idComercializadora;
    Integer idGestora;
    String tipoSuministro;          // LUZ / GAS
    String segmento;                // RESIDENCIAL / PYME
    Boolean esRenovacion;
    String tipoRenovacion;          // FIRMADA / TACITA / OTRA
    String tipoPrecio;              // FIJO / INDEXADO
    String ambito;                  // DENTRO / FUERA / NA
    String zonaComision;
    Integer idTarifaLuz;
    Integer idTarifaGas;
    String codigoTarifaAcceso;      // nombre de la tarifa (ej: "3.0TD") para match en condiciones
    Integer idProductoComercial;
    String codigoProductoComercial; // código del producto para match en condiciones
    BigDecimal potenciaCalculoKw;
    String potenciaOrigen;          // CONTRATO / MAYOR_CONTRATADA (P1–P6) / null si no hay potencia
    BigDecimal consumoAnualKwh;
    BigDecimal consumoAnualMwh;     // = consumoAnualKwh / 1000
    BigDecimal feeEurMwh;
    Integer numeroPagos;            // manual del usuario, prioridad sobre regla
    LocalDate fechaEfectiva;
    Boolean altaNueva;
    LocalDate fechaUltimaRelacionComercializadora;
    String correlacionId;
}
