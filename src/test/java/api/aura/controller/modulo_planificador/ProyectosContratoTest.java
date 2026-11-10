package api.aura.controller.modulo_planificador;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de contrato de proyectos, tareas, plantillas, borradores y actividad: reglas de estado, entregables, dependencias y
 * planificación, y el registro de lo que cambia.
 */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class ProyectosContratoTest {

    private static final String BASE = "/api/planificador";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        for (String t : new String[]{"pl_actividad", "pl_devoluciones_material", "pl_tareas_proyecto", "pl_borradores", "pl_proyectos"}) {
            jdbc.execute("DELETE FROM " + t);
        }
    }

    // ------------------------------------------------------------------ ayudas

    private ResultActions ana(MockHttpServletRequestBuilder b, String cuerpo) throws Exception {
        b.header("X-Aura-Usuario-Nombre", "Ana").header("X-Usuario-Id", 7).header("X-Usuario-Nivel", 3);
        if (cuerpo != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(b);
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String proyecto(String cuerpo) throws Exception {
        return leer(ana(post(BASE + "/projects"), cuerpo).andExpect(status().isCreated())).get("id").asText();
    }

    private String proyectoVacio() throws Exception {
        return proyecto("{\"name\":\"Casa Pérez\",\"start_date\":\"2030-01-01\"}");
    }

    private String tarea(String proyectoId, String nombre, String inicio, String fin, String extra) throws Exception {
        String cuerpo = "{\"name\":\"" + nombre + "\",\"start\":\"" + inicio + "\",\"end\":\"" + fin + "\"" + (extra == null ? "" : "," + extra) + "}";
        return leer(ana(post(BASE + "/projects/" + proyectoId + "/tasks"), cuerpo).andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode tareaDe(String proyectoId, String id) throws Exception {
        for (JsonNode t : leer(mvc.perform(get(BASE + "/projects/" + proyectoId + "/tasks")))) {
            if (t.get("id").asText().equals(id)) {
                return t;
            }
        }
        throw new AssertionError("no está la tarea " + id);
    }

    // ---------------------------------------------------------------- proyectos

    @Test
    void altaDeUnProyectoDevuelveTodasLasClavesDelContrato() throws Exception {
        JsonNode p = leer(ana(post(BASE + "/projects"), "{\"name\":\"  Casa Pérez \",\"description\":\"d\",\"start_date\":\"2030-01-01\"}").andExpect(status().isCreated()));
        for (String clave : new String[]{"id", "owner_id", "name", "description", "start_date", "documents", "baseline", "changes", "budget",
                "contract_party", "contract_generated_at", "contract_file", "payment_plan", "created_at", "dropbox_path", "deleted_at"}) {
            assertTrue(p.has(clave), "falta " + clave);
        }
        assertEquals("Casa Pérez", p.get("name").asText());
        assertEquals("2030-01-01", p.get("start_date").asText());
        assertEquals("local", p.get("owner_id").asText());
        assertTrue(p.get("deleted_at").isNull());
    }

    @Test
    void sinNombreOSinFechaValidaEsError422YSinFechaEmpiezaHoy() throws Exception {
        ana(post(BASE + "/projects"), "{\"name\":\"   \"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El proyecto necesita un nombre"));
        ana(post(BASE + "/projects"), "{\"description\":\"x\"}").andExpect(status().isUnprocessableEntity());
        ana(post(BASE + "/projects"), "{\"name\":\"X\",\"start_date\":\"mañana\"}").andExpect(status().isUnprocessableEntity());
        JsonNode p = leer(ana(post(BASE + "/projects"), "{\"name\":\"X\"}").andExpect(status().isCreated()));
        assertEquals(LocalDate.now().toString(), p.get("start_date").asText());
    }

    @Test
    void laPlantillaCreaTodosSusPasosConFechasDependenciasYEntregables() throws Exception {
        JsonNode plantillas = leer(mvc.perform(get(BASE + "/templates")).andExpect(status().isOk()));
        assertEquals("fotovoltaica", plantillas.get(0).get("id").asText());
        assertEquals(33, plantillas.get(0).get("step_count").asInt());
        assertEquals(2, plantillas.get(0).get("optional_steps").size());

        String id = proyecto("{\"name\":\"Nave\",\"start_date\":\"2030-01-01\",\"template_id\":\"fotovoltaica\"}");
        JsonNode tareas = leer(mvc.perform(get(BASE + "/projects/" + id + "/tasks")).andExpect(status().isOk()));
        assertEquals(33, tareas.size());
        JsonNode ficha = tareas.get(0);
        assertEquals("2030-01-01", ficha.get("start").asText());
        assertEquals("2030-01-02", ficha.get("end").asText(), "dos días de duración");
        assertEquals(ficha.get("end").asText(), ficha.get("planned_end").asText(), "el plazo de referencia es el de la planificación inicial");
        // «contrato» depende de la ficha: empieza al día siguiente de que acabe
        JsonNode contrato = null;
        for (JsonNode t : tareas) {
            if (t.get("name").asText().startsWith("Redactar contrato")) {
                contrato = t;
            }
        }
        assertNotNull(contrato);
        assertEquals("2030-01-03", contrato.get("start").asText());
        assertEquals(ficha.get("id").asText(), contrato.get("depends_on").get(0).asText());
        assertEquals(1, contrato.get("deliverables").size());
        assertTrue(contrato.get("deliverables").get(0).get("locked").asBoolean());
        assertEquals(3, contrato.get("checklist").size());
        assertFalse(contrato.get("checklist").get(0).get("done").asBoolean());
        assertEquals(7, leer(mvc.perform(get(BASE + "/projects/" + id))).get("documents").size());
        // La actividad anota la creación con el número de pasos
        JsonNode actividad = leer(mvc.perform(get(BASE + "/projects/" + id + "/activity")));
        assertEquals("Creó el proyecto", actividad.get(0).get("summary").asText());
        assertEquals("Con los 33 pasos de la plantilla", actividad.get(0).get("detail").asText());
        assertEquals("Ana", actividad.get(0).get("actor").asText());
        assertEquals("project", actividad.get(0).get("kind").asText());
    }

    @Test
    void losPasosOpcionalesSePuedenExcluirYSusDependenciasSeDescartan() throws Exception {
        String id = proyecto("{\"name\":\"Nave\",\"start_date\":\"2030-01-01\",\"template_id\":\"fotovoltaica\",\"exclude_steps\":[\"plataforma-elevadora\",\"subvenciones\"]}");
        assertEquals(31, leer(mvc.perform(get(BASE + "/projects/" + id + "/tasks"))).size());
        ana(post(BASE + "/projects"), "{\"name\":\"X\",\"template_id\":\"no-existe\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Plantilla no encontrada"));
    }

    @Test
    void aplicarUnaPlantillaSoloSiElProyectoNoTieneTareas() throws Exception {
        String id = proyectoVacio();
        ana(post(BASE + "/projects/" + id + "/apply-template"), "{\"template_id\":\"fotovoltaica\",\"start_date\":\"2031-02-01\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.start_date").value("2031-02-01"));
        assertEquals(33, leer(mvc.perform(get(BASE + "/projects/" + id + "/tasks"))).size());
        ana(post(BASE + "/projects/" + id + "/apply-template"), "{\"template_id\":\"fotovoltaica\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El proyecto ya tiene tareas"));
        ana(post(BASE + "/projects/nada/apply-template"), "{\"template_id\":\"fotovoltaica\"}").andExpect(status().isNotFound());
    }

    @Test
    void laListaDeProyectosMarcaFinishedCuandoTodasSusTareasEstanHechas() throws Exception {
        String id = proyectoVacio();
        assertFalse(leer(mvc.perform(get(BASE + "/projects"))).get(0).get("finished").asBoolean(), "sin tareas no está terminado");
        String t = tarea(id, "Única", "2030-01-01", "2030-01-02", null);
        assertFalse(leer(mvc.perform(get(BASE + "/projects"))).get(0).get("finished").asBoolean());
        ana(patch(BASE + "/tasks/" + t), "{\"status\":\"doing\"}").andExpect(status().isOk());
        ana(patch(BASE + "/tasks/" + t), "{\"status\":\"done\"}").andExpect(status().isOk());
        assertTrue(leer(mvc.perform(get(BASE + "/projects"))).get(0).get("finished").asBoolean());
        // Reabrir la tarea devuelve el proyecto a la lista: no hay estado que sincronizar
        ana(patch(BASE + "/tasks/" + t), "{\"status\":\"doing\"}").andExpect(status().isOk());
        assertFalse(leer(mvc.perform(get(BASE + "/projects"))).get(0).get("finished").asBoolean());
    }

    @Test
    void cambiarDatosDelProyectoAnotaLoQueCambioEnLaActividad() throws Exception {
        String id = proyectoVacio();
        ana(patch(BASE + "/projects/" + id), "{\"name\":\"Casa Nueva\",\"description\":\"otra\",\"start_date\":\"2030-02-01\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Casa Nueva"));
        JsonNode a = leer(mvc.perform(get(BASE + "/projects/" + id + "/activity")));
        java.util.List<String> resumenes = new java.util.ArrayList<>();
        a.forEach(e -> resumenes.add(e.get("summary").asText()));
        assertTrue(resumenes.contains("Cambió el nombre del proyecto"));
        assertTrue(resumenes.contains("Modificó la descripción del proyecto"));
        assertTrue(resumenes.contains("Cambió la fecha de inicio: 01/02/2030"));
        mvc.perform(get(BASE + "/projects/nada")).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Proyecto no encontrado"));
    }

    @Test
    void eliminarUnProyectoPideElNombreExactoYBorraTodoLoSuyo() throws Exception {
        String id = proyectoVacio();
        tarea(id, "Una", "2030-01-01", "2030-01-02", null);
        ana(delete(BASE + "/projects/" + id), null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Para eliminarlo hay que escribir el nombre exacto del proyecto"));
        ana(delete(BASE + "/projects/" + id + "?confirm=casa"), null).andExpect(status().isUnprocessableEntity());
        ana(delete(BASE + "/projects/" + id + "?confirm=Casa Pérez"), null).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pl_tareas_proyecto", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pl_actividad", Integer.class));
        mvc.perform(get(BASE + "/projects/" + id)).andExpect(status().isNotFound());
    }

    // --------------------------------------------------------------- borradores

    @Test
    void borradoresCrearListarConflictoDeVersionYBorrado() throws Exception {
        JsonNode b = leer(ana(post(BASE + "/drafts"), "{\"name\":\"  Alta de Pedro \",\"data\":{\"a\":1}}").andExpect(status().isCreated()));
        assertEquals("Alta de Pedro", b.get("name").asText());
        assertEquals(1, b.get("data").get("a").asInt());
        assertEquals("Ana", b.get("updated_by").asText());
        String id = b.get("id").asText();
        assertEquals(1, leer(mvc.perform(get(BASE + "/drafts"))).size());
        assertFalse(leer(mvc.perform(get(BASE + "/drafts"))).get(0).has("data"), "la lista no lleva el contenido");
        String version = b.get("updated_at").asText();
        ana(put(BASE + "/drafts/" + id), "{\"name\":\"v2\",\"data\":{\"a\":2},\"base_updated_at\":\"" + version + "\"}").andExpect(status().isOk());
        ana(put(BASE + "/drafts/" + id), "{\"name\":\"v3\",\"data\":{},\"base_updated_at\":\"" + version + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ana ha guardado este borrador mientras lo editabas. Recárgalo para ver sus cambios."));
        ana(put(BASE + "/drafts/" + id), "{\"name\":\"v4\",\"data\":{}}").andExpect(status().isOk());
        mvc.perform(delete(BASE + "/drafts/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete(BASE + "/drafts/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/drafts/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Borrador no encontrado (puede que lo hayan descartado o convertido en proyecto)"));
    }

    @Test
    void crearUnProyectoDesdeUnBorradorLoConsumeYUnSegundoIntentoFalla() throws Exception {
        String borrador = leer(ana(post(BASE + "/drafts"), "{\"name\":\"x\",\"data\":{}}")).get("id").asText();
        ana(post(BASE + "/projects"), "{\"name\":\"Desde borrador\",\"draft_id\":\"" + borrador + "\"}").andExpect(status().isCreated());
        assertEquals(0, leer(mvc.perform(get(BASE + "/drafts"))).size());
        ana(post(BASE + "/projects"), "{\"name\":\"Otra vez\",\"draft_id\":\"" + borrador + "\"}").andExpect(status().isConflict());
        assertEquals(1, leer(mvc.perform(get(BASE + "/projects"))).size(), "no se creó el segundo");
    }

    // -------------------------------------------------------------------- tareas

    @Test
    void altaDeTareaDevuelveSusClavesYAnotaElPlazoDeReferencia() throws Exception {
        String p = proyectoVacio();
        JsonNode t = leer(ana(post(BASE + "/projects/" + p + "/tasks"),
                "{\"name\":\"Replanteo\",\"start\":\"2030-01-01\",\"end\":\"2030-01-05\",\"phase\":\"Obra\",\"department\":\"Ingeniería\",\"milestone\":true}")
                .andExpect(status().isCreated()));
        for (String clave : new String[]{"id", "project_id", "name", "description", "phase", "department", "position", "milestone", "start", "end",
                "planned_end", "progress", "status", "status_reason", "status_note", "completed_at", "closing_note", "deliverables", "attachments",
                "uses", "change_acks", "payment_stage", "payment_amount", "payment_label", "dropbox_folder", "notified", "due_marks", "depends_on",
                "checklist", "kind", "created_at"}) {
            assertTrue(t.has(clave), "falta " + clave);
        }
        assertEquals("todo", t.get("status").asText());
        assertEquals(0, t.get("progress").asInt());
        assertEquals("2030-01-05", t.get("planned_end").asText());
        assertTrue(t.get("milestone").asBoolean());
        assertEquals(0, t.get("position").asInt());
        ana(post(BASE + "/projects/" + p + "/tasks"), "{\"name\":\"B\",\"start\":\"2030-01-05\",\"end\":\"2030-01-06\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1));
        assertEquals("Añadió la tarea", leer(mvc.perform(get(BASE + "/projects/" + p + "/activity"))).get(0).get("summary").asText());
        assertEquals("01/01/2030 – 05/01/2030", leer(mvc.perform(get(BASE + "/projects/" + p + "/activity"))).get(1).get("detail").asText());
    }

    @Test
    void altaDeTareaValidaDatosYProyecto() throws Exception {
        String p = proyectoVacio();
        ana(post(BASE + "/projects/" + p + "/tasks"), "{\"start\":\"2030-01-01\",\"end\":\"2030-01-05\"}").andExpect(status().isUnprocessableEntity());
        ana(post(BASE + "/projects/" + p + "/tasks"), "{\"name\":\"A\",\"start\":\"2030-01-05\",\"end\":\"2030-01-01\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La fecha de fin no puede ser anterior al inicio"));
        ana(post(BASE + "/projects/" + p + "/tasks"), "{\"name\":\"A\",\"start\":\"2030-01-01\",\"end\":\"2030-01-05\",\"progress\":150}").andExpect(status().isUnprocessableEntity());
        ana(post(BASE + "/projects/nada/tasks"), "{\"name\":\"A\",\"start\":\"2030-01-01\",\"end\":\"2030-01-05\"}").andExpect(status().isNotFound());
    }

    @Test
    void unaTareaQueDependeDeOtraSeEmpujaAlDiaSiguienteDeQueAcabeLaPredecesora() throws Exception {
        String p = proyectoVacio();
        String a = tarea(p, "A", "2030-01-01", "2030-01-05", null);
        String b = tarea(p, "B", "2030-01-01", "2030-01-03", "\"depends_on\":[\"" + a + "\",\"" + a + "\"]");
        JsonNode tb = tareaDe(p, b);
        assertEquals("2030-01-06", tb.get("start").asText());
        assertEquals("2030-01-08", tb.get("end").asText(), "conserva su duración");
        assertEquals(1, tb.get("depends_on").size(), "las dependencias repetidas se quitan");
        assertEquals("2030-01-08", tb.get("planned_end").asText());
        // Alargar A empuja a B y avisa en la actividad de cuántas desplazó
        ana(patch(BASE + "/tasks/" + a), "{\"end\":\"2030-01-10\"}").andExpect(status().isOk());
        assertEquals("2030-01-11", tareaDe(p, b).get("start").asText());
        assertEquals("2030-01-13", tareaDe(p, b).get("planned_end").asText(), "replanificar mueve también el plazo de referencia de las que se desplazan");
        JsonNode ultima = leer(mvc.perform(get(BASE + "/projects/" + p + "/activity"))).get(0);
        assertEquals("Cambió las fechas: 01/01/2030 – 10/01/2030", ultima.get("summary").asText());
        assertEquals("Antes: 01/01/2030 – 05/01/2030 · Desplazó 1 tarea dependiente", ultima.get("detail").asText());
    }

    @Test
    void lasDependenciasSeValidanSobreSiMismaOtroProyectoYCiclos() throws Exception {
        String p = proyectoVacio();
        String otro = proyecto("{\"name\":\"Otro\",\"start_date\":\"2030-01-01\"}");
        String a = tarea(p, "A", "2030-01-01", "2030-01-02", null);
        String b = tarea(p, "B", "2030-01-03", "2030-01-04", "\"depends_on\":[\"" + a + "\"]");
        String ajena = tarea(otro, "Ajena", "2030-01-01", "2030-01-02", null);
        ana(patch(BASE + "/tasks/" + a), "{\"depends_on\":[\"" + a + "\"]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Una tarea no puede depender de sí misma"));
        ana(patch(BASE + "/tasks/" + a), "{\"depends_on\":[\"" + ajena + "\"]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Solo se puede depender de tareas del mismo proyecto"));
        ana(patch(BASE + "/tasks/" + a), "{\"depends_on\":[\"" + b + "\"]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La dependencia crearía un ciclo"));
        // Lo que falló no dejó nada a medias
        assertEquals(0, tareaDe(p, a).get("depends_on").size());
    }

    @Test
    void laTareaVencidaQueNoEstaHechaSeProrrogaHastaHoyYArrastraALasSucesoras() throws Exception {
        String p = proyectoVacio();
        String a = tarea(p, "Vencida", "2020-01-01", "2020-01-05", null);
        String b = tarea(p, "Siguiente", "2020-01-06", "2020-01-08", "\"depends_on\":[\"" + a + "\"]");
        JsonNode ta = tareaDe(p, a);
        assertEquals(LocalDate.now().toString(), ta.get("end").asText());
        assertEquals("2020-01-01", ta.get("start").asText(), "conserva su inicio");
        assertEquals("2020-01-05", ta.get("planned_end").asText(), "el plazo de referencia no se toca: así se mide el retraso");
        assertEquals(LocalDate.now().plusDays(1).toString(), tareaDe(p, b).get("start").asText());
        // Una tarea hecha no se prorroga
        String c = tarea(p, "Hecha", "2020-02-01", "2020-02-03", null);
        ana(patch(BASE + "/tasks/" + c), "{\"status\":\"doing\"}").andExpect(status().isOk());
        ana(patch(BASE + "/tasks/" + c), "{\"status\":\"done\"}").andExpect(status().isOk());
        assertEquals("2020-02-03", tareaDe(p, c).get("end").asText());
    }

    // ----------------------------------------------------------- estado y avance

    @Test
    void reglasDeEstadoPendienteEnCursoPausadaBloqueadaYHecha() throws Exception {
        String p = proyectoVacio();
        String t = tarea(p, "T", "2030-01-01", "2030-01-02", null);
        ana(patch(BASE + "/tasks/" + t), "{\"status\":\"raro\"}").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("Estado no válido"));
        ana(patch(BASE + "/tasks/" + t), "{\"status\":\"paused\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Primero hay que iniciar la tarea (En curso) para poder pausarla o bloquearla"));
        ana(patch(BASE + "/tasks/" + t), "{\"status\":\"doing\"}").andExpect(status().isOk()).andExpect(jsonPath("$.progress").value(50));
        ana(patch(BASE + "/tasks/" + t), "{\"status\":\"blocked\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Indica el motivo del bloqueo"));
        ana(patch(BASE + "/tasks/" + t), "{\"status\":\"blocked\",\"status_reason\":\"Falta el permiso\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(50)).andExpect(jsonPath("$.status").value("blocked"));
        // Al salir del bloqueo el motivo se borra
        ana(patch(BASE + "/tasks/" + t), "{\"status\":\"doing\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status_reason").value(""));
        ana(patch(BASE + "/tasks/" + t), "{\"status\":\"todo\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Una tarea iniciada no puede volver a Pendiente: para retomarla, ponla En curso"));
        JsonNode hecha = leer(ana(patch(BASE + "/tasks/" + t), "{\"status\":\"done\"}").andExpect(status().isOk()));
        assertEquals(100, hecha.get("progress").asInt());
        assertEquals(LocalDate.now().toString(), hecha.get("completed_at").asText());
        JsonNode reabierta = leer(ana(patch(BASE + "/tasks/" + t), "{\"status\":\"doing\"}").andExpect(status().isOk()));
        assertTrue(reabierta.get("completed_at").isNull());
    }

    @Test
    void elProgresoSeDeduceDeLasSubtareas() throws Exception {
        String p = proyectoVacio();
        String t = tarea(p, "T", "2030-01-01", "2030-01-02", "\"checklist\":[{\"text\":\"uno\",\"done\":false},{\"text\":\"dos\",\"done\":false}]");
        // Marcar una subtarea inicia la tarea y da el 50 %
        JsonNode medio = leer(ana(patch(BASE + "/tasks/" + t), "{\"checklist\":[{\"text\":\"uno\",\"done\":true},{\"text\":\"dos\",\"done\":false}]}").andExpect(status().isOk()));
        assertEquals("doing", medio.get("status").asText());
        assertEquals(50, medio.get("progress").asInt());
        // Marcarlas todas da la tarea por hecha
        JsonNode hecha = leer(ana(patch(BASE + "/tasks/" + t), "{\"checklist\":[{\"text\":\"uno\",\"done\":true},{\"text\":\"dos\",\"done\":true}]}").andExpect(status().isOk()));
        assertEquals("done", hecha.get("status").asText());
        assertEquals(100, hecha.get("progress").asInt());
        // La actividad lo cuenta en el orden en que ocurrió
        JsonNode actividad = leer(mvc.perform(get(BASE + "/projects/" + p + "/activity")));
        java.util.List<String> textos = new java.util.ArrayList<>();
        actividad.forEach(e -> textos.add(e.get("summary").asText()));
        assertTrue(textos.contains("Marcó la subtarea «uno»"));
        assertTrue(textos.contains("Marcó la subtarea «dos»"));
        assertTrue(textos.contains("Cambió el estado: En curso → Hecha"));
    }

    @Test
    void darUnaTareaPendientePorHechaQuedaAnotadoQueNoPasoPorEnCurso() throws Exception {
        String p = proyectoVacio();
        String t = tarea(p, "T", "2030-01-01", "2030-01-02", null);
        ana(patch(BASE + "/tasks/" + t), "{\"status\":\"done\",\"closing_note\":\"Todo bien\"}").andExpect(status().isOk());
        JsonNode e = leer(mvc.perform(get(BASE + "/projects/" + p + "/activity"))).get(0);
        assertEquals("Cambió el estado: Pendiente → Hecha", e.get("summary").asText());
        assertEquals("Comentario de cierre: Todo bien · Se dio por hecha sin pasar por En curso", e.get("detail").asText());
        assertEquals("T", e.get("task_name").asText());
    }

    // --------------------------------------------------------------- entregables

    @Test
    void sinAportarLosDocumentosObligatoriosNoSeFinalizaYLosDeLaPlantillaNoSeQuitan() throws Exception {
        String p = proyecto("{\"name\":\"N\",\"start_date\":\"2030-01-01\",\"template_id\":\"fotovoltaica\"}");
        JsonNode contrato = null;
        for (JsonNode t : leer(mvc.perform(get(BASE + "/projects/" + p + "/tasks")))) {
            if (t.get("name").asText().startsWith("Redactar contrato")) {
                contrato = t;
            }
        }
        String id = contrato.get("id").asText();
        String clave = contrato.get("deliverables").get(0).get("key").asText();
        String etiqueta = contrato.get("deliverables").get(0).get("label").asText();
        ana(patch(BASE + "/tasks/" + id), "{\"status\":\"doing\"}").andExpect(status().isOk());
        ana(patch(BASE + "/tasks/" + id), "{\"status\":\"done\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Faltan por aportar: " + etiqueta));
        // No se puede quitar un elemento que viene de la plantilla
        ana(patch(BASE + "/tasks/" + id), "{\"deliverables\":[]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("«" + etiqueta + "» es un elemento requerido y no se puede quitar: márcalo como «No aplica» y explica por qué"));
        // «No aplica» exige el motivo
        ana(patch(BASE + "/tasks/" + id), "{\"deliverables\":[{\"key\":\"" + clave + "\",\"label\":\"" + etiqueta + "\",\"na\":true}]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Indica por qué «" + etiqueta + "» no aplica"));
        // Con el motivo, resuelve el último documento y la tarea se cierra sola, con sus subtareas marcadas
        JsonNode cerrada = leer(ana(patch(BASE + "/tasks/" + id),
                "{\"deliverables\":[{\"key\":\"" + clave + "\",\"label\":\"" + etiqueta + "\",\"na\":true,\"na_reason\":\"No hay contrato\"}]}").andExpect(status().isOk()));
        assertEquals("done", cerrada.get("status").asText());
        assertEquals(100, cerrada.get("progress").asInt());
        assertTrue(cerrada.get("checklist").get(0).get("done").asBoolean());
        assertTrue(cerrada.get("deliverables").get(0).get("locked").asBoolean());
    }

    @Test
    void aportarUnaNotaOUnEnlaceInicialaTareaResuelveElDocumentoYLaCierraSola() throws Exception {
        String p = proyectoVacio();
        String t = tarea(p, "Con documento", "2030-01-01", "2030-01-02",
                "\"checklist\":[{\"text\":\"a\",\"done\":false}],\"deliverables\":[{\"key\":\"k1\",\"label\":\"Informe\"}]");
        // Un elemento que no está entre los requeridos
        ana(post(BASE + "/tasks/" + t + "/attachments"), "{\"kind\":\"note\",\"text\":\"x\",\"requirement\":\"otro\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Ese elemento no está entre los requeridos de la tarea"));
        ana(post(BASE + "/tasks/" + t + "/attachments"), "{\"kind\":\"link\",\"url\":\"ftp://x\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El enlace debe empezar por http:// o https://"));
        ana(post(BASE + "/tasks/" + t + "/attachments"), "{\"kind\":\"note\",\"text\":\"  \"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La nota está vacía"));
        ana(post(BASE + "/tasks/" + t + "/attachments"), "{\"kind\":\"video\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Tipo de elemento no válido"));
        // Un enlace suelto (sin cubrir el requisito) inicia la tarea pero no la cierra
        JsonNode suelto = leer(ana(post(BASE + "/tasks/" + t + "/attachments"), "{\"kind\":\"link\",\"url\":\"https://x.es/a\"}").andExpect(status().isCreated()));
        assertEquals("doing", suelto.get("status").asText());
        assertEquals("https://x.es/a", suelto.get("attachments").get(0).get("name").asText(), "sin nombre se usa la dirección");
        // La nota que cubre el requisito resuelve el último documento: la tarea se cierra sola
        JsonNode cerrada = leer(ana(post(BASE + "/tasks/" + t + "/attachments"), "{\"kind\":\"note\",\"text\":\"Hecho\",\"requirement\":\"k1\",\"name\":\"Informe final\"}").andExpect(status().isCreated()));
        assertEquals("done", cerrada.get("status").asText());
        assertTrue(cerrada.get("checklist").get(0).get("done").asBoolean());
        JsonNode actividad = leer(mvc.perform(get(BASE + "/projects/" + p + "/activity")));
        java.util.List<String> textos = new java.util.ArrayList<>();
        actividad.forEach(e -> textos.add(e.get("summary").asText()));
        assertTrue(textos.contains("Aportó la nota «Informe final»"));
        assertTrue(textos.contains("Aportó el enlace «https://x.es/a»"));
        // Quitar un elemento
        String adjunto = cerrada.get("attachments").get(0).get("id").asText();
        ana(delete(BASE + "/tasks/" + t + "/attachments/" + adjunto), null).andExpect(status().isOk()).andExpect(jsonPath("$.attachments.length()").value(1));
        ana(delete(BASE + "/tasks/" + t + "/attachments/nada"), null).andExpect(status().isNotFound());
    }

    @Test
    void subirArchivosSinCarpetaDeDropboxAvisaYBajarUnoInexistenteEsNotFound() throws Exception {
        String p = proyectoVacio();
        String t = tarea(p, "T", "2030-01-01", "2030-01-02", null);
        mvc.perform(put(BASE + "/tasks/" + t + "/attachments/file?name=a.pdf").content("%PDF-1")).andExpect(status().isConflict());
        mvc.perform(get(BASE + "/tasks/" + t + "/attachments/x/file")).andExpect(status().isNotFound());
        mvc.perform(put(BASE + "/tasks/nada/attachments/file?name=a.pdf")).andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------- eliminar

    @Test
    void eliminarUnaTareaLaQuitaDeLasDependenciasDeLasDemasYQuedaAnotado() throws Exception {
        String p = proyectoVacio();
        String a = tarea(p, "A", "2030-01-01", "2030-01-02", null);
        String b = tarea(p, "B", "2030-01-03", "2030-01-04", "\"depends_on\":[\"" + a + "\"]");
        mvc.perform(delete(BASE + "/tasks/" + a)).andExpect(status().isNoContent());
        assertEquals(0, tareaDe(p, b).get("depends_on").size());
        JsonNode e = leer(mvc.perform(get(BASE + "/projects/" + p + "/activity"))).get(0);
        assertEquals("Eliminó la tarea", e.get("summary").asText());
        assertEquals("A", e.get("task_name").asText(), "el registro sobrevive a la tarea eliminada");
        mvc.perform(delete(BASE + "/tasks/" + a)).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Tarea no encontrada"));
    }

    @Test
    void laActividadSeDevuelveDeLoMasRecienteAlMasAntiguoConSuLimite() throws Exception {
        String p = proyectoVacio();
        for (int i = 0; i < 4; i++) {
            tarea(p, "T" + i, "2030-01-01", "2030-01-02", null);
        }
        JsonNode todas = leer(mvc.perform(get(BASE + "/projects/" + p + "/activity")));
        assertEquals(5, todas.size());
        assertEquals("T3", todas.get(0).get("task_name").asText());
        assertEquals("Creó el proyecto", todas.get(4).get("summary").asText());
        assertEquals(2, leer(mvc.perform(get(BASE + "/projects/" + p + "/activity?limit=2"))).size());
        assertTrue(todas.get(0).get("at").asText().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+00:00"));
        mvc.perform(get(BASE + "/projects/nada/activity")).andExpect(status().isNotFound());
    }
}
