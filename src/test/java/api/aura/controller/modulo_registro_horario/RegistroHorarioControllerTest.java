package api.aura.controller.modulo_registro_horario;

import api.aura.config.RegistroHorarioExceptionHandler;
import api.aura.service.modulo_registro_horario.EmpresasConfig;
import api.aura.service.modulo_registro_horario.firma.FirmaService;
import api.aura.service.modulo_registro_horario.firma.TrabajosPesados;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    static RegistroHorarioController controlador(Path archivoEmpresas) {
        var empresas = new EmpresasConfig(new ObjectMapper(), archivoEmpresas.toString());
        var trabajos = new TrabajosPesados();
        var firmas = new FirmaService(trabajos);
        return new RegistroHorarioController(empresas, firmas,
                new api.aura.service.modulo_registro_horario.GenerarService(empresas, firmas,
                        new api.aura.service.modulo_registro_horario.documento.MotorWord(firmas), trabajos));
    }

    @BeforeEach
    void preparar() throws Exception {
        Path archivo = carpeta.resolve("empresas.json");
        Files.writeString(archivo, EMPRESAS);
        mvc = MockMvcBuilders
                .standaloneSetup(controlador(archivo))
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

    private byte[] oro(String nombre) throws Exception {
        try (var in = getClass().getResourceAsStream("/registro-horario-oro/firma/" + nombre)) {
            return in.readAllBytes();
        }
    }

    @Test
    void extraerFirmaDevuelveUnPngTransparenteSinCache() throws Exception {
        var foto = new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", oro("foto1.jpg"));
        var respuesta = mvc.perform(multipart("/api/registro-horario/firma/extraer").file(foto)
                        .param("sensibilidad", "0.5").param("vista", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        // los PNG no salen byte a byte iguales (otro codificador): se compara la imagen
        var obtenida = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(respuesta));
        var esperada = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(oro("foto1_s50_v.png")));
        org.junit.jupiter.api.Assertions.assertEquals(esperada.getWidth(), obtenida.getWidth());
        org.junit.jupiter.api.Assertions.assertEquals(esperada.getHeight(), obtenida.getHeight());
        for (int y = 0; y < esperada.getHeight(); y++) {
            for (int x = 0; x < esperada.getWidth(); x++) {
                org.junit.jupiter.api.Assertions.assertEquals(esperada.getRGB(x, y), obtenida.getRGB(x, y));
            }
        }
    }

    @Test
    void extraerFirmaRechazaLoQueNoEsUnaImagenOUnaSensibilidadFueraDeRango() throws Exception {
        var basura = new MockMultipartFile("imagen", "x.jpg", "image/jpeg", "basura".getBytes());
        mvc.perform(multipart("/api/registro-horario/firma/extraer").file(basura))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("{\"detail\":\"No se pudo leer la imagen. Usa un PNG o un JPG.\"}", true));
        var foto = new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", oro("foto1.jpg"));
        mvc.perform(multipart("/api/registro-horario/firma/extraer").file(foto).param("sensibilidad", "2"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/api/registro-horario/firma/extraer"))
                .andExpect(status().isUnprocessableEntity());
    }
}
