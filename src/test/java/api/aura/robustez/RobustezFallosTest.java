package api.aura.robustez;

import api.aura.service.InformesService;
import api.aura.util.ArchivosTransaccion;
import api.aura.util.NombresArchivo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lo que pasa cuando algo falla: sin restos en disco, sin éxitos falsos. */
class RobustezFallosTest {

    @TempDir
    Path carpeta;

    /** Entrega unos bytes y luego falla, como una subida que se corta o un disco lleno. */
    private static InputStream queFallaAMedias() {
        return new InputStream() {
            int leidos;
            @Override
            public int read() throws IOException {
                if (leidos++ < 5000) return 'x';
                throw new IOException("No queda espacio en el dispositivo");
            }
        };
    }

    @Test
    void una_copia_que_falla_a_medias_no_deja_el_archivo_incompleto() {
        Path destino = carpeta.resolve("adjunto.pdf");
        assertThatThrownBy(() -> ArchivosTransaccion.copiar(queFallaAMedias(), destino))
                .isInstanceOf(IOException.class).hasMessageContaining("espacio");
        assertThat(destino).doesNotExist();
    }

    @Test
    void si_el_destino_ya_existia_no_se_toca() throws IOException {
        Path destino = carpeta.resolve("de-otro.pdf");
        Files.writeString(destino, "contenido bueno");
        assertThatThrownBy(() -> ArchivosTransaccion.copiar(new ByteArrayInputStream(new byte[]{1}), destino))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(destino).hasContent("contenido bueno");
    }

    @Test
    void una_copia_correcta_queda_igual_que_antes() throws IOException {
        Path destino = carpeta.resolve("bien.txt");
        ArchivosTransaccion.copiar(new ByteArrayInputStream("hola".getBytes(StandardCharsets.UTF_8)), destino);
        assertThat(destino).hasContent("hola");
    }

    @Test
    void un_nombre_largo_se_recorta_a_255_bytes_sin_perder_la_extension() {
        String largo = "20261016_101500_" + "0".repeat(36) + "_" + "Ñandú informe trimestral ".repeat(12) + ".pdf";
        String recortado = NombresArchivo.recortarBytes(largo, 255);
        assertThat(recortado.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(255);
        assertThat(recortado).endsWith(".pdf").startsWith("20261016_101500_");
        assertThat(largo).startsWith(recortado.substring(0, recortado.length() - 4));
        // Sin partir un carácter de varios bytes por la mitad
        assertThat(new String(recortado.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)).isEqualTo(recortado);
    }

    @Test
    void un_nombre_que_ya_cabe_no_cambia() {
        assertThat(NombresArchivo.recortarBytes("20261016_x_factura.pdf", 255)).isEqualTo("20261016_x_factura.pdf");
        assertThat(NombresArchivo.recortarBytes(null, 255)).isNull();
    }

    private InformesService informesCon(JdbcTemplate jdbc) {
        InformesService servicio = new InformesService(null, null, null, null, null);
        ReflectionTestUtils.setField(servicio, "jdbcTemplate", jdbc);
        return servicio;
    }

    @Test
    void marcar_un_pago_de_un_servicio_sin_plan_ya_no_dice_que_ha_ido_bien() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        assertThatThrownBy(() -> informesCon(jdbc).actualizarPagoParcial(9L, 2, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ya no tiene plan de pagos");
    }

    @Test
    void marcar_un_pago_que_ya_estaba_marcado_sigue_yendo_bien() {
        // Con useAffectedRows la BD devuelve 0 si el valor no cambia: el plan existe y no es un error
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        informesCon(jdbc).actualizarPagoParcial(9L, 2, true);
    }

    @Test
    void marcar_un_pago_normal_no_hace_consultas_de_mas() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        informesCon(jdbc).actualizarPagoParcial(9L, 2, true);
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
    }
}
