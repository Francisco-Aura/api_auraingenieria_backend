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
    String zonaComision;            // sale de la provincia del punto (McasZonasService); ya no se escribe a mano
    String motivoSinZona;           // por qué no se ha podido saber la zona, para decirlo si una regla la pide
    Integer idTarifaLuz;
    Integer idTarifaGas;
    String codigoTarifaAcceso;      // nombre de la tarifa (ej: "3.0TD") para match en condiciones
    Integer idProductoComercial;
    String codigoProductoComercial; // código del producto para match en condiciones
    String nombreProductoComercial; // nombre del producto, para las descripciones del desglose
    BigDecimal potenciaCalculoKw;
    String potenciaOrigen;          // CONTRATO / MAYOR_CONTRATADA (P1–P6) / null si no hay potencia
    BigDecimal consumoAnualKwh;
    BigDecimal consumoAnualMwh;     // = consumoAnualKwh / 1000
    BigDecimal feeEurMwh;
    Integer numeroPagos;            // de la comercializadora o su gestora (condiciones de cobro); la regla gana
    LocalDate fechaEfectiva;
    Boolean altaNueva;
    LocalDate fechaUltimaRelacionComercializadora;
    Integer mesesDesdeUltimaRelacion; // meses completos de esa fecha a la de inicio; null si no hubo relación
    String correlacionId;
}
