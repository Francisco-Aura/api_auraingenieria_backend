package api.aura.service.modulo_planificador;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Solo para las pruebas: sin conexión con los clientes de la intranet (en la aplicación real lo hace ClientesIntranet). */
// Solo con el perfil de las pruebas del Planificador: en las pruebas que arrancan la intranet entera
// ya está el de verdad (ClientesIntranet) y con los dos Spring no sabría cuál usar.
@Component
@Profile("planificador")
class ClientesApagados implements ClientesIntranetPort {

    @Override
    public boolean habilitado() {
        return false;
    }

    @Override
    public List<Map<String, Object>> buscar(String texto, int tamano) {
        return List.of();
    }

    @Override
    public Map<String, Object> crear(String nombre, String cif, String telefono, String email) {
        throw new IllegalStateException("Este servidor no tiene conexión con la intranet.");
    }
}
