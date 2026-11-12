package api.aura.service.mcas.engine;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasValorRangoRepository;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class EstrategiaNoComisionable implements EstrategiaCalculo {
    @Override public String getNombre() { return "NOT_COMMISSIONABLE"; }
    @Override
    public List<ResultadoCalculo.LineaResultado> calcular(ContextoCalculo ctx, McasReglaComision regla,
                                                          McasValorRangoRepository rangoRepo) {
        throw new McasNoComisionableException(
                regla.getMotivoManual() != null ? regla.getMotivoManual() : "Este caso no genera comision");
    }
}
