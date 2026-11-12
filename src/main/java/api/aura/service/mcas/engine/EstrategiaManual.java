package api.aura.service.mcas.engine;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasValorRangoRepository;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class EstrategiaManual implements EstrategiaCalculo {
    @Override public String getNombre() { return "MANUAL"; }
    @Override
    public List<ResultadoCalculo.LineaResultado> calcular(ContextoCalculo ctx, McasReglaComision regla,
                                                          McasValorRangoRepository rangoRepo) {
        throw new McasRevisionManualException(
                regla.getMotivoManual() != null ? regla.getMotivoManual() : "Regla marcada como MANUAL");
    }
}
