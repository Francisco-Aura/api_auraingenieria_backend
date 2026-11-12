package api.aura.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toda operación que lee una fila, la modifica y la vuelve a guardar tiene que ir
 * dentro de una transacción. Sin ella la entidad llega «detached»: cada guardado es
 * una sentencia suelta y dos ediciones a la vez se pisan sin que nadie se entere.
 *
 * <p>{@code ContratoService} y {@code PuntoSuministroService} ya lo hacían;
 * {@code ServicioService} y {@code ServiciosPagosService} no tenían ninguna.</p>
 */
class EscriturasTransaccionalesTest {

    private static List<String> sinTransaccion(Class<?> servicio, String... metodos) {
        List<String> faltan = new ArrayList<>();
        for (String nombre : metodos) {
            boolean encontrado = false;
            for (Method m : servicio.getDeclaredMethods()) {
                if (!m.getName().equals(nombre)) continue;
                encontrado = true;
                if (m.getAnnotation(Transactional.class) == null
                        && servicio.getAnnotation(Transactional.class) == null) {
                    faltan.add(servicio.getSimpleName() + "." + nombre);
                }
            }
            assertThat(encontrado).as("no existe %s.%s", servicio.getSimpleName(), nombre).isTrue();
        }
        return faltan;
    }

    @Test
    void guardar_un_servicio_y_sus_pagos_ocurre_dentro_de_una_transaccion() {
        assertThat(sinTransaccion(ServicioService.class, "guardarServicio", "renovarServicio", "eliminarPorId"))
                .isEmpty();
    }

    @Test
    void actualizar_cuotas_ocurre_dentro_de_una_transaccion() {
        assertThat(sinTransaccion(ServiciosPagosService.class,
                "actualizarPagosServicio", "actualizarCuota", "guardar", "generarCuotasSegunModalidad",
                "eliminarPagosPorServicio")).isEmpty();
    }

    @Test
    void los_servicios_que_ya_lo_hacian_siguen_igual() {
        assertThat(sinTransaccion(ContratoService.class, "guardarContrato", "renovarContrato")).isEmpty();
        assertThat(sinTransaccion(PuntoSuministroService.class,
                "guardarPuntoSuministro", "reactivarPuntoSuministro")).isEmpty();
        assertThat(sinTransaccion(ClienteService.class, "actualizarCliente", "eliminarCliente", "crearCliente"))
                .isEmpty();
    }
}
