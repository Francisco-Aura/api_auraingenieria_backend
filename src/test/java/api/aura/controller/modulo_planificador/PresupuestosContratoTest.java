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

    @Test
    void soloQuienValidaPuedeValidarYSeDescartaElRestoDeLaOferta() throws Exception {
        String a = enviado("A");
        String b = leer(ana(post(BASE + "/budgets/" + a + "/variant"), null).andExpect(status().isCreated())).get("id").asText();
        String c = leer(ana(post(BASE + "/budgets/" + a + "/variant"), null).andExpect(status().isCreated())).get("id").asText();
        ana(post(BASE + "/budgets/" + b + "/send"), null).andExpect(status().isOk());
        // c sigue en borrador
        ana(post(BASE + "/budgets/" + a + "/validate"), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo quien valida los presupuestos puede hacerlo."));
        jefe(post(BASE + "/budgets/" + a + "/validate"), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("validado"));
        assertEquals("descartado", leer(mvc.perform(get(BASE + "/budgets/" + b))).get("status").asText());
        assertEquals("descartado", leer(mvc.perform(get(BASE + "/budgets/" + c))).get("status").asText());
        // Otra validación en la misma oferta ya no cabe, y un descartado no se duplica
        jefe(post(BASE + "/budgets/" + b + "/validate"), null).andExpect(status().isConflict());
        ana(post(BASE + "/budgets/" + b + "/variant"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Un presupuesto descartado no se duplica: duplica el validado o uno enviado."));
    }

    @Test
    void soloSeValidaUnPresupuestoEnviado() throws Exception {
        String id = crear("A");
        jefe(post(BASE + "/budgets/" + id + "/validate"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo se valida un presupuesto enviado al cliente."));
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
                .andExpect(jsonPath("$.detail").value("El presupuesto no está validado."));
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
                .andExpect(jsonPath("$.detail").value("Este presupuesto tiene una propuesta enviada o aprobada por el cliente: no se puede quitar su validación."));
    }

    @Test
    void pendientesDeValidarCuentaOfertasYPropuestasEnviadasYSoloAQuienValida() throws Exception {
        String a = enviado("A");
        ana(post(BASE + "/budgets/" + a + "/variant"), null).andExpect(status().isCreated());
        String b = leer(ana(post(BASE + "/budgets/" + a + "/variant"), null)).get("id").asText();
        ana(post(BASE + "/budgets/" + b + "/send"), null).andExpect(status().isOk());
        String c = enviado("C");
        ana(get(BASE + "/budgets/pending-validation"), null).andExpect(status().isOk())
                .andExpect(content().json("{\"validator\":false,\"count\":0,\"to_validate\":0,\"proposals_sent\":0}", true));
        // a y b son una oferta (cuenta una vez) y c otra
        jefe(get(BASE + "/budgets/pending-validation"), null)
                .andExpect(content().json("{\"validator\":true,\"count\":2,\"to_validate\":2,\"proposals_sent\":0}", true));
        jefe(post(BASE + "/budgets/" + c + "/validate"), null).andExpect(status().isOk());
        String p = leer(ana(post(BASE + "/budgets/" + c + "/proposals"), null).andExpect(status().isCreated())).get("id").asText();
        ana(post(BASE + "/proposals/" + p + "/send"), null).andExpect(status().isOk());
        jefe(get(BASE + "/budgets/pending-validation"), null)
                .andExpect(content().json("{\"validator\":true,\"count\":2,\"to_validate\":1,\"proposals_sent\":1}", true));
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

    private String validado(String nombre) throws Exception {
        String id = enviado(nombre);
        jefe(post(BASE + "/budgets/" + id + "/validate"), null).andExpect(status().isOk());
        return id;
    }

    @Test
    void laPropuestaSoloSeCreaDeUnPresupuestoValidadoYUnaEnCursoALaVez() throws Exception {
        String id = crear("A");
        ana(post(BASE + "/budgets/" + id + "/proposals"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo se hace la propuesta al cliente de un presupuesto validado."));
        ana(post(BASE + "/budgets/" + id + "/send"), null);
        jefe(post(BASE + "/budgets/" + id + "/validate"), null).andExpect(status().isOk());
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
        ana(post(BASE + "/proposals/" + p1 + "/approve"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo contesta el cliente a una propuesta enviada."));
        ana(post(BASE + "/proposals/" + p1 + "/send"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("enviada")).andExpect(jsonPath("$.sent_by").value("Ana"));
        ana(post(BASE + "/proposals/" + p1 + "/send"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La propuesta ya está enviada."));
        ana(post(BASE + "/proposals/" + p1 + "/reject"), "{\"note\":\"  Muy cara  \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rechazada")).andExpect(jsonPath("$.note").value("Muy cara"))
                .andExpect(jsonPath("$.decided_by").value("Ana"));
        // La rechazada no bloquea: se crea la versión 2, que parte del logo de la anterior
        String logo = "data:image/png;base64,iVBORw0KGgo=";
        ana(put(BASE + "/proposals/" + p1 + "/logo"), "{\"logo\":\"" + logo + "\"}").andExpect(status().isOk()).andExpect(jsonPath("$.client_logo").value(logo));
        JsonNode p2 = leer(ana(post(BASE + "/budgets/" + id + "/proposals"), null).andExpect(status().isCreated()));
        assertEquals(2, p2.get("version").asInt());
        assertEquals(logo, p2.get("client_logo").asText());
        ana(post(BASE + "/proposals/" + p2.get("id").asText() + "/send"), null).andExpect(status().isOk());
        ana(post(BASE + "/proposals/" + p2.get("id").asText() + "/approve"), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("aprobada"));
        ana(post(BASE + "/budgets/" + id + "/proposals"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El cliente ya aprobó una propuesta de este presupuesto."));
        // El listado va de la más reciente a la más antigua y el presupuesto enseña el estado de la última
        JsonNode lista = leer(mvc.perform(get(BASE + "/budgets/" + id + "/proposals")).andExpect(status().isOk()));
        assertEquals(2, lista.get(0).get("version").asInt());
        assertEquals(1, lista.get(1).get("version").asInt());
        assertFalse(lista.get(0).has("missing_warranties"));
        JsonNode b = leer(mvc.perform(get(BASE + "/budgets/" + id)));
        assertEquals("aprobada", b.get("proposal_status").asText());
        assertEquals(2, b.get("proposal_version").asInt());
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
        mvc.perform(get(BASE + "/proposals/" + primera.get("id").asText() + "/html")).andExpect(status().isOk());
    }

    // -------------------------------------------------------------------- clientes

    @Test
    void sinConexionConLaIntranetLaBusquedaDeClientesLoDiceYNoDevuelveNada() throws Exception {
        when(clientes.habilitado()).thenReturn(false);
        mvc.perform(get(BASE + "/clients?q=casa")).andExpect(status().isOk())
                .andExpect(content().json("{\"enabled\":false,\"items\":[]}", true));
    }
}
