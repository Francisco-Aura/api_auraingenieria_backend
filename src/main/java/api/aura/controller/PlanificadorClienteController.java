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
import java.util.List;

/**
 * Clientes para el presupuestador del planificador: solo lectura y solo lo imprescindible para elegir uno (id, nombre,
 * nombre comercial y CIF; nada de IBAN, contactos ni claves de telemedida).
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
}
