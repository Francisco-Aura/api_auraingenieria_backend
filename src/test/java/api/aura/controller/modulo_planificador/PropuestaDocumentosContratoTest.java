package api.aura.controller.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.entity.modulo_planificador.pl_Presupuesto;
import api.aura.entity.modulo_planificador.pl_Propuesta;
import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import api.aura.repository.modulo_planificador.pl_PresupuestoRepository;
import api.aura.repository.modulo_planificador.pl_PropuestaRepository;
import api.aura.service.modulo_planificador.PropuestaDatos;
import api.aura.service.modulo_planificador.PropuestaHtml;
import api.aura.service.modulo_planificador.PropuestaPptx;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Los documentos de la propuesta: datos, presentación HTML y PowerPoint. Los datos y el HTML se comparan con ejemplos generados con el código
 * Python del Planificador actual; el PowerPoint se comprueba por su estructura (seis diapositivas y su texto).
 */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class PropuestaDocumentosContratoTest {

    @Autowired
    private PropuestaHtml html;
    @Autowired
    private PropuestaPptx pptx;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private pl_PresupuestoRepository presupuestos;
    @Autowired
    private pl_PropuestaRepository propuestas;
    @Autowired
    private pl_EquipoRepository equiposRepo;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        for (String t : new String[]{"pl_propuestas", "pl_presupuestos", "pl_historial_precios", "pl_equipos"}) {
            jdbc.execute("DELETE FROM " + t);
        }
    }

    private String recurso(String nombre) throws IOException {
        try (var in = getClass().getResourceAsStream("/propuesta-dorado/" + nombre)) {
            assertNotNull(in, nombre);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private JsonNode entrada() throws IOException {
        return json.readTree(recurso("entrada.json"));
    }

    private pl_Presupuesto presupuesto(JsonNode e) {
        pl_Presupuesto b = new pl_Presupuesto();
        b.setId("b-1");
        b.setNombre(e.get("name").asText());
        b.setKwp(e.get("kwp").asDouble());
        b.setPaneles(e.get("panels").asInt());
        b.setTotalPresupuesto(e.get("total").asDouble());
        b.setDatos(json.convertValue(e.get("datos"), new TypeReference<LinkedHashMap<String, Object>>() {
        }));
        b.setCreadoEl(LocalDateTime.now());
        b.setActualizadoEl(LocalDateTime.now());
        return b;
    }

    private Map<String, pl_Equipo> equipos(JsonNode e) {
        Map<String, pl_Equipo> mapa = new HashMap<>();
        for (JsonNode x : e) {
            pl_Equipo q = new pl_Equipo();
            q.setId(x.get("id").asText());
            q.setMarca(x.get("marca").asText());
            q.setModelo(x.get("modelo").asText());
            q.setCategoria(x.get("categoria").asText());
            q.setAtributos(json.convertValue(x.get("atributos"), new TypeReference<LinkedHashMap<String, Object>>() {
            }));
            mapa.put(q.getId(), q);
        }
        return mapa;
    }

    private static List<String> textosDe(XSLFSlide d) {
        List<String> textos = new ArrayList<>();
        d.getShapes().forEach(s -> {
            if (s instanceof XSLFTextShape t && !t.getText().isBlank()) textos.add(t.getText().replace("\u000b", "\n"));
        });
        return textos;
    }

    // ---------------------------------------------------------------------------------- datos

    @Test
    void losDatosDelPowerPointSonLosDelPlanificadorActual() throws Exception {
        JsonNode e = entrada();
        Map<String, Object> datos = PropuestaDatos.paraPptx(presupuesto(e.get("budget")), equipos(e.get("equipos")));
        assertEquals(json.readTree(recurso("datos-pptx.json")), json.valueToTree(datos));
    }

    @Test
    void losDatosDeLaPresentacionSonLosDelPlanificadorActual() throws Exception {
        JsonNode e = entrada();
        Map<String, Object> datos = PropuestaDatos.paraHtml(presupuesto(e.get("budget")), equipos(e.get("equipos")));
        datos.put("logo", "data:image/png;base64,AAAA");
        assertEquals(json.readTree(recurso("datos-html.json")), json.valueToTree(datos));
    }

    @Test
    void sinNadaElegidoLaPresentacionVaEnBlancoYNoInventaUnInversor() throws Exception {
        pl_Presupuesto b = new pl_Presupuesto();
        b.setNombre("Vacío");
        Map<String, Object> d = PropuestaDatos.paraHtml(b, Map.of());
        assertEquals("", d.get("panel"));
        assertEquals("", d.get("inverter"));
        assertEquals(0, d.get("inverter_count"));
        assertEquals("", d.get("strings"));
        assertEquals("", d.get("supply"));
        Map<String, Object> pp = PropuestaDatos.paraPptx(b, Map.of());
        assertEquals("—", pp.get("panel"));
        assertEquals(1, pp.get("inverter_count"));
        assertEquals("Vacío", pp.get("client"));
    }

    // --------------------------------------------------------------------------------------- HTML

    @Test
    void laPresentacionHtmlEsIgualALaDelPlanificadorActualSalvoSusDatos() throws Exception {
        JsonNode e = entrada();
        Map<String, Object> datos = PropuestaDatos.paraHtml(presupuesto(e.get("budget")), equipos(e.get("equipos")));
        datos.put("logo", "data:image/png;base64,AAAA");
        String generado = html.generar(datos);
        String neutro = Pattern.compile("(<script id=\"datos\" type=\"application/json\">)(.*?)(</script>)", Pattern.DOTALL).matcher(generado)
                .replaceFirst(m -> m.group(1) + "@@" + m.group(3));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "propuesta-pruebas"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target", "propuesta-pruebas", "propuesta.html"), neutro);
        String[] esperado = recurso("propuesta.sha256").trim().split(" ");
        assertEquals(Integer.parseInt(esperado[1]), neutro.codePointCount(0, neutro.length()), "mismo tamaño");
        assertEquals(esperado[0], sha256(neutro), "mismo contenido, con las imágenes incrustadas");
        assertFalse(generado.contains("src=\"logos/") || generado.contains("src=\"proyectos/"), "ninguna imagen queda con ruta relativa");
        // Los datos viajan dentro del propio archivo
        var m = Pattern.compile("<script id=\"datos\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL).matcher(generado);
        assertTrue(m.find());
        assertEquals(json.readTree(recurso("datos-html.json")), json.readTree(m.group(1)));
    }

    private static String sha256(String texto) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Test
    void laPresentacionDelClienteSeSacaDelBorradorSinGuardarNiValidarNada() throws Exception {
        JsonNode e = entrada();
        equipos(e.get("equipos")).values().forEach(q -> {
            q.setPartida("");
            equiposRepo.save(q);
        });
        JsonNode b = e.get("budget");
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("name", b.get("name").asText());
        cuerpo.put("data", json.convertValue(b.get("datos"), new TypeReference<LinkedHashMap<String, Object>>() {
        }));
        cuerpo.put("kwp", b.get("kwp").asDouble());
        cuerpo.put("panels", b.get("panels").asInt());
        cuerpo.put("budget_total", b.get("total").asDouble());
        String salida = mvc.perform(post("/api/planificador/budget/proposal-html").contentType("application/json").content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/html")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var m = Pattern.compile("<script id=\"datos\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL).matcher(salida);
        assertTrue(m.find());
        JsonNode datos = json.readTree(m.group(1));
        assertEquals(b.get("datos").get("customer").get("name").asText(), datos.get("client").asText(), "el cliente es el de los datos del presupuesto");
        assertEquals(b.get("total").asDouble(), datos.get("total").asDouble(), 0.001);
        assertEquals("", datos.get("logo").asText(), "sin propuesta no hay logo del cliente");
        assertEquals(0, propuestas.count(), "no crea ninguna propuesta");
        assertEquals(0, presupuestos.count(), "no guarda el presupuesto");
    }

    // ------------------------------------------------------------------------------------ PowerPoint

    @Test
    void elPowerPointTieneSeisDiapositivasConLosDatosDelPresupuesto() throws Exception {
        JsonNode e = entrada();
        Map<String, Object> datos = PropuestaDatos.paraPptx(presupuesto(e.get("budget")), equipos(e.get("equipos")));
        byte[] bytes = pptx.generar(datos);
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            assertEquals(6, ppt.getSlides().size());
            assertEquals(842, ppt.getPageSize().width, 1);
            assertEquals(595, ppt.getPageSize().height, 1);
            for (XSLFSlide d : ppt.getSlides()) {
                assertTrue(d.getShapes().stream().anyMatch(s -> s instanceof XSLFPictureShape), "cada diapositiva lleva su imagen de fondo");
            }
            List<String> portada = textosDe(ppt.getSlides().get(0));
            assertEquals(List.of("Nombre de la empresa: BONAQUIA, S.L."), portada);
            List<String> tecnica = textosDe(ppt.getSlides().get(1));
            assertTrue(tecnica.containsAll(List.of("Propuesta", "Técnica", "Autoconsumo", "22,50 kWp", "40", "BYD Battery Box × 2", "3")), tecnica.toString());
            List<String> equiposTexto = textosDe(ppt.getSlides().get(2));
            assertTrue(equiposTexto.contains("JA Solar 550 Wp\n40 unidades"), equiposTexto.toString());
            assertTrue(equiposTexto.contains("HUAWEI SUN2000-20KTL + Huawei 5K\n3 unidades"), equiposTexto.toString());
            List<String> estructura = textosDe(ppt.getSlides().get(3));
            assertTrue(estructura.contains("Coplanar a cubierta") && estructura.contains("✓ Incluida"));
            assertEquals(1, estructura.stream().filter("✓ Incluida"::equals).count(), "solo una estructura marcada");
            List<String> garantias = textosDe(ppt.getSlides().get(4));
            assertTrue(garantias.contains("Garantía de producción") && garantias.contains("30"));
            List<String> economica = textosDe(ppt.getSlides().get(5));
            assertTrue(economica.contains("13.640,00 €"), economica.toString());
            assertTrue(economica.contains("30 %") && economica.contains("40 %") && economica.contains("20 %") && economica.contains("10 %"));
            assertTrue(economica.contains("4.092,00 €"), "el 30 % del total");
            assertTrue(economica.contains("IVA no incluido"));
        }
    }

    @Test
    void laDireccionSoloSalePorSiSeConoceYLosPagosPersonalizadosSeRespetan() throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>(PropuestaDatos.paraPptx(presupuesto(entrada().get("budget")), equipos(entrada().get("equipos"))));
        datos.put("address", "Calle Mayor 1");
        datos.put("payment", List.of(50, 25, 15, 10));
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(pptx.generar(datos)))) {
            assertTrue(textosDe(ppt.getSlides().get(0)).contains("Dirección: Calle Mayor 1"));
            assertTrue(textosDe(ppt.getSlides().get(5)).contains("50 %"));
            assertTrue(textosDe(ppt.getSlides().get(5)).contains("6.820,00 €"));
        }
    }

    // -------------------------------------------------------------------------------------- endpoints

    private String propuestaGuardada() throws Exception {
        JsonNode e = entrada();
        for (JsonNode x : e.get("equipos")) {
            pl_Equipo q = new pl_Equipo();
            q.setId(x.get("id").asText());
            q.setMarca(x.get("marca").asText());
            q.setModelo(x.get("modelo").asText());
            q.setCategoria(x.get("categoria").asText());
            q.setPartida("electricidad");
            q.setAtributos(json.convertValue(x.get("atributos"), new TypeReference<LinkedHashMap<String, Object>>() {
            }));
            equiposRepo.save(q);
        }
        pl_Presupuesto b = presupuesto(e.get("budget"));
        b.setId("presupuesto-1");
        b.setEstado("validado");
        presupuestos.save(b);
        pl_Propuesta p = new pl_Propuesta();
        p.setId("propuesta-1");
        p.setPresupuestoId(b.getId());
        p.setVersion(2);
        p.setCreadoEl(LocalDateTime.now());
        p.setLogoCliente("data:image/png;base64,AAAA");
        propuestas.save(p);
        return p.getId();
    }

    @Test
    void losEndpointsDescarganElPowerPointYLaPresentacionConSuNombre() throws Exception {
        String id = propuestaGuardada();
        var r = mvc.perform(get("/api/planificador/proposals/" + id + "/pptx")).andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.presentationml.presentation")).andReturn();
        assertEquals("attachment; filename*=UTF-8''Propuesta%20Nave%20Bonaquia%20v2.pptx", r.getResponse().getHeader("Content-Disposition"));
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
            assertEquals(6, ppt.getSlides().size());
            assertTrue(textosDe(ppt.getSlides().get(0)).get(0).contains("BONAQUIA, S.L."));
        }
        var h = mvc.perform(get("/api/planificador/proposals/" + id + "/html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html")).andReturn();
        assertEquals("attachment; filename*=UTF-8''Propuesta%20Nave%20Bonaquia%20v2.html", h.getResponse().getHeader("Content-Disposition"));
        String cuerpo = h.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(cuerpo.contains("\"logo\": \"data:image/png;base64,AAAA\"") || cuerpo.contains("\"logo\":\"data:image/png;base64,AAAA\""), "lleva el logo del cliente");
        assertTrue(cuerpo.contains("HUAWEI SUN2000-20KTL + Huawei 5K"));
    }

    @Test
    void unaPropuestaOUnPresupuestoQueNoExistenSonUn404() throws Exception {
        mvc.perform(get("/api/planificador/proposals/nada/pptx")).andExpect(status().isNotFound());
        mvc.perform(get("/api/planificador/proposals/nada/html")).andExpect(status().isNotFound());
        pl_Propuesta p = new pl_Propuesta();
        p.setId("huerfana");
        p.setPresupuestoId("no-existe");
        p.setCreadoEl(LocalDateTime.now());
        propuestas.save(p);
        mvc.perform(get("/api/planificador/proposals/huerfana/pptx")).andExpect(status().isNotFound());
    }
}
