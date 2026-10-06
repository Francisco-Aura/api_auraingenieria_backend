package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.CambioEstadoRequest;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_TareaEstado;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
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
}
