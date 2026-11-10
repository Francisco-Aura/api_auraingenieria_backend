package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.dropbox.DropboxAlmacen;
import api.aura.service.modulo_planificador.dropbox.DropboxCliente;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pruebas de contrato del contrato de un proyecto en PDF: datos que faltan, guardado en Dropbox (solo el PDF), descarga y alta con contrato. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class ContratoDocumentoContratoTest {

    private static final String BASE = "/api/planificador";
    private static final String BASE_DROPBOX = "/AURA INGENIERIA/02_TRABAJOS/PRUEBAS";
    private static final String CARPETA_CONTRATO = "01_CONTRATO INSTALACION, MANTENIMIENTO Y OFERTA ECONOMICA";
    private static final String BASELINE = "\"baseline\":{\"importe_total\":\"38700\",\"paneles_uds\":\"40\",\"paneles_modelo\":\"JA Solar 550\",\"inversor_uds\":\"1\","
            + "\"inversor_modelo\":\"Huawei 20K\",\"estructura\":\"Coplanar\",\"potencia_kwp\":\"22,5\"}";
    private static final String PARTES = "{\"empresa\":\"BONAQUIA, S.L.\",\"cif\":\"B12345678\",\"domicilio\":\"Calle Mayor 1, Lorca\",\"representante\":\"Juan Perez\","
            + "\"dni\":\"12345678A\",\"cubierta_de\":\"nave industrial\",\"instalacion\":\"Poligono 3, Lorca\",\"kw_nominal\":\"20\"}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DropboxAlmacen almacen;

    private DropboxFalso falso;

    @BeforeEach
    void preparar() {
        for (String t : new String[]{"pl_actividad", "pl_devoluciones_material", "pl_tareas_proyecto", "pl_borradores", "pl_proyectos"}) {
            jdbc.execute("DELETE FROM " + t);
        }
    }

    @AfterEach
    void cerrar() {
        almacen.usar(null, "", "");
        if (falso != null) {
            falso.close();
            falso = null;
        }
    }

    private void conDropbox() throws Exception {
        falso = new DropboxFalso();
        falso.crearCarpeta(BASE_DROPBOX + "/2026");
        almacen.usar(new DropboxCliente("clave", "secreto", "refresco", falso.url(), falso.url(), Duration.ofSeconds(5)), BASE_DROPBOX, "");
    }

    private ResultActions ana(MockHttpServletRequestBuilder b, String cuerpo) throws Exception {
        b.header("X-Aura-Usuario-Nombre", "Ana").header("X-Usuario-Id", 7).header("X-Usuario-Nivel", 3);
        if (cuerpo != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(b);
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String proyecto(String extra) throws Exception {
        return leer(ana(post(BASE + "/projects"), "{\"name\":\"Nave Bonaquia\",\"start_date\":\"2026-02-01\"" + (extra == null ? "" : "," + extra) + "}")
                .andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode proyectoDe(String id) throws Exception {
        return leer(mvc.perform(get(BASE + "/projects/" + id)).andExpect(status().isOk()));
    }

    private String actividad(String id) throws Exception {
        return leer(mvc.perform(get(BASE + "/projects/" + id + "/activity"))).toString();
    }

    @Test
    void sinTodosLosDatosNoSeGeneraYSeDiceCualesFaltanPeroLoEscritoSeConserva() throws Exception {
        String id = proyecto(null);
        ana(post(BASE + "/projects/" + id + "/contract-document"), "{\"party\":{\"empresa\":\"  ACME  \",\"cif\":\"B1\",\"inventado\":\"x\"}}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("Faltan datos para generar el contrato: "),
                        org.hamcrest.Matchers.containsString("Domicilio social"), org.hamcrest.Matchers.containsString("Importe total"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Empresa promotora")), org.hamcrest.Matchers.endsWith("."))));
        JsonNode p = proyectoDe(id);
        assertEquals("ACME", p.get("contract_party").get("empresa").asText());
        assertFalse(p.get("contract_party").has("inventado"));
        assertTrue(p.get("contract_generated_at").isNull(), "sigue pendiente de generar");
        mvc.perform(get(BASE + "/projects/" + id + "/contract-document")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void conTodosLosDatosSeGeneraElPdfYSoloElPdfSeGuardaEnDropbox() throws Exception {
        conDropbox();
        String id = proyecto(BASELINE + ",\"dropbox_name\":\"BONAQUIA, S.L.\"");
        JsonNode r = leer(ana(post(BASE + "/projects/" + id + "/contract-document"), "{\"party\":" + PARTES + "}").andExpect(status().isOk()));
        String ruta = BASE_DROPBOX + "/2026/260201_BONAQUIA, S.L/" + CARPETA_CONTRATO + "/CONTRATO INSTALACION FV_AURA RENOVABLES_BONAQUIA, S.L.pdf";
        assertEquals(ruta, r.get("saved").get(0).asText());
        assertEquals(1, r.get("saved").size(), "solo se guarda un archivo: el PDF");
        assertEquals("", r.get("warning").asText());
        assertEquals(ruta, r.get("project").get("contract_file").asText());
        assertFalse(r.get("project").get("contract_generated_at").isNull());
        assertEquals("%PDF-", new String(falso.archivo(ruta), 0, 5, StandardCharsets.ISO_8859_1));
        assertTrue(falso.archivos.keySet().stream().noneMatch(k -> k.endsWith(".docx")), "el Word no se entrega ni se guarda");
        assertTrue(actividad(id).contains("Generó el contrato"));
        assertTrue(actividad(id).contains("Guardado en Dropbox: "));
    }

    @Test
    void sinCarpetaDeDropboxSeDaPorGeneradoPeroSinGuardarNada() throws Exception {
        String id = proyecto(BASELINE);
        JsonNode r = leer(ana(post(BASE + "/projects/" + id + "/contract-document"), "{\"party\":" + PARTES + "}").andExpect(status().isOk()));
        assertEquals(0, r.get("saved").size());
        assertEquals("", r.get("project").get("contract_file").asText());
        assertFalse(r.get("project").get("contract_generated_at").isNull());
        assertTrue(actividad(id).contains("No se guardó en Dropbox: el proyecto no tiene carpeta"));
    }

    @Test
    void siDropboxFallaElContratoSeDaPorGeneradoYSeAvisa() throws Exception {
        conDropbox();
        String id = proyecto(BASELINE + ",\"dropbox_name\":\"Nave\"");
        falso.rechazarCredenciales = true;
        almacen.usar(new DropboxCliente("clave", "secreto", "refresco", falso.url(), falso.url(), Duration.ofSeconds(5)), BASE_DROPBOX, "");
        JsonNode r = leer(ana(post(BASE + "/projects/" + id + "/contract-document"), "{\"party\":" + PARTES + "}").andExpect(status().isOk()));
        assertTrue(r.get("warning").asText().startsWith("No se pudo guardar el contrato en Dropbox: "));
        assertFalse(r.get("warning").asText().contains("secreto"));
        assertEquals(0, r.get("saved").size());
        assertFalse(r.get("project").get("contract_generated_at").isNull());
    }

    @Test
    void elContratoSeDescargaEnPdfYNoEnWord() throws Exception {
        String id = proyecto(BASELINE + ",\"contract_party\":" + PARTES);
        var respuesta = mvc.perform(get(BASE + "/projects/" + id + "/contract-document")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn();
        assertEquals("%PDF-", new String(respuesta.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.ISO_8859_1));
        assertTrue(respuesta.getResponse().getHeader("Content-Disposition").startsWith("inline; filename*=UTF-8''CONTRATO%20INSTALACION%20FV_AURA%20RENOVABLES_BONAQUIA"));
        mvc.perform(get(BASE + "/projects/" + id + "/contract-document?format=docx")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El contrato solo se entrega en PDF"));
        assertTrue(proyectoDe(id).get("contract_generated_at").isNull(), "descargar no lo da por generado");
        mvc.perform(get(BASE + "/projects/nada/contract-document")).andExpect(status().isNotFound());
    }

    @Test
    void alCrearUnProyectoConElContratoGeneradoEnLaAltaSeArchivaEnSuCarpeta() throws Exception {
        conDropbox();
        String id = proyecto(BASELINE + ",\"contract_party\":" + PARTES + ",\"contract_generated\":true,\"dropbox_name\":\"Alta\"");
        JsonNode p = proyectoDe(id);
        assertFalse(p.get("contract_generated_at").isNull());
        assertTrue(p.get("contract_file").asText().endsWith("CONTRATO INSTALACION FV_AURA RENOVABLES_Alta.pdf"));
        assertNotNull(falso.archivo(p.get("contract_file").asText()));
    }

    @Test
    void siFaltanDatosAlCrearElProyectoSeCreaIgualConElContratoPendiente() throws Exception {
        String id = proyecto("\"contract_generated\":true");
        JsonNode p = proyectoDe(id);
        assertTrue(p.get("contract_generated_at").isNull());
        assertTrue(actividad(id).contains("No se pudo generar el contrato"));
        assertTrue(actividad(id).contains("Faltan datos para generar el contrato"));
    }

    @Test
    void elContratoSinGuardarSeGeneraDesdeLosDatosEnviadosYSoloEnPdf() throws Exception {
        String datos = "{\"empresa\":\"BONAQUIA, S.L.\",\"cif\":\"B1\",\"domicilio\":\"D\",\"representante\":\"R\",\"dni\":\"1\",\"cubierta_de\":\"nave\",\"instalacion\":\"I\","
                + "\"kw_nominal\":\"20\",\"kwp\":\"22,5\",\"importe_total\":\"38700\",\"paneles_uds\":\"40\",\"paneles_modelo\":\"X\",\"inversor_uds\":\"1\",\"inversor_modelo\":\"Y\","
                + "\"estructura\":\"Z\",\"anio\":\"2026\",\"payment_plan\":{\"mode\":\"standard\"}}";
        var r = ana(post(BASE + "/contract/generate"), datos).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn();
        assertEquals("%PDF-", new String(r.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.ISO_8859_1));
        assertTrue(r.getResponse().getHeader("Content-Disposition").contains("Contrato%20BONAQUIA"));
        ana(post(BASE + "/contract/generate?format=docx"), datos).andExpect(status().isUnprocessableEntity());
        ana(post(BASE + "/contract/generate"), "{\"empresa\":\"X\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Faltan datos para generar el contrato: ")));
        ana(post(BASE + "/contract/generate"), datos.replace("\"mode\":\"standard\"", "\"mode\":\"custom\",\"stages\":[{\"label\":\"A\",\"percent\":10}]"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("Los porcentajes de los pagos suman 10 %: deben sumar exactamente 100 %"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pl_proyectos", Integer.class), "no se guarda nada");
    }
}
