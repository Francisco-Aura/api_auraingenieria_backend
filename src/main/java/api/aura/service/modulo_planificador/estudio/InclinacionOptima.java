package api.aura.service.modulo_planificador.estudio;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

/**
 * Busca la inclinación que más produce al año partiendo de la del estudio, con las menos consultas posibles: prueba a subir o bajar
 * 10°, sigue por el lado en que mejora, y cuando deja de mejorar afina con pasos de 5°. Se detiene en cuanto lo que se ganaría es
 * poco (menos de un {@value #UMBRAL_PORCENTAJE} % de la producción) o al llegar al máximo de consultas.
 */
public final class InclinacionOptima {

    /** Mejora mínima (en %) para seguir probando otro ángulo. */
    public static final double UMBRAL_PORCENTAJE = 0.3;
    static final int MAX_CONSULTAS = 10, PASO_INICIAL = 10, PASO_MINIMO = 5, MAX_ANGULO = 90;

    /** La inclinación del estudio y la mejor encontrada, con lo que produce cada una (kWh por kWp al año) y las consultas hechas. */
    public record Resultado(int actual, double produccionActual, int optima, double produccionOptima, int consultas) {
        /** Lo que se produciría de más con la óptima, en tanto por uno (0 si la actual ya es la mejor). */
        public double mejora() {
            return produccionActual > 0 ? Math.max(0, produccionOptima / produccionActual - 1) : 0;
        }
    }

    private InclinacionOptima() {
    }

    /** {@code produccion} da los kWh por kWp al año de una inclinación (de 0 a 90°); se llama una sola vez por ángulo. */
    public static Resultado buscar(int inicio, IntToDoubleFunction produccion) {
        Map<Integer, Double> vistos = new HashMap<>();
        IntToDoubleFunction f = a -> vistos.computeIfAbsent(a, produccion::applyAsDouble);
        int actual = Math.max(0, Math.min(MAX_ANGULO, inicio));
        double fActual = f.applyAsDouble(actual);
        int mejor = actual;
        double fMejor = fActual;
        int paso = PASO_INICIAL, sentido = 1;
        while (vistos.size() < MAX_CONSULTAS) {
            boolean mejora = false;
            for (int s : new int[]{sentido, -sentido}) {
                int c = Math.max(0, Math.min(MAX_ANGULO, mejor + s * paso));
                if (c == mejor || vistos.containsKey(c) || vistos.size() >= MAX_CONSULTAS) continue;
                double fc = f.applyAsDouble(c);
                if (fc > fMejor * (1 + UMBRAL_PORCENTAJE / 100)) {
                    mejor = c;
                    fMejor = fc;
                    sentido = s;
                    mejora = true;
                    break;
                }
            }
            if (mejora) continue;
            if (paso <= PASO_MINIMO) break;
            paso = Math.max(PASO_MINIMO, paso / 2);
        }
        return new Resultado(actual, fActual, mejor, fMejor, vistos.size());
    }
}
