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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pruebas de contrato de los proyectos comerciales: se crean con su cliente, no se repiten y se eliminan. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class ProyectosComercialesContratoTest {

    private static final String BASE = "/api/planificador/commercial-projects";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("DELETE FROM pl_proyectos_comerciales");
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return mvc.perform(post(BASE).header("X-Aura-Usuario-Nombre", "Ana").header("X-Usuario-Id", 7).header("X-Usuario-Nivel", 3)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String cuerpo(String cliente, String proyecto) {
        return "{\"customer\":" + cliente + ",\"project\":\"" + proyecto + "\"}";
    }

    private static final String EXISTENTE = "{\"kind\":\"existente\",\"id\":5,\"name\":\"Solaria Energía S.L.\",\"cif\":\"B123\"}";
    private static final String POTENCIAL = "{\"kind\":\"potencial\",\"name\":\"Bar Pepe\",\"phone\":\"600\"}";

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void altaDevuelveElProyectoConSuClienteYQuedaListado() throws Exception {
        JsonNode p = leer(crear(cuerpo(EXISTENTE, "  Nave Getafe ")).andExpect(status().isCreated()));
        for (String clave : new String[]{"id", "customer", "project", "created_at", "created_by"}) {
            assertTrue(p.has(clave), "falta la clave " + clave);
        }
        assertEquals("Nave Getafe", p.get("project").asText());
        assertEquals("existente", p.get("customer").get("kind").asText());
        assertEquals(5, p.get("customer").get("id").asInt());
        assertEquals("Solaria Energía S.L.", p.get("customer").get("name").asText());
        assertEquals("Ana", p.get("created_by").asText());

        JsonNode lista = leer(mvc.perform(get(BASE)).andExpect(status().isOk()));
        assertEquals(1, lista.size());
        assertEquals(p.get("id").asText(), lista.get(0).get("id").asText());
    }

    @Test
    void unClientePotencialNoLlevaId() throws Exception {
        JsonNode p = leer(crear(cuerpo(POTENCIAL, "Terraza")).andExpect(status().isCreated()));
        assertEquals("potencial", p.get("customer").get("kind").asText());
        assertFalse(p.get("customer").has("id"));
        assertEquals("600", p.get("customer").get("phone").asText());
        crear(cuerpo("{\"kind\":\"potencial\",\"id\":3,\"name\":\"X\"}", "Terraza")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void faltanElClienteOElNombreDelProyecto() throws Exception {
        crear("{\"project\":\"Nave\"}").andExpect(status().isUnprocessableEntity());
        crear(cuerpo(EXISTENTE, "   ")).andExpect(status().isUnprocessableEntity());
        crear(cuerpo("{\"kind\":\"existente\",\"name\":\"Sin id\"}", "Nave")).andExpect(status().isUnprocessableEntity());
        crear(cuerpo("{\"kind\":\"otro\",\"name\":\"X\"}", "Nave")).andExpect(status().isUnprocessableEntity());
        crear(cuerpo(EXISTENTE, "x".repeat(81))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void elMismoClienteNoTieneDosProyectosIgualesAunqueCambienMayusculasOTildes() throws Exception {
        crear(cuerpo(EXISTENTE, "Nave Getafe")).andExpect(status().isCreated());
        crear(cuerpo(EXISTENTE, "  nave   GETAFÉ ")).andExpect(status().isConflict());
        crear(cuerpo(EXISTENTE, "Nave Leganés")).andExpect(status().isCreated());
        crear(cuerpo("{\"kind\":\"existente\",\"id\":6,\"name\":\"Otro\"}", "Nave Getafe")).andExpect(status().isCreated());
        crear(cuerpo(POTENCIAL, "Nave Getafe")).andExpect(status().isCreated());
        crear(cuerpo("{\"kind\":\"potencial\",\"name\":\"BAR pepe\"}", "Nave Getafe")).andExpect(status().isConflict());
    }

    @Test
    void sePuedeEliminarYUnoInexistenteDa404() throws Exception {
        String id = leer(crear(cuerpo(EXISTENTE, "Nave Getafe")).andExpect(status().isCreated())).get("id").asText();
        mvc.perform(delete(BASE + "/" + id)).andExpect(status().isOk());
        assertEquals(0, leer(mvc.perform(get(BASE)).andExpect(status().isOk())).size());
        mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNotFound());
        crear(cuerpo(EXISTENTE, "Nave Getafe")).andExpect(status().isCreated());
    }

    private static final String PNG = "data:image/png;base64,iVBORw0KGgo=";

    @Test
    void elCifElLogoYElEncajeSeGuardanConLaPropuestaYLaListaSoloDiceSiLosTiene() throws Exception {
        String cuerpo = "{\"customer\":" + POTENCIAL + ",\"project\":\"Terraza\",\"cif\":\" B12345678 \",\"logo\":\"" + PNG + "\"}";
        JsonNode p = leer(crear(cuerpo).andExpect(status().isCreated()));
        assertEquals("B12345678", p.get("customer").get("cif").asText(), "el CIF de la propuesta manda sobre el del cliente");
        assertEquals(PNG, p.get("logo").asText());
        assertEquals("", p.get("encaje").asText());

        JsonNode fila = leer(mvc.perform(get(BASE)).andExpect(status().isOk())).get(0);
        assertTrue(fila.get("has_logo").asBoolean());
        assertFalse(fila.get("has_encaje").asBoolean());
        assertFalse(fila.has("logo"), "la lista no lleva las imágenes: pesan");

        JsonNode una = leer(mvc.perform(get(BASE + "/" + p.get("id").asText())).andExpect(status().isOk()));
        assertEquals(PNG, una.get("logo").asText());
    }

    @Test
    void sePuedeCambiarElCifElLogoYElEncajeSinTocarElClienteNiElNombre() throws Exception {
        String id = leer(crear(cuerpo(EXISTENTE, "Nave Getafe")).andExpect(status().isCreated())).get("id").asText();
        JsonNode p = leer(mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cif\":\"A87654321\",\"encaje\":\"" + PNG + "\",\"project\":\"Otro nombre\"}")).andExpect(status().isOk()));
        assertEquals("A87654321", p.get("customer").get("cif").asText());
        assertEquals(PNG, p.get("encaje").asText());
        assertEquals("Nave Getafe", p.get("project").asText(), "el nombre no se cambia");
        assertEquals("Solaria Energía S.L.", p.get("customer").get("name").asText());
        // Quitar el encaje: se manda vacío; lo que no llega no se toca
        JsonNode sin = leer(mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"encaje\":\"\"}")).andExpect(status().isOk()));
        assertEquals("", sin.get("encaje").asText());
        assertEquals("A87654321", sin.get("customer").get("cif").asText());
        mvc.perform(put(BASE + "/no-existe").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }

    @Test
    void unaImagenQueNoEsUnaImagenSeRechaza() throws Exception {
        crear("{\"customer\":" + EXISTENTE + ",\"project\":\"Nave\",\"logo\":\"https://x.com/a.png\"}").andExpect(status().isUnprocessableEntity());
        crear("{\"customer\":" + EXISTENTE + ",\"project\":\"Nave\",\"logo\":\"data:text/html;base64,AAAA\"}").andExpect(status().isUnprocessableEntity());
        String id = leer(crear(cuerpo(EXISTENTE, "Nave Getafe")).andExpect(status().isCreated())).get("id").asText();
        mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"encaje\":\"<svg onload=x>\"}")).andExpect(status().isUnprocessableEntity());
    }
}
