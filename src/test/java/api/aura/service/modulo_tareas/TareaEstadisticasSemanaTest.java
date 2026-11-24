package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.EstadisticasTareasDTO;
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
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * «Completadas esta semana» tiene que contar la semana en curso (lunes a domingo).
 * El filtro usaba {@code isAfter(inicioSemana.minusDays(1))} y
 * {@code isBefore(finSemana.plusDays(1))}: una ventana de nueve días que metía en
 * el recuento el domingo anterior y el lunes siguiente.
 */
class TareaEstadisticasSemanaTest {

    private final mt_TareaRepository tareaRepository = mock(mt_TareaRepository.class);

    private final TareaService servicio = new TareaService(
            tareaRepository, mock(mt_TareaEstadoRepository.class), mock(mt_CategoriaRepository.class),
            mock(mt_SubCategoriaRepository.class), mock(UsuarioRepository.class), mock(ClienteRepository.class),
            mock(TareaNotificacionService.class), mock(TareaComentarioService.class),
            mock(mt_TareaHistorialRepository.class), mock(AdjuntoRepository.class), mock(FileStorageService.class));

    private static final mt_TareaEstado FINAL = mt_TareaEstado.builder()
            .id(9L).nombre("Completada").estadoFinal(true).cuentaTiempo(false).esBloqueo(false).build();
    private static final mt_TareaEstado ABIERTO = mt_TareaEstado.builder()
            .id(1L).nombre("Pendiente").estadoFinal(false).cuentaTiempo(false).esBloqueo(false).build();

    private static LocalDateTime lunesDeEstaSemana() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
    }

    private mt_Tarea completadaEl(LocalDateTime cuando) {
        return mt_Tarea.builder().id(cuando.toEpochSecond(java.time.ZoneOffset.UTC))
                .titulo("T").estado(FINAL).fechaFinalizacion(cuando).build();
    }

    private mt_Tarea abiertaQueVence(LocalDateTime cuando) {
        return mt_Tarea.builder().id(-cuando.toEpochSecond(java.time.ZoneOffset.UTC))
                .titulo("T").estado(ABIERTO).fechaVencimiento(cuando).build();
    }

    @Test
    void solo_cuentan_las_completadas_entre_el_lunes_y_el_domingo_de_esta_semana() {
        LocalDateTime lunes = lunesDeEstaSemana();
        when(tareaRepository.findAll()).thenReturn(List.of(
                completadaEl(lunes.minusDays(1).plusHours(10)),   // domingo pasado: fuera
                completadaEl(lunes.plusHours(9)),                 // lunes: dentro
                completadaEl(lunes.plusDays(6).withHour(23)),     // domingo: dentro
                completadaEl(lunes.plusDays(7).plusHours(9))));   // lunes que viene: fuera

        EstadisticasTareasDTO datos = servicio.obtenerEstadisticas(null, 1);

        assertThat(datos.getTareasCompletadasSemana()).isEqualTo(2);
    }

    @Test
    void el_total_de_la_semana_usa_la_misma_ventana_para_las_pendientes() {
        LocalDateTime lunes = lunesDeEstaSemana();
        when(tareaRepository.findAll()).thenReturn(List.of(
                completadaEl(lunes.plusHours(9)),                 // dentro
                abiertaQueVence(lunes.minusDays(1).plusHours(8)), // domingo pasado: fuera
                abiertaQueVence(lunes.plusDays(2).plusHours(8)),  // miércoles: dentro
                abiertaQueVence(lunes.plusDays(9))));             // semana que viene: fuera

        EstadisticasTareasDTO datos = servicio.obtenerEstadisticas(null, 1);

        assertThat(datos.getTotalTareasSemana()).isEqualTo(2);
    }

    @Test
    void las_bloqueadas_se_cuentan_una_sola_vez_y_coinciden_en_los_dos_campos() {
        mt_TareaEstado bloqueo = mt_TareaEstado.builder().id(5L).nombre("Bloqueada")
                .estadoFinal(false).cuentaTiempo(false).esBloqueo(true).build();
        when(tareaRepository.findAll()).thenReturn(List.of(
                mt_Tarea.builder().id(1L).titulo("T").estado(bloqueo).build(),
                mt_Tarea.builder().id(2L).titulo("T").estado(ABIERTO).build()));

        EstadisticasTareasDTO datos = servicio.obtenerEstadisticas(null, 1);

        assertThat(datos.getTareasBloqueadas()).isEqualTo(1);
        assertThat(datos.getTareasBloqueadasPeriodo()).isEqualTo(datos.getTareasBloqueadas());
    }
}
