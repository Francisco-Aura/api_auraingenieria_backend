package api.aura.service.modulo_planificador.estudio;

import api.aura.config.ErrorApi;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Curva horaria estimada cuando no hay la de la distribuidora: reparte el consumo de las lecturas mensuales del SIPS según un horario de
 * funcionamiento (horas de trabajo, parte que se consume fuera de ellas y en fin de semana). Sirve para un primer estudio, pero no
 * sustituye a la curva real: la potencia máxima y el aprovechamiento solar salen más finos con datos medidos.
 */
public final class CurvaEstimada {

    /** Franja de plena carga: de {@code ini} a {@code fin} (sin incluirla). */
    public record Tramo(int ini, int fin) {
    }

    /** Jornada distinta en algunos meses (1 = enero … 12 = diciembre): sustituye al horario base en esos meses. {@code tramos} son franjas extra (interrupciones). */
    public record Excepcion(List<Integer> meses, int horaIni, int horaFin, double fraccionFuera, double fraccionFinde, List<Tramo> tramos) {
        public Excepcion(List<Integer> meses, int horaIni, int horaFin, double fraccionFuera, double fraccionFinde) {
            this(meses, horaIni, horaFin, fraccionFuera, fraccionFinde, List.of());
        }
    }

    /**
     * Horario de funcionamiento: de {@code horaIni} a {@code horaFin} (sin incluirla) a plena carga, más las franjas extra de {@code tramos}
     * (una jornada partida o con interrupciones), con las jornadas distintas de algunos meses.
     */
    public record Horario(int horaIni, int horaFin, double fraccionFuera, double fraccionFinde, List<Excepcion> excepciones, List<Tramo> tramos) {
        public Horario(int horaIni, int horaFin, double fraccionFuera, double fraccionFinde, List<Excepcion> excepciones) {
            this(horaIni, horaFin, fraccionFuera, fraccionFinde, excepciones, List.of());
        }

        public Horario(int horaIni, int horaFin, double fraccionFuera, double fraccionFinde) {
            this(horaIni, horaFin, fraccionFuera, fraccionFinde, List.of(), List.of());
        }

        public static Horario porDefecto() {
            return new Horario(7, 19, 0.15, 0.10);
        }

        void validar() {
            validar(horaIni, horaFin, fraccionFuera, fraccionFinde, tramos);
            List<Integer> usados = new ArrayList<>();
            for (Excepcion x : excepciones) {
                validar(x.horaIni(), x.horaFin(), x.fraccionFuera(), x.fraccionFinde(), x.tramos());
                if (x.meses().isEmpty()) throw new ErrorApi(HttpStatus.BAD_REQUEST, "Elige a qué meses se aplica cada jornada distinta.");
                for (int m : x.meses()) {
                    if (m < 1 || m > 12) throw new ErrorApi(HttpStatus.BAD_REQUEST, "Los meses van de 1 a 12.");
                    if (usados.contains(m)) throw new ErrorApi(HttpStatus.BAD_REQUEST, "Un mes no puede tener dos jornadas distintas.");
                    usados.add(m);
                }
            }
        }

        private static void validar(int horaIni, int horaFin, double fraccionFuera, double fraccionFinde, List<Tramo> tramos) {
            if (horaIni < 0 || horaIni > 23 || horaFin < 1 || horaFin > 24 || horaFin <= horaIni) {
                throw new ErrorApi(HttpStatus.BAD_REQUEST, "El horario de funcionamiento no es válido (de 0 a 24 h, la salida después de la entrada).");
            }
            if (fraccionFuera < 0 || fraccionFuera > 1 || fraccionFinde < 0 || fraccionFinde > 1) {
                throw new ErrorApi(HttpStatus.BAD_REQUEST, "Las fracciones de consumo fuera de horario y en fin de semana van de 0 a 1.");
            }
            List<Tramo> todos = new ArrayList<>(List.of(new Tramo(horaIni, horaFin)));
            for (Tramo t : tramos) {
                if (t.ini() < 0 || t.ini() > 23 || t.fin() < 1 || t.fin() > 24 || t.fin() <= t.ini()) {
                    throw new ErrorApi(HttpStatus.BAD_REQUEST, "Una franja del horario no es válida (de 0 a 24 h, la salida después de la entrada).");
                }
                for (Tramo o : todos) {
                    if (t.ini() < o.fin() && o.ini() < t.fin()) throw new ErrorApi(HttpStatus.BAD_REQUEST, "Las franjas del horario no pueden solaparse.");
                }
                todos.add(t);
            }
        }

        private static boolean dentro(int h, int ini, int fin, List<Tramo> tramos) {
            if (h >= ini && h < fin) return true;
            for (Tramo t : tramos) if (h >= t.ini() && h < t.fin()) return true;
            return false;
        }

