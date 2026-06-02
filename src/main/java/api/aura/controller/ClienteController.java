package api.aura.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api.aura.dto.ClienteCarteraDTO;
import api.aura.dto.ClienteDTO;
import api.aura.entity.Cliente;
import api.aura.service.ClienteService;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private static final Logger logger = LoggerFactory.getLogger(ClienteController.class);

    @Autowired
    private ClienteService clienteService;

    @PostMapping
    public ResponseEntity<?> crearCliente(@Valid @RequestBody ClienteDTO clienteDTO) {
        if (logger.isDebugEnabled()) {
            String nombre = clienteDTO.getNombre() != null ? clienteDTO.getNombre().trim() : "";
            logger.debug("Solicitud para crear cliente. nombre='{}', cif='{}'", nombre, clienteDTO.getCif());
        }
        Cliente clienteCreado = clienteService.crearCliente(clienteDTO);
        return ResponseEntity.ok(clienteCreado);
    }

    @GetMapping
    public ResponseEntity<Page<Cliente>> obtenerTodosLosClientes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Cliente> clientes = clienteService.obtenerTodosLosClientes(PageRequest.of(page, size));
        return ResponseEntity.ok(clientes);
    }

    @GetMapping("/buscar")
    public ResponseEntity<Page<ClienteDTO>> buscarClientes(
            @RequestParam(name = "q") String termino,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String terminoNormalizado = termino == null ? "" : termino.trim();
        if (logger.isDebugEnabled()) {
            logger.debug("Búsqueda de clientes. longitudTermino={}, page={}, size={}",
                    terminoNormalizado.length(), page, size);
        }
        try {
            Page<ClienteDTO> clientes = clienteService.buscarClientes(terminoNormalizado, PageRequest.of(page, size));
            return ResponseEntity.ok(clientes);
        } catch (Exception e) {
            logger.error("Error al buscar clientes", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @GetMapping("/cartera")
    public List<ClienteCarteraDTO> obternerCarteraClientes() {
        return clienteService.obtenerCarteraClientes();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteDTO> obtenerClientePorId(@PathVariable Integer id) {
        return clienteService.obtenerClientePorId(id)
                .map(cliente -> ResponseEntity.ok(clienteService.toDTO(cliente)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarCliente(@PathVariable Integer id,
            @Valid @RequestBody ClienteDTO clienteDTO) {
        Cliente clienteActualizado = clienteService.actualizarCliente(id, clienteDTO);
        return ResponseEntity.ok(clienteActualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCliente(@PathVariable Integer id) {
        clienteService.eliminarCliente(id);
        return ResponseEntity.noContent().build();
    }
}
