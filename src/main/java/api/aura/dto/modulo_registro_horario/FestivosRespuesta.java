package api.aura.dto.modulo_registro_horario;

import java.util.List;

/** Respuesta de GET /api/registro-horario/festivos/{anio}: fechas AAAA-MM-DD ordenadas. */
public record FestivosRespuesta(int anio, List<String> festivos) {
}
