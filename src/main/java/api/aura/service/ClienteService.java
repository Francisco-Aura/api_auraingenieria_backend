package api.aura.service;

import org.springframework.beans.factory.annotation.Autowired;
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

    //Listar Cartera Clientes
    public List<ClienteCarteraDTO> obternerCarteraClientes() {
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
}
