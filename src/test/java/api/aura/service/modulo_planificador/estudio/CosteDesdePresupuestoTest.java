package api.aura.service.modulo_planificador.estudio;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** El coste de la instalación sale del presupuesto de referencia (proporcional a los kWp) o, sin él, del precio provisional. */
class CosteDesdePresupuestoTest {

    private static Curva laboral() {
        double[] k = new double[Curva.HORAS];
        for (int i = 0; i < Curva.HORAS; i++) k[i] = Curva.laborable(i) && Curva.hora(i) >= 8 && Curva.hora(i) < 18 ? 40 : 2;
        return new Curva(k, false, "prueba");
    }

    private static Calculo calcular(double kwp, double bateria, Map<String, Object> eco) {
        Map<String, Object> d = new HashMap<>();
        d.put("instalacion", Map.of("kwp", kwp, "bateriaKwh", bateria, "orientacion", "sur"));
        d.put("economia", eco);
        return Calculo.de(d, laboral());
    }

    @Test
    void sinPresupuestoSeUsaElPrecioProvisional() {
        Map<String, Object> eco = new HashMap<>();
        eco.put("precioWp", 0.90);
        Calculo c = calcular(10, 0, eco);
        assertFalse(c.conPresupuesto());
        assertEquals(9000, c.base.entrada().inversion(), 0.01);
        assertEquals(0.90, c.precioWp, 1e-9);
    }

    @Test
    void conPresupuestoLaInversionEsLaSuya() {
        Map<String, Object> eco = new HashMap<>();
        eco.put("precioWp", 0.90);
        eco.put("presupuestoId", "p1");
        eco.put("presupuestoTotal", 11_500.0);
        eco.put("presupuestoKwp", 10.0);
        Calculo c = calcular(10, 0, eco);
        assertTrue(c.conPresupuesto());
        assertEquals(11_500, c.base.entrada().inversion(), 0.01, "el precio provisional no cuenta");
        assertEquals(1.15, c.precioWp, 1e-9, "€/Wp que se enseña: el del presupuesto");
    }

    @Test
    void laInversionEscalaConLosKwpDeCadaPotenciaQueSeCompara() {
        Map<String, Object> eco = new HashMap<>();
        eco.put("presupuestoTotal", 11_500.0);
        eco.put("presupuestoKwp", 10.0);
        // El estudio es de 12 kWp y el presupuesto de 10: 1,15 €/Wp en las dos
        Calculo c = calcular(12, 0, eco);
        assertEquals(13_800, c.base.entrada().inversion(), 0.01);
        for (Calculo.Punto p : c.escenarios) {
            assertEquals(1.15 * 1000 * p.sim().kwp(), p.entrada().inversion(), 0.5, "cada potencia al coste por vatio del presupuesto");
        }
    }

    @Test
    void unPresupuestoSinImporteOSinKwpNoCuenta() {
        Map<String, Object> sinKwp = new HashMap<>();
        sinKwp.put("precioWp", 0.80);
        sinKwp.put("presupuestoTotal", 5_000.0);
        assertFalse(calcular(10, 0, sinKwp).conPresupuesto());
        assertEquals(8000, calcular(10, 0, sinKwp).base.entrada().inversion(), 0.01);

        Map<String, Object> sinImporte = new HashMap<>();
        sinImporte.put("precioWp", 0.80);
        sinImporte.put("presupuestoKwp", 10.0);
        sinImporte.put("presupuestoTotal", 0);
        assertFalse(calcular(10, 0, sinImporte).conPresupuesto());
    }

    @Test
    void elPresupuestoGanaALaInversionTotalEscrita() {
        Map<String, Object> eco = new HashMap<>();
        eco.put("inversionTotal", 99_999.0);
        eco.put("presupuestoTotal", 11_500.0);
        eco.put("presupuestoKwp", 10.0);
        assertEquals(11_500, calcular(10, 0, eco).base.entrada().inversion(), 0.01);
    }
}
