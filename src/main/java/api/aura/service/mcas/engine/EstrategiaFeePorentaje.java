package api.aura.service.mcas.engine;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasValorRangoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * FEE_PERCENTAGE: consumo_mwh × fee_eur_mwh × porcentaje_aplicacion_fee
 * calculo_json: {"porcentaje_aplicacion_fee": 0.44, "fee_minimo_eur_mwh": 5.0}
 */
@Component
public class EstrategiaFeePorentaje implements EstrategiaCalculo {
    private final ObjectMapper mapper;
    public EstrategiaFeePorentaje(ObjectMapper mapper) { this.mapper = mapper; }

    @Override public String getNombre() { return "FEE_PERCENTAGE"; }

    @Override
    public List<ResultadoCalculo.LineaResultado> calcular(ContextoCalculo ctx, McasReglaComision regla,
                                                          McasValorRangoRepository rangoRepo) {
        if (ctx.getConsumoAnualMwh() == null)
            throw new McasDatosRequeridosException("FEE_PERCENTAGE: consumo_anual requerido", List.of("consumo_mwh"));
        if (ctx.getFeeEurMwh() == null)
            throw new McasDatosRequeridosException("FEE_PERCENTAGE: FEE del contrato requerido", List.of("fee"));
        try {
            JsonNode j = mapper.readTree(regla.getCalculoJson());
            BigDecimal pct = ParametrosMcas.factor(j, "FEE_PERCENTAGE", "porcentaje_aplicacion_fee");
            // FEE por debajo del mínimo: no es un fallo técnico sino una comisión que
            // la regla no cubre (v2 §14); el operador la completa a mano.
            if (j.has("fee_minimo_eur_mwh") && !j.get("fee_minimo_eur_mwh").isNull()) {
                BigDecimal min = j.get("fee_minimo_eur_mwh").decimalValue();
                if (ctx.getFeeEurMwh().compareTo(min) < 0)
                    throw new McasRevisionManualException("FEE del contrato " + FormatoMcas.numero(ctx.getFeeEurMwh())
                            + " €/MWh por debajo del mínimo " + FormatoMcas.numero(min)
                            + " €/MWh de la regla: complete la comisión manualmente");
            }
            BigDecimal importe = ctx.getConsumoAnualMwh()
                    .multiply(ctx.getFeeEurMwh(), MathContext.DECIMAL128)
                    .multiply(pct, MathContext.DECIMAL128);
            return List.of(ResultadoCalculo.LineaResultado.builder()
                    .tipoLinea("ENERGIA")
                    .descripcion(FormatoMcas.numero(ctx.getConsumoAnualMwh()) + " MWh × " + FormatoMcas.numero(ctx.getFeeEurMwh())
                            + " €/MWh × " + FormatoMcas.porcentaje(pct))
                    .metodo("FEE_PERCENTAGE")
                    .valorBase(ctx.getConsumoAnualMwh())
                    .valorTasa(ctx.getFeeEurMwh())
                    .valorPorcentaje(pct)
                    .importe(importe).idRegla(regla.getId()).build());
        } catch (McasCalculoException | McasRevisionManualException e) { throw e;
        } catch (Exception e) {
            throw new McasCalculoException("FEE_PERCENTAGE: error en calculo_json — " + e.getMessage());
        }
    }
}
