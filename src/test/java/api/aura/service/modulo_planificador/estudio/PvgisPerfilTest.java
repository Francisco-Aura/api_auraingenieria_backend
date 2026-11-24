package api.aura.service.modulo_planificador.estudio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Lectura de la serie de PVGIS y uso de la producción real en el estudio. */
class PvgisPerfilTest {

    /** Respuesta de PVGIS de dos años: de 6 a 18 h UTC produce 500 W por kWp, el resto 0. */
    private static String respuesta() {
        StringBuilder b = new StringBuilder("{\"outputs\":{\"hourly\":[");
        boolean primero = true;
        for (int anio : new int[]{2018, 2019}) {
            java.time.LocalDateTime t = java.time.LocalDateTime.of(anio, 1, 1, 0, 10);
            while (t.getYear() == anio) {
                if (!primero) b.append(',');
                primero = false;
                double p = t.getHour() >= 6 && t.getHour() < 18 ? 500 : 0;
                b.append("{\"time\":\"").append(t.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd:HHmm"))).append("\",\"P\":").append(p).append("}");
                t = t.plusHours(1);
            }
        }
        return b.append("]}}").toString();
    }

    @Test
    void laSerieDePvgisSePasaAlAnoTipoEnHoraLocal() throws Exception {
        double[] v = Pvgis.anoTipo(new ObjectMapper().readTree(respuesta()));
        assertEquals(Curva.HORAS, v.length);
        double suma = 0;
        for (double x : v) suma += x;
        assertEquals(365 * 12 * 0.5, suma, 1e-6);
        // En enero (UTC+1) las 6 UTC son las 7 locales; en julio (UTC+2) son las 8 locales
        assertEquals(0, v[Curva.indice(0, 10, 6)], 1e-9);
        assertEquals(0.5, v[Curva.indice(0, 10, 7)], 1e-9);
        assertEquals(0.5, v[Curva.indice(6, 10, 8)], 1e-9);
        assertEquals(0, v[Curva.indice(6, 10, 7)], 1e-9);
    }

    @Test
    void laSerieSeGuardaYSeLeeSinPerderPrecision() {
        double[] v = new double[Curva.HORAS];
        for (int i = 0; i < v.length; i++) v[i] = i % 24 == 12 ? 0.8123 : 0;
        assertArrayEquals(v, Pvgis.desdeTexto(Pvgis.comoTexto(v)), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> Pvgis.desdeTexto("1,2,3"));
    }

    private static Map<String, Object> datos(String orientacion, double inclinacion, double azimut) {
        Map<String, Object> ins = new HashMap<>();
        ins.put("kwp", 100);
        ins.put("bateriaKwh", 0);
        ins.put("orientacion", orientacion);
        ins.put("inclinacion", inclinacion);
        ins.put("azimut", azimut);
        Map<String, Object> d = new HashMap<>();
        d.put("instalacion", ins);
        d.put("economia", Map.of("precioEnergia", 0.1));
        return d;
    }

    private static Curva consumoPlano() {
        double[] k = new double[Curva.HORAS];
        java.util.Arrays.fill(k, 30);
        return new Curva(k, false, "prueba");
    }

    @Test
    void conPvgisLaProduccionEsLaDeLaSerie() {
        double[] mediodia = new double[Curva.HORAS];
        for (int i = 0; i < mediodia.length; i++) mediodia[i] = Curva.hora(i) >= 10 && Curva.hora(i) < 14 ? 1.0 / 365 * 6 : 0; // 6 h-equivalentes por kWp y año... (1460 h)
        double anual = 0;
        for (double x : mediodia) anual += x;
        Calculo c = Calculo.de(datos("personalizada", 20, 40), consumoPlano(), null, new PerfilSolar(mediodia, null));
        assertTrue(c.conPvgis);
        assertEquals(anual, c.instalacion.produccionEspecifica(), 1e-9);
        assertEquals(anual * 100, c.base.sim().produccion(), 1e-6);
        assertEquals("PVGIS", ((Map<?, ?>) c.aMapa().get("solar")).get("fuente"));
        assertEquals("Azimut 40° (sureste), 20° de inclinación".replace("sureste", "suroeste"), c.orientacionTexto());
    }

