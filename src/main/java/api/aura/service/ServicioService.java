package api.aura.service;

import api.aura.entity.Servicio;
import api.aura.repository.ServicioRepository;
import api.aura.dto.ServicioDTO;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.PuntoSuministroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ServicioService {

    @Autowired
    private ServicioRepository servicioRepository;
    @Autowired
    private PuntoSuministroRepository puntoSuministroRepository;

    public Servicio guardarServicio(Servicio servicio) {
        // Si es un nuevo servicio (sin ID) y tiene punto de suministro
        if (servicio.getId() == null && servicio.getPuntoSuministro() != null && servicio.getFechaInicioServicio() != null) {
            Integer puntoSuministroId = servicio.getPuntoSuministro().getId();
            List<Servicio> serviciosAnteriores = servicioRepository.findByPuntoSuministroId(puntoSuministroId);
            for (Servicio anterior : serviciosAnteriores) {
                // Solo cerrar los que estén activos (sin fecha de fin o baja)
                if ((anterior.getFechaFinServicio() == null || anterior.getFechaFinServicio().after(servicio.getFechaInicioServicio())) &&
                    (anterior.getFechaBaja() == null)) {
                    // Cerrar el anterior el día antes del nuevo inicio
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTime(servicio.getFechaInicioServicio());
                    cal.add(java.util.Calendar.DATE, -1);
                    java.sql.Date diaAnterior = new java.sql.Date(cal.getTimeInMillis());
                    anterior.setFechaFinServicio(diaAnterior);
                    anterior.setFechaBaja(diaAnterior);
                    servicioRepository.save(anterior);
                }
            }
        }
        return servicioRepository.save(servicio);
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
        return servicioRepository.findByPuntoSuministroId(puntoSuministroId).stream().map(this::toDTO).toList();
    }

    public void eliminarPorId(Integer id) {
        servicioRepository.deleteById(id);
    }
}
