package api.aura.service.modulo_planificador;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Clob;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Migración de datos (Fase 7): una base SQLite creada con el código del Planificador actual (con comillas, barras invertidas,
 * saltos de línea, emojis, fechas con microsegundos, JSON anidado…) pasa por {@code scripts/migracion/sqlite_a_mariadb.py}; el SQL
 * resultante se carga en las tablas del nuevo backend y cada valor de cada fila coincide con el original. Después, la API trabaja
 * con esos datos como con cualquiera.
 */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class MigracionDatosTest {

    private static final String BASE = "/api/planificador";
    private static final String[] TABLAS_HIJAS_PRIMERO = {"pl_actividad", "pl_devoluciones_material", "pl_tareas_proyecto", "pl_propuestas", "pl_historial_precios",
            "pl_borradores", "pl_presupuestos", "pl_proyectos", "pl_equipos", "pl_marcas", "pl_proveedores", "pl_meta"};
    private static final DateTimeFormatter MICROS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource datos;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private MockMvc mvc;

    @BeforeEach
    @AfterEach
    void limpiar() {
        for (String t : TABLAS_HIJAS_PRIMERO) {
            jdbc.execute("DELETE FROM " + t);
        }
    }

    private void cargar() {
        ResourceDatabasePopulator p = new ResourceDatabasePopulator(new ClassPathResource("migracion-dorada/datos-h2.sql"));
        p.setSqlScriptEncoding("UTF-8");
        p.execute(datos);
    }

    private JsonNode esperado() throws IOException {
        try (var in = getClass().getResourceAsStream("/migracion-dorada/esperado.json")) {
            return json.readTree(in);
        }
    }

    private String texto(Object o) throws Exception {
        if (o instanceof Clob c) {
            return c.getSubString(1, (int) c.length());
        }
        if (o instanceof byte[] b) {
            return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        }
        return String.valueOf(o);
    }

    private void igual(JsonNode esperado, Object real, String donde) throws Exception {
        if (esperado.isNull()) {
            assertNull(real, donde);
            return;
        }
        assertNotNull(real, donde);
        if (esperado.isContainerNode()) {
            assertEquals(esperado, json.readTree(texto(real)), donde);
        } else if (esperado.isTextual()) {
            String t = real instanceof Timestamp ts ? ts.toLocalDateTime().format(MICROS) : texto(real);
            assertEquals(esperado.asText(), t, donde);
        } else if (real instanceof Boolean b) {
            assertEquals(esperado.asInt() != 0, b, donde);
        } else {
            assertEquals(esperado.asDouble(), ((Number) real).doubleValue(), 1e-9, donde);
        }
    }

    @Test
    void cadaValorDeCadaFilaLlegaIgualQueEnElOriginal() throws Exception {
        cargar();
        JsonNode todo = esperado();
        int filas = 0;
        for (String tabla : TABLAS_HIJAS_PRIMERO) {
            JsonNode esperadas = todo.get(tabla);
            String clave = "pl_meta".equals(tabla) ? "clave" : ("pl_marcas".equals(tabla) || "pl_proveedores".equals(tabla)) ? "nombre" : "id";
            assertEquals(esperadas.size(), jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class), tabla + ": número de filas");
            for (Iterator<String> ids = esperadas.fieldNames(); ids.hasNext(); ) {
                String id = ids.next();
                Map<String, Object> real = jdbc.queryForMap("SELECT * FROM " + tabla + " WHERE " + clave + " = ?", id);
                for (Iterator<Map.Entry<String, JsonNode>> campos = esperadas.get(id).fields(); campos.hasNext(); ) {
                    var campo = campos.next();
                    igual(campo.getValue(), real.get(campo.getKey()), tabla + "/" + id + "/" + campo.getKey());
                }
                filas++;
            }
        }
        assertTrue(filas >= 15);
    }

    @Test
    void soloSeLlevanLasMarcasDeRevisionDePreciosDeLaTablaDeAjustes() throws Exception {
        cargar();
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM pl_meta", Integer.class));
        assertEquals("2026-10-20", jdbc.queryForObject("SELECT valor FROM pl_meta WHERE clave = 'price_review_notified'", String.class));
    }

    @Test
    void laApiLeeYEscribeConLosDatosMigrados() throws Exception {
        cargar();
        // los identificadores y vínculos se conservan: el proyecto, sus tareas con la dependencia y los pagos
        mvc.perform(get(BASE + "/projects")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='p1')]").exists())
                .andExpect(jsonPath("$[?(@.id=='p2')]").doesNotExist()); // el eliminado sigue en la papelera, no en la lista
        mvc.perform(get(BASE + "/projects/p1/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id=='t2')].depends_on[0]").value("t1"))
                .andExpect(jsonPath("$[?(@.id=='t1')].notified[0]").value("t2"))
                .andExpect(jsonPath("$[?(@.id=='t1')].status").value("done"));
        mvc.perform(get(BASE + "/budgets")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='b1')]").exists());
        // y se puede seguir trabajando: cambiar una tarea migrada
        mvc.perform(patch(BASE + "/tasks/t2").header("X-Aura-Usuario-Nombre", "Ana").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"doing\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("doing"));
        assertEquals("doing", jdbc.queryForObject("SELECT estado FROM pl_tareas_proyecto WHERE id = 't2'", String.class));
    }

    /** Prueba opcional con una copia REAL: PLANIFICADOR_COPIA_H2_SQL=ruta del SQL generado con --para-h2 (ver docs/migracion/FASE-7). */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PLANIFICADOR_COPIA_H2_SQL", matches = ".+")
    void unaCopiaRealSeCargaYLaApiLaLee() throws Exception {
        ResourceDatabasePopulator p = new ResourceDatabasePopulator(new org.springframework.core.io.FileSystemResource(System.getenv("PLANIFICADOR_COPIA_H2_SQL")));
        p.setSqlScriptEncoding("UTF-8");
        p.execute(datos);
        var proyectos = json.readTree(mvc.perform(get(BASE + "/projects")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        int tareas = 0;
        for (JsonNode pr : proyectos) {
            tareas += json.readTree(mvc.perform(get(BASE + "/projects/" + pr.get("id").asText() + "/tasks")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).size();
            mvc.perform(get(BASE + "/projects/" + pr.get("id").asText() + "/activity")).andExpect(status().isOk());
        }
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM pl_tareas_proyecto t JOIN pl_proyectos p ON p.id = t.proyecto_id WHERE p.eliminado_el IS NULL", Integer.class), tareas);
        mvc.perform(get(BASE + "/equipment")).andExpect(status().isOk());
        System.out.println("COPIA REAL: " + proyectos.size() + " proyectos, " + tareas + " tareas en la API");
    }

    @Test
    void elSqlParaMariaDbLlevaLasOrdenesQueHacenFaltaYElRecuentoFinal() throws Exception {
        String sql;
        try (var in = getClass().getResourceAsStream("/migracion-dorada/datos-mariadb.sql")) {
            sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(sql.contains("SET NAMES utf8mb4;"));
        assertTrue(sql.contains("NO_BACKSLASH_ESCAPES"));
        assertFalse(sql.contains("FORMAT JSON"), "la variante de MariaDB no lleva lo propio de H2");
        assertTrue(sql.contains("SELECT 'pl_tareas_proyecto' AS tabla, COUNT(*) AS filas FROM pl_tareas_proyecto;"));
        // con NO_BACKSLASH_ESCAPES la barra invertida va tal cual y la comilla se duplica
        assertTrue(sql.contains("barra \\ y \\n literal"));
        assertTrue(sql.contains("Comilla '' y"));
    }
}
