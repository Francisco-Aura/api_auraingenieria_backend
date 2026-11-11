package api.aura.dto.modulo_registro_horario;

import java.util.List;

/** Respuesta de GET /api/registro-horario/empresas: localidad general y empresas del desplegable. */
public record EmpresasRespuesta(String localidad, List<EmpresaDTO> empresas) {
}
