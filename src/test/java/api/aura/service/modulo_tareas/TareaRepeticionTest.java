package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.CambioEstadoRequest;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_TareaEstado;
import api.aura.entity.modulo_tareas.mt_TareaHistorial;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
import api.aura.repository.modulo_tareas.mt_TareaHistorialRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** «Repetir»: al completar una tarea periódica se crea la siguiente, una sola vez. */
@SpringBootTest
@Transactional
class TareaRepeticionTest {

    @Autowired private TareaService tareaService;
    @Autowired private mt_TareaRepository tareas;
    @Autowired private mt_TareaEstadoRepository estados;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private mt_TareaHistorialRepository historial;

    private mt_TareaEstado pendiente;
    private mt_TareaEstado completada;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        pendiente = estados.save(mt_TareaEstado.builder().nombre("PENDIENTE-REP-" + System.nanoTime())
                .estadoFinal(false).orden(-10).cuentaTiempo(false).esBloqueo(false).build());
        completada = estados.save(mt_TareaEstado.builder().nombre("COMPLETADA-REP-" + System.nanoTime())
                .estadoFinal(true).orden(99).cuentaTiempo(false).esBloqueo(false).build());
        usuario = usuarios.save(Usuario.builder().nombre("Usuario repeticion").build());
    }

    private mt_Tarea tarea(String titulo, String repetir) {
        return tareas.save(mt_Tarea.builder().titulo(titulo).prioridad(1).notificar(true).enviarCorreo(false)
                .repetir(repetir).estado(pendiente).usuarioAsignado(usuario).usuarioCreador(usuario)
                .fechaInicio(LocalDateTime.of(2026, 9, 14, 9, 0))
                .fechaVencimiento(LocalDateTime.of(2026, 9, 14, 18, 0)).build());
    }

    private void cambiarA(mt_Tarea tarea, mt_TareaEstado estado) {
        CambioEstadoRequest request = new CambioEstadoRequest();
        request.setEstadoId(estado.getId());
        request.setUsuarioAccionId(usuario.getId());
        tareaService.cambiarEstado(tarea.getId(), request);
    }

    private List<mt_Tarea> conTitulo(String titulo) {
        return tareas.findAll().stream().filter(t -> titulo.equals(t.getTitulo())).toList();
    }

    @Test
    void completar_una_tarea_semanal_crea_la_siguiente_una_semana_despues() {
        mt_Tarea original = tarea("Revisar telemedida semanal", "semanal");

        cambiarA(original, completada);

        List<mt_Tarea> siguientes = conTitulo("Revisar telemedida semanal").stream()
                .filter(t -> !t.getId().equals(original.getId())).toList();
        assertThat(siguientes).hasSize(1);
        mt_Tarea siguiente = siguientes.get(0);
        assertThat(siguiente.getFechaInicio()).isEqualTo(LocalDateTime.of(2026, 9, 21, 9, 0));
        assertThat(siguiente.getFechaVencimiento()).isEqualTo(LocalDateTime.of(2026, 9, 21, 18, 0));
        assertThat(siguiente.getEstado().getEstadoFinal()).isFalse();
        assertThat(siguiente.getRepetir()).isEqualTo("semanal");
        assertThat(siguiente.getUsuarioAsignado().getId()).isEqualTo(usuario.getId());
        assertThat(siguiente.getNotificar()).isTrue();
    }

    @Test
    void los_periodos_mensual_anual_y_diario_desplazan_las_fechas() {
        mt_Tarea mensual = tarea("Cobro mensual", "mensual");
        mt_Tarea anual = tarea("Revision anual", "anual");
        mt_Tarea diaria = tarea("Parte diario", "diario");

        cambiarA(mensual, completada);
        cambiarA(anual, completada);
        cambiarA(diaria, completada);

        assertThat(conTitulo("Cobro mensual")).extracting(mt_Tarea::getFechaVencimiento)
                .contains(LocalDateTime.of(2026, 10, 14, 18, 0));
        assertThat(conTitulo("Revision anual")).extracting(mt_Tarea::getFechaVencimiento)
                .contains(LocalDateTime.of(2027, 9, 14, 18, 0));
        assertThat(conTitulo("Parte diario")).extracting(mt_Tarea::getFechaVencimiento)
                .contains(LocalDateTime.of(2026, 9, 15, 18, 0));
    }

    @Test
    void reabrir_y_volver_a_completar_no_duplica_la_siguiente() {
        mt_Tarea original = tarea("Llamada de seguimiento", "semanal");

        cambiarA(original, completada);
        cambiarA(original, pendiente);
        cambiarA(original, completada);

        assertThat(conTitulo("Llamada de seguimiento")).hasSize(2);
    }

    @Test
    void una_tarea_sin_repetir_no_crea_ninguna_otra() {
        mt_Tarea original = tarea("Tarea puntual", null);

        cambiarA(original, completada);

        assertThat(conTitulo("Tarea puntual")).hasSize(1);
    }

    private mt_Tarea tareaQueVence(String titulo, String repetir, LocalDateTime vencimiento) {
        return tareas.save(mt_Tarea.builder().titulo(titulo).prioridad(1).notificar(true).enviarCorreo(false)
                .repetir(repetir).estado(pendiente).usuarioAsignado(usuario).usuarioCreador(usuario)
                .fechaVencimiento(vencimiento).build());
    }

    /** Completa la tarea y luego, una a una, las repeticiones que van saliendo. */
    private List<LocalDateTime> completarSerie(mt_Tarea primera, int veces) {
        mt_Tarea actual = primera;
        for (int i = 0; i < veces; i++) {
            cambiarA(actual, completada);
            final Long anterior = actual.getId();
            actual = conTitulo(primera.getTitulo()).stream()
                    .filter(t -> t.getId() > anterior)
                    .min(java.util.Comparator.comparing(mt_Tarea::getId)).orElseThrow();
        }
        return conTitulo(primera.getTitulo()).stream()
                .sorted(java.util.Comparator.comparing(mt_Tarea::getId))
                .map(mt_Tarea::getFechaVencimiento).toList();
    }

    @Test
    void una_tarea_mensual_del_31_vuelve_al_31_tras_febrero() {
        List<LocalDateTime> fechas = completarSerie(
                tareaQueVence("Cierre de mes", "mensual", LocalDateTime.of(2027, 1, 31, 18, 0)), 4);

        assertThat(fechas).containsExactly(
                LocalDateTime.of(2027, 1, 31, 18, 0), LocalDateTime.of(2027, 2, 28, 18, 0),
                LocalDateTime.of(2027, 3, 31, 18, 0), LocalDateTime.of(2027, 4, 30, 18, 0),
                LocalDateTime.of(2027, 5, 31, 18, 0));
    }

    @Test
    void una_tarea_mensual_del_30_sigue_en_el_30() {
        List<LocalDateTime> fechas = completarSerie(
                tareaQueVence("Factura del 30", "mensual", LocalDateTime.of(2027, 1, 30, 9, 0)), 3);

        assertThat(fechas).containsExactly(
                LocalDateTime.of(2027, 1, 30, 9, 0), LocalDateTime.of(2027, 2, 28, 9, 0),
                LocalDateTime.of(2027, 3, 30, 9, 0), LocalDateTime.of(2027, 4, 30, 9, 0));
    }

    @Test
    void una_tarea_anual_del_29_de_febrero_vuelve_al_29_en_bisiesto() {
        List<LocalDateTime> fechas = completarSerie(
                tareaQueVence("Aniversario bisiesto", "anual", LocalDateTime.of(2028, 2, 29, 10, 0)), 4);

        assertThat(fechas).containsExactly(
                LocalDateTime.of(2028, 2, 29, 10, 0), LocalDateTime.of(2029, 2, 28, 10, 0),
                LocalDateTime.of(2030, 2, 28, 10, 0), LocalDateTime.of(2031, 2, 28, 10, 0),
                LocalDateTime.of(2032, 2, 29, 10, 0));
    }

    @Test
    void sin_vencimiento_reabrir_y_volver_a_completar_tampoco_duplica() {
        mt_Tarea original = tareas.save(mt_Tarea.builder().titulo("Sin fecha periodica").prioridad(1)
                .notificar(false).enviarCorreo(false).repetir("semanal").estado(pendiente)
                .usuarioAsignado(usuario).usuarioCreador(usuario).build());

        cambiarA(original, completada);
        cambiarA(original, pendiente);
        cambiarA(original, completada);

        assertThat(conTitulo("Sin fecha periodica")).hasSize(2);
    }

    @Test
    void una_serie_que_ya_se_habia_quedado_en_el_28_vuelve_sola_a_su_dia() {
        // Serie creada con la versión anterior: 31/01 -> 28/02 -> 28/03 (se quedó en el 28)
        mt_Tarea enero = tareaQueVence("Serie antigua", "mensual", LocalDateTime.of(2027, 1, 31, 18, 0));
        mt_Tarea febrero = repeticionAntigua(enero, LocalDateTime.of(2027, 2, 28, 18, 0));
        mt_Tarea marzo = repeticionAntigua(febrero, LocalDateTime.of(2027, 3, 28, 18, 0));

        cambiarA(marzo, completada);

        assertThat(conTitulo("Serie antigua")).extracting(mt_Tarea::getFechaVencimiento)
                .contains(LocalDateTime.of(2027, 4, 30, 18, 0));
    }

    @Test
    void si_alguien_cambia_la_fecha_a_un_dia_anterior_manda_la_nueva() {
        mt_Tarea enero = tareaQueVence("Serie cambiada", "mensual", LocalDateTime.of(2027, 1, 31, 18, 0));
        mt_Tarea febrero = repeticionAntigua(enero, LocalDateTime.of(2027, 2, 15, 18, 0));

        cambiarA(febrero, completada);

        assertThat(conTitulo("Serie cambiada")).extracting(mt_Tarea::getFechaVencimiento)
                .contains(LocalDateTime.of(2027, 3, 15, 18, 0));
    }

    @Test
    void si_alguien_pasa_a_mano_al_28_una_del_30_manda_el_28() {
        // Serie del 30: 30/03 -> 30/04, y la de abril se movió a mano al 28 (como antes: 28/05)
        mt_Tarea marzo = tareaQueVence("Serie del 30 movida al 28", "mensual", LocalDateTime.of(2027, 3, 30, 18, 0));
        mt_Tarea abril = repeticionAntigua(marzo, LocalDateTime.of(2027, 4, 28, 18, 0));

        cambiarA(abril, completada);

        assertThat(conTitulo("Serie del 30 movida al 28")).extracting(mt_Tarea::getFechaVencimiento)
                .contains(LocalDateTime.of(2027, 5, 28, 18, 0))
                .doesNotContain(LocalDateTime.of(2027, 5, 30, 18, 0));
    }

    @Test
    void una_serie_del_28_sigue_en_el_28() {
        List<LocalDateTime> fechas = completarSerie(
                tareaQueVence("Pago del 28", "mensual", LocalDateTime.of(2027, 1, 28, 9, 0)), 3);

        assertThat(fechas).containsExactly(
                LocalDateTime.of(2027, 1, 28, 9, 0), LocalDateTime.of(2027, 2, 28, 9, 0),
                LocalDateTime.of(2027, 3, 28, 9, 0), LocalDateTime.of(2027, 4, 28, 9, 0));
    }

    private mt_Tarea repeticionAntigua(mt_Tarea anterior, LocalDateTime vencimiento) {
        mt_Tarea nueva = tareaQueVence(anterior.getTitulo(), "mensual", vencimiento);
        historial.save(mt_TareaHistorial.builder().tarea(nueva).estado(pendiente).usuarioAccion(usuario)
                .fechaInicio(LocalDateTime.now()).comentario(TareaService.MARCA_REPETICION + anterior.getId()).build());
        return nueva;
    }

    @Test
    void si_alguien_pasa_la_fecha_a_mano_al_29_manda_el_29() {
        // Serie del 31: 31/03 -> 30/04 (recortada); la de mayo se movió a mano al 29
        mt_Tarea marzo = tareaQueVence("Serie movida al 29", "mensual", LocalDateTime.of(2027, 3, 31, 18, 0));
        mt_Tarea abril = repeticionAntigua(marzo, LocalDateTime.of(2027, 4, 30, 18, 0));
        mt_Tarea mayo = repeticionAntigua(abril, LocalDateTime.of(2027, 5, 29, 18, 0));

        cambiarA(mayo, completada);

        assertThat(conTitulo("Serie movida al 29")).extracting(mt_Tarea::getFechaVencimiento)
                .contains(LocalDateTime.of(2027, 6, 29, 18, 0))
                .doesNotContain(LocalDateTime.of(2027, 6, 30, 18, 0));
    }

    @Test
    void la_serie_de_otra_persona_con_el_mismo_titulo_y_fecha_no_corta_esta() {
        Usuario otro = usuarios.save(Usuario.builder().nombre("Otro comercial").build());
        mt_Tarea deAna = tarea("Informe semanal comercial", "semanal");
        mt_Tarea deOtro = tareas.save(mt_Tarea.builder().titulo("Informe semanal comercial").prioridad(1)
                .notificar(true).enviarCorreo(false).repetir("semanal").estado(pendiente)
                .usuarioAsignado(otro).usuarioCreador(usuario)
                .fechaInicio(LocalDateTime.of(2026, 9, 14, 9, 0))
                .fechaVencimiento(LocalDateTime.of(2026, 9, 14, 18, 0)).build());

        cambiarA(deAna, completada);
        cambiarA(deOtro, completada);

        assertThat(conTitulo("Informe semanal comercial"))
                .filteredOn(t -> LocalDateTime.of(2026, 9, 21, 18, 0).equals(t.getFechaVencimiento()))
                .extracting(t -> t.getUsuarioAsignado().getId())
                .containsExactlyInAnyOrder(usuario.getId(), otro.getId());
    }

    @Test
    void un_motivo_de_bloqueo_de_mas_de_255_caracteres_se_rechaza_con_un_mensaje_claro() {
        mt_TareaEstado bloqueada = estados.save(mt_TareaEstado.builder().nombre("BLOQ-REP-" + System.nanoTime())
                .estadoFinal(false).orden(50).cuentaTiempo(false).esBloqueo(true).build());
        mt_Tarea original = tarea("Tarea con bloqueo largo", null);
        CambioEstadoRequest request = new CambioEstadoRequest();
        request.setEstadoId(bloqueada.getId());
        request.setUsuarioAccionId(usuario.getId());
        request.setMotivoBloqueo("x".repeat(256));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tareaService.cambiarEstado(original.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }
}
