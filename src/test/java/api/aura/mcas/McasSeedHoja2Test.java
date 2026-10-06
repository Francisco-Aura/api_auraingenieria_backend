package api.aura.mcas;

import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.entity.mcas.McasCalendarioPago;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.entity.tablas_maestras.TBTarifasLuz;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBTarifasLuzRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.McasProposalService;
import api.aura.service.mcas.McasRuleSetValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Precarga de reglas y retrocesiones de la Hoja2 (V20260929_2_Seed_MCAS_Reglas_Hoja2.sql)
 * contra H2 y el motor real: los casos UAT del análisis v2 §17.3 y los de la Hoja2 del cliente.
 *
 * <p>Se ejecutan los scripts de semilla tal cual se lanzan en MariaDB (V20260913_2, V20260929_2 y
 * los nombres legibles de V20260930_2) sobre el esquema que crea Hibernate. Los conjuntos quedan en
 * BORRADOR; solo aquí se publican para poder calcular. V20260929_2 y V20260930_2 se generan con
 * {@code tools/mcas/generar_seed_hoja2.py}: si un caso falla se corrige el generador, no el SQL.</p>
 *
 * <p>No es {@code @Transactional} (mismo patrón que {@link McasFlujoIntegrationTest}): los scripts y
 * los cálculos confirman de verdad. La preparación se hace una sola vez por clase.</p>
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McasSeedHoja2Test {

    private static final String SEED_ANTERIOR = "V20260913_2_Seed_MCAS_Fixed_Confirmed.sql";
    private static final String SEED = "V20260929_2_Seed_MCAS_Reglas_Hoja2.sql";
    private static final String NOMBRES = "V20260930_2_Seed_MCAS_Nombres.sql";

    // Nombres como los de la BBDD real; ENDESA XXI está para comprobar que se excluye.
    private static final String ENDESA = "ENDESA ENERGIA S.A.U.";
    private static final String ENDESA_XXI = "ENDESA XXI";
    private static final String IBERDROLA = "IBERDROLA CLIENTES";
    private static final String IRIS = "IRIS ENERGIA, S.L.";
    private static final String UNIELECTRICA = "Unieléctrica Energía";
    private static final String NATURGY = "NATURGY IBERIA";
    private static final String VISALIA = "VISALIA ENERGIA";
    private static final String TOTAL = "TOTALENERGIES CLIENTES";

    private static final List<String> CONJUNTOS = List.of(
            "ENDESA-FIJO-20260309", "IBERDROLA-FIJO-20260101", "IRIS-FIJO-20260304", "UNIELECTRICA-FIJO-20260101",
            "NATURGY-FIJO-20251003", "VISALIA-FIJO-20250612", "VISALIA-INDEX-20250612", "TOTAL-FIJO-20250520");
    private static final List<String> CONJUNTOS_NUEVOS = List.of(
            "ENDESA-FIJO-20260309", "IBERDROLA-FIJO-20260101", "UNIELECTRICA-FIJO-20260101",
            "NATURGY-FIJO-20251003", "VISALIA-INDEX-20250612", "TOTAL-FIJO-20250520");
    private static final List<String> POLITICAS = List.of(
            "RET-ENDESA-RESIDENCIAL-20260309", "RET-ENDESA-PYME-20260309", "RET-IBERDROLA-2_0TD-20260101",
            "RET-IBERDROLA-3_0TD-20260101", "RET-IBERDROLA-6_1TD-20260101", "RET-IRIS-20260304", "RET-NATURGY-PYME-20251003",
            "RET-NATURGY-RESIDENCIAL-20251003");

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private McasProposalService mcas;
    @Autowired private McasRuleSetValidator validador;
    @Autowired private ModuloConfiguracionService modulos;
    @Autowired private McasConjuntoReglasRepository conjuntos;
    @Autowired private TBComercializadorasRepository comercializadoras;
    @Autowired private TBTarifasLuzRepository tarifasLuz;
    @Autowired private ClienteRepository clientes;
    @Autowired private ContratoRepository contratos;

    private final Map<String, Integer> idComercializadora = new HashMap<>();
    private final Map<String, Integer> idTarifa = new HashMap<>();
    private final AtomicInteger secuencia = new AtomicInteger();
    private Map<String, Long> recuentosPrimera;
    private Map<String, Long> recuentosSegunda;
    private List<String> estadosConjuntosSembrados;
    private List<String> estadosPoliticasSembradas;
    private List<String> creadoPorConjuntosNuevos;
    private List<String> nombresPrimera;
    private List<String> nombresSegunda;

    @BeforeAll
    void sembrar() {
        for (String nombre : List.of(ENDESA, ENDESA_XXI, IBERDROLA, IRIS, UNIELECTRICA, NATURGY, VISALIA, TOTAL))
            idComercializadora.put(nombre, comercializadoras.save(TBComercializadoras.builder().nombre(nombre).build()).getId());
        // «3.0 TD» con espacio: la condición de la regla dice «3.0TD» (tarifa normalizada, T1.1).
        for (String tarifa : List.of("2.0TD", "3.0 TD", "6.1TD"))
            idTarifa.put(tarifa, tarifasLuz.save(TBTarifasLuz.builder().nombre(tarifa).build()).getId());

        // En MariaDB la columna tiene DEFAULT CURRENT_TIMESTAMP (V20260913_1) y V20260913_2 no la
        // rellena; el esquema que crea Hibernate en H2 no lleva ese valor por defecto.
        jdbc.execute("ALTER TABLE mcas_conjuntos_reglas ALTER COLUMN fecha_creacion SET DEFAULT CURRENT_TIMESTAMP");
        ejecutar(SEED_ANTERIOR);
        ejecutar(SEED);
        recuentosPrimera = recuentos();
        ejecutar(SEED);
        recuentosSegunda = recuentos();
        // Nombres legibles (V20260930_2): después de la semilla y de la migración V20260930_1
        // (las columnas ya las crea Hibernate en H2).
        ejecutar(NOMBRES);
        nombresPrimera = nombres();
        ejecutar(NOMBRES);
        nombresSegunda = nombres();

        estadosConjuntosSembrados = jdbc.queryForList(
                "SELECT DISTINCT estado FROM mcas_conjuntos_reglas WHERE codigo IN (" + lista(CONJUNTOS) + ")", String.class);
        estadosPoliticasSembradas = jdbc.queryForList(
                "SELECT DISTINCT estado FROM mcas_politicas_retrocesion WHERE codigo IN (" + lista(POLITICAS) + ")", String.class);
        creadoPorConjuntosNuevos = jdbc.queryForList(
                "SELECT DISTINCT creado_por FROM mcas_conjuntos_reglas WHERE codigo IN (" + lista(CONJUNTOS_NUEVOS) + ")", String.class);

        // Solo dentro de la prueba: el script deja todo en BORRADOR y el motor solo usa lo publicado.
        jdbc.update("UPDATE mcas_conjuntos_reglas SET estado = 'PUBLICADO' WHERE codigo IN (" + lista(CONJUNTOS) + ")");
    }

    @BeforeEach
    void indicadores() {
        modulos.activar(McasProposalService.FLAG_ENABLED);
        modulos.activar(McasProposalService.FLAG_FIJO);
    }

    // =====================================================================
    // Script: recuentos, idempotencia, guardas, solapes
    // =====================================================================

    @Test
    void siembra_lo_previsto_por_comercializadora_y_todo_en_borrador() {
        //                                           productos, conjuntos, activas, inactivas, valores, políticas, tramos
        assertThat(recuento(ENDESA)).isEqualTo(new Recuento(0, 1, 7, 0, 434, 2, 6));
        assertThat(recuento(IBERDROLA)).isEqualTo(new Recuento(4, 1, 5, 0, 270, 3, 15));
        assertThat(recuento(IRIS)).isEqualTo(new Recuento(1, 1, 5, 1, 0, 1, 0));
        assertThat(recuento(UNIELECTRICA)).isEqualTo(new Recuento(0, 1, 5, 0, 0, 0, 0));
        assertThat(recuento(NATURGY)).isEqualTo(new Recuento(6, 1, 5, 0, 0, 2, 6));
        assertThat(recuento(VISALIA)).isEqualTo(new Recuento(5, 2, 2, 4, 0, 0, 0));
        assertThat(recuento(TOTAL)).isEqualTo(new Recuento(0, 1, 2, 0, 0, 0, 0));

        assertThat(estadosConjuntosSembrados).containsExactly("BORRADOR");
        assertThat(estadosPoliticasSembradas).containsExactly("BORRADOR");
        assertThat(creadoPorConjuntosNuevos).containsExactly("seed-hoja2");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcas_tramos_retrocesion t JOIN mcas_politicas_retrocesion p"
                + " ON p.id = t.id_politica WHERE p.codigo LIKE 'RET-IBERDROLA-%' AND t.tipo_valor = 'SIN_CONFIRMAR'",
                Long.class)).isEqualTo(12L);
    }

    @Test
    void anota_el_excel_cuando_manda_la_hoja2_y_desactiva_lo_que_la_hoja2_no_contempla() {
        assertThat(localizador("ENDESA_RESIDENCIAL_MENOS_10KW"))
                .contains("Hoja2 K3").contains("110 €").contains("Excel ENDESA C7:C11").contains("88 €");
        assertThat(localizador("NATURGY_PYME_LUZ_10MWH_O_MAS")).contains("Hoja2 K26").contains("Excel NATURGY E10:J10");
        assertThat(jdbc.queryForObject("SELECT activo FROM mcas_reglas_comision WHERE codigo = 'IRIS_PLAN_PERSONALIZADO_PYME'",
                Boolean.class)).isFalse();
        assertThat(localizador("IRIS_PLAN_PERSONALIZADO_PYME")).startsWith("Sustituida por IRIS_PYME_LUZ");
        assertThat(jdbc.queryForList("SELECT codigo FROM mcas_reglas_comision WHERE activo = FALSE AND codigo LIKE 'VISALIA%'",
                String.class)).containsExactlyInAnyOrder("VISALIA_FIJO_COMERCIO_PYME", "VISALIA_ESMERALDA_PYME",
                "VISALIA_JADE_PYME", "VISALIA_CUARZO_PYME");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcas_reglas_comision WHERE LENGTH(localizador_origen) > 250",
                Long.class)).isZero();
    }

    @Test
    void el_script_es_idempotente_y_no_toca_lo_publicado() {
        assertThat(recuentosSegunda).isEqualTo(recuentosPrimera);

        ejecutar(SEED);   // tercera vez, ya con los conjuntos publicados

        assertThat(recuentos()).isEqualTo(recuentosPrimera);
        assertThat(jdbc.queryForList("SELECT DISTINCT estado FROM mcas_conjuntos_reglas WHERE codigo IN ("
                + lista(CONJUNTOS) + ")", String.class)).containsExactly("PUBLICADO");
    }

    @Test
    void ningun_conjunto_sembrado_tiene_reglas_que_se_solapen() {
        for (String codigo : CONJUNTOS) {
            McasConjuntoReglas conjunto = conjuntos.findByCodigo(codigo).orElseThrow();
            McasRuleSetValidator.Resultado resultado = validador.validarPublicacion(conjunto);
            assertThat(resultado.errores()).as(codigo).isEmpty();
            assertThat(resultado.valido()).as(codigo).isTrue();
        }
    }

    @Test
    void endesa_xxi_queda_fuera() {
        assertThat(recuento(ENDESA_XXI)).isEqualTo(new Recuento(0, 0, 0, 0, 0, 0, 0));
    }

    // =====================================================================
    // Nombres legibles (V20260930_2_Seed_MCAS_Nombres.sql)
    // =====================================================================

    @Test
    void todas_las_tablas_y_reglas_sembradas_tienen_nombre_y_descripcion() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcas_conjuntos_reglas WHERE codigo IN (" + lista(CONJUNTOS)
                + ") AND (nombre IS NULL OR descripcion IS NULL)", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcas_reglas_comision r JOIN mcas_conjuntos_reglas cr"
                + " ON cr.id = r.id_conjunto_reglas WHERE cr.codigo IN (" + lista(CONJUNTOS)
                + ") AND (r.nombre IS NULL OR r.descripcion IS NULL)", Long.class)).isZero();
        // 7 comercializadoras sembradas: 8 tablas y 36 reglas (31 activas y 5 desactivadas)
        assertThat(nombresPrimera).hasSize(8 + 36).noneMatch(n -> n.contains("=-|") || n.endsWith("|-"));
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT nombre) FROM mcas_conjuntos_reglas WHERE codigo IN ("
                + lista(CONJUNTOS) + ")", Long.class)).isEqualTo(8L);

        assertThat(nombreConjunto("ENDESA-FIJO-20260309")).isEqualTo("Endesa 2026-03");
        assertThat(nombreConjunto("VISALIA-INDEX-20250612")).isEqualTo("Visalia indexado 2025-06");
        assertThat(nombreConjunto("UNIELECTRICA-FIJO-20260101")).isEqualTo("Unieléctrica 2026-01");
        assertThat(nombreRegla("ENDESA_PYME_LUZ_3_0TD")).isEqualTo("Endesa · pyme luz 3.0TD");
        assertThat(nombreRegla("IRIS_PYME_LUZ")).isEqualTo("Iris · pyme luz");
        assertThat(nombreRegla("IBERDROLA_FUERA_HASTA_20KW")).isEqualTo("Iberdrola · fuera de ámbito hasta 20 kW");
        // Reglas de V20260913_2 que el generador no crea, también con nombre
        assertThat(nombreRegla("IRIS_PLAN_PERSONALIZADO_PYME")).isEqualTo("Iris · Plan Personalizado");
        assertThat(nombreRegla("VISALIA_BASE_PYME")).isEqualTo("Visalia · pyme Base");
        assertThat(jdbc.queryForObject("SELECT descripcion FROM mcas_reglas_comision WHERE codigo = 'ENDESA_GAS_MANUAL'",
                String.class)).contains("complete usted");
    }

    @Test
    void los_nombres_no_llevan_importes_ni_porcentajes_que_quedarian_falsos_en_una_nueva_version() {
        // «Nueva versión» copia el nombre: si el administrador cambia el importe, un nombre con
        // «110 €» mentiría. Los importes van en la descripción, que dice de dónde salen.
        List<String> nombresReglas = nombresPrimera.stream().filter(n -> n.indexOf('/') >= 0 && n.indexOf('/') < n.indexOf('='))
                .map(n -> n.substring(n.indexOf('=') + 1, n.indexOf('|'))).toList();
        assertThat(nombresReglas).hasSize(36).noneMatch(n -> n.contains("€") || n.contains("%"));
        assertThat(nombreRegla("ENDESA_RESIDENCIAL_MENOS_10KW")).isEqualTo("Endesa · residencial menos de 10 kW");
        assertThat(nombreRegla("NATURGY_PYME_LUZ_10MWH_O_MAS")).isEqualTo("Naturgy · pyme luz 10.000 kWh o más");
        assertThat(descripcionRegla("ENDESA_RESIDENCIAL_MENOS_10KW")).startsWith("Importe fijo de 110 € (Hoja2)");
        assertThat(descripcionRegla("IRIS_PYME_LUZ")).contains("44 %");
        assertThat(descripcionRegla("NATURGY_PYME_LUZ_10MWH_O_MAS")).contains("4,05 €");
    }

    @Test
    void el_script_de_nombres_no_pisa_una_descripcion_puesta_a_mano() {
        jdbc.update("UPDATE mcas_reglas_comision SET nombre = NULL, descripcion = 'Descripción puesta a mano'"
                + " WHERE codigo = 'TOTAL_LUZ'");
        try {
            ejecutar(NOMBRES);

            assertThat(nombreRegla("TOTAL_LUZ")).isEqualTo("TotalEnergies · luz");
            assertThat(descripcionRegla("TOTAL_LUZ")).isEqualTo("Descripción puesta a mano");
        } finally {
            jdbc.update("UPDATE mcas_reglas_comision SET nombre = NULL, descripcion = NULL WHERE codigo = 'TOTAL_LUZ'");
            ejecutar(NOMBRES);
        }
        assertThat(nombres()).isEqualTo(nombresPrimera);
    }

    @Test
    void el_script_de_nombres_es_idempotente_y_no_pisa_lo_que_cambie_el_administrador() {
        assertThat(nombresSegunda).isEqualTo(nombresPrimera);
        jdbc.update("UPDATE mcas_reglas_comision SET nombre = 'Puesto a mano' WHERE codigo = 'TOTAL_RENOVACION_MANUAL'");
        try {
            ejecutar(NOMBRES);   // tercera vez, con los conjuntos ya publicados

            assertThat(nombreRegla("TOTAL_RENOVACION_MANUAL")).isEqualTo("Puesto a mano");
            List<String> ahora = nombres();
            ahora.removeIf(n -> n.contains("Puesto a mano"));
            assertThat(ahora).isEqualTo(nombresPrimera.stream()
                    .filter(n -> !n.startsWith("TOTAL-FIJO-20250520/TOTAL_RENOVACION_MANUAL=")).toList());
        } finally {
            jdbc.update("UPDATE mcas_reglas_comision SET nombre = NULL WHERE codigo = 'TOTAL_RENOVACION_MANUAL'");
            ejecutar(NOMBRES);
        }
        assertThat(nombres()).isEqualTo(nombresPrimera);
    }

    // =====================================================================
    // ENDESA
    // =====================================================================

    @Test
    void endesa_pyme_por_matriz_de_tarifa_potencia_y_consumo() {
        // UAT 1: 3.0TD, 45 kW, 58.500 kWh (la tarifa del contrato se llama «3.0 TD», con espacio)
        calculada(calcular(endesaPyme("3.0 TD", "45", 58_500)), "411.84", "ENDESA_PYME_LUZ_3_0TD");
        // Fronteras: 30 kW y 50.000 kWh cierran su tramo; 31 kW y 50.001 kWh abren el siguiente
        calculada(calcular(endesaPyme("3.0 TD", "30", 50_000)), "243.36", "ENDESA_PYME_LUZ_3_0TD");
        calculada(calcular(endesaPyme("3.0 TD", "31", 50_001)), "411.84", "ENDESA_PYME_LUZ_3_0TD");
        calculada(calcular(endesaPyme("2.0TD", "8", 4_000)), "46.80", "ENDESA_PYME_LUZ_2_0TD");
        calculada(calcular(endesaPyme("6.1TD", "40", 120_000)), "434.00", "ENDESA_PYME_LUZ_6_1TD");
    }

    @Test
    void endesa_3_0td_fuera_de_la_tabla_va_a_revision_manual() {
        McasPropuesta p = calcular(endesaPyme("3.0 TD", "10", 20_000));   // 3.0TD empieza en >15 kW
        assertThat(p.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(p.getImporteCalculado()).isNull();
    }

    @Test
    void endesa_residencial_segun_la_hoja2() {
        calculada(calcular(contrato(ENDESA, "LUZ", "RESIDENCIAL").mcasPotenciaCalculo(new BigDecimal("9.99"))
                .consumoAnual(3_000d)), "110.00", "ENDESA_RESIDENCIAL_MENOS_10KW");
        calculada(calcular(contrato(ENDESA, "LUZ", "RESIDENCIAL").mcasPotenciaCalculo(new BigDecimal("10"))
                .consumoAnual(3_000d)), "120.00", "ENDESA_RESIDENCIAL_10KW_O_MAS");
    }

    @Test
    void una_renovacion_endesa_en_revision_manual_conserva_los_pagos_del_contrato() {
        // «Completar manualmente» precarga el número de pagos de la propuesta: sin él, el
        // servicio se creaba en un solo pago aunque el contrato dijera 12.
        McasPropuesta p = calcular(endesaPyme("3.0 TD", "45", 58_500).mcasEsRenovacion(true)
                .mcasTipoRenovacion("FIRMADA").mcasNumeroPagos(12));

        manual(p, "Endesa: las renovaciones se introducen manualmente (Hoja2)");
        assertThat(p.getNumeroPagos()).isEqualTo(12);
    }

    @Test
    void endesa_renovacion_y_gas_van_a_revision_manual_con_el_motivo_de_la_hoja2() {
        manual(calcular(endesaPyme("3.0 TD", "45", 58_500).mcasEsRenovacion(true).mcasTipoRenovacion("FIRMADA")),
                "Endesa: las renovaciones se introducen manualmente (Hoja2)");
        manual(calcular(contrato(ENDESA, "GAS", "PYME").consumoAnual(80_000d)),
                "Endesa: el gas se introduce manualmente (Hoja2)");
    }

    // =====================================================================
    // IBERDROLA
    // =====================================================================

    @Test
    void iberdrola_fuera_de_ambito_por_potencia_zona_y_oferta() {
        calculada(calcular(iberdrola("4", 3_000, "ZBR", "OFERTA_0")), "74.40", "IBERDROLA_FUERA_HASTA_20KW");
        calculada(calcular(iberdrola("12", 9_000, "ZAR", "OFERTA_2")), "57.60", "IBERDROLA_FUERA_HASTA_20KW");
        calculada(calcular(iberdrola("25", 60_000, "ZBR", "OFERTA_1")), "352.80", "IBERDROLA_FUERA_MAS_20KW");
    }

    @Test
    void iberdrola_n_aplica_consultar_y_dentro_de_ambito_van_a_revision_manual() {
        // «N. Aplica» (Oferta 3 hasta 15 kW) y «>200.000 kWh Consultar»: sin celda en la tabla
        assertThat(calcular(iberdrola("4", 3_000, "ZBR", "OFERTA_3")).getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(calcular(iberdrola("25", 250_000, "ZBR", "OFERTA_0")).getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        manual(calcular(iberdrola("12", 9_000, "ZBR", "OFERTA_0").mcasAmbito("DENTRO")),
                "Iberdrola: dentro de ámbito se introduce manualmente (Hoja2)");
    }

    // =====================================================================
    // IRIS, UNIELÉCTRICA, NATURGY, VISALIA, TOTALENERGIES
    // =====================================================================

    @Test
    void iris_pyme_residencial_y_fee_por_debajo_del_minimo() {
        calculada(calcular(pymeConFee(IRIS, "7")), "571.26", "IRIS_PYME_LUZ");                       // UAT 5
        McasPropuesta feeBajo = calcular(pymeConFee(IRIS, "4"));                                       // UAT 6
        assertThat(feeBajo.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(feeBajo.getAdvertenciasJson()).contains("mínimo");
        calculada(calcular(contrato(IRIS, "LUZ", "RESIDENCIAL").consumoAnual(4_500d)), "40.00", "IRIS_RESIDENCIAL_MENOS_5000");
        calculada(calcular(contrato(IRIS, "LUZ", "RESIDENCIAL").consumoAnual(8_000d)), "64.00", "IRIS_RESIDENCIAL_5000_O_MAS");
    }

    @Test
    void unielectrica_pyme_y_residencial() {
        calculada(calcular(pymeConFee(UNIELECTRICA, "7")), "714.08", "UNIELECTRICA_PYME_LUZ");
        calculada(calcular(contrato(UNIELECTRICA, "LUZ", "RESIDENCIAL").consumoAnual(4_000d)), "50.00",
                "UNIELECTRICA_RESIDENCIAL_MENOS_5000");
        calculada(calcular(contrato(UNIELECTRICA, "LUZ", "RESIDENCIAL").consumoAnual(8_000d)), "80.00",
                "UNIELECTRICA_RESIDENCIAL_5000_O_MAS");
    }

    @Test
    void naturgy_pyme_y_residencial() {
        calculada(calcular(contrato(NATURGY, "LUZ", "PYME").consumoAnual(185_475d)), "751.17", "NATURGY_PYME_LUZ_10MWH_O_MAS");
        calculada(calcular(contrato(NATURGY, "LUZ", "PYME").consumoAnual(8_000d)), "40.50", "NATURGY_PYME_LUZ_MENOS_10MWH");
        calculada(calcular(contrato(NATURGY, "LUZ", "RESIDENCIAL")), "50.63", "NATURGY_RESIDENCIAL");
    }

    @Test
    void visalia_base_en_12_pagos_y_esmeralda_manual() {
        McasPropuesta base = calcular(pymeConFee(VISALIA, "8").mcasNumeroPagos(12)
                .mcasIdProductoComercial(producto(VISALIA, "BASE")));                                 // UAT 9
        calculada(base, "771.58", "VISALIA_BASE_PYME");
        List<BigDecimal> cuotas = mcas.obtenerCalendario(base.getId()).stream()
                .map(McasCalendarioPago::getImporte).collect(Collectors.toList());
        assertThat(cuotas).hasSize(12);
        // Criterio de reparto vigente: céntimos truncados y la última absorbe la diferencia
        cuotas.subList(0, 11).forEach(c -> assertThat(c).isEqualByComparingTo("64.29"));
        assertThat(cuotas.get(11)).isEqualByComparingTo("64.39");

        McasPropuesta esmeralda = calcular(pymeConFee(VISALIA, "8").mcasIdProductoComercial(producto(VISALIA, "ESMERALDA")));
        assertThat(esmeralda.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");   // desactivada por la Hoja2
    }

    @Test
    void totalenergies_luz_y_renovacion_manual() {
        calculada(calcular(pymeConFee(TOTAL, "7")), "584.25", "TOTAL_LUZ");
        manual(calcular(pymeConFee(TOTAL, "7").mcasEsRenovacion(true).mcasTipoRenovacion("TACITA")),
                "TotalEnergies: las renovaciones se introducen manualmente (Hoja2)");
    }

    // =====================================================================
    // Retrocesión: informativa, nunca cambia el importe
    // =====================================================================

    @Test
    void la_retrocesion_publicada_se_asocia_sin_cambiar_el_importe() {
        jdbc.update("UPDATE mcas_politicas_retrocesion SET estado = 'PUBLICADO' WHERE codigo = 'RET-ENDESA-PYME-20260309'");
        Integer politica = jdbc.queryForObject(
                "SELECT id FROM mcas_politicas_retrocesion WHERE codigo = 'RET-ENDESA-PYME-20260309'", Integer.class);

        McasPropuesta p = calcular(endesaPyme("3.0 TD", "45", 58_500));

        assertThat(p.getIdPoliticaRetrocesion()).isEqualTo(politica);
        calculada(p, "411.84", "ENDESA_PYME_LUZ_3_0TD");
        assertThat(p.getImporteDefinitivo()).isEqualByComparingTo("411.84");
    }

    @Test
    void cada_tarifa_iberdrola_asocia_solo_su_politica_de_retrocesion() {
        // Excel IBERDROLA A82:A83: una fila para 2.0TD y otra para «3.0TD y 6.1TD». Una política
        // sin tarifa para la segunda fila cubría también la 2.0TD y cualquier otra tarifa.
        jdbc.update("UPDATE mcas_politicas_retrocesion SET estado = 'PUBLICADO' WHERE codigo LIKE 'RET-IBERDROLA-%'");
        try {
            assertThat(politica(calcular(iberdrola("4", 3_000, "ZBR", "OFERTA_0").idTarifaLuz(idTarifa.get("2.0TD")))))
                    .isEqualTo("RET-IBERDROLA-2_0TD-20260101");
            assertThat(politica(calcular(iberdrola("25", 60_000, "ZBR", "OFERTA_1")))) // tarifa «3.0 TD», con espacio
                    .isEqualTo("RET-IBERDROLA-3_0TD-20260101");
            assertThat(politica(calcular(iberdrola("25", 60_000, "ZBR", "OFERTA_1").idTarifaLuz(idTarifa.get("6.1TD")))))
                    .isEqualTo("RET-IBERDROLA-6_1TD-20260101");
            assertThat(politica(calcular(iberdrola("4", 3_000, "ZBR", "OFERTA_0").idTarifaLuz(0)))).isNull();
        } finally {
            jdbc.update("UPDATE mcas_politicas_retrocesion SET estado = 'BORRADOR' WHERE codigo LIKE 'RET-IBERDROLA-%'");
        }
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    /** Código de la política de retrocesión asociada a la propuesta (o null). */
    private String politica(McasPropuesta p) {
        return p.getIdPoliticaRetrocesion() == null ? null : jdbc.queryForObject(
                "SELECT codigo FROM mcas_politicas_retrocesion WHERE id = ?", String.class, p.getIdPoliticaRetrocesion());
    }

    private record Recuento(long productos, long conjuntos, long reglasActivas, long reglasInactivas,
                            long valoresRango, long politicas, long tramos) {}

    private Recuento recuento(String comercializadora) {
        Integer id = idComercializadora.get(comercializadora);
        return new Recuento(
                contar("SELECT COUNT(*) FROM mcas_productos_comerciales WHERE id_comercializadora = ?", id),
                contar("SELECT COUNT(*) FROM mcas_conjuntos_reglas WHERE id_comercializadora = ?", id),
                contar("SELECT COUNT(*) FROM mcas_reglas_comision r JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas"
                        + " WHERE cr.id_comercializadora = ? AND r.activo = TRUE", id),
                contar("SELECT COUNT(*) FROM mcas_reglas_comision r JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas"
                        + " WHERE cr.id_comercializadora = ? AND r.activo = FALSE", id),
                contar("SELECT COUNT(*) FROM mcas_valores_rango v JOIN mcas_reglas_comision r ON r.id = v.id_regla"
                        + " JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas WHERE cr.id_comercializadora = ?", id),
                contar("SELECT COUNT(*) FROM mcas_politicas_retrocesion WHERE id_comercializadora = ?", id),
                contar("SELECT COUNT(*) FROM mcas_tramos_retrocesion t JOIN mcas_politicas_retrocesion p ON p.id = t.id_politica"
                        + " WHERE p.id_comercializadora = ?", id));
    }

    private Map<String, Long> recuentos() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String tabla : List.of("mcas_productos_comerciales", "mcas_conjuntos_reglas", "mcas_reglas_comision",
                "mcas_valores_rango", "mcas_politicas_retrocesion", "mcas_tramos_retrocesion"))
            m.put(tabla, jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class));
        m.put("reglas_activas", jdbc.queryForObject("SELECT COUNT(*) FROM mcas_reglas_comision WHERE activo = TRUE", Long.class));
        return m;
    }

    private long contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    /** «CONJUNTO=nombre|descripcion» y «CONJUNTO/REGLA=nombre|descripcion» de lo sembrado, ordenados. */
    private List<String> nombres() {
        List<String> filas = new java.util.ArrayList<>(jdbc.queryForList(
                "SELECT CONCAT(codigo, '=', COALESCE(nombre, '-'), '|', COALESCE(descripcion, '-'))"
                        + " FROM mcas_conjuntos_reglas WHERE codigo IN (" + lista(CONJUNTOS) + ")", String.class));
        filas.addAll(jdbc.queryForList(
                "SELECT CONCAT(cr.codigo, '/', r.codigo, '=', COALESCE(r.nombre, '-'), '|', COALESCE(r.descripcion, '-'))"
                        + " FROM mcas_reglas_comision r JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas"
                        + " WHERE cr.codigo IN (" + lista(CONJUNTOS) + ")", String.class));
        java.util.Collections.sort(filas);
        return filas;
    }

    private String nombreConjunto(String codigo) {
        return jdbc.queryForObject("SELECT nombre FROM mcas_conjuntos_reglas WHERE codigo = ?", String.class, codigo);
    }

    private String nombreRegla(String codigo) {
        return jdbc.queryForObject("SELECT nombre FROM mcas_reglas_comision WHERE codigo = ?", String.class, codigo);
    }

    private String descripcionRegla(String codigo) {
        return jdbc.queryForObject("SELECT descripcion FROM mcas_reglas_comision WHERE codigo = ?", String.class, codigo);
    }

    private String localizador(String regla) {
        return jdbc.queryForObject("SELECT localizador_origen FROM mcas_reglas_comision WHERE codigo = ?", String.class, regla);
    }

    private Integer producto(String comercializadora, String codigo) {
        return jdbc.queryForObject("SELECT id FROM mcas_productos_comerciales WHERE id_comercializadora = ? AND codigo = ?",
                Integer.class, idComercializadora.get(comercializadora), codigo);
    }

    private static String lista(List<String> codigos) {
        return codigos.stream().map(c -> "'" + c + "'").collect(Collectors.joining(", "));
    }

    /** Los scripts de BBDD/ no viajan en el classpath (el pom los excluye): se leen del repositorio. */
    private void ejecutar(String script) {
        Path ruta = Path.of("src", "main", "resources", "BBDD", script);
        if (!Files.exists(ruta)) ruta = Path.of("backend").resolve(ruta);
        assertThat(ruta).as("script de semilla").exists();
        try (Connection conexion = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conexion, new EncodedResource(new FileSystemResource(ruta), StandardCharsets.UTF_8));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo ejecutar " + script, e);
        }
    }

    private Contrato.ContratoBuilder contrato(String comercializadora, String suministro, String segmento) {
        String numero = "SEED-HOJA2-" + secuencia.incrementAndGet();
        Cliente cliente = clientes.save(Cliente.builder().nombre("Cliente " + numero).cif(numero).build());
        return Contrato.builder().cliente(cliente).contrato(numero)
                .fechaInicio(Date.valueOf("2026-04-01")).fechaFin(Date.valueOf("2027-03-31"))
                .idComercializadoraActual(idComercializadora.get(comercializadora))
                .mcasTipoPrecio("FIJO").mcasTipoSuministro(suministro).mcasSegmento(segmento)
                .mcasEsRenovacion(false).mcasNumeroPagos(1);
    }

    private Contrato.ContratoBuilder endesaPyme(String tarifa, String potenciaKw, double consumoKwh) {
        return contrato(ENDESA, "LUZ", "PYME").idTarifaLuz(idTarifa.get(tarifa))
                .mcasPotenciaCalculo(new BigDecimal(potenciaKw)).consumoAnual(consumoKwh);
    }

    private Contrato.ContratoBuilder iberdrola(String potenciaKw, double consumoKwh, String zona, String oferta) {
        return contrato(IBERDROLA, "LUZ", "PYME").idTarifaLuz(idTarifa.get("3.0 TD"))
                .mcasAmbito("FUERA").mcasZonaComision(zona).mcasIdProductoComercial(producto(IBERDROLA, oferta))
                .mcasPotenciaCalculo(new BigDecimal(potenciaKw)).consumoAnual(consumoKwh);
    }

    private Contrato.ContratoBuilder pymeConFee(String comercializadora, String fee) {
        return contrato(comercializadora, "LUZ", "PYME").consumoAnual(185_475d)
                .mcasFeeEurMwh(new BigDecimal(fee)).mcasFeeUnidad("EUR_MWH");
    }

    private McasPropuesta calcular(Contrato.ContratoBuilder datos) {
        return mcas.iniciarCalculo(contratos.save(datos.build()).getId());
    }

    private void calculada(McasPropuesta p, String importe, String regla) {
        assertThat(p.getEstado()).as("estado (advertencias: %s)", p.getAdvertenciasJson()).isEqualTo("PENDIENTE_VALIDACION");
        assertThat(p.getImporteCalculado()).isEqualByComparingTo(importe);
        assertThat(p.getTrazaCalculoJson()).contains("\"regla\":\"" + regla + "\"");
    }

    private void manual(McasPropuesta p, String motivo) {
        assertThat(p.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(p.getAdvertenciasJson()).contains(motivo);
    }
}
