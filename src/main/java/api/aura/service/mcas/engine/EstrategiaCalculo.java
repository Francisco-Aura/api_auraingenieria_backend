package api.aura.service.mcas.engine;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasValorRangoRepository;
import java.util.List;

/**
 * Interfaz de estrategia de cálculo.
 * Cada implementación es determinista, sin efectos secundarios.
 * NUNCA usa eval ni fórmulas arbitrarias de BD.
 */
public interface EstrategiaCalculo {
    String getNombre();
    List<ResultadoCalculo.LineaResultado> calcular(
            ContextoCalculo ctx,
            McasReglaComision regla,
            McasValorRangoRepository rangoRepo);
}
