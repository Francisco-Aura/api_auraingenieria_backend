package api.aura.service;

import api.aura.entity.Servicio;
import api.aura.entity.ServiciosPagos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicioConPagosService {

    private final ServicioService servicioService;
    private final ServiciosPagosService serviciosPagosService;

    public ServicioConPagosService(
            ServicioService servicioService,
            ServiciosPagosService serviciosPagosService) {
        this.servicioService = servicioService;
        this.serviciosPagosService = serviciosPagosService;
    }

    @Transactional
    public Servicio guardar(Servicio servicio, ServiciosPagos pagos) {
        if (servicio == null) {
            throw new IllegalArgumentException("Los datos del servicio son obligatorios");
        }

        Servicio guardado = servicioService.guardarServicio(servicio);
        if (pagos != null) {
            pagos.setServicio(guardado);
            serviciosPagosService.guardar(pagos);
        }
        return guardado;
    }
}
