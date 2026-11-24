package api.aura.service.modulo_tareas;

import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_TareaEstado;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Notificar 1h antes» con la BD de verdad: un error de BD al avisar de una tarea
 * no se lleva por delante los avisos del resto de la pasada. Antes iba todo en una
 * transacción, el error la marcaba para deshacer y el commit final fallaba con
 * UnexpectedRollbackException: ninguna tarea quedaba avisada.
 */
@SpringBootTest
class TareaSchedulerTransaccionTest {

    private static final String RESTRICCION = "chk_prueba_aviso_vencimiento";

    @Autowired private TareaSchedulerService scheduler;
    @Autowired private mt_TareaRepository tareas;
    @Autowired private mt_TareaEstadoRepository estados;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private JdbcTemplate jdbc;

    private mt_TareaEstado pendiente;
    private Usuario asignado;
    private Usuario administrador;
    private mt_Tarea conFallo;
    private mt_Tarea correcta;

    @BeforeEach
    void preparar() {
        pendiente = estados.save(mt_TareaEstado.builder().nombre("PENDIENTE-AVISO-" + System.nanoTime())
                .estadoFinal(false).orden(50).cuentaTiempo(false).esBloqueo(false).build());
        asignado = usuarios.save(Usuario.builder().nombre("Usuario aviso vencimiento").build());
        administrador = usuarios.save(Usuario.builder().nombre("Administrador aviso vencimiento").nivelAcceso(1).build());
        LocalDateTime enMediaHora = LocalDateTime.now().plusMinutes(30);
        conFallo = tareas.save(tarea("FALLO-BD revisar contador", enMediaHora));
        correcta = tareas.save(tarea("Llamar al cliente", enMediaHora.plusMinutes(5)));
        // El aviso de la primera tarea choca con una restricción de la BD
        jdbc.execute("ALTER TABLE modulotareas_notificaciones ADD CONSTRAINT " + RESTRICCION
                + " CHECK (mensaje NOT LIKE '%FALLO-BD%')");
    }

    private mt_Tarea tarea(String titulo, LocalDateTime vencimiento) {
        return mt_Tarea.builder().titulo(titulo).prioridad(2).notificar(true).enviarCorreo(false)
                .estado(pendiente).usuarioAsignado(asignado).usuarioCreador(asignado)
                .fechaVencimiento(vencimiento).build();
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("ALTER TABLE modulotareas_notificaciones DROP CONSTRAINT IF EXISTS " + RESTRICCION);
        jdbc.update("DELETE FROM modulotareas_notificaciones WHERE tarea_id IN (?, ?)", conFallo.getId(),
                correcta.getId());
        tareas.deleteAllById(java.util.List.of(conFallo.getId(), correcta.getId()));
        usuarios.delete(asignado);
        usuarios.delete(administrador);
        estados.delete(pendiente);
    }

    private long avisosDe(mt_Tarea tarea, Usuario destinatario) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM modulotareas_notificaciones WHERE tarea_id = ? "
                + "AND usuario_id = ?", Long.class, tarea.getId(), destinatario.getId());
    }

    @Test
    void un_error_de_bd_con_una_tarea_no_pierde_el_aviso_de_las_demas() {
        scheduler.verificarTareasProximasAVencer();

        assertThat(avisosDe(correcta, asignado)).isEqualTo(1);
        assertThat(avisosDe(correcta, administrador)).isEqualTo(1);
        assertThat(tareas.findById(correcta.getId()).orElseThrow().getNotificadoProximoVencimiento()).isTrue();
        // La tarea que falló no queda marcada: se reintentará en la siguiente pasada
        assertThat(avisosDe(conFallo, asignado)).isZero();
        assertThat(avisosDe(conFallo, administrador)).isZero();
        assertThat(tareas.findById(conFallo.getId()).orElseThrow().getNotificadoProximoVencimiento()).isFalse();
    }
}
