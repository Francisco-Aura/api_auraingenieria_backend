package api.aura.controller.modulo_planificador;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class SaludControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void saludDevuelveElMismoJsonQueElPlanificadorActual() throws Exception {
        mockMvc.perform(get("/api/planificador/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"ok\"}", true));
    }
}
