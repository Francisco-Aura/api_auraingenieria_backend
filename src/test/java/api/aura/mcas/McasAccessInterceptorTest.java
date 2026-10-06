package api.aura.mcas;

import api.aura.config.AuraUserFilter;
import api.aura.config.GlobalExceptionHandler;
import api.aura.config.McasAccesoUsuario;
import api.aura.config.McasAccessInterceptor;
import api.aura.config.McasWebConfig;
import api.aura.controller.ConfiguracionModulosController;
import api.aura.controller.McasSecurityExceptionHandler;
import api.aura.controller.mcas.McasProductosController;
import api.aura.entity.ModuloConfiguracion;
import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.mcas.McasProductoComercialRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.McasAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los indicadores MCAS solo los cambia un administrador, escriba como escriba el
 * código en la URL: la búsqueda en MariaDB no distingue mayúsculas.
 */
class McasAccessInterceptorTest {

    private McasAccessService access;
    private ModuloConfiguracionService modulos;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        access = mock(McasAccessService.class);
        modulos = mock(ModuloConfiguracionService.class);
        when(modulos.activar(anyString())).thenReturn(new ModuloConfiguracion());
        when(modulos.desactivar(anyString())).thenReturn(new ModuloConfiguracion());
        mvc = MockMvcBuilders
                .standaloneSetup(new ConfiguracionModulosController(modulos),
                        new McasProductosController(mock(McasProductoComercialRepository.class)),
                        new PruebaAccesoController())
                // Mismo par de advices que en la aplicación, con el genérico primero:
                // la denegación debe seguir llegando como 403 y no como 500.
                .setControllerAdvice(new GlobalExceptionHandler(), new McasSecurityExceptionHandler())
                .addMappedInterceptors(McasWebConfig.RUTAS_INTERCEPTADAS, new McasAccessInterceptor(access))
                .build();
    }

    private void noAdministrador() {
        doThrow(new SecurityException("MCAS requiere nivel de acceso administrador")).when(access).requireAdmin();
    }

    @Test
    void indicador_mcas_en_minusculas_exige_administrador() throws Exception {
        noAdministrador();

        mvc.perform(post("/api/configuracion-modulos/mcas_enabled/desactivar"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MCAS requiere nivel de acceso administrador"));

        verify(modulos, never()).desactivar(anyString());
    }

    @Test
    void indicador_mcas_codificado_en_la_url_exige_administrador() throws Exception {
        noAdministrador();

        mvc.perform(post(URI.create("/api/configuracion-modulos/%4Dcas_Enabled/activar"))).andExpect(status().isForbidden());

        verify(modulos, never()).activar(anyString());
    }

    @Test
    void indicador_mcas_en_mayusculas_exige_administrador() throws Exception {
        noAdministrador();

        mvc.perform(post("/api/configuracion-modulos/MCAS_ENABLED/activar")).andExpect(status().isForbidden());
        mvc.perform(get("/api/configuracion-modulos/mcas")).andExpect(status().isForbidden());
    }

    @Test
    void otros_modulos_no_cambian_de_comportamiento() throws Exception {
        noAdministrador();

        mvc.perform(post("/api/configuracion-modulos/GESTOR_TAREAS/activar")).andExpect(status().isOk());
        mvc.perform(get("/api/configuracion-modulos/GESTOR_TAREAS/activo")).andExpect(status().isOk());

        verify(modulos).activar("GESTOR_TAREAS");
    }

    @Test
    void el_catalogo_de_productos_se_puede_leer_sin_ser_administrador() throws Exception {
        noAdministrador();

        mvc.perform(get("/api/mcas/productos-comerciales")).andExpect(status().isOk());
        mvc.perform(post("/api/mcas/productos-comerciales").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administrador_puede_cambiar_indicadores_mcas() throws Exception {
        mvc.perform(post("/api/configuracion-modulos/MCAS_ENABLED/desactivar")).andExpect(status().isOk());

        verify(access).requireAdmin();
        verify(modulos).desactivar("MCAS_ENABLED");
    }

    // ============================================================
    // @McasAccesoUsuario: basta un usuario identificado (plan 30/09/2026)
    // ============================================================

    @Test
    void con_la_anotacion_basta_un_usuario_identificado() throws Exception {
        noAdministrador();

        mvc.perform(get("/api/mcas/prueba-acceso/usuario")).andExpect(status().isOk());

        verify(access).requireUsuario();
        verify(access, never()).requireAdmin();
    }

    @Test
    void sin_la_anotacion_sigue_exigiendo_administrador() throws Exception {
        noAdministrador();

        mvc.perform(get("/api/mcas/prueba-acceso/admin")).andExpect(status().isForbidden());

        verify(access).requireAdmin();
        verify(access, never()).requireUsuario();
    }

    /** Con el filtro y el servicio reales: la identidad sale de la cabecera X-Aura-Usuario-Nombre. */
    @Test
    void anonimo_recibe_403_aunque_el_metodo_lleve_la_anotacion() throws Exception {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(usuarios.findFirstByNombreIgnoreCase("Operador"))
                .thenReturn(Optional.of(Usuario.builder().nombre("Operador").nivelAcceso(2).build()));
        MockMvc real = mvcCon(new McasAccessService(usuarios));

        real.perform(get("/api/mcas/prueba-acceso/usuario"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Usuario no identificado para operar MCAS"));
        real.perform(get("/api/mcas/prueba-acceso/usuario").header("X-Aura-Usuario-Nombre", "Desconocido"))
                .andExpect(status().isForbidden());

        // Un usuario existente de nivel 2 pasa por el método anotado, pero no por el resto.
        real.perform(get("/api/mcas/prueba-acceso/usuario").header("X-Aura-Usuario-Nombre", "Operador"))
                .andExpect(status().isOk());
        real.perform(get("/api/mcas/prueba-acceso/admin").header("X-Aura-Usuario-Nombre", "Operador"))
                .andExpect(status().isForbidden());
    }

    private MockMvc mvcCon(McasAccessService servicio) {
        return MockMvcBuilders.standaloneSetup(new PruebaAccesoController())
                .setControllerAdvice(new GlobalExceptionHandler(), new McasSecurityExceptionHandler())
                .addFilters(new AuraUserFilter())
                .addMappedInterceptors(McasWebConfig.RUTAS_INTERCEPTADAS, new McasAccessInterceptor(servicio))
                .build();
    }

    /** Controlador mínimo para probar la anotación sin depender de los endpoints reales. */
    @RestController
    @RequestMapping("/api/mcas/prueba-acceso")
    static class PruebaAccesoController {
        @McasAccesoUsuario
        @GetMapping("/usuario")
        public String usuario() { return "ok"; }

        @GetMapping("/admin")
        public String admin() { return "ok"; }
    }
}
