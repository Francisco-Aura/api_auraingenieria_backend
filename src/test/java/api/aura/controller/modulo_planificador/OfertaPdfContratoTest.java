package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.OfertaPdf;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Lectura del PDF de la oferta: sobre PDF de ejemplo (dibujados con reportlab) se obtiene lo mismo que con el código Python del Planificador
 * actual (campos, fuentes, confianza, avisos y lo que falta), incluida la estructura que marca la X.
 */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class OfertaPdfContratoTest {

    @Autowired
    private OfertaPdf oferta;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private MockMvc mvc;

    private byte[] pdf(String caso) throws IOException {
        try (var in = getClass().getResourceAsStream("/oferta-dorada/" + caso + ".pdf")) {
            assertNotNull(in, caso);
            return in.readAllBytes();
        }
    }

    private JsonNode esperado(String caso) throws IOException {
        try (var in = getClass().getResourceAsStream("/oferta-dorada/" + caso + ".json")) {
            return json.readTree(in);
        }
    }

    private JsonNode leido(String caso) throws IOException {
        List<String> paginas = oferta.leerPaginas(pdf(caso));
        return json.valueToTree(OfertaPdf.extraer(paginas, oferta.leerTrozos(pdf(caso), OfertaPdf.paginasDeEstructura(paginas))));
    }

    @Test
    void unPresupuestoCompletoSeLeeIgualQueConElPlanificadorActual() throws Exception {
        assertEquals(esperado("completo"), leido("completo"));
    }

    @Test
    void conBateriaYVariosImportesSeTomaElMayorYSeAvisa() throws Exception {
        JsonNode r = leido("baterias");
        assertEquals(esperado("baterias"), r);
        assertEquals("40000", r.get("fields").get("importe_total").get("value").asText());
        assertEquals("media", r.get("fields").get("importe_total").get("confidence").asText());
        assertEquals("BYD Battery Box 10 kWh", r.get("fields").get("bateria").get("value").asText());
    }

    @Test
    void unaFormaDePagoDistintaDeLaHabitualSeAvisa() throws Exception {
        JsonNode r = leido("pago_raro");
        assertEquals(esperado("pago_raro"), r);
        assertTrue(r.get("notes").toString().contains("Forma de pago distinta de la habitual (30/40/20/10): 50 %, 50 %"));
    }

    @Test
    void sinXMarcadaLaEstructuraQuedaParaRellenarAManoYSeAvisa() throws Exception {
        JsonNode r = leido("sin_x");
        assertEquals(esperado("sin_x"), r);
        assertTrue(r.get("missing").toString().contains("estructura"));
        assertTrue(r.get("notes").toString().contains("indícalo a mano"));
    }

    @Test
    void laXMarcadaSobreLastradaDaElTipoDeEstructuraConSuFuente() throws Exception {
        JsonNode e = leido("completo").get("fields").get("estructura");
        assertEquals("Lastrada sin perforación", e.get("value").asText());
        assertEquals("alta", e.get("confidence").asText());
        assertTrue(e.get("source").asText().startsWith("Estructura marcada con una X (pág. 4): "));
    }

    @Test
    void unPdfSinTextoEsUnEscaneoYNoTieneDatos() throws Exception {
        assertTrue(esperado("escaneo").isNull(), "el original tampoco encuentra nada");
        Map<String, Object> r = oferta.analizar(pdf("escaneo"), "oferta escaneada.pdf");
        assertEquals("scan", r.get("kind"));
        assertEquals("oferta escaneada.pdf", r.get("filename"));
        assertEquals(Map.of(), r.get("fields"));
    }

    @Test
    void laEmpresaYLaLocalidadSalenSinFormaJuridicaNiCodigoPostal() {
        assertEquals("Solar Vega", OfertaPdf.limpiarEmpresa("Solar Vega, S.L.U."));
        assertNull(OfertaPdf.limpiarEmpresa("________"));
        assertEquals("Lorca", OfertaPdf.localidadDeDireccion("Avda. Juan Carlos I, nº 120, 30800 Lorca (Murcia)"));
        assertNull(OfertaPdf.localidadDeDireccion("Dirección ____ ____"));
        assertEquals("Mula", OfertaPdf.localidadDeDireccion("Calle Mayor 3, Mula"));
    }

    @Test
    void elNombreDelArchivoSeLimpia() {
        assertEquals("oferta (1).pdf", OfertaPdf.nombreSeguro("C:\\docs\\oferta (1).pdf"));
        assertEquals("a_b.pdf", OfertaPdf.nombreSeguro("a*b.pdf"));
        assertEquals("archivo", OfertaPdf.nombreSeguro(" . "));
    }

    @Test
    void elEndpointLeeElPdfYRechazaLoQueNoLoEs() throws Exception {
        var r = mvc.perform(post("/api/planificador/budget/extract?name=oferta.pdf").contentType(MediaType.APPLICATION_OCTET_STREAM).content(pdf("completo")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("budget")).andExpect(jsonPath("$.filename").value("oferta.pdf")).andReturn();
        JsonNode cuerpo = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("51.6", cuerpo.get("fields").get("potencia_kwp").get("value").asText());
        assertEquals("Solar Vega", cuerpo.get("project").get("company").get("value").asText());
        assertEquals("Lorca", cuerpo.get("project").get("locality").get("value").asText());
        assertEquals("Instalación Solar Vega Lorca", cuerpo.get("project").get("name").get("value").asText());
        assertEquals("12", cuerpo.get("extras").get("garantia_producto").asText());
        mvc.perform(post("/api/planificador/budget/extract").contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[0])).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El archivo está vacío"));
        mvc.perform(post("/api/planificador/budget/extract").contentType(MediaType.APPLICATION_OCTET_STREAM).content("no es un pdf".getBytes()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("Solo se puede subir el presupuesto en PDF."));
        mvc.perform(post("/api/planificador/budget/extract").contentType(MediaType.APPLICATION_OCTET_STREAM).content("%PDF-1.4 roto".getBytes()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("No se ha podido leer el PDF: parece dañado."));
    }
}
