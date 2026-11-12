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

    @Test
    void losArchivosSubidosSeCierranTrasCopiarlos() throws Exception {
        FileStorageService service = new FileStorageService();
        ReflectionTestUtils.setField(service, "baseUploadDir", uploadDirectory.toString());
        mt_Tarea tarea = new mt_Tarea();
        tarea.setId(44L);
        mt_Comentario comentario = new mt_Comentario();
        comentario.setTarea(tarea);
        ArchivoQueRegistraCierre deTarea = new ArchivoQueRegistraCierre();
        ArchivoQueRegistraCierre deComentario = new ArchivoQueRegistraCierre();

        service.guardarAdjuntoTarea(deTarea, tarea);
        service.guardarAdjunto(deComentario, comentario);

        assertThat(deTarea.flujoCerrado).isTrue();
        assertThat(deComentario.flujoCerrado).isTrue();
    }

    /** MultipartFile cuyo flujo recuerda si se ha cerrado (en Windows, un flujo abierto bloquea el temporal). */
    static class ArchivoQueRegistraCierre extends MockMultipartFile {
        boolean flujoCerrado;

        ArchivoQueRegistraCierre() {
            super("file", "acta.pdf", "application/pdf", "contenido".getBytes());
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream("contenido".getBytes()) {
                @Override
                public void close() {
                    flujoCerrado = true;
                }
            };
        }
    }

    @Test
    void un_nombre_de_archivo_con_rutas_no_escapa_de_la_carpeta_de_la_tarea() throws Exception {
        FileStorageService service = new FileStorageService();
        ReflectionTestUtils.setField(service, "baseUploadDir", uploadDirectory.toString());
        mt_Tarea tarea = new mt_Tarea();
        tarea.setId(44L);

        mt_Adjunto adjunto = service.guardarAdjuntoTarea(new MockMultipartFile(
                "file", "../../../secreto.txt", "text/plain", "contenido".getBytes()), tarea);

        Path guardado = Path.of(adjunto.getRutaArchivo()).toAbsolutePath().normalize();
        assertThat(guardado).startsWith(uploadDirectory.toAbsolutePath().normalize());
        assertThat(guardado.getFileName().toString()).endsWith("secreto.txt");
        assertThat(Files.readString(guardado)).isEqualTo("contenido");
    }

    @Test
    void los_adjuntos_de_tarea_y_de_comentario_se_guardan_en_la_misma_carpeta() throws Exception {
        FileStorageService service = new FileStorageService();
        ReflectionTestUtils.setField(service, "baseUploadDir", uploadDirectory.toString());
        mt_Tarea tarea = new mt_Tarea();
        tarea.setId(45L);
        mt_Comentario comentario = new mt_Comentario();
        comentario.setTarea(tarea);

        mt_Adjunto deTarea = service.guardarAdjuntoTarea(
                new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes()), tarea);
        mt_Adjunto deComentario = service.guardarAdjunto(
                new MockMultipartFile("file", "b.pdf", "application/pdf", "b".getBytes()), comentario);

        assertThat(Path.of(deTarea.getRutaArchivo()).getParent())
                .isEqualTo(Path.of(deComentario.getRutaArchivo()).getParent());
    }

}
