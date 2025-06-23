package api.aura.service;

import api.aura.entity.Contrato;
import api.aura.entity.Cliente;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.ContratoRepository;
import api.aura.repository.ClienteRepository;
import api.aura.repository.PuntoSuministroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Service
public class ContratoService {

    private static final Logger logger = LoggerFactory.getLogger(ContratoService.class);

    private final ContratoRepository contratoRepository;
    private final ClienteRepository clienteRepository;
    private final PuntoSuministroRepository puntoSuministroRepository;

    @Autowired
    public ContratoService(ContratoRepository contratoRepository, 
                         ClienteRepository clienteRepository,
                         PuntoSuministroRepository puntoSuministroRepository) {
        this.contratoRepository = contratoRepository;
        this.clienteRepository = clienteRepository;
        this.puntoSuministroRepository = puntoSuministroRepository;
    }

    public Contrato guardarContrato(Contrato contrato) {
        logger.info("Guardando contrato para cliente ID: {}", contrato.getCliente() != null ? contrato.getCliente().getId() : "null");
        
        // Si el contrato tiene un cliente pero no está cargado completamente
        if (contrato.getCliente() != null && contrato.getCliente().getId() != null) {
            Optional<Cliente> clienteOpt = clienteRepository.findById(contrato.getCliente().getId());
            if (clienteOpt.isPresent()) {
                contrato.setCliente(clienteOpt.get());
                logger.info("Cliente asociado correctamente al contrato");
            } else {
                logger.error("No se encontró el cliente con ID: {}", contrato.getCliente().getId());
                throw new IllegalArgumentException("No se encontró el cliente especificado");
            }
        } else if (contrato.getPuntoSuministro() != null && contrato.getPuntoSuministro().getId() != null) {
            // Si no tiene cliente pero tiene punto de suministro, buscamos el cliente a través del punto de suministro
            Optional<PuntoSuministro> puntoSuministroOpt = puntoSuministroRepository.findById(contrato.getPuntoSuministro().getId());
            if (puntoSuministroOpt.isPresent() && puntoSuministroOpt.get().getCliente() != null) {
                contrato.setCliente(puntoSuministroOpt.get().getCliente());
                logger.info("Cliente asociado al contrato a través del punto de suministro");
            } else {
                logger.error("No se encontró el punto de suministro o no tiene cliente asociado");
                throw new IllegalArgumentException("El punto de suministro no tiene un cliente asociado");
            }
        } else {
            logger.error("El contrato no tiene un cliente asociado");
            throw new IllegalArgumentException("El contrato debe tener un cliente asociado");
        }
        
        return contratoRepository.save(contrato);
    }

    public List<Contrato> obtenerTodos() {
        return contratoRepository.findAll();
    }

    public Optional<Contrato> obtenerPorId(Integer id) {
        return contratoRepository.findById(id);
    }

    public List<Contrato> obtenerPorClienteId(Integer clienteId) {
        return contratoRepository.findByClienteId(clienteId);
    }

    public List<Contrato> obtenerPorPuntoSuministroId(Integer puntoSuministroId) {
        System.out.println("Buscando contratos para punto de suministro ID: " + puntoSuministroId);
        List<Contrato> contratos = contratoRepository.findByPuntoSuministroId(puntoSuministroId);
        System.out.println("Encontrados " + contratos.size() + " contratos.");

        // Cargar la información del cliente para cada contrato
        contratos.forEach(contrato -> {
            System.out.println("Procesando contrato ID: " + contrato.getId());
            if (contrato.getCliente() != null) {
                System.out.println("Contrato " + contrato.getId() + " tiene cliente asociado con ID: " + contrato.getCliente().getId());
                // Cargar la información completa del cliente
                clienteRepository.findById(contrato.getCliente().getId())
                    .ifPresent(cliente -> {
                        contrato.setCliente(cliente);
                        System.out.println("  Cliente cargado para contrato " + contrato.getId() + ":");
                        System.out.println("    Nombre: " + cliente.getNombre());
                        System.out.println("    DNI Autorizado: " + cliente.getDniAutorizado());
                        System.out.println("    CIF: " + cliente.getCif());
                    });
            } else {
                System.out.println("Contrato " + contrato.getId() + " NO tiene cliente asociado.");
            }
        });
        System.out.println("Finalizado procesamiento de contratos para PS ID: " + puntoSuministroId);
        return contratos;
    }

    public void eliminarPorId(Integer id) {
        contratoRepository.deleteById(id);
    }
}
