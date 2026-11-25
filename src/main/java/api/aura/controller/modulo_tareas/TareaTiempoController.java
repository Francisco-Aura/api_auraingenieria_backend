package api.aura.controller.modulo_tareas;

import api.aura.controller.SesionActual;
import api.aura.dto.modulo_tareas.TiempoDTO;
import api.aura.service.modulo_tareas.TareaTiempoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Control de tiempo por tarea. La identidad es siempre la de la sesión: nadie controla el tiempo de otro. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "workmeter.activo", havingValue = "true")
@RequestMapping("/api/tareas/tiempo")
@RequiredArgsConstructor
public class TareaTiempoController {

    private final TareaTiempoService tiempoService;

    @PostMapping("/iniciar/{tareaId}")
    public TiempoDTO.Estado iniciar(@PathVariable Long tareaId) {
        return tiempoService.iniciar(tareaId, SesionActual.idRequerido());
    }

    @PostMapping("/pausar")
    public TiempoDTO.Estado pausar() {
        return tiempoService.pausar(SesionActual.idRequerido());
    }

    @PostMapping("/reanudar")
    public TiempoDTO.Estado reanudar() {
        return tiempoService.reanudar(SesionActual.idRequerido());
    }

    @GetMapping("/activo")
    public TiempoDTO.Estado activo() {
        return tiempoService.estadoActual(SesionActual.idRequerido());
    }

    /** Horas por tarea entre dos fechas. Sin {@code usuarioId}, las del usuario de la sesión; las de otro, solo administradores. */
    @GetMapping("/resumen")
    public TiempoDTO.Resumen resumen(
            @RequestParam(required = false) Integer usuarioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer actual = SesionActual.idRequerido();
        Integer objetivo = usuarioId != null ? usuarioId : actual;
        SesionActual.exigirPropioOAdmin(objetivo);
        return tiempoService.resumen(objetivo, desde, hasta);
    }
}
