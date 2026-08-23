package api.aura.service.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Adjunto;
import api.aura.entity.modulo_tareas.mt_Comentario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageServiceTest {

    @TempDir
    Path uploadDirectory;

    @Test
    void repeatedUploadsWithSameOriginalNameCreateDistinctPhysicalFiles() throws Exception {
        FileStorageService service = new FileStorageService();
        ReflectionTestUtils.setField(service, "baseUploadDir", uploadDirectory.toString());
        mt_Tarea tarea = new mt_Tarea();
        tarea.setId(42L);

        mt_Adjunto first = service.guardarAdjuntoTarea(
                new MockMultipartFile("file", "informe.pdf", "application/pdf", "first".getBytes()), tarea);
        mt_Adjunto second = service.guardarAdjuntoTarea(
                new MockMultipartFile("file", "informe.pdf", "application/pdf", "second".getBytes()), tarea);

        assertThat(first.getNombreArchivo()).isEqualTo("informe.pdf");
        assertThat(second.getNombreArchivo()).isEqualTo("informe.pdf");
        assertThat(first.getRutaArchivo()).isNotEqualTo(second.getRutaArchivo());
        assertThat(Files.readString(Path.of(first.getRutaArchivo()))).isEqualTo("first");
        assertThat(Files.readString(Path.of(second.getRutaArchivo()))).isEqualTo("second");
    }

    @Test
    void repeatedCommentUploadsWithSameOriginalNameCreateDistinctPhysicalFiles() throws Exception {
        FileStorageService service = new FileStorageService();
        ReflectionTestUtils.setField(service, "baseUploadDir", uploadDirectory.toString());
        mt_Tarea tarea = new mt_Tarea();
        tarea.setId(43L);
        mt_Comentario comentario = new mt_Comentario();
        comentario.setTarea(tarea);

        mt_Adjunto first = service.guardarAdjunto(
                new MockMultipartFile("file", "foto.png", "image/png", "first".getBytes()), comentario);
        mt_Adjunto second = service.guardarAdjunto(
                new MockMultipartFile("file", "foto.png", "image/png", "second".getBytes()), comentario);

        assertThat(first.getNombreArchivo()).isEqualTo("foto.png");
        assertThat(second.getNombreArchivo()).isEqualTo("foto.png");
        assertThat(first.getRutaArchivo()).isNotEqualTo(second.getRutaArchivo());
        assertThat(Files.readString(Path.of(first.getRutaArchivo()))).isEqualTo("first");
        assertThat(Files.readString(Path.of(second.getRutaArchivo()))).isEqualTo("second");
    }
}
