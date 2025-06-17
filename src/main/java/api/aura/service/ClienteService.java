package api.aura.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import api.aura.dto.ClienteCarteraDTO;
import api.aura.dto.ClienteDTO;
import api.aura.entity.Cliente;
import api.aura.entity.Usuario;
import api.aura.entity.AppMonitorizacion;
import api.aura.entity.IntegracionSistemasFV;
import api.aura.repository.ClienteRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.AppMonitorizacionRepository;
import api.aura.repository.IntegracionSistemasFVRepository;

import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {    @Autowired
    private ClienteRepository clienteRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AppMonitorizacionRepository appMonitorizacionRepository;

    @Autowired
    private IntegracionSistemasFVRepository integracionSistemasFVRepository;

    // Crear un nuevo cliente
    public Cliente crearCliente(ClienteDTO clienteDTO) {
        Cliente cliente = new Cliente();
    
        // Asignación de campos desde el DTO a la entidad
        cliente.setNombre(clienteDTO.getNombre());
        cliente.setCif(clienteDTO.getCif());
        cliente.setDireccion(clienteDTO.getDireccion());
        cliente.setPoblacion(clienteDTO.getPoblacion());
        cliente.setProvincia(clienteDTO.getProvincia());
        cliente.setCodigoPostal(clienteDTO.getCodigoPostal());
        cliente.setNombreAutorizado(clienteDTO.getNombreAutorizado());
        cliente.setTelefonoAutorizado(clienteDTO.getTelefonoAutorizado());
        cliente.setDniAutorizado(clienteDTO.getDniAutorizado());
        cliente.setEmailAutorizado(clienteDTO.getEmailAutorizado());
        cliente.setNombreContacto1(clienteDTO.getNombreContacto1());
        cliente.setCargoContacto1(clienteDTO.getCargoContacto1());
        cliente.setTelefonoContacto1(clienteDTO.getTelefonoContacto1());
        cliente.setEmailContacto1(clienteDTO.getEmailContacto1());
        cliente.setNombreContacto2(clienteDTO.getNombreContacto2());
        cliente.setCargoContacto2(clienteDTO.getCargoContacto2());
        cliente.setTelefonoContacto2(clienteDTO.getTelefonoContacto2());
        cliente.setEmailContacto2(clienteDTO.getEmailContacto2());
        cliente.setNombreContacto3(clienteDTO.getNombreContacto3());
        cliente.setCargoContacto3(clienteDTO.getCargoContacto3());
        cliente.setTelefonoContacto3(clienteDTO.getTelefonoContacto3());
        cliente.setEmailContacto3(clienteDTO.getEmailContacto3());
        cliente.setIban(clienteDTO.getIban());
        cliente.setNotas(clienteDTO.getNotas());
        cliente.setFechaAlta(clienteDTO.getFechaAlta());
        cliente.setFechaBaja(clienteDTO.getFechaBaja());
        cliente.setNombreComercial(clienteDTO.getNombreComercial());
        cliente.setCodigoSector(clienteDTO.getCodigoSector());
        cliente.setCodigoSubtipologia(clienteDTO.getCodigoSubtipologia());
        cliente.setActividad(clienteDTO.getActividad());        cliente.setUserTelemedida(clienteDTO.getUserTelemedida());
        cliente.setPassTelemedida(clienteDTO.getPassTelemedida());

        // Campos de instalaciones
        cliente.setBateriaCondensadores(clienteDTO.getBateriaCondensadores());
        cliente.setPotenciaBateria(clienteDTO.getPotenciaBateria());
        cliente.setModeloBateria(clienteDTO.getModeloBateria());
        cliente.setInstalacionFV(clienteDTO.getInstalacionFV());
        cliente.setKwInversor(clienteDTO.getKwInversor());
        cliente.setKwPanelesSolares(clienteDTO.getKwPanelesSolares());
        cliente.setBateriaAlmacenamiento(clienteDTO.getBateriaAlmacenamiento());
        cliente.setPotenciaBateriaAlmacenamiento(clienteDTO.getPotenciaBateriaAlmacenamiento());
        cliente.setBackup(clienteDTO.getBackup());
        cliente.setPeaksaving(clienteDTO.getPeaksaving());

        // Relación con AppMonitorizacion
        if (clienteDTO.getAppMonitorizacionId() != null) {
            Optional<AppMonitorizacion> appOpt = appMonitorizacionRepository.findById(clienteDTO.getAppMonitorizacionId());
            if (appOpt.isPresent()) {
                cliente.setAppMonitorizacion(appOpt.get());
            } else {
                throw new RuntimeException("APP Monitorización no encontrada con ID: " + clienteDTO.getAppMonitorizacionId());
            }
        }

        // Relación con IntegracionSistemasFV
        if (clienteDTO.getIntegracionSistemasFVId() != null) {
            Optional<IntegracionSistemasFV> integracionOpt = integracionSistemasFVRepository.findById(clienteDTO.getIntegracionSistemasFVId());
            if (integracionOpt.isPresent()) {
                cliente.setIntegracionSistemasFV(integracionOpt.get());
            } else {
                throw new RuntimeException("Integración Sistemas FV no encontrada con ID: " + clienteDTO.getIntegracionSistemasFVId());
            }
        }
    
        // Relación con Usuario
        if (clienteDTO.getIdUsuario() != null) {
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(clienteDTO.getIdUsuario());
            if (usuarioOpt.isPresent()) {
                cliente.setUsuario(usuarioOpt.get());
            } else {
                throw new RuntimeException("Usuario no encontrado con ID: " + clienteDTO.getIdUsuario());
            }
        }
    
        // Guarda el cliente en la base de datos
        return clienteRepository.save(cliente);
    }

    // Listar todos los clientes
    public List<Cliente> obtenerTodosLosClientes() {
        return clienteRepository.findAll();
    }

    // Obtiene una lista de clientes con información resumida
    public List<ClienteCarteraDTO> obtenerCarteraClientes() {
        return clienteRepository.obtenerCarteraClientes();
    }

    // Buscar cliente por ID
    public Optional<Cliente> obtenerClientePorId(Integer id) {
        return clienteRepository.findById(id);
    }

    // Actualizar un cliente existente
    public Cliente actualizarCliente(Integer id, ClienteDTO clienteDTO) {
        return clienteRepository.findById(id)
                .map(cliente -> {
                    // Actualiza los campos de la entidad usando el DTO
                    cliente.setNombre(clienteDTO.getNombre());
                    cliente.setCif(clienteDTO.getCif());
                    cliente.setDireccion(clienteDTO.getDireccion());
                    cliente.setPoblacion(clienteDTO.getPoblacion());
                    cliente.setProvincia(clienteDTO.getProvincia());
                    cliente.setCodigoPostal(clienteDTO.getCodigoPostal());
                    cliente.setNombreAutorizado(clienteDTO.getNombreAutorizado());
                    cliente.setTelefonoAutorizado(clienteDTO.getTelefonoAutorizado());
                    cliente.setDniAutorizado(clienteDTO.getDniAutorizado());
                    cliente.setEmailAutorizado(clienteDTO.getEmailAutorizado());
                    cliente.setNombreContacto1(clienteDTO.getNombreContacto1());
                    cliente.setCargoContacto1(clienteDTO.getCargoContacto1());
                    cliente.setTelefonoContacto1(clienteDTO.getTelefonoContacto1());
                    cliente.setEmailContacto1(clienteDTO.getEmailContacto1());
                    cliente.setNombreContacto2(clienteDTO.getNombreContacto2());
                    cliente.setCargoContacto2(clienteDTO.getCargoContacto2());
                    cliente.setTelefonoContacto2(clienteDTO.getTelefonoContacto2());
                    cliente.setEmailContacto2(clienteDTO.getEmailContacto2());
                    cliente.setNombreContacto3(clienteDTO.getNombreContacto3());
                    cliente.setCargoContacto3(clienteDTO.getCargoContacto3());
                    cliente.setTelefonoContacto3(clienteDTO.getTelefonoContacto3());
                    cliente.setEmailContacto3(clienteDTO.getEmailContacto3());
                    cliente.setIban(clienteDTO.getIban());
                    cliente.setNotas(clienteDTO.getNotas());
                    cliente.setFechaAlta(clienteDTO.getFechaAlta());
                    cliente.setFechaBaja(clienteDTO.getFechaBaja());
                    cliente.setNombreComercial(clienteDTO.getNombreComercial());
                    cliente.setCodigoSector(clienteDTO.getCodigoSector());
                    cliente.setCodigoSubtipologia(clienteDTO.getCodigoSubtipologia());
                    cliente.setActividad(clienteDTO.getActividad());                    cliente.setUserTelemedida(clienteDTO.getUserTelemedida());
                    cliente.setPassTelemedida(clienteDTO.getPassTelemedida());

                    // Campos de instalaciones
                    cliente.setBateriaCondensadores(clienteDTO.getBateriaCondensadores());
                    cliente.setPotenciaBateria(clienteDTO.getPotenciaBateria());
                    cliente.setModeloBateria(clienteDTO.getModeloBateria());
                    cliente.setInstalacionFV(clienteDTO.getInstalacionFV());
                    cliente.setKwInversor(clienteDTO.getKwInversor());
                    cliente.setKwPanelesSolares(clienteDTO.getKwPanelesSolares());
                    cliente.setBateriaAlmacenamiento(clienteDTO.getBateriaAlmacenamiento());
                    cliente.setPotenciaBateriaAlmacenamiento(clienteDTO.getPotenciaBateriaAlmacenamiento());
                    cliente.setBackup(clienteDTO.getBackup());
                    cliente.setPeaksaving(clienteDTO.getPeaksaving());

                    // Actualiza la relación con AppMonitorizacion
                    if (clienteDTO.getAppMonitorizacionId() != null) {
                        Optional<AppMonitorizacion> appOpt = appMonitorizacionRepository.findById(clienteDTO.getAppMonitorizacionId());
                        if (appOpt.isPresent()) {
                            cliente.setAppMonitorizacion(appOpt.get());
                        } else {
                            throw new RuntimeException("APP Monitorización no encontrada con ID: " + clienteDTO.getAppMonitorizacionId());
                        }
                    } else {
                        cliente.setAppMonitorizacion(null);
                    }

                    // Actualiza la relación con IntegracionSistemasFV
                    if (clienteDTO.getIntegracionSistemasFVId() != null) {
                        Optional<IntegracionSistemasFV> integracionOpt = integracionSistemasFVRepository.findById(clienteDTO.getIntegracionSistemasFVId());
                        if (integracionOpt.isPresent()) {
                            cliente.setIntegracionSistemasFV(integracionOpt.get());
                        } else {
                            throw new RuntimeException("Integración Sistemas FV no encontrada con ID: " + clienteDTO.getIntegracionSistemasFVId());
                        }
                    } else {
                        cliente.setIntegracionSistemasFV(null);
                    }
    
                    // Actualiza la relación con Usuario si es necesario
                    if (clienteDTO.getIdUsuario() != null) {
                        Optional<Usuario> usuarioOpt = usuarioRepository.findById(clienteDTO.getIdUsuario());
                        if (usuarioOpt.isPresent()) {
                            cliente.setUsuario(usuarioOpt.get());
                        } else {
                            throw new RuntimeException("Usuario no encontrado con ID: " + clienteDTO.getIdUsuario());
                        }
                    } else {
                        cliente.setUsuario(null); // Si no se envía, limpia la relación
                    }
    
                    return clienteRepository.save(cliente);
                })
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con ID: " + id));
    }
    

    // Baja lógica del cliente
    public void eliminarCliente(Integer id) {
        clienteRepository.findById(id).ifPresent(cliente -> {
            cliente.setFechaBaja(new java.util.Date());
            clienteRepository.save(cliente);
        });
    }

    // Obtener clientes paginados
    public Page<Cliente> obtenerTodosLosClientes(PageRequest pageRequest) {
        return clienteRepository.findAll(pageRequest);
    }

    // Convertir entidad Cliente a DTO
    public ClienteDTO toDTO(Cliente cliente) {
        ClienteDTO dto = new ClienteDTO();
        dto.setId(cliente.getId());
        dto.setNombre(cliente.getNombre());
        dto.setCif(cliente.getCif());
        dto.setDireccion(cliente.getDireccion());
        dto.setPoblacion(cliente.getPoblacion());
        dto.setProvincia(cliente.getProvincia());
        dto.setCodigoPostal(cliente.getCodigoPostal());
        dto.setNombreAutorizado(cliente.getNombreAutorizado());
        dto.setTelefonoAutorizado(cliente.getTelefonoAutorizado());
        dto.setDniAutorizado(cliente.getDniAutorizado());
        dto.setEmailAutorizado(cliente.getEmailAutorizado());
        dto.setNombreContacto1(cliente.getNombreContacto1());
        dto.setCargoContacto1(cliente.getCargoContacto1());
        dto.setTelefonoContacto1(cliente.getTelefonoContacto1());
        dto.setEmailContacto1(cliente.getEmailContacto1());
        dto.setNombreContacto2(cliente.getNombreContacto2());
        dto.setCargoContacto2(cliente.getCargoContacto2());
        dto.setTelefonoContacto2(cliente.getTelefonoContacto2());
        dto.setEmailContacto2(cliente.getEmailContacto2());
        dto.setNombreContacto3(cliente.getNombreContacto3());
        dto.setCargoContacto3(cliente.getCargoContacto3());
        dto.setTelefonoContacto3(cliente.getTelefonoContacto3());
        dto.setEmailContacto3(cliente.getEmailContacto3());
        dto.setIban(cliente.getIban());
        dto.setNotas(cliente.getNotas());
        dto.setFechaAlta(cliente.getFechaAlta());
        dto.setFechaBaja(cliente.getFechaBaja());
        dto.setNombreComercial(cliente.getNombreComercial());
        dto.setCodigoSector(cliente.getCodigoSector());
        dto.setCodigoSubtipologia(cliente.getCodigoSubtipologia());
        dto.setActividad(cliente.getActividad());        dto.setUserTelemedida(cliente.getUserTelemedida());
        dto.setPassTelemedida(cliente.getPassTelemedida());

        // Campos de instalaciones
        dto.setBateriaCondensadores(cliente.getBateriaCondensadores());
        dto.setPotenciaBateria(cliente.getPotenciaBateria());
        dto.setModeloBateria(cliente.getModeloBateria());
        dto.setInstalacionFV(cliente.getInstalacionFV());
        dto.setKwInversor(cliente.getKwInversor());
        dto.setKwPanelesSolares(cliente.getKwPanelesSolares());
        dto.setBateriaAlmacenamiento(cliente.getBateriaAlmacenamiento());
        dto.setPotenciaBateriaAlmacenamiento(cliente.getPotenciaBateriaAlmacenamiento());
        dto.setBackup(cliente.getBackup());
        dto.setPeaksaving(cliente.getPeaksaving());

        // Relación con AppMonitorizacion
        if (cliente.getAppMonitorizacion() != null) {
            dto.setAppMonitorizacionId(cliente.getAppMonitorizacion().getId());
        }

        // Relación con IntegracionSistemasFV
        if (cliente.getIntegracionSistemasFV() != null) {
            dto.setIntegracionSistemasFVId(cliente.getIntegracionSistemasFV().getId());
        }

        // Relación con Usuario
        if (cliente.getUsuario() != null) {
            dto.setIdUsuario(cliente.getUsuario().getId());
        }

        return dto;
    }
}
