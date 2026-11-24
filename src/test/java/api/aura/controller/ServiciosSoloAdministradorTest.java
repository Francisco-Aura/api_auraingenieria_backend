package api.aura.controller;

import api.aura.config.AuraUserHolder;
import api.aura.config.UsuarioSesion;
import api.aura.service.ServicioService;
import api.aura.service.ServiciosPagosService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Servicios y sus pagos: la pantalla solo se los enseña a los administradores (gestión
 * de servicios e informes de pagos); un usuario solo usa los dos resúmenes del listado
 * de puntos de suministro.
 */
@ExtendWith(MockitoExtension.class)
class ServiciosSoloAdministradorTest {

    @Mock private ServicioService servicios;
    @Mock private ServiciosPagosService pagos;
    @InjectMocks private ServicioController servicioController;
    @InjectMocks private ServiciosPagosController pagosController;

    @AfterEach
    void cerrarSesion() {
        AuraUserHolder.clear();
    }

    @Test
    void un_usuario_no_consulta_servicios_ni_pagos() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));

        assertThatThrownBy(() -> servicioController.obtenerTodos()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicioController.obtenerPorId(1)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicioController.obtenerPorClienteId(1)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicioController.obtenerPorPuntoSuministroId(1)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> pagosController.obtenerPagosPorServicio(1)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> pagosController.obtenerCuotasPorServicio(1)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> pagosController.obtenerPagosPorServicios(List.of(1, 2))).isInstanceOf(SecurityException.class);
        verifyNoInteractions(servicios, pagos);
    }

    @Test
    void un_usuario_sigue_viendo_los_resumenes_del_listado_de_puntos() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));
        when(servicios.obtenerIdsPuntosConServicioActivo(any())).thenReturn(java.util.Set.of(3));

        assertThatCode(() -> servicioController.obtenerPuntosConServicioActivo(List.of(1))).doesNotThrowAnyException();
        assertThatCode(() -> servicioController.obtenerTotalServiciosActivos()).doesNotThrowAnyException();
    }

    @Test
    void un_administrador_consulta_servicios_y_pagos() {
        AuraUserHolder.set(new UsuarioSesion(1, "Admin", 1));
        when(servicios.obtenerPorId(1)).thenReturn(Optional.empty());

        assertThatCode(() -> {
            servicioController.obtenerTodos();
            servicioController.obtenerPorId(1);
            servicioController.obtenerPorPuntoSuministroId(1);
            pagosController.obtenerPagosPorServicio(1);
            pagosController.obtenerCuotasPorServicio(1);
        }).doesNotThrowAnyException();
    }

    @Test
    void un_administrador_pide_los_planes_de_varios_servicios_de_una_vez() {
        AuraUserHolder.set(new UsuarioSesion(1, "Admin", 1));
        when(pagos.obtenerPagosPorServicios(List.of(1, 2))).thenReturn(List.of());

        assertThatCode(() -> pagosController.obtenerPagosPorServicios(List.of(1, 2))).doesNotThrowAnyException();
        assertThatThrownBy(() -> pagosController.obtenerPagosPorServicios(java.util.Collections.nCopies(501, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
