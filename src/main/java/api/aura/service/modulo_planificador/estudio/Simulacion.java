package api.aura.service.modulo_planificador.estudio;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simulación hora a hora de una instalación fotovoltaica sin vertido a red (antivertido), con batería opcional, sobre la curva de consumo.
 * La producción sale de una producción específica anual (kWh por kWp) repartida por meses y horas con un perfil típico del sur de España;
 * no usa datos meteorológicos diarios, así que cada día de un mes produce lo mismo (día medio). La batería guarda el sobrante y lo
 * devuelve cuando falta sol, con el rendimiento de ida y vuelta y la descarga útil de los parámetros.
 */
public final class Simulacion {

    /** Datos de la instalación. */
    public record Instalacion(double kwp, double bateriaKwh, double produccionEspecifica, String orientacion,
                              double rendimientoBateria, double descargaUtil, double cRate, double[] perfil) {
        public Instalacion {
            if (kwp < 0 || bateriaKwh < 0 || produccionEspecifica <= 0) throw new IllegalArgumentException("Instalación no válida");
        }

        public Instalacion(double kwp, double bateriaKwh, double produccionEspecifica, String orientacion,
                           double rendimientoBateria, double descargaUtil, double cRate) {
            this(kwp, bateriaKwh, produccionEspecifica, orientacion, rendimientoBateria, descargaUtil, cRate, null);
        }

        public static double produccionPorDefecto(String orientacion) {
            // De los informes de referencia en Lorca: sur 30° y este-oeste 15° (el cálculo actual usa 15° y 10°; estos valores son los del modelo sin PVGIS)
            return "sur".equals(orientacion) ? 1685 : 1420;
        }

        public static Instalacion de(double kwp, double bateriaKwh, String orientacion, Double produccion) {
            String o = "sur".equals(orientacion) ? "sur" : "este-oeste";
            return new Instalacion(kwp, bateriaKwh, produccion == null || produccion <= 0 ? produccionPorDefecto(o) : produccion, o, 0.90, 0.90, 0.5);
        }

        /**
         * Con la producción hora a hora real de 1 kWp (PVGIS). Si no se fija la producción específica es la suma de esa serie; si se
         * fija, la serie se escala a ella.
         */
        public static Instalacion conPerfil(double kwp, double bateriaKwh, String orientacion, Double produccion, double[] perfil) {
            double anual = 0;
            for (double v : perfil) anual += v;
            return new Instalacion(kwp, bateriaKwh, produccion == null || produccion <= 0 ? anual : produccion, "sur".equals(orientacion) ? "sur" : "este-oeste",
                    0.90, 0.90, 0.5, perfil);
        }

        public Instalacion conBateria(double nueva) {
            return new Instalacion(kwp, nueva, produccionEspecifica, orientacion, rendimientoBateria, descargaUtil, cRate, perfil);
        }

        public Instalacion conKwp(double nuevos) {
            return new Instalacion(nuevos, bateriaKwh, produccionEspecifica, orientacion, rendimientoBateria, descargaUtil, cRate, perfil);
        }
    }

    /** Resultado de un año simulado. Las energías en kWh y las potencias en kW. */
    public record Resultado(double kwp, double bateriaKwh, double consumo, double produccion, double directa, double desdeBateria,
                            double recortada, double ciclos, double[] consumoMes, double[] solarMes, double[] recortadaMes,
                            double[] produccionMes, double[][] diaTipo, double produccionMaxima, double[] solarPeriodo, double[] bateriaMes) {
        public double solarConsumida() {
            return directa + desdeBateria;
        }

        public double cobertura() {
            return consumo <= 0 ? 0 : solarConsumida() / consumo;
        }

        public double aprovechamiento() {
            return produccion <= 0 ? 0 : solarConsumida() / produccion;
        }
    }

    private static final double[] REPARTO_MENSUAL = {5.4, 6.5, 8.6, 9.5, 10.7, 11.2, 11.8, 10.9, 9.0, 7.3, 5.4, 4.7};
    private static final double[] DURACION_DIA = {9.9, 10.8, 11.9, 13.1, 14.1, 14.6, 14.3, 13.4, 12.3, 11.1, 10.1, 9.6};
    private static final double[] MEDIODIA = {13.2, 13.3, 13.2, 14.1, 14.0, 14.1, 14.2, 14.1, 14.0, 13.9, 13.0, 13.1};

    private Simulacion() {
    }

    /** Producción de un kWp, hora a hora, de un año tipo: suma la producción específica. */
    public static double[] produccionPorKwp(double produccionEspecifica, String orientacion) {
        double exponente = "sur".equals(orientacion) ? 1.3 : 0.9;
        double suma = 0;
        for (double d : REPARTO_MENSUAL) suma += d;
        double[] p = new double[Curva.HORAS];
        for (int m = 0; m < 12; m++) {
            double energiaDia = produccionEspecifica * REPARTO_MENSUAL[m] / suma / Curva.diasDelMes(m);
            double[] peso = new double[24];
            double total = 0;
            for (int h = 0; h < 24; h++) {
                double x = (h + 0.5 - MEDIODIA[m]) / (DURACION_DIA[m] / 2.0);
                peso[h] = Math.abs(x) >= 1 ? 0 : Math.pow(Math.cos(Math.PI * x / 2.0), exponente);
                total += peso[h];
            }
            for (int d = 1; d <= Curva.diasDelMes(m); d++) {
                for (int h = 0; h < 24; h++) p[Curva.indice(m, d, h)] = energiaDia * peso[h] / total;
            }
        }
        return p;
    }

