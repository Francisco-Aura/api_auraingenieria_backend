package api.aura.service.mcas.engine;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasValorRangoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

/** FIXED_AMOUNT: importe constante de calculo_json.importe */
@Component
public class EstrategiaImporteFijo implements EstrategiaCalculo {
    private final ObjectMapper mapper;
    public EstrategiaImporteFijo(ObjectMapper mapper) { this.mapper = mapper; }

    @Override public String getNombre() { return "FIXED_AMOUNT"; }

    @Override
    public List<ResultadoCalculo.LineaResultado> calcular(ContextoCalculo ctx, McasReglaComision regla,
                                                          McasValorRangoRepository rangoRepo) {
        try {
            JsonNode j = mapper.readTree(regla.getCalculoJson());
            BigDecimal importe = ParametrosMcas.decimal(j, "FIXED_AMOUNT", "importe");
            return List.of(ResultadoCalculo.LineaResultado.builder()
                    .tipoLinea("COMISION_BASE").descripcion("Importe fijo")
                    .metodo("FIXED_AMOUNT").importe(importe).idRegla(regla.getId()).build());
        } catch (McasCalculoException e) { throw e;
        } catch (Exception e) {
            throw new McasCalculoException("FIXED_AMOUNT: error en calculo_json — " + e.getMessage());
        }
    }
}
