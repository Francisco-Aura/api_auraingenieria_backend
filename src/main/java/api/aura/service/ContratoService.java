package api.aura.service;

import api.aura.entity.Contrato;
import api.aura.entity.Cliente;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.ContratoRepository;
import api.aura.repository.ClienteRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.service.mcas.McasProposalService;
import api.aura.util.BajaEfectiva;
import api.aura.util.FechaNegocio;
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
                rellenarUltimaRelacion(contratoActual);

                logger.info("Campos del contrato actualizados. Guardando en base de datos...");
                Contrato guardado = contratoRepository.save(contratoActual);
                dispararMcasSiProcede(guardado);
                avisarCambioEnElPunto(guardado);
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

        rellenarUltimaRelacion(contrato);
        Contrato guardado = contratoRepository.save(contrato);
        dispararMcasSiProcede(guardado);
        avisarCambioEnElPunto(guardado);
        return guardado;
    }

    /**
     * Guarda un contrato cambiado desde otra pantalla (p. ej. el informe de contratos
     * pendientes de renovación) como si se guardase desde su formulario: recalcula la
     * última relación con la comercializadora y, si cambia algo de la comisión, MCAS la
     * vuelve a calcular. Sin esto la propuesta pendiente se quedaba desactualizada.
     */
    @Transactional
    public Contrato guardarCambiosDeOtraPantalla(Contrato contrato) {
        rellenarUltimaRelacion(contrato);
        Contrato guardado = contratoRepository.save(contrato);
        dispararMcasSiProcede(guardado);
        avisarCambioEnElPunto(guardado);
        return guardado;
    }

    /**
     * «Última relación con la comercializadora»: ya no se escribe a mano (reunión del
     * 04/11/2026). Es el final del contrato anterior del mismo punto de suministro si
     * era con la misma comercializadora: su fecha fin o, si se dio de baja antes, la
     * de baja. Si el anterior es de otra comercializadora, o no hay, se queda vacía.
     * En una renovación cuenta su fecha fin: la baja del original la pone la propia
     * renovación (el día en que se renueva) y no es una baja del cliente.
     */
    public void rellenarUltimaRelacion(Contrato contrato) {
        contrato.setMcasFechaUltimaRelacionComercializadora(null);
        Integer idPunto = contrato.getPuntoSuministro() != null ? contrato.getPuntoSuministro().getId() : null;
        Integer comercializadora = contrato.getIdComercializadoraActual();
        if (idPunto == null || comercializadora == null || comercializadora == 0) return;
        // El último contrato anterior del punto CON ESA comercializadora, aunque después
        // hubiera otra (Endesa, luego Iberdrola, otra vez Endesa: cuenta la relación con
        // Endesa; respuesta del cliente a la duda 22). Antes solo se miraba el inmediatamente
        // anterior y en ese caso salía vacía.
        contratoRepository.findByPuntoSuministroId(idPunto).stream()
                .filter(otro -> otro.getId() != null && !otro.getId().equals(contrato.getId()))
                .filter(otro -> comercializadora.equals(otro.getIdComercializadoraActual()))
                .filter(otro -> esAnterior(otro, contrato))
                .max(java.util.Comparator.comparing((Contrato otro) -> FechaNegocio.aLocalDate(otro.getFechaInicio()),
                                java.util.Comparator.nullsFirst(java.util.Comparator.<LocalDate>naturalOrder()))
                        .thenComparing(Contrato::getId))
                .map(anterior -> finDeRelacion(anterior, Boolean.TRUE.equals(contrato.getMcasEsRenovacion())))
                .ifPresent(contrato::setMcasFechaUltimaRelacionComercializadora);
    }

    /** Empezó antes (por fecha de inicio; sin fechas, por orden de alta). */
    private static boolean esAnterior(Contrato otro, Contrato contrato) {
        LocalDate suyo = FechaNegocio.aLocalDate(otro.getFechaInicio());
        LocalDate este = FechaNegocio.aLocalDate(contrato.getFechaInicio());
        if (suyo != null && este != null) return suyo.isBefore(este);
        return contrato.getId() == null || otro.getId() < contrato.getId();
    }

    /** Fecha fin del contrato o, si se dio de baja antes (y no es por renovarlo), la de baja. */
    private static java.util.Date finDeRelacion(Contrato c, boolean renovacion) {
        java.util.Date fin = c.getFechaFin();
        java.util.Date baja = !renovacion && BajaEfectiva.tieneBaja(c.getFechaBaja()) ? c.getFechaBaja() : null;
        if (baja != null && (fin == null || baja.before(fin))) return baja;
        return fin;
    }

    private void copiarCamposMcas(Contrato origen, Contrato destino) {
        destino.setMcasFeeEurMwh(origen.getMcasFeeEurMwh());
        destino.setMcasFeeUnidad(origen.getMcasFeeUnidad());
        destino.setMcasTipoPrecio(origen.getMcasTipoPrecio());
        destino.setMcasTipoSuministro(origen.getMcasTipoSuministro());
        destino.setMcasSegmento(origen.getMcasSegmento());
        destino.setMcasEsRenovacion(origen.getMcasEsRenovacion());
        destino.setMcasTipoRenovacion(origen.getMcasTipoRenovacion());
        destino.setMcasIdGestora(origen.getMcasIdGestora());
        destino.setMcasIdProductoComercial(origen.getMcasIdProductoComercial());
        // Ya no se rellenan a mano: la potencia es siempre la mayor contratada, el nº de
        // pagos y el primer cobro salen de las condiciones de cobro de la comercializadora
        // (Configuración MCAS), la zona sale de la provincia del punto (la pone MCAS al
        // calcular) y el ámbito era lo mismo que la zona. Los valores antiguos se borran
        destino.setMcasAmbito(null);
        destino.setMcasPotenciaCalculo(null);
        destino.setMcasNumeroPagos(null);
        destino.setMcasFechaPrimeraAplicacion(null);
        destino.setMcasAltaNueva(origen.getMcasAltaNueva());
    }

    /**
     * Cualquier cambio en los contratos de un punto puede dar lugar a una retrocesión (baja,
     * cambio de comercializadora) o dejar una sin sentido: se revisa tras el COMMIT.
     */
    private void avisarCambioEnElPunto(Contrato contrato) {
        if (contrato.getPuntoSuministro() != null && contrato.getPuntoSuministro().getId() != null)
            eventPublisher.publishEvent(new api.aura.service.mcas.McasBajaEvent(contrato.getPuntoSuministro().getId()));
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

    @Transactional
    public void eliminarPorId(Integer id) {
        // Su PDF se quedaba huérfano en disco: se borra cuando el borrado queda confirmado
        contratoRepository.findById(id).ifPresent(contrato -> {
            // Un contrato borrado no llegó a hacerse: su comisión y su servicio tampoco
            // (en la misma transacción; una baja del contrato no pasa por aquí)
            mcasProposalService.eliminarComisionesDelContrato(id);
            contratoFileStorageService.eliminarPdfSiExiste(contrato);
            contratoRepository.delete(contrato);
            avisarCambioEnElPunto(contrato);
        });
    }

    /**
     * Renueva un contrato existente: crea uno nuevo con los datos recibidos y marca el original como vencido (fechaBaja = hoy).
     */
    @Transactional
    public Contrato renovarContrato(Integer idContratoOriginal, Contrato nuevoContrato) {
        // Bloqueo del original hasta el final de la transacción: dos peticiones de
        // renovación del mismo contrato (doble envío, dos pestañas) van una detrás de otra
        Contrato original = contratoRepository.findByIdForUpdate(idContratoOriginal)
                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado con ID: " + idContratoOriginal));

        // Garantizar que el nuevo contrato no tenga ID (se creará nuevo)
        nuevoContrato.setId(null);
        nuevoContrato.setMcasEsRenovacion(true);
        // El formulario copia el contrato original, también su baja: la renovación de un
        // contrato ya dado de baja nacía de baja y no salía en ningún informe
        nuevoContrato.setFechaBaja(null);

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

        // Si la misma renovación ya se ha guardado (mismo punto, número y fechas), se
        // devuelve esa en vez de crear un contrato duplicado
        Contrato yaRenovado = renovacionIgual(original, nuevoContrato);
        if (yaRenovado != null) {
            logger.info("Renovación repetida del contrato {}: se devuelve la ya creada (ID {})",
                    idContratoOriginal, yaRenovado.getId());
            return yaRenovado;
        }

        // Guardar nuevo contrato
        rellenarUltimaRelacion(nuevoContrato);
        Contrato guardado = contratoRepository.save(nuevoContrato);

        // Marcar original como vencido (si ya tenía una baja real, se conserva su fecha)
        if (BajaEfectiva.estaActivo(original.getFechaBaja())) {
            original.setFechaBaja(Date.valueOf(LocalDate.now()));
            contratoRepository.save(original);
        }
        dispararMcasSiProcede(guardado);
        avisarCambioEnElPunto(guardado);

        logger.info("Contrato {} renovado. Nuevo contrato ID: {}. Original marcado como vencido.", idContratoOriginal, guardado.getId());
        return guardado;
    }

    /**
     * Renovación ya guardada por una petición anterior (doble envío): un contrato del
     * mismo punto creado después del original, con el mismo número y fechas. El original
     * nunca cuenta: si venía de otra renovación y se dejaban su número y fechas, la
     * renovación devolvía el propio original y no creaba nada.
     */
    private Contrato renovacionIgual(Contrato original, Contrato nuevo) {
        if (nuevo.getPuntoSuministro() == null || nuevo.getPuntoSuministro().getId() == null) return null;
        return contratoRepository.findByPuntoSuministroId(nuevo.getPuntoSuministro().getId()).stream()
                .filter(c -> c.getId() != null && original.getId() != null && c.getId() > original.getId())
                .filter(c -> Boolean.TRUE.equals(c.getMcasEsRenovacion()))
                // Una renovación que después se dio de baja ya no cuenta: hay que crear otra
                .filter(c -> BajaEfectiva.estaActivo(c.getFechaBaja()))
                .filter(c -> java.util.Objects.equals(c.getContrato(), nuevo.getContrato()))
                .filter(c -> java.util.Objects.equals(FechaNegocio.aLocalDate(c.getFechaInicio()), FechaNegocio.aLocalDate(nuevo.getFechaInicio())))
                .filter(c -> java.util.Objects.equals(FechaNegocio.aLocalDate(c.getFechaFin()), FechaNegocio.aLocalDate(nuevo.getFechaFin())))
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public Contrato renovarContratoConPdf(Integer idContratoOriginal, Contrato nuevoContrato,
            org.springframework.web.multipart.MultipartFile archivo) {
        Contrato guardado = renovarContrato(idContratoOriginal, nuevoContrato);
        // En una renovación repetida que ya tiene su PDF no se sube otra copia
        if (guardado.getRutaArchivoContrato() != null && !guardado.getRutaArchivoContrato().isBlank()) return guardado;
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
