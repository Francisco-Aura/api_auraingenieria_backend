package api.aura.service;

import api.aura.entity.Contrato;
import api.aura.entity.Cliente;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.ContratoRepository;
import api.aura.repository.ClienteRepository;
import api.aura.repository.PuntoSuministroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ContratoService {

    private static final Logger logger = LoggerFactory.getLogger(ContratoService.class);

    private final ContratoRepository contratoRepository;
    private final ClienteRepository clienteRepository;
    private final PuntoSuministroRepository puntoSuministroRepository;
    private final ContratoFileStorageService contratoFileStorageService;

    public ContratoService(ContratoRepository contratoRepository,
            ClienteRepository clienteRepository,
            PuntoSuministroRepository puntoSuministroRepository,
            ContratoFileStorageService contratoFileStorageService) {
        this.contratoRepository = contratoRepository;
        this.clienteRepository = clienteRepository;
        this.puntoSuministroRepository = puntoSuministroRepository;
        this.contratoFileStorageService = contratoFileStorageService;
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
                contratoActual.setComentarioRenovacion(contrato.getComentarioRenovacion());

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

    @Transactional
    public Contrato guardarContratoConPdf(Contrato contrato, org.springframework.web.multipart.MultipartFile archivo) {
        Contrato guardado = guardarContrato(contrato);
        contratoFileStorageService.guardarPdf(guardado, archivo);
        return contratoRepository.save(guardado);
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

        // Cargar la información del cliente para cada contrato en una sola consulta
        // (antes se hacía un findById por contrato dentro del forEach: N consultas).
        List<Integer> clienteIds = contratos.stream()
                .map(c -> c.getCliente() != null ? c.getCliente().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (!clienteIds.isEmpty()) {
            Map<Integer, Cliente> clientesPorId = clienteRepository.findAllById(clienteIds).stream()
                    .collect(Collectors.toMap(Cliente::getId, c -> c));

            contratos.forEach(contrato -> {
                if (contrato.getCliente() != null) {
                    Cliente cliente = clientesPorId.get(contrato.getCliente().getId());
                    if (cliente != null) {
                        contrato.setCliente(cliente);
                    }
                }
            });
        }

        return contratos;
    }

    public void eliminarPorId(Integer id) {
        contratoRepository.deleteById(id);
    }

    /**
     * Renueva un contrato existente: crea uno nuevo con los datos recibidos y marca el original como vencido (fechaBaja = hoy).
     */
    @Transactional
    public Contrato renovarContrato(Integer idContratoOriginal, Contrato nuevoContrato) {
        Contrato original = contratoRepository.findById(idContratoOriginal)
                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado con ID: " + idContratoOriginal));

        // Garantizar que el nuevo contrato no tenga ID (se creará nuevo)
        nuevoContrato.setId(null);

        // Preservar asociaciones del original si no vienen en el nuevo
        if (nuevoContrato.getCliente() == null && original.getCliente() != null) {
            nuevoContrato.setCliente(original.getCliente());
        } else if (nuevoContrato.getCliente() != null && nuevoContrato.getCliente().getId() != null) {
            clienteRepository.findById(nuevoContrato.getCliente().getId())
                    .ifPresent(nuevoContrato::setCliente);
        }
        if (nuevoContrato.getPuntoSuministro() == null && original.getPuntoSuministro() != null) {
            nuevoContrato.setPuntoSuministro(original.getPuntoSuministro());
        } else if (nuevoContrato.getPuntoSuministro() != null && nuevoContrato.getPuntoSuministro().getId() != null) {
            puntoSuministroRepository.findById(nuevoContrato.getPuntoSuministro().getId())
                    .ifPresent(nuevoContrato::setPuntoSuministro);
        }

        // Guardar nuevo contrato
        Contrato guardado = contratoRepository.save(nuevoContrato);

        // Marcar original como vencido
        original.setFechaBaja(Date.valueOf(LocalDate.now()));
        contratoRepository.save(original);

        logger.info("Contrato {} renovado. Nuevo contrato ID: {}. Original marcado como vencido.", idContratoOriginal, guardado.getId());
        return guardado;
    }

    @Transactional
    public Contrato renovarContratoConPdf(Integer idContratoOriginal, Contrato nuevoContrato,
            org.springframework.web.multipart.MultipartFile archivo) {
        Contrato guardado = renovarContrato(idContratoOriginal, nuevoContrato);
        contratoFileStorageService.guardarPdf(guardado, archivo);
        return contratoRepository.save(guardado);
    }

    public java.nio.file.Path obtenerRutaPdf(Integer id) {
        Contrato contrato = contratoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado con ID: " + id));
        return contratoFileStorageService.obtenerPdf(contrato);
    }

    /**
     * Actualiza solo el campo comentarioRenovacion de un contrato.
     */
    @Transactional
    public Contrato actualizarComentarioRenovacion(Integer id, String comentario) {
        Contrato contrato = contratoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado con ID: " + id));
        // Trim: espacios o saltos de línea solos no cuentan como comentario
        String limpio = comentario != null ? comentario.trim() : null;
        contrato.setComentarioRenovacion(limpio == null || limpio.isEmpty() ? null : limpio);
        return contratoRepository.save(contrato);
    }
}
