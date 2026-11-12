package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.ComentarioDTO;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Comentario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.AdjuntoRepository;
import api.aura.repository.modulo_tareas.mt_ComentarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mencionar dos veces a la misma persona en un comentario le mandaba dos avisos
 * idénticos.
 */
class MencionesComentarioTest {

    private final mt_ComentarioRepository comentarios = mock(mt_ComentarioRepository.class);
    private final mt_TareaRepository tareas = mock(mt_TareaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final TareaNotificacionService notificaciones = mock(TareaNotificacionService.class);

    private final TareaComentarioService servicio = new TareaComentarioService(
            comentarios, tareas, usuarios, notificaciones,
            mock(AdjuntoRepository.class), mock(FileStorageService.class));

    private static final Usuario ANA = Usuario.builder().id(7).nombre("ANA COMERCIAL").build();
    private static final Usuario LUIS = Usuario.builder().id(9).nombre("LUIS TÉCNICO").build();
    private static final mt_Tarea TAREA = mt_Tarea.builder().id(3L).titulo("Revisar").build();

    @BeforeEach
    void setUp() {
        when(tareas.findById(3L)).thenReturn(Optional.of(TAREA));
        when(usuarios.findById(9)).thenReturn(Optional.of(LUIS));
        when(usuarios.findAll()).thenReturn(List.of(ANA, LUIS));
        when(comentarios.save(any())).thenAnswer(inv -> {
            mt_Comentario c = inv.getArgument(0);
            c.setId(50L);
            return c;
        });
    }

    private ComentarioDTO comentario(String texto) {
        return ComentarioDTO.builder().tareaId(3L).usuarioId(9).comentario(texto).build();
    }

    @Test
    void mencionar_dos_veces_a_la_misma_persona_manda_un_solo_aviso() {
        servicio.crearComentario(comentario("@ANACOMERCIAL mira esto y dile a @ANACOMERCIAL que confirme"));

        verify(notificaciones, times(1)).notificarMencion(any(), eq(ANA), any());
    }

    @Test
    void mencionar_a_dos_personas_manda_un_aviso_a_cada_una() {
        servicio.crearComentario(comentario("@ANACOMERCIAL y @LUISTÉCNICO, atentos"));

        verify(notificaciones).notificarMencion(any(), eq(ANA), any());
        verify(notificaciones).notificarMencion(any(), eq(LUIS), any());
    }

    @Test
    void una_mencion_al_final_de_la_frase_tambien_avisa() {
        servicio.crearComentario(comentario("Gracias @ANACOMERCIAL."));

        verify(notificaciones).notificarMencion(any(), eq(ANA), any());
    }

    @Test
    void un_nombre_con_guion_o_apostrofo_se_menciona_entero() {
        Usuario joseLuis = Usuario.builder().id(11).nombre("José-Luis O'Brien").build();
        when(usuarios.findAll()).thenReturn(List.of(ANA, LUIS, joseLuis));

        servicio.crearComentario(comentario("@José-LuisO'Brien revisa esto"));

        verify(notificaciones).notificarMencion(any(), eq(joseLuis), any());
    }

    @Test
    void un_correo_en_el_texto_no_es_una_mencion() {
        servicio.crearComentario(comentario("Escribe a soporte@anacomercial.es"));

        verify(notificaciones, times(0)).notificarMencion(any(), any(), any());
    }

    @Test
    void corregir_un_comentario_solo_avisa_a_quien_se_menciona_por_primera_vez() {
        mt_Comentario existente = mt_Comentario.builder().id(50L).tarea(TAREA).usuarioAutor(LUIS)
                .comentario("@ANACOMERCIAL revisa").build();
        when(comentarios.findById(50L)).thenReturn(Optional.of(existente));

        servicio.actualizarComentario(50L, "@ANACOMERCIAL revisa, y @LUISTÉCNICO también", 9);

        verify(notificaciones, times(0)).notificarMencion(any(), eq(ANA), any());
        verify(notificaciones).notificarMencion(any(), eq(LUIS), any());
    }
}
