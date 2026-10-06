package api.aura.service;

import api.aura.entity.Contrato;
import api.aura.entity.Cliente;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.ContratoRepository;
import api.aura.repository.ClienteRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.service.mcas.McasProposalService;
import api.aura.util.BajaEfectiva;
import api.aura.service.mcas.McasContratoGuardadoEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ContratoService {

    private static final Logger logger = LoggerFactory.getLogger(ContratoService.class);

    private final ContratoRepository contratoRepository;
    private final ClienteRepository clienteRepository;
    private final PuntoSuministroRepository puntoSuministroRepository;
    private final ContratoFileStorageService contratoFileStorageService;
    private final McasProposalService mcasProposalService;
    private final ApplicationEventPublisher eventPublisher;

    public ContratoService(ContratoRepository contratoRepository,
            ClienteRepository clienteRepository,
            PuntoSuministroRepository puntoSuministroRepository,
            ContratoFileStorageService contratoFileStorageService,
            McasProposalService mcasProposalService,
            ApplicationEventPublisher eventPublisher) {
        this.contratoRepository = contratoRepository;
        this.clienteRepository = clienteRepository;
        this.puntoSuministroRepository = puntoSuministroRepository;
        this.contratoFileStorageService = contratoFileStorageService;
        this.mcasProposalService = mcasProposalService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Contrato guardarContrato(Contrato contrato) {
        logger.info("Guardando contrato para cliente ID: {}",
                contrato.getCliente() != null ? contrato.getCliente().getId() : "null");

        // Si el contrato tiene ID, es una actualización
        if (contrato.getId() != null) {
            logger.info("Actualizando contrato existente con ID: {}", contrato.getId());

            Optional<Contrato> contratoExistente = contratoRepository.findByIdForUpdate(contrato.getId());
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

                copiarCamposMcas(contrato, contratoActual);

                logger.info("Campos del contrato actualizados. Guardando en base de datos...");
                Contrato guardado = contratoRepository.save(contratoActual);
                dispararMcasSiProcede(guardado);
                return guardado;
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

        Contrato guardado = contratoRepository.save(contrato);
        dispararMcasSiProcede(guardado);
        return guardado;
    }

    private void copiarCamposMcas(Contrato origen, Contrato destino) {
        destino.setMcasFeeEurMwh(origen.getMcasFeeEurMwh());
        destino.setMcasFeeUnidad(origen.getMcasFeeUnidad());
        destino.setMcasTipoPrecio(origen.getMcasTipoPrecio());
        destino.setMcasTipoSuministro(origen.getMcasTipoSuministro());
        destino.setMcasSegmento(origen.getMcasSegmento());
        destino.setMcasEsRenovacion(origen.getMcasEsRenovacion());
        destino.setMcasTipoRenovacion(origen.getMcasTipoRenovacion());
        destino.setMcasAmbito(origen.getMcasAmbito());
        destino.setMcasIdGestora(origen.getMcasIdGestora());
        destino.setMcasIdProductoComercial(origen.getMcasIdProductoComercial());
        destino.setMcasZonaComision(origen.getMcasZonaComision());
        destino.setMcasPotenciaCalculo(origen.getMcasPotenciaCalculo());
        destino.setMcasNumeroPagos(origen.getMcasNumeroPagos());
        destino.setMcasFechaPrimeraAplicacion(origen.getMcasFechaPrimeraAplicacion());
        destino.setMcasAltaNueva(origen.getMcasAltaNueva());
        destino.setMcasFechaUltimaRelacionComercializadora(
                origen.getMcasFechaUltimaRelacionComercializadora());
    }

    private void dispararMcasSiProcede(Contrato contrato) {
        if (!contratoListoParaMcas(contrato)) {
            logger.debug("Contrato {} aun no esta listo para MCAS; no se genera propuesta de borrador",
                    contrato.getId());
            return;
        }
        // Con el módulo desactivado MCAS es inerte: ni crea propuestas ni toma
        // bloqueos sobre el contrato.
        if (!mcasProposalService.moduloHabilitado()) {
            logger.debug("MCAS deshabilitado (MCAS_ENABLED=false); no se dispara para contrato {}",
                    contrato.getId());
            return;
        }
        // La propuesta se genera tras el COMMIT del contrato (McasContratoEventListener),
        // para que un fallo de MCAS nunca revierta el guardado del contrato y para
        // evitar el interbloqueo del SELECT ... FOR UPDATE sobre la fila aún sin confirmar.
        eventPublisher.publishEvent(new McasContratoGuardadoEvent(contrato.getId()));
    }

    /**
     * MCAS se dispara al guardar un contrato comercialmente identificable, nunca
     * por completar parcialmente los campos de un borrador. Los datos propios de
     * cada estrategia se validan despues en el motor y pueden requerir revision.
     */
    private boolean contratoListoParaMcas(Contrato contrato) {
        return contrato.getId() != null
                && contrato.getMcasTipoPrecio() != null
                && !contrato.getMcasTipoPrecio().isBlank()
                && contrato.getMcasTipoSuministro() != null
                && !contrato.getMcasTipoSuministro().isBlank()
                && contrato.getContrato() != null
                && !contrato.getContrato().isBlank()
                && contrato.getFechaInicio() != null
                && contrato.getIdComercializadoraActual() != null
                && contrato.getIdComercializadoraActual() > 0;
    }

    /**
     * Igual que {@link #guardarContrato(Contrato)}, pero permite además eliminar
     * el PDF actualmente adjunto (usado cuando el usuario pulsa "Quitar" sobre
     * el documento ya guardado, sin subir uno nuevo).
     */
    @Transactional
    public Contrato guardarContrato(Contrato contrato, boolean eliminarArchivoExistente) {
        Contrato guardado = guardarContrato(contrato);
        if (eliminarArchivoExistente) {
            contratoFileStorageService.eliminarPdfSiExiste(guardado);
            guardado = contratoRepository.save(guardado);
        }
        return guardado;
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
        // El cliente de cada contrato ya viene cargado (relación EAGER, en lotes):
        // no hace falta volver a consultarlo.
        return contratoRepository.findByPuntoSuministroId(puntoSuministroId);
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
        nuevoContrato.setMcasEsRenovacion(true);

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

        // Marcar original como vencido (si ya tenía una baja real, se conserva su fecha)
        if (BajaEfectiva.estaActivo(original.getFechaBaja())) {
            original.setFechaBaja(Date.valueOf(LocalDate.now()));
            contratoRepository.save(original);
        }
        dispararMcasSiProcede(guardado);

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
