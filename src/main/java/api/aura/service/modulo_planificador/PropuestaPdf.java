package api.aura.service.modulo_planificador;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * La propuesta en PDF: es la presentación HTML impresa por un Chrome o Chromium sin ventana (A4 apaisado, con la página de «Aceptación y
 * firma», que solo sale al imprimir). Hace falta que el servidor tenga uno instalado; si no, {@link #disponible()} es falso y quien llama
 * puede ofrecer el HTML para imprimirlo desde el navegador. La ruta se puede fijar con {@code planificador.chrome} (variable de entorno
 * {@code PLANIFICADOR_CHROME}); si no, se busca en los sitios habituales.
 */
@Component
public class PropuestaPdf {

    private static final List<String> HABITUALES = List.of(
            "/usr/bin/google-chrome", "/usr/bin/google-chrome-stable", "/usr/bin/chromium", "/usr/bin/chromium-browser", "/snap/bin/chromium",
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe", "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe");
    private static final long ESPERA_SEGUNDOS = 120;

    private final String rutaConfigurada;

    public PropuestaPdf(@Value("${planificador.chrome:}") String rutaConfigurada) {
        this.rutaConfigurada = rutaConfigurada == null ? "" : rutaConfigurada.strip();
    }

    /** Que no se pueda generar el PDF por no haber navegador (el resto de fallos son errores de verdad). */
    public static class SinNavegador extends RuntimeException {
        public SinNavegador() {
            super("El servidor no tiene Chrome ni Chromium para generar el PDF.");
        }
    }

    /** El navegador que se usará, o null si no hay ninguno. */
    String navegador() {
        List<String> candidatos = new ArrayList<>();
        if (!rutaConfigurada.isEmpty()) candidatos.add(rutaConfigurada);
        candidatos.addAll(HABITUALES);
        for (String c : candidatos) {
            if (Files.isRegularFile(Path.of(c))) return c;
        }
        return null;
    }

    public boolean disponible() {
        return navegador() != null;
    }

    /** Imprime el HTML a PDF. Lanza {@link SinNavegador} si el servidor no tiene navegador. */
    public byte[] generar(String html) {
        String chrome = navegador();
        if (chrome == null) throw new SinNavegador();
        Path carpeta = null;
        try {
            carpeta = Files.createTempDirectory("propuesta-pdf");
            Path entrada = carpeta.resolve("propuesta.html");
            Path salida = carpeta.resolve("propuesta.pdf");
            Files.writeString(entrada, html, StandardCharsets.UTF_8);
            List<String> orden = List.of(chrome, "--headless=new", "--disable-gpu", "--no-sandbox", "--no-pdf-header-footer",
                    "--user-data-dir=" + carpeta.resolve("perfil"), "--virtual-time-budget=20000",
                    "--print-to-pdf=" + salida, entrada.toUri().toString());
            Process proceso = new ProcessBuilder(orden).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!proceso.waitFor(ESPERA_SEGUNDOS, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                throw new IllegalStateException("el navegador tardó más de " + ESPERA_SEGUNDOS + " segundos");
            }
            if (!Files.isRegularFile(salida) || Files.size(salida) == 0) {
                throw new IllegalStateException("el navegador no generó el archivo");
            }
            return Files.readAllBytes(salida);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("se interrumpió la generación del PDF", e);
        } finally {
            borrar(carpeta);
        }
    }

    private static void borrar(Path carpeta) {
        if (carpeta == null) return;
        try (Stream<Path> todo = Files.walk(carpeta)) {
            todo.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignorada) {
            // un temporal que no se borra no debe romper la descarga
        }
    }
}
