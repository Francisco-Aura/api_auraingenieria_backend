package api.aura.service;

import api.aura.entity.Servicio;
import api.aura.entity.ServiciosPagos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicioConPagosServiceTest {

    @Mock
    private ServicioService servicioService;

    @Mock
    private ServiciosPagosService serviciosPagosService;

    @InjectMocks
    private ServicioConPagosService servicioConPagosService;

    @Test
    void guardaServicioYPagosEnOrdenYVinculaElServicioPersistido() {
        Servicio solicitado = new Servicio();
        Servicio guardado = new Servicio();
        guardado.setId(42);
        ServiciosPagos pagos = new ServiciosPagos();
        when(servicioService.guardarServicio(solicitado)).thenReturn(guardado);

        Servicio resultado = servicioConPagosService.guardar(solicitado, pagos);

        assertSame(guardado, resultado);
        assertSame(guardado, pagos.getServicio());
        var orden = inOrder(servicioService, serviciosPagosService);
        orden.verify(servicioService).guardarServicio(solicitado);
        orden.verify(serviciosPagosService).guardar(pagos);
    }

    @Test
    void noCreaPagosCuandoElComandoNoLosIncluye() {
        Servicio solicitado = new Servicio();
        Servicio guardado = new Servicio();
        when(servicioService.guardarServicio(solicitado)).thenReturn(guardado);

        assertSame(guardado, servicioConPagosService.guardar(solicitado, null));
        verify(serviciosPagosService, never()).guardar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void propagaElErrorDePagosParaQueLaTransaccionPuedaRevertir() {
        Servicio solicitado = new Servicio();
        Servicio guardado = new Servicio();
        guardado.setId(42);
        ServiciosPagos pagos = new ServiciosPagos();
        RuntimeException fallo = new RuntimeException("fallo de pagos");
        when(servicioService.guardarServicio(solicitado)).thenReturn(guardado);
        when(serviciosPagosService.guardar(pagos)).thenThrow(fallo);

        RuntimeException resultado = assertThrows(
                RuntimeException.class,
                () -> servicioConPagosService.guardar(solicitado, pagos));

        assertSame(fallo, resultado);
        verify(servicioService).guardarServicio(solicitado);
    }
}
