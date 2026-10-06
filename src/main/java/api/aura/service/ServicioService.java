package api.aura.service;

import api.aura.entity.Servicio;
import api.aura.entity.Cliente;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.ServicioRepository;
import api.aura.repository.ClienteRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.dto.ServicioDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.List;
import java.util.Optional;

@Service
public class ServicioService {

    private static final Logger logger = LoggerFactory.getLogger(ServicioService.class);

    @Autowired
    private ServicioRepository servicioRepository;
    @Autowired
    private PuntoSuministroRepository puntoSuministroRepository;
    @Autowired
    private ClienteRepository clienteRepository;

    @Transactional
    public Servicio guardarServicio(Servicio servicio) {
        logger.info("Guardando servicio para cliente ID: {}",
                servicio.getCliente() != null ? servicio.getCliente().getId() : "null");

        // Si el servicio tiene ID, es una actualización
        if (servicio.getId() != null) {
            logger.info("Actualizando servicio existente con ID: {}", servicio.getId());

            Optional<Servicio> servicioExistente = servicioRepository.findById(servicio.getId());
            if (servicioExistente.isPresent()) {
                Servicio servicioActual = servicioExistente.get();

                servicioActual.setFecha(servicio.getFecha());
                servicioActual.setFechaInicioServicio(servicio.getFechaInicioServicio());
                servicioActual.setFechaFinServicio(servicio.getFechaFinServicio());
                servicioActual.setFechaPago(servicio.getFechaPago());
                servicioActual.setFechaBaja(servicio.getFechaBaja());
                servicioActual.setDescripcion(servicio.getDescripcion());
                servicioActual.setIdGestora(servicio.getIdGestora());
                servicioActual.setIdProducto(servicio.getIdProducto());
                servicioActual.setIdComercializadora(servicio.getIdComercializadora());
                servicioActual.setObservaciones(servicio.getObservaciones());
                servicioActual.setCups(servicio.getCups());
                servicioActual.setImporte(servicio.getImporte());
                servicioActual.setDescuento(servicio.getDescuento());
                servicioActual.setImporteFinal(
                        resolverImporteFinal(servicio.getImporte(), servicio.getDescuento(), servicio.getImporteFinal()));
                servicioActual.setIdPagoPlazos(servicio.getIdPagoPlazos());

                logger.info("Actualizando modalidad de pago: {} -> {}",
                        servicioActual.getIdPagoPlazos(), servicio.getIdPagoPlazos());

                logger.info("Campos del servicio actualizados. Guardando en base de datos...");
                return servicioRepository.save(servicioActual);
            } else {
                logger.error("No se encontró el servicio con ID: {}", servicio.getId());
                throw new IllegalArgumentException("No se encontró el servicio con ID: " + servicio.getId());
            }
        }

        // Si no tiene ID, es un servicio nuevo
        logger.info("Creando nuevo servicio");

        // Si el servicio tiene un cliente pero no está cargado completamente
        if (servicio.getCliente() != null && servicio.getCliente().getId() != null) {
            Optional<Cliente> clienteOpt = clienteRepository.findById(servicio.getCliente().getId());
            if (clienteOpt.isPresent()) {
                servicio.setCliente(clienteOpt.get());
                logger.info("Cliente asociado correctamente al servicio");
            } else {
                logger.error("No se encontró el cliente con ID: {}", servicio.getCliente().getId());
                throw new IllegalArgumentException("No se encontró el cliente especificado");
            }
        } else if (servicio.getPuntoSuministro() != null && servicio.getPuntoSuministro().getId() != null) {
            // Si no tiene cliente pero tiene punto de suministro, buscamos el cliente a
            // través del punto de suministro
            Optional<PuntoSuministro> puntoSuministroOpt = puntoSuministroRepository
                    .findById(servicio.getPuntoSuministro().getId());
            if (puntoSuministroOpt.isPresent() && puntoSuministroOpt.get().getCliente() != null) {
                servicio.setCliente(puntoSuministroOpt.get().getCliente());
                logger.info("Cliente asociado al servicio a través del punto de suministro");
            } else {
                logger.error("No se encontró el punto de suministro o no tiene cliente asociado");
                throw new IllegalArgumentException("El punto de suministro no tiene un cliente asociado");
            }
        } else {
            logger.error("El servicio no tiene un cliente asociado");
            throw new IllegalArgumentException("El servicio debe tener un cliente asociado");
        }

        servicio.setImporteFinal(
                resolverImporteFinal(servicio.getImporte(), servicio.getDescuento(), servicio.getImporteFinal()));

        return servicioRepository.save(servicio);
    }

