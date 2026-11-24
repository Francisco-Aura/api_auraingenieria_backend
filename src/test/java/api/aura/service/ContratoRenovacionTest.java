package api.aura.service;

import api.aura.entity.Contrato;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.service.mcas.McasProposalService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContratoRenovacionTest {

    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final ContratoService servicio = new ContratoService(contratos, mock(ClienteRepository.class),
            mock(PuntoSuministroRepository.class), mock(ContratoFileStorageService.class),
            mock(McasProposalService.class), mock(ApplicationEventPublisher.class));

    private Contrato renovar(Contrato original) {
        when(contratos.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));
        when(contratos.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));
        servicio.renovarContrato(original.getId(), Contrato.builder().contrato("CT-NUEVO").build());
        return original;
    }

    @Test
    void renovar_un_contrato_activo_lo_marca_como_vencido_hoy() {
        Contrato original = renovar(Contrato.builder().id(7).contrato("CT-1").build());

        assertThat(original.getFechaBaja()).isEqualTo(java.sql.Date.valueOf(LocalDate.now()));
    }

    @Test
    void renovar_un_contrato_que_ya_estaba_de_baja_conserva_su_fecha_de_baja() {
        java.sql.Date bajaAnterior = java.sql.Date.valueOf("2025-03-03");

        Contrato original = renovar(Contrato.builder().id(8).contrato("CT-2").fechaBaja(bajaAnterior).build());

        assertThat(original.getFechaBaja()).isEqualTo(bajaAnterior);
    }

    @Test
    void las_fechas_centinela_cuentan_como_contrato_activo() {
        // 0001-01-01 y 1900-01-01 equivalen a «sin baja» en informes y pantallas
        Contrato original = renovar(Contrato.builder().id(9).contrato("CT-3")
                .fechaBaja(java.sql.Date.valueOf("1900-01-01")).build());

        assertThat(original.getFechaBaja()).isEqualTo(java.sql.Date.valueOf(LocalDate.now()));
    }

    @Test
    void una_renovacion_repetida_devuelve_la_ya_creada_sin_duplicar() {
        api.aura.entity.PuntoSuministro punto = new api.aura.entity.PuntoSuministro();
        punto.setId(40);
        Contrato original = Contrato.builder().id(9).contrato("CT-3").puntoSuministro(punto).build();
        Contrato yaCreada = Contrato.builder().id(10).contrato("CT-NUEVO").puntoSuministro(punto)
                .mcasEsRenovacion(true).fechaInicio(java.sql.Date.valueOf("2026-11-01"))
                .fechaFin(java.sql.Date.valueOf("2027-10-31")).build();
        when(contratos.findByIdForUpdate(9)).thenReturn(Optional.of(original));
        when(contratos.findByPuntoSuministroId(40)).thenReturn(java.util.List.of(yaCreada));

        // Segundo envío con los mismos datos (las fechas llegan del JSON como java.util.Date)
        java.util.Date inicio = java.util.Date.from(LocalDate.parse("2026-11-01").atStartOfDay(java.time.ZoneId.of("Europe/Madrid")).toInstant());
        java.util.Date fin = java.util.Date.from(LocalDate.parse("2027-10-31").atStartOfDay(java.time.ZoneId.of("Europe/Madrid")).toInstant());
        Contrato resultado = servicio.renovarContrato(9, Contrato.builder().contrato("CT-NUEVO")
                .fechaInicio(inicio).fechaFin(fin).build());

        assertThat(resultado).isSameAs(yaCreada);
        org.mockito.Mockito.verify(contratos, org.mockito.Mockito.never()).save(any(Contrato.class));
    }

    @Test
    void si_la_renovacion_ya_creada_se_dio_de_baja_se_crea_otra() {
        api.aura.entity.PuntoSuministro punto = new api.aura.entity.PuntoSuministro();
        punto.setId(42);
        Contrato original = Contrato.builder().id(13).contrato("CT-5").puntoSuministro(punto).build();
        Contrato deBaja = Contrato.builder().id(14).contrato("CT-NUEVO").puntoSuministro(punto)
                .mcasEsRenovacion(true).fechaInicio(java.sql.Date.valueOf("2026-11-01"))
                .fechaFin(java.sql.Date.valueOf("2027-10-31"))
                .fechaBaja(java.sql.Date.valueOf("2026-11-15")).build();
        when(contratos.findByIdForUpdate(13)).thenReturn(Optional.of(original));
        when(contratos.findByPuntoSuministroId(42)).thenReturn(java.util.List.of(deBaja));
        when(contratos.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));

        Contrato resultado = servicio.renovarContrato(13, Contrato.builder().contrato("CT-NUEVO")
                .fechaInicio(java.sql.Date.valueOf("2026-11-01"))
                .fechaFin(java.sql.Date.valueOf("2027-10-31")).build());

        assertThat(resultado).isNotSameAs(deBaja);
        org.mockito.Mockito.verify(contratos, org.mockito.Mockito.atLeastOnce()).save(any(Contrato.class));
    }

    @Test
    void con_otras_fechas_si_es_una_renovacion_nueva() {
        api.aura.entity.PuntoSuministro punto = new api.aura.entity.PuntoSuministro();
        punto.setId(41);
        Contrato original = Contrato.builder().id(11).contrato("CT-4").puntoSuministro(punto).build();
        Contrato anterior = Contrato.builder().id(12).contrato("CT-NUEVO").puntoSuministro(punto)
                .mcasEsRenovacion(true).fechaInicio(java.sql.Date.valueOf("2025-11-01")).build();
        when(contratos.findByIdForUpdate(11)).thenReturn(Optional.of(original));
        when(contratos.findByPuntoSuministroId(41)).thenReturn(java.util.List.of(anterior));
        when(contratos.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));

        Contrato resultado = servicio.renovarContrato(11, Contrato.builder().contrato("CT-NUEVO")
                .fechaInicio(java.sql.Date.valueOf("2026-11-01")).build());

        assertThat(resultado).isNotSameAs(anterior);
        org.mockito.Mockito.verify(contratos, org.mockito.Mockito.atLeastOnce()).save(any(Contrato.class));
    }

    @Test
    void renovar_un_contrato_que_ya_venia_de_otra_renovacion_con_su_mismo_numero_y_fechas_crea_el_nuevo() {
        api.aura.entity.PuntoSuministro punto = new api.aura.entity.PuntoSuministro();
        punto.setId(42);
        java.sql.Date inicio = java.sql.Date.valueOf("2026-11-01");
        java.sql.Date fin = java.sql.Date.valueOf("2027-10-31");
        // El original es a su vez una renovación y el formulario trae su número y sus fechas
        Contrato original = Contrato.builder().id(20).contrato("CT-5").puntoSuministro(punto)
                .mcasEsRenovacion(true).fechaInicio(inicio).fechaFin(fin).build();
        when(contratos.findByIdForUpdate(20)).thenReturn(Optional.of(original));
        when(contratos.findByPuntoSuministroId(42)).thenReturn(java.util.List.of(original));
        when(contratos.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));

        Contrato resultado = servicio.renovarContrato(20, Contrato.builder().contrato("CT-5")
                .puntoSuministro(punto).fechaInicio(inicio).fechaFin(fin).build());

        assertThat(resultado).isNotSameAs(original);
        assertThat(original.getFechaBaja()).as("el original queda vencido").isNotNull();
    }

    @Test
    void la_renovacion_no_hereda_la_baja_del_contrato_original() {
        when(contratos.findByIdForUpdate(30)).thenReturn(Optional.of(Contrato.builder().id(30).contrato("CT-6")
                .fechaBaja(java.sql.Date.valueOf("2026-09-15")).build()));
        when(contratos.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));

        Contrato nuevo = servicio.renovarContrato(30, Contrato.builder().contrato("CT-6B")
                .fechaBaja(java.sql.Date.valueOf("2026-09-15")).build());

        assertThat(nuevo.getFechaBaja()).isNull();
    }
}
