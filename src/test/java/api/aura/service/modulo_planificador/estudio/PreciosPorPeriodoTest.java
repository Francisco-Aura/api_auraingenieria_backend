package api.aura.service.modulo_planificador.estudio;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Periodos tarifarios del año tipo y valoración de la energía y la potencia con los precios del BOE. */
class PreciosPorPeriodoTest {

    // Peajes + cargos de 2026 de la 3.0TD (BOE-A-2025-26348 y BOE-A-2025-26705)
    private static final PreciosRegulados BOE_30TD_2026 = new PreciosRegulados("Peajes y cargos 2026", java.time.LocalDate.of(2026, 1, 1), "3.0TD",
            new double[]{0.063352, 0.038914, 0.019279, 0.009795, 0.004706, 0.002898},
            new double[]{20.376927, 10.617621, 4.481534, 3.886333, 2.513851, 1.442287}, "BOE-A-2025-26348, BOE-A-2025-26705", null);

    private static Curva laboral() {
        double[] k = new double[Curva.HORAS];
        for (int i = 0; i < Curva.HORAS; i++) k[i] = Curva.laborable(i) && Curva.hora(i) >= 8 && Curva.hora(i) < 18 ? 40 : 2;
        return new Curva(k, false, "prueba");
    }

    private static Map<String, Object> datos(Map<String, Object> eco) {
        Map<String, Object> d = new HashMap<>();
        d.put("tarifa", "3.0TD");
        d.put("instalacion", Map.of("kwp", 100, "bateriaKwh", 0, "orientacion", "sur"));
        d.put("economia", eco);
        return d;
    }

    @Test
    void reconoceLasTarifasComoVienenEnElSips() {
        assertEquals("3.0TD", Periodos.normalizar("3.0TD"));
        assertEquals("6.1TD", Periodos.normalizar("6.1 TD"));
        assertEquals("2.0TD", Periodos.normalizar("2,0td"));
        assertNull(Periodos.normalizar("3.1A"));
        assertNull(Periodos.normalizar(""));
        assertNull(Periodos.normalizar(null));
    }

    @Test
    void elCalendarioSigueLaCircularDeLaCnmc() {
        // Enero (temporada alta): punta P1, llano P2, noche P6
        int lunesEnero = Curva.indice(0, 13, 0); // 13/01 del año tipo es lunes (el año empieza en miércoles)
        assertTrue(Curva.laborable(lunesEnero));
        assertEquals(0, Periodos.periodo("3.0TD", lunesEnero + 10));
        assertEquals(1, Periodos.periodo("3.0TD", lunesEnero + 8));
        assertEquals(1, Periodos.periodo("3.0TD", lunesEnero + 15));
        assertEquals(5, Periodos.periodo("3.0TD", lunesEnero + 3));
        // Abril (temporada baja): punta P4 y llano P5; junio (media): P3 y P4; marzo (media alta): P2 y P3
        int lunesAbril = Curva.indice(3, 14, 0);
        assertEquals(3, Periodos.periodo("3.0TD", lunesAbril + 12));
        assertEquals(4, Periodos.periodo("3.0TD", lunesAbril + 16));
        assertEquals(2, Periodos.periodo("6.1TD", Curva.indice(5, 16, 12)));
        assertEquals(1, Periodos.periodo("6.1TD", Curva.indice(2, 17, 12)));
        // Fin de semana y festivo nacional: P6 todo el día (P3 en la 2.0TD)
        int sabado = Curva.indice(0, 4, 12);
        assertFalse(Curva.laborable(sabado));
        assertEquals(5, Periodos.periodo("3.0TD", sabado));
        assertEquals(5, Periodos.periodo("3.0TD", Curva.indice(4, 1, 12)), "1 de mayo");
        assertEquals(2, Periodos.periodo("2.0TD", sabado));
        // 2.0TD en laborable: punta de 10 a 14, llano de 8 a 10
        assertEquals(0, Periodos.periodo("2.0TD", lunesEnero + 11));
        assertEquals(1, Periodos.periodo("2.0TD", lunesEnero + 9));
        assertEquals(2, Periodos.periodo("2.0TD", lunesEnero + 2));
    }