    /**
     * Garantiza que importeFinal quede siempre informado cuando importe (y
     * opcionalmente descuento) tienen valor, en lugar de confiar ciegamente en
     * lo que envíe el cliente. Evita que servicios queden con importeFinal en
     * blanco en los informes aunque importe/descuento sí estén indicados.
     */
    private Double resolverImporteFinal(Double importe, Double descuento, Double importeFinal) {
        if (importeFinal != null) {
            return importeFinal;
        }
        if (importe != null) {
            return importe - (descuento != null ? descuento : 0d);
        }
        return null;
    }

    @Transactional
    public Servicio renovarServicio(Integer id) {
        Optional<Servicio> originalOpt = servicioRepository.findById(id);
        if (originalOpt.isEmpty())
            return null;
        Servicio original = originalOpt.get();
        Servicio nuevo = new Servicio();

        // Copiar campos relevantes
        nuevo.setCliente(original.getCliente());
        nuevo.setPuntoSuministro(original.getPuntoSuministro());
        nuevo.setDescripcion(original.getDescripcion());
        nuevo.setIdGestora(original.getIdGestora());
        nuevo.setIdProducto(original.getIdProducto());
        nuevo.setIdComercializadora(original.getIdComercializadora());
        nuevo.setObservaciones(original.getObservaciones());
        nuevo.setCups(original.getCups());
        nuevo.setImporte(original.getImporte());
        nuevo.setDescuento(original.getDescuento());
        nuevo.setImporteFinal(
                resolverImporteFinal(original.getImporte(), original.getDescuento(), original.getImporteFinal()));
        nuevo.setIdPagoPlazos(original.getIdPagoPlazos());

        // Fechas: inicio hoy, fin +1 año
        java.sql.Date hoy = new java.sql.Date(System.currentTimeMillis());
        nuevo.setFechaInicioServicio(hoy);
        Calendar cal = Calendar.getInstance();
        cal.setTime(hoy);
        cal.add(Calendar.YEAR, 1);
        nuevo.setFechaFinServicio(new java.sql.Date(cal.getTimeInMillis()));

        // Otros campos
        nuevo.setFecha(hoy);
        nuevo.setFechaPago(null);
        nuevo.setFechaBaja(null);

        // Guardar
        return servicioRepository.save(nuevo);
    }

    public ServicioDTO toDTO(Servicio servicio) {
        ServicioDTO dto = new ServicioDTO();
        dto.setId(servicio.getId());
        dto.setFecha(servicio.getFecha());
        dto.setFechaInicioServicio(servicio.getFechaInicioServicio());
        dto.setFechaFinServicio(servicio.getFechaFinServicio());
        dto.setFechaPago(servicio.getFechaPago());
        dto.setDescripcion(servicio.getDescripcion());
        dto.setIdGestora(servicio.getIdGestora());
        dto.setIdProducto(servicio.getIdProducto());
        dto.setIdComercializadora(servicio.getIdComercializadora());
        dto.setObservaciones(servicio.getObservaciones());
        dto.setImporte(servicio.getImporte());
        dto.setDescuento(servicio.getDescuento());
        dto.setImporteFinal(servicio.getImporteFinal());
        dto.setIdPagoPlazos(servicio.getIdPagoPlazos());
        dto.setIdCliente(servicio.getCliente() != null ? servicio.getCliente().getId() : null);
        dto.setIdPuntoSuministro(servicio.getPuntoSuministro() != null ? servicio.getPuntoSuministro().getId() : null);
        // CUPS: lo traemos del punto de suministro si no está en el servicio
        String cups = servicio.getCups();
        if ((cups == null || cups.isEmpty()) && servicio.getPuntoSuministro() != null) {
            PuntoSuministro ps = servicio.getPuntoSuministro();
            cups = ps.getCUPSluz() != null && !ps.getCUPSluz().isEmpty() ? ps.getCUPSluz() : ps.getCUPSgas();
        }
        dto.setCups(cups);
        // Pagos: por ahora null o vacío
        dto.setPagos(null);
        return dto;
    }

    public List<ServicioDTO> obtenerTodos() {
        List<Servicio> servicios = servicioRepository.findAll();
        return servicios.stream().map(this::toDTO).toList();
    }

    public Optional<ServicioDTO> obtenerPorId(Integer id) {
        return servicioRepository.findById(id).map(this::toDTO);
    }

    public List<ServicioDTO> obtenerPorClienteId(Integer clienteId) {
        return servicioRepository.findByClienteId(clienteId).stream().map(this::toDTO).toList();
    }

    public List<ServicioDTO> obtenerPorPuntoSuministroId(Integer puntoSuministroId) {
        return servicioRepository.findByPuntoSuministroIdWithCliente(puntoSuministroId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public void eliminarPorId(Integer id) {
        servicioRepository.deleteById(id);
    }

    public java.util.Set<Integer> obtenerIdsPuntosConServicioActivo(java.util.List<Integer> idProductos) {
        return servicioRepository.findIdsPuntosSuministroConServicioActivo(idProductos);
    }

    public long contarServiciosActivos() {
        return servicioRepository.countServiciosActivos();
    }
}
