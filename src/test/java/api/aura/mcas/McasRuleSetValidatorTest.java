package api.aura.mcas;

import api.aura.dto.mcas.McasIncidenciaDTO;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.service.mcas.McasRuleSetValidator;
import api.aura.service.mcas.engine.RegistroEstrategias;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Validación de una tabla de comisiones antes de activarla. Los mensajes van en
 * español con tildes y en usted, y citan la regla por su nombre (o por su código si
 * aún no tiene nombre); cada problema sale también como incidencia con la regla y,
 * en las tablas por tramos, el orden de la celda.
 */
class McasRuleSetValidatorTest {

    private McasReglaComisionRepository reglas;
    private McasConjuntoReglasRepository conjuntos;
    private McasValorRangoRepository rangos;
    private RegistroEstrategias estrategias;
    private McasRuleSetValidator validator;

    @BeforeEach
    void setUp() {
        reglas = mock(McasReglaComisionRepository.class);
        conjuntos = mock(McasConjuntoReglasRepository.class);
        rangos = mock(McasValorRangoRepository.class);
        estrategias = mock(RegistroEstrategias.class);
        validator = new McasRuleSetValidator(reglas, rangos, conjuntos, estrategias, new ObjectMapper());
    }

    @Test
    void rechaza_reglas_con_condiciones_identicas_que_podrian_coincidir_a_la_vez() {
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision a = regla(1, "A", "{\"segmento\":\"PYME\"}", "FIXED_AMOUNT", "{\"importe\":10}");
        McasReglaComision b = regla(2, "B", "{\"segmento\":\"PYME\"}", "FIXED_AMOUNT", "{\"importe\":20}");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(a, b));
        when(estrategias.existe("FIXED_AMOUNT")).thenReturn(true);

