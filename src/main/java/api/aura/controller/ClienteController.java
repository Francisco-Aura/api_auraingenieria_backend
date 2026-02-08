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
    public ResponseEntity<Cliente> crearCliente(@Valid @RequestBody ClienteDTO clienteDTO) {
        logger.info("Solicitud para crear cliente: {}", clienteDTO);
        try {
            // Convierte el DTO a la entidad y guarda
            Cliente clienteCreado = clienteService.crearCliente(clienteDTO);
            return ResponseEntity.ok(clienteCreado);
        } catch (RuntimeException e) {
            logger.error("Error al crear cliente: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
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
        logger.info("Buscando clientes con el término: {}", termino);
        try {
            Page<ClienteDTO> clientes = clienteService.buscarClientes(termino, PageRequest.of(page, size));
            return ResponseEntity.ok(clientes);
        } catch (Exception e) {
            logger.error("Error al buscar clientes: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
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
    public ResponseEntity<Cliente> actualizarCliente(@PathVariable Integer id, @Valid @RequestBody ClienteDTO clienteDTO) {
        try {
            // Convierte el DTO a la entidad y actualiza
            Cliente clienteActualizado = clienteService.actualizarCliente(id, clienteDTO);
            return ResponseEntity.ok(clienteActualizado);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCliente(@PathVariable Integer id) {
        clienteService.eliminarCliente(id);
        return ResponseEntity.noContent().build();
    }
}

