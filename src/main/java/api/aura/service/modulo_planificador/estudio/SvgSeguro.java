package api.aura.service.modulo_planificador.estudio;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deja pasar una gráfica SVG dibujada por la pantalla para meterla en el informe que el servidor convierte a PDF con Chrome. Como el
 * SVG llega del navegador no se fía de él: se lee como XML (sin DTD ni entidades), solo se conservan unos pocos elementos y atributos de
 * dibujo y se descarta todo lo que pueda cargar algo de fuera o ejecutar algo (scripts, enlaces, estilos, imágenes, {@code url()} salvo
 * las plantillas internas).
 */
public final class SvgSeguro {

    public static final int MAX_CARACTERES = 300_000;

    private static final Set<String> ELEMENTOS = Set.of("svg", "g", "defs", "pattern", "path", "rect", "line", "circle", "text", "tspan");
    private static final Set<String> ATRIBUTOS = Set.of("class", "id", "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "width", "height", "d",
            "fill", "stroke", "stroke-width", "stroke-dasharray", "stroke-linejoin", "stroke-linecap", "opacity", "fill-opacity", "transform", "patternunits",
            "patterntransform", "text-anchor", "dy", "viewbox", "preserveaspectratio", "role", "aria-label");
    private static final Pattern CLASE = Pattern.compile("[A-Za-z0-9 _-]{0,80}");
    private static final Pattern ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,40}");
    /** Valores de dibujo: números, colores (rgb, hex, var(--x)), listas de números, transformaciones y trazados. */
    private static final Pattern VALOR = Pattern.compile("[A-Za-z0-9#.,()\\s%+\\-_:]*");
    private static final Pattern TRAZADO = Pattern.compile("[MLZmlz0-9.,\\s\\-eE+]*");
    private static final Pattern FILL_URL = Pattern.compile("url\\(#[A-Za-z][A-Za-z0-9_-]{0,40}\\)");

    private SvgSeguro() {
    }

    /** El SVG limpio, o null si no es un SVG válido o es demasiado grande. */
    public static String limpiar(String svg) {
        if (svg == null || svg.isBlank() || svg.length() > MAX_CARACTERES) return null;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setNamespaceAware(false);
            Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
            Element raiz = doc.getDocumentElement();
            if (!"svg".equals(raiz.getTagName())) return null;
            StringBuilder sb = new StringBuilder(svg.length());
            volcar(raiz, sb, true);
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void volcar(Element e, StringBuilder sb, boolean raiz) {
        sb.append('<').append(e.getTagName());
        if (raiz) sb.append(" xmlns=\"http://www.w3.org/2000/svg\"");
        NamedNodeMap atrs = e.getAttributes();
        for (int i = 0; i < atrs.getLength(); i++) {
            Node a = atrs.item(i);
            String nombre = a.getNodeName().toLowerCase();
            String valor = a.getNodeValue();
            if (!ATRIBUTOS.contains(nombre) || !valorValido(nombre, valor)) continue;
            sb.append(' ').append(a.getNodeName()).append("=\"").append(escapar(valor)).append('"');
        }
        sb.append('>');
        NodeList hijos = e.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            Node h = hijos.item(i);
            if (h instanceof Element el) {
                if (ELEMENTOS.contains(el.getTagName())) volcar(el, sb, false);
            } else if (h.getNodeType() == Node.TEXT_NODE && ("text".equals(e.getTagName()) || "tspan".equals(e.getTagName()))) {
                sb.append(escapar(h.getNodeValue()));
            }
        }
        sb.append("</").append(e.getTagName()).append('>');
    }

    private static boolean valorValido(String nombre, String valor) {
        if (valor.length() > 20_000) return false;
        return switch (nombre) {
            case "class" -> CLASE.matcher(valor).matches();
            case "id" -> ID.matcher(valor).matches();
            case "d" -> TRAZADO.matcher(valor).matches();
            case "role", "aria-label" -> valor.length() < 200 && !valor.contains("<");
            case "fill", "stroke" -> FILL_URL.matcher(valor).matches() || (!valor.contains("url") && VALOR.matcher(valor).matches());
            default -> !valor.toLowerCase().contains("url") && VALOR.matcher(valor).matches();
        };
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
