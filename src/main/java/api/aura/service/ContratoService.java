package api.aura.service;

import api.aura.entity.Contrato;
import api.aura.entity.Cliente;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.ContratoRepository;
import api.aura.repository.ClienteRepository;
import api.aura.repository.PuntoSuministroRepository;
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

    public ContratoService(ContratoRepository contratoRepository,
            ClienteRepository clienteRepository,
            PuntoSuministroRepository puntoSuministroRepository) {
        this.contratoRepository = contratoRepository;
        this.clienteRepository = clienteRepository;
        this.puntoSuministroRepository = puntoSuministroRepository;
    }

    public Contrato guardarContrato(Contrato contrato) {
        logger.info("Guardando contrato para cliente ID: {}",
                contrato.getCliente() != null ? contrato.getCliente().getId() : "null");

        // Si el contrato tiene ID, es una actualización
        if (contrato.getId() != null) {
            logger.info("Actualizando contrato existente con ID: {}", contrato.getId());

            Optional<Contrato> contratoExistente = contratoRepository.findById(contrato.getId());
            if (contratoExistente.isPresent()) {
                Contrato contratoActual = contratoExistente.get();

                // Actualizar todos los campos explícitamente
                contratoActual.setFecha(contrato.getFecha());
                contratoActual.setFechaEnvio(contrato.getFechaEnvio());
                contratoActual.setFechaInicio(contrato.getFechaInicio());
                contratoActual.setFechaFin(contrato.getFechaFin());
                contratoActual.setContrato(contrato.getContrato());
                contratoActual.setIdTarifaLuz(contrato.getIdTarifaLuz());
                contratoActual.setIdTarifaGas(contrato.getIdTarifaGas());
                contratoActual.setPotencia1(contrato.getPotencia1());
                contratoActual.setPotencia2(contrato.getPotencia2());
                contratoActual.setPotencia3(contrato.getPotencia3());
                contratoActual.setPotencia4(contrato.getPotencia4());
                contratoActual.setPotencia5(contrato.getPotencia5());
                contratoActual.setPotencia6(contrato.getPotencia6());
                contratoActual.setConsumoAnual(contrato.getConsumoAnual());
                contratoActual.setExencionImpuestoElectricidad(contrato.getExencionImpuestoElectricidad());
                contratoActual.setIdComercializadoraActual(contrato.getIdComercializadoraActual());
                contratoActual.setIdComercializadoraAnterior(contrato.getIdComercializadoraAnterior());
                contratoActual.setAutoconsumo(contrato.getAutoconsumo());
                contratoActual.setDenunciaContrato(contrato.getDenunciaContrato());
                contratoActual.setFechaDenunciaContrato(contrato.getFechaDenunciaContrato());
                contratoActual.setObservaciones(contrato.getObservaciones());
                contratoActual.setUserFO(contrato.getUserFO());
                contratoActual.setPasswordFO(contrato.getPasswordFO());

                logger.info("Campos del contrato actualizados. Guardando en base de datos...");
                return contratoRepository.save(contratoActual);
            } else {
                logger.error("No se encontró el contrato con ID: {}", contrato.getId());
                throw new IllegalArgumentException("No se encontró el contrato con ID: " + contrato.getId());
            }
        }

        // Si no tiene ID, es un contrato nuevo
        logger.info("Creando nuevo contrato");

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
            // Si no tiene cliente pero tiene punto de suministro, buscamos el cliente a
            // través del punto de suministro
            Optional<PuntoSuministro> puntoSuministroOpt = puntoSuministroRepository
                    .findById(contrato.getPuntoSuministro().getId());
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

    public List<Contrato> obtenerContratosActivos() {
        return contratoRepository.findContratosActivos();
    }

    public Optional<Contrato> obtenerPorId(Integer id) {
        return contratoRepository.findById(id);
    }

    public List<Contrato> obtenerPorClienteId(Integer clienteId) {
        return contratoRepository.findByClienteId(clienteId);
    }

    public List<Contrato> obtenerPorPuntoSuministroId(Integer puntoSuministroId) {
        List<Contrato> contratos = contratoRepository.findByPuntoSuministroId(puntoSuministroId);

        // Cargar la información del cliente para cada contrato
        contratos.forEach(contrato -> {
            if (contrato.getCliente() != null) {
                clienteRepository.findById(contrato.getCliente().getId())
                        .ifPresent(contrato::setCliente);
            }
        });
        return contratos;
    }

    public void eliminarPorId(Integer id) {
        contratoRepository.deleteById(id);
    }
}
