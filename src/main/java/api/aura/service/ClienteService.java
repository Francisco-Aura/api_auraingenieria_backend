package api.aura.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import api.aura.dto.ClienteCarteraDTO;
import api.aura.entity.Cliente;
import api.aura.repository.ClienteRepository;

import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    // Crear un nuevo cliente
    public Cliente crearCliente(Cliente cliente) {
        return clienteRepository.save(cliente);
    }

    // Listar todos los clientes
    public List<Cliente> obtenerTodosLosClientes() {
        return clienteRepository.findAll();
    }

    /**
     * Obtiene una lista de clientes en forma de DTO con información resumida.
     *
     * @return Lista de clientes con datos de la cartera.
     */
    public List<ClienteCarteraDTO> obtenerCarteraClientes() {
        return clienteRepository.obtenerCarteraClientes();
    }

    // Buscar cliente por ID
    public Optional<Cliente> obtenerClientePorId(Integer id) {
        return clienteRepository.findById(id);
    }

    // Actualizar un cliente existente
    public Cliente actualizarCliente(Integer id, Cliente clienteActualizado) {
        return clienteRepository.findById(id)
                .map(cliente -> {
                    cliente.setNombre(clienteActualizado.getNombre());
                    cliente.setCif(clienteActualizado.getCif());
                    cliente.setDireccion(clienteActualizado.getDireccion());
                    cliente.setPoblacion(clienteActualizado.getPoblacion());
                    cliente.setProvincia(clienteActualizado.getProvincia());
                    cliente.setCodigoPostal(clienteActualizado.getCodigoPostal());
                    cliente.setNombreAutorizado(clienteActualizado.getNombreAutorizado());
                    cliente.setTelefonoAutorizado(clienteActualizado.getTelefonoAutorizado());
                    cliente.setDniAutorizado(clienteActualizado.getDniAutorizado());
                    cliente.setEmailAutorizado(clienteActualizado.getEmailAutorizado());
                    // Continúa con los demás campos según sea necesario
                    return clienteRepository.save(cliente);
                })
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con ID: " + id));
    }

    // Eliminar cliente (baja lógica)
    public void eliminarCliente(Integer id) {
        clienteRepository.findById(id).ifPresent(cliente -> {
            cliente.setFechaBaja(new java.util.Date());
            clienteRepository.save(cliente);
        });
    }

    public Page<Cliente> obtenerTodosLosClientes(PageRequest of) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'obtenerTodosLosClientes'");
    }
}
