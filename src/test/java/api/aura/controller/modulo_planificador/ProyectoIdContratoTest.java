package api.aura.controller.modulo_planificador;

import api.aura.repository.modulo_planificador.pl_EstudioRepository;
import api.aura.repository.modulo_planificador.pl_MetaRepository;
import api.aura.repository.modulo_planificador.pl_PresupuestoRepository;
import api.aura.service.modulo_planificador.EstudioService;
import api.aura.service.modulo_planificador.PresupuestoService;
import api.aura.service.modulo_planificador.ProyectoIdRelleno;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** El proyecto_id de presupuestos y estudios: agrupa por propuesta aunque el nombre esté escrito distinto, y se rellena en lo que ya había. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class ProyectoIdContratoTest {

    private static final String BASE = "/api/planificador";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private pl_PresupuestoRepository presupuestos;
    @Autowired
    private pl_EstudioRepository estudios;
    @Autowired
    private pl_MetaRepository meta;
    @Autowired
    private PresupuestoService presupuestoService;
    @Autowired
    private EstudioService estudioService;
    @Autowired
    private PlatformTransactionManager transacciones;

    @BeforeEach
    void limpiar() {
        jdbc.execute("DELETE FROM pl_propuestas");
        jdbc.execute("DELETE FROM pl_presupuestos");
        jdbc.execute("DELETE FROM pl_estudios");
        jdbc.execute("DELETE FROM pl_proyectos_comerciales");
        jdbc.execute("DELETE FROM pl_meta WHERE clave = '" + ProyectoIdRelleno.CLAVE + "'");
    }

    private ResultActions ana(MockHttpServletRequestBuilder b, String cuerpo) throws Exception {
        return mvc.perform(b.header("X-Aura-Usuario-Nombre", "Ana").header("X-Usuario-Id", 7).header("X-Usuario-Nivel", 3)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode presupuesto(String nombre, String tipo) throws Exception {
        String cliente = "existente".equals(tipo) ? "\"customer_kind\":\"existente\",\"customer_id\":5" : "\"customer_kind\":\"potencial\"";
        return leer(ana(post(BASE + "/budgets"), "{\"name\":\"" + nombre + "\"," + cliente + ",\"kwp\":10,\"panels\":20,\"budget_total\":1000,\"data\":{\"lines\":[]}}")
                .andExpect(status().isCreated()));
    }

    private JsonNode estudio(String cliente, String proyecto) throws Exception {
        return leer(ana(post(BASE + "/studies"), "{\"customer\":{\"kind\":\"potencial\",\"name\":\"" + cliente + "\"},\"proyecto\":\"" + proyecto + "\"}")
                .andExpect(status().isCreated()));
    }

    private int propuestas() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pl_proyectos_comerciales", Integer.class);
    }

    @Test
    void variantesConElNombreEscritoDistintoCompartenPropuesta() throws Exception {
        JsonNode a = presupuesto("Solaria · Nave Getafe", "existente");
        JsonNode b = presupuesto("solaria · NAVE  getafe v2", "existente");
        JsonNode c = presupuesto("Solaria · Nave Getafe (copia)", "existente");
        assertFalse(a.get("project_id").asText().isEmpty());
        assertEquals(a.get("project_id").asText(), b.get("project_id").asText());
        assertEquals(a.get("project_id").asText(), c.get("project_id").asText());
        assertEquals(1, propuestas());
        assertEquals("Nave Getafe", jdbc.queryForObject("SELECT nombre FROM pl_proyectos_comerciales", String.class));
    }

    @Test
    void otroProyectoOOtroClienteSonOtraPropuestaYSinProyectoNoHay() throws Exception {
        JsonNode a = presupuesto("Solaria · Nave Getafe", "existente");
        JsonNode otroProyecto = presupuesto("Solaria · Nave Leganés", "existente");
        JsonNode otroCliente = presupuesto("Bar Pepe · Nave Getafe", "potencial");
        JsonNode sinProyecto = presupuesto("Solo cliente", "potencial");
        assertNotEquals(a.get("project_id").asText(), otroProyecto.get("project_id").asText());
        assertNotEquals(a.get("project_id").asText(), otroCliente.get("project_id").asText());
        assertEquals("", sinProyecto.get("project_id").asText());
        assertEquals(3, propuestas());
    }

    @Test
    void duplicarMantieneLaPropuestaYElEstudioDelMismoProyectoCoincide() throws Exception {
        JsonNode e = estudio("Bar Pepe", "Terraza");
        JsonNode b = presupuesto("bar pepe · terraza", "potencial");
        assertFalse(e.get("project_id").asText().isEmpty());
        assertEquals(e.get("project_id").asText(), b.get("project_id").asText());
        JsonNode copia = leer(ana(post(BASE + "/budgets/" + b.get("id").asText() + "/variant"), "{}").andExpect(status().is2xxSuccessful()));
        assertEquals(b.get("project_id").asText(), copia.get("project_id").asText());
        assertEquals(1, propuestas());
    }

    @Test
    void elRellenoDaPropuestaALoQueYaHabiaYSoloUnaVez() throws Exception {
        presupuesto("Solaria · Nave Getafe", "existente");
        presupuesto("solaria · nave getafe v2", "existente");
        presupuesto("Solo cliente", "potencial");
        estudio("Bar Pepe", "Terraza");
        // Como estaba antes del cambio: sin propuesta en ninguna fila
        jdbc.execute("UPDATE pl_presupuestos SET proyecto_id = ''");
        jdbc.execute("UPDATE pl_estudios SET proyecto_id = ''");
        jdbc.execute("DELETE FROM pl_proyectos_comerciales");

        ProyectoIdRelleno relleno = new ProyectoIdRelleno(presupuestos, estudios, meta, presupuestoService, estudioService, transacciones);
        int[] hechos = relleno.rellenar();
        assertEquals(2, hechos[0]);
        assertEquals(1, hechos[1]);
        assertEquals(2, propuestas());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(DISTINCT proyecto_id) FROM pl_presupuestos WHERE proyecto_id <> ''", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pl_presupuestos WHERE proyecto_id = ''", Integer.class));

        int[] otraVez = relleno.rellenar();
        assertEquals(0, otraVez[0] + otraVez[1]);
        assertEquals(2, propuestas());
    }
}
