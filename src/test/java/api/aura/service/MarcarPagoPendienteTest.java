package api.aura.service;

import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.InformesRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.tablas_maestras.TBSectoresRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * «Marcar como pagado» en un servicio que no tiene ninguna cuota vencida debe
 * decirlo con esas palabras. {@code queryForObject} lanza
 * {@link EmptyResultDataAccessException} cuando no hay filas, así que el
 * {@code catch (Exception)} se tragaba el mensaje y el usuario veía siempre
 * «No se pudo marcar el pago. Inténtelo de nuevo».
 */
class MarcarPagoPendienteTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final InformesService servicio = new InformesService(
            mock(InformesRepository.class), mock(ContratoRepository.class),
            mock(PuntoSuministroRepository.class), mock(ClienteRepository.class),
            mock(TBSectoresRepository.class));

    private InformesService conJdbc() {
        ReflectionTestUtils.setField(servicio, "jdbcTemplate", jdbc);
        return servicio;
    }

    @Test
    void sin_cuotas_pendientes_lo_dice_con_claridad() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> conJdbc().marcarSiguientePagoPendiente(77L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No hay pagos pendientes");
        verify(jdbc, never()).update(anyString(), any(), any());
    }

    @Test
    void con_una_cuota_vencida_la_marca_como_pagada() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(3);

        conJdbc().marcarSiguientePagoPendiente(77L);

        verify(jdbc).update("UPDATE servicios_pagos SET tercer_pago_pagado = ? WHERE id_servicio = ?", 1, 77L);
    }

    private static <T> Class<T> eq(Class<T> tipo) {
        return org.mockito.ArgumentMatchers.eq(tipo);
    }
}
