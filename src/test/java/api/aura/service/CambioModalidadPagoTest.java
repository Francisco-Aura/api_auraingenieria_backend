package api.aura.service;

import api.aura.entity.Servicio;
import api.aura.entity.ServiciosPagos;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.repository.ServiciosPagosRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Al pasar a una modalidad con menos cuotas, las que sobran no pueden seguir pendientes
 * (las reclamaba «Servicios pendientes de pago» y no había forma de quitarlas).
 */
class CambioModalidadPagoTest {

    private final ServiciosPagosRepository repositorio = mock(ServiciosPagosRepository.class);
    private final TBPagoPlazosRepository plazos = mock(TBPagoPlazosRepository.class);
    private final ServiciosPagosService servicio = new ServiciosPagosService();
    private final ServiciosPagos existente = new ServiciosPagos();

    CambioModalidadPagoTest() {
        ReflectionTestUtils.setField(servicio, "serviciosPagosRepository", repositorio);
        ReflectionTestUtils.setField(servicio, "pagoPlazosRepository", plazos);
        existente.setId(1);
        existente.setServicio(Servicio.builder().id(7).build());
        // Plan de 12 cuotas de 100 €: la 1 pagada; la 5 también (pagada aunque sobre)
        existente.setPrimerPagoImporte(100.0);
        existente.setPrimerPagoPagado(true);
        existente.setCuartoPagoImporte(100.0);
        existente.setCuartoPagoFecha(Date.valueOf("2026-04-01"));
        existente.setCuartoPagoPagado(false);
        existente.setQuintoPagoImporte(100.0);
        existente.setQuintoPagoPagado(true);
        existente.setDecimosegundoPagoImporte(100.0);
        existente.setDecimosegundoPagoPagado(false);
        when(repositorio.findByServicioId(7)).thenReturn(Optional.of(existente));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(plazos.findById(3)).thenReturn(Optional.of(TBPagoPlazos.builder().id(3).numeroCuotas(3).build()));
    }

    private ServiciosPagos pasarATresCuotas() {
        ServiciosPagos cuerpo = new ServiciosPagos();
        cuerpo.setServicio(Servicio.builder().id(7).build());
        cuerpo.setPagoPlazos(TBPagoPlazos.builder().id(3).build());
        return cuerpo;
    }

    @Test
    void las_cuotas_que_sobran_sin_pagar_dejan_de_estar_pendientes() {
        ServiciosPagos guardado = servicio.guardar(pasarATresCuotas());

        assertThat(guardado.getCuartoPagoPagado()).isTrue();
        assertThat(guardado.getCuartoPagoImporte()).isNull();
        assertThat(guardado.getCuartoPagoFecha()).isNull();
        assertThat(guardado.getDecimosegundoPagoPagado()).isTrue();
        assertThat(guardado.getDecimosegundoPagoImporte()).isNull();
    }

    @Test
    void lo_ya_pagado_no_se_toca() {
        ServiciosPagos guardado = servicio.actualizarPagosServicio(7, pasarATresCuotas());

        assertThat(guardado.getPrimerPagoImporte()).isEqualTo(100.0);
        assertThat(guardado.getQuintoPagoImporte()).as("pagada aunque sobre").isEqualTo(100.0);
        assertThat(guardado.getQuintoPagoPagado()).isTrue();
    }

    @Test
    void sin_modalidad_en_la_peticion_no_se_cierra_nada() {
        ServiciosPagos cuerpo = new ServiciosPagos();
        cuerpo.setServicio(Servicio.builder().id(7).build());

        ServiciosPagos guardado = servicio.guardar(cuerpo);

        assertThat(guardado.getCuartoPagoPagado()).isFalse();
        assertThat(guardado.getCuartoPagoImporte()).isEqualTo(100.0);
    }

    @Test
    void guardar_con_la_misma_modalidad_no_cierra_cuotas() {
        // El plan ya era de la modalidad 3 y en Administración se le bajaron las cuotas:
        // editar el servicio no puede borrar las cuotas que tiene
        existente.setPagoPlazos(TBPagoPlazos.builder().id(3).build());

        ServiciosPagos guardado = servicio.guardar(pasarATresCuotas());

        assertThat(guardado.getCuartoPagoPagado()).isFalse();
        assertThat(guardado.getCuartoPagoImporte()).isEqualTo(100.0);
        assertThat(guardado.getCuartoPagoFecha()).isEqualTo(Date.valueOf("2026-04-01"));
    }
}
