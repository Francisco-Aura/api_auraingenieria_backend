package api.aura.mcas;

import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.service.mcas.engine.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Motor MCAS tras la ronda de usabilidad (30/09/2026): las advertencias salen en
 * texto para el usuario, la causa técnica queda en la traza y en causaError, y el
 * resultado dice qué datos faltan. La selección del conjunto se puede usar sola
 * (vista previa).
 */
class McasMotorUsabilidadTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final McasConjuntoReglasRepository conjuntos = mock(McasConjuntoReglasRepository.class);
    private final McasReglaComisionRepository reglas = mock(McasReglaComisionRepository.class);
    private final McasValorRangoRepository rangos = mock(McasValorRangoRepository.class);
    private final RegistroEstrategias registro = new RegistroEstrategias(List.of(
            new EstrategiaFeePorentaje(mapper), new EstrategiaImporteFijo(mapper), new EstrategiaMatrizLookup(),
            new EstrategiaRangoLookup(), new EstrategiaTasaPorMwh(mapper), new EstrategiaSumaCompuesta(mapper),
            new EstrategiaManual(), new EstrategiaNoComisionable()));
    private final MotorMcas motor = new MotorMcas(conjuntos, reglas, rangos, registro, mapper);

    private static final LocalDate FECHA = LocalDate.of(2026, 3, 16);

    // ============================================================
    // Selección del conjunto
    // ============================================================

    @Test
    void seleccionar_conjunto_prefiere_el_de_la_gestora_y_vacio_si_no_hay() {
        McasConjuntoReglas generico = conjunto(1, null);
        McasConjuntoReglas deGestora = conjunto(2, 3);
        when(conjuntos.findPublicadoFijoPorFecha(eq(5), eq(3), any(), eq("FIJO"))).thenReturn(List.of(generico, deGestora));
        when(conjuntos.findPublicadoFijoPorFecha(eq(5), isNull(), any(), eq("FIJO"))).thenReturn(List.of(generico));
        when(conjuntos.findPublicadoFijoPorFecha(eq(6), any(), any(), any())).thenReturn(List.of());

        assertThat(motor.seleccionarConjunto(ctx(5).idGestora(3).build())).contains(deGestora);
        assertThat(motor.seleccionarConjunto(ctx(5).build())).contains(generico);
        assertThat(motor.seleccionarConjunto(ctx(6).build())).isEmpty();
    }

    @Test
    void seleccionar_conjunto_con_dos_de_igual_especificidad_es_error_de_configuracion() {
        when(conjuntos.findPublicadoFijoPorFecha(eq(5), isNull(), any(), eq("FIJO")))
                .thenReturn(List.of(conjunto(1, null), conjunto(2, null)));

        assertThatThrownBy(() -> motor.seleccionarConjunto(ctx(5).build()))
                .isInstanceOf(McasCalculoException.class).hasMessageContaining("2 conjuntos");
    }

    @Test
    void sin_conjunto_la_advertencia_es_humana_y_la_causa_tecnica_queda_en_la_traza() throws Exception {
        when(conjuntos.findPublicadoFijoPorFecha(any(), any(), any(), any())).thenReturn(List.of());

        ResultadoCalculo r = motor.evaluar(ctx(5).build());

        assertThat(r.getResultado()).isEqualTo(ResultadoCalculo.Resultado.REQUIERE_REVISION_MANUAL);
        assertThat(r.getCausaError()).startsWith("Sin conjunto de reglas PUBLICADO para comercializadora=5");
        assertThat(r.getAdvertencias()).containsExactly("Esta comercializadora no tiene una tabla de comisiones "
                + "activa en la fecha del contrato (16/03/2026); la comisión se revisará manualmente.");
        assertThat(mapper.readTree(r.getTrazaJson()).path("revision_manual").asText()).startsWith("Sin conjunto");
        assertThat(r.getCamposFaltantes()).isEmpty();
    }

    // ============================================================
    // Datos que faltan
    // ============================================================

    @Test
    void condiciones_sin_dato_devuelven_los_campos_que_faltan() throws Exception {
        conjuntoConReglas(regla(10, "PYME", "FIXED_AMOUNT",
                "{\"segmento\":\"PYME\",\"consumo_min_kwh\":5000}", "{\"importe\":100}"));

        ResultadoCalculo r = motor.evaluar(ctx(5).build());

        assertThat(r.getResultado()).isEqualTo(ResultadoCalculo.Resultado.REQUIERE_DATOS);
        assertThat(r.getCamposFaltantes()).containsExactly("consumo_kwh", "segmento");
        assertThat(r.getCausaError()).isEqualTo("Faltan datos requeridos por las reglas: consumo_kwh, segmento");
        assertThat(r.getAdvertencias())
                .containsExactly("Faltan datos para calcular la comisión: consumo anual y segmento.");
        JsonNode traza = mapper.readTree(r.getTrazaJson());
        assertThat(traza.path("requiere_datos").asText()).contains("consumo_kwh");
        assertThat(traza.path("campos_faltantes")).extracting(JsonNode::asText).containsExactly("consumo_kwh", "segmento");
    }

    @Test
    void la_estrategia_dice_que_campo_falta() {
        conjuntoConReglas(regla(11, "FEE", "FEE_PERCENTAGE", "{}", "{\"porcentaje_aplicacion_fee\":0.44}"));

        ResultadoCalculo r = motor.evaluar(ctx(5).consumoAnualKwh(new BigDecimal("185475"))
                .consumoAnualMwh(new BigDecimal("185.475")).build());

        assertThat(r.getResultado()).isEqualTo(ResultadoCalculo.Resultado.REQUIERE_DATOS);
        assertThat(r.getCamposFaltantes()).containsExactly("fee");
        assertThat(r.getAdvertencias()).containsExactly("Falta un dato para calcular la comisión: FEE del contrato.");
        assertThat(r.getCausaError()).isEqualTo("FEE_PERCENTAGE: FEE del contrato requerido");
    }

    @Test
    void una_tabla_pide_todas_las_dimensiones_que_faltan() {
        McasReglaComision matriz = regla(12, "MATRIZ", "MATRIX_LOOKUP", "{}", null);
        conjuntoConReglas(matriz);
        when(rangos.findByIdReglaOrderByOrdenAsc(12)).thenReturn(List.of(
                valor(12, "consumo_kwh", null), valor(12, "potencia_kw", null), valor(12, "zona_comision", "ZBR")));

        ResultadoCalculo r = motor.evaluar(ctx(5).consumoAnualKwh(new BigDecimal("1000")).build());

        assertThat(r.getResultado()).isEqualTo(ResultadoCalculo.Resultado.REQUIERE_DATOS);
        assertThat(r.getCamposFaltantes()).containsExactly("potencia_kw", "zona_comision");
        assertThat(r.getAdvertencias())
                .containsExactly("Faltan datos para calcular la comisión: potencia de cálculo y zona de comisión.");
    }

    @Test
    void estrategias_que_piden_consumo_o_fee_indican_el_campo() {
        assertThatThrownBy(() -> new EstrategiaTasaPorMwh(mapper).calcular(ContextoCalculo.builder().build(),
                McasReglaComision.builder().id(1).calculoJson("{\"tasa_eur_mwh\":4}").build(), rangos))
                .isInstanceOfSatisfying(McasDatosRequeridosException.class,
                        e -> assertThat(e.getCampos()).containsExactly("consumo_mwh"));
        assertThatThrownBy(() -> new EstrategiaSumaCompuesta(mapper).calcular(
                ContextoCalculo.builder().consumoAnualMwh(BigDecimal.TEN).build(),
                McasReglaComision.builder().id(2).calculoJson(
                        "{\"lineas\":[{\"metodo\":\"FEE_PERCENTAGE\",\"porcentaje_aplicacion_fee\":0.4}]}").build(), rangos))
                .isInstanceOfSatisfying(McasDatosRequeridosException.class,
                        e -> assertThat(e.getCampos()).containsExactly("FEE"));
        assertThatThrownBy(() -> new EstrategiaRangoLookup().calcular(ContextoCalculo.builder().build(),
                McasReglaComision.builder().id(3).build(), rangosCon(3, valor(3, "potencia_kw", null))))
                .isInstanceOfSatisfying(McasDatosRequeridosException.class,
                        e -> assertThat(e.getCampos()).containsExactly("potencia_kw"));
    }

    // ============================================================
    // Textos para el usuario
    // ============================================================

    @Test
    void ninguna_regla_y_solapamiento_salen_sin_codigos() {
        conjuntoConReglas(regla(20, "GAS", "FIXED_AMOUNT", "{\"tipo_suministro\":\"GAS\"}", "{\"importe\":1}"));
        ResultadoCalculo ninguna = motor.evaluar(ctx(5).tipoSuministro("LUZ").build());
        assertThat(ninguna.getAdvertencias()).containsExactly(
                "Ninguna regla de la tabla de comisiones se aplica a este contrato; la comisión se revisará manualmente.");
        assertThat(ninguna.getCausaError()).contains("CONJ-1");

        conjuntoConReglas(regla(21, "A", "FIXED_AMOUNT", "{}", "{\"importe\":1}"),
                regla(22, "B", "FIXED_AMOUNT", "{}", "{\"importe\":2}"));
        ResultadoCalculo solape = motor.evaluar(ctx(5).build());
        assertThat(solape.getResultado()).isEqualTo(ResultadoCalculo.Resultado.ERROR_CALCULO);
        assertThat(solape.getAdvertencias().get(0)).startsWith("Varias reglas de la tabla de comisiones");
        assertThat(solape.getCausaError()).contains("[A, B]");
    }

    @Test
    void fuera_de_tabla_sin_codigo_de_regla_en_la_advertencia_pero_si_en_la_traza() throws Exception {
        McasReglaComision matriz = regla(30, "ENDESA_30TD", "RANGE_LOOKUP", "{}", null);
        conjuntoConReglas(matriz);
        McasValorRango tramo = valor(30, "consumo_kwh", null);
        tramo.setValorMax(new BigDecimal("5000"));
        when(rangos.findByIdReglaOrderByOrdenAsc(30)).thenReturn(List.of(tramo));

        ResultadoCalculo r = motor.evaluar(ctx(5).consumoAnualKwh(new BigDecimal("9000")).build());

        assertThat(r.getAdvertencias()).containsExactly(
                "Consumo anual 9.000 kWh fuera de la tabla de comisiones: complete la comisión manualmente.");
        assertThat(r.getCausaError()).contains("ENDESA_30TD");
        assertThat(mapper.readTree(r.getTrazaJson()).path("regla").asText()).isEqualTo("ENDESA_30TD");
    }

    @Test
    void la_traza_guarda_el_nombre_legible_de_la_regla() throws Exception {
        McasReglaComision fija = regla(40, "IRIS_RES", "FIXED_AMOUNT", "{}", "{\"importe\":40}");
        fija.setNombre("Iris · residencial · 40 €");
        conjuntoConReglas(fija);

        ResultadoCalculo r = motor.evaluar(ctx(5).build());

        assertThat(r.getResultado()).isEqualTo(ResultadoCalculo.Resultado.CALCULADO);
        JsonNode traza = mapper.readTree(r.getTrazaJson());
        assertThat(traza.path("regla").asText()).isEqualTo("IRIS_RES");
        assertThat(traza.path("regla_nombre").asText()).isEqualTo("Iris · residencial · 40 €");

        conjuntoConReglas(regla(41, "SIN_NOMBRE", "FIXED_AMOUNT", "{}", "{\"importe\":40}"));
        assertThat(mapper.readTree(motor.evaluar(ctx(5).build()).getTrazaJson()).has("regla_nombre")).isFalse();
    }

    @Test
    void una_politica_de_pago_ilegible_avisa_en_texto_humano_y_la_traza_conserva_el_detalle() throws Exception {
        McasReglaComision fija = regla(50, "POLITICA", "FIXED_AMOUNT", "{}", "{\"importe\":40}");
        fija.setPoliticaPagoJson("{roto");
        conjuntoConReglas(fija);

        ResultadoCalculo r = motor.evaluar(ctx(5).build());

        assertThat(r.getAdvertencias()).containsExactly(
                "No se ha podido leer el número de pagos de la regla; la comisión se reparte en un solo pago.");
        assertThat(mapper.readTree(r.getTrazaJson()).path("avisos").get(0).asText()).contains("POLITICA");
    }

    @Test
    void reglas_activas_del_conjunto_para_derivar_los_campos() {
        McasConjuntoReglas c = conjunto(1, null);
        McasReglaComision a = regla(60, "A", "FIXED_AMOUNT", "{}", "{\"importe\":1}");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(1)).thenReturn(List.of(a));

        assertThat(motor.reglasActivas(c)).containsExactly(a);
    }

    // ============================================================
    // Ayudas
    // ============================================================

    private ContextoCalculo.ContextoCalculoBuilder ctx(int idComercializadora) {
        return ContextoCalculo.builder().idContrato(99).idComercializadora(idComercializadora)
                .tipoPrecio("FIJO").fechaEfectiva(FECHA);
    }

    private McasConjuntoReglas conjunto(int id, Integer idGestora) {
        return McasConjuntoReglas.builder().id(id).codigo("CONJ-" + id).version("1").idComercializadora(5)
                .idGestora(idGestora).vigenciaDesde(LocalDate.of(2026, 1, 1)).build();
    }

    private void conjuntoConReglas(McasReglaComision... lista) {
        McasConjuntoReglas c = conjunto(1, null);
        when(conjuntos.findPublicadoFijoPorFecha(eq(5), isNull(), any(), eq("FIJO"))).thenReturn(List.of(c));
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(1)).thenReturn(List.of(lista));
    }

    private McasReglaComision regla(int id, String codigo, String metodo, String condiciones, String calculo) {
        return McasReglaComision.builder().id(id).idConjuntoReglas(1).codigo(codigo).metodoCalculo(metodo)
                .condicionesJson(condiciones).calculoJson(calculo).activo(true).build();
    }

    private McasValorRango valor(int idRegla, String dimension, String categoria) {
        return McasValorRango.builder().idRegla(idRegla).dimension(dimension)
                .valorMin(categoria == null ? BigDecimal.ZERO : null).valorCategoria(categoria)
                .valorResultado(BigDecimal.TEN).orden(1).build();
    }

    private McasValorRangoRepository rangosCon(int idRegla, McasValorRango... valores) {
        McasValorRangoRepository repo = mock(McasValorRangoRepository.class);
        when(repo.findByIdReglaOrderByOrdenAsc(idRegla)).thenReturn(List.of(valores));
        return repo;
    }
}
