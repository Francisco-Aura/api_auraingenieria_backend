package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.TiempoDTO;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_TareaEstado;
import api.aura.entity.modulo_tareas.mt_TareaTramo;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import api.aura.repository.modulo_tareas.mt_TareaTramoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TareaTiempoServiceTest {

    private final mt_TareaTramoRepository tramos = mock(mt_TareaTramoRepository.class);
    private final mt_TareaRepository tareas = mock(mt_TareaRepository.class);
    private final mt_TareaEstadoRepository estados = mock(mt_TareaEstadoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final TareaService tareaService = mock(TareaService.class);
    private final TareaTiempoService servicio = new TareaTiempoService(tramos, tareas, estados, usuarios, tareaService);

    private final mt_TareaEstado enCurso = estado(2L, true, false);
    private final mt_TareaEstado pendiente = estado(1L, false, false);
    private final mt_TareaEstado completado = estado(3L, false, true);

    @BeforeEach
    void preparar() {
        when(usuarios.getReferenceById(7)).thenReturn(Usuario.builder().id(7).build());
        when(estados.findByCuentaTiempoTrueAndEstadoFinalFalseOrderByOrdenAsc()).thenReturn(List.of(enCurso));
        when(tramos.ultimosCerrados(any(), any())).thenReturn(List.of());
        when(tramos.solapados(any(), any(), any())).thenReturn(List.of());
    }

    private static mt_TareaEstado estado(Long id, boolean cuentaTiempo, boolean fin) {
        return mt_TareaEstado.builder().id(id).nombre("e" + id).estadoFinal(fin).cuentaTiempo(cuentaTiempo)
                .esBloqueo(false).orden(id.intValue()).build();
    }

    private mt_Tarea tarea(Long id, Integer asignado, mt_TareaEstado estado) {
        mt_Tarea t = new mt_Tarea();
        t.setId(id);
        t.setTitulo("Tarea " + id);
        t.setEstado(estado);
        if (asignado != null) {
            t.setUsuarioAsignado(Usuario.builder().id(asignado).build());
        }
        when(tareas.findById(id)).thenReturn(Optional.of(t));
        return t;
    }

    private mt_TareaTramo abierto(mt_Tarea tarea, LocalDateTime inicio) {
        return mt_TareaTramo.builder().id(50L).tarea(tarea).inicio(inicio).build();
    }

    @Test
    void empezar_una_tarea_ajena_se_rechaza() {
        tarea(10L, 8, enCurso);

        assertThatThrownBy(() -> servicio.iniciar(10L, 7)).isInstanceOf(SecurityException.class);
        verify(tramos, never()).save(any());
    }

    @Test
    void una_tarea_completada_no_se_puede_empezar() {
        tarea(10L, 7, completado);

        assertThatThrownBy(() -> servicio.iniciar(10L, 7)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void empezar_una_tarea_pendiente_la_pone_en_curso_y_abre_un_tramo() {
        tarea(10L, 7, pendiente);
        when(tramos.buscarAbiertoBloqueando(7)).thenReturn(Optional.empty());
        when(tramos.buscarAbierto(7)).thenReturn(Optional.empty());

        servicio.iniciar(10L, 7);

        verify(tareaService).cambiarEstado(eq(10L), any());
        verify(tramos).save(any(mt_TareaTramo.class));
    }

    @Test
    void empezar_otra_tarea_cierra_la_que_corria() {
        mt_Tarea anterior = tarea(9L, 7, enCurso);
        tarea(10L, 7, enCurso);
        mt_TareaTramo corriendo = abierto(anterior, LocalDateTime.now().minusMinutes(30));
        when(tramos.buscarAbiertoBloqueando(7)).thenReturn(Optional.of(corriendo));
        when(tramos.buscarAbierto(7)).thenReturn(Optional.empty());

        servicio.iniciar(10L, 7);

        assertThat(corriendo.getFin()).isNotNull();
        assertThat(corriendo.getCierre()).isEqualTo(mt_TareaTramo.CIERRE_CAMBIO_TAREA);
        verify(tareaService, never()).cambiarEstado(any(), any());
        verify(tramos, times(2)).save(any(mt_TareaTramo.class));
    }

    @Test
    void empezar_la_tarea_que_ya_corre_no_abre_otro_tramo() {
        mt_Tarea t = tarea(10L, 7, enCurso);
        mt_TareaTramo corriendo = abierto(t, LocalDateTime.now().minusMinutes(5));
        when(tramos.buscarAbiertoBloqueando(7)).thenReturn(Optional.of(corriendo));
        when(tramos.buscarAbierto(7)).thenReturn(Optional.of(corriendo));

        TiempoDTO.Estado e = servicio.iniciar(10L, 7);

        assertThat(e.isActivo()).isTrue();
        assertThat(corriendo.getFin()).isNull();
        verify(tramos, never()).save(any());
    }

    @Test
    void pausar_cierra_el_tramo() {
        mt_Tarea t = tarea(10L, 7, enCurso);
        mt_TareaTramo corriendo = abierto(t, LocalDateTime.now().minusMinutes(5));
        when(tramos.buscarAbiertoBloqueando(7)).thenReturn(Optional.of(corriendo));
        when(tramos.buscarAbierto(7)).thenReturn(Optional.empty());

        TiempoDTO.Estado e = servicio.pausar(7);

        assertThat(e.isActivo()).isFalse();
        assertThat(corriendo.getCierre()).isEqualTo(mt_TareaTramo.CIERRE_MANUAL);
    }

    @Test
    void pausar_por_inactividad_termina_el_tramo_cuando_dejo_de_haber_actividad() {
        mt_Tarea t = tarea(10L, 7, enCurso);
        LocalDateTime inicio = LocalDateTime.now().minusMinutes(30);
        mt_TareaTramo corriendo = abierto(t, inicio);
        when(tramos.buscarAbiertoBloqueando(7)).thenReturn(Optional.of(corriendo));
        when(tramos.buscarAbierto(7)).thenReturn(Optional.empty());

        servicio.pausar(7, 600);

        assertThat(corriendo.getCierre()).isEqualTo(mt_TareaTramo.CIERRE_INACTIVIDAD);
        assertThat(Duration.between(inicio, corriendo.getFin()).toMinutes()).isBetween(19L, 20L);
    }

    @Test
    void pausar_por_inactividad_nunca_deja_el_fin_antes_del_inicio() {
        mt_Tarea t = tarea(10L, 7, enCurso);
        LocalDateTime inicio = LocalDateTime.now().minusMinutes(2);
        mt_TareaTramo corriendo = abierto(t, inicio);
        when(tramos.buscarAbiertoBloqueando(7)).thenReturn(Optional.of(corriendo));
        when(tramos.buscarAbierto(7)).thenReturn(Optional.empty());

        servicio.pausar(7, 3600);

        assertThat(corriendo.getFin()).isEqualTo(inicio);
    }

    @Test
    void un_tramo_olvidado_mas_de_doce_horas_se_cierra_en_el_limite() {
        mt_Tarea t = tarea(10L, 7, enCurso);
        LocalDateTime inicio = LocalDateTime.now().minusHours(30);
        mt_TareaTramo olvidado = abierto(t, inicio);
        when(tramos.buscarAbierto(7)).thenReturn(Optional.of(olvidado));

        TiempoDTO.Estado e = servicio.estadoActual(7);

        assertThat(e.isActivo()).isFalse();
        assertThat(olvidado.getFin()).isEqualTo(inicio.plusHours(12));
        assertThat(olvidado.getCierre()).isEqualTo(mt_TareaTramo.CIERRE_CADUCADO);
    }

    @Test
    void un_tramo_de_una_tarea_completada_se_cierra_cuando_se_completo() {
        mt_Tarea t = tarea(10L, 7, completado);
        LocalDateTime inicio = LocalDateTime.now().minusHours(3);
        t.setFechaFinalizacion(inicio.plusHours(1));
        mt_TareaTramo corriendo = abierto(t, inicio);
        when(tramos.buscarAbierto(7)).thenReturn(Optional.of(corriendo));

        servicio.estadoActual(7);

        assertThat(corriendo.getFin()).isEqualTo(inicio.plusHours(1));
        assertThat(corriendo.getCierre()).isEqualTo(mt_TareaTramo.CIERRE_COMPLETADA);
    }

    @Test
    void reanudar_sin_tarea_anterior_avisa() {
        when(tramos.buscarAbierto(7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reanudar(7)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reanudar_vuelve_a_la_ultima_tarea() {
        mt_Tarea t = tarea(10L, 7, enCurso);
        mt_TareaTramo cerrado = abierto(t, LocalDateTime.now().minusHours(2));
        cerrado.setFin(LocalDateTime.now().minusHours(1));
        when(tramos.ultimosCerrados(any(), any())).thenReturn(List.of(cerrado));
        when(tramos.buscarAbierto(7)).thenReturn(Optional.empty());
        when(tramos.buscarAbiertoBloqueando(7)).thenReturn(Optional.empty());

        servicio.reanudar(7);

        verify(tramos).save(any(mt_TareaTramo.class));
    }

    @Test
    void el_resumen_reparte_por_dias_un_tramo_que_cruza_la_medianoche() {
        mt_Tarea a = tarea(10L, 7, enCurso);
        LocalDate d1 = LocalDate.now().minusDays(3);
        LocalDateTime medianoche = d1.plusDays(1).atStartOfDay();
        mt_TareaTramo t = abierto(a, medianoche.minusHours(1));
        t.setFin(medianoche.plusHours(2));
        when(tramos.solapados(eq(7), any(), any())).thenReturn(List.of(t));

        TiempoDTO.Resumen r = servicio.resumen(7, d1, d1.plusDays(2));

        assertThat(r.getSegundosTotales()).isEqualTo(3 * 3600);
        assertThat(r.getDias()).hasSize(2);
        assertThat(r.getDias().get(0).getFecha()).isEqualTo(d1);
        assertThat(r.getDias().get(0).getSegundos()).isEqualTo(3600);
        assertThat(r.getDias().get(1).getSegundos()).isEqualTo(2 * 3600);
    }

    @Test
    void el_resumen_suma_por_tarea_y_recorta_al_rango() {
        mt_Tarea a = tarea(10L, 7, enCurso);
        mt_Tarea b = tarea(11L, 7, enCurso);
        LocalDate dia = LocalDate.now().minusDays(1);
        LocalDateTime ini = dia.atStartOfDay();
        mt_TareaTramo t1 = abierto(a, ini.plusHours(9));
        t1.setFin(ini.plusHours(11));
        mt_TareaTramo t2 = abierto(b, ini.plusHours(11));
        t2.setFin(ini.plusHours(12));
        // Empezó la víspera: solo cuentan las 2 horas del día pedido
        mt_TareaTramo t3 = abierto(a, ini.minusHours(2));
        t3.setFin(ini.plusHours(2));
        when(tramos.solapados(eq(7), any(), any())).thenReturn(List.of(t3, t1, t2));

        TiempoDTO.Resumen r = servicio.resumen(7, dia, dia);

        assertThat(r.getSegundosTotales()).isEqualTo(5 * 3600);
        assertThat(r.getTareas()).hasSize(2);
        assertThat(r.getTareas().get(0).getTareaId()).isEqualTo(10L);
        assertThat(r.getTareas().get(0).getSegundos()).isEqualTo(4 * 3600);
    }

    @Test
    void un_rango_invertido_o_enorme_se_rechaza() {
        LocalDate hoy = LocalDate.now();

        assertThatThrownBy(() -> servicio.resumen(7, hoy, hoy.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.resumen(7, hoy.minusDays(1000), hoy)).isInstanceOf(IllegalArgumentException.class);
    }
}
