package api.aura.service.mcas.engine;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasValorRangoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/** RATE_PER_MWH: consumo_mwh × tasa_eur_mwh */
@Component
public class EstrategiaTasaPorMwh implements EstrategiaCalculo {
    private final ObjectMapper mapper;
    public EstrategiaTasaPorMwh(ObjectMapper mapper) { this.mapper = mapper; }

    @Override public String getNombre() { return "RATE_PER_MWH"; }

    @Override
    public List<ResultadoCalculo.LineaResultado> calcular(ContextoCalculo ctx, McasReglaComision regla,
                                                          McasValorRangoRepository rangoRepo) {
        if (ctx.getConsumoAnualMwh() == null)
            throw new McasDatosRequeridosException("RATE_PER_MWH: consumo_anual requerido", List.of("consumo_mwh"));
        try {
            JsonNode j = mapper.readTree(regla.getCalculoJson());
            BigDecimal tasa = ParametrosMcas.decimal(j, "RATE_PER_MWH", "tasa_eur_mwh");
            BigDecimal importe = ctx.getConsumoAnualMwh().multiply(tasa, MathContext.DECIMAL128);
            return List.of(ResultadoCalculo.LineaResultado.builder()
                    .tipoLinea("ENERGIA")
                    .descripcion(FormatoMcas.numero(ctx.getConsumoAnualMwh()) + " MWh × " + FormatoMcas.numero(tasa) + " €/MWh")
                    .metodo("RATE_PER_MWH")
                    .valorBase(ctx.getConsumoAnualMwh()).valorTasa(tasa)
                    .importe(importe).idRegla(regla.getId()).build());
        } catch (McasCalculoException e) { throw e;
        } catch (Exception e) {
            throw new McasCalculoException("RATE_PER_MWH: error en calculo_json — " + e.getMessage());
        }
    }
}