    @Test
    @SuppressWarnings("unchecked")
    void lasGraficasTraenTodasLasSeriesQueDibujaLaPantalla() {
        Map<String, Object> d = datos("sur", 30, 0);
        d.put("tarifa", "3.0TD");
        ((Map<String, Object>) d.get("instalacion")).put("bateriaKwh", 100);
        Map<String, Object> eco = new HashMap<>(Map.of("precioEnergia", 0.1, "financiacionMeses", 36, "financiacionTae", 9.8, "reposicion", 5000, "reposicionAno", 13));
        d.put("economia", eco);
        d.put("potencias", java.util.List.of(80, 80, 80, 80, 80, 90));
        Map<String, Object> g = (Map<String, Object>) Calculo.de(d, consumoPlano()).aMapa().get("graficas");
        assertEquals(12, ((java.util.List<?>) g.get("consumoPeriodoMes")).size());
        assertEquals(6, ((java.util.List<?>) ((java.util.List<?>) g.get("consumoPeriodoMes")).get(0)).size());
        assertEquals(8, ((java.util.List<?>) g.get("diaTipo")).size());
        assertEquals(24, ((java.util.List<?>) g.get("perfilSabado")).size());
        assertEquals(9, ((java.util.List<?>) g.get("barridoPotencia")).size());
        java.util.List<Map<String, Object>> bat = (java.util.List<Map<String, Object>>) g.get("barridoBateria");
        assertTrue(bat.size() >= 6);
        assertEquals(1, bat.stream().filter(b -> Boolean.TRUE.equals(b.get("actual"))).count());
        assertEquals(5, ((java.util.List<?>) ((Map<String, Object>) g.get("escenariosVan")).get("items")).size());
        Map<String, Object> pot = (Map<String, Object>) g.get("potencia");
        assertEquals(6, pot.get("periodos"));
        assertEquals(80.0, ((java.util.List<Number>) pot.get("hoy")).get(0).doubleValue(), 1e-9);
        Map<String, Object> fin = (Map<String, Object>) g.get("financiacion");
        assertEquals(36, fin.get("meses"));
        assertTrue(((Number) fin.get("cuota")).doubleValue() > 0);
        assertEquals(13, g.get("reposicionAno"));
        double[] bal = ((java.util.List<Number>) fin.get("balanceConstante")).stream().mapToDouble(Number::doubleValue).toArray();
        assertEquals(0, bal[0], 1e-9);
        // La batería sube la cobertura: con 0 kWh se cubre menos que con la actual
        assertTrue(((Number) bat.get(0).get("cobertura")).doubleValue() <= ((Number) bat.get(bat.size() - 1).get("cobertura")).doubleValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinFinanciacionNoHayBalanceDeCuotas() {
        Map<String, Object> g = (Map<String, Object>) Calculo.de(datos("sur", 30, 0), consumoPlano()).aMapa().get("graficas");
        assertNull(g.get("financiacion"));
        assertNull(g.get("tarifa"));
        assertNull(g.get("consumoPeriodoMes"));
    }

    @Test
    void sinPerfilSeUsaElModeloYUnaOrientacionLibreAvisa() {
        Calculo c = Calculo.de(datos("personalizada", 20, 40), consumoPlano(), null, PerfilSolar.sinPerfil("Indica la ubicación"));
        assertFalse(c.conPvgis);
        assertEquals("sur", c.instalacion.orientacion());
        assertEquals("Indica la ubicación", c.avisos.get(0));
        Calculo ow = Calculo.de(datos("personalizada", 10, -85), consumoPlano(), null, PerfilSolar.sinPerfil(null));
        assertEquals("este-oeste", ow.instalacion.orientacion());
        assertEquals("este", Calculo.rumbo(-90));
        assertEquals("norte", Calculo.rumbo(180));
    }
}
