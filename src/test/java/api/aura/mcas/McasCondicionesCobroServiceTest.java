package api.aura.mcas;

import api.aura.entity.mcas.McasCondicionCobro;
import api.aura.entity.mcas.McasCondicionCobro.DiaCobro;
import api.aura.repository.mcas.McasCondicionCobroRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBGestorasRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.service.mcas.McasCondicionesCobroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Condiciones de cobro de las comisiones (respuestas del cliente a las dudas 8 y 9, 05/11/2026). */
class McasCondicionesCobroServiceTest {

    private static final int IBERDROLA = 5;
    private static final int NEXO = 3;
    private static final LocalDate INICIO = LocalDate.of(2026, 11, 10);

    private final McasCondicionCobroRepository repo = mock(McasCondicionCobroRepository.class);
    private final TBComercializadorasRepository comercializadoras = mock(TBComercializadorasRepository.class);
    private final TBGestorasRepository gestoras = mock(TBGestorasRepository.class);
    private final TBPagoPlazosRepository pagoPlazos = mock(TBPagoPlazosRepository.class);
    private final McasCondicionesCobroService servicio = new McasCondicionesCobroService(repo, comercializadoras, gestoras, pagoPlazos);

    @BeforeEach
    void maestros() {
        when(comercializadoras.existsById(any())).thenReturn(true);
        when(gestoras.existsById(any())).thenReturn(true);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pagoPlazos.findFirstByNumeroCuotas(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(inv -> inv.<Integer>getArgument(0) == 5
                ? Optional.empty() : Optional.of(TBPagoPlazos.builder().id(1).numeroCuotas(inv.getArgument(0)).build()));
    }

    @Test
    void no_se_guardan_pagos_sin_su_modalidad_en_pago_plazos() {
        when(repo.findByIdComercializadora(IBERDROLA)).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.crear(condiciones(null, 5, 0, DiaCobro.MISMO_DIA)))
                .hasMessage("En Administración, Pago Plazos no hay ninguna modalidad de 5 cuotas: créela antes, "
                        + "o las comisiones no se podrán aprobar.");
    }

    @Test
    void sin_condiciones_el_primer_cobro_es_la_fecha_de_inicio_y_uno_cada_mes() {
        when(repo.findByIdComercializadora(IBERDROLA)).thenReturn(List.of());

        assertThat(servicio.fechasCobro(IBERDROLA, null, INICIO, 3)).containsExactly(
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10), LocalDate.of(2027, 1, 10));
        assertThat(servicio.numeroPagos(IBERDROLA, null)).isNull();
    }

    @Test
    void el_ejemplo_del_cliente_tramitado_el_10_se_cobra_a_final_de_mes_o_el_1_del_siguiente() {
        when(repo.findByIdComercializadora(IBERDROLA)).thenReturn(List.of(condiciones(null, 2, 0, DiaCobro.ULTIMO_DIA_MES)));
        assertThat(servicio.fechasCobro(IBERDROLA, null, INICIO, 2))
                .containsExactly(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31));

        when(repo.findByIdComercializadora(IBERDROLA)).thenReturn(List.of(condiciones(null, 2, 1, DiaCobro.DIA_1)));
        assertThat(servicio.fechasCobro(IBERDROLA, null, INICIO, 2))
                .containsExactly(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1));
    }

    @Test
    void el_mismo_dia_se_calcula_desde_el_inicio_y_no_se_queda_en_el_28() {
        when(repo.findByIdComercializadora(IBERDROLA)).thenReturn(List.of(condiciones(null, 3, 0, DiaCobro.MISMO_DIA)));

        assertThat(servicio.fechasCobro(IBERDROLA, null, LocalDate.of(2026, 1, 31), 3)).containsExactly(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
    }

    @Test
    void las_de_la_gestora_ganan_a_las_generales_y_si_no_tiene_valen_las_generales() {
        McasCondicionCobro generales = condiciones(null, 1, 0, DiaCobro.ULTIMO_DIA_MES);
        McasCondicionCobro nexo = condiciones(NEXO, 3, 2, DiaCobro.DIA_1);
        when(repo.findByIdComercializadora(IBERDROLA)).thenReturn(List.of(generales, nexo));

        assertThat(servicio.numeroPagos(IBERDROLA, NEXO)).isEqualTo(3);
        assertThat(servicio.fechasCobro(IBERDROLA, NEXO, INICIO, 1)).containsExactly(LocalDate.of(2027, 1, 1));
        assertThat(servicio.numeroPagos(IBERDROLA, 99)).isEqualTo(1);
        assertThat(servicio.numeroPagos(IBERDROLA, null)).isEqualTo(1);
    }

    @Test
    void sin_fecha_de_inicio_las_cuotas_van_sin_fecha() {
        when(repo.findByIdComercializadora(IBERDROLA)).thenReturn(List.of());

        assertThat(servicio.fechasCobro(IBERDROLA, null, null, 2)).hasSize(2).containsOnlyNulls();
    }

    @Test
    void se_valida_con_las_palabras_del_formulario() {
        when(repo.findByIdComercializadora(IBERDROLA)).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.crear(condiciones(null, 13, 0, DiaCobro.MISMO_DIA, null)))
                .hasMessage("Elija la comercializadora.");
        assertThatThrownBy(() -> servicio.crear(condiciones(null, 13, 0, DiaCobro.MISMO_DIA)))
                .hasMessage("El número de pagos tiene que estar entre 1 y 12.");
        assertThatThrownBy(() -> servicio.crear(condiciones(null, 1, 25, DiaCobro.MISMO_DIA)))
                .hasMessage("Los meses hasta el primer cobro tienen que estar entre 0 y 24.");

        McasCondicionCobro guardada = servicio.crear(condiciones(null, null, null, null));
        assertThat(guardada.getMesesHastaPrimerCobro()).isZero();
        assertThat(guardada.getDiaPrimerCobro()).isEqualTo(DiaCobro.MISMO_DIA);
        assertThat(guardada.getActualizadoEn()).isNotNull();
    }

    @Test
    void no_se_repiten_las_de_una_comercializadora_y_gestora_pero_se_pueden_editar() {
        McasCondicionCobro existente = condiciones(null, 1, 0, DiaCobro.MISMO_DIA);
        existente.setId(7);
        when(repo.findByIdComercializadora(IBERDROLA)).thenReturn(List.of(existente));
        when(repo.findById(7)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> servicio.crear(condiciones(null, 2, 0, DiaCobro.MISMO_DIA)))
                .hasMessageContaining("ya tiene sus condiciones generales");
        assertThat(servicio.crear(condiciones(NEXO, 2, 0, DiaCobro.MISMO_DIA)).getIdGestora()).isEqualTo(NEXO);
        assertThat(servicio.actualizar(7, condiciones(null, 4, 1, DiaCobro.DIA_1)).getNumeroPagos()).isEqualTo(4);
    }

    private static McasCondicionCobro condiciones(Integer gestora, Integer pagos, Integer meses, DiaCobro dia) {
        return condiciones(gestora, pagos, meses, dia, IBERDROLA);
    }

    private static McasCondicionCobro condiciones(Integer gestora, Integer pagos, Integer meses, DiaCobro dia,
                                                  Integer comercializadora) {
        McasCondicionCobro c = McasCondicionCobro.builder().idComercializadora(comercializadora).idGestora(gestora)
                .numeroPagos(pagos).build();
        c.setMesesHastaPrimerCobro(meses);
        c.setDiaPrimerCobro(dia);
        return c;
    }
}
