package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.ComentarioDTO;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.AdjuntoRepository;
import api.aura.repository.modulo_tareas.mt_ComentarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TareaComentarioServiceTest {

    private final mt_ComentarioRepository comentarios = mock(mt_ComentarioRepository.class);
    private final mt_TareaRepository tareas = mock(mt_TareaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final TareaNotificacionService notificaciones = mock(TareaNotificacionService.class);
    private final TareaComentarioService servicio = new TareaComentarioService(comentarios, tareas, usuarios,
            notificaciones, mock(AdjuntoRepository.class), mock(FileStorageService.class));

    private final mt_Tarea tarea = mt_Tarea.builder().id(7L).titulo("Visita técnica").build();
    private final Usuario autor = Usuario.builder().id(2).nombre("Luis Técnico").build();

    private void comentar(String texto, Usuario... plantilla) {
        when(tareas.findById(7L)).thenReturn(Optional.of(tarea));
        when(usuarios.findById(2)).thenReturn(Optional.of(autor));
        when(usuarios.findAll()).thenReturn(List.of(plantilla));
        ComentarioDTO dto = new ComentarioDTO();
        dto.setTareaId(7L);
        dto.setUsuarioId(2);
        dto.setComentario(texto);
        servicio.crearComentario(dto);
    }

    @Test
    void la_mencion_de_un_usuario_con_tildes_le_notifica() {
        // El desplegable de menciones inserta el nombre sin espacios y tal cual: @MaríaNúñez
        Usuario maria = Usuario.builder().id(3).nombre("María Núñez").build();

        comentar("@MaríaNúñez revisa el plano, por favor", autor, maria);

        verify(notificaciones).notificarMencion(eq(tarea), eq(maria), eq(autor));
    }

    @Test
    void la_mencion_sin_tildes_sigue_notificando_y_un_nombre_inexistente_no() {
        Usuario pedro = Usuario.builder().id(4).nombre("Pedro Ventas").build();

        comentar("@PedroVentas y @Desconocido, reunión mañana", autor, pedro);

        verify(notificaciones).notificarMencion(eq(tarea), eq(pedro), eq(autor));
        verify(notificaciones, never()).notificarMencion(any(), eq(autor), any());
    }
}