        var resultado = validator.validar(conjunto);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.errores()).anyMatch(e -> e.contains("mismas condiciones") && e.contains("«A»"));
    }

    @Test
    void rechaza_rangos_solapados_antes_de_publicar() {
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision regla = regla(3, "RANGOS", "{}", "RANGE_LOOKUP", "{}");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(regla));
        when(estrategias.existe("RANGE_LOOKUP")).thenReturn(true);
        when(rangos.findByIdReglaOrderByOrdenAsc(3)).thenReturn(List.of(
                rango(0, 100, true, true, 1),
                rango(100, 200, true, false, 2)));

        var resultado = validator.validar(conjunto);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.errores()).anyMatch(e -> e.contains("tramos 1 y 2 se solapan"));
        assertThat(resultado.incidencias()).anyMatch(i -> i.getOrden() != null && i.getOrden() == 2);
    }

    @Test
    void acepta_regla_confirmada_con_json_y_parametros_validos() {
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision regla = regla(4, "IRIS_PLAN", "{\"codigo_producto\":\"PLAN_PERSONALIZADO\"}",
                "FEE_PERCENTAGE", "{\"porcentaje_aplicacion_fee\":0.44,\"fee_minimo_eur_mwh\":5}");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(regla));
        when(estrategias.existe("FEE_PERCENTAGE")).thenReturn(true);

        var resultado = validator.validar(conjunto);

        assertThat(resultado.valido()).isTrue();
        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.incidencias()).isEmpty();
    }

    @Test
    void rechaza_condicion_desconocida_para_evitar_que_un_typo_amplie_la_regla() {
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision regla = regla(5, "TYPO",
                "{\"segmentoo\":\"PYME\"}", "FIXED_AMOUNT", "{\"importe\":10}");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(regla));
        when(estrategias.existe("FIXED_AMOUNT")).thenReturn(true);

        var resultado = validator.validar(conjunto);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.errores()).anyMatch(e -> e.contains("«segmentoo» no existe"));
    }

    @Test
    void rechaza_reglas_distintas_cuyas_condiciones_se_solapan() {
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision generica = regla(6, "GENERICO", "{\"tipo_suministro\":\"LUZ\"}",
                "FIXED_AMOUNT", "{\"importe\":10}");
        McasReglaComision pyme = regla(7, "PYME", "{\"tipo_suministro\":\"LUZ\",\"segmento\":\"PYME\"}",
                "FIXED_AMOUNT", "{\"importe\":20}");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(generica, pyme));
        when(estrategias.existe("FIXED_AMOUNT")).thenReturn(true);

        var resultado = validator.validar(conjunto);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.errores()).anyMatch(e -> e.contains("GENERICO") && e.contains("PYME")
                && e.contains("se solapan"));
    }

    @Test
    void la_tarifa_de_acceso_se_compara_igual_que_en_el_motor_al_buscar_solapes() {
        // El motor considera la misma tarifa «2.0 TD» y «2.0TD»: validar tiene que verlo
        // antes de publicar, no dejar que el cálculo acabe en «Solapamiento».
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision conEspacio = regla(8, "CON_ESPACIO", "{\"tarifa_acceso\":\"2.0 TD\"}",
                "FIXED_AMOUNT", "{\"importe\":10}");
        McasReglaComision sinEspacio = regla(9, "SIN_ESPACIO", "{\"tarifa_acceso\":\"2.0TD\"}",
                "FIXED_AMOUNT", "{\"importe\":20}");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(conEspacio, sinEspacio));
        when(estrategias.existe("FIXED_AMOUNT")).thenReturn(true);

        var resultado = validator.validar(conjunto);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.errores()).anyMatch(e -> e.contains("CON_ESPACIO") && e.contains("SIN_ESPACIO"));
    }

    @Test
    void rechaza_numero_de_pagos_sugerido_fuera_del_limite_del_servicio() {
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision regla = regla(8, "PAGOS", "{\"segmento\":\"PYME\"}",
                "FIXED_AMOUNT", "{\"importe\":10}");
        regla.setPoliticaPagoJson("{\"numero_pagos_sugerido\":13}");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(regla));
        when(estrategias.existe("FIXED_AMOUNT")).thenReturn(true);

        var resultado = validator.validar(conjunto);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.errores()).anyMatch(e -> e.contains("número de pagos")
                && e.contains("entre 1 y 12"));
    }

    @Test
    void rechaza_publicacion_con_otro_conjunto_publicado_de_igual_ambito_y_vigencia() {
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision regla = regla(9, "VALIDA", "{\"segmento\":\"PYME\"}",
                "FIXED_AMOUNT", "{\"importe\":10}");
        McasConjuntoReglas existente = McasConjuntoReglas.builder().id(10).codigo("OTRO")
                .idComercializadora(7).fasePrecio("FIJO")
                .vigenciaDesde(LocalDate.of(2026, 6, 1)).estado("PUBLICADO").build();
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(regla));
        when(estrategias.existe("FIXED_AMOUNT")).thenReturn(true);
        when(conjuntos.findByIdComercializadoraAndEstado(7, "PUBLICADO")).thenReturn(List.of(existente));

        var resultado = validator.validarPublicacion(conjunto);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.errores()).anyMatch(e -> e.contains("«OTRO»") && e.contains("Coincide en fechas"));
    }

    @Test
    void un_cierre_previsto_de_la_tabla_activa_evita_el_solape_al_activar() {
        McasConjuntoReglas conjunto = conjunto();   // desde el 01/01/2026
        McasReglaComision regla = regla(9, "VALIDA", "{\"segmento\":\"PYME\"}",
                "FIXED_AMOUNT", "{\"importe\":10}");
        McasConjuntoReglas anterior = McasConjuntoReglas.builder().id(10).codigo("ANTERIOR")
                .idComercializadora(7).fasePrecio("FIJO")
                .vigenciaDesde(LocalDate.of(2025, 1, 1)).estado("PUBLICADO").build();
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(regla));
        when(estrategias.existe("FIXED_AMOUNT")).thenReturn(true);
        when(conjuntos.findByIdComercializadoraAndEstado(7, "PUBLICADO")).thenReturn(List.of(anterior));

        assertThat(validator.validarPublicacion(conjunto).valido()).isFalse();
        var resultado = validator.validarPublicacion(conjunto, Map.of(10, LocalDate.of(2025, 12, 31)));

        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.valido()).isTrue();
        assertThat(anterior.getVigenciaHasta()).as("solo se simula el cierre").isNull();
    }

    @Test
    void valida_multi_dimension_lookup_como_matriz() {
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision regla = regla(10, "MATRIZ", "{}", "MULTI_DIMENSION_LOOKUP", "{}");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(regla));
        when(estrategias.existe("MULTI_DIMENSION_LOOKUP")).thenReturn(true);
        when(rangos.findByIdReglaOrderByOrdenAsc(10)).thenReturn(List.of(
                McasValorRango.builder().idRegla(10).dimension("consumo_kwh")
                        .valorMin(BigDecimal.ZERO).valorResultado(BigDecimal.TEN).orden(1).build(),
                McasValorRango.builder().idRegla(10).dimension("codigo_producto")
                        .valorCategoria("PLAN_EMPRESA").valorResultado(BigDecimal.TEN).orden(1).build()));

        var resultado = validator.validar(conjunto);

        assertThat(resultado.valido()).isTrue();
    }

    @Test
    void rechaza_rango_categorico_sin_categoria() {
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision regla = regla(11, "CAT", "{}", "RANGE_LOOKUP", "{}");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(regla));
        when(estrategias.existe("RANGE_LOOKUP")).thenReturn(true);
        when(rangos.findByIdReglaOrderByOrdenAsc(11)).thenReturn(List.of(
                McasValorRango.builder().idRegla(11).dimension("tarifa_acceso")
                        .valorResultado(BigDecimal.TEN).orden(1).build()));

        var resultado = validator.validar(conjunto);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.errores()).anyMatch(e -> e.contains("no indica el valor de tarifa de acceso"));
    }

    @Test
    void rechaza_huecos_internos_entre_rangos_numericos() {
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision regla = regla(12, "HUECO", "{}", "RANGE_LOOKUP", "{}");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(regla));
        when(estrategias.existe("RANGE_LOOKUP")).thenReturn(true);
        when(rangos.findByIdReglaOrderByOrdenAsc(12)).thenReturn(List.of(
                McasValorRango.builder().idRegla(12).dimension("consumo_kwh")
                        .valorMin(BigDecimal.ZERO).valorMax(new BigDecimal("100"))
                        .minInclusivo(true).maxInclusivo(true).valorResultado(BigDecimal.TEN).orden(1).build(),
                McasValorRango.builder().idRegla(12).dimension("consumo_kwh")
                        .valorMin(new BigDecimal("101")).valorMax(new BigDecimal("200"))
                        .minInclusivo(true).maxInclusivo(true).valorResultado(BigDecimal.ONE).orden(2).build()));

        var resultado = validator.validar(conjunto);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.errores()).anyMatch(e -> e.contains("hueco entre los tramos 1 y 2"));
    }

    // =====================================================================
    // Incidencias con el nombre de la regla y la celda (usabilidad 30/09/2026)
    // =====================================================================

    @Test
    void cada_problema_es_una_incidencia_con_la_regla_por_su_nombre_y_la_celda() {
        McasConjuntoReglas conjunto = conjunto();
        McasReglaComision regla = regla(13, "ENDESA_PYME_LUZ_3_0TD", "{}", "MATRIX_LOOKUP", "{}");
        regla.setNombre("Endesa · pyme luz 3.0TD");
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of(regla));
        when(estrategias.existe("MATRIX_LOOKUP")).thenReturn(true);
        when(rangos.findByIdReglaOrderByOrdenAsc(13)).thenReturn(List.of(
                celda(13, 11, "consumo_kwh", "0", "50000", "243.36"),
                celda(13, 11, "potencia_kw", "15", "30", "243.36"),
                celda(13, 12, "consumo_kwh", "50000", "100000", null),
                celda(13, 12, "potencia_kw", "15", "30", null)));

        var resultado = validator.validar(conjunto);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.incidencias()).containsExactly(McasIncidenciaDTO.builder()
                .idRegla(13).regla("Endesa · pyme luz 3.0TD").orden(12).mensaje("La celda 12 no tiene importe.").build());
        assertThat(resultado.errores()).containsExactly("Endesa · pyme luz 3.0TD: la celda 12 no tiene importe.");
        assertThat(resultado.errores()).noneMatch(e -> e.contains("ENDESA_PYME_LUZ_3_0TD"));
    }

    @Test
    void los_problemas_de_la_tabla_entera_son_incidencias_sin_regla_y_con_tildes() {
        McasConjuntoReglas conjunto = McasConjuntoReglas.builder().id(9).codigo("TEST").fasePrecio("OTRA").build();
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(9)).thenReturn(List.of());

        var resultado = validator.validar(conjunto);

        assertThat(resultado.incidencias()).allMatch(i -> i.getIdRegla() == null && i.getRegla() == null);
        assertThat(resultado.errores()).containsExactly(
                "Elija la comercializadora de la tabla.",
                "Indique desde qué fecha se aplica la tabla.",
                "Elija el tipo de precio de la tabla: fijo, indexado o ambos («OTRA» no es válido).",
                "La tabla no tiene ninguna regla activa: añada o active al menos una.");
    }

    @Test
    void la_misma_cantidad_con_distintos_decimales_no_son_importes_distintos_en_una_celda() {
        // El motor compara importes con compareTo (10.0 = 10.00): el validador también.
        McasReglaComision regla = regla(14, "MATRIZ", "{}", "MATRIX_LOOKUP", "{}");

        var incidencias = validator.validarTabla(regla, List.of(
                celda(14, 1, "consumo_kwh", "0", "100", "10.0"),
                celda(14, 1, "potencia_kw", "0", "50", "10.00")));

        assertThat(incidencias).isEmpty();
    }

    @Test
    void validar_una_tabla_en_memoria_no_lee_la_base_de_datos() {
        McasReglaComision regla = regla(15, "RANGOS", "{}", "RANGE_LOOKUP", "{}");
        regla.setNombre("Iberdrola · por consumo");

        var incidencias = validator.validarTabla(regla, List.of(
                rango(0, 100, true, true, 1),
                rango(50, 200, false, true, 2)));

        verifyNoInteractions(rangos);
        assertThat(incidencias).extracting(McasIncidenciaDTO::getRegla).containsOnly("Iberdrola · por consumo");
        assertThat(incidencias).extracting(McasIncidenciaDTO::getOrden).containsExactly(2);
        assertThat(validator.validarTabla(regla, List.of())).extracting(McasIncidenciaDTO::getMensaje)
                .containsExactly("La tabla de la regla no tiene tramos: añada al menos uno.");
    }

    @Test
    void una_regla_sin_nombre_se_cita_por_su_codigo() {
        McasReglaComision regla = regla(16, "SIN_NOMBRE", "{}", "MATRIX_LOOKUP", "{}");

        var incidencias = validator.validarTabla(regla, List.of());

        assertThat(incidencias).extracting(McasIncidenciaDTO::getRegla).containsExactly("SIN_NOMBRE");
        assertThat(McasRuleSetValidator.nombreRegla(McasReglaComision.builder().id(5).nombre("  ").build()))
                .isEqualTo("Regla 5");
    }

    private McasConjuntoReglas conjunto() {
        return McasConjuntoReglas.builder().id(9).codigo("TEST").idComercializadora(7)
                .fasePrecio("FIJO").vigenciaDesde(LocalDate.of(2026, 1, 1)).build();
    }

    private McasReglaComision regla(int id, String codigo, String condiciones, String metodo, String calculo) {
        return McasReglaComision.builder().id(id).idConjuntoReglas(9).codigo(codigo)
                .condicionesJson(condiciones).metodoCalculo(metodo).calculoJson(calculo).activo(true).build();
    }

    private McasValorRango rango(int min, int max, boolean minInc, boolean maxInc, int orden) {
        return McasValorRango.builder().idRegla(3).dimension("consumo_kwh")
                .valorMin(BigDecimal.valueOf(min)).valorMax(BigDecimal.valueOf(max))
                .minInclusivo(minInc).maxInclusivo(maxInc)
                .valorResultado(BigDecimal.TEN).orden(orden).build();
    }

    private McasValorRango celda(int idRegla, int orden, String dimension, String min, String max, String importe) {
        return McasValorRango.builder().idRegla(idRegla).dimension(dimension)
                .valorMin(new BigDecimal(min)).valorMax(new BigDecimal(max)).minInclusivo(false).maxInclusivo(true)
                .valorResultado(importe == null ? null : new BigDecimal(importe)).orden(orden).build();
    }
}
