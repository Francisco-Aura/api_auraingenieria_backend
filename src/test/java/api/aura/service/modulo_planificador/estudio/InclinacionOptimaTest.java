package api.aura.service.modulo_planificador.estudio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InclinacionOptimaTest {

    /** Producción con el máximo en {@code pico} grados: cae poco a poco a ambos lados. */
    private static double curva(int a, int pico) {
        return 1700 - 0.35 * (a - pico) * (a - pico);
    }

    @Test
    void desdeUnaInclinacionBajaSubeHastaAcotarElOptimo() {
        List<Integer> pruebas = new ArrayList<>();
        InclinacionOptima.Resultado r = InclinacionOptima.buscar(10, a -> {
            pruebas.add(a);
            return curva(a, 30);
        });
        assertEquals(10, r.actual());
        assertTrue(Math.abs(r.optima() - 30) <= 5, "queda a menos de 5° del óptimo: " + r.optima());
        assertTrue(r.produccionOptima() > r.produccionActual());
        assertTrue(r.mejora() > 0.05);
        assertTrue(r.consultas() <= InclinacionOptima.MAX_CONSULTAS);
        assertEquals(pruebas.size(), pruebas.stream().distinct().count(), "no se consulta dos veces el mismo ángulo");
    }

    @Test
    void desdeArribaBajaPorElOtroLado() {
        InclinacionOptima.Resultado r = InclinacionOptima.buscar(60, a -> curva(a, 30));
        assertTrue(Math.abs(r.optima() - 30) <= 5, "" + r.optima());
        assertTrue(r.mejora() > 0.1);
    }

    @Test
    void siYaEstaCercaDelOptimoParaConPocasConsultas() {
        InclinacionOptima.Resultado r = InclinacionOptima.buscar(30, a -> curva(a, 31));
        assertEquals(30, r.optima());
        assertEquals(0, r.mejora(), 1e-9);
        assertTrue(r.consultas() <= 5, "consultas: " + r.consultas());
    }

    @Test
    void conUnaCurvaMuyPlanaNoSigueBuscando() {
        // Entre 20° y 40° apenas cambia: no compensa seguir consultando
        InclinacionOptima.Resultado r = InclinacionOptima.buscar(20, a -> 1700 - 0.0005 * (a - 30) * (a - 30));
        assertTrue(r.mejora() < 0.01);
        assertTrue(r.consultas() <= 5, "consultas: " + r.consultas());
    }

    @Test
    void nuncaSaleDeLosLimites() {
        InclinacionOptima.Resultado r = InclinacionOptima.buscar(0, a -> 1700 - 2.0 * a);
        assertEquals(0, r.optima());
        InclinacionOptima.Resultado t = InclinacionOptima.buscar(85, a -> 1000 + 3.0 * a);
        assertEquals(90, t.optima());
    }
}
