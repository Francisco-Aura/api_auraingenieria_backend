package api.aura.controller.modulo_registro_horario;

import api.aura.config.ErrorApi;
import api.aura.dto.modulo_registro_horario.EmpresasRespuesta;
import api.aura.dto.modulo_registro_horario.FestivosRespuesta;
import api.aura.service.modulo_registro_horario.CalendarioFestivos;
import api.aura.service.modulo_registro_horario.EmpresasConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Registro Horario. Servicio sin datos personales: el perfil, el calendario y la firma viven en el navegador de cada
 * persona y aquí no se guarda nada. Mismas respuestas que el servicio anterior, bajo /api/registro-horario.
 */
@RestController
@RequestMapping("/api/registro-horario")
@RequiredArgsConstructor
public class RegistroHorarioController {

    private final EmpresasConfig empresas;

    @GetMapping("/health")
    public Map<String, Boolean> salud() {
        return Map.of("ok", true);
    }

    /** Empresas del desplegable y localidad general. */
    @GetMapping("/empresas")
    public EmpresasRespuesta listarEmpresas() {
        return empresas.respuesta();
    }

    /** Festivos del año (nacionales + los locales de la configuración) en formato AAAA-MM-DD. */
    @GetMapping("/festivos/{anio}")
    public FestivosRespuesta festivos(@PathVariable int anio) {
        if (anio < 2000 || anio > 2100) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "Año fuera de rango.");
        }
        List<String> fechas = CalendarioFestivos.festivosDelAnio(anio, empresas.festivosExtra()).stream()
                .map(LocalDate::toString).toList();
        return new FestivosRespuesta(anio, fechas);
    }
}
