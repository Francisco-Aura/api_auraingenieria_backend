package api.aura.service.modulo_planificador.estudio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Peajes más cargos («precio BOE») de un BOE y una tarifa, periodo a periodo: energía en €/kWh y potencia en €/kW·año. Los periodos que
 * la tarifa no tiene valen 0. {@code nombre} y {@code desde} dicen de qué BOE salen (su nombre y el día desde el que vale); {@code aviso}
 * explica si no se han podido tomar los que se pedían.
 */
public record PreciosRegulados(String nombre, java.time.LocalDate desde, String tarifa, double[] energia, double[] potencia, String referencia, String aviso) {

    public PreciosRegulados {
        if (energia.length != 6 || potencia.length != 6) throw new IllegalArgumentException("Hacen falta seis periodos");
    }

    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", nombre);
        m.put("desde", desde.toString());
        m.put("tarifa", tarifa);
        m.put("energia", lista(energia, Periodos.periodosEnergia(tarifa)));
        m.put("potencia", lista(potencia, Periodos.periodosPotencia(tarifa)));
        m.put("referencia", referencia);
        if (aviso != null) m.put("aviso", aviso);
        return m;
    }

    static List<Double> lista(double[] v, int n) {
        List<Double> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(v[i]);
        return l;
    }
}
