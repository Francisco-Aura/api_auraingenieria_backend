package api.aura.service.modulo_tareas;

import api.aura.repository.ClienteRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.AdjuntoRepository;
import api.aura.repository.modulo_tareas.mt_CategoriaRepository;
import api.aura.repository.modulo_tareas.mt_SubCategoriaRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
import api.aura.repository.modulo_tareas.mt_TareaHistorialRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** «Por vencer»: el horizonte en días se acota y un valor absurdo ya no acaba en error 500. */
class TareaPorVencerTest {

    private final mt_TareaRepository tareas = mock(mt_TareaRepository.class);
    private final TareaService servicio = new TareaService(tareas, mock(mt_TareaEstadoRepository.class),
            mock(mt_CategoriaRepository.class), mock(mt_SubCategoriaRepository.class), mock(UsuarioRepository.class),
            mock(ClienteRepository.class), mock(TareaNotificacionService.class), mock(TareaComentarioService.class),
            mock(mt_TareaHistorialRepository.class), mock(AdjuntoRepository.class), mock(FileStorageService.class));

    private LocalDateTime hastaConsultado() {
        ArgumentCaptor<LocalDateTime> hasta = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tareas).findByEstado_EstadoFinalFalseAndFechaVencimientoBetween(any(), hasta.capture());
        return hasta.getValue();
    }

    @Test
    void los_dias_habituales_no_cambian() {
        servicio.obtenerPorVencer(7);

        assertThat(hastaConsultado()).isEqualTo(LocalDate.now().plusDays(7).atTime(23, 59, 59));
    }

    @Test
    void un_horizonte_enorme_se_acota_a_diez_anos() {
        servicio.obtenerPorVencer(Integer.MAX_VALUE);

        assertThat(hastaConsultado()).isEqualTo(
                LocalDate.now().plusDays(TareaService.MAX_DIAS_POR_VENCER).atTime(23, 59, 59));
    }

    @Test
    void dias_negativos_siguen_sin_devolver_nada_y_no_consultan() {
        assertThat(servicio.obtenerPorVencer(-5)).isEmpty();
        assertThat(servicio.obtenerPorVencer(Integer.MIN_VALUE)).isEmpty();

        verify(tareas, never()).findByEstado_EstadoFinalFalseAndFechaVencimientoBetween(any(), any());
    }
}
