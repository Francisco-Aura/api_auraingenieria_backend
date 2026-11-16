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

    /** Horario de funcionamiento: de {@code horaIni} a {@code horaFin} (sin incluirla) a plena carga. */
    public record Horario(int horaIni, int horaFin, double fraccionFuera, double fraccionFinde) {
        public static Horario porDefecto() {
            return new Horario(7, 19, 0.15, 0.10);
        }

        void validar() {
            if (horaIni < 0 || horaIni > 23 || horaFin < 1 || horaFin > 24 || horaFin <= horaIni) {
                throw new ErrorApi(HttpStatus.BAD_REQUEST, "El horario de funcionamiento no es válido (de 0 a 24 h, la salida después de la entrada).");
            }
            if (fraccionFuera < 0 || fraccionFuera > 1 || fraccionFinde < 0 || fraccionFinde > 1) {
                throw new ErrorApi(HttpStatus.BAD_REQUEST, "Las fracciones de consumo fuera de horario y en fin de semana van de 0 a 1.");
            }
        }

        double peso(int indice) {
            int h = Curva.hora(indice);
            double d = Curva.laborable(indice) ? 1.0 : fraccionFinde;
            return d * (h >= horaIni && h < horaFin ? 1.0 : fraccionFuera);
        }
    }

    private CurvaEstimada() {
    }

    public static Curva crear(List<Sips.Lectura> lecturas, double kwhAnual, Horario h) {
        h.validar();
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
                double e = l.total();
                for (int idx : horas) {
                    k[idx] = e * h.peso(idx) / peso;
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