    @Test
    void laEnergiaSeValoraConElPrecioDeSuPeriodoMasPeajesYCargos() {
        Map<String, Object> eco = new HashMap<>();
        eco.put("precioEnergia", 0.10);
        eco.put("modoPrecio", "periodos");
        eco.put("preciosPeriodo", List.of(0.09, 0.085, 0.08, 0.075, 0.07, 0.065));
        Calculo c = Calculo.de(datos(eco), laboral(), BOE_30TD_2026);
        assertTrue(c.avisos.isEmpty(), c.avisos.toString());
        assertEquals(0.09 + 0.063352, c.precioPeriodo[0], 1e-9);
        double[] sol = c.base.sim().solarPeriodo();
        double suma = 0, valor = 0;
        for (int p = 0; p < 6; p++) {
            suma += sol[p];
            valor += sol[p] * c.precioPeriodo[p];
        }
        assertEquals(c.base.sim().solarConsumida(), suma, 1e-6);
        assertEquals(valor / suma, c.precioEnergia, 1e-9);
        assertTrue(c.precioEnergia > c.precioPeriodo[5] && c.precioEnergia < c.precioPeriodo[0]);
        assertEquals("periodos", ((Map<?, ?>) c.aMapa().get("precios")).get("modo"));

        // Con los peajes ya incluidos en el precio, se toma tal cual
        eco.put("incluyePeajes", true);
        Calculo incluidos = Calculo.de(datos(eco), laboral(), BOE_30TD_2026);
        assertEquals(0.09, incluidos.precioPeriodo[0], 1e-9);
        assertTrue(incluidos.precioEnergia < c.precioEnergia);
    }

    @Test
    void siFaltaAlgoSeUsaElPrecioMedioConUnAviso() {
        Map<String, Object> eco = new HashMap<>();
        eco.put("precioEnergia", 0.12);
        eco.put("modoPrecio", "periodos");
        eco.put("preciosPeriodo", Arrays.asList(0.09, null, 0.08, 0.075, 0.07, 0.065));
        Calculo c = Calculo.de(datos(eco), laboral(), BOE_30TD_2026);
        assertNull(c.precioPeriodo);
        assertEquals(0.12, c.precioEnergia, 1e-9);
        assertTrue(c.avisos.get(0).contains("P2"));

        eco.put("preciosPeriodo", List.of(0.09, 0.085, 0.08, 0.075, 0.07, 0.065));
        Calculo sinBoe = Calculo.de(datos(eco), laboral(), null);
        assertNull(sinBoe.precioPeriodo);
        assertTrue(sinBoe.avisos.get(0).contains("No hay peajes y cargos"));
    }

    @Test
    void laReduccionDePotenciaSeValoraAPrecioBoe() {
        Map<String, Object> eco = new HashMap<>();
        eco.put("precioEnergia", 0.12);
        eco.put("ahorroPotencia", 999);
        eco.put("reduccionPotencia", List.of(10, 10, 0, 0, 0, 5));
        Calculo c = Calculo.de(datos(eco), laboral(), BOE_30TD_2026);
        assertTrue(c.potenciaCalculada);
        assertEquals(10 * 20.376927 + 10 * 10.617621 + 5 * 1.442287, c.ahorroPotencia, 1e-6);
        assertEquals(c.ahorroPotencia, c.base.fin().ahorro()[0] - c.base.sim().solarConsumida() * c.precioEnergia, 1e-6);

        // Sin precios del BOE se queda el ahorro que se haya escrito
        Calculo sin = Calculo.de(datos(eco), laboral(), null);
        assertFalse(sin.potenciaCalculada);
        assertEquals(999, sin.ahorroPotencia, 1e-9);
    }
}