        double peso(int indice) {
            int h = Curva.hora(indice);
            int mes = Curva.mes(indice) + 1;
            int ini = horaIni, fin = horaFin;
            List<Tramo> extra = tramos;
            double fuera = fraccionFuera, finde = fraccionFinde;
            for (Excepcion x : excepciones) {
                if (x.meses().contains(mes)) {
                    ini = x.horaIni();
                    fin = x.horaFin();
                    extra = x.tramos();
                    fuera = x.fraccionFuera();
                    finde = x.fraccionFinde();
                    break;
                }
            }
            double d = Curva.laborable(indice) ? 1.0 : finde;
            return d * (dentro(h, ini, fin, extra) ? 1.0 : fuera);
        }
    }

    private CurvaEstimada() {
    }

    /** Sin conocer la tarifa: el consumo de cada lectura se reparte entre sus horas solo con el horario de funcionamiento. */
    public static Curva crear(List<Sips.Lectura> lecturas, double kwhAnual, Horario h) {
        return crear(lecturas, kwhAnual, h, null);
    }

    /**
     * Con la tarifa de acceso (2.0TD, 3.0TD, 6.1TD), el consumo de cada periodo del SIPS (P1 a P6) se reparte solo entre las horas de esas
     * fechas que son de ese periodo, así que cada periodo cuadra con lo medido. El horario de funcionamiento solo decide la forma
     * dentro de cada periodo. Sin tarifa (null) se usa el reparto solo por horario.
     */
    public static Curva crear(List<Sips.Lectura> lecturas, double kwhAnual, Horario h, String tarifa) {
        h.validar();
        int[] periodo = tarifa == null ? null : Periodos.delAno(tarifa);
        double[] k = new double[Curva.HORAS];
        boolean[] cubierta = new boolean[Curva.HORAS];
        int cubiertas = 0;
        if (!lecturas.isEmpty()) {
            LocalDate fin = lecturas.get(lecturas.size() - 1).hasta();
            LocalDate corte = fin.minusDays(365);
            for (Sips.Lectura l : lecturas) {
                if (!l.hasta().isAfter(corte)) continue;
                List<Integer> horas = new ArrayList<>();
                double peso = 0;
                for (LocalDate d = l.desde(); d.isBefore(l.hasta()); d = d.plusDays(1)) {
                    if (d.isBefore(corte) || (d.getMonthValue() == 2 && d.getDayOfMonth() == 29)) continue;
                    for (int hora = 0; hora < 24; hora++) {
                        int idx = Curva.indice(d.getMonthValue() - 1, d.getDayOfMonth(), hora);
                        if (cubierta[idx]) continue;
                        horas.add(idx);
                        peso += h.peso(idx);
                    }
                }
                if (peso <= 0) continue;
                if (periodo != null) {
                    repartirPorPeriodos(l, horas, h, periodo, Periodos.periodosEnergia(tarifa), k);
                } else {
                    double e = l.total();
                    for (int idx : horas) k[idx] = e * h.peso(idx) / peso;
                }
                for (int idx : horas) {
                    cubierta[idx] = true;
                    cubiertas++;
                }
            }
            if (cubiertas < Curva.HORAS * 0.9) {
                throw new ErrorApi(HttpStatus.BAD_REQUEST, "Las lecturas del SIPS solo cubren " + Math.round(100.0 * cubiertas / Curva.HORAS)
                        + " % del año: faltan meses para estimar la curva. Sube la curva real de la distribuidora.");
            }
            for (int i = 0; i < Curva.HORAS; i++) {
                if (!cubierta[i]) k[i] = media(k, cubierta, Curva.hora(i), Curva.mes(i));
            }
        } else if (kwhAnual > 0) {
            double peso = 0;
            for (int i = 0; i < Curva.HORAS; i++) peso += h.peso(i);
            for (int i = 0; i < Curva.HORAS; i++) k[i] = kwhAnual * h.peso(i) / peso;
        } else {
            throw new ErrorApi(HttpStatus.BAD_REQUEST, "Sin lecturas ni consumo anual no se puede estimar la curva. Sube el SIPS o la curva de la distribuidora.");
        }
        return new Curva(k, true, "Estimada a partir del SIPS");
    }

