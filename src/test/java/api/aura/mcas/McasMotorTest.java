package api.aura.mcas;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasValorRango;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.service.mcas.engine.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class McasMotorTest {

    @Mock McasValorRangoRepository rangoRepo;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setUp() { MockitoAnnotations.openMocks(this); }

    // ---- FEE_PERCENTAGE ----

    @Test void iris_185475kwh_fee7_44pct_571_26() throws Exception {
        var s = new EstrategiaFeePorentaje(mapper);
        var r = McasReglaComision.builder().id(1).metodoCalculo("FEE_PERCENTAGE")
                .calculoJson("{\"porcentaje_aplicacion_fee\":0.44,\"fee_minimo_eur_mwh\":5.0}").build();
        var ctx = ctx(185475, 7, null);
        var res = s.calcular(ctx, r, rangoRepo);
        BigDecimal total = res.stream().map(ResultadoCalculo.LineaResultado::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(total).isEqualByComparingTo("571.26"); // 185.475×7×0.44
    }

    @Test void unielectrica_185475kwh_fee7_55pct_714_08() throws Exception {
        var s = new EstrategiaFeePorentaje(mapper);
        var r = McasReglaComision.builder().id(2).calculoJson("{\"porcentaje_aplicacion_fee\":0.55}").build();
        var res = s.calcular(ctx(185475, 7, null), r, rangoRepo);
        BigDecimal total = res.get(0).getImporte().setScale(2, RoundingMode.HALF_UP);
        assertThat(total).isEqualByComparingTo("714.08"); // 185.475×7×0.55
    }

    @Test void visalia_185475kwh_fee8_52pct_771_58() throws Exception {
        var s = new EstrategiaFeePorentaje(mapper);
        var r = McasReglaComision.builder().id(3).calculoJson("{\"porcentaje_aplicacion_fee\":0.52,\"fee_minimo_eur_mwh\":5.0}").build();
        BigDecimal total = s.calcular(ctx(185475, 8, null), r, rangoRepo).get(0).getImporte().setScale(2, RoundingMode.HALF_UP);
        assertThat(total).isEqualByComparingTo("771.58"); // 185.475×8×0.52
    }

    @Test void total_185475kwh_fee7_45pct_584_25() throws Exception {
        var s = new EstrategiaFeePorentaje(mapper);
        var r = McasReglaComision.builder().id(4).calculoJson("{\"porcentaje_aplicacion_fee\":0.45}").build();
        BigDecimal total = s.calcular(ctx(185475, 7, null), r, rangoRepo).get(0).getImporte().setScale(2, RoundingMode.HALF_UP);
        assertThat(total).isEqualByComparingTo("584.25"); // 185.475×7×0.45
    }

    // v2 §14: FEE por debajo del mínimo → revisión manual (antes ERROR_CALCULO, que parecía un fallo técnico).
    @Test void fee_por_debajo_del_minimo_pide_revision_manual() throws Exception {
        var s = new EstrategiaFeePorentaje(mapper);
        var r = McasReglaComision.builder().id(5).calculoJson("{\"porcentaje_aplicacion_fee\":0.44,\"fee_minimo_eur_mwh\":5.0}").build();
        ContextoCalculo ctx = ContextoCalculo.builder().consumoAnualMwh(new BigDecimal("100")).feeEurMwh(new BigDecimal("4.5")).build();
        assertThatThrownBy(() -> s.calcular(ctx, r, rangoRepo)).isInstanceOf(McasRevisionManualException.class)
                .hasMessage("FEE del contrato 4,5 €/MWh por debajo del mínimo 5 €/MWh de la regla: complete la comisión manualmente");
    }

    // ---- Tarifa de acceso normalizada ----

    @Test void normalizar_tarifa_quita_espacios_guiones_y_puntos_que_no_son_decimales() {
        assertThat(McasTexto.normalizarTarifa("2.0 TD")).isEqualTo("2.0TD");
        assertThat(McasTexto.normalizarTarifa("2.0TD")).isEqualTo("2.0TD");
        assertThat(McasTexto.normalizarTarifa("2,0TD")).isEqualTo("2.0TD");
        assertThat(McasTexto.normalizarTarifa(" 3.0-td ")).isEqualTo("3.0TD");
        assertThat(McasTexto.normalizarTarifa("RL.1")).isEqualTo("RL1");
        assertThat(McasTexto.normalizarTarifa("RL1")).isEqualTo("RL1");
        assertThat(McasTexto.normalizarTarifa(null)).isNull();
        assertThat(McasTexto.mismaTarifa("2.0 TD", "2,0td")).isTrue();
        assertThat(McasTexto.mismaTarifa("2.0TD", "3.0TD")).isFalse();
    }

    @Test void range_lookup_categorico_compara_la_tarifa_normalizada() {
        var s = new EstrategiaRangoLookup();
        var r = McasReglaComision.builder().id(75).build();
        var valor = McasValorRango.builder().idRegla(75).dimension("tarifa_acceso")
                .valorCategoria("3.0TD").valorResultado(new BigDecimal("245.50")).orden(1).build();
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(75)).thenReturn(List.of(valor));

        var resultado = s.calcular(ContextoCalculo.builder().codigoTarifaAcceso("3.0 TD").build(), r, rangoRepo);

        assertThat(resultado.get(0).getImporte()).isEqualByComparingTo("245.50");
    }

    // ---- Fuera de la tabla → revisión manual (v2 §14) ----

    @Test void range_lookup_fuera_de_la_tabla_pide_revision_manual() {
        var s = new EstrategiaRangoLookup();
        var r = McasReglaComision.builder().id(76).codigo("IBERDROLA_FUERA_MAS_20KW").build();
        var hasta200k = McasValorRango.builder().idRegla(76).dimension("consumo_kwh")
                .valorMin(BigDecimal.ZERO).valorMax(new BigDecimal("200000"))
                .minInclusivo(true).maxInclusivo(true).valorResultado(new BigDecimal("500")).orden(1).build();
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(76)).thenReturn(List.of(hasta200k));

        assertThatThrownBy(() -> s.calcular(ContextoCalculo.builder()
                        .consumoAnualKwh(new BigDecimal("250000")).build(), r, rangoRepo))
                .isInstanceOf(McasRevisionManualException.class)
                .hasMessage("Consumo anual 250.000 kWh fuera de la tabla de la regla IBERDROLA_FUERA_MAS_20KW: "
                        + "complete la comisión manualmente");
    }

    @Test void matrix_lookup_sin_celda_pide_revision_manual() {
        var s = new EstrategiaMatrizLookup();
        var r = McasReglaComision.builder().id(77).codigo("ENDESA_PYME_LUZ_3_0TD").build();
        var consumo = McasValorRango.builder().idRegla(77).dimension("consumo_kwh")
                .valorMin(BigDecimal.ZERO).valorMax(new BigDecimal("50000"))
                .minInclusivo(true).maxInclusivo(true).valorResultado(new BigDecimal("243.36")).orden(1).build();
        var potencia = McasValorRango.builder().idRegla(77).dimension("potencia_kw")
                .valorMin(new BigDecimal("15")).valorMax(new BigDecimal("30"))
                .minInclusivo(false).maxInclusivo(true).valorResultado(new BigDecimal("243.36")).orden(1).build();
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(77)).thenReturn(List.of(consumo, potencia));

        assertThatThrownBy(() -> s.calcular(ContextoCalculo.builder()
                        .consumoAnualKwh(new BigDecimal("20000")).potenciaCalculoKw(new BigDecimal("10")).build(), r, rangoRepo))
                .isInstanceOf(McasRevisionManualException.class)
                .hasMessage("Consumo anual 20.000 kWh y potencia 10 kW fuera de la tabla de la regla "
                        + "ENDESA_PYME_LUZ_3_0TD: complete la comisión manualmente");
    }

    @Test void motor_deja_regla_y_conjunto_en_la_traza_de_una_revision_manual() throws Exception {
        McasConjuntoReglasRepository conjuntos = mock(McasConjuntoReglasRepository.class);
        McasReglaComisionRepository reglas = mock(McasReglaComisionRepository.class);
        RegistroEstrategias registro = new RegistroEstrategias(List.of(new EstrategiaFeePorentaje(mapper)));
        MotorMcas motor = new MotorMcas(conjuntos, reglas, rangoRepo, registro, mapper);
        var conjunto = McasConjuntoReglas.builder().id(40).codigo("IRIS-FIJO").version("3").idComercializadora(5)
                .vigenciaDesde(java.time.LocalDate.of(2026, 1, 1)).build();
        var regla = McasReglaComision.builder().id(41).codigo("IRIS_PYME_LUZ").metodoCalculo("FEE_PERCENTAGE")
                .condicionesJson("{\"tarifa_acceso\":\"2.0TD\"}")
                .calculoJson("{\"porcentaje_aplicacion_fee\":0.44,\"fee_minimo_eur_mwh\":5}").build();
        when(conjuntos.findPublicadoFijoPorFecha(eq(5), isNull(), any(), eq("FIJO"))).thenReturn(List.of(conjunto));
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(40)).thenReturn(List.of(regla));

        ResultadoCalculo resultado = motor.evaluar(ContextoCalculo.builder().idContrato(99).idComercializadora(5)
                .tipoPrecio("FIJO").fechaEfectiva(java.time.LocalDate.of(2026, 3, 16))
                .codigoTarifaAcceso("2.0 TD").consumoAnualMwh(new BigDecimal("185.475"))
                .feeEurMwh(new BigDecimal("4")).build());

        assertThat(resultado.getResultado()).isEqualTo(ResultadoCalculo.Resultado.REQUIERE_REVISION_MANUAL);
        assertThat(resultado.getIdConjuntoReglas()).isEqualTo(40);
        assertThat(resultado.getIdRegla()).isEqualTo(41);
        var traza = mapper.readTree(resultado.getTrazaJson());
        assertThat(traza.path("revision_manual").asText()).contains("por debajo del mínimo");
        assertThat(traza.path("regla").asText()).isEqualTo("IRIS_PYME_LUZ");
        assertThat(traza.path("regla_id").asInt()).isEqualTo(41);
        assertThat(traza.path("conjunto").asText()).isEqualTo("IRIS-FIJO");
        assertThat(traza.path("conjunto_version").asText()).isEqualTo("3");
        assertThat(traza.path("tarifa_acceso").asText()).isEqualTo("2.0 TD");
    }

    // ---- RATE_PER_MWH ----

    @Test void naturgy_185475kwh_405_751_17() throws Exception {
        var s = new EstrategiaTasaPorMwh(mapper);
        var r = McasReglaComision.builder().id(6).calculoJson("{\"tasa_eur_mwh\":4.05}").build();
        BigDecimal total = s.calcular(ctx(185475, null, null), r, rangoRepo).get(0).getImporte().setScale(2, RoundingMode.HALF_UP);
        assertThat(total).isEqualByComparingTo("751.17"); // 185.475×4.05
    }

    // ---- RANGE_LOOKUP ----

    @Test void range_lookup_dentro_de_rango() {
        var s = new EstrategiaRangoLookup();
        var r = McasReglaComision.builder().id(7).build();
        var rv = McasValorRango.builder().idRegla(7).dimension("consumo_kwh")
                .valorMin(new BigDecimal("50001")).valorMax(new BigDecimal("100000"))
                .minInclusivo(true).maxInclusivo(true).valorResultado(new BigDecimal("411.84")).orden(1).build();
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(7)).thenReturn(List.of(rv));
        var ctx = ContextoCalculo.builder().consumoAnualKwh(new BigDecimal("58500")).build();
        assertThat(s.calcular(ctx, r, rangoRepo).get(0).getImporte()).isEqualByComparingTo("411.84");
    }

    @Test void range_lookup_rechaza_dos_rangos_coincidentes() {
        var s = new EstrategiaRangoLookup();
        var r = McasReglaComision.builder().id(70).build();
        var a = McasValorRango.builder().idRegla(70).dimension("consumo_kwh")
                .valorMin(new BigDecimal("0")).valorMax(new BigDecimal("100000"))
                .minInclusivo(true).maxInclusivo(true).valorResultado(new BigDecimal("100")).orden(1).build();
        var b = McasValorRango.builder().idRegla(70).dimension("consumo_kwh")
                .valorMin(new BigDecimal("50000")).valorMax(new BigDecimal("150000"))
                .minInclusivo(true).maxInclusivo(true).valorResultado(new BigDecimal("200")).orden(2).build();
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(70)).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> s.calcular(
                ContextoCalculo.builder().consumoAnualKwh(new BigDecimal("75000")).build(), r, rangoRepo))
                .isInstanceOf(McasCalculoException.class)
                .hasMessageContaining("2 rangos");
    }

    @Test void range_lookup_categorico_resuelve_tarifa_sin_codigo_nuevo() {
        var s = new EstrategiaRangoLookup();
        var r = McasReglaComision.builder().id(72).build();
        var valor = McasValorRango.builder().idRegla(72).dimension("tarifa_acceso")
                .valorCategoria("3.0TD").valorResultado(new BigDecimal("245.50")).orden(1).build();
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(72)).thenReturn(List.of(valor));

        var resultado = s.calcular(ContextoCalculo.builder().codigoTarifaAcceso("3.0td").build(), r, rangoRepo);

        assertThat(resultado.get(0).getImporte()).isEqualByComparingTo("245.50");
    }

    @Test void range_lookup_sin_dato_del_contrato_solicita_datos() {
        var s = new EstrategiaRangoLookup();
        var r = McasReglaComision.builder().id(73).build();
        var valor = McasValorRango.builder().idRegla(73).dimension("potencia_kw")
                .valorMin(BigDecimal.ZERO).valorResultado(BigDecimal.TEN).orden(1).build();
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(73)).thenReturn(List.of(valor));

        assertThatThrownBy(() -> s.calcular(ContextoCalculo.builder().build(), r, rangoRepo))
                .isInstanceOf(McasDatosRequeridosException.class)
                .hasMessageContaining("potencia_kw");
    }

    @Test void registro_expone_matrix_lookup_con_el_nombre_requerido_por_cliente() {
        var matriz = new EstrategiaMatrizLookup();
        var registro = new RegistroEstrategias(List.of(matriz));

        assertThat(registro.existe("MULTI_DIMENSION_LOOKUP")).isTrue();
        assertThat(registro.obtener("MULTI_DIMENSION_LOOKUP")).isSameAs(matriz);
    }

    @Test void matrix_lookup_rechaza_dos_celdas_coincidentes() {
        var s = new EstrategiaMatrizLookup();
        var r = McasReglaComision.builder().id(71).build();
        var c1Consumo = McasValorRango.builder().idRegla(71).dimension("consumo_kwh")
                .valorMin(BigDecimal.ZERO).valorMax(new BigDecimal("100000"))
                .minInclusivo(true).maxInclusivo(true).valorResultado(new BigDecimal("100")).orden(1).build();
        var c1Potencia = McasValorRango.builder().idRegla(71).dimension("potencia_kw")
                .valorMin(BigDecimal.ZERO).valorMax(new BigDecimal("50"))
                .minInclusivo(true).maxInclusivo(true).valorResultado(new BigDecimal("100")).orden(1).build();
        var c2Consumo = McasValorRango.builder().idRegla(71).dimension("consumo_kwh")
                .valorMin(new BigDecimal("50000")).valorMax(new BigDecimal("150000"))
                .minInclusivo(true).maxInclusivo(true).valorResultado(new BigDecimal("200")).orden(2).build();
        var c2Potencia = McasValorRango.builder().idRegla(71).dimension("potencia_kw")
                .valorMin(new BigDecimal("20")).valorMax(new BigDecimal("80"))
                .minInclusivo(true).maxInclusivo(true).valorResultado(new BigDecimal("200")).orden(2).build();
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(71))
                .thenReturn(List.of(c1Consumo, c1Potencia, c2Consumo, c2Potencia));

        assertThatThrownBy(() -> s.calcular(ContextoCalculo.builder()
                        .consumoAnualKwh(new BigDecimal("75000"))
                        .potenciaCalculoKw(new BigDecimal("30")).build(), r, rangoRepo))
                .isInstanceOf(McasCalculoException.class)
                .hasMessageContaining("2 celdas");
    }

    @Test void matrix_lookup_sin_una_dimension_del_contrato_solicita_datos() {
        var s = new EstrategiaMatrizLookup();
        var r = McasReglaComision.builder().id(74).build();
        var consumo = McasValorRango.builder().idRegla(74).dimension("consumo_kwh")
                .valorMin(BigDecimal.ZERO).valorResultado(BigDecimal.TEN).orden(1).build();
        var producto = McasValorRango.builder().idRegla(74).dimension("codigo_producto")
                .valorCategoria("PLAN_EMPRESA").valorResultado(BigDecimal.TEN).orden(1).build();
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(74)).thenReturn(List.of(consumo, producto));

        assertThatThrownBy(() -> s.calcular(ContextoCalculo.builder()
                        .consumoAnualKwh(BigDecimal.TEN).build(), r, rangoRepo))
                .isInstanceOf(McasDatosRequeridosException.class)
                .hasMessageContaining("codigo_producto");
    }

    @Test void motor_rechaza_dos_conjuntos_publicados_con_igual_especificidad() {
        McasConjuntoReglasRepository conjuntos = mock(McasConjuntoReglasRepository.class);
        McasReglaComisionRepository reglas = mock(McasReglaComisionRepository.class);
        RegistroEstrategias registro = mock(RegistroEstrategias.class);
        MotorMcas motor = new MotorMcas(conjuntos, reglas, rangoRepo, registro, mapper);
        var a = McasConjuntoReglas.builder().id(1).codigo("A").idComercializadora(5)
                .idGestora(3).vigenciaDesde(java.time.LocalDate.of(2026, 1, 1)).build();
        var b = McasConjuntoReglas.builder().id(2).codigo("B").idComercializadora(5)
                .idGestora(3).vigenciaDesde(java.time.LocalDate.of(2026, 1, 1)).build();
        when(conjuntos.findPublicadoFijoPorFecha(eq(5), eq(3), any(), eq("FIJO")))
                .thenReturn(List.of(a, b));

        ResultadoCalculo resultado = motor.evaluar(ContextoCalculo.builder()
                .idContrato(99).idComercializadora(5).idGestora(3).tipoPrecio("FIJO")
                .fechaEfectiva(java.time.LocalDate.of(2026, 3, 16)).build());

        assertThat(resultado.getResultado()).isEqualTo(ResultadoCalculo.Resultado.ERROR_CALCULO);
        assertThat(resultado.getCausaError()).contains("2 conjuntos");
        verifyNoInteractions(reglas, registro);
    }

    @Test void motor_clasifica_como_requiere_datos_el_fee_ausente() {
        McasConjuntoReglasRepository conjuntos = mock(McasConjuntoReglasRepository.class);
        McasReglaComisionRepository reglas = mock(McasReglaComisionRepository.class);
        RegistroEstrategias registro = new RegistroEstrategias(List.of(new EstrategiaFeePorentaje(mapper)));
        MotorMcas motor = new MotorMcas(conjuntos, reglas, rangoRepo, registro, mapper);
        var conjunto = McasConjuntoReglas.builder().id(3).codigo("FEE").idComercializadora(5)
                .vigenciaDesde(java.time.LocalDate.of(2026, 1, 1)).build();
        var regla = McasReglaComision.builder().id(30).codigo("FEE").metodoCalculo("FEE_PERCENTAGE")
                .condicionesJson("{}").calculoJson("{\"porcentaje_aplicacion_fee\":0.44}").build();
        when(conjuntos.findPublicadoFijoPorFecha(eq(5), isNull(), any(), eq("FIJO"))).thenReturn(List.of(conjunto));
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(3)).thenReturn(List.of(regla));

        ResultadoCalculo resultado = motor.evaluar(ContextoCalculo.builder().idContrato(99)
                .idComercializadora(5).tipoPrecio("FIJO").fechaEfectiva(java.time.LocalDate.of(2026, 3, 16)).build());

        assertThat(resultado.getResultado()).isEqualTo(ResultadoCalculo.Resultado.REQUIERE_DATOS);
        assertThat(resultado.getCausaError()).contains("consumo_anual requerido");
    }

    @Test void motor_clasifica_como_requiere_datos_una_condicion_ausente() {
        McasConjuntoReglasRepository conjuntos = mock(McasConjuntoReglasRepository.class);
        McasReglaComisionRepository reglas = mock(McasReglaComisionRepository.class);
        RegistroEstrategias registro = new RegistroEstrategias(List.of(new EstrategiaImporteFijo(mapper)));
        MotorMcas motor = new MotorMcas(conjuntos, reglas, rangoRepo, registro, mapper);
        var conjunto = McasConjuntoReglas.builder().id(31).codigo("SEGMENTO").idComercializadora(5)
                .vigenciaDesde(java.time.LocalDate.of(2026, 1, 1)).build();
        var regla = McasReglaComision.builder().id(32).codigo("PYME").metodoCalculo("FIXED_AMOUNT")
                .condicionesJson("{\"segmento\":\"PYME\"}").calculoJson("{\"importe\":100}").build();
        when(conjuntos.findPublicadoFijoPorFecha(eq(5), isNull(), any(), eq("FIJO"))).thenReturn(List.of(conjunto));
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(31)).thenReturn(List.of(regla));

        ResultadoCalculo resultado = motor.evaluar(ContextoCalculo.builder().idContrato(99)
                .idComercializadora(5).tipoPrecio("FIJO").fechaEfectiva(java.time.LocalDate.of(2026, 3, 16)).build());

        assertThat(resultado.getResultado()).isEqualTo(ResultadoCalculo.Resultado.REQUIERE_DATOS);
        assertThat(resultado.getCausaError()).contains("segmento");
    }

    // ---- FIXED_AMOUNT ----

    @Test void fixed_amount_411_84() throws Exception {
        var s = new EstrategiaImporteFijo(mapper);
        var r = McasReglaComision.builder().id(8).calculoJson("{\"importe\":411.84}").build();
        assertThat(s.calcular(ctx(0, null, null), r, rangoRepo).get(0).getImporte()).isEqualByComparingTo("411.84");
    }

    @Test void composite_sum_suma_lineas_y_porcentaje_sobre_acumulado() {
        var s = new EstrategiaSumaCompuesta(mapper);
        var r = McasReglaComision.builder().id(81).calculoJson("""
                {"lineas":[
                  {"metodo":"FIXED_AMOUNT","importe":100},
                  {"metodo":"RATE_PER_MWH","tasa_eur_mwh":4},
                  {"metodo":"PERCENTAGE_OF_PREVIOUS","porcentaje":0.10}
                ]}
                """).build();
        var ctx = ContextoCalculo.builder().consumoAnualMwh(new BigDecimal("10")).build();

        var lineas = s.calcular(ctx, r, rangoRepo);

        assertThat(lineas).hasSize(3);
        assertThat(lineas.stream().map(ResultadoCalculo.LineaResultado::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("154");
    }

    // ---- Pago HALF_UP 12 cuotas ----

    @Test void buildPagos_12_suma_exacta() {
        MotorMcas m = new MotorMcas(null, null, null, null, mapper);
        List<BigDecimal> pagos = m.buildPagos(new BigDecimal("771.58"), 12);
        assertThat(pagos).hasSize(12);
        BigDecimal suma = pagos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo("771.58");
    }

    // ---- NOT_COMMISSIONABLE / MANUAL ----

    @Test void not_commissionable_lanza_excepcion() {
        var s = new EstrategiaNoComisionable();
        var r = McasReglaComision.builder().motivoManual("Renovacion B2C no comisiona").build();
        assertThatThrownBy(() -> s.calcular(ctx(0, null, null), r, rangoRepo))
                .isInstanceOf(McasNoComisionableException.class);
    }

    @Test void rango_limite_inclusivo_inferior() {
        var rv = McasValorRango.builder().valorMin(new BigDecimal("100")).minInclusivo(true).maxInclusivo(false).build();
        assertThat(EstrategiaRangoLookup.coincideRango(rv, new BigDecimal("100"))).isTrue();
        assertThat(EstrategiaRangoLookup.coincideRango(rv, new BigDecimal("99.99"))).isFalse();
    }

    // ---- helper ----
    @Test void un_fallo_de_la_bd_sube_tal_cual_y_no_se_guarda_como_error_de_calculo() {
        // La transacción ya queda marcada para deshacerse: disfrazarlo de ERROR_CALCULO solo
        // retrasaba el fallo al confirmar y dejaba una auditoría suelta
        var caida = new org.springframework.dao.DataAccessResourceFailureException("conexión perdida");
        McasConjuntoReglasRepository conjuntos = mock(McasConjuntoReglasRepository.class, invocacion -> { throw caida; });
        var motor = new MotorMcas(conjuntos, mock(McasReglaComisionRepository.class), rangoRepo,
                new RegistroEstrategias(List.of()), mapper);
        assertThatThrownBy(() -> motor.evaluar(ctx(185475, 7, null))).isSameAs(caida);
    }

    private ContextoCalculo ctx(double consumoKwh, Integer feeInt, BigDecimal potencia) {
        BigDecimal kwh = consumoKwh > 0 ? new BigDecimal(String.valueOf(consumoKwh)) : null;
        BigDecimal mwh = kwh != null ? kwh.divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP) : null;
        BigDecimal fee = feeInt != null ? new BigDecimal(String.valueOf(feeInt)) : null;
        return ContextoCalculo.builder().consumoAnualKwh(kwh).consumoAnualMwh(mwh)
                .feeEurMwh(fee).potenciaCalculoKw(potencia).build();
    }
}
