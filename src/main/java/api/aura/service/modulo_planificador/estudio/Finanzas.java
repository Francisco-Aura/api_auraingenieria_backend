package api.aura.service.modulo_planificador.estudio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rentabilidad de una instalación a 25 años: el ahorro del primer año sube con el precio de la energía y baja con la degradación de los
 * módulos (y de la batería); el mantenimiento sube cada año. Los flujos son de fin de año. Calibrado con los informes de referencia:
 * con los datos del informe de Eiffage (850.264 € de inversión) sale retorno de 10,3 años, retorno descontado de 14,1, VAN de 495.000 € y
 * TIR del 9,6 %.
 */
public final class Finanzas {

    /** Datos económicos. Importes sin IVA. */
    public record Entrada(double inversion, double kwhDirecta, double kwhBateria, double precioKwh, double mantenimiento,
                          double ahorroPotencia, double subidaEnergia, double degradacion, double degradacionBateria,
                          double subidaMantenimiento, double descuento, int anos, int anoReposicion, double reposicion) {
        public static Entrada conValoresPorDefecto(double inversion, double kwhDirecta, double kwhBateria, double precioKwh,
                                                   double mantenimiento, double ahorroPotencia) {
            return new Entrada(inversion, kwhDirecta, kwhBateria, precioKwh, mantenimiento, ahorroPotencia,
                    0.03, 0.004, kwhBateria > 0 ? 0.02 : 0, 0.02, 0.05, 25, 13, 0);
        }

        public Entrada conPrecio(double nuevo) {
            return new Entrada(inversion, kwhDirecta, kwhBateria, nuevo, mantenimiento, ahorroPotencia, subidaEnergia, degradacion,
                    degradacionBateria, subidaMantenimiento, descuento, anos, anoReposicion, reposicion);
        }
    }

    /** Resultado: los flujos van del año 1 al último; el acumulado empieza en el año 0 con la inversión en negativo. */
    public record Resultado(double[] ahorro, double[] mantenimiento, double[] neto, double[] acumulado, double retorno,
                            double retornoDescontado, double van, double tir, double ahorroAcumulado, double ahorroPrimerAno) {
    }

    private Finanzas() {
    }

    public static Resultado calcular(Entrada e) {
        int n = e.anos();
        double[] ahorro = new double[n], mant = new double[n], neto = new double[n], acum = new double[n + 1];
        acum[0] = -e.inversion();
        for (int t = 1; t <= n; t++) {
            double crecer = Math.pow(1 + e.subidaEnergia(), t - 1);
            double directa = e.kwhDirecta() * Math.pow(1 - e.degradacion(), t - 1) * e.precioKwh() * crecer;
            double bateria = e.kwhBateria() * Math.pow(1 - e.degradacion(), t - 1) * Math.pow(1 - e.degradacionBateria(), t - 1) * e.precioKwh() * crecer;
            ahorro[t - 1] = directa + bateria + e.ahorroPotencia();
            mant[t - 1] = e.mantenimiento() * Math.pow(1 + e.subidaMantenimiento(), t - 1)
                    + (e.reposicion() > 0 && t == e.anoReposicion() ? e.reposicion() : 0);
            neto[t - 1] = ahorro[t - 1] - mant[t - 1];
            acum[t] = acum[t - 1] + neto[t - 1];
        }
        double retorno = cruce(e.inversion(), neto, 0), retornoDesc = cruce(e.inversion(), neto, e.descuento());
        double van = -e.inversion();
        double suma = 0;
        for (int t = 1; t <= n; t++) {
            van += neto[t - 1] / Math.pow(1 + e.descuento(), t);
            suma += neto[t - 1];
        }
        return new Resultado(ahorro, mant, neto, acum, retorno, retornoDesc, van, tir(e.inversion(), neto), suma, neto.length == 0 ? 0 : neto[0]);
    }

    /** Año (con decimales) en que lo acumulado supera la inversión; NaN si nunca. */
    private static double cruce(double inversion, double[] neto, double descuento) {
        double acum = -inversion;
        for (int t = 1; t <= neto.length; t++) {
            double flujo = neto[t - 1] / Math.pow(1 + descuento, t);
            double antes = acum;
            acum += flujo;
            if (acum >= 0 && flujo > 0) return t - 1 + (-antes) / flujo;
        }
        return Double.NaN;
    }

    private static double tir(double inversion, double[] neto) {
        double lo = -0.9, hi = 5;
        if (van(inversion, neto, lo) < 0) return Double.NaN;
        if (van(inversion, neto, hi) > 0) return Double.NaN;
        for (int i = 0; i < 120; i++) {
            double mid = (lo + hi) / 2;
            if (van(inversion, neto, mid) > 0) lo = mid;
            else hi = mid;
        }
        return (lo + hi) / 2;
    }

    private static double van(double inversion, double[] neto, double tasa) {
        double v = -inversion;
        for (int t = 1; t <= neto.length; t++) v += neto[t - 1] / Math.pow(1 + tasa, t);
        return v;
    }

    public static Map<String, Object> aMapa(Entrada e, Resultado r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inversion", e.inversion());
        m.put("ahorroPrimerAno", r.ahorroPrimerAno());
        // Solo la energía que deja de comprar el primer año (sin potencia ni mantenimiento)
        m.put("ahorroEnergiaPrimerAno", (e.kwhDirecta() + e.kwhBateria()) * e.precioKwh());
        m.put("retorno", nulo(r.retorno()));
        m.put("retornoDescontado", nulo(r.retornoDescontado()));
        m.put("van", r.van());
        m.put("tir", nulo(r.tir()));
        m.put("ahorroAcumulado", r.ahorroAcumulado());
        List<Double> acum = new ArrayList<>();
        for (double v : r.acumulado()) acum.add(v);
        m.put("acumulado", acum);
        return m;
    }

    private static Double nulo(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
    }
}
