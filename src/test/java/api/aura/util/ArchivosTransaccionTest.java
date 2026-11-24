package api.aura.util;

import api.aura.entity.Contrato;
import api.aura.service.ContratoFileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los archivos en disco siguen el desenlace de la transacción: un borrado solo se
 * hace si el cambio en BD se confirma, y lo recién escrito se retira si se deshace.
 */
class ArchivosTransaccionTest {

    @TempDir
    Path directorio;

    /** Transacción sin recursos: basta para que se ejecuten las sincronizaciones. */
    private static final class TransaccionDePrueba extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private final TransactionTemplate transaccion = new TransactionTemplate(new TransaccionDePrueba());

    private Path archivo(String nombre) throws Exception {
        return Files.writeString(directorio.resolve(nombre), "contenido");
    }

    @Test
    void el_borrado_espera_al_commit() throws Exception {
        Path ruta = archivo("a.txt");

        transaccion.executeWithoutResult(estado -> {
            ArchivosTransaccion.borrarTrasConfirmar(ruta);
            assertThat(ruta).exists();
        });

        assertThat(ruta).doesNotExist();
    }

    @Test
    void si_la_transaccion_se_deshace_el_archivo_se_conserva() throws Exception {
        Path ruta = archivo("a.txt");

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> {
            ArchivosTransaccion.borrarTrasConfirmar(ruta);
            throw new IllegalStateException("falla el borrado en BD");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(ruta).exists();
    }

    @Test
    void lo_recien_escrito_se_retira_solo_si_se_deshace() throws Exception {
        Path confirmado = archivo("confirmado.txt");
        Path deshecho = archivo("deshecho.txt");

        transaccion.executeWithoutResult(estado -> ArchivosTransaccion.borrarSiSeDeshace(confirmado));
        transaccion.executeWithoutResult(estado -> {
            ArchivosTransaccion.borrarSiSeDeshace(deshecho);
            estado.setRollbackOnly();
        });

        assertThat(confirmado).exists();
        assertThat(deshecho).doesNotExist();
    }

    @Test
    void sin_transaccion_se_borra_en_el_acto_como_antes() throws Exception {
        Path ruta = archivo("a.txt");

        ArchivosTransaccion.borrarTrasConfirmar(ruta);
        ArchivosTransaccion.borrarSiSeDeshace(archivo("b.txt"));

        assertThat(ruta).doesNotExist();
        assertThat(directorio.resolve("b.txt")).exists();
    }

    @Test
    void sustituir_el_pdf_de_un_contrato_y_deshacer_conserva_el_anterior() {
        ContratoFileStorageService servicio = new ContratoFileStorageService(directorio.toString());
        Contrato contrato = new Contrato();
        contrato.setId(77);
        servicio.guardarPdf(contrato, pdf("primero.pdf"));
        Path primero = Path.of(contrato.getRutaArchivoContrato());
        String[] segundo = new String[1];

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> {
            servicio.guardarPdf(contrato, pdf("segundo.pdf"));
            segundo[0] = contrato.getRutaArchivoContrato();
            throw new IllegalStateException("falla el guardado del contrato");
        })).isInstanceOf(IllegalStateException.class);

        // La fila del contrato sigue apuntando al primero, que existe; el segundo no queda huérfano
        assertThat(primero).exists();
        assertThat(Path.of(segundo[0])).doesNotExist();
    }

    private static MockMultipartFile pdf(String nombre) {
        return new MockMultipartFile("archivo", nombre, "application/pdf",
                "%PDF-1.4 contenido".getBytes(StandardCharsets.US_ASCII));
    }
}
