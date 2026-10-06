package api.aura.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los errores del cliente (parámetros, JSON, método, archivo grande) no son
 * "errores inesperados" del servidor, y los que sí lo son deben quedar en el log.
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    @RestController
    static class ControladorPrueba {
        @GetMapping("/prueba/numero")
        String numero(@RequestParam int n) {
            return "ok " + n;
        }

        @PostMapping("/prueba/json")
        String json(@RequestBody Map<String, Object> cuerpo) {
            return "ok";
        }

        @GetMapping("/prueba/fallo")
        String fallo() {
            throw new IllegalStateException("detalle interno del fallo");
        }

        @PostMapping("/prueba/archivo")
        String archivo(@RequestParam("archivo") MultipartFile archivo) {
            throw new MaxUploadSizeExceededException(10 * 1024 * 1024);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ControladorPrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void falta_un_parametro_obligatorio_responde_400() throws Exception {
        mvc.perform(get("/prueba/numero"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Falta el parámetro obligatorio 'n'."));
    }

    @Test
    void un_parametro_con_tipo_incorrecto_responde_400() throws Exception {
        mvc.perform(get("/prueba/numero").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El valor 'abc' no es válido para el parámetro 'n'."));
    }

    @Test
    void un_json_mal_formado_responde_400() throws Exception {
        mvc.perform(post("/prueba/json").contentType(MediaType.APPLICATION_JSON).content("{\"a\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Los datos enviados no tienen un formato válido."));
    }

    @Test
    void un_metodo_no_soportado_responde_405() throws Exception {
        mvc.perform(delete("/prueba/numero"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Operación no permitida sobre este recurso."));
    }

    @Test
    void un_archivo_demasiado_grande_responde_413_con_el_limite() throws Exception {
        mvc.perform(multipart("/prueba/archivo")
                        .file(new MockMultipartFile("archivo", "grande.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("El archivo supera el tamaño máximo permitido (10 MB)."));
    }

    @Test
    void un_error_inesperado_responde_500_generico_y_queda_registrado_en_el_log(CapturedOutput salida) throws Exception {
        mvc.perform(get("/prueba/fallo"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Se ha producido un error inesperado. Por favor, inténtelo de nuevo."));

        assertThat(salida.getAll()).contains("detalle interno del fallo").contains("IllegalStateException");
    }
}
