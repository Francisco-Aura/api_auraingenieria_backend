package api.aura.service;

import api.aura.entity.Contrato;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guardado del PDF del contrato: es la unica parte de contratos que escribe en disco
 * y no tenia ninguna prueba.
 */
class ContratoFileStorageServiceTest {

    @TempDir
    Path directorio;

    private ContratoFileStorageService servicio() {
        return new ContratoFileStorageService(directorio.toString());
    }

    private MockMultipartFile pdf(String nombre) {
        return new MockMultipartFile("archivo", nombre, "application/pdf",
                "%PDF-1.4 contenido".getBytes(StandardCharsets.US_ASCII));
    }

    private Contrato contrato() {
        Contrato contrato = new Contrato();
        contrato.setId(77);
        return contrato;
    }

    @Test
    void guarda_el_pdf_con_nombre_propio_y_anota_los_metadatos() {
        Contrato contrato = contrato();

        servicio().guardarPdf(contrato, pdf("Contrato firmado.pdf"));

        assertThat(contrato.getNombreArchivo()).isEqualTo("Contrato firmado.pdf");
        assertThat(contrato.getTipoMimeArchivo()).isEqualTo("application/pdf");
        assertThat(contrato.getTamanioArchivo()).isEqualTo(18L);
        Path guardado = Path.of(contrato.getRutaArchivoContrato());
        assertThat(guardado).exists();
        // El nombre en disco no es el que envia el usuario.
        assertThat(guardado.getFileName().toString()).endsWith(".pdf").doesNotContain("Contrato");
        assertThat(guardado.getParent().getFileName().toString()).isEqualTo("contrato_77");
    }

    @Test
    void un_nombre_con_rutas_no_saca_el_archivo_de_su_carpeta() {
        Contrato contrato = contrato();

        servicio().guardarPdf(contrato, pdf("../../../etc/passwd.pdf"));

        assertThat(contrato.getNombreArchivo()).isEqualTo("passwd.pdf");
        assertThat(Path.of(contrato.getRutaArchivoContrato()).normalize())
                .startsWith(directorio.toAbsolutePath().normalize());
    }

    @Test
    void sustituir_el_pdf_borra_el_anterior() {
        Contrato contrato = contrato();
        ContratoFileStorageService servicio = servicio();
        servicio.guardarPdf(contrato, pdf("primero.pdf"));
        Path primero = Path.of(contrato.getRutaArchivoContrato());

        servicio.guardarPdf(contrato, pdf("segundo.pdf"));

        assertThat(primero).doesNotExist();
        assertThat(Path.of(contrato.getRutaArchivoContrato())).exists();
    }

    @Test
    void rechaza_lo_que_no_es_un_pdf() {
        ContratoFileStorageService servicio = servicio();
        Contrato contrato = contrato();

        assertThatThrownBy(() -> servicio.guardarPdf(contrato,
                new MockMultipartFile("archivo", "hoja.xlsx", "application/pdf",
                        "%PDF-".getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("debe ser un PDF");

        assertThatThrownBy(() -> servicio.guardarPdf(contrato,
                new MockMultipartFile("archivo", "falso.pdf", "application/pdf",
                        "PKzip-no-es-pdf".getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no corresponde a un PDF");

        assertThatThrownBy(() -> servicio.guardarPdf(contrato,
                new MockMultipartFile("archivo", "vacio.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Debe seleccionar");
    }

    @Test
    void eliminar_el_pdf_borra_el_fichero_y_limpia_la_ficha() {
        Contrato contrato = contrato();
        ContratoFileStorageService servicio = servicio();
        servicio.guardarPdf(contrato, pdf("borrame.pdf"));
        Path guardado = Path.of(contrato.getRutaArchivoContrato());

        servicio.eliminarPdfSiExiste(contrato);

        assertThat(guardado).doesNotExist();
        assertThat(contrato.getRutaArchivoContrato()).isNull();
        assertThat(contrato.getNombreArchivo()).isNull();
        assertThat(contrato.getTamanioArchivo()).isNull();
    }

    @Test
    void no_sirve_un_pdf_que_apunte_fuera_de_la_carpeta_de_subidas() throws Exception {
        Path fuera = Files.createTempFile("ajeno", ".pdf");
        Contrato contrato = contrato();
        contrato.setRutaArchivoContrato(fuera.toString());
        ContratoFileStorageService servicio = servicio();

        try {
            assertThatThrownBy(() -> servicio.obtenerPdf(contrato))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no est");
        } finally {
            Files.deleteIfExists(fuera);
        }
    }

    @Test
    void sin_pdf_adjunto_lo_dice_con_claridad() {
        ContratoFileStorageService servicio = servicio();
        Contrato contrato = contrato();

        assertThatThrownBy(() -> servicio.obtenerPdf(contrato))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no tiene un PDF adjunto");
    }
}
