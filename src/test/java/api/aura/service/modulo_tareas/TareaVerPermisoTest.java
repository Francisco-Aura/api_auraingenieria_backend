package api.aura.service.modulo_tareas;

import api.aura.entity.Cliente;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.repository.ClienteRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.AdjuntoRepository;
import api.aura.repository.modulo_tareas.mt_CategoriaRepository;
import api.aura.repository.modulo_tareas.mt_SubCategoriaRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
import api.aura.repository.modulo_tareas.mt_TareaHistorialRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ver una tarea por su número: las mismas que el usuario encuentra en pantalla (suyas,
 * con un aviso suyo o ligadas a un cliente). Antes cualquiera leía cualquiera.
 */
class TareaVerPermisoTest {

    private final mt_TareaRepository tareas = mock(mt_TareaRepository.class);
    private final TareaNotificacionService notificaciones = mock(TareaNotificacionService.class);
    private final TareaService servicio = new TareaService(tareas, mock(mt_TareaEstadoRepository.class),
            mock(mt_CategoriaRepository.class), mock(mt_SubCategoriaRepository.class), mock(UsuarioRepository.class),
            mock(ClienteRepository.class), notificaciones, mock(TareaComentarioService.class),
            mock(mt_TareaHistorialRepository.class), mock(AdjuntoRepository.class), mock(FileStorageService.class));

    private final Usuario ana = Usuario.builder().id(7).nombre("Ana").build();
    private final Usuario luis = Usuario.builder().id(9).nombre("Luis").build();

    private void existe(long id, mt_Tarea tarea) {
        tarea.setId(id);
        when(tareas.findById(id)).thenReturn(Optional.of(tarea));
    }

    @Test
    void la_suya_asignada_o_creada_si() {
        existe(1L, mt_Tarea.builder().usuarioAsignado(ana).usuarioCreador(luis).build());
        existe(2L, mt_Tarea.builder().usuarioAsignado(luis).usuarioCreador(ana).build());

        assertThat(servicio.puedeVerTarea(1L, 7)).isTrue();
        assertThat(servicio.puedeVerTarea(2L, 7)).isTrue();
    }

    @Test
    void una_general_de_otro_no_salvo_que_le_haya_llegado_un_aviso() {
        existe(3L, mt_Tarea.builder().usuarioAsignado(luis).usuarioCreador(luis).build());

        assertThat(servicio.puedeVerTarea(3L, 7)).isFalse();

        when(notificaciones.tieneAvisoDe(3L, 7)).thenReturn(true); // p. ej. le mencionaron
        assertThat(servicio.puedeVerTarea(3L, 7)).isTrue();
    }

    @Test
    void una_ligada_a_un_cliente_si_como_en_la_ficha_del_cliente() {
        existe(4L, mt_Tarea.builder().usuarioAsignado(luis).usuarioCreador(luis)
                .clienteRelacionado(Cliente.builder().id(30).build()).build());

        assertThat(servicio.puedeVerTarea(4L, 7)).isTrue();
    }

    @Test
    void si_no_existe_responde_quien_la_busca_y_sin_usuario_no() {
        when(tareas.findById(99L)).thenReturn(Optional.empty());

        assertThat(servicio.puedeVerTarea(99L, 7)).as("luego da «no encontrada»").isTrue();
        assertThat(servicio.puedeVerTarea(99L, null)).isFalse();
        assertThat(servicio.puedeVerTarea(null, 7)).isFalse();
    }
}
