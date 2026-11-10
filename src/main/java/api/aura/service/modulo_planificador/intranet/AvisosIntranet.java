package api.aura.service.modulo_planificador.intranet;

import api.aura.dto.modulo_tareas.PlanificadorAvisoRequest;
import api.aura.service.modulo_planificador.AvisoError;
import api.aura.service.modulo_planificador.AvisosPort;
import api.aura.service.modulo_tareas.TareaNotificacionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Avisos del Planificador (tarea terminada, vencimientos, revisión de precios) como notificaciones de la intranet: se crean
 * directamente con el servicio de notificaciones de tareas, para los usuarios del grupo indicado.
 * Va en su propia transacción: si falla crear las notificaciones, no se deshace lo que se estaba guardando (terminar la tarea).
 */
@Component
@RequiredArgsConstructor
public class AvisosIntranet implements AvisosPort {

    private final TareaNotificacionService notificaciones;
    private final ObjectMapper json;

    @Override
    public boolean activo() {
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enviar(Map<String, Object> aviso) {
        try {
            PlanificadorAvisoRequest peticion = json.convertValue(aviso, PlanificadorAvisoRequest.class);
            if (peticion.getGroup() == null || peticion.getGroup().isBlank() || peticion.getMessage() == null || peticion.getMessage().isBlank()) {
                throw new AvisoError("Faltan el grupo o el mensaje");
            }
            notificaciones.notificarDesdePlanificador(peticion);
        } catch (AvisoError e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AvisoError("No se pudo crear la notificación en la intranet");
        }
    }
}
