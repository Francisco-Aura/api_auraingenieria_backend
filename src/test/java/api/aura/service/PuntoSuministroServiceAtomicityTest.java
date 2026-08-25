package api.aura.service;

import api.aura.entity.Cliente;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.tablas_maestras.TBAppMonitorizacionRepository;
import api.aura.repository.tablas_maestras.TBDistribuidoraRepository;
import api.aura.repository.tablas_maestras.TBIntegracionSistemasFVRepository;
import api.aura.repository.tablas_maestras.TBPropiedadRepository;
import api.aura.repository.tablas_maestras.TBTipoComunicacionRepository;
import api.aura.repository.tablas_maestras.TBTipoContadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PuntoSuministroServiceAtomicityTest {

    @Mock PuntoSuministroRepository puntoSuministroRepository;
    @Mock TBDistribuidoraRepository distribuidoraRepository;
    @Mock TBTipoContadorRepository tipoContadorRepository;
    @Mock TBTipoComunicacionRepository tipoComunicacionRepository;
    @Mock TBPropiedadRepository propiedadRepository;
    @Mock TBAppMonitorizacionRepository appMonitorizacionRepository;
    @Mock TBIntegracionSistemasFVRepository integracionSistemasFVRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ContratoRepository contratoRepository;

    @InjectMocks PuntoSuministroService puntoSuministroService;

    @Test
    void contractSynchronizationFailureEscapesTransactionalSave() {
        PuntoSuministro punto = PuntoSuministro.builder()
                .id(12)
                .cliente(Cliente.builder().id(4).build())
                .direccion("Calle Uno")
                .codigoPostal("28001")
                .poblacion("Madrid")
                .provincia("Madrid")
                .build();
        punto.setCUPSluz("ES1234567890");
        when(puntoSuministroRepository.findById(12)).thenReturn(Optional.of(punto));
        when(puntoSuministroRepository.save(any(PuntoSuministro.class))).thenReturn(punto);
        when(contratoRepository.actualizarExencionYAutoconsumoPorPuntoSuministro(12, 0, 0))
                .thenThrow(new IllegalStateException("fallo de sincronizacion"));

        assertThrows(IllegalStateException.class,
                () -> puntoSuministroService.guardarPuntoSuministro(punto));
    }
}
