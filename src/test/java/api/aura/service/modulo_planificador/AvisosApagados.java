package api.aura.service.modulo_planificador;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Solo para las pruebas: sin conexión con las notificaciones de la intranet (en la aplicación real lo hace AvisosIntranet). */
// Solo con el perfil de las pruebas del Planificador: en las pruebas que arrancan la intranet entera
// ya está el de verdad (AvisosIntranet) y con los dos Spring no sabría cuál usar.
@Component
@Profile("planificador")
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
