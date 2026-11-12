package api.aura.service;

import api.aura.entity.Servicio;
import api.aura.entity.ServiciosPagos;
import api.aura.repository.ServicioRepository;
import api.aura.repository.ServiciosPagosRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** La ventana de servicios pide los planes de todos sus servicios en una sola consulta. */
@SpringBootTest
@Transactional
class PagosVariosServiciosTest {

    @Autowired private ServiciosPagosService servicio;
    @Autowired private ServicioRepository servicios;
    @Autowired private ServiciosPagosRepository pagos;

    @Test
    void devuelve_los_planes_de_los_servicios_pedidos_y_no_los_de_otros() {
        Servicio conPlan = servicios.save(Servicio.builder().descripcion("Con plan").build());
        Servicio sinPlan = servicios.save(Servicio.builder().descripcion("Sin plan").build());
        Servicio otro = servicios.save(Servicio.builder().descripcion("Otro").build());
        pagos.save(ServiciosPagos.builder().servicio(conPlan).importeFinal(100.0).build());
        pagos.save(ServiciosPagos.builder().servicio(otro).importeFinal(50.0).build());

        List<ServiciosPagos> planes = servicio.obtenerPagosPorServicios(List.of(conPlan.getId(), sinPlan.getId()));

        assertThat(planes).extracting(p -> p.getServicio().getId()).containsExactly(conPlan.getId());
        assertThat(servicio.obtenerPagosPorServicios(List.of())).isEmpty();
    }
}
