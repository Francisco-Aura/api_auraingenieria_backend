package api.aura.service;

import api.aura.entity.Servicio;
import api.aura.entity.ServiciosPagos;
import api.aura.repository.ServiciosPagosRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El alta de pagos de un servicio queda siempre ligada al servicio de la ruta. */
class ServiciosPagosAltaTest {

    private final ServiciosPagosRepository repositorio = mock(ServiciosPagosRepository.class);
    private final ServiciosPagosService servicio = new ServiciosPagosService();

    ServiciosPagosAltaTest() {
        ReflectionTestUtils.setField(servicio, "serviciosPagosRepository", repositorio);
        when(repositorio.findByServicioId(7)).thenReturn(Optional.empty());
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sin_servicio_en_el_cuerpo_se_liga_al_de_la_ruta() {
        ServiciosPagos guardado = servicio.actualizarPagosServicio(7, new ServiciosPagos());
        assertThat(guardado.getServicio().getId()).isEqualTo(7);
    }

    @Test
    void con_el_id_de_otro_registro_no_lo_sobrescribe() {
        ServiciosPagos cuerpo = new ServiciosPagos();
        cuerpo.setId(99);
        cuerpo.setServicio(Servicio.builder().id(3).build());

        ServiciosPagos guardado = servicio.actualizarPagosServicio(7, cuerpo);

        assertThat(guardado.getId()).isNull();
        assertThat(guardado.getServicio().getId()).isEqualTo(7);
    }

    @Test
    void si_ya_trae_su_servicio_se_conserva_tal_cual() {
        Servicio s = Servicio.builder().id(7).build();
        ServiciosPagos cuerpo = new ServiciosPagos();
        cuerpo.setServicio(s);

        assertThat(servicio.actualizarPagosServicio(7, cuerpo).getServicio()).isSameAs(s);
    }
}
