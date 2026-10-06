package api.aura.mcas;

import api.aura.controller.mcas.McasPoliticasRetrocesionController;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.entity.tablas_maestras.TBGestoras;
import api.aura.repository.mcas.McasPoliticaRetrocesionRepository;
import api.aura.repository.mcas.McasTramoRetrocesionRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBGestorasRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pantalla «Retrocesiones» de Configuración MCAS: endpoints del contrato de
 * interfaz contra H2 (el acceso de nivel 1 lo cubre el interceptor de /api/mcas/**,
 * probado en {@link McasAccessInterceptorTest}).
 */
@SpringBootTest
class McasRetrocesionControllerTest {

    @Autowired private McasPoliticasRetrocesionController controller;
    @Autowired private ObjectMapper mapper;
    @Autowired private TBComercializadorasRepository comercializadoras;
    @Autowired private TBGestorasRepository gestoras;
    @Autowired private McasPoliticaRetrocesionRepository politicas;
    @Autowired private McasTramoRetrocesionRepository tramos;

    private MockMvc mvc;
    private Integer idComercializadora;
    private Integer idGestora;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        idComercializadora = comercializadoras.save(TBComercializadoras.builder().nombre("COMERCIALIZADORA RETRO").build()).getId();
        idGestora = gestoras.save(TBGestoras.builder().nombre("GESTORA RETRO").build()).getId();
    }

    @Test
    void ciclo_completo_crear_editar_publicar_y_archivar() throws Exception {
        String codigo = "RET-TEST-" + System.nanoTime();
        JsonNode creada = json(mvc.perform(post("/api/mcas/politicas-retrocesion")
                        .contentType(MediaType.APPLICATION_JSON).content(politica(codigo, "PUBLICADO",
                                tramo("DIAS", 0, 90, "20", "SIN_CONFIRMAR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.nombreComercializadora").value("COMERCIALIZADORA RETRO"))
                .andExpect(jsonPath("$.nombreGestora").value("GESTORA RETRO"))
                .andExpect(jsonPath("$.tramos[0].tipoValor").value("SIN_CONFIRMAR"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        int id = creada.path("id").asInt();
        assertThat(creada.path("resumen").asText()).contains("0–90 días: 20 (unidad sin confirmar)");

        // Sin confirmar la unidad no se publica.
        mvc.perform(post("/api/mcas/politicas-retrocesion/" + id + "/publicar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("sin confirmar")));

        // Editar sustituye los tramos.
        mvc.perform(put("/api/mcas/politicas-retrocesion/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(politica(codigo, null,
                                tramo("MESES", 0, 3, "1", "PORCENTAJE"), tramo("MESES", 3, 6, "0.6", "PORCENTAJE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tramos.length()").value(2))
                .andExpect(jsonPath("$.resumen").value(org.hamcrest.Matchers.containsString("0–3 meses: 100 %; 3–6 meses: 60 %")));
        assertThat(tramos.findByIdPoliticaOrderByUnidadAscDesdeValorAscPrioridadAsc(id)).hasSize(2);

        mvc.perform(post("/api/mcas/politicas-retrocesion/" + id + "/publicar"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PUBLICADO"));

        // Publicada: ni se edita ni se elimina, pero aparece en el listado.
        mvc.perform(put("/api/mcas/politicas-retrocesion/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(politica(codigo, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        mvc.perform(delete("/api/mcas/politicas-retrocesion/" + id)).andExpect(status().isBadRequest());
        JsonNode listado = json(mvc.perform(get("/api/mcas/politicas-retrocesion")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode enListado = null;
        for (JsonNode p : listado) if (p.path("id").asInt() == id) enListado = p;
        assertThat(enListado).isNotNull();
        assertThat(enListado.path("tramos")).hasSize(2);
        assertThat(enListado.path("resumen").asText()).endsWith("Nunca supera lo cobrado.");

        mvc.perform(post("/api/mcas/politicas-retrocesion/" + id + "/archivar"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ARCHIVADO"));
    }

    @Test
    void un_borrador_se_elimina_con_sus_tramos() throws Exception {
        int id = json(mvc.perform(post("/api/mcas/politicas-retrocesion")
                        .contentType(MediaType.APPLICATION_JSON).content(politica("RET-BORRAR-" + System.nanoTime(), null,
                                tramo("MESES", 0, 12, "1", "PORCENTAJE"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("id").asInt();

        mvc.perform(delete("/api/mcas/politicas-retrocesion/" + id)).andExpect(status().isOk());

        assertThat(politicas.findById(id)).isEmpty();
        assertThat(tramos.findByIdPoliticaOrderByUnidadAscDesdeValorAscPrioridadAsc(id)).isEmpty();
        mvc.perform(delete("/api/mcas/politicas-retrocesion/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void los_errores_de_validacion_vuelven_como_400_con_mensaje() throws Exception {
        mvc.perform(post("/api/mcas/politicas-retrocesion")
                        .contentType(MediaType.APPLICATION_JSON).content(politica(null, null,
                                tramo("MESES", 6, 3, "1", "PORCENTAJE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("mayor que")));
    }

    private JsonNode json(String texto) throws Exception {
        return mapper.readTree(texto);
    }

    private String politica(String codigo, String estado, String... tramosJson) {
        return """
                {"codigo":%s,"idComercializadora":%d,"idGestora":%d,"version":"1","vigenciaDesde":"2026-03-09",
                 "segmento":"PYME","metodo":"PORCENTAJE","limiteImportePagado":true,"estado":%s,
                 "descripcion":"Prueba","tramos":[%s]}
                """.formatted(codigo == null ? "null" : "\"" + codigo + "\"", idComercializadora, idGestora,
                estado == null ? "null" : "\"" + estado + "\"", String.join(",", tramosJson));
    }

    private String tramo(String unidad, int desde, int hasta, String valor, String tipo) {
        return """
                {"unidad":"%s","desdeValor":%d,"hastaValor":%d,"valor":%s,"tipoValor":"%s"}
                """.formatted(unidad, desde, hasta, valor, tipo);
    }
}
