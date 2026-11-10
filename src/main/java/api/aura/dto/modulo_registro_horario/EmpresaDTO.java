package api.aura.dto.modulo_registro_horario;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Una empresa del desplegable. {@code texto} es lo que se ve y lo que la pantalla devuelve al generar el documento;
 * el resto sale del archivo de configuración del servidor. Mismos nombres de campo que el Registro Horario anterior.
 */
public record EmpresaDTO(
        @JsonProperty("texto") String texto,
        @JsonProperty("empresa") String empresa,
        @JsonProperty("cif") String cif,
        @JsonProperty("ccc") String ccc,
        @JsonProperty("centro_trabajo") String centroTrabajo,
        @JsonProperty("localidad") String localidad) {
}
