package api.aura.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Los ficheros en disco no forman parte de la transacción de la BD. Borrar un
 * fichero antes del COMMIT y que después la transacción se deshaga deja la fila
 * apuntando a un archivo que ya no existe; escribirlo y que se deshaga deja un
 * fichero huérfano. Estas utilidades atan la operación de disco al desenlace de
 * la transacción en curso.
 *
 * <p>Sin transacción activa (pruebas unitarias, llamadas sueltas) se comportan
 * como antes: el borrado se hace en el acto y no hay nada que deshacer.</p>
 */
public final class ArchivosTransaccion {

    private static final Logger log = LoggerFactory.getLogger(ArchivosTransaccion.class);

    /** Acción sobre disco que puede fallar con una IOException. */
    @FunctionalInterface
    public interface AccionDisco {
        void ejecutar() throws IOException;
    }

    private ArchivosTransaccion() {
    }

    /**
     * Ejecuta el borrado cuando la transacción en curso se confirma; si se deshace,
     * el fichero se conserva (la fila sigue apuntando a él). Sin transacción, en el
     * acto y propagando el error como antes.
     *
     * <p>Tras el COMMIT ya no hay vuelta atrás: un fallo al borrar solo se registra
     * (queda un fichero huérfano, nunca una fila sin su fichero).</p>
     */
    public static void borrarTrasConfirmar(String descripcion, AccionDisco borrado) throws IOException {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            borrado.ejecutar();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ejecutarSinPropagar(descripcion, borrado);
            }
        });
    }

    /** Igual que {@link #borrarTrasConfirmar(String, AccionDisco)} con {@code Files.deleteIfExists}. */
    public static void borrarTrasConfirmar(Path ruta) {
        try {
            borrarTrasConfirmar(String.valueOf(ruta), () -> Files.deleteIfExists(ruta));
        } catch (IOException e) {
            // Sin transacción: mismo trato que el borrado directo de antes (no crítico)
            log.warn("No se pudo eliminar el archivo {}: {}", ruta, e.getMessage());
        }
    }

    /**
     * Copia el contenido en {@code destino}. Si la copia falla a medias (disco lleno, se
     * corta la subida...), borra lo que llegó a escribirse: antes quedaba un archivo
     * incompleto en el disco al que no apuntaba nadie.
     */
    public static void copiar(InputStream contenido, Path destino, CopyOption... opciones) throws IOException {
        try {
            Files.copy(contenido, destino, opciones);
        } catch (FileAlreadyExistsException yaExistia) {
            throw yaExistia; // no se escribió nada, y lo que hay no es nuestro
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(destino);
            } catch (IOException borrado) {
                e.addSuppressed(borrado);
            }
            throw e;
        }
    }

    /**
     * Si la transacción en curso se deshace, borra el fichero recién escrito para que
     * no quede huérfano. Sin transacción no hace nada.
     */
    public static void borrarSiSeDeshace(Path ruta) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                if (estado == STATUS_ROLLED_BACK) {
                    ejecutarSinPropagar(String.valueOf(ruta), () -> Files.deleteIfExists(ruta));
                }
            }
        });
    }

    private static void ejecutarSinPropagar(String descripcion, AccionDisco accion) {
        try {
            accion.ejecutar();
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo eliminar el archivo {}: {}", descripcion, e.getMessage());
        }
    }
}
