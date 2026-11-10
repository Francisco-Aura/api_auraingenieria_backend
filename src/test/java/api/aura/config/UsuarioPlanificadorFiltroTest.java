package api.aura.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioPlanificadorFiltroTest {

    private UsuarioPlanificadorHolder.Usuario ejecutar(MockHttpServletRequest peticion) throws Exception {
        AtomicReference<UsuarioPlanificadorHolder.Usuario> visto = new AtomicReference<>();
        FilterChain cadena = (req, res) -> visto.set(UsuarioPlanificadorHolder.get());
        new UsuarioPlanificadorFiltro().doFilter(peticion, new MockHttpServletResponse(), cadena);
        assertNull(UsuarioPlanificadorHolder.get(), "el usuario debe limpiarse al acabar la petición");
        return visto.get();
    }

    @Test
    void leeLasCabecerasDeLaIntranet() throws Exception {
        MockHttpServletRequest p = new MockHttpServletRequest("GET", "/api/planificador/health");
        p.addHeader("X-Aura-Usuario-Nombre", " Ana Pérez ");
        UsuarioPlanificadorHolder.Usuario u = ejecutar(p);
        assertEquals("Ana Pérez", u.nombre());
        assertNull(u.id());
    }

    @Test
    void leeLasCabecerasDelPlanificadorActualYDecodificaElNombre() throws Exception {
        MockHttpServletRequest p = new MockHttpServletRequest("GET", "/x");
        p.addHeader("X-Usuario", "Ana%20P%C3%A9rez");
        p.addHeader("X-Usuario-Id", "1");
        p.addHeader("X-Usuario-Nivel", "3");
        UsuarioPlanificadorHolder.Usuario u = ejecutar(p);
        assertEquals("Ana Pérez", u.nombre());
        assertEquals(1, u.id());
        assertEquals(3, u.nivelAcceso());
    }

    @Test
    void sinCabecerasNoHayUsuarioYUnNumeroMalNoRompe() throws Exception {
        MockHttpServletRequest p = new MockHttpServletRequest("GET", "/x");
        p.addHeader("X-Usuario-Nivel", "abc");
        assertNull(ejecutar(p));
    }

    @Test
    void ignoraLasPeticionesOptions() throws Exception {
        MockHttpServletRequest p = new MockHttpServletRequest("OPTIONS", "/x");
        p.addHeader("X-Usuario", "Ana");
        assertNull(ejecutar(p));
    }
}
