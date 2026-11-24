package api.aura.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los clientes para el presupuestador del planificador los pide otro servidor: sin sesión ni la
 * cabecera AJAX del navegador, con su clave en X-Planificador-Token (la comprueba
 * PlanificadorClienteController). La clave no abre el resto de la API de clientes.
 */
@SpringBootTest(properties = "planificador.token=clave-de-prueba")
class PlanificadorClientesSeguridadTest {

    private static final String RUTA = "/api/planificador/clientes";

    @Autowired private WebApplicationContext contexto;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(new AuraUserFilter())
                .apply(springSecurity())
                .build();
    }

    @Test
    void con_su_clave_llega_al_controlador_sin_sesion_ni_cabecera_ajax() throws Exception {
        // Un cliente que no existe: 404 del controlador, no 401 de la seguridad
        mvc.perform(get(RUTA + "/999999").header("X-Planificador-Token", "clave-de-prueba"))
                .andExpect(status().isNotFound());
        // Alta sin nombre ni CIF: 400 del controlador, no 403 por la cabecera AJAX
        mvc.perform(post(RUTA).header("X-Planificador-Token", "clave-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sin_clave_o_con_otra_se_rechaza() throws Exception {
        mvc.perform(get(RUTA + "/1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA).header("X-Planificador-Token", "otra")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\",\"cif\":\"B1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void la_clave_no_abre_el_resto_de_clientes() throws Exception {
        mvc.perform(get("/api/clientes").header("X-Planificador-Token", "clave-de-prueba"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/planificador/otra-cosa").header("X-Planificador-Token", "clave-de-prueba"))
                .andExpect(status().isUnauthorized());
    }
}
