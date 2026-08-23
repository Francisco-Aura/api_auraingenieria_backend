package api.aura.service;

import api.aura.dto.ClienteDTO;
import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.entity.PuntoSuministro;
import api.aura.entity.Servicio;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.ServicioRepository;
import api.aura.repository.UsuarioRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceLegacyBajaTest {

    @Mock ClienteRepository clienteRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock PuntoSuministroRepository puntoSuministroRepository;
    @Mock ServicioRepository servicioRepository;
    @Mock ContratoRepository contratoRepository;

    @InjectMocks ClienteService clienteService;

    @ParameterizedTest
    @ValueSource(strings = {"0001-01-01", "1900-01-01"})
    void changingHistoricalSentinelToRealDateUpdatesActiveChildren(String sentinel) {
        Cliente cliente = Cliente.builder()
                .id(7)
                .fechaBaja(Date.valueOf(sentinel))
                .build();
        ClienteDTO actualizacion = new ClienteDTO();
        Date bajaReal = Date.valueOf("2026-08-11");
        actualizacion.setFechaBaja(bajaReal);
        PuntoSuministro punto = new PuntoSuministro();
        Servicio servicio = new Servicio();
        Contrato contrato = new Contrato();
        when(clienteRepository.findById(7)).thenReturn(Optional.of(cliente));
        when(clienteRepository.save(cliente)).thenReturn(cliente);
        when(puntoSuministroRepository.findActivosPorClienteId(7)).thenReturn(List.of(punto));
        when(servicioRepository.findActivosPorClienteId(7)).thenReturn(List.of(servicio));
        when(contratoRepository.findActivosPorClienteId(7)).thenReturn(List.of(contrato));

        clienteService.actualizarCliente(7, actualizacion);

        assertEquals(bajaReal, punto.getFechaBaja());
        assertEquals(bajaReal, servicio.getFechaBaja());
        assertEquals(bajaReal, contrato.getFechaBaja());
    }
}
