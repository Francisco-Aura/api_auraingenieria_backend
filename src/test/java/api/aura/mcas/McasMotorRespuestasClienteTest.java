package api.aura.mcas;

import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.service.mcas.engine.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Motor MCAS con las respuestas del cliente del 05/11/2026 (dudas 8, 19 y 22). */
class McasMotorRespuestasClienteTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final McasConjuntoReglasRepository conjuntos = mock(McasConjuntoReglasRepository.class);
    private final McasReglaComisionRepository reglas = mock(McasReglaComisionRepository.class);
    private final McasValorRangoRepository rangos = mock(McasValorRangoRepository.class);
    private final RegistroEstrategias registro = new RegistroEstrategias(List.of(
            new EstrategiaFeePorentaje(mapper), new EstrategiaImporteFijo(mapper), new EstrategiaMatrizLookup(),
            new EstrategiaRangoLookup(), new EstrategiaTasaPorMwh(mapper), new EstrategiaSumaCompuesta(mapper),
            new EstrategiaManual(), new EstrategiaNoComisionable()));
    private final MotorMcas motor = new MotorMcas(conjuntos, reglas, rangos, registro, mapper);

    // ------------------------------------------------------------------ duda 19

    @Test
    void una_tacita_al_80_por_ciento_cobra_el_80_de_lo_calculado_y_lo_ensena_en_el_desglose() {
        McasReglaComision rock = regla(1, "ROCK_TACITA", "FEE_PERCENTAGE", "{\"tipo_renovacion\":\"TACITA\"}",
                "{\"porcentaje_aplicacion_fee\":0.40}");
        rock.setPorcentajeResultado(new BigDecimal("80"));
        conjuntoConReglas(rock);

        ResultadoCalculo r = motor.evaluar(ctx().tipoRenovacion("TACITA")
                .consumoAnualMwh(new BigDecimal("100")).feeEurMwh(new BigDecimal("12")).build());

        // 100 MWh × 12 €/MWh × 40 % = 480 €; al 80 %, 384 €
        assertThat(r.getResultado()).isEqualTo(ResultadoCalculo.Resultado.CALCULADO);
        assertThat(r.getImporteTotal()).isEqualByComparingTo("384.00");
        assertThat(r.getLineas()).extracting(ResultadoCalculo.LineaResultado::getMetodo)
                .containsExactly("FEE_PERCENTAGE", "PORCENTAJE_RESULTADO");
        assertThat(r.getLineas().get(1).getImporte()).isEqualByComparingTo("-96");
        assertThat(r.getLineas().get(1).getDescripcion()).isEqualTo("Se cobra el 80 % de lo calculado");
        assertThat(r.getTrazaJson()).contains("\"porcentaje_resultado\":\"80\"");
    }

    @Test
    void sin_porcentaje_o_al_100_no_hay_linea_de_ajuste() {
        McasReglaComision fija = regla(1, "FIJA", "FIXED_AMOUNT", null, "{\"importe\":120}");
        fija.setPorcentajeResultado(new BigDecimal("100"));
        conjuntoConReglas(fija);

        ResultadoCalculo r = motor.evaluar(ctx().build());

        assertThat(r.getImporteTotal()).isEqualByComparingTo("120.00");
        assertThat(r.getLineas()).hasSize(1);
    }

    // ------------------------------------------------------------------ duda 8 y 13

    @Test
    void los_pagos_de_la_regla_ganan_a_los_de_la_comercializadora_y_si_no_hay_ninguno_uno() {
        McasReglaComision conPagos = regla(1, "TRAMO_ALTO", "FIXED_AMOUNT", null, "{\"importe\":300}");
        conPagos.setPoliticaPagoJson("{\"numero_pagos_sugerido\":3}");
        conjuntoConReglas(conPagos);
        assertThat(motor.evaluar(ctx().numeroPagos(2).build()).getNumeroPagos()).isEqualTo(3);

        conjuntoConReglas(regla(1, "GENERAL", "FIXED_AMOUNT", null, "{\"importe\":300}"));
        assertThat(motor.evaluar(ctx().numeroPagos(2).build()).getNumeroPagos()).isEqualTo(2);
        assertThat(motor.evaluar(ctx().build()).getNumeroPagos()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ duda 22

    @Test
    void volver_en_menos_de_12_meses_a_la_misma_comercializadora_no_comisiona_y_gana_a_la_regla_general() {
        conjuntoConReglas(
                regla(1, "PYME", "FIXED_AMOUNT", "{\"segmento\":\"PYME\"}", "{\"importe\":120}"),
                regla(2, "VUELVE_PRONTO", "NOT_COMMISSIONABLE",
                        "{\"segmento\":\"PYME\",\"meses_ultima_relacion_max\":12}", null));

        // Ejemplo del cliente: Endesa hasta el 15/05/2025 y vuelve el 13/02/2026 (8 meses)
        ResultadoCalculo vuelve = motor.evaluar(ctx().segmento("PYME").mesesDesdeUltimaRelacion(8).build());
        assertThat(vuelve.getResultado()).isEqualTo(ResultadoCalculo.Resultado.NO_COMISIONABLE);

        ResultadoCalculo pasadoElAnio = motor.evaluar(ctx().segmento("PYME").mesesDesdeUltimaRelacion(12).build());
        assertThat(pasadoElAnio.getImporteTotal()).isEqualByComparingTo("120.00");
    }

    @Test
    void sin_relacion_anterior_la_excepcion_no_se_aplica_ni_pide_datos() {
        conjuntoConReglas(
                regla(1, "PYME", "FIXED_AMOUNT", "{\"segmento\":\"PYME\"}", "{\"importe\":120}"),
                regla(2, "VUELVE_PRONTO", "NOT_COMMISSIONABLE",
                        "{\"segmento\":\"PYME\",\"meses_ultima_relacion_max\":12}", null));

        ResultadoCalculo r = motor.evaluar(ctx().segmento("PYME").build());

        assertThat(r.getResultado()).isEqualTo(ResultadoCalculo.Resultado.CALCULADO);
        assertThat(r.getImporteTotal()).isEqualByComparingTo("120.00");
    }

    // ------------------------------------------------------------------ ayudas

    private ContextoCalculo.ContextoCalculoBuilder ctx() {
        return ContextoCalculo.builder().idContrato(99).idComercializadora(5).tipoSuministro("LUZ")
                .tipoPrecio("FIJO").fechaEfectiva(LocalDate.of(2026, 3, 16));
    }

    private void conjuntoConReglas(McasReglaComision... lista) {
        McasConjuntoReglas c = McasConjuntoReglas.builder().id(1).codigo("CONJ-1").version("1").idComercializadora(5)
                .vigenciaDesde(LocalDate.of(2026, 1, 1)).build();
        when(conjuntos.findPublicadoFijoPorFecha(eq(5), isNull(), any(), eq("FIJO"))).thenReturn(List.of(c));
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(1)).thenReturn(List.of(lista));
    }

    private McasReglaComision regla(int id, String codigo, String metodo, String condiciones, String calculo) {
        return McasReglaComision.builder().id(id).idConjuntoReglas(1).codigo(codigo).metodoCalculo(metodo)
                .condicionesJson(condiciones).calculoJson(calculo).activo(true).build();
    }
}
