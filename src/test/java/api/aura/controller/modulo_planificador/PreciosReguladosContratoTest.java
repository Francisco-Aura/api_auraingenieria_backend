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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Los peajes y cargos del BOE de los estudios se leen y se guardan desde la pantalla: cada BOE tiene su nombre y la fecha desde la que vale. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class PreciosReguladosContratoTest {

    private static final String BASE = "/api/planificador/regulated-prices";
    private static final List<String> TARIFAS = List.of("2.0TD", "3.0TD", "6.1TD");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("DELETE FROM pl_precios_regulados");
        jdbc.execute("DELETE FROM pl_boes");
    }

    /** Un BOE completo: su nombre, desde cuándo vale y todas las tarifas con el valor `v` en sus periodos. */
    private Map<String, Object> boeCompleto(String nombre, String desde, double v) {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (String t : TARIFAS)
            for (String termino : List.of("energia", "potencia"))
                for (String concepto : List.of("peaje", "cargo")) {
                    int n = "2.0TD".equals(t) ? ("energia".equals(termino) ? 3 : 2) : 6;
                    List<Object> p = new ArrayList<>();
                    for (int i = 0; i < 6; i++) p.add(i < n ? v : null);
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("tarifa", t);
                    f.put("termino", termino);
                    f.put("concepto", concepto);
                    f.put("periodos", p);
                    f.put("referencia", "peaje".equals(concepto) ? "BOE-A-1" : "BOE-A-2");
                    filas.add(f);
                }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("vigenteDesde", desde);
        cuerpo.put("precios", filas);
        return cuerpo;
    }

    private JsonNode enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion, Object cuerpo, int estado) throws Exception {
        String r = mvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)))
                .andExpect(status().is(estado)).andReturn().getResponse().getContentAsString();
        return json.readTree(r);
    }

    private JsonNode anadir(Object cuerpo) throws Exception {
        return enviar(post(BASE), cuerpo, 201);
    }

    private JsonNode guardar(int id, Object cuerpo) throws Exception {
        return enviar(put(BASE + "/" + id), cuerpo, 200);
    }

    @Test
    void unBoeSeAnadeSeLeeSeCambiaYSeElimina() throws Exception {
        JsonNode r = anadir(boeCompleto("Peajes y cargos 2027", "2027-01-01", 0.5));
        int id = r.get("id").asInt();
        assertEquals(1, r.get("boes").size());
        assertEquals("Peajes y cargos 2027", r.get("boes").get(0).get("nombre").asText());
        assertEquals("2027-01-01", r.get("boes").get(0).get("vigenteDesde").asText());
        assertEquals(12, r.get("precios").size());
        JsonNode primera = r.get("precios").get(0);
        assertEquals(id, primera.get("boeId").asInt());
        assertEquals("2.0TD", primera.get("tarifa").asText());
        assertEquals("[0.5,0.5,0.5,null,null,null]", primera.get("periodos").toString(), "la 2.0TD solo tiene tres periodos de energía");

        // Cambiarlo sustituye sus precios, su nombre y su fecha: no lo duplica
        r = guardar(id, boeCompleto("Peajes 2027 revisados", "2027-03-01", 0.25));
        assertEquals(1, r.get("boes").size());
        assertEquals("Peajes 2027 revisados", r.get("boes").get(0).get("nombre").asText());
        assertEquals("2027-03-01", r.get("boes").get(0).get("vigenteDesde").asText());
        assertEquals(12, r.get("precios").size());
        assertEquals(0.25, r.get("precios").get(0).get("periodos").get(0).asDouble(), 1e-9);

        r = json.readTree(mvc.perform(delete(BASE + "/" + id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(0, r.get("boes").size());
        assertEquals(0, r.get("precios").size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pl_precios_regulados", Integer.class), "al eliminar el BOE se van sus precios");
    }

    @Test
    void puedeHaberVariosBoeElMismoAnioOUnoCadaDosYSalenDelMasRecienteAlMasAntiguo() throws Exception {
        anadir(boeCompleto("Peajes y cargos 2025", "2025-01-01", 0.1));
        anadir(boeCompleto("Cargos julio 2025", "2025-07-01", 0.2));
        JsonNode r = anadir(boeCompleto("Peajes y cargos 2027", "2027-01-01", 0.3));
        assertEquals(3, r.get("boes").size());
        assertEquals("[\"Peajes y cargos 2027\",\"Cargos julio 2025\",\"Peajes y cargos 2025\"]",
                json.writeValueAsString(java.util.stream.StreamSupport.stream(r.get("boes").spliterator(), false).map(b -> b.get("nombre").asText()).toList()));
        assertEquals(36, r.get("precios").size(), "cada BOE tiene sus 12 precios y no se mezclan");
        // Dos BOE que empiezan el mismo día también se admiten
        r = anadir(boeCompleto("Otro del mismo día", "2025-07-01", 0.4));
        assertEquals(4, r.get("boes").size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unBoeIncompletoOConDatosMalosNoSeGuarda() throws Exception {
        Map<String, Object> cuerpo = boeCompleto("Prueba", "2027-01-01", 0.5);
        ((List<Object>) cuerpo.get("precios")).remove(0);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Falta el peaje de energia de la 2.0TD."));

        cuerpo = boeCompleto("Prueba", "2027-01-01", 0.5);
        Map<String, Object> f = (Map<String, Object>) ((List<Object>) cuerpo.get("precios")).get(4);
        ((List<Object>) f.get("periodos")).set(5, -1);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El P6 del peaje de energia de la 3.0TD no es válido."));

        // Sin nombre, con una fecha que no es una fecha o fuera de rango
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(boeCompleto("  ", "2027-01-01", 0.5))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("Pon el nombre del BOE."));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(boeCompleto("Prueba", "mañana", 0.5))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("Pon la fecha desde la que vale el BOE."));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(boeCompleto("Prueba", "1999-01-01", 0.5))))
                .andExpect(status().isUnprocessableEntity());
        // Cambiar uno que no existe
        mvc.perform(put(BASE + "/99999").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(boeCompleto("Prueba", "2027-01-01", 0.5))))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pl_precios_regulados", Integer.class), "no se guarda nada a medias");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pl_boes", Integer.class), "ni el BOE sin sus precios");
    }
}
