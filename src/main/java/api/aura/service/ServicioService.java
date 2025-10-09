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

    public Servicio guardarServicio(Servicio servicio) {
        logger.info("Guardando servicio para cliente ID: {}", servicio.getCliente() != null ? servicio.getCliente().getId() : "null");
        
        // Si el servicio tiene ID, es una actualización
        if (servicio.getId() != null) {
            logger.info("Actualizando servicio existente con ID: {}", servicio.getId());
            
            // Buscar el servicio existente
            Optional<Servicio> servicioExistente = servicioRepository.findById(servicio.getId());
            if (servicioExistente.isPresent()) {
                Servicio servicioActual = servicioExistente.get();
                
                // Actualizar todos los campos explícitamente
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
                servicioActual.setImporteFinal(servicio.getImporteFinal());
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
            // Si no tiene cliente pero tiene punto de suministro, buscamos el cliente a través del punto de suministro
            Optional<PuntoSuministro> puntoSuministroOpt = puntoSuministroRepository.findById(servicio.getPuntoSuministro().getId());
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

        // COMENTADO: Esta lógica automática de cierre de servicios anteriores
        // causaba que se modificaran las fechas de servicios existentes al crear nuevos servicios.
        // Se debe manejar la gestión de fechas de manera explícita desde el frontend cuando sea necesario.
        /*
        // Si es un nuevo servicio y tiene punto de suministro, cerrar servicios anteriores
        if (servicio.getFechaInicioServicio() != null) {
            Integer puntoSuministroId = servicio.getPuntoSuministro().getId();
            List<Servicio> serviciosAnteriores = servicioRepository.findByPuntoSuministroId(puntoSuministroId);
            for (Servicio anterior : serviciosAnteriores) {
                // Solo cerrar los que estén activos (sin fecha de fin o baja)
                if ((anterior.getFechaFinServicio() == null || anterior.getFechaFinServicio().after(servicio.getFechaInicioServicio())) &&
                    (anterior.getFechaBaja() == null)) {
                    // Cerrar el anterior el día antes del nuevo inicio
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(servicio.getFechaInicioServicio());
                    cal.add(Calendar.DATE, -1);
                    java.sql.Date diaAnterior = new java.sql.Date(cal.getTimeInMillis());
                    anterior.setFechaFinServicio(diaAnterior);
                    anterior.setFechaBaja(diaAnterior);
                    servicioRepository.save(anterior);
                }
            }
        }
        */
        
        return servicioRepository.save(servicio);
    }

    public Servicio renovarServicio(Integer id) {
        Optional<Servicio> originalOpt = servicioRepository.findById(id);
        if (originalOpt.isEmpty()) return null;
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
        nuevo.setImporteFinal(original.getImporteFinal());
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
        List<Servicio> servicios = servicioRepository.findByPuntoSuministroId(puntoSuministroId);
        
        // Cargar la información del cliente para cada servicio
        servicios.forEach(servicio -> {
            if (servicio.getCliente() != null) {
                // Cargar la información completa del cliente
                clienteRepository.findById(servicio.getCliente().getId())
                    .ifPresent(cliente -> {
                        servicio.setCliente(cliente);
                    });
            }
        });
        
        return servicios.stream().map(this::toDTO).toList();
    }

    public void eliminarPorId(Integer id) {
        servicioRepository.deleteById(id);
    }
}
