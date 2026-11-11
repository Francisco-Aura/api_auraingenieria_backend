package api.aura.dto.modulo_registro_horario;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Lo que la pantalla manda al pulsar «Generar»: datos de la persona, horario, estado de cada día y la firma (PNG en
 * base64). Mismos nombres de campo que el servicio anterior. La validación está en {@code GenerarService}.
 */
public record GenerarRequest(
        @JsonProperty("anio") Integer anio,
        @JsonProperty("mes") Integer mes,
        @JsonProperty("trabajador") String trabajador,
        @JsonProperty("nif") String nif,
        @JsonProperty("num_afiliacion") String numAfiliacion,
        /** El texto elegido en el desplegable de /empresas. */
        @JsonProperty("empresa") String empresa,
        @JsonProperty("horario_base") Horario horarioBase,
        /** Un estado por fecha AAAA-MM-DD: MANANA, TARDE, FESTIVO, VACACIONES, PERMISO, ENFERMEDAD o LIBRE. */
        @JsonProperty("estado_dias") Map<String, String> estadoDias,
        @JsonProperty("horas_dia") Map<String, HorasDia> horasDia,
        @JsonProperty("firma_png_base64") String firmaPngBase64,
        /** «ultimo_dia» o una fecha AAAA-MM-DD (la del día en que se genera). */
        @JsonProperty("fecha_firma") String fechaFirma,
        /** «pdf» (por defecto) o «docx». */
        @JsonProperty("formato") String formato) {

    /** Entrada y salida de una franja, «HH:MM». */
    public record Tramo(@JsonProperty("ini") String ini, @JsonProperty("fin") String fin) {
    }

    public record Horario(@JsonProperty("manana") Tramo manana, @JsonProperty("tarde") Tramo tarde) {
    }

    public record HorasDia(@JsonProperty("manana") Tramo manana, @JsonProperty("tarde") Tramo tarde) {
    }
}
