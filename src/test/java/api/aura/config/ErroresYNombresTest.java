package api.aura.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Mensajes de clave ajena y nombre de usuario en los historiales. */
class ErroresYNombresTest {

    @Test
    void la_clave_ajena_distingue_borrar_algo_en_uso_de_guardar_algo_que_ya_no_existe() throws Exception {
        GlobalExceptionHandler manejador = new GlobalExceptionHandler();
        var traducir = GlobalExceptionHandler.class.getDeclaredMethod("traducirErrorBD",
                org.springframework.dao.DataIntegrityViolationException.class);
        traducir.setAccessible(true);

        String alGuardar = (String) traducir.invoke(manejador, new org.springframework.dao.DataIntegrityViolationException(
                "Cannot add or update a child row: a foreign key constraint fails (`aura`.`cliente_notas`, CONSTRAINT `fk` "
                        + "FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`))"));
        String alBorrar = (String) traducir.invoke(manejador, new org.springframework.dao.DataIntegrityViolationException(
                "Cannot delete or update a parent row: a foreign key constraint fails (`aura`.`punto_suministro`, CONSTRAINT `fk` "
                        + "FOREIGN KEY (`TBmonitorizacion_id`) REFERENCES `tbmonitorizacion` (`id`))"));

        assertThat(alGuardar).contains("referencia a un dato que no existe");
        assertThat(alBorrar).contains("está siendo utilizado");
    }

    @Test
    void el_nombre_para_los_historiales_no_pasa_de_su_columna() throws Exception {
        String fuente = Files.readString(Path.of("src/main/java/api/aura/config/AuraUserDataSource.java"));
        assertThat(AuraUserDataSource.LONGITUD_NOMBRE_HISTORIAL).isEqualTo(100);
        assertThat(fuente).contains("nombre.substring(0, LONGITUD_NOMBRE_HISTORIAL)");
    }
}
