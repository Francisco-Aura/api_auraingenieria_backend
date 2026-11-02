package api.aura.controller;

import api.aura.dto.ClienteDTO;
import api.aura.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Clientes para el presupuestador del planificador: lectura de lo imprescindible para elegir uno (id, nombre,
 * nombre comercial y CIF; nada de IBAN, contactos ni claves de telemedida) y el alta mínima de un cliente nuevo
 * (nombre, CIF, teléfono y email) cuando se valida un presupuesto de un cliente potencial. No modifica ni borra
 * clientes que ya existen.
 *
 * Lo llama el servidor del planificador, no el navegador: exige la misma clave compartida que los avisos
 * (cabecera X-Planificador-Token, variable de entorno PLANIFICADOR_TOKEN). Sin clave configurada queda desactivado.
 */
@RestController
@RequestMapping("/api/planificador/clientes")
@RequiredArgsConstructor
public class PlanificadorClienteController {

    private static final int MAX_RESULTADOS = 50;

    private final ClienteService clienteService;

    @Value("${planificador.token:}")
    private String planificadorToken;

    /** Busca por nombre, nombre comercial o CIF (la misma búsqueda que la sección de clientes). Sin texto, lista vacía. */
    @GetMapping
    public ResponseEntity<?> buscar(
            @RequestHeader(value = "X-Planificador-Token", required = false) String token,
            @RequestParam(name = "q", defaultValue = "") String termino,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        if (!tokenValido(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int limite = Math.max(1, Math.min(size, MAX_RESULTADOS));
        List<ClienteResumen> clientes = clienteService.buscarClientes(termino, PageRequest.of(0, limite))
                .getContent().stream().map(PlanificadorClienteController::resumen).toList();
        return ResponseEntity.ok(clientes);
    }

    /** Un cliente por su id (para comprobar que sigue existiendo y enseñar su nombre actual). */
    @GetMapping("/{id}")
    public ResponseEntity<?> porId(
            @RequestHeader(value = "X-Planificador-Token", required = false) String token,
            @PathVariable Integer id) {
        if (!tokenValido(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return clienteService.obtenerClientePorId(id)
                .map(cliente -> ResponseEntity.ok((Object) resumen(clienteService.toDTO(cliente))))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Alta de un cliente nuevo desde el planificador. Si ya hay uno con ese CIF (sin mirar mayúsculas, espacios ni guiones)
     * no se crea otro: se devuelve el que existe, con `creado` en false.
     */
    @PostMapping
    public ResponseEntity<?> alta(
            @RequestHeader(value = "X-Planificador-Token", required = false) String token,
            @RequestBody ClienteAlta datos) {
        if (!tokenValido(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String nombre = datos.nombre() == null ? "" : datos.nombre().trim();
        String cif = datos.cif() == null ? "" : datos.cif().trim();
        if (nombre.isEmpty() || cif.isEmpty() || nombre.length() > 250 || cif.length() > 50) {
            return ResponseEntity.badRequest().body("Hacen falta el nombre y el CIF del cliente");
        }
        String buscado = normalizarCif(cif);
        List<ClienteDTO> coincidencias = clienteService.buscarClientes(cif, PageRequest.of(0, MAX_RESULTADOS))
                .getContent().stream().filter(c -> normalizarCif(c.getCif()).equals(buscado)).toList();
        if (!coincidencias.isEmpty()) {
            return ResponseEntity.ok(new ClienteAltaResultado(resumen(coincidencias.get(0)), false));
        }
        ClienteDTO nuevo = new ClienteDTO();
        nuevo.setNombre(nombre);
        nuevo.setCif(cif);
        nuevo.setTelefonoContacto1(recortar(datos.telefono(), 50));
        nuevo.setEmailContacto1(recortar(datos.email(), 150));
        nuevo.setFechaAlta(new Date());
        ClienteDTO creado = clienteService.toDTO(clienteService.crearCliente(nuevo));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ClienteAltaResultado(resumen(creado), true));
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

    private static ClienteResumen resumen(ClienteDTO c) {
        return new ClienteResumen(c.getId(), c.getNombre(), c.getNombreComercial(), c.getCif());
    }

    /** Comparación en tiempo constante; sin clave configurada nunca es válida. */
    private boolean tokenValido(String recibido) {
        if (planificadorToken == null || planificadorToken.isBlank() || recibido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                planificadorToken.getBytes(StandardCharsets.UTF_8), recibido.getBytes(StandardCharsets.UTF_8));
    }

    public record ClienteResumen(Integer id, String nombre, String nombreComercial, String cif) {
    }

    /** Lo único que el planificador puede dar de alta de un cliente. */
    public record ClienteAlta(String nombre, String cif, String telefono, String email) {
    }

    public record ClienteAltaResultado(ClienteResumen cliente, boolean creado) {
    }
}
