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
import api.aura.util.BajaEfectiva;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * «Dado de baja» tiene que significar lo mismo en toda la aplicación: fecha nula,
 * año 1 y año 1900 son fechas históricas heredadas que equivalen a «sin baja».
 * PuntoSuministroService comparaba con {@code != null}, así que un cliente con la
 * fecha 1900 —activo en pantalla y en todas las consultas— impedía reactivar o
 * guardar sus puntos de suministro con el mensaje «el cliente está dado de baja».
 */
@ExtendWith(MockitoExtension.class)
class BajaEfectivaTest {

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
    void la_regla_de_baja_es_una_sola_y_trata_1900_y_el_ano_1_como_sin_baja() {
        assertThat(BajaEfectiva.tieneBaja(null)).isFalse();
        assertThat(BajaEfectiva.tieneBaja(Date.valueOf("1900-01-01"))).isFalse();
        assertThat(BajaEfectiva.tieneBaja(Date.valueOf("0001-01-01"))).isFalse();
        assertThat(BajaEfectiva.tieneBaja(Date.valueOf("2026-09-16"))).isTrue();
    }

    @Test
    void un_cliente_con_la_fecha_historica_1900_no_impide_reactivar_su_punto() {
        Cliente cliente = Cliente.builder().id(4).fechaBaja(Date.valueOf("1900-01-01")).build();
        PuntoSuministro punto = PuntoSuministro.builder().id(12).cliente(cliente)
                .fechaBaja(Date.valueOf("2026-01-10")).build();
        when(puntoSuministroRepository.findById(12)).thenReturn(Optional.of(punto));
        lenient().when(puntoSuministroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> puntoSuministroService.reactivarPuntoSuministro(12)).doesNotThrowAnyException();
        assertThat(punto.getFechaBaja()).isNull();
    }

    @Test
    void un_cliente_realmente_de_baja_sigue_impidiendo_reactivar_su_punto() {
        Cliente cliente = Cliente.builder().id(4).fechaBaja(Date.valueOf("2026-02-01")).build();
        PuntoSuministro punto = PuntoSuministro.builder().id(12).cliente(cliente)
                .fechaBaja(Date.valueOf("2026-02-01")).build();
        when(puntoSuministroRepository.findById(12)).thenReturn(Optional.of(punto));

        assertThatThrownBy(() -> puntoSuministroService.reactivarPuntoSuministro(12))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cliente asociado está dado de baja");
        assertThat(punto.getFechaBaja()).isNotNull();
    }

    @Test
    void guardar_un_punto_reactivado_tampoco_se_bloquea_por_una_fecha_historica() {
        PuntoSuministro guardado = PuntoSuministro.builder().id(12)
                .cliente(Cliente.builder().id(4).build())
                .direccion("CALLE MAYOR, 1").codigoPostal("46001").poblacion("VALENCIA").provincia("VALENCIA")
                .CUPSluz("ES0031000022222222AB0F").build();
        PuntoSuministro existente = PuntoSuministro.builder().id(12)
                .cliente(Cliente.builder().id(4).build())
                .fechaBaja(Date.valueOf("2026-01-10")).build();
        when(puntoSuministroRepository.findById(12)).thenReturn(Optional.of(existente));
        when(clienteRepository.findById(4)).thenReturn(Optional.of(
                Cliente.builder().id(4).fechaBaja(Date.valueOf("1900-01-01")).build()));
        when(puntoSuministroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> puntoSuministroService.guardarPuntoSuministro(guardado)).doesNotThrowAnyException();
    }
}
