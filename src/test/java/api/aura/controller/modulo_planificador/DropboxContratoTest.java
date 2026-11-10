package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.dropbox.DropboxAlmacen;
import api.aura.service.modulo_planificador.dropbox.DropboxCliente;
import api.aura.service.modulo_planificador.dropbox.DropboxError;
import api.aura.service.modulo_planificador.dropbox.DropboxEstructura;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pruebas de contrato de Dropbox: carpetas de proyectos, archivos de tareas y fichas técnicas, contra un Dropbox de mentira en memoria. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class DropboxContratoTest {

    private static final String BASE = "/api/planificador";
    private static final String BASE_DROPBOX = "/AURA INGENIERIA/02_TRABAJOS/PRUEBAS";
    private static final String FICHAS = "/AURA INGENIERIA/FICHAS TECNICAS";

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
        for (String t : new String[]{"pl_actividad", "pl_devoluciones_material", "pl_tareas_proyecto", "pl_borradores", "pl_proyectos", "pl_historial_precios", "pl_equipos", "pl_marcas"}) {
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

    /** Enciende Dropbox contra el servidor de mentira, con la carpeta base y la de fichas ya creadas. */
    private void conDropbox() throws Exception {
        falso = new DropboxFalso();
        falso.crearCarpeta(BASE_DROPBOX);
        falso.crearCarpeta(BASE_DROPBOX + "/2026");
        falso.crearCarpeta(FICHAS);
        almacen.usar(new DropboxCliente("clave", "secreto", "refresco", falso.url(), falso.url(), Duration.ofSeconds(5)), BASE_DROPBOX, FICHAS);
    }

    private ResultActions ana(MockHttpServletRequestBuilder b, String cuerpo) throws Exception {
        b.header("X-Aura-Usuario-Nombre", "Ana").header("X-Usuario-Id", 7).header("X-Usuario-Nivel", 3);
        if (cuerpo != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(b);
    }

    private ResultActions subir(MockHttpServletRequestBuilder b, byte[] contenido) throws Exception {
        b.header("X-Aura-Usuario-Nombre", "Ana").contentType(MediaType.APPLICATION_OCTET_STREAM).content(contenido);
        return mvc.perform(b);
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String proyecto(String extra) throws Exception {
        return leer(ana(post(BASE + "/projects"), "{\"name\":\"Nave\",\"start_date\":\"2026-02-01\",\"template_id\":\"fotovoltaica\",\"exclude_steps\":[]" + (extra == null ? "" : "," + extra) + "}")
                .andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode proyectoDe(String id) throws Exception {
        return leer(mvc.perform(get(BASE + "/projects/" + id)).andExpect(status().isOk()));
    }

    private JsonNode tareas(String id) throws Exception {
        return leer(mvc.perform(get(BASE + "/projects/" + id + "/tasks")).andExpect(status().isOk()));
    }

    private String tareaConCarpeta(String proyectoId, boolean conCarpeta) throws Exception {
        for (JsonNode t : tareas(proyectoId)) {
            if (t.get("dropbox_folder").asText().isEmpty() != conCarpeta) {
                return t.get("id").asText();
            }
        }
        throw new AssertionError("no hay tarea " + (conCarpeta ? "con" : "sin") + " carpeta");
    }

    // ---------------------------------------------------------------------------- nombres y rutas

    @Test
    void losNombresDeCarpetasYArchivosSeLimpianComoEnElPlanificadorActual() {
        assertEquals("BONAQUIA, S.L. (CAMPING DEL AGUA)", DropboxEstructura.limpiarComponente("  BONAQUIA,  S.L. (CAMPING DEL AGUA).  "));
        assertEquals("a-b-c-d-e-f-g-h-i", DropboxEstructura.limpiarComponente("a/b\\c:d*e?f\"g<h>i"));
        assertEquals("260201_BONAQUIA, S.L", DropboxEstructura.nombreCarpetaProyecto(LocalDate.of(2026, 2, 1), "BONAQUIA, S.L."));
        assertThrows(IllegalArgumentException.class, () -> DropboxEstructura.nombreCarpetaProyecto(LocalDate.of(2026, 2, 1), " .. "));
        assertEquals("/a/b/c", DropboxEstructura.unir("/a/", "b", "", "/c/"));
        assertEquals("/", DropboxEstructura.unir());
        assertEquals("informe final.pdf", DropboxEstructura.limpiarNombreArchivo("C:\\docs\\informe final.pdf"));
        assertEquals("archivo", DropboxEstructura.limpiarNombreArchivo("   "));
        assertEquals("sin extension", DropboxEstructura.limpiarNombreArchivo("sin extension"));
        String largo = "x".repeat(200) + ".pdf";
        String corto = DropboxEstructura.limpiarNombreArchivo(largo);
        assertTrue(corto.endsWith(".pdf") && corto.length() == 120, "conserva la extensión al acortar");
        assertEquals(7, DropboxEstructura.SUBCARPETAS_FIJAS.size());
        assertEquals("/b/2026/260201_Nave/05_FACTURAS EMITIDAS", DropboxEstructura.rutaSubcarpeta(DropboxEstructura.rutaProyecto("/b", LocalDate.of(2026, 2, 1), "Nave"), "05_FACTURAS EMITIDAS"));
    }

    @Test
    void lasRutasDeFichaSoloAdmitenUnPdfEnLaCarpetaDeUnaMarca() {
        assertTrue(DropboxAlmacen.esRutaDeFicha("HUAWEI/ficha.pdf"));
        assertFalse(DropboxAlmacen.esRutaDeFicha("HUAWEI/sub/ficha.pdf"));
        assertFalse(DropboxAlmacen.esRutaDeFicha("HUAWEI/ficha.docx"));
        assertFalse(DropboxAlmacen.esRutaDeFicha("../ficha.pdf"));
        assertFalse(DropboxAlmacen.esRutaDeFicha("HUAWEI\\ficha.pdf"));
        assertFalse(DropboxAlmacen.esRutaDeFicha(" HUAWEI/ficha.pdf"));
        assertEquals("HUAWEI", DropboxAlmacen.marcaDeFicha("HUAWEI/ficha.pdf"));
    }

    // ------------------------------------------------------------------------------- sin Dropbox

    @Test
    void sinDropboxElServidorLoDiceYNoGuardaNada() throws Exception {
        mvc.perform(get(BASE + "/dropbox")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.base").value(""))
                .andExpect(jsonPath("$.datasheets").value(false));
        mvc.perform(get(BASE + "/dropbox/folders")).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Dropbox no está configurado en este servidor"));
        mvc.perform(get(BASE + "/dropbox/preview?name=X&start_date=2026-02-01")).andExpect(status().isConflict());
        String id = proyecto(null);
        ana(post(BASE + "/projects/" + id + "/dropbox-folder"), "{\"name\":\"X\"}").andExpect(status().isConflict());
        subir(put(BASE + "/tasks/" + tareaConCarpeta(id, true) + "/attachments/file?name=a.pdf"), "x".getBytes()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Este proyecto no tiene carpeta en Dropbox")));
        mvc.perform(get(BASE + "/features")).andExpect(status().isOk()).andExpect(jsonPath("$.local_files").value(false));
    }

    // ------------------------------------------------------------------------ carpetas de proyecto

    @Test
    void alCrearUnProyectoConNombreDeCarpetaSeCreaConSusSieteSubcarpetas() throws Exception {
        conDropbox();
        mvc.perform(get(BASE + "/dropbox")).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.base").value(BASE_DROPBOX)).andExpect(jsonPath("$.datasheets").value(true));
        String id = proyecto("\"dropbox_name\":\"BONAQUIA, S.L.\"");
        String ruta = proyectoDe(id).get("dropbox_path").asText();
        assertEquals(BASE_DROPBOX + "/2026/260201_BONAQUIA, S.L", ruta);
        assertTrue(falso.existeCarpeta(ruta));
        for (String sub : DropboxEstructura.SUBCARPETAS_FIJAS) {
            assertTrue(falso.existeCarpeta(ruta + "/" + sub), sub);
        }
        JsonNode actividad = leer(mvc.perform(get(BASE + "/projects/" + id + "/activity")));
        assertTrue(actividad.toString().contains("Carpeta de Dropbox del proyecto"));
        assertTrue(falso.llamadas.stream().noneMatch(c -> c.contains("delete") || c.contains("move")), "nunca se borra ni se mueve nada");
    }

    @Test
    void sinCarpetaDeAnioElProyectoSeCreaPeroSeAnotaElFallo() throws Exception {
        conDropbox();
        String id = proyecto("\"dropbox_name\":\"Otra\"").replace("2026", "2026");
        // El año 2027 no existe y la base sí: se crea el año; con una base inexistente no se crea nada
        falso.carpetas.remove(BASE_DROPBOX.toLowerCase());
        String sinBase = leer(ana(post(BASE + "/projects"), "{\"name\":\"Sin base\",\"start_date\":\"2030-01-01\",\"dropbox_name\":\"X\"}").andExpect(status().isCreated())).get("id").asText();
        assertEquals("", proyectoDe(sinBase).get("dropbox_path").asText(), "el proyecto se crea aunque Dropbox falle");
        assertTrue(leer(mvc.perform(get(BASE + "/projects/" + sinBase + "/activity"))).toString().contains("No se pudo crear la carpeta de Dropbox"));
        assertFalse(falso.existeCarpeta(BASE_DROPBOX), "no se levanta un árbol nuevo por error de ruta");
        assertNotNull(id);
    }

    @Test
    void unaCarpetaExistenteSeUsaTalCualYUnaFueraDeLaBaseSeRechaza() throws Exception {
        conDropbox();
        falso.crearCarpeta(BASE_DROPBOX + "/2026/260115_A MANO");
        String id = proyecto("\"dropbox_existing\":\"" + BASE_DROPBOX + "/2026/260115_A MANO\"");
        assertEquals(BASE_DROPBOX + "/2026/260115_A MANO", proyectoDe(id).get("dropbox_path").asText());
        assertFalse(falso.existeCarpeta(BASE_DROPBOX + "/2026/260115_A MANO/01_CONTRATO INSTALACION, MANTENIMIENTO Y OFERTA ECONOMICA"), "no se crea nada dentro");

        String otro = proyecto(null);
        ana(post(BASE + "/projects/" + otro + "/dropbox-folder"), "{\"existing\":\"/AURA INGENIERIA/OTRA COSA/2026/x\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Esa carpeta queda fuera de la carpeta de trabajos de Dropbox"));
        ana(post(BASE + "/projects/" + otro + "/dropbox-folder"), "{\"existing\":\"" + BASE_DROPBOX + "/2026\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Elige la carpeta del proyecto, dentro de la carpeta de su año (base/año/proyecto)"));
        ana(post(BASE + "/projects/" + otro + "/dropbox-folder"), "{\"existing\":\"" + BASE_DROPBOX + "/2026/../x\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Ruta no válida"));
        ana(post(BASE + "/projects/" + otro + "/dropbox-folder"), "{\"existing\":\"" + BASE_DROPBOX + "/2026/no existe\"}").andExpect(status().isNotFound());
    }

    @Test
    void lasCarpetasSeCreanDespuesYSoloUnaVez() throws Exception {
        conDropbox();
        String id = proyecto(null);
        JsonNode p = leer(ana(post(BASE + "/projects/" + id + "/dropbox-folder"), "{\"name\":\"Nave Tardía\"}").andExpect(status().isOk()));
        assertEquals(BASE_DROPBOX + "/2026/260201_Nave Tardía", p.get("dropbox_path").asText());
        ana(post(BASE + "/projects/" + id + "/dropbox-folder"), "{\"name\":\"Otra\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El proyecto ya tiene carpeta de Dropbox"));
        // Completar subcarpetas: no hay nada que crear hasta que falte alguna
        ana(post(BASE + "/projects/" + id + "/dropbox-folder/subfolders"), null).andExpect(status().isOk()).andExpect(jsonPath("$.created").isEmpty());
        falso.carpetas.remove((p.get("dropbox_path").asText() + "/03_FOTOS").toLowerCase());
        ana(post(BASE + "/projects/" + id + "/dropbox-folder/subfolders"), null).andExpect(jsonPath("$.created[0]").value("03_FOTOS"));
        String sinCarpeta = proyecto(null);
        ana(post(BASE + "/projects/" + sinCarpeta + "/dropbox-folder/subfolders"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El proyecto no tiene carpeta de Dropbox: créala primero"));
    }

    @Test
    void sePuedeMirarDentroDeLaCarpetaBaseYVerSiYaExisteLaDelProyecto() throws Exception {
        conDropbox();
        falso.crearCarpeta(BASE_DROPBOX + "/2026/260201_NAVE");
        falso.crearCarpeta(BASE_DROPBOX + "/2026/260201_OTRA");
        falso.crearCarpeta(BASE_DROPBOX + "/2026/260305_Z");
        mvc.perform(get(BASE + "/dropbox/folders")).andExpect(jsonPath("$.depth").value(0)).andExpect(jsonPath("$.can_choose").value(false)).andExpect(jsonPath("$.folders[0]").value("2026"));
        mvc.perform(get(BASE + "/dropbox/folders").param("path", BASE_DROPBOX + "/2026")).andExpect(jsonPath("$.depth").value(1))
                .andExpect(jsonPath("$.folders.length()").value(3));
        mvc.perform(get(BASE + "/dropbox/folders").param("path", BASE_DROPBOX + "/2026/260201_NAVE")).andExpect(jsonPath("$.can_choose").value(true));
        mvc.perform(get(BASE + "/dropbox/preview?name=Nave&start_date=2026-02-01")).andExpect(status().isOk())
                .andExpect(jsonPath("$.folder").value("260201_Nave")).andExpect(jsonPath("$.year_exists").value(true)).andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.same_day.length()").value(2));
        mvc.perform(get(BASE + "/dropbox/preview?name=nave&start_date=2026-02-01")).andExpect(jsonPath("$.exists").value(true));
        mvc.perform(get(BASE + "/dropbox/preview?name=OtroNombre&start_date=2026-02-01")).andExpect(jsonPath("$.exists").value(false));
        mvc.perform(get(BASE + "/dropbox/preview?name=Nave&start_date=2031-02-01")).andExpect(jsonPath("$.year_exists").value(false));
        mvc.perform(get(BASE + "/dropbox/preview?name=Nave&start_date=ayer")).andExpect(status().isUnprocessableEntity());
    }

    // ---------------------------------------------------------------------- archivos de las tareas

    @Test
    void unArchivoSubidoAUnaTareaVaALaSubcarpetaDeEsaTareaYSeAbreConUnEnlaceTemporal() throws Exception {
        conDropbox();
        String id = proyecto("\"dropbox_name\":\"Nave\"");
        String tarea = tareaConCarpeta(id, true);
        String carpetaDeTarea = leer(mvc.perform(get(BASE + "/projects/" + id + "/tasks"))).findValues("dropbox_folder").stream().map(JsonNode::asText).filter(x -> !x.isEmpty()).findFirst().orElseThrow();
        JsonNode t = leer(subir(put(BASE + "/tasks/" + tarea + "/attachments/file?name=Factura 1.pdf"), "PDF-uno".getBytes()).andExpect(status().isCreated()));
        JsonNode adjunto = t.get("attachments").get(0);
        assertEquals("file", adjunto.get("kind").asText());
        assertEquals("dropbox", adjunto.get("storage").asText());
        assertEquals("Factura 1.pdf", adjunto.get("name").asText());
        assertEquals(7, adjunto.get("size").asInt());
        assertTrue(adjunto.get("path").asText().startsWith(BASE_DROPBOX + "/2026/260201_Nave/"));
        assertEquals("PDF-uno", DropboxFalso.texto(falso.archivo(adjunto.get("path").asText())));
        assertEquals("doing", t.get("status").asText(), "aportar algo a una tarea pendiente la pone en curso");
        // Un nombre repetido no sobrescribe: Dropbox lo renombra
        JsonNode segundo = leer(subir(put(BASE + "/tasks/" + tarea + "/attachments/file?name=Factura 1.pdf"), "PDF-dos".getBytes()).andExpect(status().isCreated())).get("attachments").get(1);
        assertEquals("Factura 1 (1).pdf", segundo.get("name").asText());
        assertEquals("PDF-uno", DropboxFalso.texto(falso.archivo(adjunto.get("path").asText())));
        // Se abre con una redirección a un enlace temporal
        mvc.perform(get(BASE + "/tasks/" + tarea + "/attachments/" + adjunto.get("id").asText() + "/file")).andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://dl.example.test/temporal")));
        mvc.perform(get(BASE + "/tasks/" + tarea + "/attachments/inexistente/file")).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Archivo no encontrado"));
        // Quitarlo del planificador no lo borra de Dropbox
        ana(delete(BASE + "/tasks/" + tarea + "/attachments/" + adjunto.get("id").asText()), null).andExpect(status().isOk());
        assertNotNull(falso.archivo(adjunto.get("path").asText()));
        assertFalse(carpetaDeTarea.isEmpty());
    }

    @Test
    void unaTareaSinSubcarpetaGuardaEnLaRaizDelProyectoYUnArchivoVacioOExageradoSeRechaza() throws Exception {
        conDropbox();
        String id = proyecto("\"dropbox_name\":\"Nave\"");
        String tarea = tareaConCarpeta(id, false);
        JsonNode t = leer(subir(put(BASE + "/tasks/" + tarea + "/attachments/file?name=nota.txt"), "hola".getBytes()).andExpect(status().isCreated()));
        assertEquals(BASE_DROPBOX + "/2026/260201_Nave/nota.txt", t.get("attachments").get(0).get("path").asText());
        subir(put(BASE + "/tasks/" + tarea + "/attachments/file?name=vacio.txt"), new byte[0]).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El archivo está vacío"));
        subir(put(BASE + "/tasks/" + tarea + "/attachments/file?name=grande.bin"), new byte[25 * 1024 * 1024 + 1]).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("El archivo supera el máximo de 25 MB"));
        subir(put(BASE + "/tasks/" + tarea + "/attachments/file?name=x.txt&requirement=zzz"), "x".getBytes()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unArchivoPuedeCumplirUnRequisitoYCerrarLaTareaSiEraElUltimo() throws Exception {
        conDropbox();
        String id = proyecto("\"dropbox_name\":\"Nave\"");
        String tarea = null;
        String requisito = null;
        for (JsonNode t : tareas(id)) {
            JsonNode entregables = t.get("deliverables");
            if (entregables.size() == 1 && !entregables.get(0).get("na").asBoolean()) {
                tarea = t.get("id").asText();
                requisito = entregables.get(0).get("key").asText();
                break;
            }
        }
        assertNotNull(tarea, "la plantilla tiene alguna tarea con un único documento requerido");
        JsonNode t = leer(subir(put(BASE + "/tasks/" + tarea + "/attachments/file?name=doc.pdf&requirement=" + requisito), "x".getBytes()).andExpect(status().isCreated()));
        assertEquals("done", t.get("status").asText());
    }

    @Test
    void siDropboxFallaElArchivoNoSeGuardaYSeAvisa() throws Exception {
        conDropbox();
        String id = proyecto("\"dropbox_name\":\"Nave\"");
        String tarea = tareaConCarpeta(id, true);
        falso.rechazarCredenciales = true;
        almacen.usar(new DropboxCliente("clave", "secreto", "refresco", falso.url(), falso.url(), Duration.ofSeconds(5)), BASE_DROPBOX, FICHAS);
        subir(put(BASE + "/tasks/" + tarea + "/attachments/file?name=a.pdf"), "x".getBytes()).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("No se pudo guardar en Dropbox: "),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto")), org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("refresco")))));
        assertEquals(0, tareas(id).get(0).get("attachments").size() + 0 * tareas(id).size());
    }

    // ------------------------------------------------------------------------------ fichas técnicas

    private String equipoConMarca(String marca) throws Exception {
        ana(post(BASE + "/brands"), "{\"name\":\"" + marca + "\"}").andExpect(status().isCreated());
        return leer(ana(post(BASE + "/equipment"), "{\"category\":\"modulos\",\"group\":\"modulos\",\"brand\":\"" + marca + "\",\"model\":\"M1\"}").andExpect(status().isCreated())).get("id").asText();
    }

    @Test
    void unaFichaSeGuardaEnLaCarpetaDeSuMarcaYSeVeEnPantalla() throws Exception {
        conDropbox();
        String id = equipoConMarca("HUAWEI");
        JsonNode e = leer(subir(put(BASE + "/equipment/" + id + "/datasheet?name=Ficha M1"), "%PDF-1.4 contenido".getBytes()).andExpect(status().isOk()));
        assertEquals("HUAWEI/Ficha M1.pdf", e.get("datasheet").asText());
        assertEquals("%PDF-1.4 contenido", DropboxFalso.texto(falso.archivo(FICHAS + "/HUAWEI/Ficha M1.pdf")));
        assertTrue(falso.existeCarpeta(FICHAS + "/HUAWEI"), "la carpeta de la marca se crea si falta");
        // Un nombre repetido no sobrescribe
        assertEquals("HUAWEI/Ficha M1 (1).pdf", leer(subir(put(BASE + "/equipment/" + id + "/datasheet?name=Ficha M1.pdf"), "%PDF-1.4 otra".getBytes())).get("datasheet").asText());
        // Verla
        var vista = mvc.perform(get(BASE + "/equipment/" + id + "/datasheet")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn();
        assertEquals("%PDF-1.4 otra", vista.getResponse().getContentAsString());
        assertTrue(vista.getResponse().getHeader("Content-Disposition").startsWith("inline; filename*=UTF-8''Ficha%20M1"));
        // Las que ya hay en la carpeta
        mvc.perform(get(BASE + "/equipment/" + id + "/datasheet-options")).andExpect(jsonPath("$.brand").value("HUAWEI")).andExpect(jsonPath("$.folder").value(true))
                .andExpect(jsonPath("$.files.length()").value(2));
    }

    @Test
    void lasFichasSeValidan() throws Exception {
        conDropbox();
        String id = equipoConMarca("HUAWEI");
        subir(put(BASE + "/equipment/" + id + "/datasheet?name=a.pdf"), new byte[0]).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("El archivo está vacío"));
        subir(put(BASE + "/equipment/" + id + "/datasheet?name=a.pdf"), "no es un pdf".getBytes()).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("La ficha técnica tiene que ser un PDF"));
        String sinMarca = leer(ana(post(BASE + "/equipment"), "{\"category\":\"modulos\",\"group\":\"modulos\",\"model\":\"Sin marca\"}").andExpect(status().isCreated())).get("id").asText();
        subir(put(BASE + "/equipment/" + sinMarca + "/datasheet?name=a.pdf"), "%PDF-".getBytes()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Elige primero la marca del producto: la ficha se guarda en la carpeta de su marca"));
        mvc.perform(get(BASE + "/equipment/" + sinMarca + "/datasheet-options")).andExpect(jsonPath("$.files.length()").value(0)).andExpect(jsonPath("$.folder").value(false));
        mvc.perform(get(BASE + "/equipment/" + id + "/datasheet")).andExpect(status().isNotFound());
        subir(put(BASE + "/equipment/inexistente/datasheet?name=a.pdf"), "%PDF-".getBytes()).andExpect(status().isNotFound());
    }

    @Test
    void sinLaCarpetaDeFichasConfiguradaSubirUnaFichaEsUnConflicto() throws Exception {
        conDropbox();
        almacen.usar(new DropboxCliente("clave", "secreto", "refresco", falso.url(), falso.url(), Duration.ofSeconds(5)), BASE_DROPBOX, "");
        String id = equipoConMarca("HUAWEI");
        subir(put(BASE + "/equipment/" + id + "/datasheet?name=a.pdf"), "%PDF-".getBytes()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Las fichas técnicas no están configuradas en este servidor (Dropbox)"));
        mvc.perform(get(BASE + "/dropbox")).andExpect(jsonPath("$.datasheets").value(false));
    }

    // --------------------------------------------------------------------------------------- cliente

    @Test
    void elClienteRenuevaElTokenUnaVezYNuncaMuestraLasClaves() throws Exception {
        falso = new DropboxFalso();
        falso.crearCarpeta("/x");
        DropboxCliente c = new DropboxCliente("clave-secreta", "secreto-app", "refresco-token", falso.url(), falso.url(), Duration.ofSeconds(5));
        assertTrue(c.carpetaExiste("/x"));
        assertFalse(c.carpetaExiste("/y"));
        assertTrue(c.carpetaExiste("/X"), "Dropbox no distingue mayúsculas");
        assertEquals(1, falso.peticionesDeToken, "el token se renueva solo cuando hace falta");
        assertEquals("DropboxCliente(<configurado>)", c.toString());
        assertEquals("Cuenta de prueba", c.resumenCuenta().get("name"));
        assertEquals(false, c.resumenCuenta().get("team_space"));
        c.asegurarCarpeta("/x/a/b/c"); // crea las que faltan por encima
        assertTrue(falso.existeCarpeta("/x/a/b/c"));
        assertEquals(List.of("a"), c.listarSubcarpetas("/x"));
        assertNull(c.listarSubcarpetas("/no existe"));
        // Nombres con tildes y ñ viajan escapados en la cabecera
        var guardado = c.subir("/x/Ñandú año.txt", "hola".getBytes());
        assertEquals("/x/Ñandú año.txt", guardado.get("path"));
        assertEquals("hola", new String(c.descargar("/x/Ñandú año.txt")));
        falso.rechazarCredenciales = true;
        DropboxCliente malo = new DropboxCliente("clave-secreta", "secreto-app", "refresco-token", falso.url(), falso.url(), Duration.ofSeconds(5));
        DropboxError error = assertThrows(DropboxError.class, () -> malo.carpetaExiste("/x"));
        assertFalse(error.getMessage().contains("secreto") || error.getMessage().contains("refresco"));
        assertEquals(400, error.getEstado());
        DropboxCliente sinRed = new DropboxCliente("a", "b", "c", "http://127.0.0.1:1", "http://127.0.0.1:1", Duration.ofSeconds(2));
        assertTrue(assertThrows(DropboxError.class, () -> sinRed.carpetaExiste("/x")).getMessage().startsWith("No se pudo conectar con Dropbox"));
    }
}
