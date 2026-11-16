package api.aura.controller.modulo_planificador;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Estudios fotovoltaicos: registro, SIPS, curva, cálculo e informe por HTTP. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class EstudiosContratoTest {

    private static final String BASE = "/api/planificador/studies";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("DELETE FROM pl_estudios");
    }

    private JsonNode crear(String cliente) throws Exception {
        String r = mvc.perform(post(BASE).contentType("application/json").content("{\"cliente\":\"" + cliente + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(r);
    }

    private static byte[] sips(int lecturas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet h = wb.createSheet("sips");
            h.createRow(0).createCell(0).setCellValue("Datos suministro");
            Row cab = h.createRow(1);
            String[] nombres = {"", "Distribuidora", "Tarifa", "Localidad", "Provincia", "Nombre Completo Titular", "Potencia Contratada P1", "Potencia Contratada P2",
                    "Potencia Contratada P3", "Potencia Contratada P4", "Potencia Contratada P5", "Potencia Contratada P6", "kWh Anual"};
            for (int i = 0; i < nombres.length; i++) cab.createCell(i).setCellValue(nombres[i]);
            Row val = h.createRow(2);
            val.createCell(0).setCellValue("ES0021000000000000XX");
            val.createCell(1).setCellValue("i-DE");
            val.createCell(2).setCellValue("6.1TD");
            val.createCell(3).setCellValue("Lorca");
            val.createCell(4).setCellValue("Murcia");
            val.createCell(5).setCellValue("TITULAR PRIVADO");
            for (int p = 0; p < 6; p++) val.createCell(6 + p).setCellValue(150);
            val.createCell(12).setCellValue(96000);
            h.createRow(3).createCell(0).setCellValue("Datos lecturas");
            Row cl = h.createRow(4);
            cl.createCell(1).setCellValue("Fecha Lectura");
            cl.createCell(2).setCellValue("Fecha Lectura Anterior");
            for (int p = 0; p < 6; p++) cl.createCell(4 + p).setCellValue("Consumo Activa P" + (p + 1));
            LocalDate fin = LocalDate.of(2026, 9, 30);
            for (int m = 0; m < lecturas; m++) {
                LocalDate hasta = fin.minusMonths(m).withDayOfMonth(fin.minusMonths(m).lengthOfMonth());
                LocalDate desde = hasta.minusMonths(1).withDayOfMonth(hasta.minusMonths(1).lengthOfMonth());
                Row r = h.createRow(5 + m);
                r.createCell(0).setCellValue("ES0021000000000000XX");
                r.createCell(1).setCellValue(hasta.toEpochDay() + 25569);
                r.createCell(2).setCellValue(desde.toEpochDay() + 25569);
                for (int p = 0; p < 6; p++) r.createCell(4 + p).setCellValue(1500);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private JsonNode subir(String id, String ruta, String nombre, byte[] bytes) throws Exception {
        String r = mvc.perform(multipart(BASE + "/" + id + "/" + ruta).file(new MockMultipartFile("file", nombre, MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(r);
    }

    private JsonNode cambiar(String id, String cuerpo) throws Exception {
        return json.readTree(mvc.perform(put(BASE + "/" + id).contentType("application/json").content(cuerpo)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void creaUnEstudioConEstadoPorDefectoYSinCalcular() throws Exception {
        JsonNode e = crear("Curtidos Prueba");
        assertEquals("en_estudio", e.get("estado").asText());
        assertFalse(e.get("calculado").asBoolean());
        assertEquals(7, e.get("estados").size());
        assertEquals(0.10, e.get("datos").get("economia").get("precioEnergia").asDouble(), 1e-9);
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].cliente").value("Curtidos Prueba"));
        mvc.perform(post(BASE).contentType("application/json").content("{\"cliente\":\"  \"}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void elSipsRellenaElSuministroYEstimaLaCurvaSinGuardarAlTitular() throws Exception {
        JsonNode e = crear("Cliente SIPS");
        JsonNode r = subir(e.get("id").asText(), "sips", "sips.xlsx", sips(12));
        assertEquals("6.1TD", r.get("datos").get("tarifa").asText());
        assertEquals("ES0021000000000000XX", r.get("cups").asText());
        assertEquals("Lorca", r.get("datos").get("localidad").asText());
        assertTrue(r.get("curva").get("estimada").asBoolean());
        assertTrue(r.get("calculado").asBoolean());
        assertTrue(r.get("datos").get("instalacion").get("kwp").asDouble() > 0, "propone una potencia");
        assertFalse(r.toString().contains("TITULAR PRIVADO"), "el titular no se guarda");
        assertEquals(6, r.get("datos").get("sips").get("potencias").size());
        assertEquals(6 * 1500 * 12, r.get("resultado").get("consumo").get("consumoAnual").asDouble(), 6 * 1500 * 12 * 0.01);
    }

    @Test
    void conPocasLecturasElSipsSeGuardaYAvisaDeQueFaltaLaCurva() throws Exception {
        JsonNode e = crear("Cliente corto");
        JsonNode r = subir(e.get("id").asText(), "sips", "sips.xlsx", sips(3));
        assertTrue(r.get("aviso").asText().contains("faltan meses"));
        assertFalse(r.get("calculado").asBoolean());
        mvc.perform(get(BASE + "/" + e.get("id").asText() + "/report.html")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cambiarLaInstalacionRecalculaYUnaCurvaRealSustituyeALaEstimada() throws Exception {
        JsonNode e = crear("Cliente real");
        String id = e.get("id").asText();
        subir(id, "sips", "sips.xlsx", sips(12));
        JsonNode a = cambiar(id, "{\"datos\":{\"instalacion\":{\"kwp\":100,\"bateriaKwh\":0,\"orientacion\":\"sur\"}}}");
        JsonNode b = cambiar(id, "{\"datos\":{\"instalacion\":{\"kwp\":100,\"bateriaKwh\":120,\"orientacion\":\"sur\"}}}");
        assertTrue(b.get("resultado").get("simulacion").get("cobertura").asDouble() > a.get("resultado").get("simulacion").get("cobertura").asDouble());
        assertEquals(5, b.get("resultado").get("escenarios").size());
        StringBuilder sb = new StringBuilder("Fecha;Hora;Consumo_kWh\n");
        for (LocalDate d = LocalDate.of(2025, 10, 1); d.isBefore(LocalDate.of(2026, 10, 1)); d = d.plusDays(1)) {
            for (int h = 1; h <= 24; h++) sb.append(String.format("%02d/%02d/%d;%d;%s\n", d.getDayOfMonth(), d.getMonthValue(), d.getYear(), h, h >= 8 && h <= 17 ? "20" : "3"));
        }
        JsonNode c = subir(id, "curve", "curva.csv", sb.toString().getBytes(StandardCharsets.UTF_8));
        assertFalse(c.get("curva").get("estimada").asBoolean());
        assertEquals("curva.csv", c.get("curva").get("origen").asText());
        assertEquals(365 * (10 * 20 + 14 * 3), c.get("resultado").get("consumo").get("consumoAnual").asDouble(), 100);
        // Un horario nuevo ya no toca una curva real
        JsonNode d = cambiar(id, "{\"datos\":{\"horario\":{\"ini\":6,\"fin\":20}}}");
        assertFalse(d.get("curva").get("estimada").asBoolean());
    }

    @Test
    void validaLosDatosYGuardaEstadoYObservaciones() throws Exception {
        JsonNode e = crear("Cliente estado");
        String id = e.get("id").asText();
        JsonNode r = cambiar(id, "{\"estado\":\"pte_doc\",\"observaciones\":\"Falta la factura de julio\"}");
        assertEquals("Pendiente de documentación del cliente", r.get("estadoNombre").asText());
        assertEquals("Falta la factura de julio", r.get("observaciones").asText());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"estado\":\"inventado\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"datos\":{\"instalacion\":{\"kwp\":-5}}}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"datos\":{\"economia\":{\"precioEnergia\":\"mucho\"}}}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"datos\":{\"horario\":{\"ini\":18,\"fin\":9}}}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(BASE + "/no-existe")).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void elInformeHtmlTieneSietePaginasYLasCifrasDelEstudio() throws Exception {
        JsonNode e = crear("Aceitunas <Prueba> & Cía");
        String id = e.get("id").asText();
        subir(id, "sips", "sips.xlsx", sips(12));
        cambiar(id, "{\"datos\":{\"instalacion\":{\"kwp\":120,\"bateriaKwh\":100,\"orientacion\":\"sur\"}}}");
        var res = mvc.perform(get(BASE + "/" + id + "/report.html")).andExpect(status().isOk()).andReturn().getResponse();
        String html = res.getContentAsString(StandardCharsets.UTF_8);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "estudio-pruebas"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target", "estudio-pruebas", "estudio.html"), html);
        assertEquals(7, html.split("<section class=\"pag\">", -1).length - 1);
        assertTrue(html.contains("120 kWp y 100 kWh de batería cubren el"));
        assertTrue(html.contains("Aceitunas &lt;Prueba&gt; &amp; Cía"), "el nombre del cliente va escapado");
        assertFalse(html.contains("<Prueba>"));
        assertTrue(html.contains("Curva estimada"));
        assertTrue(html.contains("Escenarios de potencia") && html.contains("<svg"));
        assertTrue(html.contains("data:image/png;base64,"), "lleva el logo de AURA");
        assertFalse(html.replaceAll("data:image/png;base64,[A-Za-z0-9+/=]+", "").contains("NaN"), "sin valores NaN");
    }

    @Test
    void elInformeEnPdfTieneSietePaginasOAvisaDeQueNoHayNavegador() throws Exception {
        JsonNode e = crear("Cliente PDF");
        String id = e.get("id").asText();
        subir(id, "sips", "sips.xlsx", sips(12));
        var res = mvc.perform(get(BASE + "/" + id + "/report.pdf")).andReturn().getResponse();
        if (res.getStatus() == 501) return; // sin Chrome en esta máquina
        assertEquals(200, res.getStatus(), res.getContentAsString());
        try (org.apache.pdfbox.pdmodel.PDDocument d = org.apache.pdfbox.Loader.loadPDF(res.getContentAsByteArray())) {
            assertEquals(7, d.getNumberOfPages());
            String texto = new org.apache.pdfbox.text.PDFTextStripper().getText(d);
            assertTrue(texto.contains("La inversión") && texto.contains("Escenarios de potencia"));
        }
    }
}
