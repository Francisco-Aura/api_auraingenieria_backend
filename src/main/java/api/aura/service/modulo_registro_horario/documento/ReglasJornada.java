package api.aura.service.modulo_registro_horario.documento;

import api.aura.service.modulo_registro_horario.documento.DatosRegistro.HorasDia;
import api.aura.service.modulo_registro_horario.documento.DatosRegistro.Tramo;

import java.time.LocalDate;
import java.util.List;

/** Qué se trabaja cada día y cómo se cuentan las horas. Mismas reglas que el Registro Horario anterior. */
public final class ReglasJornada {

    public static final String[] MESES = {"ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO", "AGOSTO",
            "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"};

    /** Lo que se escribe un día: los tramos trabajados (0, 1 o 2) o, si no se trabaja, una nota («FESTIVO»...) o {@code null}. */
    public record Jornada(List<Tramo> tramos, String nota) {
    }

    private ReglasJornada() {
    }

    public static int aMinutos(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** 145 hrs. (si hay minutos sueltos: 145:30 hrs.) */
    public static String fmtHoras(int minutos) {
        int h = minutos / 60, m = minutos % 60;
        return m == 0 ? h + " hrs." : String.format("%d:%02d hrs.", h, m);
    }

    static void validarTramos(List<Tramo> tramos, String contexto) {
        if (tramos.size() > 2) {
            throw new DatosNoValidosException(contexto + ": la plantilla solo tiene mañana y tarde (máx. 2 tramos).");
        }
        for (Tramo t : tramos) {
            if (aMinutos(t.fin()) <= aMinutos(t.ini())) {
                throw new DatosNoValidosException(contexto + ": la salida " + t.fin() + " no es posterior a la entrada " + t.ini() + ".");
            }
        }
    }

    /** Jornada del día según su estado y las horas habituales o propias. */
    static Jornada jornadaDelDia(LocalDate dia, DatosRegistro cfg) {
        String iso = dia.toString();
        String estado = cfg.estadoDias().getOrDefault(iso, "LIBRE");
        HorasDia propias = cfg.horasDia() == null ? null : cfg.horasDia().get(iso); // horas reales de ese día, si difieren de las habituales
        Tramo manana = propias != null && propias.manana() != null ? propias.manana() : cfg.horarioManana();
        Tramo tarde = propias != null && propias.tarde() != null ? propias.tarde() : cfg.horarioTarde();
        switch (estado) {
            case "MANANA":
                validarTramos(List.of(manana), "horas del " + iso);
                return new Jornada(List.of(manana), null);
            case "TARDE":
                validarTramos(List.of(manana, tarde), "horas del " + iso);
                if (aMinutos(tarde.ini()) < aMinutos(manana.fin())) {
                    throw new DatosNoValidosException("horas del " + iso + ": la tarde empieza antes de que termine la mañana.");
                }
                return new Jornada(List.of(manana, tarde), null);
            case "FESTIVO", "VACACIONES", "PERMISO", "ENFERMEDAD":
                return new Jornada(List.of(), estado);
            default:
                return new Jornada(List.of(), null);
        }
    }
}
