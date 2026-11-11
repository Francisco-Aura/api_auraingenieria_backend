package api.aura.service.modulo_registro_horario.documento;

import java.util.Map;

/**
 * Todo lo que hace falta para rellenar el registro de jornada de un mes. Las horas son «HH:MM» y las fechas «AAAA-MM-DD».
 *
 * @param estadoDias  un estado por fecha: MANANA, TARDE, FESTIVO, VACACIONES, PERMISO, ENFERMEDAD o LIBRE
 * @param horasDia    horas reales de un día cuando difieren de las habituales (mañana y/o tarde)
 * @param fechaFirma  «ultimo_dia», «hoy» o una fecha AAAA-MM-DD
 */
public record DatosRegistro(
        String trabajador, String nif, String numAfiliacion,
        String empresa, String cif, String ccc, String centroTrabajo, String localidad,
        Tramo horarioManana, Tramo horarioTarde,
        Map<String, String> estadoDias, Map<String, HorasDia> horasDia, String fechaFirma) {

    /** Entrada y salida de una franja, «HH:MM». */
    public record Tramo(String ini, String fin) {
    }

    /** Horas propias de un día: cada franja puede ser {@code null} (se usa la habitual). */
    public record HorasDia(Tramo manana, Tramo tarde) {
    }
}
