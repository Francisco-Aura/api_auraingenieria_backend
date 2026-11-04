package api.aura.mcas;

import api.aura.config.AuraUserFilter;
import api.aura.config.UsuarioSesion;
import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.entity.Usuario;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.entity.tablas_maestras.TBProductos;
import api.aura.entity.tablas_maestras.TBTarifasLuz;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ServicioRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.mcas.McasPropuestaRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import api.aura.repository.tablas_maestras.TBProductosRepository;
import api.aura.repository.tablas_maestras.TBTarifasLuzRepository;
import api.aura.service.ContratoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCAS recién instalado: base de datos sin ninguna tabla de comisiones, regla,
 * producto comercial ni política de retrocesión (lo que queda tras los scripts de
 * esquema, sin precarga). Comprueba que el módulo funciona vacío y que el
 * administrador puede montar desde cero, con los mismos endpoints que usa la
 * pantalla, las tablas de ejemplo del manual (importes inventados) y calcular los
 * contratos de ejemplo.
 *
 * <p>BD H2 propia (los demás tests dejan datos en la compartida) y pasos en orden:
 * (1) arranque vacío, (2) tablas creadas desde cero, (3) retrocesión. Sin
 * {@code @Transactional}: la propuesta se genera tras el commit del contrato.</p>
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mcas_arranque_vacio;MODE=MySQL;"
        + "NON_KEYWORDS=USER,VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McasArranqueVacioTest {

    private static final String SIN_TABLA = "Esta comercializadora no tiene una tabla de comisiones activa en la fecha "
            + "del contrato (16/03/2026); la comisión se revisará manualmente.";

    @Autowired private WebApplicationContext contexto;
    @Autowired private ObjectMapper mapper;
    @Autowired private ContratoService contratoService;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ClienteRepository clientes;
    @Autowired private TBComercializadorasRepository comercializadoras;
    @Autowired private TBTarifasLuzRepository tarifasLuz;
    @Autowired private TBProductosRepository productosServicio;
    @Autowired private TBPagoPlazosRepository pagoPlazos;
    @Autowired private McasPropuestaRepository propuestas;
    @Autowired private ServicioRepository servicios;

    private MockMvc mvc;
    private UsuarioSesion admin;
    private Integer iris, endesa, naturgy, unielectrica, iberdrola, total;
    private Integer tarifa30;

    @BeforeAll
    void instalacionNueva() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(new AuraUserFilter()).build();
        Usuario u = usuarios.save(Usuario.builder().nombre("Admin Demo").user("admin.demo").nivelAcceso(1).build());
        admin = new UsuarioSesion(u.getId(), u.getNombre(), u.getNivelAcceso());
        // Datos maestros que ya existen en cualquier Aura (no son de MCAS)
        iris = comercializadora("IRIS ENERGIA, S.L.");
        endesa = comercializadora("ENDESA ENERGIA S.A.U.");
        naturgy = comercializadora("NATURGY IBERIA, S.A.");
        unielectrica = comercializadora("UNIELÉCTRICA ENERGÍA, S.A.");
        iberdrola = comercializadora("IBERDROLA CLIENTES S.A.U.");
        total = comercializadora("TOTALENERGIES CLIENTES");
        tarifa30 = tarifasLuz.save(TBTarifasLuz.builder().nombre("3.0TD").build()).getId();
        productosServicio.save(TBProductos.builder().nombre("SWITCHING").build());
        pagoPlazos.save(TBPagoPlazos.builder().nombre("1 PLAZO").numeroCuotas(1).build());
    }

    // =====================================================================
    // 1. Recién instalado: vacío y sin errores
    // =====================================================================

    @Test
    @Order(1)
    void recien_instalado_todo_vacio_sin_errores_y_sin_tabla_se_completa_a_mano() throws Exception {
        for (String listado : List.of("/api/mcas/conjuntos-reglas", "/api/mcas/productos-comerciales",
                "/api/mcas/politicas-retrocesion", "/api/mcas/propuestas"))
            assertThat(get(listado)).as(listado).isEmpty();
        assertThat(get("/api/mcas/propuestas/recuento").path("total").asLong()).isZero();
        // Interruptores apagados (en una BD real los crea V20260913_1 a 0; aquí ni existen)
        for (JsonNode interruptor : get("/api/configuracion-modulos/mcas"))
            assertThat(interruptor.path("activo").asBoolean()).isFalse();

        // Vista previa de un contrato sin tabla: apagado primero, «sin tabla» después
        JsonNode apagado = post("/api/mcas/vista-previa", contratoJson(total, "TOT-VP").put("mcasSegmento", "PYME"));
        assertThat(apagado.path("estado").asText()).isEqualTo("DESACTIVADO");
        encender();
        JsonNode previa = post("/api/mcas/vista-previa", contratoJson(total, "TOT-VP").put("mcasSegmento", "PYME"));
        assertThat(previa.path("estado").asText()).isEqualTo("SIN_REGLAS");
        assertThat(previa.path("mensaje").asText()).isEqualTo(SIN_TABLA);

        // Guardar un contrato de una comercializadora sin tabla: revisión manual con mensaje claro
        Contrato contrato = guardar(contratoJson(total, "TOT-1").put("mcasSegmento", "PYME").put("consumoAnual", 20_000));
        JsonNode propuesta = get("/api/mcas/propuestas/contrato/" + contrato.getId()).get(0);
        assertThat(propuesta.path("estado").asText()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(propuesta.path("advertencias")).extracting(JsonNode::asText).containsExactly(SIN_TABLA);
        assertThat(propuesta.path("admiteEdicionManual").asBoolean()).isTrue();
        assertThat(get("/api/mcas/propuestas/contrato/" + contrato.getId() + "/resumen").path("mensaje").asText())
                .contains("no tiene una tabla de comisiones activa");
        assertThat(get("/api/mcas/propuestas/recuento").path("porRevisar").asLong()).isEqualTo(1);

        // «Completar manualmente»: editar y finalizar con el importe que diga el validador
        long id = propuesta.path("id").asLong();
        assertThat(post("/api/mcas/propuestas/" + id + "/editar", null).path("estado").asText()).isEqualTo("EN_EDICION");
        JsonNode hecha = post("/api/mcas/propuestas/" + id + "/finalizar-editado", mapper.createObjectNode()
                .put("importeDefinitivo", 75).put("numeroPagos", 1).put("motivo", "TotalEnergies aún no tiene tabla"));
        assertThat(hecha.path("estado").asText()).isEqualTo("FINALIZADO_EDITADO");
        assertThat(servicios.findById(hecha.path("idServicioCreado").asInt()).orElseThrow().getObservaciones())
                .isEqualTo("Comisión generada por MCAS. Propuesta #" + id + ". Importe introducido manualmente. "
                        + "Aprobada por: Admin Demo.");
        assertThat(get("/api/mcas/propuestas/recuento").path("total").asLong()).isZero();
    }

    // =====================================================================
    // 2. Las tablas de ejemplo del manual, creadas desde cero
    // =====================================================================

    @Test
    @Order(2)
    void las_tablas_de_ejemplo_se_crean_desde_cero_se_activan_y_calculan_los_contratos_de_ejemplo() throws Exception {
        encender();
        // Productos sin código: se genera desde el nombre
        assertThat(producto(iris, "Plan Personalizado")).isEqualTo("PLAN_PERSONALIZADO");
        assertThat(producto(iberdrola, "Plan Estable")).isEqualTo("PLAN_ESTABLE");
        assertThat(producto(iberdrola, "Plan Online")).isEqualTo("PLAN_ONLINE");

        // Iris: % sobre el FEE (40 %, FEE mínimo 5), importe fijo y regla a mano
        int irisTabla = tabla(iris, "Iris 2026");
        regla(irisTabla, "Iris · pymes luz", "FEE_PERCENTAGE",
                "{\"tipo_suministro\":\"LUZ\",\"segmento\":\"PYME\",\"codigo_producto\":\"PLAN_PERSONALIZADO\",\"es_renovacion\":false}",
                "{\"porcentaje_aplicacion_fee\":0.4,\"fee_minimo_eur_mwh\":5}", null);
        regla(irisTabla, "Iris · residencial", "FIXED_AMOUNT", "{\"segmento\":\"RESIDENCIAL\",\"es_renovacion\":false}",
                "{\"importe\":50}", null);
        regla(irisTabla, "Iris · renovaciones", "MANUAL", "{\"es_renovacion\":true}", null,
                "Las renovaciones de Iris se revisan a mano");

        // Endesa: tabla consumo (filas) × potencia (columnas)
        int endesaTabla = tabla(endesa, "Endesa 2026");
        int matriz = regla(endesaTabla, "Endesa · pymes 3.0TD", "MATRIX_LOOKUP",
                "{\"segmento\":\"PYME\",\"tarifa_acceso\":\"3.0TD\"}", null, null);
        double[][] consumos = {{0, 20_000}, {20_000, 50_000}, {50_000, 100_000}};
        double[][] potencias = {{0, 15}, {15, 30}, {30, 50}};
        int[][] importes = {{100, 150, 200}, {150, 200, 250}, {200, 250, 300}};
        ArrayNode celdas = mapper.createArrayNode();
        int orden = 1;
        for (int f = 0; f < 3; f++)
            for (int c = 0; c < 3; c++, orden++) {
                celdas.add(tramo("consumo_kwh", consumos[f][0], consumos[f][1], false, importes[f][c], orden));
                celdas.add(tramo("potencia_kw", potencias[c][0], potencias[c][1], false, importes[f][c], orden));
            }
        guardarTabla(matriz, celdas);
        regla(endesaTabla, "Endesa · residencial", "FIXED_AMOUNT", "{\"segmento\":\"RESIDENCIAL\"}", "{\"importe\":60}", null);

        // Naturgy: 4 € por MWh
        int naturgyTabla = tabla(naturgy, "Naturgy 2026");
        regla(naturgyTabla, "Naturgy · pymes", "RATE_PER_MWH", "{\"segmento\":\"PYME\"}", "{\"tasa_eur_mwh\":4}", null);

        // Unieléctrica: tabla por tramos de consumo
        int uniTabla = tabla(unielectrica, "Unieléctrica 2026");
        int tramos = regla(uniTabla, "Unieléctrica · tramos de consumo", "RANGE_LOOKUP", null, null, null);
        ArrayNode filas = mapper.createArrayNode();
        filas.add(tramo("consumo_kwh", 0, 5_000, false, 40, 1));
        filas.add(tramo("consumo_kwh", 5_000, 20_000, true, 80, 2));
        filas.add(((ObjectNode) tramo("consumo_kwh", 20_000, null, false, 120, 3)).put("minInclusivo", false));
        guardarTabla(tramos, filas);

        // Iberdrola: varios datos (zona, potencia, oferta) y condición de ámbito
        int iberdrolaTabla = tabla(iberdrola, "Iberdrola 2026");
        int varios = regla(iberdrolaTabla, "Iberdrola · fuera de ámbito", "MULTI_DIMENSION_LOOKUP",
                "{\"ambito\":\"FUERA\"}", null, null);
        ArrayNode cubo = mapper.createArrayNode();
        orden = 1;
        String[] zonas = {"ZBR", "ZAR"};
        String[] ofertas = {"PLAN_ESTABLE", "PLAN_ONLINE"};
        int[][][] precios = {{{70, 90}, {110, 130}}, {{60, 80}, {100, 120}}};
        for (int z = 0; z < 2; z++)
            for (int f = 0; f < 2; f++)
                for (int o = 0; o < 2; o++, orden++) {
                    int importe = precios[z][f][o];
                    cubo.add(categoria("zona_comision", zonas[z], importe, orden));
                    cubo.add(tramo("potencia_kw", f == 0 ? 0 : 15, f == 0 ? 15 : 50, false, importe, orden));
                    cubo.add(categoria("codigo_producto", ofertas[o], importe, orden));
                }
        guardarTabla(varios, cubo);

        // «Probar con un contrato» con la tabla aún en borrador: nombre legible y textos sin códigos
        Contrato uni = guardar(contratoJson(unielectrica, "UNI-1").put("consumoAnual", 12_000));
        JsonNode prueba = get("/api/mcas/simulaciones?conjunto=" + uniTabla + "&contrato=UNI-1");
        assertThat(prueba.path("resultado").asText()).isEqualTo("CALCULADO");
        assertThat(prueba.path("importe").decimalValue()).isEqualByComparingTo("80");
        assertThat(prueba.path("nombreRegla").asText()).isEqualTo("Unieléctrica · tramos de consumo");
        assertThat(prueba.path("reglaAplicada").asText()).isEqualTo("UNIELECTRICA_TRAMOS_DE_CONSUMO");
        assertThat(prueba.path("nombreConjunto").asText()).isEqualTo("Unieléctrica 2026");
        assertThat(prueba.path("lineas").get(0).path("descripcion").asText())
                .isEqualTo("Tabla por tramos: consumo anual 12.000 kWh");
        assertThat(prueba.path("avisos")).extracting(JsonNode::asText)
                .contains("La tabla está en borrador: el cálculo automático solo usa las tablas activas.");
        // Sin tabla activa, la propuesta del contrato quedó en revisión manual
        assertThat(ultima(uni).getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");

        // Comprobar y activar cada tabla
        for (int t : List.of(irisTabla, endesaTabla, naturgyTabla, uniTabla, iberdrolaTabla)) {
            JsonNode comprobacion = get("/api/mcas/conjuntos-reglas/" + t + "/comprobacion");
            assertThat(comprobacion.path("puedeActivar").asBoolean()).as(comprobacion.toString()).isTrue();
            assertThat(post("/api/mcas/conjuntos-reglas/" + t + "/activar", null).path("estado").asText())
                    .isEqualTo("PUBLICADO");
        }

        // La propuesta de Unieléctrica se recalcula con la tabla activa
        long idUni = ultima(uni).getId();
        assertThat(post("/api/mcas/propuestas/" + idUni + "/recalcular", null).path("importeAutomatico").decimalValue())
                .isEqualByComparingTo("80.00");

        // Contratos de ejemplo
        calcula(contratoJson(iris, "IRIS-1").put("mcasSegmento", "PYME").put("consumoAnual", 30_000)
                .put("mcasFeeEurMwh", 12).put("mcasFeeUnidad", "EUR_MWH")
                .put("mcasIdProductoComercial", idProducto(iris, "PLAN_PERSONALIZADO")), "144.00", "Iris · pymes luz");
        calcula(contratoJson(endesa, "END-1").put("mcasSegmento", "PYME").put("consumoAnual", 35_000)
                .put("idTarifaLuz", tarifa30).put("potencia1", 20), "200.00", "Endesa · pymes 3.0TD");
        calcula(contratoJson(naturgy, "NAT-1").put("mcasSegmento", "PYME").put("consumoAnual", 50_000),
                "200.00", "Naturgy · pymes");
        calcula(contratoJson(iberdrola, "IBE-1").put("mcasSegmento", "PYME").put("consumoAnual", 9_000)
                .put("mcasAmbito", "FUERA").put("mcasZonaComision", "ZBR").put("potencia1", 10)
                .put("mcasIdProductoComercial", idProducto(iberdrola, "PLAN_ONLINE")), "90.00", "Iberdrola · fuera de ámbito");
        calcula(contratoJson(iris, "IRIS-2").put("mcasSegmento", "RESIDENCIAL").put("consumoAnual", 3_000),
                "50.00", "Iris · residencial");

        // La regla a mano manda la renovación a revisión con su motivo
        Contrato renovacion = guardar(contratoJson(iris, "IRIS-3").put("mcasSegmento", "PYME").put("consumoAnual", 30_000)
                .put("mcasEsRenovacion", true).put("mcasTipoRenovacion", "FIRMADA"));
        JsonNode aMano = get("/api/mcas/propuestas/contrato/" + renovacion.getId()).get(0);
        assertThat(aMano.path("estado").asText()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(aMano.path("advertencias")).extracting(JsonNode::asText)
                .containsExactly("Las renovaciones de Iris se revisan a mano");

        // Aprobar: el servicio dice qué regla y qué tabla, por su nombre
        Contrato irisContrato = guardar(contratoJson(iris, "IRIS-4").put("mcasSegmento", "PYME").put("consumoAnual", 30_000)
                .put("mcasFeeEurMwh", 12).put("mcasFeeUnidad", "EUR_MWH")
                .put("mcasIdProductoComercial", idProducto(iris, "PLAN_PERSONALIZADO")));
        JsonNode aprobada = post("/api/mcas/propuestas/" + ultima(irisContrato).getId() + "/aprobar", null);
        assertThat(servicios.findById(aprobada.path("idServicioCreado").asInt()).orElseThrow().getObservaciones())
                .contains("Regla «Iris · pymes luz» de la tabla de comisiones «Iris 2026» (versión 2026.01.01)");
    }

    // =====================================================================
    // 3. Retrocesión creada desde cero
    // =====================================================================

    @Test
    @Order(3)
    void una_politica_de_retrocesion_creada_desde_cero_se_publica_y_sale_como_retrocesion_potencial() throws Exception {
        encender();
        ObjectNode politica = mapper.createObjectNode().put("idComercializadora", iberdrola)
                .put("vigenciaDesde", "2026-01-01").put("metodo", "PORCENTAJE")
                .put("descripcion", "Si el cliente se da de baja pronto, Iberdrola nos descuenta la comisión");
        ArrayNode tramos = politica.putArray("tramos");
        tramos.addObject().put("unidad", "DIAS").put("desdeValor", 0).put("hastaValor", 90)
                .put("hastaInclusivo", true).put("valor", 1).put("tipoValor", "PORCENTAJE");
        tramos.addObject().put("unidad", "DIAS").put("desdeValor", 91).put("hastaValor", 180)
                .put("hastaInclusivo", true).put("valor", 0.5).put("tipoValor", "PORCENTAJE");
        JsonNode creada = post("/api/mcas/politicas-retrocesion", politica);
        assertThat(creada.path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(creada.path("codigo").asText()).isEqualTo("RET-IBERDROLA-20260101");
        JsonNode publicada = post("/api/mcas/politicas-retrocesion/" + creada.path("id").asInt() + "/publicar", null);
        assertThat(publicada.path("estado").asText()).isEqualTo("PUBLICADO");

        Contrato contrato = guardar(contratoJson(iberdrola, "IBE-2").put("mcasSegmento", "PYME").put("consumoAnual", 9_000)
                .put("mcasAmbito", "FUERA").put("mcasZonaComision", "ZBR").put("potencia1", 10)
                .put("mcasIdProductoComercial", idProducto(iberdrola, "PLAN_ONLINE")));
        JsonNode propuesta = get("/api/mcas/propuestas/contrato/" + contrato.getId()).get(0);
        assertThat(propuesta.path("importeAutomatico").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(propuesta.path("idPoliticaRetrocesion").asInt()).isEqualTo(creada.path("id").asInt());
        assertThat(propuesta.path("retrocesionPotencial").asText())
                .isEqualTo("Desde la activación: 0–90 días: 100 %; 91–180 días: 50 %. Nunca supera lo cobrado.");
        assertThat(propuesta.path("importeDefinitivo").decimalValue()).as("la retrocesión no descuenta nada")
                .isEqualByComparingTo("90.00");
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    private void encender() throws Exception {
        post("/api/configuracion-modulos/MCAS_ENABLED/activar", null);
        post("/api/configuracion-modulos/MCAS_FIXED_ENABLED/activar", null);
    }

    private String producto(Integer comercializadora, String nombre) throws Exception {
        return post("/api/mcas/productos-comerciales", mapper.createObjectNode().put("idComercializadora", comercializadora)
                .put("nombre", nombre).put("codigo", "").put("tipoPrecio", "FIJO").put("tipoSuministro", "LUZ"))
                .path("codigo").asText();
    }

    private int idProducto(Integer comercializadora, String codigo) throws Exception {
        for (JsonNode p : get("/api/mcas/productos-comerciales?idComercializadora=" + comercializadora))
            if (codigo.equals(p.path("codigo").asText())) return p.path("id").asInt();
        throw new AssertionError("No existe el producto " + codigo);
    }

    private int tabla(Integer comercializadora, String nombre) throws Exception {
        // Como el formulario: «Versión» y «Código interno» vacíos (el servidor los genera)
        JsonNode t = post("/api/mcas/conjuntos-reglas", mapper.createObjectNode().put("nombre", nombre)
                .put("idComercializadora", comercializadora).put("vigenciaDesde", "2026-01-01").put("fasePrecio", "FIJO")
                .put("version", "").put("codigo", ""));
        assertThat(t.path("estado").asText()).isEqualTo("BORRADOR");
        return t.path("id").asInt();
    }

    private int regla(int tabla, String nombre, String metodo, String condiciones, String calculo, String motivo)
            throws Exception {
        ObjectNode r = mapper.createObjectNode().put("nombre", nombre).put("metodoCalculo", metodo)
                .put("politicaPagoJson", "{\"numero_pagos_sugerido\":1}");
        if (condiciones != null) r.put("condicionesJson", condiciones);
        if (calculo != null) r.put("calculoJson", calculo);
        if (motivo != null) r.put("motivoManual", motivo);
        return post("/api/mcas/conjuntos-reglas/" + tabla + "/reglas", r).path("id").asInt();
    }

    private void guardarTabla(int regla, ArrayNode valores) throws Exception {
        String firma = get("/api/mcas/reglas/" + regla + "/tabla").path("firma").asText();
        ObjectNode cambio = mapper.createObjectNode().put("firma", firma);
        cambio.set("valores", valores);
        JsonNode guardada = put("/api/mcas/reglas/" + regla + "/tabla", cambio);
        assertThat(guardada.path("valores").size()).as(guardada.toString()).isEqualTo(valores.size());
    }

    private JsonNode tramo(String dimension, double min, Double max, boolean maxIncluido, int importe, int orden) {
        ObjectNode t = mapper.createObjectNode().put("dimension", dimension).put("valorMin", min)
                .put("minInclusivo", true).put("maxInclusivo", maxIncluido).put("valorResultado", importe)
                .put("orden", orden);
        if (max != null) t.put("valorMax", max);
        return t;
    }

    private JsonNode tramo(String dimension, double min, double max, boolean maxIncluido, int importe, int orden) {
        return tramo(dimension, min, (Double) max, maxIncluido, importe, orden);
    }

    private JsonNode categoria(String dimension, String valor, int importe, int orden) {
        return mapper.createObjectNode().put("dimension", dimension).put("valorCategoria", valor)
                .put("valorResultado", importe).put("orden", orden);
    }

    private ObjectNode contratoJson(Integer comercializadora, String numero) {
        return mapper.valueToTree(Map.of("contrato", numero, "fechaInicio", "2026-03-16", "fechaFin", "2027-03-15",
                "idComercializadoraActual", comercializadora, "mcasTipoPrecio", "FIJO", "mcasTipoSuministro", "LUZ",
                "mcasEsRenovacion", false, "mcasNumeroPagos", 1));
    }

    /** Guarda el contrato como el controlador de contratos: la propuesta se genera tras el commit. */
    private Contrato guardar(ObjectNode cuerpo) throws Exception {
        Contrato c = mapper.treeToValue(cuerpo, Contrato.class);
        c.setCliente(clientes.save(Cliente.builder().nombre("Cliente " + c.getContrato()).cif(c.getContrato()).build()));
        return contratoService.guardarContrato(c);
    }

    private void calcula(ObjectNode cuerpo, String importe, String regla) throws Exception {
        Contrato contrato = guardar(cuerpo);
        JsonNode p = get("/api/mcas/propuestas/contrato/" + contrato.getId()).get(0);
        assertThat(p.path("estado").asText()).as("%s: %s", contrato.getContrato(), p.path("advertencias"))
                .isEqualTo("PENDIENTE_VALIDACION");
        assertThat(p.path("importeAutomatico").decimalValue()).as(contrato.getContrato()).isEqualByComparingTo(importe);
        assertThat(p.path("nombreRegla").asText()).isEqualTo(regla);
    }

    private McasPropuesta ultima(Contrato contrato) {
        return propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId()).get(0);
    }

    private Integer comercializadora(String nombre) {
        return comercializadoras.save(TBComercializadoras.builder().nombre(nombre).build()).getId();
    }

    private JsonNode get(String url) throws Exception {
        return enviar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url), null);
    }

    private JsonNode post(String url, JsonNode cuerpo) throws Exception {
        return enviar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url), cuerpo);
    }

    private JsonNode put(String url, JsonNode cuerpo) throws Exception {
        return enviar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url), cuerpo);
    }

    /** Petición del administrador con su sesión; falla con el cuerpo si no es 200. */
    private JsonNode enviar(MockHttpServletRequestBuilder peticion, JsonNode cuerpo) throws Exception {
        peticion.sessionAttr(UsuarioSesion.ATRIBUTO, admin).header("X-Requested-With", "XMLHttpRequest");
        if (cuerpo != null) peticion.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(cuerpo));
        var respuesta = mvc.perform(peticion).andReturn().getResponse();
        String texto = respuesta.getContentAsString(StandardCharsets.UTF_8);
        assertThat(respuesta.getStatus()).as("%s → %s", peticion, texto).isEqualTo(200);
        return texto.isBlank() ? mapper.createObjectNode() : mapper.readTree(texto);
    }
}
