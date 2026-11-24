package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.ClientesIntranetPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de contrato de presupuestos y propuestas: estados (borrador → enviado → validado o descartado), variantes,
 * conflictos de versión, permisos de quien valida y de quien borra, y propuestas al cliente.
 */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class PresupuestosContratoTest {

    private static final String BASE = "/api/planificador";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private ClientesIntranetPort clientes;

    @BeforeEach
    void limpiar() {
        jdbc.execute("DELETE FROM pl_propuestas");
        jdbc.execute("DELETE FROM pl_presupuestos");
        jdbc.execute("DELETE FROM pl_estudios");
        jdbc.execute("DELETE FROM pl_historial_precios");
        jdbc.execute("DELETE FROM pl_equipos");
        jdbc.execute("DELETE FROM pl_proveedores");
        jdbc.execute("DELETE FROM pl_marcas");
    }

    // ------------------------------------------------------------------ ayudas

    /** Quién hace la petición: nombre, id y nivel de la intranet. */
    private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder b, String nombre, int id, int nivel) {
        return b.header("X-Aura-Usuario-Nombre", nombre).header("X-Usuario-Id", id).header("X-Usuario-Nivel", nivel);
    }

    private ResultActions enviarComo(MockHttpServletRequestBuilder b, String nombre, int id, int nivel, String cuerpo) throws Exception {
        MockHttpServletRequestBuilder r = como(b, nombre, id, nivel);
        if (cuerpo != null) {
            r.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(r);
    }

    private ResultActions ana(MockHttpServletRequestBuilder b, String cuerpo) throws Exception {
        return enviarComo(b, "Ana", 7, 3, cuerpo);
    }

    private ResultActions jefe(MockHttpServletRequestBuilder b, String cuerpo) throws Exception {
        return enviarComo(b, "Jefe", 1, 1, cuerpo);
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static String presupuesto(String nombre, String tipoCliente, String extra) {
        String cliente = "existente".equals(tipoCliente) ? "\"customer_kind\":\"existente\",\"customer_id\":5"
                : "\"customer_kind\":\"potencial\"";
        return "{\"name\":\"" + nombre + "\"," + cliente + ",\"kwp\":10.5,\"panels\":20,\"budget_total\":12345.5,"
                + "\"data\":{\"lines\":[" + extra + "]}}";
    }

    private String crear(String nombre) throws Exception {
        return leer(ana(post(BASE + "/budgets"), presupuesto(nombre, "existente", "")).andExpect(status().isCreated())).get("id").asText();
    }

    private String enviado(String nombre) throws Exception {
        String id = crear(nombre);
        ana(post(BASE + "/budgets/" + id + "/send"), null).andExpect(status().isOk());
        return id;
    }

    // ------------------------------------------------------------ alta y lectura

    @Test
    void altaDevuelveElResumenConDatosYQuedaANombreDeQuienLoGuarda() throws Exception {
        JsonNode b = leer(ana(post(BASE + "/budgets"), presupuesto("  Casa Pérez ", "existente", "")).andExpect(status().isCreated()));
        for (String clave : new String[]{"id", "name", "owner", "owner_id", "status", "offer_id", "customer_id", "customer_kind",
                "variant_name", "kwp", "panels", "budget_total", "version", "created_at", "updated_at", "updated_by",
                "proposal_status", "proposal_version", "data"}) {
            assertTrue(b.has(clave), "falta la clave " + clave);
        }
        assertEquals("Casa Pérez", b.get("name").asText());
        assertEquals("Ana", b.get("owner").asText());
        assertEquals("7", b.get("owner_id").asText());
        assertEquals("borrador", b.get("status").asText());
        assertEquals(1, b.get("version").asInt());
        assertEquals(5, b.get("customer_id").asInt());
        assertEquals(12345.5, b.get("budget_total").asDouble());
        assertTrue(b.get("created_at").asText().endsWith("+00:00"), "fecha con zona horaria");
        assertEquals("", b.get("proposal_status").asText());
        assertEquals(0, b.get("proposal_version").asInt());
    }

    @Test
    void sinCabecerasElPresupuestoQuedaSinPropietario() throws Exception {
        mvc.perform(post(BASE + "/budgets").contentType(MediaType.APPLICATION_JSON).content(presupuesto("X", "existente", "")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.owner").value("")).andExpect(jsonPath("$.owner_id").value(""));
    }

    @Test
    void elClienteEsObligatorioYCoherente() throws Exception {
        ana(post(BASE + "/budgets"), "{\"name\":\"x\",\"data\":{}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Elige el cliente del presupuesto (uno existente o un potencial)."));
        ana(post(BASE + "/budgets"), "{\"customer_kind\":\"existente\",\"data\":{}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Falta el cliente de la intranet al que se vincula el presupuesto."));
        ana(post(BASE + "/budgets"), "{\"customer_kind\":\"potencial\",\"customer_id\":3,\"data\":{}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Un cliente potencial no tiene id en la intranet."));
        ana(post(BASE + "/budgets"), "{\"customer_kind\":\"potencial\",\"data\":{\"lines\":5}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El presupuesto no tiene el formato esperado."));
        ana(post(BASE + "/budgets"), "{\"customer_kind\":\"potencial\"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void elIdLocalSeConservaPeroNoSePuedeRepetirNiSerRaro() throws Exception {
        String cuerpo = "{\"id\":\"abcd-1234-efgh\",\"customer_kind\":\"potencial\",\"data\":{}}";
        ana(post(BASE + "/budgets"), cuerpo).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("abcd-1234-efgh"));
        ana(post(BASE + "/budgets"), cuerpo).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este presupuesto ya está guardado como borrador."));
        ana(post(BASE + "/budgets"), "{\"id\":\"../x\",\"customer_kind\":\"potencial\",\"data\":{}}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Identificador de presupuesto no válido."));
    }

    @Test
    void listaSinContenidoOrdenadaPorElMasReciente() throws Exception {
        String a = crear("A");
        Thread.sleep(5);
        String b = crear("B");
        JsonNode lista = leer(mvc.perform(get(BASE + "/budgets")).andExpect(status().isOk()));
        assertEquals(2, lista.size());
        assertEquals(b, lista.get(0).get("id").asText());
        assertEquals(a, lista.get(1).get("id").asText());
        assertFalse(lista.get(0).has("data"));
        mvc.perform(get(BASE + "/budgets/" + a)).andExpect(status().isOk()).andExpect(jsonPath("$.data.lines").isArray());
        mvc.perform(get(BASE + "/budgets/no-existe")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El presupuesto ya no existe: puede que lo hayan eliminado."));
    }

    // ------------------------------------------------------------------- cambios

    @Test
    void guardarCambiosSubeLaVersionYSiOtraPersonaGuardoAntesAvisaConConflicto() throws Exception {
        String id = crear("A");
        String cuerpo = presupuesto("A2", "existente", "").replace("}}", "},\"version\":1}");
        JsonNode b = leer(ana(put(BASE + "/budgets/" + id), cuerpo).andExpect(status().isOk()));
        assertEquals(2, b.get("version").asInt());
        assertEquals("A2", b.get("name").asText());
        // El segundo guarda sobre la versión 1: ya no es la actual
        enviarComo(put(BASE + "/budgets/" + id), "Luis", 9, 3, cuerpo).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ana ha guardado una versión más reciente de este presupuesto mientras lo tenías abierto."));
        // Con force se guarda encima
        enviarComo(put(BASE + "/budgets/" + id), "Luis", 9, 3, cuerpo.replace("\"version\":1", "\"force\":true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.updated_by").value("Luis"));
        // Sin versión y sin force también es conflicto
        ana(put(BASE + "/budgets/" + id), presupuesto("A3", "existente", "")).andExpect(status().isConflict());
    }

    @Test
    void unPresupuestoEnviadoNoSeEdita() throws Exception {
        String id = enviado("A");
        ana(put(BASE + "/budgets/" + id), presupuesto("A", "existente", "").replace("}}", "},\"force\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Un presupuesto enviado no se edita: duplícalo como variante para cambiar algo."));
    }

    // ------------------------------------------------------------------- estados

    @Test
    void enviarSoloDesdeBorradorYSubeLaVersion() throws Exception {
        String id = crear("A");
        ana(post(BASE + "/budgets/" + id + "/send"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("enviado")).andExpect(jsonPath("$.version").value(2));
        ana(post(BASE + "/budgets/" + id + "/send"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El presupuesto ya está enviado."));
    }

    @Test
    void unPresupuestoConProductosDeEjemploNoSeEnvia() throws Exception {
        jdbc.update("INSERT INTO pl_equipos (id, posicion, categoria, partida, marca, modelo, proveedor, unidad, precio_unitario, "
                + "por_modulo, cantidad_defecto, atributos, notas, muestra, stock, stock_minimo, ficha_tecnica, ofertas) "
                + "VALUES ('eq-1', 1, 'contadores', 'electricidad', 'ALFA', 'ALFA C1', '', 'ud', 10, FALSE, 1, '{}' FORMAT JSON, '', TRUE, 0, 0, '', '[]' FORMAT JSON)");
        String id = leer(ana(post(BASE + "/budgets"), presupuesto("A", "existente", "{\"itemId\":\"eq-1\"}")).andExpect(status().isCreated())).get("id").asText();
        ana(post(BASE + "/budgets/" + id + "/send"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Hay productos de ejemplo, con precios inventados (ALFA C1): cámbialos o pon su "
                        + "precio real en el inventario antes de enviarlo."));
    }

    @Test
    void variantesComparteOfertaCopianLosDatosYNoSeDuplicaUnDescartado() throws Exception {
        String id = crear("A");
        JsonNode v = leer(ana(post(BASE + "/budgets/" + id + "/variant"), "{\"name\":\"Con batería\"}").andExpect(status().isCreated()));
        assertEquals("borrador", v.get("status").asText());
        assertEquals("Con batería", v.get("variant_name").asText());
        assertFalse(v.get("offer_id").asText().isEmpty());
        assertNotEquals(id, v.get("id").asText());
        assertEquals(12345.5, v.get("budget_total").asDouble());
        JsonNode original = leer(mvc.perform(get(BASE + "/budgets/" + id)));
        assertEquals(v.get("offer_id").asText(), original.get("offer_id").asText(), "el original pasa a tener la misma oferta");
        // Un nombre vacío y sin cuerpo también valen
        ana(post(BASE + "/budgets/" + id + "/variant"), null).andExpect(status().isCreated()).andExpect(jsonPath("$.variant_name").value(""));
        // Cambiar el nombre de la variante vale en cualquier estado
        ana(post(BASE + "/budgets/" + id + "/variant-name"), "{\"name\":\"" + "x".repeat(80) + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.variant_name").value("x".repeat(60)));
    }

    private String estudio(int clienteId, String proyecto) throws Exception {
        String cuerpo = "{\"customer\":{\"kind\":\"existente\",\"id\":" + clienteId + ",\"name\":\"Pepe\"},\"proyecto\":\"" + proyecto + "\"}";
        return leer(mvc.perform(post(BASE + "/studies").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())).get("id").asText();
    }

    @Test
    void soloSeAsocianPresupuestoYEstudioDelMismoClienteYProyecto() throws Exception {
        String id = crear("Pepe · Nave 2");
        assertEquals("", leer(mvc.perform(get(BASE + "/budgets/" + id))).get("study_id").asText(), "nace sin estudio");
        String igual = estudio(5, "nave  2");
        String otroProyecto = estudio(5, "Cubierta");
        String otroCliente = estudio(6, "Nave 2");

        ana(post(BASE + "/budgets/" + id + "/study"), "{\"study_id\":\"" + otroProyecto + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("otro proyecto")));
        ana(post(BASE + "/budgets/" + id + "/study"), "{\"study_id\":\"" + otroCliente + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("otro cliente")));
        ana(post(BASE + "/budgets/" + id + "/study"), "{\"study_id\":\"no-existe\"}").andExpect(status().isNotFound());

        ana(post(BASE + "/budgets/" + id + "/study"), "{\"study_id\":\"" + igual + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.study_id").value(igual));
        // Las variantes nuevas heredan el estudio
        assertEquals(igual, leer(ana(post(BASE + "/budgets/" + id + "/variant"), null).andExpect(status().isCreated())).get("study_id").asText());

        // Asociado, no se cambia el proyecto sin quitar antes la asociación
        JsonNode actual = leer(mvc.perform(get(BASE + "/budgets/" + id)));
        ana(put(BASE + "/budgets/" + id), presupuesto("Pepe · Otra cosa", "existente", "").replace("{\"name\"",
                "{\"version\":" + actual.get("version").asInt() + ",\"name\"")).andExpect(status().isConflict());

        // Quitar la asociación (id vacío) y, si se borra el estudio, los presupuestos se quedan sin él
        ana(post(BASE + "/budgets/" + id + "/study"), "{\"study_id\":\"\"}").andExpect(status().isOk()).andExpect(jsonPath("$.study_id").value(""));
        ana(post(BASE + "/budgets/" + id + "/study"), "{\"study_id\":\"" + igual + "\"}").andExpect(status().isOk());
        mvc.perform(delete(BASE + "/studies/" + igual)).andExpect(status().isNoContent());
        assertEquals("", leer(mvc.perform(get(BASE + "/budgets/" + id))).get("study_id").asText());
    }

    @Test
    void unPresupuestoNuevoPuedeNacerAsociadoAUnEstudioDeSuMismoClienteYProyecto() throws Exception {
        String estudio = estudio(5, "Nave 2");
        String cuerpo = presupuesto("Pepe · Nave 2", "existente", "");
        ana(post(BASE + "/budgets"), cuerpo.replace("{\"name\"", "{\"study_id\":\"" + estudio + "\",\"name\""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.study_id").value(estudio));
        // De otro proyecto, o de un estudio que no existe, no se crea
        ana(post(BASE + "/budgets"), presupuesto("Pepe · Otra", "existente", "").replace("{\"name\"", "{\"study_id\":\"" + estudio + "\",\"name\""))
                .andExpect(status().isConflict());
        ana(post(BASE + "/budgets"), cuerpo.replace("{\"name\"", "{\"study_id\":\"no-existe\",\"name\"")).andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pl_presupuestos", Integer.class));
    }

    @Test
    void losPresupuestosDelMismoClienteYProyectoSonDeLaMismaOfertaSinCrearVariantes() throws Exception {
        String a = crear("Pepe · Nave 2");
        assertEquals("", leer(mvc.perform(get(BASE + "/budgets/" + a))).get("offer_id").asText(), "solo no tiene oferta");
        // Mismo cliente y mismo proyecto (sin mirar mayúsculas, tildes, espacios ni «v2»): entra en su oferta y se la da al primero
        JsonNode b = leer(ana(post(BASE + "/budgets"), presupuesto("Pepe · nave  2 v2", "existente", "")).andExpect(status().isCreated()));
        String oferta = b.get("offer_id").asText();
        assertFalse(oferta.isEmpty());
        assertEquals(oferta, leer(mvc.perform(get(BASE + "/budgets/" + a))).get("offer_id").asText());
        // Otro proyecto del mismo cliente, o el mismo proyecto de otro cliente, o sin proyecto: cada uno por su lado
        assertEquals("", leer(ana(post(BASE + "/budgets"), presupuesto("Pepe · Nave 3", "existente", "")).andExpect(status().isCreated())).get("offer_id").asText());
        assertEquals("", leer(ana(post(BASE + "/budgets"), presupuesto("Pepe · Nave 2", "potencial", "")).andExpect(status().isCreated())).get("offer_id").asText());
        String sinProyecto = crear("Pepe");
        crear("Pepe");
        assertEquals("", leer(mvc.perform(get(BASE + "/budgets/" + sinProyecto))).get("offer_id").asText());
        // Guardar con otro proyecto lo saca de la oferta; guardarlo con el de otra oferta lo mete en ella
        String c = leer(ana(post(BASE + "/budgets"), presupuesto("Pepe · Nave 2 ", "existente", "")).andExpect(status().isCreated())).get("id").asText();
        String cuerpo = presupuesto("Pepe · Nave 9", "existente", "").replace("}}", "},\"version\":1}");
        assertEquals("", leer(ana(put(BASE + "/budgets/" + c), cuerpo).andExpect(status().isOk())).get("offer_id").asText());
        assertEquals(oferta, leer(mvc.perform(get(BASE + "/budgets/" + a))).get("offer_id").asText(), "los demás siguen juntos");
        String d = crear("Pepe · Nave 9b");
        String volver = presupuesto("Pepe · Nave 2", "existente", "").replace("}}", "},\"version\":1}");
        assertEquals(oferta, leer(ana(put(BASE + "/budgets/" + d), volver).andExpect(status().isOk())).get("offer_id").asText());
    }

    @Test
    void cualquieraApruebaElPresupuestoYSeDescartaElRestoDeLaOferta() throws Exception {
        String a = enviado("A");
        String b = leer(ana(post(BASE + "/budgets/" + a + "/variant"), null).andExpect(status().isCreated())).get("id").asText();
        String c = leer(ana(post(BASE + "/budgets/" + a + "/variant"), null).andExpect(status().isCreated())).get("id").asText();
        ana(post(BASE + "/budgets/" + b + "/send"), null).andExpect(status().isOk());
        // c sigue en borrador
        ana(post(BASE + "/budgets/" + a + "/validate"), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("validado"));
        assertEquals("descartado", leer(mvc.perform(get(BASE + "/budgets/" + b))).get("status").asText());
        assertEquals("descartado", leer(mvc.perform(get(BASE + "/budgets/" + c))).get("status").asText());
        // Otra validación en la misma oferta ya no cabe, y un descartado no se duplica
        ana(post(BASE + "/budgets/" + b + "/validate"), null).andExpect(status().isConflict());
        ana(post(BASE + "/budgets/" + b + "/variant"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Un presupuesto descartado no se duplica: duplica el validado o uno enviado."));
    }

    @Test
    void soloSeValidaUnPresupuestoEnviado() throws Exception {
        String id = crear("A");
        jefe(post(BASE + "/budgets/" + id + "/validate"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo se aprueba un presupuesto enviado al cliente."));
    }

    @Test
    void validarUnClientePotencialNoLoDaDeAltaEnLaIntranet() throws Exception {
        // Desconectado como en el Planificador actual: sigue siendo potencial y no se pide el CIF ni se llama a la intranet
        when(clientes.crear(any(), any(), any(), any())).thenThrow(new IllegalStateException("no debería llamarse"));
        String cuerpo = "{\"name\":\"Casa\",\"customer_kind\":\"potencial\",\"data\":{\"customer\":{\"name\":\"Casa Pérez\",\"phone\":\"600\"},\"lines\":[]}}";
        String a = leer(ana(post(BASE + "/budgets"), cuerpo).andExpect(status().isCreated())).get("id").asText();
        ana(post(BASE + "/budgets/" + a + "/send"), null).andExpect(status().isOk());
        String b = leer(ana(post(BASE + "/budgets/" + a + "/variant"), null).andExpect(status().isCreated())).get("id").asText();
        jefe(post(BASE + "/budgets/" + a + "/validate"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("validado")).andExpect(jsonPath("$.customer_kind").value("potencial"));
        JsonNode variante = leer(mvc.perform(get(BASE + "/budgets/" + b)));
        assertEquals("potencial", variante.get("customer_kind").asText());
        assertEquals("descartado", variante.get("status").asText());
        org.mockito.Mockito.verify(clientes, org.mockito.Mockito.never()).crear(any(), any(), any(), any());
    }

    @Test
    void quitarLaValidacionDevuelveLaOfertaAEnviadoSalvoConPropuestaEnviada() throws Exception {
        String a = enviado("A");
        String b = leer(ana(post(BASE + "/budgets/" + a + "/variant"), null).andExpect(status().isCreated())).get("id").asText();
        ana(post(BASE + "/budgets/" + b + "/send"), null).andExpect(status().isOk());
        ana(post(BASE + "/budgets/" + a + "/unvalidate"), null).andExpect(status().isForbidden());
        jefe(post(BASE + "/budgets/" + a + "/unvalidate"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El presupuesto no está aprobado."));
        jefe(post(BASE + "/budgets/" + a + "/validate"), null).andExpect(status().isOk());
        // Un borrador de propuesta se borra al quitar la validación
        ana(post(BASE + "/budgets/" + a + "/proposals"), null).andExpect(status().isCreated());
        jefe(post(BASE + "/budgets/" + a + "/unvalidate"), null).andExpect(status().isOk());
        assertEquals("enviado", leer(mvc.perform(get(BASE + "/budgets/" + a))).get("status").asText());
        assertEquals("enviado", leer(mvc.perform(get(BASE + "/budgets/" + b))).get("status").asText());
        assertEquals(0, leer(mvc.perform(get(BASE + "/budgets/" + a + "/proposals"))).size());
        // Con una propuesta enviada ya no se puede
        jefe(post(BASE + "/budgets/" + a + "/validate"), null).andExpect(status().isOk());
        String p = leer(ana(post(BASE + "/budgets/" + a + "/proposals"), null).andExpect(status().isCreated())).get("id").asText();
        ana(post(BASE + "/proposals/" + p + "/send"), null).andExpect(status().isOk());
        jefe(post(BASE + "/budgets/" + a + "/unvalidate"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este presupuesto tiene una propuesta entregada o validada: no se puede quitar su aprobación."));
    }

    @Test
    void pendientesDeValidarCuentaSoloLasPropuestasEntregadasListasParaValidar() throws Exception {
        String c = enviado("C");
        ana(get(BASE + "/budgets/pending-validation"), null).andExpect(status().isOk())
                .andExpect(content().json("{\"validator\":false,\"count\":0,\"to_validate\":0,\"proposals_sent\":0}", true));
        ana(post(BASE + "/budgets/" + c + "/validate"), null).andExpect(status().isOk());
        String p = leer(ana(post(BASE + "/budgets/" + c + "/proposals"), null).andExpect(status().isCreated())).get("id").asText();
        ana(post(BASE + "/proposals/" + p + "/send"), null).andExpect(status().isOk());
        // Entregada, pero sin estudio aceptado: todavía no se puede validar
        jefe(get(BASE + "/budgets/pending-validation"), null)
                .andExpect(content().json("{\"validator\":true,\"count\":0,\"to_validate\":0,\"proposals_sent\":0}", true));
        aceptarEstudioDe(c);
        jefe(get(BASE + "/budgets/pending-validation"), null)
                .andExpect(content().json("{\"validator\":true,\"count\":1,\"to_validate\":1,\"proposals_sent\":1}", true));
    }

    @Test
    void alEntregarLaPropuestaElEstudioPasaAPteClienteYUnoAceptadoNoSeToca() throws Exception {
        String c = enviado("C");
        String estudio = estudio(5, "");
        ana(post(BASE + "/budgets/" + c + "/study"), "{\"study_id\":\"" + estudio + "\"}").andExpect(status().isOk());
        ana(post(BASE + "/budgets/" + c + "/validate"), null).andExpect(status().isOk());
        String p = leer(ana(post(BASE + "/budgets/" + c + "/proposals"), null).andExpect(status().isCreated())).get("id").asText();
        assertEquals("en_estudio", leer(mvc.perform(get(BASE + "/studies/" + estudio))).get("estado").asText());
        int version = leer(mvc.perform(get(BASE + "/studies/" + estudio))).get("version").asInt();
        ana(post(BASE + "/proposals/" + p + "/send"), null).andExpect(status().isOk());
        JsonNode despues = leer(mvc.perform(get(BASE + "/studies/" + estudio)));
        assertEquals("pte_cliente", despues.get("estado").asText());
        assertEquals(version + 1, despues.get("version").asInt());

        // Un estudio ya aceptado (lo decide una persona) se queda como está
        String d = enviado("D");
        String aceptado = aceptarEstudioDe(d);
        ana(post(BASE + "/budgets/" + d + "/validate"), null).andExpect(status().isOk());
        String q = leer(ana(post(BASE + "/budgets/" + d + "/proposals"), null).andExpect(status().isCreated())).get("id").asText();
        ana(post(BASE + "/proposals/" + q + "/send"), null).andExpect(status().isOk());
        assertEquals("aceptado", leer(mvc.perform(get(BASE + "/studies/" + aceptado))).get("estado").asText());

        // Uno rechazado por el cliente no se acepta solo: la validación se bloquea hasta que una persona lo reabra
        String e2 = enviado("E");
        String rechazado = estudio(5, "");
        ana(post(BASE + "/budgets/" + e2 + "/study"), "{\"study_id\":\"" + rechazado + "\"}").andExpect(status().isOk());
        ana(post(BASE + "/budgets/" + e2 + "/validate"), null).andExpect(status().isOk());
        String r = leer(ana(post(BASE + "/budgets/" + e2 + "/proposals"), null).andExpect(status().isCreated())).get("id").asText();
        ana(post(BASE + "/proposals/" + r + "/send"), null).andExpect(status().isOk());
        ana(put(BASE + "/studies/" + rechazado), "{\"estado\":\"rechazado\"}").andExpect(status().isOk());
        jefe(post(BASE + "/proposals/" + r + "/approve"), null).andExpect(status().isConflict());
        assertEquals("rechazado", leer(mvc.perform(get(BASE + "/studies/" + rechazado))).get("estado").asText());
    }

    // -------------------------------------------------------------------- borrado

    @Test
    void soloLoBorraQuienLoCreoOUnNivel1() throws Exception {
        String id = crear("A");
        enviarComo(delete(BASE + "/budgets/" + id), "Luis", 9, 3, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo Ana puede eliminar este borrador."));
        ana(delete(BASE + "/budgets/" + id), null).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/budgets/" + id)).andExpect(status().isNotFound());
        String otro = crear("B");
        enviarComo(delete(BASE + "/budgets/" + otro), "Admin", 2, 1, null).andExpect(status().isNoContent());
        mvc.perform(delete(BASE + "/budgets/no-existe")).andExpect(status().isNoContent());
    }

    @Test
    void siNoSeSupoQuienLoCreoCualquieraLoBorra() throws Exception {
        String id = leer(mvc.perform(post(BASE + "/budgets").contentType(MediaType.APPLICATION_JSON).content(presupuesto("X", "existente", "")))).get("id").asText();
        enviarComo(delete(BASE + "/budgets/" + id), "Luis", 9, 3, null).andExpect(status().isNoContent());
    }

    @Test
    void unProductoUsadoPorUnPresupuestoNoSePuedeEliminarDelInventario() throws Exception {
        jdbc.update("INSERT INTO pl_equipos (id, posicion, categoria, partida, marca, modelo, proveedor, unidad, precio_unitario, "
                + "por_modulo, cantidad_defecto, atributos, notas, muestra, stock, stock_minimo, ficha_tecnica, ofertas) "
                + "VALUES ('eq-9', 1, 'contadores', 'electricidad', '', 'C9', '', 'ud', 10, FALSE, 1, '{}' FORMAT JSON, '', FALSE, 0, 0, '', '[]' FORMAT JSON)");
        ana(post(BASE + "/budgets"), presupuesto("Casa Pérez", "existente", "{\"itemId\":\"eq-9\"}")).andExpect(status().isCreated());
        mvc.perform(delete(BASE + "/equipment/eq-9")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este producto está en 1 presupuesto(s) guardado(s) (Casa Pérez): no se puede eliminar mientras se use."));
    }

    // ----------------------------------------------------------------- propuestas

    /** Crea un estudio del mismo cliente y proyecto que el presupuesto, lo asocia y lo marca como aceptado. */
    private String aceptarEstudioDe(String presupuestoId) throws Exception {
        String estudio = estudio(5, "");
        ana(post(BASE + "/budgets/" + presupuestoId + "/study"), "{\"study_id\":\"" + estudio + "\"}").andExpect(status().isOk());
        ana(put(BASE + "/studies/" + estudio), "{\"estado\":\"aceptado\"}").andExpect(status().isOk());
        return estudio;
    }

    private String validado(String nombre) throws Exception {
        String id = enviado(nombre);
        ana(post(BASE + "/budgets/" + id + "/validate"), null).andExpect(status().isOk());
        return id;
    }

    @Test
    void laPropuestaSoloSeCreaDeUnPresupuestoValidadoYUnaEnCursoALaVez() throws Exception {
        String id = crear("A");
        ana(post(BASE + "/budgets/" + id + "/proposals"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo se hace la propuesta al cliente de un presupuesto validado."));
        ana(post(BASE + "/budgets/" + id + "/send"), null);
        ana(post(BASE + "/budgets/" + id + "/validate"), null).andExpect(status().isOk());
        JsonNode p = leer(ana(post(BASE + "/budgets/" + id + "/proposals"), null).andExpect(status().isCreated()));
        assertEquals(1, p.get("version").asInt());
        assertEquals("borrador", p.get("status").asText());
        assertEquals("Ana", p.get("created_by").asText());
        assertTrue(p.get("sent_at").isNull());
        ana(post(BASE + "/budgets/" + id + "/proposals"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya hay una propuesta en curso: termínala antes de crear otra."));
    }

    @Test
    void cicloDeLaPropuestaEnviarRechazarCrearOtraYAprobar() throws Exception {
        String id = validado("A");
        String p1 = leer(ana(post(BASE + "/budgets/" + id + "/proposals"), null)).get("id").asText();
        // Validar o anular es cosa de quien valida
        ana(post(BASE + "/proposals/" + p1 + "/approve"), null).andExpect(status().isForbidden());
        jefe(post(BASE + "/proposals/" + p1 + "/approve"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo se valida o se anula una propuesta entregada."));
        ana(post(BASE + "/proposals/" + p1 + "/send"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("enviada")).andExpect(jsonPath("$.sent_by").value("Ana"));
        ana(post(BASE + "/proposals/" + p1 + "/send"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La propuesta ya está enviada."));
        ana(post(BASE + "/proposals/" + p1 + "/reject"), "{\"note\":\"  Muy cara  \"}").andExpect(status().isForbidden());
        jefe(post(BASE + "/proposals/" + p1 + "/reject"), "{\"note\":\"  Muy cara  \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rechazada")).andExpect(jsonPath("$.note").value("Muy cara"))
                .andExpect(jsonPath("$.decided_by").value("Jefe"));
        // La rechazada no bloquea: se crea la versión 2, que parte del logo de la anterior
        String logo = "data:image/png;base64,iVBORw0KGgo=";
        ana(put(BASE + "/proposals/" + p1 + "/logo"), "{\"logo\":\"" + logo + "\"}").andExpect(status().isOk()).andExpect(jsonPath("$.client_logo").value(logo));
        JsonNode p2 = leer(ana(post(BASE + "/budgets/" + id + "/proposals"), null).andExpect(status().isCreated()));
        assertEquals(2, p2.get("version").asInt());
        assertEquals(logo, p2.get("client_logo").asText());
        ana(post(BASE + "/proposals/" + p2.get("id").asText() + "/send"), null).andExpect(status().isOk());
        // Hace falta un estudio asociado
        jefe(post(BASE + "/proposals/" + p2.get("id").asText() + "/approve"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Para validar la propuesta, el presupuesto tiene que estar aprobado y tener un estudio asociado que no esté rechazado ni cancelado."));
        // Con estudio (sin aceptar), al validar la propuesta el estudio queda aceptado solo
        String estudioDelPresupuesto = estudio(5, "");
        ana(post(BASE + "/budgets/" + id + "/study"), "{\"study_id\":\"" + estudioDelPresupuesto + "\"}").andExpect(status().isOk());
        jefe(post(BASE + "/proposals/" + p2.get("id").asText() + "/approve"), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("aprobada"));
        assertEquals("aceptado", leer(mvc.perform(get(BASE + "/studies/" + estudioDelPresupuesto))).get("estado").asText());
        ana(post(BASE + "/budgets/" + id + "/proposals"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este presupuesto ya tiene una propuesta validada."));
        // El listado va de la más reciente a la más antigua y el presupuesto enseña el estado de la última
        JsonNode lista = leer(mvc.perform(get(BASE + "/budgets/" + id + "/proposals")).andExpect(status().isOk()));
        assertEquals(2, lista.get(0).get("version").asInt());
        assertEquals(1, lista.get(1).get("version").asInt());
        assertFalse(lista.get(0).has("missing_warranties"));
        assertTrue(lista.get(0).get("has_logo").asBoolean()); // la versión 2 parte del logo de la anterior
        JsonNode b = leer(mvc.perform(get(BASE + "/budgets/" + id)));
        assertEquals("aprobada", b.get("proposal_status").asText());
        assertEquals(2, b.get("proposal_version").asInt());
    }

    @Test
    void unEstudioAceptadoQuedaBloqueadoYSoloSeReabreSinPropuestaEntregada() throws Exception {
        String id = validado("A");
        String estudio = aceptarEstudioDe(id);
        ana(put(BASE + "/studies/" + estudio), "{\"observaciones\":\"x\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Un estudio aceptado está bloqueado: cambia su estado para poder modificarlo."));
        ana(delete(BASE + "/studies/" + estudio), null).andExpect(status().isConflict());
        // El presupuesto aprobado tampoco cambia de estudio
        ana(post(BASE + "/budgets/" + id + "/study"), "{\"study_id\":\"\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Un presupuesto aprobado está bloqueado: no se cambia su estudio."));
        // Con una propuesta entregada no se reabre; sin ella, sí
        String p = leer(ana(post(BASE + "/budgets/" + id + "/proposals"), null)).get("id").asText();
        ana(post(BASE + "/proposals/" + p + "/send"), null).andExpect(status().isOk());
        ana(put(BASE + "/studies/" + estudio), "{\"estado\":\"pte_cliente\"}").andExpect(status().isConflict());
        jefe(post(BASE + "/proposals/" + p + "/reject"), null).andExpect(status().isOk());
        ana(put(BASE + "/studies/" + estudio), "{\"estado\":\"pte_cliente\"}").andExpect(status().isOk());
        ana(put(BASE + "/studies/" + estudio), "{\"observaciones\":\"x\"}").andExpect(status().isOk());
    }

    @Test
    void elLogoDelClienteDeLaPropuestaSeValida() throws Exception {
        String id = validado("A");
        String p = leer(ana(post(BASE + "/budgets/" + id + "/proposals"), null)).get("id").asText();
        ana(put(BASE + "/proposals/" + p + "/logo"), "{\"logo\":\"data:text/html;base64,AAAA\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El logo tiene que ser una imagen PNG, JPG, WebP o SVG de menos de 1,5 MB"));
        ana(delete(BASE + "/proposals/" + p + "/logo"), null).andExpect(status().isOk()).andExpect(jsonPath("$.client_logo").value(""));
    }

    @Test
    void soloSeEliminaUnaPropuestaEnBorradorYSoloElAutorOUnNivel1() throws Exception {
        String id = validado("A");
        String p = leer(ana(post(BASE + "/budgets/" + id + "/proposals"), null)).get("id").asText();
        enviarComo(delete(BASE + "/proposals/" + p), "Luis", 9, 3, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo Ana puede eliminar esta propuesta."));
        ana(delete(BASE + "/proposals/" + p), null).andExpect(status().isNoContent());
        String q = leer(ana(post(BASE + "/budgets/" + id + "/proposals"), null)).get("id").asText();
        ana(post(BASE + "/proposals/" + q + "/send"), null);
        ana(delete(BASE + "/proposals/" + q), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo se elimina una propuesta que sigue en borrador."));
        mvc.perform(delete(BASE + "/proposals/no-existe")).andExpect(status().isNoContent());
        mvc.perform(post(BASE + "/proposals/no-existe/send")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La propuesta ya no existe: puede que la hayan eliminado."));
    }

    @Test
    void laListaDePropuestasYaNoBloqueaLaDescargaPorGarantiasQueFaltan() throws Exception {
        jdbc.update("INSERT INTO pl_equipos (id, posicion, categoria, partida, marca, modelo, proveedor, unidad, precio_unitario, "
                + "por_modulo, cantidad_defecto, atributos, notas, muestra, stock, stock_minimo, ficha_tecnica, ofertas) "
                + "VALUES ('m1', 1, 'modulos', 'modulos', 'AIKO', '615', '', 'ud', 1, FALSE, 1, '{\"garantia_anos\":\"12 años\"}' FORMAT JSON, '', FALSE, 0, 0, '', '[]' FORMAT JSON),"
                + "('i1', 2, 'inversores', 'electricidad', 'HUAWEI', 'SUN2000', '', 'ud', 1, FALSE, 1, '{}' FORMAT JSON, '', FALSE, 0, 0, '', '[]' FORMAT JSON)");
        String cuerpo = presupuesto("A", "existente", "{\"itemId\":\"m1\"},{\"itemId\":\"i1\"}");
        String id = leer(ana(post(BASE + "/budgets"), cuerpo)).get("id").asText();
        ana(post(BASE + "/budgets/" + id + "/send"), null).andExpect(status().isOk());
        jefe(post(BASE + "/budgets/" + id + "/validate"), null).andExpect(status().isOk());
        ana(post(BASE + "/budgets/" + id + "/proposals"), null).andExpect(status().isCreated());
        // La regla de garantías está desactivada: la lista no avisa de nada y la presentación se descarga igualmente
        JsonNode primera = leer(mvc.perform(get(BASE + "/budgets/" + id + "/proposals"))).get(0);
        assertFalse(primera.has("missing_warranties"));
        String p = primera.get("id").asText();
        // Sin logo del cliente (ni en la propuesta ni en el presupuesto) no se descarga
        mvc.perform(get(BASE + "/proposals/" + p + "/html")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Sube el logo de la empresa del cliente para descargar la propuesta: sale en la presentación."));
        ana(put(BASE + "/proposals/" + p + "/logo"), "{\"logo\":\"data:image/png;base64,iVBORw0KGgo=\"}").andExpect(status().isOk());
        mvc.perform(get(BASE + "/proposals/" + p + "/html")).andExpect(status().isOk());
    }

    // -------------------------------------------------------------------- clientes

    @Test
    void sinConexionConLaIntranetLaBusquedaDeClientesLoDiceYNoDevuelveNada() throws Exception {
        when(clientes.habilitado()).thenReturn(false);
        mvc.perform(get(BASE + "/clients?q=casa")).andExpect(status().isOk())
                .andExpect(content().json("{\"enabled\":false,\"items\":[]}", true));
    }
}
