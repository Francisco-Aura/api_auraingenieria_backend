package api.aura.mcas;

import api.aura.entity.Contrato;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasLineaPropuesta;
import api.aura.entity.mcas.McasPoliticaRetrocesion;
import api.aura.entity.mcas.McasProductoComercial;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasTramoRetrocesion;
import api.aura.entity.mcas.McasValorRango;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.repository.ContratoRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasLineaPropuestaRepository;
import api.aura.repository.mcas.McasPoliticaRetrocesionRepository;
import api.aura.repository.mcas.McasProductoComercialRepository;
import api.aura.repository.mcas.McasPropuestaRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasTramoRetrocesionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.McasProposalService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Script de limpieza V20261021_1_MCAS_Quitar_Precarga.sql contra una BD con filas
 * como las que dejaron las precargas retiradas (V20260913_2, V20260929_2 y
 * V20260930_2), más lo que una persona creó desde la aplicación y lo que ya tiene
 * propuestas: solo se va lo precargado que nadie usa.
 *
 * <p>BD H2 propia (no la compartida por los demás tests) y esquema de Hibernate.
 * El script es MariaDB; tres sentencias no existen en H2 y el test las adapta al
 * leerlo, sin tocar el script: {@code SET NAMES} se omite (H2 siempre trabaja en
 * Unicode), {@code START TRANSACTION}/{@code COMMIT} pasan a la conexión
 * ({@code setAutoCommit}/{@code commit}) y {@code DROP TEMPORARY TABLE} se lanza
 * como {@code DROP TABLE} (H2 no admite la palabra TEMPORARY al borrar).</p>
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mcas_quitar_precarga;MODE=MySQL;"
        + "NON_KEYWORDS=USER,VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McasQuitarPrecargaTest {

    private static final String SCRIPT = "V20261021_1_MCAS_Quitar_Precarga.sql";
    private static final String EXCEL = "COMISIONES NEXO A 16032026 1.xlsx";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TBComercializadorasRepository comercializadoras;
    @Autowired private McasConjuntoReglasRepository conjuntos;
    @Autowired private McasReglaComisionRepository reglas;
    @Autowired private McasValorRangoRepository rangos;
    @Autowired private McasPoliticaRetrocesionRepository politicas;
    @Autowired private McasTramoRetrocesionRepository tramos;
    @Autowired private McasProductoComercialRepository productos;
    @Autowired private McasPropuestaRepository propuestas;
    @Autowired private McasLineaPropuestaRepository lineas;
    @Autowired private ContratoRepository contratos;
    @Autowired private ModuloConfiguracionService modulos;

    private Integer iris, visalia, iberdrola, naturgy, endesa, otra;
    private Integer reglaEndesa;
    private List<List<Map<String, Object>>> primera;
    private List<List<Map<String, Object>>> segunda;
    private Map<String, Long> recuentoTrasPrimera;

    @BeforeAll
    void precargaComoEnDesarrollo() throws Exception {
        iris = comercializadora("IRIS ENERGIA, S.L.");
        visalia = comercializadora("VISALIA ENERGIA");
        iberdrola = comercializadora("IBERDROLA CLIENTES S.A.U.");
        naturgy = comercializadora("NATURGY IBERIA, S.A.");
        endesa = comercializadora("ENDESA ENERGIA S.A.U.");
        otra = comercializadora("ELÉCTRICA DE PRUEBA");
        modulos.activar(McasProposalService.FLAG_ENABLED);       // alguien lo encendió: el script no lo toca
        modulos.desactivar(McasProposalService.FLAG_FIJO);

        // --- Tablas precargadas (V20260913_2 = seed-controlado, V20260929_2 = seed-hoja2)
        McasConjuntoReglas irisPrecarga = conjunto("IRIS-FIJO-20260304", iris, "seed-controlado", EXCEL, "BORRADOR");
        regla(irisPrecarga, "IRIS_PLAN_PERSONALIZADO_PYME", "FEE_PERCENTAGE",
                "{\"codigo_producto\":\"PLAN_PERSONALIZADO\"}");
        McasConjuntoReglas endesaPrecarga = conjunto("ENDESA-FIJO-20260309", endesa, "seed-hoja2", EXCEL, "BORRADOR");
        reglaEndesa = regla(endesaPrecarga, "ENDESA_PYME_LUZ_3_0TD", "MATRIX_LOOKUP", "{\"segmento\":\"PYME\"}").getId();
        rangos.save(McasValorRango.builder().idRegla(reglaEndesa).dimension("consumo_kwh")
                .valorMin(BigDecimal.ZERO).valorMax(new BigDecimal("50000")).valorResultado(new BigDecimal("243.36")).orden(1).build());
        rangos.save(McasValorRango.builder().idRegla(reglaEndesa).dimension("potencia_kw")
                .valorMin(BigDecimal.ZERO).valorMax(new BigDecimal("30")).valorResultado(new BigDecimal("243.36")).orden(1).build());
        McasConjuntoReglas visaliaPrecarga = conjunto("VISALIA-FIJO-20250612", visalia, "seed-controlado", EXCEL, "PUBLICADO");
        regla(visaliaPrecarga, "VISALIA_BASE_PYME", "FEE_PERCENTAGE", "{\"codigo_producto\":\"BASE\"}");

        // Precargada y con una propuesta: se queda
        McasConjuntoReglas iberdrolaConPropuesta = conjunto("IBERDROLA-FIJO-20260101", iberdrola, "seed-hoja2", EXCEL,
                "PUBLICADO");
        regla(iberdrolaConPropuesta, "IBERDROLA_FUERA_HASTA_20KW", "MULTI_DIMENSION_LOOKUP",
                "{\"ambito\":\"FUERA\"}");
        // Precargada y con una línea de propuesta en una de sus reglas: se queda
        McasConjuntoReglas naturgyConLinea = conjunto("NATURGY-FIJO-20251003", naturgy, "seed-hoja2", EXCEL, "PUBLICADO");
        McasReglaComision reglaNaturgy = regla(naturgyConLinea, "NATURGY_PYME_LUZ_10MWH_O_MAS", "RATE_PER_MWH", null);

        // --- Lo que crea una persona desde la aplicación: se queda
        McasConjuntoReglas mia = conjunto("ELECTRICA-FIJO-20261001", otra, "Admin Demo", null, "BORRADOR");
        McasReglaComision reglaMia = regla(mia, "MI_REGLA", "RANGE_LOOKUP", null);
        rangos.save(McasValorRango.builder().idRegla(reglaMia.getId()).dimension("consumo_kwh")
                .valorMin(BigDecimal.ZERO).valorResultado(new BigDecimal("40")).orden(1).build());
        // Copia de la tabla de Iris con «Nueva versión» (hereda archivo_origen): se queda y se avisa
        McasConjuntoReglas copia = conjunto("IRIS-FIJO-20261001", iris, "Admin Demo", EXCEL, "BORRADOR");
        copia.setIdConjuntoOrigen(irisPrecarga.getId());
        conjuntos.save(copia);
        regla(copia, "IRIS_PLAN_PERSONALIZADO_PYME", "FEE_PERCENTAGE", "{\"codigo_producto\":\"PLAN_PERSONALIZADO\"}");
        // Naturgy de una persona que usa un producto precargado en sus condiciones
        McasConjuntoReglas naturgyMia = conjunto("NATURGY-FIJO-20261001", naturgy, "Admin Demo", null, "BORRADOR");
        regla(naturgyMia, "NATURGY_PF_LUZ", "FIXED_AMOUNT", "{\"codigo_producto\":\"PF_LUZ\"}");

        // --- Políticas de retrocesión
        Integer retEndesa = politica("RET-ENDESA-PYME-20260309", endesa, EXCEL);
        Integer retIberdrola = politica("RET-IBERDROLA-2_0TD-20260101", iberdrola, EXCEL);     // con propuesta
        politica("RET-IRIS-PERSONA", iris, null);                                            // de una persona

        // --- Productos comerciales
        producto(iris, "PLAN_PERSONALIZADO", "2026-03-04");    // precargado; solo lo usa la copia de una persona
        producto(visalia, "BASE", "2025-06-12");                // precargado, sin uso: se va
        producto(visalia, "JADE", "2026-10-01");                // mismo código, otra fecha: de una persona
        Integer oferta0 = producto(iberdrola, "OFERTA_0", "2026-01-01");   // precargado, lo usa un contrato
        producto(iberdrola, "OFERTA_1", "2026-01-01");          // precargado, sin uso: se va
        producto(naturgy, "PF_LUZ", "2025-10-03");              // precargado, lo usa una regla de una persona
        producto(naturgy, "PV_SUPRA", "2025-10-03");            // precargado, sin uso: se va
        producto(iberdrola, "PLAN_ONLINE", null);               // de una persona

        // --- Propuestas ligadas a la precarga
        Integer contrato = contratos.save(Contrato.builder().contrato("LIMPIEZA-1")
                .fechaInicio(Date.valueOf("2026-03-16")).idComercializadoraActual(iberdrola)
                .mcasIdProductoComercial(oferta0).build()).getId();
        propuestas.save(McasPropuesta.builder().idContrato(contrato).tipoEvento("NUEVO_CONTRATO")
                .claveIdempotencia("limpieza-1").estado("FINALIZADO_APROBADO").idServicioCreado(77)
                .idConjuntoReglas(iberdrolaConPropuesta.getId()).idPoliticaRetrocesion(retIberdrola).build());
        McasPropuesta sinTabla = propuestas.save(McasPropuesta.builder().idContrato(contrato)
                .tipoEvento("RENOVACION").claveIdempotencia("limpieza-2").estado("PENDIENTE_VALIDACION").build());
        lineas.save(McasLineaPropuesta.builder().idPropuesta(sinTabla.getId()).tipoLinea("ENERGIA")
                .importeCalculado(BigDecimal.TEN).idRegla(reglaNaturgy.getId()).build());

        assertThat(tramos.findByIdPoliticaOrderByUnidadAscDesdeValorAscPrioridadAsc(retEndesa)).hasSize(2);

        primera = ejecutarScript();
        recuentoTrasPrimera = recuentos();
        segunda = ejecutarScript();
    }

    @Test
    void quita_las_tablas_precargadas_sin_propuestas_con_sus_reglas_y_tramos() {
        assertThat(codigosConjunto()).doesNotContain("IRIS-FIJO-20260304", "ENDESA-FIJO-20260309", "VISALIA-FIJO-20250612");
        assertThat(reglas.findById(reglaEndesa)).isEmpty();
        assertThat(rangos.findByIdReglaOrderByOrdenAsc(reglaEndesa)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcas_reglas_comision r LEFT JOIN mcas_conjuntos_reglas c"
                + " ON c.id = r.id_conjunto_reglas WHERE c.id IS NULL", Long.class)).as("reglas huérfanas").isZero();
    }

    @Test
    void nunca_borra_lo_que_tiene_propuestas_ni_lo_que_crea_una_persona() {
        assertThat(codigosConjunto()).contains("IBERDROLA-FIJO-20260101", "NATURGY-FIJO-20251003",
                "ELECTRICA-FIJO-20261001", "IRIS-FIJO-20261001", "NATURGY-FIJO-20261001");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcas_reglas_comision WHERE codigo = 'IBERDROLA_FUERA_HASTA_20KW'",
                Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcas_valores_rango v JOIN mcas_reglas_comision r"
                + " ON r.id = v.id_regla WHERE r.codigo = 'MI_REGLA'", Long.class)).isEqualTo(1L);
        assertThat(propuestas.count()).isEqualTo(2);
    }

    @Test
    void quita_las_politicas_precargadas_sin_propuestas_y_sus_tramos() {
        assertThat(codigosPolitica()).doesNotContain("RET-ENDESA-PYME-20260309")
                .contains("RET-IBERDROLA-2_0TD-20260101", "RET-IRIS-PERSONA");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcas_tramos_retrocesion t LEFT JOIN"
                + " mcas_politicas_retrocesion p ON p.id = t.id_politica WHERE p.id IS NULL", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcas_tramos_retrocesion t JOIN mcas_politicas_retrocesion p"
                + " ON p.id = t.id_politica WHERE p.codigo = 'RET-IBERDROLA-2_0TD-20260101'", Long.class)).isEqualTo(2L);
    }

    @Test
    void quita_los_productos_precargados_que_nadie_usa() {
        assertThat(productosDe(visalia)).containsExactly("JADE");
        assertThat(productosDe(iberdrola)).containsExactlyInAnyOrder("OFERTA_0", "PLAN_ONLINE");
        assertThat(productosDe(naturgy)).containsExactly("PF_LUZ");
        assertThat(productosDe(iris)).containsExactly("PLAN_PERSONALIZADO");
    }

    @Test
    void no_toca_los_interruptores() {
        assertThat(modulos.estaActivo(McasProposalService.FLAG_ENABLED)).isTrue();
        assertThat(modulos.estaActivo(McasProposalService.FLAG_FIJO)).isFalse();
    }

    @Test
    void la_comprobacion_final_cuenta_lo_quitado_y_lista_lo_que_se_queda() {
        assertThat(primera).hasSize(3);
        assertThat(totales(primera.get(0))).containsExactly(3L, 3L, 1L, 3L);   // tablas, reglas, políticas, productos
        assertThat(primera.get(1)).extracting(f -> f.get("TIPO") + " " + f.get("CODIGO")).containsExactlyInAnyOrder(
                "tabla de comisiones IBERDROLA-FIJO-20260101",
                "tabla de comisiones NATURGY-FIJO-20251003",
                "tabla copiada de la precarga IRIS-FIJO-20261001",
                "política de retrocesión RET-IBERDROLA-2_0TD-20260101",
                "producto comercial PLAN_PERSONALIZADO",
                "producto comercial OFERTA_0",
                "producto comercial PF_LUZ");
        assertThat(primera.get(1)).filteredOn(f -> "IBERDROLA-FIJO-20260101".equals(f.get("CODIGO")))
                .extracting(f -> ((Number) f.get("PROPUESTAS")).longValue() + "/" + ((Number) f.get("CON_SERVICIO")).longValue())
                .containsExactly("1/1");
        assertThat(primera.get(2)).extracting(f -> f.get("CODIGO") + "=" + f.get("ACTIVO"))
                .contains("MCAS_ENABLED=true", "MCAS_FIXED_ENABLED=false");
    }

    @Test
    void es_idempotente() {
        assertThat(recuentos()).isEqualTo(recuentoTrasPrimera);
        assertThat(totales(segunda.get(0))).containsExactly(0L, 0L, 0L, 0L);
        assertThat(segunda.get(1)).hasSameSizeAs(primera.get(1));
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    /**
     * Ejecuta el script en una sola conexión (como un cliente SQL) y devuelve el
     * resultado de cada SELECT, en orden. Adapta a H2 las tres sentencias que solo
     * existen en MariaDB (ver la documentación de la clase).
     */
    private List<List<Map<String, Object>>> ejecutarScript() throws Exception {
        String sql = Files.readString(Path.of("src", "main", "resources", "BBDD", SCRIPT), StandardCharsets.UTF_8);
        List<List<Map<String, Object>>> resultados = new ArrayList<>();
        try (Connection conexion = dataSource.getConnection()) {
            JdbcTemplate enLaConexion = new JdbcTemplate(new SingleConnectionDataSource(conexion, true));
            for (String sentencia : sentencias(sql)) {
                String s = sentencia.trim();
                if (s.startsWith("SET NAMES")) continue;
                if (s.equals("START TRANSACTION")) { conexion.setAutoCommit(false); continue; }
                if (s.equals("COMMIT")) { conexion.commit(); conexion.setAutoCommit(true); continue; }
                s = s.replace("DROP TEMPORARY TABLE", "DROP TABLE");
                if (s.startsWith("SELECT")) resultados.add(enLaConexion.queryForList(s));
                else enLaConexion.execute(s);
            }
        }
        return resultados;
    }

    /** Sentencias del script: sin comentarios «--» (fuera de los textos) y separadas por «;». */
    static List<String> sentencias(String sql) {
        List<String> lista = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean enTexto = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (!enTexto && c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                actual.append('\n');
                continue;
            }
            if (c == '\'') enTexto = !enTexto;
            if (!enTexto && c == ';') {
                if (!actual.toString().isBlank()) lista.add(actual.toString().trim());
                actual.setLength(0);
                continue;
            }
            actual.append(c);
        }
        if (!actual.toString().isBlank()) lista.add(actual.toString().trim());
        return lista;
    }

    private List<Long> totales(List<Map<String, Object>> filas) {
        return filas.stream().map(f -> ((Number) f.get("TOTAL")).longValue()).toList();
    }

    private Map<String, Long> recuentos() {
        Map<String, Long> m = new java.util.LinkedHashMap<>();
        for (String tabla : List.of("mcas_productos_comerciales", "mcas_conjuntos_reglas", "mcas_reglas_comision",
                "mcas_valores_rango", "mcas_politicas_retrocesion", "mcas_tramos_retrocesion", "mcas_propuestas",
                "mcas_lineas_propuesta", "configuracion_modulos"))
            m.put(tabla, jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class));
        return m;
    }

    private List<String> codigosConjunto() {
        return conjuntos.findAll().stream().map(McasConjuntoReglas::getCodigo).toList();
    }

    private List<String> codigosPolitica() {
        return politicas.findAll().stream().map(McasPoliticaRetrocesion::getCodigo).toList();
    }

    private List<String> productosDe(Integer comercializadora) {
        return productos.findAll().stream().filter(p -> comercializadora.equals(p.getIdComercializadora()))
                .map(McasProductoComercial::getCodigo).toList();
    }

    private Integer comercializadora(String nombre) {
        return comercializadoras.save(TBComercializadoras.builder().nombre(nombre).build()).getId();
    }

    private McasConjuntoReglas conjunto(String codigo, Integer comercializadora, String creadoPor, String archivo,
                                        String estado) {
        return conjuntos.save(McasConjuntoReglas.builder().codigo(codigo).idComercializadora(comercializadora)
                .vigenciaDesde(LocalDate.of(2026, 1, 1)).estado(estado).creadoPor(creadoPor).archivoOrigen(archivo)
                .build());
    }

    private McasReglaComision regla(McasConjuntoReglas conjunto, String codigo, String metodo, String condiciones) {
        return reglas.save(McasReglaComision.builder().idConjuntoReglas(conjunto.getId()).codigo(codigo)
                .prioridad(10).metodoCalculo(metodo).condicionesJson(condiciones).activo(true).build());
    }

    private Integer politica(String codigo, Integer comercializadora, String archivo) {
        Integer id = politicas.save(McasPoliticaRetrocesion.builder().codigo(codigo).idComercializadora(comercializadora)
                .version("1").vigenciaDesde(LocalDate.of(2026, 1, 1)).metodo("PORCENTAJE").limiteImportePagado(true)
                .archivoOrigen(archivo).estado("BORRADOR").build()).getId();
        tramos.save(McasTramoRetrocesion.builder().idPolitica(id).unidad("DIAS").desdeValor(0).hastaValor(90)
                .valor(BigDecimal.ONE).tipoValor("PORCENTAJE").build());
        tramos.save(McasTramoRetrocesion.builder().idPolitica(id).unidad("DIAS").desdeValor(90).hastaValor(180)
                .valor(new BigDecimal("0.5")).tipoValor("PORCENTAJE").build());
        return id;
    }

    private Integer producto(Integer comercializadora, String codigo, String desde) {
        return productos.save(McasProductoComercial.builder().idComercializadora(comercializadora).codigo(codigo)
                .nombre(codigo).tipoPrecio("FIJO").tipoSuministro("LUZ")
                .activoDesde(desde == null ? null : LocalDate.parse(desde)).build()).getId();
    }
}
