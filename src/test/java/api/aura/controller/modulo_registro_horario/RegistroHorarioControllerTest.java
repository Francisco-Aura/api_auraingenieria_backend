package api.aura.controller.modulo_registro_horario;

import api.aura.config.RegistroHorarioExceptionHandler;
import api.aura.service.modulo_registro_horario.EmpresasConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las mismas respuestas que test_api.py del Registro Horario anterior (health, empresas, festivos), con las empresas
 * inventadas de su empresas_prueba.json. Sin base de datos: no hace falta arrancar la intranet.
 */
class RegistroHorarioControllerTest {

    private static final String EMPRESAS = """
            {"localidad": "LORCA", "festivos_extra": ["09-08"], "empresas": [
              {"empresa": "EMPRESA UNO, S.L.", "cif": "B00000001", "ccc": "30/0000000/01", "centro_trabajo": "Calle Ejemplo 1, Lorca"},
              {"empresa": "EMPRESA DOS, S.A.", "cif": "A00000002", "ccc": "30/0000000/02",
               "centro_trabajo": "Poligono Ejemplo, Murcia", "localidad": "MURCIA"}]}
            """;

    @TempDir
    Path carpeta;
    MockMvc mvc;

    @BeforeEach
    void preparar() throws Exception {
        Path archivo = carpeta.resolve("empresas.json");
        Files.writeString(archivo, EMPRESAS);
        mvc = MockMvcBuilders
                .standaloneSetup(new RegistroHorarioController(new EmpresasConfig(new ObjectMapper(), archivo.toString())))
                .setControllerAdvice(new RegistroHorarioExceptionHandler())
                .build();
    }

    @Test
    void saludDevuelveElMismoJsonQueElAnterior() throws Exception {
        mvc.perform(get("/api/registro-horario/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"ok\":true}", true));
    }

    @Test
    void empresasConLosMismosCamposQueElAnterior() throws Exception {
        mvc.perform(get("/api/registro-horario/empresas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localidad").value("LORCA"))
                .andExpect(jsonPath("$.empresas", hasSize(2)))
                .andExpect(jsonPath("$.empresas[0].texto").value("EMPRESA UNO, S.L."))
                .andExpect(jsonPath("$.empresas[0].empresa").value("EMPRESA UNO, S.L."))
                .andExpect(jsonPath("$.empresas[0].cif").value("B00000001"))
                .andExpect(jsonPath("$.empresas[0].ccc").value("30/0000000/01"))
                .andExpect(jsonPath("$.empresas[0].centro_trabajo").value("Calle Ejemplo 1, Lorca"))
                .andExpect(jsonPath("$.empresas[0].localidad").value(""))
                .andExpect(jsonPath("$.empresas[1].texto").value("EMPRESA DOS, S.A."))
                .andExpect(jsonPath("$.empresas[1].localidad").value("MURCIA"));
    }

    @Test
    void festivosIncluyenViernesSantoNacionalesYElLocalDeLaConfiguracion() throws Exception {
        mvc.perform(get("/api/registro-horario/festivos/2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anio").value(2026))
                .andExpect(jsonPath("$.festivos", hasItem("2026-04-03")))
                .andExpect(jsonPath("$.festivos", hasItem("2026-10-12")))
                .andExpect(jsonPath("$.festivos", hasItem("2026-12-25")))
                .andExpect(jsonPath("$.festivos", hasItem("2026-09-08")));
    }

    @Test
    void festivosDeUnAnioFueraDeRangoDan422ConElMensajeDelAnterior() throws Exception {
        for (String anio : new String[]{"1999", "2101", "0"}) {
            mvc.perform(get("/api/registro-horario/festivos/" + anio))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(content().json("{\"detail\":\"Año fuera de rango.\"}", true));
        }
    }

    @Test
    void festivosConAnioQueNoEsUnNumeroDan422() throws Exception {
        mvc.perform(get("/api/registro-horario/festivos/abc"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", not("")));
    }
}
