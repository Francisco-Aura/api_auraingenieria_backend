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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de contrato de la Fase 5: contrato de referencia, forma de pago, tareas de facturar, cambios con reparto de importes,
 * presupuesto del proyecto con variaciones y devolución de material al almacén.
 */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class ContratosFacturacionContratoTest {

    private static final String BASE = "/api/planificador";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        for (String t : new String[]{"pl_actividad", "pl_devoluciones_material", "pl_tareas_proyecto", "pl_borradores", "pl_proyectos", "pl_historial_precios", "pl_equipos", "pl_marcas"}) {
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

    private String proyectoConPlantilla(String extra) throws Exception {
        String cuerpo = "{\"name\":\"Nave\",\"start_date\":\"2030-01-01\",\"template_id\":\"fotovoltaica\",\"exclude_steps\":[]" + (extra == null ? "" : "," + extra) + "}";
        return leer(ana(post(BASE + "/projects"), cuerpo).andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode tareas(String id) throws Exception {
        return leer(mvc.perform(get(BASE + "/projects/" + id + "/tasks")).andExpect(status().isOk()));
    }

    private List<JsonNode> pagos(String id) throws Exception {
        List<JsonNode> lista = new ArrayList<>();
        for (JsonNode t : tareas(id)) {
            if (!t.get("payment_stage").isNull()) {
                lista.add(t);
            }
        }
        lista.sort((a, b) -> Integer.compare(a.get("payment_stage").asInt(), b.get("payment_stage").asInt()));
        return lista;
    }

    private JsonNode contrato(String id) throws Exception {
        return leer(mvc.perform(get(BASE + "/projects/" + id + "/contract")).andExpect(status().isOk()));
    }

    private ResultActions parche(String id, String cuerpo) throws Exception {
        return ana(patch(BASE + "/projects/" + id), cuerpo);
    }

    private void estado(String tareaId, String estado) throws Exception {
        ana(patch(BASE + "/tasks/" + tareaId), "{\"status\":\"" + estado + "\"}");
    }

    private static final String CAMBIO_1000 = "{\"id\":\"c1\",\"date\":\"2030-02-01\",\"reason\":\"Más paneles\",\"impact\":1000}";

    // ------------------------------------------------------------ contrato de referencia

    @Test
    void losCamposDelContratoSonLosMismosParaTodosLosProyectos() throws Exception {
        JsonNode campos = leer(mvc.perform(get(BASE + "/contract/fields")).andExpect(status().isOk()));
        assertEquals(8, campos.size());
        assertEquals("money", campos.get(0).get("type").asText());
        assertEquals("potencia_kwp", campos.get(7).get("key").asText());
    }

    @Test
    void lineaBaseSoloGuardaLosDatosConocidosConSuTipoYAceptaElFormatoEspanol() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":\"10.000,50\",\"paneles_uds\":\"20\",\"paneles_modelo\":\"  JA 550 \",\"potencia_kwp\":\"11,5\",\"inventado\":3,\"bateria\":\"\"}");
        JsonNode base = leer(mvc.perform(get(BASE + "/projects/" + id)).andExpect(status().isOk())).get("baseline");
        assertEquals(10000.5, base.get("importe_total").asDouble());
        assertEquals(20, base.get("paneles_uds").asInt());
        assertTrue(base.get("paneles_uds").isInt());
        assertEquals("JA 550", base.get("paneles_modelo").asText());
        assertEquals(11.5, base.get("potencia_kwp").asDouble());
        assertFalse(base.has("inventado"));
        assertTrue(base.get("bateria").isNull());
    }

    @Test
    void lineaBaseConDatosMalosEsError422ConElMensajeDelCampo() throws Exception {
        ana(post(BASE + "/projects"), "{\"name\":\"X\",\"baseline\":{\"importe_total\":\"mucho\"}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("«Importe total del contrato (sin IVA)» debe ser un número"));
        ana(post(BASE + "/projects"), "{\"name\":\"X\",\"baseline\":{\"paneles_uds\":-3}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("«Paneles (unidades)» debe ser un número positivo"));
        ana(post(BASE + "/projects"), "{\"name\":\"X\",\"baseline\":{\"paneles_uds\":2.5}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("«Paneles (unidades)» debe ser un número entero"));
    }

    // ------------------------------------------------------------------- forma de pago

    @Test
    void sinFormaDePagoSeUsaLaEstandarDeCuatroPagos() throws Exception {
        String id = proyectoConPlantilla(null);
        JsonNode plan = leer(mvc.perform(get(BASE + "/projects/" + id))).get("payment_plan");
        assertEquals("standard", plan.get("mode").asText());
        assertEquals(4, plan.get("stages").size());
        assertEquals(30.0, plan.get("stages").get(0).get("percent").asDouble());
        assertEquals("Reserva del material", plan.get("stages").get(0).get("note").asText());
    }

    @Test
    void formaDePagoPersonalizadaSeValida() throws Exception {
        String id = proyectoConPlantilla(null);
        parche(id, "{\"payment_plan\":{\"mode\":\"custom\",\"stages\":[]}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La forma de pago necesita al menos un pago"));
        parche(id, "{\"payment_plan\":{\"mode\":\"custom\",\"stages\":[{\"label\":\"\",\"percent\":100}]}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Indica a qué corresponde cada pago"));
        parche(id, "{\"payment_plan\":{\"mode\":\"custom\",\"stages\":[{\"label\":\"A\",\"percent\":60},{\"label\":\"B\",\"percent\":30}]}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Los porcentajes de los pagos suman 90 %: deben sumar exactamente 100 %"));
        parche(id, "{\"payment_plan\":{\"mode\":\"custom\",\"stages\":[{\"label\":\"A\",\"percent\":0},{\"label\":\"B\",\"percent\":100}]}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El porcentaje de «A» debe estar entre 0 y 100"));
        JsonNode ok = leer(parche(id, "{\"payment_plan\":{\"mode\":\"custom\",\"stages\":[{\"label\":\" A   la firma \",\"percent\":\"40,5\"},{\"label\":\"B\",\"percent\":59.5}]}}")
                .andExpect(status().isOk())).get("payment_plan");
        assertEquals("custom", ok.get("mode").asText());
        assertEquals("A la firma", ok.get("stages").get(0).get("label").asText());
        assertEquals(40.5, ok.get("stages").get(0).get("percent").asDouble());
        assertEquals("", ok.get("stages").get(0).get("note").asText());
    }

    // -------------------------------------------------------- tareas de facturar al crear

    @Test
    void lasCuatroTareasDeFacturarTomanElImporteDeSuPago() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        List<JsonNode> p = pagos(id);
        assertEquals(4, p.size());
        assertEquals(List.of(3000.0, 4000.0, 2000.0, 1000.0), p.stream().map(t -> t.get("payment_amount").asDouble()).toList());
        assertEquals("A la firma del contrato", p.get(0).get("payment_label").asText());
    }

    @Test
    void conMasDeCuatroPagosSeCreanTareasNuevasAlFinalDeLaFase6() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":1000},\"payment_plan\":{\"mode\":\"custom\",\"stages\":["
                + "{\"label\":\"P1\",\"percent\":20},{\"label\":\"P2\",\"percent\":20},{\"label\":\"P3\",\"percent\":20},{\"label\":\"P4\",\"percent\":20},{\"label\":\"P5\",\"percent\":10},{\"label\":\"P6\",\"percent\":10}]}");
        List<JsonNode> p = pagos(id);
        assertEquals(6, p.size());
        assertEquals(List.of(200.0, 200.0, 200.0, 200.0, 100.0, 100.0), p.stream().map(t -> t.get("payment_amount").asDouble()).toList());
        JsonNode quinta = p.get(4);
        assertEquals("Emitir factura pago 5: P5 y confirmar cobro", quinta.get("name").asText());
        assertEquals(p.get(3).get("id").asText(), quinta.get("depends_on").get(0).asText());
        assertEquals(p.get(3).get("phase").asText(), quinta.get("phase").asText());
        assertEquals(2, quinta.get("deliverables").size());
        assertEquals("Factura: P5", quinta.get("deliverables").get(0).get("label").asText());
        assertEquals(35, tareas(id).size(), "33 de la plantilla + 2 nuevas");
    }

    @Test
    void conMenosDeCuatroPagosSeQuedanLasUltimasTareasDeFacturar() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":1000},\"payment_plan\":{\"mode\":\"custom\",\"stages\":[{\"label\":\"Todo\",\"percent\":100}]}");
        List<JsonNode> p = pagos(id);
        assertEquals(1, p.size());
        assertEquals(1000.0, p.get(0).get("payment_amount").asDouble());
        assertEquals("Emitir factura del pago único y confirmar cobro", p.get(0).get("name").asText());
        assertEquals(30, tareas(id).size(), "33 menos las 3 primeras de facturar");
    }

    // ------------------------------------------------------------------------- cambios

    @Test
    void unCambioDeImporteSeRepartePorLosPagosPendientesEnProporcion() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        JsonNode p = leer(parche(id, "{\"changes\":[" + CAMBIO_1000 + "]}").andExpect(status().isOk()));
        JsonNode cambio = p.get("changes").get(0);
        assertEquals(1000.0, cambio.get("impact").asDouble());
        JsonNode reparto = cambio.get("allocation").get("tasks");
        assertEquals(4, reparto.size());
        assertEquals(List.of(3300.0, 4400.0, 2200.0, 1100.0), pagos(id).stream().map(t -> t.get("payment_amount").asDouble()).toList());
        JsonNode c = contrato(id);
        assertEquals(11000.0, c.get("summary").get("current_total").asDouble());
        assertEquals(1000.0, c.get("summary").get("modifications").asDouble());
        assertEquals(11000.0, c.get("current").get("importe_total").asDouble());
        assertEquals(11000.0, c.get("payments").get("total").asDouble());
        assertEquals(4, c.get("payments").get("stages").size());
        assertEquals(3300.0, c.get("payments").get("stages").get(0).get("amount").asDouble());
    }

    @Test
    void unPagoYaEmpezadoNoCambiaYLaDiferenciaVaAlosSiguientes() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        estado(pagos(id).get(0).get("id").asText(), "doing");
        parche(id, "{\"changes\":[" + CAMBIO_1000 + "]}").andExpect(status().isOk());
        double[] esperado = {3000.0, 4000.0 + 1000.0 * 4000 / 7000, 2000.0 + 1000.0 * 2000 / 7000, 1000.0 + 1000.0 * 1000 / 7000};
        List<JsonNode> p = pagos(id);
        assertEquals(3000.0, p.get(0).get("payment_amount").asDouble());
        double suma = p.stream().mapToDouble(t -> t.get("payment_amount").asDouble()).sum();
        assertEquals(11000.0, suma, 0.001);
        assertEquals(Math.round(esperado[1] * 100) / 100.0, p.get(1).get("payment_amount").asDouble(), 0.011);
    }

    @Test
    void sinPagosPendientesUnAumentoCreaLaTareaDeFacturarLaAmpliacion() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        for (JsonNode t : pagos(id)) {
            estado(t.get("id").asText(), "doing");
        }
        JsonNode p = leer(parche(id, "{\"changes\":[" + CAMBIO_1000 + "]}").andExpect(status().isOk()));
        String nueva = p.get("changes").get(0).get("allocation").get("new_task_id").asText();
        assertFalse(nueva.isEmpty());
        List<JsonNode> lista = pagos(id);
        assertEquals(5, lista.size());
        JsonNode extra = lista.get(4);
        assertEquals(nueva, extra.get("id").asText());
        assertEquals(1000.0, extra.get("payment_amount").asDouble());
        assertEquals("Ampliación: Más paneles", extra.get("payment_label").asText());
        assertEquals("Emitir factura de ampliación: Más paneles y confirmar cobro", extra.get("name").asText());
        JsonNode c = contrato(id);
        assertEquals(nueva, c.get("changes").get(0).get("payment_effect").get("new_task").asText());
        assertTrue(c.get("payments").get("stages").get(4).get("extra").asBoolean());
    }

    @Test
    void unAumentoPuedeCobrarseEnUnPagoNuevoAunqueQuedenPagosPendientes() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        parche(id, "{\"changes\":[{\"id\":\"c1\",\"date\":\"2030-02-01\",\"reason\":\"Extra\",\"impact\":500,\"payment_mode\":\"new\"}]}").andExpect(status().isOk());
        List<JsonNode> p = pagos(id);
        assertEquals(5, p.size());
        assertEquals(3000.0, p.get(0).get("payment_amount").asDouble());
        assertEquals(500.0, p.get(4).get("payment_amount").asDouble());
        // En una rebaja «new» no tiene sentido: se reparte como siempre
        String id2 = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        JsonNode cambio = leer(parche(id2, "{\"changes\":[{\"id\":\"c1\",\"date\":\"2030-02-01\",\"reason\":\"Rebaja\",\"impact\":-500,\"payment_mode\":\"new\"}]}")).get("changes").get(0);
        assertFalse(cambio.has("payment_mode"));
        assertEquals(4, pagos(id2).size());
    }

    @Test
    void unaRebajaNuncaDejaUnPagoPorDebajoDeCero() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":1000},\"payment_plan\":{\"mode\":\"custom\",\"stages\":[{\"label\":\"A\",\"percent\":90},{\"label\":\"B\",\"percent\":10}]}");
        parche(id, "{\"changes\":[{\"id\":\"r\",\"date\":\"2030-02-01\",\"reason\":\"Rebaja\",\"impact\":-1500}]}").andExpect(status().isOk());
        List<JsonNode> p = pagos(id);
        assertEquals(List.of(0.0, 0.0), p.stream().map(t -> t.get("payment_amount").asDouble()).toList());
    }

    @Test
    void quitarUnCambioDeshaceSuRepartoPeroNoSiLaFacturaYaEstaEnMarcha() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        parche(id, "{\"changes\":[" + CAMBIO_1000 + "]}").andExpect(status().isOk());
        estado(pagos(id).get(1).get("id").asText(), "doing");
        parche(id, "{\"changes\":[]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("No se puede modificar ni eliminar el cambio nº 1: la factura «")));
        // Con todo pendiente sí se puede, y los importes vuelven
        String id2 = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        parche(id2, "{\"changes\":[" + CAMBIO_1000 + "]}").andExpect(status().isOk());
        parche(id2, "{\"changes\":[]}").andExpect(status().isOk());
        assertEquals(List.of(3000.0, 4000.0, 2000.0, 1000.0), pagos(id2).stream().map(t -> t.get("payment_amount").asDouble()).toList());
    }

    @Test
    void guardarDeNuevoUnCambioIgualNoVuelveARepartir() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        JsonNode antes = leer(parche(id, "{\"changes\":[" + CAMBIO_1000 + "]}")).get("changes").get(0).get("allocation");
        JsonNode despues = leer(parche(id, "{\"changes\":[" + CAMBIO_1000 + "]}")).get("changes").get(0).get("allocation");
        assertEquals(antes, despues);
        assertEquals(List.of(3300.0, 4400.0, 2200.0, 1100.0), pagos(id).stream().map(t -> t.get("payment_amount").asDouble()).toList());
    }

    @Test
    void loscambiosSeValidanYSeOrdenanPorFecha() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        parche(id, "{\"changes\":[{\"date\":\"2030-02-01\",\"impact\":5}]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Indica el motivo del cambio"));
        parche(id, "{\"changes\":[{\"date\":\"ayer\",\"reason\":\"x\"}]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La fecha del cambio no es válida"));
        parche(id, "{\"changes\":[{\"date\":\"2030-02-01\",\"reason\":\"x\",\"impact\":\"muchísimo\"}]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El impacto económico debe ser un número (+ o -)"));
        JsonNode p = leer(parche(id, "{\"changes\":[{\"date\":\"2030-03-01\",\"reason\":\"B\",\"impact\":0},{\"date\":\"2030-02-01\",\"what\":\"A\",\"impact\":0,\"set\":{\"paneles_uds\":\"12\",\"importe_total\":5,\"nada\":1}}]}")
                .andExpect(status().isOk()));
        assertEquals("A", p.get("changes").get(0).get("reason").asText());
        assertEquals("B", p.get("changes").get(1).get("reason").asText());
        assertFalse(p.get("changes").get(0).get("id").asText().isEmpty());
        JsonNode fijado = p.get("changes").get(0).get("set");
        assertEquals(12, fijado.get("paneles_uds").asInt());
        assertFalse(fijado.has("importe_total"), "el importe solo cambia por el impacto");
        assertFalse(fijado.has("nada"));
    }

    @Test
    void cambiarEquiposReabreLasTareasHechasQueLosUsanYMarcaLasAbiertasParaRevisar() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000,\"paneles_uds\":20}");
        JsonNode todas = tareas(id);
        String usa = null;
        String otra = null;
        for (JsonNode t : todas) {
            for (JsonNode u : t.get("uses")) {
                if (u.asText().equals("paneles_uds")) {
                    if (usa == null) usa = t.get("id").asText(); else if (otra == null) otra = t.get("id").asText();
                }
            }
        }
        assertNotNull(usa);
        assertNotNull(otra);
        // Se cierra una tarea que usa los paneles (las que exigen documentación no se pueden cerrar así, por eso se prueba con una suelta)
        String suelta = otra;
        jdbc.update("UPDATE pl_tareas_proyecto SET estado = 'done', progreso = 100 WHERE id = ?", suelta);
        String cambio = "{\"id\":\"c1\",\"date\":\"2030-02-01\",\"reason\":\"Más paneles\",\"impact\":0,\"set\":{\"paneles_uds\":24}}";
        parche(id, "{\"changes\":[" + cambio + "]}").andExpect(status().isOk());
        JsonNode reabierta = null;
        for (JsonNode t : tareas(id)) {
            if (t.get("id").asText().equals(suelta)) reabierta = t;
        }
        assertEquals("doing", reabierta.get("status").asText());
        JsonNode c = contrato(id);
        assertEquals(24, c.get("current").get("paneles_uds").asInt());
        assertEquals(20, c.get("baseline").get("paneles_uds").asInt());
        assertTrue(c.get("pending_by_task").has(usa), "las abiertas quedan pendientes de revisar el cambio");
        assertEquals("c1", c.get("pending_by_task").get(usa).get(0).asText());
        assertTrue(c.get("changes").get(0).get("affected_tasks").toString().contains(suelta));
        assertEquals("paneles_uds", c.get("changes").get(0).get("touched").get(0).asText());
        // Revisarlo la quita de pendientes
        ana(patch(BASE + "/tasks/" + usa), "{\"change_acks\":[\"c1\",\"inexistente\"]}").andExpect(status().isOk());
        assertFalse(contrato(id).get("pending_by_task").has(usa));
    }

    @Test
    void guardarElMismoValorNoReabreNada() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        String suelta = null;
        for (JsonNode t : tareas(id)) {
            for (JsonNode u : t.get("uses")) {
                if (u.asText().equals("paneles_uds") && suelta == null) suelta = t.get("id").asText();
            }
        }
        String cambio = "{\"id\":\"c1\",\"date\":\"2030-02-01\",\"reason\":\"Más\",\"impact\":0,\"set\":{\"paneles_uds\":24}}";
        parche(id, "{\"changes\":[" + cambio + "]}").andExpect(status().isOk());
        jdbc.update("UPDATE pl_tareas_proyecto SET estado = 'done', progreso = 100 WHERE id = ?", suelta);
        parche(id, "{\"changes\":[" + cambio + "]}").andExpect(status().isOk());
        for (JsonNode t : tareas(id)) {
            if (t.get("id").asText().equals(suelta)) assertEquals("done", t.get("status").asText());
        }
    }

    @Test
    void elContratoDeUnProyectoSinCambiosDevuelveSuResumen() throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":8000.5}");
        JsonNode c = contrato(id);
        assertEquals(8, c.get("fields").size());
        assertEquals("importe_total", c.get("fields").get(0).get("key").asText());
        assertEquals(8000.5, c.get("summary").get("original").asDouble());
        assertEquals(0.0, c.get("summary").get("modifications").asDouble());
        assertEquals(0, c.get("changes").size());
        assertEquals(8000.5, c.get("current").get("importe_total").asDouble());
        mvc.perform(get(BASE + "/projects/nada/contract")).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Proyecto no encontrado"));
    }

    // ------------------------------------------------------------ presupuesto del proyecto

    private static final String PRESUPUESTO = "{\"client\":\"ACME\",\"moduleWp\":550,\"moduleCount\":20,\"saleRatio\":0.5,\"lines\":["
            + "{\"id\":\"l1\",\"group\":\"Paneles\",\"equipment\":\"Panel\",\"model\":\"JA 550\",\"unit\":\"ud\",\"qty\":0,\"auto\":true,\"price\":100,\"itemId\":\"%s\"},"
            + "{\"id\":\"l2\",\"group\":\"Cable\",\"equipment\":\"Cable\",\"model\":\"6mm\",\"unit\":\"m\",\"qty\":50,\"price\":2.5}]}";

    private String proyectoConPresupuesto(String itemId) throws Exception {
        String id = proyectoConPlantilla("\"baseline\":{\"importe_total\":10000}");
        parche(id, "{\"budget\":" + String.format(PRESUPUESTO, itemId) + "}").andExpect(status().isOk());
        return id;
    }

    @Test
    void sinPresupuestoElProyectoNoLoTieneConfigurado() throws Exception {
        String id = proyectoConPlantilla(null);
        mvc.perform(get(BASE + "/projects/" + id + "/budget")).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void elPresupuestoSeGuardaNormalizadoYSeCalculanSusTotales() throws Exception {
        String id = proyectoConPresupuesto("x");
        JsonNode b = leer(mvc.perform(get(BASE + "/projects/" + id + "/budget")).andExpect(status().isOk()));
        assertTrue(b.get("configured").asBoolean());
        assertEquals("ACME", b.get("client").asText());
        // Coste: 20 módulos × 100 + 50 m × 2,5 = 2125; presupuesto: 0,5 × 550 × 20 = 5500
        assertEquals(2125.0, b.get("initial").get("cost").asDouble());
        assertEquals(5500.0, b.get("initial").get("budget").asDouble());
        assertEquals(3375.0, b.get("initial").get("profit").asDouble());
        assertEquals(0.6136, b.get("initial").get("margin").asDouble(), 0.00001);
        assertEquals(2, b.get("lines").size());
        assertEquals(20.0, b.get("lines").get(0).get("current_qty").asDouble());
        assertFalse(b.get("lines").get(0).get("added").asBoolean());
    }

    @Test
    void unCambioConVariacionesAjustaElPresupuestoVigenteYSusDeltas() throws Exception {
        String id = proyectoConPresupuesto("x");
        String cambio = "{\"id\":\"c1\",\"date\":\"2030-02-01\",\"reason\":\"Más\",\"impact\":0,\"budget\":{\"module_delta\":4,\"lines\":[{\"id\":\"l2\",\"qty_delta\":10},"
                + "{\"id\":\"nueva\",\"qty_delta\":3,\"new\":{\"group\":\"Extra\",\"equipment\":\"Caja\",\"model\":\"C1\",\"unit\":\"ud\",\"price\":10}}]}}";
        parche(id, "{\"changes\":[" + cambio + "]}").andExpect(status().isOk());
        JsonNode b = leer(mvc.perform(get(BASE + "/projects/" + id + "/budget")).andExpect(status().isOk()));
        assertEquals(24, b.get("current").get("module_count").asInt());
        assertEquals(24.0, b.get("lines").get(0).get("current_qty").asDouble(), "la línea automática sigue a los módulos");
        assertEquals(60.0, b.get("lines").get(1).get("current_qty").asDouble());
        assertEquals(10.0, b.get("lines").get(1).get("delta").asDouble());
        assertTrue(b.get("lines").get(2).get("added").asBoolean());
        assertEquals(1, b.get("per_change").size());
        assertEquals(4 * 100 + 10 * 2.5 + 3 * 10, b.get("per_change").get(0).get("cost_delta").asDouble());
        assertEquals(0.5 * 550 * 4, b.get("per_change").get(0).get("budget_delta").asDouble());
    }

    @Test
    void unCambioNoPuedeDejarUnaCantidadPorDebajoDeCeroNiRefererirseALineasInexistentes() throws Exception {
        String id = proyectoConPresupuesto("x");
        parche(id, "{\"changes\":[{\"id\":\"c\",\"date\":\"2030-02-01\",\"reason\":\"x\",\"budget\":{\"lines\":[{\"id\":\"l2\",\"qty_delta\":-60}]}}]}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("El cambio 1 deja «6mm» por debajo de cero"));
        parche(id, "{\"changes\":[{\"id\":\"c\",\"date\":\"2030-02-01\",\"reason\":\"x\",\"budget\":{\"module_delta\":-21}}]}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("El cambio 1 deja el número de módulos por debajo de cero"));
        parche(id, "{\"changes\":[{\"id\":\"c\",\"date\":\"2030-02-01\",\"reason\":\"x\",\"budget\":{\"lines\":[{\"id\":\"zzz\",\"qty_delta\":1}]}}]}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("El cambio 1 se refiere a una línea que ya no existe"));
    }

    @Test
    void sinPresupuestoInicialNoSePuedenRegistrarCambiosSobreEl() throws Exception {
        String id = proyectoConPlantilla(null);
        parche(id, "{\"changes\":[{\"id\":\"c\",\"date\":\"2030-02-01\",\"reason\":\"x\",\"budget\":{\"module_delta\":1}}]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Antes de registrar cambios sobre el presupuesto hay que guardar el presupuesto inicial"));
    }

    @Test
    void conCambiosSobreElPresupuestoNoSePuedeSustituirElInicial() throws Exception {
        String id = proyectoConPresupuesto("x");
        parche(id, "{\"changes\":[{\"id\":\"c\",\"date\":\"2030-02-01\",\"reason\":\"x\",\"budget\":{\"module_delta\":1}}]}").andExpect(status().isOk());
        parche(id, "{\"budget\":" + String.format(PRESUPUESTO, "x") + "}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Ya hay cambios registrados sobre el presupuesto: no se puede sustituir el inicial"));
    }

    @Test
    void laslineasDelPresupuestoNecesitanPartidaEIdentificadorUnico() throws Exception {
        String id = proyectoConPlantilla(null);
        parche(id, "{\"budget\":{\"moduleWp\":1,\"moduleCount\":1,\"saleRatio\":1,\"lines\":[{\"id\":\"a\",\"group\":\"\",\"qty\":1,\"price\":1}]}}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("Cada línea del presupuesto necesita su partida"));
        parche(id, "{\"budget\":{\"moduleWp\":1,\"moduleCount\":1,\"saleRatio\":1,\"lines\":[{\"id\":\"a\",\"group\":\"G\"},{\"id\":\"a\",\"group\":\"G\"}]}}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("Las líneas del presupuesto necesitan un identificador único"));
        parche(id, "{\"budget\":{\"moduleWp\":1,\"moduleCount\":1,\"saleRatio\":1,\"lines\":[{\"id\":\"a\",\"group\":\"G\",\"qty\":\"x\"}]}}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("«Cantidad» debe ser un número"));
        // Quitar el presupuesto
        JsonNode p = leer(parche(id, "{\"budget\":null}").andExpect(status().isOk()));
        assertEquals(0, p.get("budget").size());
    }

    // --------------------------------------------------------------------------- material

    private String equipo(String cuerpo) throws Exception {
        return leer(ana(post(BASE + "/equipment"), cuerpo).andExpect(status().isCreated())).get("id").asText();
    }

    @Test
    void elMaterialListaLosProductosDelPresupuestoQueSonDelInventarioYNoLosServicios() throws Exception {
        String panel = equipo("{\"category\":\"modulos\",\"group\":\"modulos\",\"model\":\"JA 550\",\"unit_price\":100,\"stock\":3}");
        String id = proyectoConPresupuesto(panel);
        JsonNode m = leer(mvc.perform(get(BASE + "/projects/" + id + "/material")).andExpect(status().isOk()));
        assertTrue(m.get("configured").asBoolean());
        assertEquals(1, m.get("products").size());
        JsonNode p = m.get("products").get(0);
        assertEquals(panel, p.get("id").asText());
        assertEquals(20.0, p.get("used").asDouble());
        assertEquals(0.0, p.get("returned").asDouble());
        assertEquals(20.0, p.get("available").asDouble());
        assertEquals(3.0, p.get("stock").asDouble());
        assertEquals(0, m.get("returns").size());
    }

    @Test
    void devolverMaterialSumaAlStockYSeDeshaceRestandolo() throws Exception {
        String panel = equipo("{\"category\":\"modulos\",\"group\":\"modulos\",\"model\":\"JA 550\",\"stock\":3}");
        String id = proyectoConPresupuesto(panel);
        JsonNode m = leer(ana(post(BASE + "/projects/" + id + "/material/returns"), "{\"equipment_id\":\"" + panel + "\",\"qty\":4,\"note\":\"Sobraron\"}").andExpect(status().isOk()));
        assertEquals(4.0, m.get("products").get(0).get("returned").asDouble());
        assertEquals(16.0, m.get("products").get(0).get("available").asDouble());
        assertEquals(7.0, m.get("products").get(0).get("stock").asDouble());
        JsonNode devolucion = m.get("returns").get(0);
        assertEquals("Sobraron", devolucion.get("note").asText());
        assertEquals("Ana", devolucion.get("by").asText());
        assertEquals("JA 550", devolucion.get("label").asText());
        assertEquals(7.0, jdbc.queryForObject("SELECT stock FROM pl_equipos WHERE id = ?", Double.class, panel));
        JsonNode deshecho = leer(ana(delete(BASE + "/projects/" + id + "/material/returns/" + devolucion.get("id").asText()), null).andExpect(status().isOk()));
        assertEquals(3.0, deshecho.get("products").get(0).get("stock").asDouble());
        assertEquals(0, deshecho.get("returns").size());
    }

    @Test
    void noSePuedeDevolverMasDeLoEmpleadoNiDeAlgoQueNoLlevaElProyecto() throws Exception {
        String panel = equipo("{\"category\":\"modulos\",\"group\":\"modulos\",\"model\":\"JA 550\",\"stock\":3}");
        String otro = equipo("{\"category\":\"modulos\",\"group\":\"modulos\",\"model\":\"X1\",\"stock\":3}");
        String id = proyectoConPresupuesto(panel);
        ana(post(BASE + "/projects/" + id + "/material/returns"), "{\"equipment_id\":\"" + otro + "\",\"qty\":1}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Ese producto no se emplea en este proyecto")));
        ana(post(BASE + "/projects/" + id + "/material/returns"), "{\"equipment_id\":\"" + panel + "\",\"qty\":0}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La cantidad tiene que ser mayor que cero"));
        ana(post(BASE + "/projects/" + id + "/material/returns"), "{\"equipment_id\":\"" + panel + "\",\"qty\":21}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("No puede sobrar más de lo que se ha empleado: el proyecto lleva 20 ud, ya se han devuelto 0 y quedan 20"));
    }

    @Test
    void noSePuedeDeshacerUnaDevolucionSiElStockYaSeGasto() throws Exception {
        String panel = equipo("{\"category\":\"modulos\",\"group\":\"modulos\",\"model\":\"JA 550\",\"stock\":0}");
        String id = proyectoConPresupuesto(panel);
        JsonNode m = leer(ana(post(BASE + "/projects/" + id + "/material/returns"), "{\"equipment_id\":\"" + panel + "\",\"qty\":5}").andExpect(status().isOk()));
        ana(patch(BASE + "/equipment/" + panel), "{\"stock\":2}").andExpect(status().isOk());
        ana(delete(BASE + "/projects/" + id + "/material/returns/" + m.get("returns").get(0).get("id").asText()), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No se puede deshacer: en el almacén hay 2 ud y esta devolución sumó 5 (parte ya se ha gastado)"));
        ana(delete(BASE + "/projects/" + id + "/material/returns/inexistente"), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Devolución no encontrada"));
    }
}