    /**
     * Reparte los kWh de cada periodo de la lectura entre las horas de ese periodo, en proporción al peso del horario (a partes iguales si
     * ese peso es 0). Lo de un periodo que no tiene ninguna hora en las fechas de la lectura se reparte entre todas con el horario.
     */
    private static void repartirPorPeriodos(Sips.Lectura l, List<Integer> horas, Horario h, int[] periodo, int n, double[] k) {
        double[] pesoPeriodo = new double[n];
        int[] cuenta = new int[n];
        double pesoTotal = 0;
        for (int idx : horas) {
            int p = Math.min(periodo[idx], n - 1);
            pesoPeriodo[p] += h.peso(idx);
            cuenta[p]++;
            pesoTotal += h.peso(idx);
        }
        double[] kwh = new double[n];
        double resto = 0;
        for (int p = 0; p < l.kwh().length; p++) {
            double e = Math.max(0, l.kwh()[p]);
            if (p < n && cuenta[p] > 0) kwh[p] = e;
            else resto += e;
        }
        for (int idx : horas) {
            int p = Math.min(periodo[idx], n - 1);
            double parte = kwh[p] > 0 ? kwh[p] * (pesoPeriodo[p] > 0 ? h.peso(idx) / pesoPeriodo[p] : 1.0 / cuenta[p]) : 0;
            k[idx] = parte + (resto > 0 && pesoTotal > 0 ? resto * h.peso(idx) / pesoTotal : 0);
        }
    }

    /** Diferencia (en puntos de porcentaje, de 0 a 1) a partir de la cual el reparto por periodos se considera distinto. */
    static final double UMBRAL_AVISO = 0.10;

    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    /**
     * Compara, lectura a lectura (mes a mes), el reparto del consumo por periodos tarifarios que dice el SIPS (dato medido) con el de
     * la curva estimada en esos mismos días. Si en algún mes difieren 10 puntos o más, devuelve un aviso con los meses: el horario
     * supuesto no refleja cuándo consume el cliente y la cobertura solar puede salir mal. Null si encaja o si no hay datos.
     */
    public static String avisoPeriodos(Curva estimada, List<Sips.Lectura> lecturas, String tarifa) {
        if (lecturas.isEmpty()) return null;
        int n = "2.0TD".equals(tarifa) ? 3 : 6;
        int[] periodo = Periodos.delAno(tarifa);
        double[] kwh = estimada.kwh();
        LocalDate corte = lecturas.get(lecturas.size() - 1).hasta().minusDays(365);
        List<String> meses = new ArrayList<>();
        String ejemplo = null;
        double mayorGlobal = 0;
        for (Sips.Lectura l : lecturas) {
            if (!l.hasta().isAfter(corte)) continue;
            double[] sips = new double[n];
            double totalSips = 0;
            for (int p = 0; p < Math.min(n, l.kwh().length); p++) {
                sips[p] = Math.max(0, l.kwh()[p]);
                totalSips += sips[p];
            }
            double[] est = new double[n];
            double totalEst = 0;
            for (LocalDate d = l.desde(); d.isBefore(l.hasta()); d = d.plusDays(1)) {
                if (d.isBefore(corte) || (d.getMonthValue() == 2 && d.getDayOfMonth() == 29)) continue;
                for (int h = 0; h < 24; h++) {
                    int idx = Curva.indice(d.getMonthValue() - 1, d.getDayOfMonth(), h);
                    est[Math.min(periodo[idx], n - 1)] += kwh[idx];
                    totalEst += kwh[idx];
                }
            }
            if (totalSips <= 0 || totalEst <= 0) continue;
            int peor = 0;
            double mayor = 0;
            for (int p = 0; p < n; p++) {
                double dif = Math.abs(sips[p] / totalSips - est[p] / totalEst);
                if (dif > mayor) {
                    mayor = dif;
                    peor = p;
                }
            }
            if (mayor < UMBRAL_AVISO) continue;
            // El mes que da nombre a la lectura es el que tiene más días en ella
            LocalDate mitad = l.desde().plusDays(java.time.temporal.ChronoUnit.DAYS.between(l.desde(), l.hasta()) / 2);
            String mes = MESES[mitad.getMonthValue() - 1];
            if (!meses.contains(mes)) meses.add(mes);
            if (mayor > mayorGlobal) {
                mayorGlobal = mayor;
                ejemplo = mes + ", P" + (peor + 1) + ": " + Math.round(sips[peor] / totalSips * 100) + " % según el SIPS frente a "
                        + Math.round(est[peor] / totalEst * 100) + " % estimado";
            }
        }
        if (meses.isEmpty()) return null;
        String cuales = meses.size() <= 4 ? String.join(", ", meses) : meses.size() + " meses (" + String.join(", ", meses.subList(0, 4)) + "…)";
        return "En " + cuales + " el SIPS reparte el consumo por periodos de forma distinta a la curva estimada (el mayor desvío, en " + ejemplo
                + "). El horario de funcionamiento supuesto puede no reflejar cuándo consume el cliente en esos meses, y la cobertura solar puede no ser fiable."
                + " Ajusta el horario o sube la curva real de la distribuidora.";
    }

    private static double media(double[] k, boolean[] c, int hora, int mes) {
        double s = 0;
        int n = 0;
        for (int i = 0; i < Curva.HORAS; i++) {
            if (c[i] && Curva.hora(i) == hora && Curva.mes(i) == mes) {
                s += k[i];
                n++;
            }
        }
        return n == 0 ? 0 : s / n;
    }
}
