package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.CambioEstadoRequest;
import api.aura.dto.modulo_tareas.TareaDTO;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_TareaEstado;
import api.aura.entity.modulo_tareas.mt_TareaHistorial;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las notificaciones y la auditoría usan a quien hace el cambio, no al asignado ni al creador. */
class TareaAccionUsuarioTest {

    private final mt_TareaRepository tareas = mock(mt_TareaRepository.class);
    private final mt_TareaEstadoRepository estados = mock(mt_TareaEstadoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final TareaNotificacionService notificaciones = mock(TareaNotificacionService.class);
    private final mt_TareaHistorialRepository historial = mock(mt_TareaHistorialRepository.class);
    private final TareaService servicio = new TareaService(tareas, estados, mock(mt_CategoriaRepository.class),
            mock(mt_SubCategoriaRepository.class), usuarios, mock(ClienteRepository.class), notificaciones,
            mock(TareaComentarioService.class), historial, mock(AdjuntoRepository.class),
            mock(FileStorageService.class));

    private final Usuario creador = Usuario.builder().id(1).nombre("Ana Comercial").nivelAcceso(1).build();
    private final Usuario asignado = Usuario.builder().id(5).nombre("Luis Tecnico").build();
    private final Usuario otro = Usuario.builder().id(7).nombre("Maria Gestora").build();
    private final mt_TareaEstado pendiente = mt_TareaEstado.builder().id(1L).nombre("Pendiente")
            .estadoFinal(false).cuentaTiempo(false).esBloqueo(false).build();
    private final mt_TareaEstado enProgreso = mt_TareaEstado.builder().id(2L).nombre("En Progreso")
            .estadoFinal(false).cuentaTiempo(true).esBloqueo(false).build();

    private mt_Tarea tarea() {
        mt_Tarea tarea = mt_Tarea.builder().id(40L).titulo("Visita").prioridad(2).estado(pendiente)
                .usuarioAsignado(asignado).usuarioCreador(creador).build();
        when(tareas.findById(40L)).thenReturn(Optional.of(tarea));
        when(tareas.save(any(mt_Tarea.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(1)).thenReturn(Optional.of(creador));
        when(usuarios.findById(5)).thenReturn(Optional.of(asignado));
        when(usuarios.findById(7)).thenReturn(Optional.of(otro));
        return tarea;
    }

    @Test
    void el_cambio_de_estado_identifica_a_quien_lo_hace() {
        tarea();
        when(estados.findById(2L)).thenReturn(Optional.of(enProgreso));
        CambioEstadoRequest request = new CambioEstadoRequest();
        request.setEstadoId(2L);
        request.setUsuarioAccionId(5);

        servicio.cambiarEstado(40L, request);

        // El propio asignado hace el cambio: la notificación sabe quién es y no se la envía
        verify(notificaciones).notificarCambioEstado(any(mt_Tarea.class), eq("En Progreso"), eq(5));
        ArgumentCaptor<mt_TareaHistorial> registro = ArgumentCaptor.forClass(mt_TareaHistorial.class);
        verify(historial).save(registro.capture());
        assertThat(registro.getValue().getUsuarioAccion().getId()).isEqualTo(5);
    }

    @Test
    void la_edicion_notifica_en_nombre_de_quien_edita_y_no_del_creador() {
        tarea();
        TareaDTO cambios = TareaDTO.builder().titulo("Visita reprogramada").prioridad(2)
                .usuarioAsignadoId(5).usuarioCreadorId(1).usuarioAccionId(7).build();

        servicio.actualizar(40L, cambios);

        verify(notificaciones).notificarCampoEspecifico(any(mt_Tarea.class), eq("TITULO"), anyString(), eq(7));
    }
}
