package api.aura.service.modulo_tareas;

import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Adjunto;
import api.aura.entity.modulo_tareas.mt_Comentario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_TareaEstado;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.AdjuntoRepository;
import api.aura.repository.modulo_tareas.mt_ComentarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Borrar una tarea borra los archivos de sus adjuntos, pero solo cuando el borrado
 * en BD queda confirmado. Antes se borraban primero: si después fallaba el borrado
 * de la tarea, esta seguía existiendo con adjuntos cuyos archivos ya no estaban.
 */
@SpringBootTest
class TareaEliminarArchivosTest {

    @Autowired private TareaService tareaService;
    @Autowired private mt_TareaRepository tareas;
    @Autowired private mt_TareaEstadoRepository estados;
    @Autowired private mt_ComentarioRepository comentarios;
    @Autowired private AdjuntoRepository adjuntos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private JdbcTemplate jdbc;

    // Dentro del directorio de adjuntos de la aplicación (file.upload.base-dir)
    private final Path carpeta = Paths.get("uploads/tareas/prueba-" + UUID.randomUUID());
    private mt_TareaEstado pendiente;
    private Usuario usuario;
    private mt_Tarea tarea;
    private Path archivo;

    @BeforeEach
    void preparar() throws Exception {
        pendiente = estados.save(mt_TareaEstado.builder().nombre("PENDIENTE-BORRADO-" + System.nanoTime())
                .estadoFinal(false).orden(60).cuentaTiempo(false).esBloqueo(false).build());
        usuario = usuarios.save(Usuario.builder().nombre("Usuario borrado tareas").build());
        tarea = tareas.save(mt_Tarea.builder().titulo("Tarea con adjunto").prioridad(2).notificar(false)
                .enviarCorreo(false).estado(pendiente).usuarioAsignado(usuario).usuarioCreador(usuario).build());
        mt_Comentario comentario = comentarios.save(mt_Comentario.builder().tarea(tarea).usuarioAutor(usuario)
                .comentario("Adjunto el plano").build());
        Files.createDirectories(carpeta);
        archivo = Files.writeString(carpeta.resolve("plano.pdf"), "%PDF-1.4");
        adjuntos.save(mt_Adjunto.builder().comentario(comentario).nombreArchivo("plano.pdf")
                .rutaArchivo(archivo.toString()).tipoMime("application/pdf").tamanio(8L).build());
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM modulotareas_notificaciones WHERE tarea_id = ?", tarea.getId());
        if (tareas.existsById(tarea.getId())) {
            tareaService.eliminar(tarea.getId());
        }
        usuarios.delete(usuario);
        estados.delete(pendiente);
        FileSystemUtils.deleteRecursively(carpeta.toFile());
        // Las carpetas base solo se retiran si la prueba las dejó vacías
        for (Path base = carpeta.getParent(); base != null; base = base.getParent()) {
            try {
                Files.deleteIfExists(base);
            } catch (java.io.IOException noVacia) {
                break;
            }
        }
    }

    @Test
    void al_borrar_la_tarea_se_borran_los_archivos_de_sus_adjuntos() {
        tareaService.eliminar(tarea.getId());

        assertThat(tareas.existsById(tarea.getId())).isFalse();
        assertThat(archivo).doesNotExist();
    }

    @Test
    void si_el_borrado_en_bd_falla_la_tarea_conserva_sus_archivos() {
        // Una fila de otra tabla que referencia la tarea sin borrado en cascada impide borrarla
        jdbc.execute("CREATE TABLE prueba_ref_tarea (tarea_id BIGINT REFERENCES modulotareas_tareas(id))");
        try {
            jdbc.update("INSERT INTO prueba_ref_tarea (tarea_id) VALUES (?)", tarea.getId());

            assertThatThrownBy(() -> tareaService.eliminar(tarea.getId())).isInstanceOf(DataAccessException.class);

            assertThat(tareas.existsById(tarea.getId())).isTrue();
            assertThat(archivo).exists();
        } finally {
            jdbc.execute("DROP TABLE prueba_ref_tarea");
        }
    }
}
