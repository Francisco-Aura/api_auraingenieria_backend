package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.PresupuestoPdf;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** El PDF del presupuesto: mismo texto, página a página, que el del Planificador actual (ejemplos guardados generados con el código Python) y sus reglas. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class PresupuestoPdfContratoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 5);

    @Autowired
    private PresupuestoPdf pdf;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    private String recurso(String nombre) throws IOException {
        try (var in = getClass().getResourceAsStream("/presupuesto-dorado/" + nombre)) {
            assertNotNull(in, nombre);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, Object> muestra() throws IOException {
        return json.readValue(recurso("muestra.json"), new TypeReference<>() {
        });
    }

    private static List<String> paginas(byte[] bytes) throws IOException {
        PdfReader lector = new PdfReader(bytes);
        PdfTextExtractor extractor = new PdfTextExtractor(lector);
        List<String> salida = new ArrayList<>();
        for (int i = 1; i <= lector.getNumberOfPages(); i++) {
            salida.add(extractor.getTextFromPage(i).replaceAll("\\s+", ""));
        }
        return salida;
    }

    /**
     * Los caracteres del texto sin pie de página y ordenados: dos extractores de PDF leen en orden distinto, pero si falta, sobra o cambia
     * un solo carácter (un importe, una etiqueta), la huella ya no coincide.
     */
    private static String huella(String t) {
        char[] c = t.replaceAll("\\s+", "").replaceAll("Presupuesto.05/11/2026", "").replaceAll("Página\\d+", "")
                .replace("ConceptoCant.Precioud.Total", "").replace("EquipoDescripciónCantidad", "").toCharArray();
        java.util.Arrays.sort(c);
        return new String(c);
    }

    // --------------------------------------------------------------------------- texto = original

    @Test
    void elPdfInternoTieneElMismoTextoQueElDelPlanificadorActual() throws Exception {
        byte[] generado = pdf.generar(muestra(), "interno", HOY);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "presupuesto-pruebas"));
        java.nio.file.Files.write(java.nio.file.Path.of("target", "presupuesto-pruebas", "interno.pdf"), generado);
        List<String> paginas = paginas(generado);
        // Se compara todo el texto junto: reportlab y OpenPDF pueden repartir distinto las filas entre páginas
        assertEquals(huella(recurso("interno.txt")), huella(String.join("", paginas)));
    }

    @Test
    void elPdfDeClienteTieneElMismoTextoQueElDelPlanificadorActual() throws Exception {
        List<String> paginas = paginas(pdf.generar(muestra(), "cliente", HOY));
        assertEquals(huella(recurso("cliente.txt")), huella(String.join("", paginas)));
    }

    @Test
    void sinEquiposElPdfDeClienteLoDice() throws Exception {
        Map<String, Object> vacio = new java.util.HashMap<>(muestra());
        vacio.put("lines", List.of());
        vacio.put("strings", List.of());
        String texto = String.join("", paginas(pdf.generar(vacio, "cliente", HOY)));
        assertEquals(huella(recurso("cliente-vacio.txt")), huella(texto));
        assertTrue(texto.contains("Sinequiposañadidos."));
    }

    @Test
    void elPdfInternoSeParteEnVariasPaginasConElPieEnTodasYElDeClienteCabeEnUna() throws Exception {
        byte[] interno = pdf.generar(muestra(), "interno", HOY);
        List<String> p = paginas(interno);
        assertTrue(p.size() >= 2);
        for (int i = 0; i < p.size(); i++) {
            assertTrue(p.get(i).contains("Presupuesto·05/11/2026"), "pie de la página " + (i + 1));
            assertTrue(p.get(i).contains("Página" + (i + 1)));
        }
        assertEquals(1, paginas(pdf.generar(muestra(), "cliente", HOY)).size());
    }

    @Test
    void elClienteNoVeCostesNiBeneficioNiPartidasAdministrativasNiManoDeObra() throws Exception {
        String texto = String.join("", paginas(pdf.generar(muestra(), "cliente", HOY)));
        assertFalse(texto.contains("Beneficio") || texto.contains("Costetotal") || texto.contains("Gastosadministrativos") || texto.contains("Montaje") || texto.contains("Transporte"));
        assertTrue(texto.contains("Preciodelainstalación") && texto.contains("PreciosinIVA."));
        String interno = String.join("", paginas(pdf.generar(muestra(), "interno", HOY)));
        assertTrue(interno.contains("Beneficio") && interno.contains("Costetotal") && interno.contains("Documentointerno"));
        assertFalse(interno.contains("Nosaleeninterno"), "una partida que no existe no se dibuja");
    }

    // ------------------------------------------------------------------------------------ reglas

    @Test
    void losNumerosMalosCuentanComoCeroYLosImportesSeEscribenEnEspanol() {
        assertEquals(0.0, PresupuestoPdf.f("12,5"), "una coma no es un número para el original");
        assertEquals(12.5, PresupuestoPdf.f("12.5"));
        assertEquals(0.0, PresupuestoPdf.f(null));
        assertEquals(0.0, PresupuestoPdf.f("x"));
        assertEquals(0.0, PresupuestoPdf.f(Double.POSITIVE_INFINITY));
    }

    @Test
    void elNombreDelArchivoLlevaElClienteElTipoYLaFecha() {
        assertEquals("Presupuesto_cliente_BONAQUIA_SL_Camping_261105", PresupuestoPdf.nombreBase("BONAQUIA, S.L. (Camping)", "cliente", HOY));
        assertEquals("Presupuesto_interno_sin_cliente_261105", PresupuestoPdf.nombreBase("  ", "interno", HOY));
        assertEquals("Presupuesto_interno_Muñoz_Peña_261105", PresupuestoPdf.nombreBase("Muñoz Peña", "interno", HOY));
    }

    // ------------------------------------------------------------------------------- endpoint

    @Test
    void elEndpointDevuelveElPdfEnLosDosModosYRechazaOtroModo() throws Exception {
        String cuerpo = recurso("muestra.json");
        var r = mvc.perform(post("/api/planificador/budget/pdf?mode=cliente").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn();
        assertEquals("%PDF-", new String(r.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.ISO_8859_1));
        assertTrue(r.getResponse().getHeader("Content-Disposition").startsWith("inline; filename*=UTF-8''Presupuesto_cliente_BONAQUIA_SL_Camping_"));
        mvc.perform(post("/api/planificador/budget/pdf").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Presupuesto_interno_")));
        mvc.perform(post("/api/planificador/budget/pdf?mode=otro").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El PDF puede ser «interno» o «cliente»."));
        mvc.perform(post("/api/planificador/budget/pdf").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }
}
