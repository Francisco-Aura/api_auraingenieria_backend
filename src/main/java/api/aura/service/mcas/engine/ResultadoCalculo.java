package api.aura.service.mcas.engine;

import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.util.List;

/** Resultado del motor de reglas MCAS */
@Value @Builder
public class ResultadoCalculo {

    public enum Resultado {
        CALCULADO, REQUIERE_REVISION_MANUAL, NO_COMISIONABLE, ERROR_CALCULO, REQUIERE_DATOS
    }

    Resultado resultado;
    Integer idRegla;
    Integer idConjuntoReglas;
    String metodoCalculo;
    List<LineaResultado> lineas;
    BigDecimal importeTotal;
    Integer numeroPagos;
    String trazaJson;
    List<String> advertencias;
    String causaError;
    /** Códigos técnicos de los datos que faltan (REQUIERE_DATOS); nunca null. */
    @Builder.Default
    List<String> camposFaltantes = List.of();

    @Value @Builder
    public static class LineaResultado {
        String tipoLinea;
        String descripcion;
        String metodo;
        BigDecimal valorBase;
        BigDecimal valorTasa;
        BigDecimal valorPorcentaje;
        BigDecimal importe;
        Integer idRegla;
    }
}
