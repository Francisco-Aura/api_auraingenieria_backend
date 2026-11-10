package api.aura.service.modulo_planificador.intranet;

import api.aura.dto.ClienteDTO;
import api.aura.service.ClienteService;
import api.aura.service.modulo_planificador.ClientesIntranetPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Clientes de la intranet para el presupuestador: búsqueda y alta mínima (nombre, CIF, teléfono, email) con el servicio de
 * clientes. Solo lo imprescindible: nada de IBAN, contactos ni claves de telemedida. Si ya hay un cliente con ese CIF
 * (sin mirar mayúsculas, espacios, puntos ni guiones) no se crea otro. No modifica ni borra clientes que ya existen.
 */
@Component
@RequiredArgsConstructor
public class ClientesIntranet implements ClientesIntranetPort {

    private static final int MAX_RESULTADOS = 50;

    private final ClienteService clientes;

    @Override
    public boolean habilitado() {
        return true;
    }

    @Override
    public List<Map<String, Object>> buscar(String texto, int tamano) {
        int limite = Math.max(1, Math.min(tamano, MAX_RESULTADOS));
        return clientes.buscarClientes(texto == null ? "" : texto, PageRequest.of(0, limite)).getContent().stream().map(ClientesIntranet::resumen).toList();
    }

    @Override
    public Map<String, Object> crear(String nombre, String cif, String telefono, String email) {
        String buscado = normalizarCif(cif);
        List<ClienteDTO> coincidencias = clientes.buscarClientes(cif, PageRequest.of(0, MAX_RESULTADOS)).getContent().stream()
                .filter(c -> normalizarCif(c.getCif()).equals(buscado)).toList();
        if (!coincidencias.isEmpty()) {
            return resumen(coincidencias.get(0));
        }
        ClienteDTO nuevo = new ClienteDTO();
        nuevo.setNombre(nombre);
        nuevo.setCif(cif);
        nuevo.setTelefonoContacto1(recortar(telefono, 50));
        nuevo.setEmailContacto1(recortar(email, 150));
        // La cartera de clientes une dirección, población y provincia en una sola columna: se dejan vacías, no nulas,
        // para que esa consulta y la pantalla de la cartera no tropiecen con un cliente sin dirección
        nuevo.setDireccion("");
        nuevo.setPoblacion("");
        nuevo.setProvincia("");
        nuevo.setCodigoPostal("");
        nuevo.setFechaAlta(new Date());
        return resumen(clientes.toDTO(clientes.crearCliente(nuevo)));
    }

    private static Map<String, Object> resumen(ClienteDTO c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getNombre());
        m.put("trade_name", c.getNombreComercial());
        m.put("cif", c.getCif());
        return m;
    }

    private static String normalizarCif(String cif) {
        return cif == null ? "" : cif.replaceAll("[\\s.-]", "").toUpperCase(Locale.ROOT);
    }

    private static String recortar(String texto, int max) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }
}
