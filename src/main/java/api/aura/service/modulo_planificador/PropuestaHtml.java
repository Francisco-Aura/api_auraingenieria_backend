package api.aura.service.modulo_planificador;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Presentación HTML de la propuesta al cliente de un presupuesto validado (equivale a proposal_html.py).
 *
 * <p>La plantilla es {@code propuesta/propuesta.html} (diapositivas animadas, A4 apaisado, imprimible a PDF). Aquí se rellena con los datos del
 * presupuesto y se devuelve un único archivo con las imágenes incrustadas, para poder mandarlo o abrirlo sin carpetas. Lo que el presupuesto
 * no tiene (dirección del cliente, logo…) se deja en blanco: la plantilla lo omite o lo deja para rellenar.
 */
@Component
public class PropuestaHtml {

    private static final String RUTA = "propuesta/";
    private static final Pattern BLOQUE_DATOS = Pattern.compile("(<script id=\"datos\" type=\"application/json\">)(.*?)(</script>)", Pattern.DOTALL);
    private static final Pattern IMAGEN_FIJA = Pattern.compile("src=\"((?:logos|proyectos)/[^\"$]+|equipo\\.jpg|marca\\.png|marca-gris\\.png|portada-foto\\.jpg|logo-aura\\.png)\"");
    private static final Pattern IMAGEN_CALCULADA = Pattern.compile("src=\"((?:logos|proyectos)/\\$\\{[^\"]+\\}\\.(?:png|jpg))\"");
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, byte[]> activos = new HashMap<>();
    private String plantilla;

    private static String tipoDe(String nombre) {
        String n = nombre.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private synchronized void cargar() {
        if (plantilla != null) return;
        try {
            PathMatchingResourcePatternResolver r = new PathMatchingResourcePatternResolver();
            // Como read_text() de Python: los saltos de línea del archivo pasan a LF, se hayan guardado como se hayan guardado
            plantilla = new String(r.getResource("classpath:" + RUTA + "propuesta.html").getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace('\r', '\n');
            for (Resource recurso : r.getResources("classpath:" + RUTA + "**")) {
                if (!recurso.isReadable() || recurso.getFilename() == null) continue;
                String url = recurso.getURL().toString();
                String relativa = url.substring(url.lastIndexOf("/" + RUTA) + 1 + RUTA.length());
                if (relativa.contains(".") && !relativa.endsWith("/")) activos.put(relativa, recurso.getInputStream().readAllBytes());
            }
        } catch (IOException e) {
            plantilla = null;
            throw new IllegalStateException("Falta la plantilla de la propuesta: " + e.getMessage());
        }
    }

    /** Una imagen de la propuesta por su ruta relativa («fondo.png», «logos/x.png»); null si no existe. */
    public byte[] imagen(String relativa) {
        cargar();
        return activos.get(relativa);
    }

    private String datos(String relativa) {
        byte[] bytes = activos.get(relativa);
        if (bytes == null) throw new IllegalStateException("Falta la imagen de la propuesta: " + relativa);
        return "data:" + tipoDe(relativa) + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static String sustituir(Pattern p, String entrada, java.util.function.Function<Matcher, String> f) {
        Matcher m = p.matcher(entrada);
        StringBuilder sb = new StringBuilder();
        int ultimo = 0;
        while (m.find()) {
            sb.append(entrada, ultimo, m.start()).append(f.apply(m));
            ultimo = m.end();
        }
        return sb.append(entrada.substring(ultimo)).toString();
    }

    /** Pasa las imágenes de rutas relativas a datos incrustados (las del HTML, las armadas en el JavaScript con el nombre calculado y las fijas). */
    String incrustarImagenes(String html) {
        html = sustituir(IMAGEN_FIJA, html, m -> "src=\"" + datos(m.group(1)) + "\"");
        List<String> nombres = new ArrayList<>();
        for (String n : activos.keySet()) {
            if (n.startsWith("logos/") || n.startsWith("proyectos/")) nombres.add(n);
        }
        Collections.sort(nombres);
        StringBuilder tabla = new StringBuilder("const ASSETS = {");
        for (int i = 0; i < nombres.size(); i++) {
            if (i > 0) tabla.append(',');
            tabla.append('"').append(nombres.get(i)).append("\":\"").append(datos(nombres.get(i))).append('"');
        }
        tabla.append("}\n");
        int punto = html.indexOf("const D = JSON.parse(");
        if (punto >= 0) html = html.substring(0, punto) + tabla + html.substring(punto);
        return sustituir(IMAGEN_CALCULADA, html, m -> "src=\"${ASSETS[`" + m.group(1) + "`]}\"");
    }

    /** La plantilla con los datos indicados y con las imágenes incrustadas. */
    public String generar(Map<String, Object> datosPropuesta) {
        cargar();
        String html = plantilla;
        try {
            String carga = json.writeValueAsString(datosPropuesta).replace("</", "<\\/");
            html = sustituir(BLOQUE_DATOS, html, m -> m.group(1) + "\n" + carga + "\n" + m.group(3));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudieron preparar los datos de la propuesta");
        }
        return incrustarImagenes(html);
    }
}
