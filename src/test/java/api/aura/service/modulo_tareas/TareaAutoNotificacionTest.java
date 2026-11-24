package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.TareaDTO;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_TareaEstado;
import api.aura.repository.ClienteRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.AdjuntoRepository;
import api.aura.repository.modulo_tareas.mt_CategoriaRepository;
import api.aura.repository.modulo_tareas.mt_SubCategoriaRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
import api.aura.repository.modulo_tareas.mt_TareaHistorialRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nadie recibe avisos de sus propios actos. El cambio de estado ya lo respetaba,
 * pero al CREAR una tarea se llamaba a la versión sin autor de
 * {@code notificarAsignacion}: quien creaba una tarea y se la asignaba a sí mismo
 * recibía una notificación de su propia asignación.
 */
class TareaAutoNotificacionTest {

    private final mt_TareaRepository tareaRepository = mock(mt_TareaRepository.class);
    private final mt_TareaEstadoRepository estadoRepository = mock(mt_TareaEstadoRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final TareaNotificacionService notificaciones = mock(TareaNotificacionService.class);

    private final TareaService servicio = new TareaService(
            tareaRepository, estadoRepository, mock(mt_CategoriaRepository.class),
            mock(mt_SubCategoriaRepository.class), usuarioRepository, mock(ClienteRepository.class),
            notificaciones, mock(TareaComentarioService.class), mock(mt_TareaHistorialRepository.class),
            mock(AdjuntoRepository.class), mock(FileStorageService.class));

    private static final Usuario ANA = Usuario.builder().id(7).nombre("ANA COMERCIAL").nivelAcceso(1).build();
    private static final Usuario LUIS = Usuario.builder().id(9).nombre("LUIS TÉCNICO").nivelAcceso(2).build();

    @BeforeEach
    void setUp() {
        when(estadoRepository.findByEstadoFinalFalseOrderByOrdenAsc()).thenReturn(List.of(
                mt_TareaEstado.builder().id(1L).nombre("Pendiente").estadoFinal(false)
                        .cuentaTiempo(false).esBloqueo(false).build()));
        when(tareaRepository.save(any())).thenAnswer(inv -> {
            mt_Tarea t = inv.getArgument(0);
            if (t.getId() == null) t.setId(100L);
            return t;
        });
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(ANA));
        when(usuarioRepository.findById(9)).thenReturn(Optional.of(LUIS));
    }

    private TareaDTO nueva(Integer asignadoA, Integer creador) {
        return TareaDTO.builder().titulo("Revisar factura")
                .usuarioAsignadoId(asignadoA).usuarioCreadorId(creador).build();
    }

    @Test
    void crear_una_tarea_para_uno_mismo_no_genera_aviso_propio() {
        servicio.crear(nueva(7, 7));

        // El id del autor viaja siempre: TareaNotificacionService descarta el aviso
        // cuando coincide con la persona asignada.
        verify(notificaciones).notificarAsignacion(any(mt_Tarea.class), eq(7));
    }

    @Test
    void crear_una_tarea_para_otra_persona_si_la_avisa() {
        servicio.crear(nueva(9, 7));

        verify(notificaciones).notificarAsignacion(any(mt_Tarea.class), eq(7));
    }

    @Test
    void si_el_cliente_manda_el_autor_de_la_accion_se_usa_ese() {
        TareaDTO dto = nueva(9, 7);
        dto.setUsuarioAccionId(9);

        servicio.crear(dto);

        verify(notificaciones).notificarAsignacion(any(mt_Tarea.class), eq(9));
    }
}
