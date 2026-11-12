package api.aura.controller;

import api.aura.config.AuraUserHolder;
import api.aura.config.UsuarioSesion;
import api.aura.dto.ClienteDTO;
import api.aura.entity.Cliente;
import api.aura.entity.Usuario;
import api.aura.service.ClienteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Usuario asignado del cliente: en la pantalla solo lo cambian los administradores
 * («Solo usuarios avanzados pueden modificar este campo»); la API ahora igual.
 */
@ExtendWith(MockitoExtension.class)
class ClienteUsuarioAsignadoTest {

    @Mock private ClienteService servicio;
    @InjectMocks private ClienteController controlador;

    @AfterEach
    void cerrarSesion() {
        AuraUserHolder.clear();
    }

    @Test
    void un_usuario_no_cambia_el_usuario_asignado_al_editar() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));
        Cliente actual = Cliente.builder().id(5).usuario(Usuario.builder().id(3).build()).build();
        when(servicio.obtenerClientePorId(5)).thenReturn(Optional.of(actual));

        controlador.actualizarCliente(5, dto(7));

        assertThat(enviado(5).getIdUsuario()).isEqualTo(3);
    }

    @Test
    void un_usuario_no_asigna_usuario_a_un_cliente_que_no_lo_tiene() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));
        when(servicio.obtenerClientePorId(5)).thenReturn(Optional.of(Cliente.builder().id(5).build()));

        controlador.actualizarCliente(5, dto(7));

        assertThat(enviado(5).getIdUsuario()).isNull();
    }

    @Test
    void un_usuario_da_de_alta_sin_usuario_asignado_como_en_la_pantalla() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));

        controlador.crearCliente(dto(7));

        ArgumentCaptor<ClienteDTO> captor = ArgumentCaptor.forClass(ClienteDTO.class);
        verify(servicio).crearCliente(captor.capture());
        assertThat(captor.getValue().getIdUsuario()).isNull();
    }

    @Test
    void un_administrador_asigna_el_usuario_que_elige() {
        AuraUserHolder.set(new UsuarioSesion(1, "Admin", 1));

        controlador.actualizarCliente(5, dto(7));
        controlador.crearCliente(dto(8));

        assertThat(enviado(5).getIdUsuario()).isEqualTo(7);
        ArgumentCaptor<ClienteDTO> captor = ArgumentCaptor.forClass(ClienteDTO.class);
        verify(servicio).crearCliente(captor.capture());
        assertThat(captor.getValue().getIdUsuario()).isEqualTo(8);
    }

    private static ClienteDTO dto(Integer idUsuario) {
        ClienteDTO dto = new ClienteDTO();
        dto.setNombre("Cliente");
        dto.setCif("B00000000");
        dto.setIdUsuario(idUsuario);
        return dto;
    }

    private ClienteDTO enviado(Integer id) {
        ArgumentCaptor<ClienteDTO> captor = ArgumentCaptor.forClass(ClienteDTO.class);
        verify(servicio).actualizarCliente(eq(id), captor.capture());
        return captor.getValue();
    }
}
