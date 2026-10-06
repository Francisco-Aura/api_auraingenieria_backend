package api.aura.mcas;

import api.aura.config.AuraUserFilter;
import api.aura.entity.Contrato;
import api.aura.entity.Usuario;
import api.aura.repository.ContratoRepository;
import api.aura.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceso de la ronda de usabilidad con la configuración real de la aplicación
 * (filtro de usuario + interceptor MCAS): el resumen y la vista previa los ve
 * cualquier usuario identificado; todo lo demás de Comisiones MCAS sigue siendo
 * solo para administradores.
 */
@SpringBootTest
class McasAccesoOperadorTest {

    @Autowired private WebApplicationContext contexto;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ContratoRepository contratos;

    private MockMvc mvc;
    private String admin;
    private String operador;
    private Integer idContrato;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(new AuraUserFilter()).build();
        long n = System.nanoTime();
        admin = "Admin acceso " + n;
        operador = "Operador acceso " + n;
        usuarios.save(Usuario.builder().nombre(admin).nivelAcceso(1).build());
        usuarios.save(Usuario.builder().nombre(operador).nivelAcceso(2).build());
        idContrato = contratos.save(Contrato.builder().contrato("ACC-" + n)
                .fechaInicio(Date.valueOf("2026-03-16")).build()).getId();
    }

    @Test
    void resumen_y_vista_previa_abiertos_a_un_usuario_de_nivel_2() throws Exception {
        mvc.perform(como(operador, get(resumen()))).andExpect(status().isOk());
        mvc.perform(como(operador, vistaPrevia())).andExpect(status().isOk());
        mvc.perform(como(admin, get(resumen()))).andExpect(status().isOk());
    }

    @Test
    void anonimo_o_desconocido_recibe_403() throws Exception {
        mvc.perform(get(resumen())).andExpect(status().isForbidden());
        mvc.perform(vistaPrevia()).andExpect(status().isForbidden());
        mvc.perform(como("No existe " + System.nanoTime(), get(resumen()))).andExpect(status().isForbidden());
    }

    @Test
    void el_resto_de_comisiones_sigue_siendo_solo_para_administradores() throws Exception {
        for (RequestBuilder peticion : new RequestBuilder[]{
                como(operador, get("/api/mcas/propuestas/recuento")),
                como(operador, get("/api/mcas/propuestas")),
                como(operador, get("/api/mcas/propuestas/contrato/" + idContrato)),
                como(operador, get("/api/mcas/propuestas/buscar-contrato").param("ref", "ACC")),
                como(operador, post("/api/mcas/propuestas/aprobar-lote")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1]}")),
                como(operador, post("/api/mcas/propuestas/1/descartar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))})
            mvc.perform(peticion).andExpect(status().isForbidden());

        mvc.perform(como(admin, get("/api/mcas/propuestas/recuento"))).andExpect(status().isOk());
    }

    private String resumen() {
        return "/api/mcas/propuestas/contrato/" + idContrato + "/resumen";
    }

    private MockHttpServletRequestBuilder vistaPrevia() {
        return post("/api/mcas/vista-previa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contrato\":\"ACC\",\"mcasTipoPrecio\":\"FIJO\"}");
    }

    private MockHttpServletRequestBuilder como(String usuario, MockHttpServletRequestBuilder peticion) {
        return peticion.header("X-Aura-Usuario-Nombre", usuario);
    }
}
