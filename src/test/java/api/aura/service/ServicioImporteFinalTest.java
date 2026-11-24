package api.aura.service;

import api.aura.entity.Cliente;
import api.aura.entity.Servicio;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ServicioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Importe final de un servicio cuando no llega informado: importe - descuento.
 * Con double, 100,10 - 0,20 daba 99,89999999999999 y ese valor con decimales
 * espurios se guardaba y salía en los informes.
 */
@ExtendWith(MockitoExtension.class)
class ServicioImporteFinalTest {

    @Mock private ServicioRepository servicioRepository;
    @Mock private ClienteRepository clienteRepository;
    @InjectMocks private ServicioService servicioService;

    @BeforeEach
    void guardarDevuelveLoGuardado() {
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Servicio nuevo(Double importe, Double descuento, Double importeFinal) {
        when(clienteRepository.findById(7)).thenReturn(Optional.of(Cliente.builder().id(7).build()));
        return Servicio.builder().cliente(Cliente.builder().id(7).build())
                .importe(importe).descuento(descuento).importeFinal(importeFinal).build();
    }

    @Test
    void laRestaNoArrastraDecimalesDeComaFlotante() {
        assertThat(servicioService.guardarServicio(nuevo(100.10, 0.20, null)).getImporteFinal()).isEqualTo(99.90);
        assertThat(servicioService.guardarServicio(nuevo(0.30, 0.10, null)).getImporteFinal()).isEqualTo(0.20);
        assertThat(servicioService.guardarServicio(nuevo(1234.56, 34.56, null)).getImporteFinal()).isEqualTo(1200.00);
    }

    @Test
    void sinDescuentoElImporteFinalEsElImporte() {
        assertThat(servicioService.guardarServicio(nuevo(150.25, null, null)).getImporteFinal()).isEqualTo(150.25);
    }

    @Test
    void elImporteFinalRecibidoSeRespeta() {
        assertThat(servicioService.guardarServicio(nuevo(100.10, 0.20, 95.0)).getImporteFinal()).isEqualTo(95.0);
    }

    @Test
    void sinImporteNoSeInventaUnImporteFinal() {
        assertThat(servicioService.guardarServicio(nuevo(null, 5.0, null)).getImporteFinal()).isNull();
    }
}
