package api.aura.service.modulo_planificador;

import org.springframework.stereotype.Component;

import java.util.Map;

/** Solo para las pruebas: sin conexión con las notificaciones de la intranet (en la aplicación real lo hace AvisosIntranet). */
@Component
class AvisosApagados implements AvisosPort {

    @Override
    public boolean activo() {
        return false;
    }

    @Override
    public void enviar(Map<String, Object> aviso) {
        // nada
    }
}