    /** La serie de 1 kWp escalada para que sume la producción específica indicada. */
    static double[] escalar(double[] perfil, double anual) {
        double suma = 0;
        for (double v : perfil) suma += v;
        double f = suma <= 0 ? 0 : anual / suma;
        double[] r = new double[perfil.length];
        for (int i = 0; i < r.length; i++) r[i] = perfil[i] * f;
        return r;
    }

    public static Resultado simular(Curva curva, Instalacion ins) {
        return simular(curva, ins, null);
    }

    /** Con {@code periodos} (el periodo tarifario 0-5 de cada hora) reparte además la solar consumida por periodos. */
    public static Resultado simular(Curva curva, Instalacion ins, int[] periodos) {
        double[] c = curva.kwh();
        double[] porKwp = ins.perfil() == null ? produccionPorKwp(ins.produccionEspecifica(), ins.orientacion()) : escalar(ins.perfil(), ins.produccionEspecifica());
        double usable = ins.bateriaKwh() * ins.descargaUtil();
        double eta = Math.sqrt(ins.rendimientoBateria());
        double potencia = ins.bateriaKwh() * ins.cRate();
        double soc = 0, directa = 0, bateria = 0, recortada = 0, produccion = 0, descargado = 0, maxProd = 0;
        double[] consumoMes = new double[12], solarMes = new double[12], recortadaMes = new double[12], produccionMes = new double[12], bateriaMes = new double[12];
        double[] solarPeriodo = periodos == null ? null : new double[6];
        // Día tipo (laborable medio) de cada mes: consumo, producción y solar consumida por hora
        double[][][] dia = new double[12][4][24];
        int[] diasLab = new int[12];
        for (int i = 0; i < Curva.HORAS; i++) {
            int m = Curva.mes(i), h = Curva.hora(i);
            double pv = ins.kwp() * porKwp[i];
            double carga = c[i];
            double d = Math.min(pv, carga);
            double sobra = pv - d, falta = carga - d;
            double aportada = 0, cargada = 0;
            if (ins.bateriaKwh() > 0) {
                if (falta > 0) {
                    aportada = Math.min(Math.min(falta, potencia), soc * eta);
                    soc -= aportada / eta;
                    descargado += aportada / eta;
                } else if (sobra > 0) {
                    cargada = Math.min(Math.min(sobra, potencia), (usable - soc) / eta);
                    soc += cargada * eta;
                }
            }
            directa += d;
            bateria += aportada;
            double rec = sobra - cargada;
            recortada += rec;
            produccion += pv;
            maxProd = Math.max(maxProd, pv);
            consumoMes[m] += carga;
            solarMes[m] += d + aportada;
            bateriaMes[m] += aportada;
            if (solarPeriodo != null) solarPeriodo[periodos[i]] += d + aportada;
            recortadaMes[m] += rec;
            produccionMes[m] += pv;
            if (Curva.laborable(i)) {
                dia[m][0][h] += carga;
                dia[m][1][h] += pv;
                dia[m][2][h] += d + aportada;
                dia[m][3][h] += aportada;
                if (h == 0) diasLab[m]++;
            }
        }
        // Se guardan junio (índice 5) y diciembre (índice 11): 4 series de 24 horas cada uno (consumo, producción, solar consumida y la parte que sale de la batería)
        int[] meses = {5, 11};
        double[][] diaTipo = new double[8][24];
        for (int k = 0; k < 2; k++) {
            int m = meses[k];
            for (int s = 0; s < 4; s++) {
                for (int h = 0; h < 24; h++) diaTipo[k * 4 + s][h] = diasLab[m] == 0 ? 0 : dia[m][s][h] / diasLab[m];
            }
        }
        double ciclos = usable <= 0 ? 0 : descargado / usable;
        return new Resultado(ins.kwp(), ins.bateriaKwh(), curva.total(), produccion, directa, bateria, recortada, ciclos,
                consumoMes, solarMes, recortadaMes, produccionMes, diaTipo, maxProd, solarPeriodo, bateriaMes);
    }

    public static Map<String, Object> aMapa(Resultado r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kwp", r.kwp());
        m.put("bateriaKwh", r.bateriaKwh());
        m.put("consumo", r.consumo());
        m.put("produccion", r.produccion());
        m.put("directa", r.directa());
        m.put("desdeBateria", r.desdeBateria());
        m.put("solarConsumida", r.solarConsumida());
        m.put("recortada", r.recortada());
        m.put("cobertura", r.cobertura());
        m.put("aprovechamiento", r.aprovechamiento());
        m.put("ciclos", r.ciclos());
        m.put("produccionMaxima", r.produccionMaxima());
        return m;
    }
}
