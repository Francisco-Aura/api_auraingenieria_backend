package api.aura.service.modulo_registro_horario.documento;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * El Word en memoria: el XML del documento (que se va rellenando), sus imágenes y el resto de partes de la plantilla
 * (estilos, ajustes...) tal cual. Nada se escribe en disco: la plantilla se lee del classpath y el resultado son bytes.
 */
public final class DocumentoWord {

    static final String RUTA_PLANTILLA = "/registro-horario/plantilla.docx";
    private static final String DOCUMENTO = "word/document.xml";
    private static final String RELACIONES = "word/_rels/document.xml.rels";
    private static final String TIPOS = "[Content_Types].xml";
    private static final String TIPO_IMAGEN = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";
    private static final String NS_RELACIONES = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final Pattern PARTE_IMAGEN = Pattern.compile("word/media/image(\\d+)\\.png");

    private final Map<String, byte[]> partes = new LinkedHashMap<>(); // todo menos el documento y sus relaciones
    private final Document documento;
    private final Document relaciones;
    private final Element cuerpo;

    private DocumentoWord(Map<String, byte[]> partes) {
        this.partes.putAll(partes);
        this.documento = leerXml(this.partes.remove(DOCUMENTO));
        this.relaciones = leerXml(this.partes.remove(RELACIONES));
        this.cuerpo = XmlDocx.hijoW(documento.getDocumentElement(), "body");
    }

    /** Una copia nueva de la plantilla mensual. */
    public static DocumentoWord desdePlantilla() {
        try (InputStream in = DocumentoWord.class.getResourceAsStream(RUTA_PLANTILLA)) {
            if (in == null) {
                throw new IllegalStateException("Falta la plantilla del registro horario");
            }
            Map<String, byte[]> partes = new LinkedHashMap<>();
            try (ZipInputStream zip = new ZipInputStream(in)) {
                for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                    if (!e.isDirectory()) {
                        partes.put(e.getName(), zip.readAllBytes());
                    }
                }
            }
            return new DocumentoWord(partes);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la plantilla del registro horario", e);
        }
    }

    public Document xml() {
        return documento;
    }

    public Element cuerpo() {
        return cuerpo;
    }

    /** Bytes de la imagen a la que apunta {@code r:embed="rIdN"}. */
    public byte[] imagen(String rId) {
        for (Element rel : XmlDocx.hijos(relaciones.getDocumentElement())) {
            if (rId.equals(rel.getAttribute("Id"))) {
                byte[] b = partes.get("word/" + rel.getAttribute("Target"));
                if (b != null) {
                    return b;
                }
            }
        }
        throw new IllegalArgumentException("No existe la imagen " + rId);
    }

    /** Siguiente identificador libre para un dibujo: el mayor número que haya en algún atributo {@code id} más uno. */
    int siguienteId() {
        long mayor = 0;
        for (Element e : XmlDocx.todos(documento.getDocumentElement())) {
            Attr id = e.getAttributeNode("id");
            if (id != null && id.getValue().matches("\\d+")) {
                mayor = Math.max(mayor, Long.parseLong(id.getValue()));
            }
        }
        return (int) (mayor + 1);
    }

    /** Añade la imagen (si no estaba ya) y devuelve su identificador de relación. */
    String anadirImagen(byte[] png) {
        for (Element rel : XmlDocx.hijos(relaciones.getDocumentElement())) {
            if (TIPO_IMAGEN.equals(rel.getAttribute("Type"))) {
                byte[] existente = partes.get("word/" + rel.getAttribute("Target"));
                if (existente != null && Arrays.equals(existente, png)) {
                    return rel.getAttribute("Id");
                }
            }
        }
        int n = 1;
        while (partes.containsKey("word/media/image" + n + ".png")) {
            n++;
        }
        String destino = "media/image" + n + ".png";
        partes.put("word/" + destino, png);
        List<String> usados = new ArrayList<>();
        for (Element rel : XmlDocx.hijos(relaciones.getDocumentElement())) {
            usados.add(rel.getAttribute("Id"));
        }
        int k = 1;
        while (usados.contains("rId" + k)) {
            k++;
        }
        Element rel = relaciones.createElementNS(NS_RELACIONES, "Relationship");
        rel.setAttribute("Id", "rId" + k);
        rel.setAttribute("Type", TIPO_IMAGEN);
        rel.setAttribute("Target", destino);
        relaciones.getDocumentElement().appendChild(rel);
        return "rId" + k;
    }

    /** El archivo .docx. */
    public byte[] guardar() {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(salida)) {
                Map<String, byte[]> todo = new LinkedHashMap<>(partes);
                todo.put(TIPOS, tiposConPng(todo.get(TIPOS)));
                todo.put(RELACIONES, escribirXml(relaciones));
                todo.put(DOCUMENTO, escribirXml(documento));
                List<String> orden = new ArrayList<>(todo.keySet());
                orden.remove(TIPOS);
                orden.add(0, TIPOS);
                for (String nombre : orden) {
                    zip.putNextEntry(new ZipEntry(nombre));
                    zip.write(todo.get(nombre));
                    zip.closeEntry();
                }
            }
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el documento", e);
        }
    }

    /** Se asegura de que el Word declare el tipo de las imágenes PNG. */
    private static byte[] tiposConPng(byte[] original) {
        String texto = new String(original, StandardCharsets.UTF_8);
        if (texto.contains("Extension=\"png\"")) {
            return original;
        }
        Matcher m = Pattern.compile("(<Types[^>]*>)").matcher(texto);
        if (!m.find()) {
            return original;
        }
        return (texto.substring(0, m.end()) + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                + texto.substring(m.end())).getBytes(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- XML

    static Document leerXml(byte[] datos) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            Document d = f.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(datos)));
            quitarBlancos(d.getDocumentElement());
            return d;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException("XML de la plantilla no válido", e);
        }
    }

    /** Quita los espacios en blanco entre elementos (el texto de un {@code w:t} no se toca). */
    private static void quitarBlancos(Element e) {
        boolean hayElementos = XmlDocx.primerHijoElemento(e) != null;
        Node h = e.getFirstChild();
        while (h != null) {
            Node siguiente = h.getNextSibling();
            if (h instanceof Element hijo) {
                quitarBlancos(hijo);
            } else if (hayElementos && h.getNodeType() == Node.TEXT_NODE && h.getNodeValue().isBlank()) {
                e.removeChild(h);
            }
            h = siguiente;
        }
    }

    static byte[] escribirXml(Document d) {
        try {
            d.setXmlStandalone(true);
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.STANDALONE, "yes");
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            t.transform(new DOMSource(d), new StreamResult(salida));
            return salida.toByteArray();
        } catch (TransformerException e) {
            throw new IllegalStateException("No se pudo escribir el XML", e);
        }
    }

    /** Para pruebas: atributos de un elemento como texto. */
    static String atributos(Element e) {
        NamedNodeMap m = e.getAttributes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < m.getLength(); i++) {
            sb.append(m.item(i).getNodeName()).append('=').append(m.item(i).getNodeValue()).append(' ');
        }
        return sb.toString();
    }
}
