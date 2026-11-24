package api.aura.mcas;

import api.aura.config.AuraUserHolder;
import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;
import api.aura.service.mcas.McasAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McasAccessServiceTest {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final McasAccessService access = new McasAccessService(usuarios);

    @AfterEach void clear() { AuraUserHolder.clear(); }

    @Test void exige_usuario_identificado() {
        assertThatThrownBy(access::requireAdmin).isInstanceOf(SecurityException.class);
    }

    @Test void exige_nivel_uno() {
        AuraUserHolder.set("Operador");
        when(usuarios.findFirstByNombreIgnoreCase("Operador"))
                .thenReturn(Optional.of(Usuario.builder().nivelAcceso(2).build()));
        assertThatThrownBy(access::requireAdmin).isInstanceOf(SecurityException.class);
    }

    @Test void permite_administrador_nivel_uno() {
        AuraUserHolder.set("Admin");
        when(usuarios.findFirstByNombreIgnoreCase("Admin"))
                .thenReturn(Optional.of(Usuario.builder().nivelAcceso(1).build()));
        assertThatCode(access::requireAdmin).doesNotThrowAnyException();
    }

    // requireUsuario: resumen y vista previa del contrato (cualquier nivel)

    @Test void requireUsuario_exige_usuario_identificado() {
        assertThatThrownBy(access::requireUsuario).isInstanceOf(SecurityException.class)
                .hasMessage("Usuario no identificado para operar MCAS");
        AuraUserHolder.set("   ");
        assertThatThrownBy(access::requireUsuario).isInstanceOf(SecurityException.class);
    }

    @Test void requireUsuario_exige_que_el_usuario_exista() {
        AuraUserHolder.set("Nadie");
        when(usuarios.findFirstByNombreIgnoreCase("Nadie")).thenReturn(Optional.empty());
        assertThatThrownBy(access::requireUsuario).isInstanceOf(SecurityException.class)
                .hasMessage("Usuario no autorizado para operar MCAS");
    }

    @Test void requireUsuario_admite_cualquier_nivel() {
        AuraUserHolder.set(" Operador ");
        when(usuarios.findFirstByNombreIgnoreCase("Operador"))
                .thenReturn(Optional.of(Usuario.builder().nivelAcceso(3).build()));
        assertThatCode(access::requireUsuario).doesNotThrowAnyException();
    }
}
