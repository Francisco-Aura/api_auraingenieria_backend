package api.aura.controller.modulo_registro_horario;

import api.aura.config.ErrorApi;
import api.aura.dto.modulo_registro_horario.EmpresasRespuesta;
import api.aura.dto.modulo_registro_horario.FestivosRespuesta;
import api.aura.service.modulo_registro_horario.CalendarioFestivos;
import api.aura.service.modulo_registro_horario.EmpresasConfig;
import api.aura.service.modulo_registro_horario.firma.FirmaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final FirmaService firma;

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

    /** Separa la firma de la foto o escaneo subido. Devuelve un PNG con fondo transparente (sin guardar nada). */
    @PostMapping(value = "/firma/extraer", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> extraerFirma(@RequestParam("imagen") MultipartFile imagen,
                                               @RequestParam(value = "sensibilidad", defaultValue = "0.5") double sensibilidad,
                                               @RequestParam(value = "vista", defaultValue = "false") boolean vista)
            throws IOException {
        if (!(sensibilidad >= 0 && sensibilidad <= 1)) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "Hay un dato no válido.");
        }
        byte[] png = firma.extraerPng(imagen.getBytes(), sensibilidad, vista);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(png);
    }
}
