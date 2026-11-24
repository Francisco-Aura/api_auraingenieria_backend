package api.aura.service.mcas.engine;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Registro de estrategias de cálculo. Inyección automática vía @Component. */
@Component
public class RegistroEstrategias {
    private final Map<String, EstrategiaCalculo> mapa;

    public RegistroEstrategias(List<EstrategiaCalculo> lista) {
        mapa = lista.stream().collect(Collectors.toMap(EstrategiaCalculo::getNombre, Function.identity()));
        EstrategiaCalculo matriz = mapa.get("MATRIX_LOOKUP");
        if (matriz != null) mapa.put("MULTI_DIMENSION_LOOKUP", matriz);
    }

    public EstrategiaCalculo obtener(String nombre) {
        EstrategiaCalculo e = mapa.get(nombre);
        if (e == null)
            throw new McasCalculoException("Estrategia no registrada: '" + nombre + "'");
        return e;
    }

    public boolean existe(String nombre) { return mapa.containsKey(nombre); }
}
