package api.aura.controller.modulo_registro_horario;

import api.aura.config.RegistroHorarioExceptionHandler;
import api.aura.service.modulo_registro_horario.EmpresasConfig;
import api.aura.service.modulo_registro_horario.GenerarService;
import api.aura.service.modulo_registro_horario.documento.MotorWord;
import api.aura.service.modulo_registro_horario.documento.ReglasJornada;
import api.aura.service.modulo_registro_horario.firma.FirmaService;
import api.aura.service.modulo_registro_horario.firma.TrabajosPesados;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * POST /api/registro-horario/generar: las mismas pruebas que test_api.py del Registro Horario anterior (PDF y Word, nombre
 * del archivo, cabeceras, rechazo de datos malos, nada en disco) con los casos inventados cuyo resultado dio el original.
 */
class GenerarControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path carpeta;
    MockMvc mvc;
    JsonNode oro;

    @BeforeEach
    void preparar() throws Exception {
        try (var in = getClass().getResourceAsStream("/registro-horario-oro/word/casos.json")) {
            oro = MAPPER.readTree(in);
        }
        ObjectNode config = MAPPER.createObjectNode().put("localidad", "LORCA");
        var lista = config.putArray("empresas");
        oro.get("empresas").elements().forEachRemaining(lista::add);
        Path archivo = carpeta.resolve("empresas.json");
        Files.writeString(archivo, MAPPER.writeValueAsString(config));
        mvc = montar(archivo, null);
    }

    static MockMvc montar(Path empresas, MotorWord motorAlternativo) {
        var config = new EmpresasConfig(MAPPER, empresas.toString());
        var trabajos = new TrabajosPesados();
        var firmas = new FirmaService(trabajos);
        var generador = new GenerarService(config, firmas, motorAlternativo != null ? motorAlternativo : new MotorWord(firmas), trabajos);
        return MockMvcBuilders.standaloneSetup(new RegistroHorarioController(config, firmas, generador))
                .setControllerAdvice(new RegistroHorarioExceptionHandler()).build();
    }

    private ObjectNode peticion(String caso) {
        for (JsonNode c : oro.get("casos")) {
            if (c.get("nombre").asText().equals(caso)) {
                return (ObjectNode) c.get("peticion").deepCopy();
            }
        }
        throw new IllegalArgumentException(caso);
    }

    private MockHttpServletResponse enviar(JsonNode cuerpo) throws Exception {
        return mvc.perform(post("/api/registro-horario/generar").contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsBytes(cuerpo))).andReturn().getResponse();
    }

    private static String detalle(MockHttpServletResponse r) throws IOException {
        return MAPPER.readTree(r.getContentAsByteArray()).get("detail").asText();
    }

    // ---------------------------------------------------------------- lo que sale bien

    @Test
    void generaElPdfConLasCabecerasYElNombreDelAnterior() throws Exception {
        ObjectNode p = peticion("completo");
        p.put("formato", "pdf");
        MockHttpServletResponse r = enviar(p);
        assertEquals(200, r.getStatus(), r.getContentAsString());
        assertEquals("application/pdf", r.getContentType());
        assertTrue(r.getHeader("Content-Disposition").contains("filename*=UTF-8''Reg_horas_Ana_P%C3%A9rez_Garc%C3%ADa_2026-10.pdf"));
        assertTrue(r.getHeader("Content-Disposition").startsWith("attachment; filename=\"documento.pdf\";"));
        assertEquals("no-store", r.getHeader("Cache-Control"));
        JsonNode caso = oro.get("casos").get(0);
        assertEquals(caso.get("dias").asText(), r.getHeader("X-Dias-Trabajados"));
        assertEquals(ReglasJornada.fmtHoras(caso.get("min_manana").asInt() + caso.get("min_tarde").asInt()), r.getHeader("X-Horas"));
        try (PDDocument pdf = Loader.loadPDF(r.getContentAsByteArray())) {
            assertEquals(1, pdf.getNumberOfPages());
            String texto = new PDFTextStripper().getText(pdf);
            for (String esperado : new String[]{"EMPRESA UNO, S.L.", "B00000001", "30/0000000/01", "10/2026", "Ana Pérez García",
                    "00000000T", "FESTIVO", "VACACIONES", "PERMISO", "ENFERMEDAD", "OCTUBRE"}) {
                assertTrue(texto.contains(esperado), esperado);
            }
        }
    }

    @Test
    void generaElWordYSeAbre() throws Exception {
        ObjectNode p = peticion("completo");
        p.put("formato", "docx");
        MockHttpServletResponse r = enviar(p);
        assertEquals(200, r.getStatus());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", r.getContentType());
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(r.getContentAsByteArray()))) {
            assertTrue(doc.getTables().size() >= 3);
        }
    }

    @Test
    void cadaCasoDeOroDaLosMismosDiasYHoras() throws Exception {
        int n = 0;
        for (JsonNode c : oro.get("casos")) {
            if (c.has("error")) {
                continue;
            }
            for (String formato : new String[]{"pdf", "docx"}) {
                ObjectNode p = (ObjectNode) c.get("peticion").deepCopy();
                p.put("formato", formato);
                MockHttpServletResponse r = enviar(p);
                String nombre = c.get("nombre").asText() + " " + formato;
                assertEquals(200, r.getStatus(), nombre + ": " + r.getContentAsString());
                assertEquals(c.get("dias").asText(), r.getHeader("X-Dias-Trabajados"), nombre);
                assertEquals(ReglasJornada.fmtHoras(c.get("min_manana").asInt() + c.get("min_tarde").asInt()), r.getHeader("X-Horas"), nombre);
            }
            n++;
        }
        assertEquals(9, n);
    }

    @Test
    void elFormatoPorDefectoEsPdfYLaFechaDeFirmaPorDefectoElUltimoDia() throws Exception {
        ObjectNode p = peticion("completo");
        p.remove("formato");
        p.remove("fecha_firma");
        MockHttpServletResponse r = enviar(p);
        assertEquals(200, r.getStatus());
        assertEquals("application/pdf", r.getContentType());
    }

    @Test
    void lasHorasEscritasSinCeroInicialSeAceptan() throws Exception {
        ObjectNode p = peticion("completo");
        ((ObjectNode) p.get("horario_base").get("manana")).put("ini", "8:00");
        MockHttpServletResponse r = enviar(p);
        assertEquals(200, r.getStatus(), r.getContentAsString());
    }

    // ---------------------------------------------------------------- lo que se rechaza

    @Test
    void rechazaDatosMalosConElMensajeDelAnterior() throws Exception {
        record Caso(String nombre, java.util.function.Consumer<ObjectNode> cambio, String contiene) {
        }
        java.util.List<Caso> casos = java.util.List.of(
                new Caso("empresa que no existe", p -> p.put("empresa", "NO EXISTE"), "La empresa elegida no está en la lista. Vuelve a elegirla."),
                new Caso("horario al revés", p -> ((ObjectNode) p.get("horario_base").get("manana")).put("ini", "15:00").put("fin", "08:00"),
                        "Horas del 2026-10-01: la salida 08:00 no es posterior a la entrada 15:00."),
                new Caso("día de otro mes", p -> p.set("estado_dias", MAPPER.createObjectNode().put("2026-11-02", "MANANA")),
                        "El día 2026-11-02 no pertenece a octubre de 2026."),
                new Caso("fecha imposible", p -> p.set("estado_dias", MAPPER.createObjectNode().put("2026-10-32", "MANANA")), "Fecha no válida: 2026-10-32."),
                new Caso("más de 31 días", p -> ((ObjectNode) p.get("estado_dias")).put("2026-10-99", "MANANA"), "(estado_dias)"),
                new Caso("día de otro mes en las horas propias", p -> p.set("horas_dia", MAPPER.createObjectNode().set("2026-09-01",
                        MAPPER.createObjectNode().set("manana", MAPPER.createObjectNode().put("ini", "08:00").put("fin", "15:00")))),
                        "El día 2026-09-01 no pertenece a octubre de 2026."),
                new Caso("ningún día trabajado", p -> p.set("estado_dias", libres(p)), "No hay ningún día trabajado en el periodo"),
                new Caso("firma que no es base64", p -> p.put("firma_png_base64", "no es base64!!"), "La firma no es una imagen válida."),
                new Caso("firma que no es una imagen", p -> p.put("firma_png_base64",
                        Base64.getEncoder().encodeToString("no es una imagen".getBytes())), "No se pudo leer la imagen. Usa un PNG o un JPG."),
                new Caso("firma que pesa más de 2 MB", p -> p.put("firma_png_base64", "A".repeat(2_900_000)), "La imagen de la firma pesa demasiado."),
                new Caso("firma absurdamente larga", p -> p.put("firma_png_base64", "A".repeat(3_000_000)), "(firma_png_base64)"),
                new Caso("NIF vacío", p -> p.put("nif", "  "), "(nif)"),
                new Caso("mes 13", p -> p.put("mes", 13), "(mes)"),
                new Caso("año fuera de rango", p -> p.put("anio", 1999), "(anio)"),
                new Caso("estado desconocido", p -> ((ObjectNode) p.get("estado_dias")).put("2026-10-01", "OTRO"), "(estado_dias)"),
                new Caso("hora mal escrita", p -> ((ObjectNode) p.get("horario_base").get("tarde")).put("ini", "25:00"), "(horario_base › tarde › ini)"),
                new Caso("formato desconocido", p -> p.put("formato", "odt"), "(formato)"),
                new Caso("fecha de firma mal", p -> p.put("fecha_firma", "mañana"), "(fecha_firma)"),
                new Caso("nombre con carácter de control", p -> p.put("trabajador", "Ana\u0001"), "(trabajador)"),
                new Caso("nombre demasiado largo", p -> p.put("trabajador", "x".repeat(121)), "(trabajador)"));
        for (Caso c : casos) {
            ObjectNode p = peticion("completo");
            c.cambio().accept(p);
            MockHttpServletResponse r = enviar(p);
            assertEquals(422, r.getStatus(), c.nombre());
            assertTrue(detalle(r).contains(c.contiene()), c.nombre() + ": " + detalle(r));
        }
    }

    private static ObjectNode libres(ObjectNode p) {
        ObjectNode libres = MAPPER.createObjectNode();
        p.get("estado_dias").fieldNames().forEachRemaining(k -> libres.put(k, "LIBRE"));
        return libres;
    }

    @Test
    void unCuerpoQueNoEsJsonOFaltaDaUn422() throws Exception {
        MockHttpServletResponse r = mvc.perform(post("/api/registro-horario/generar").contentType(MediaType.APPLICATION_JSON)
                .content("{esto no es json")).andReturn().getResponse();
        assertEquals(422, r.getStatus());
        r = enviar(MAPPER.createObjectNode());
        assertEquals(422, r.getStatus());
        assertTrue(detalle(r).startsWith("Hay un dato no válido"));
    }

    // ---------------------------------------------------------------- privacidad y fallos

    @Test
    void noGuardaNadaEnDiscoNiEnLaCarpetaTemporal() throws Exception {
        Path temporal = Path.of(System.getProperty("java.io.tmpdir"));
        long antesTemporal, antesTrabajo;
        try (Stream<Path> t = Files.list(temporal); Stream<Path> w = Files.list(Path.of("").toAbsolutePath())) {
            antesTemporal = t.count();
            antesTrabajo = w.count();
        }
        assertEquals(200, enviar(peticion("completo")).getStatus());
        try (Stream<Path> t = Files.list(temporal); Stream<Path> w = Files.list(Path.of("").toAbsolutePath())) {
            assertEquals(antesTrabajo, w.count(), "carpeta de trabajo");
            assertTrue(t.count() <= antesTemporal + 1, "carpeta temporal"); // otras pruebas pueden crear su carpeta
        }
    }

    @Test
    void unFalloInternoDa500SinDatosPersonalesEnLaRespuesta() throws Exception {
        MotorWord roto = org.mockito.Mockito.mock(MotorWord.class);
        org.mockito.Mockito.when(roto.construir(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("fallo con el NIF 00000000T"));
        mvc = montar(carpeta.resolve("empresas.json"), roto);
        MockHttpServletResponse r = enviar(peticion("completo"));
        assertEquals(500, r.getStatus());
        assertEquals("No se pudo generar el documento.", detalle(r));
        assertFalse(r.getContentAsString().contains("00000000T"));
    }

    @Test
    void elNombreDelArchivoSeLimpiaComoEnElAnterior() {
        String[][] casos = {
                {"Ana Pérez García", "Reg_horas_Ana_Pérez_García_2026-03.pdf", "Reg_horas_Ana_P%C3%A9rez_Garc%C3%ADa_2026-03.pdf"},
                {"María-José Núñez <&> Ibáñez", "Reg_horas_María-José_Núñez_&_Ibáñez_2026-03.pdf", "Reg_horas_Mar%C3%ADa-Jos%C3%A9_N%C3%BA%C3%B1ez_%26_Ib%C3%A1%C3%B1ez_2026-03.pdf"},
                {"  Dr. J.R.R.   Tolkien; Jr.  ", "Reg_horas_Dr_J_R_R_Tolkien_Jr_2026-03.pdf", "Reg_horas_Dr_J_R_R_Tolkien_Jr_2026-03.pdf"},
                {"a/b\\c:d*e?f\"g|h", "Reg_horas_abcdefgh_2026-03.pdf", "Reg_horas_abcdefgh_2026-03.pdf"},
                {"x".repeat(100) + " Zeta", "Reg_horas_" + "x".repeat(80) + "_2026-03.pdf", "Reg_horas_" + "x".repeat(80) + "_2026-03.pdf"},
                {"._.._", "Reg_horas__2026-03.pdf", "Reg_horas__2026-03.pdf"},
                {"Ünï çödé ~ñ+ 100%", "Reg_horas_Ünï_çödé_~ñ+_100%_2026-03.pdf", "Reg_horas_%C3%9Cn%C3%AF_%C3%A7%C3%B6d%C3%A9_~%C3%B1%2B_100%25_2026-03.pdf"},
                {"Ana\tPérez López", "Reg_horas_AnaPérez_López_2026-03.pdf", "Reg_horas_AnaP%C3%A9rez_L%C3%B3pez_2026-03.pdf"}};
        for (String[] c : casos) {
            String nombre = GenerarService.nombreArchivo(c[0], 2026, 3, "pdf");
            assertEquals(c[1], nombre, c[0]);
            assertEquals(c[2], GenerarService.codificarNombre(nombre), c[0]);
        }
    }
}
