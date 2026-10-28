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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El aviso del planificador lo envía otro servidor: no tiene sesión ni la cabecera AJAX del
 * navegador, y se identifica con su clave en X-Planificador-Token. El resto de las
 * notificaciones sigue exigiendo sesión.
 */
@SpringBootTest(properties = "planificador.token=clave-de-prueba")
class PlanificadorAvisoSeguridadTest {

    private static final String RUTA = "/api/notificaciones-tareas/planificador";
    private static final String AVISO = "{\"group\":\"ingenieria\",\"message\":\"La tarea anterior ha terminado\"}";

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
    void con_su_clave_entra_sin_sesion_ni_cabecera_ajax() throws Exception {
        mvc.perform(post(RUTA).header("X-Planificador-Token", "clave-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON).content(AVISO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creadas").exists());
    }

    @Test
    void sin_clave_o_con_otra_se_rechaza() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(AVISO))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA).header("X-Planificador-Token", "otra")
                        .contentType(MediaType.APPLICATION_JSON).content(AVISO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void la_clave_no_abre_el_resto_de_las_notificaciones() throws Exception {
        mvc.perform(get("/api/notificaciones-tareas/usuario/1").header("X-Planificador-Token", "clave-de-prueba"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notificaciones-tareas/usuario/1/marcar-todas-leidas")
                        .header("X-Planificador-Token", "clave-de-prueba")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }
}
