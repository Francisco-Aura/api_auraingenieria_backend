package api.aura.service.modulo_registro_horario.documento;

import api.aura.service.modulo_registro_horario.documento.DatosRegistro.HorasDia;
import api.aura.service.modulo_registro_horario.documento.DatosRegistro.Tramo;
import api.aura.service.modulo_registro_horario.firma.FirmaService;
import api.aura.service.modulo_registro_horario.firma.Imagen;
import api.aura.service.modulo_registro_horario.firma.TrabajosPesados;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El Word que se rellena en Java es el mismo que rellena el motor del Registro Horario anterior (python-docx) con los
 * mismos datos inventados: mismo {@code word/document.xml} (elemento a elemento y atributo a atributo), mismas relaciones e
 * imágenes (estas con una tolerancia de 1 nivel de gris, por el orden de pasadas del redimensionado de Pillow).
 */
class MotorWordTest {

    private static final String RAIZ = "/registro-horario-oro/word/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final FirmaService firmas = new FirmaService(new TrabajosPesados());
    private final MotorWord motor = new MotorWord(firmas);

    // ---------------------------------------------------------------- datos de prueba

    static JsonNode casos() throws IOException {
        return MAPPER.readTree(FirmaParaDocumentoTest.recurso("casos.json"));
    }

    private static String hhmm(String t) {
        String[] p = t.split(":");
        return String.format("%02d:%s", Integer.parseInt(p[0]), p[1]);
    }

    private static Tramo tramo(JsonNode t) {
        return new Tramo(hhmm(t.get("ini").asText()), hhmm(t.get("fin").asText()));
    }

    /** Lo que hará el controlador de la Fase 5: la petición web + la empresa elegida de la lista. */
    static DatosRegistro datos(JsonNode peticion, JsonNode empresas) {
        JsonNode emp = empresas.get(peticion.get("empresa").asText());
        Map<String, String> estados = new LinkedHashMap<>();
        peticion.get("estado_dias").fields().forEachRemaining(e -> estados.put(e.getKey(), e.getValue().asText()));
        Map<String, HorasDia> horas = new LinkedHashMap<>();
        peticion.get("horas_dia").fields().forEachRemaining(e -> horas.put(e.getKey(), new HorasDia(
                e.getValue().has("manana") ? tramo(e.getValue().get("manana")) : null,
                e.getValue().has("tarde") ? tramo(e.getValue().get("tarde")) : null)));
        String localidad = emp.get("localidad").asText();
        return new DatosRegistro(peticion.get("trabajador").asText(), peticion.get("nif").asText(),
                peticion.get("num_afiliacion").asText(), emp.get("empresa").asText(), emp.get("cif").asText(),
                emp.get("ccc").asText(), emp.get("centro_trabajo").asText(), localidad.isEmpty() ? "LORCA" : localidad,
                tramo(peticion.get("horario_base").get("manana")), tramo(peticion.get("horario_base").get("tarde")),
                estados, horas, peticion.get("fecha_firma").asText());
    }

    private MotorWord.Resultado construir(JsonNode caso, JsonNode empresas) {
        JsonNode p = caso.get("peticion");
        return motor.construir(datos(p, empresas), p.get("anio").asInt(), p.get("mes").asInt(),
                Base64.getDecoder().decode(p.get("firma_png_base64").asText()));
    }

    // ---------------------------------------------------------------- comparación

    private static Map<String, byte[]> partes(byte[] docx) throws IOException {
        Map<String, byte[]> r = new TreeMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry e = z.getNextEntry(); e != null; e = z.getNextEntry()) {
                r.put(e.getName(), z.readAllBytes());
            }
        }
        return r;
    }

    /** Primera diferencia entre dos XML (elementos, atributos sin contar declaraciones de espacios de nombres, y texto), o {@code null}. */
    static String diferencia(Node a, Node b, String ruta) {
        if (a instanceof Document da && b instanceof Document db) {
            return diferencia(da.getDocumentElement(), db.getDocumentElement(), ruta);
        }
        if (a.getNodeType() != b.getNodeType()) {
            return ruta + ": distinto tipo de nodo";
        }
        if (a instanceof Element ea && b instanceof Element eb) {
            if (!java.util.Objects.equals(ea.getNamespaceURI(), eb.getNamespaceURI()) || !ea.getLocalName().equals(eb.getLocalName())) {
                return ruta + ": <" + ea.getNodeName() + "> en vez de <" + eb.getNodeName() + ">";
            }
            String aa = atributos(ea), ab = atributos(eb);
            if (!aa.equals(ab)) {
                return ruta + "/" + ea.getNodeName() + ": atributos [" + aa + "] en vez de [" + ab + "]";
            }
            List<Node> ha = hijos(a), hb = hijos(b);
            String donde = ruta + "/" + ea.getNodeName() + "[" + indice(ea) + "]";
            for (int i = 0; i < Math.min(ha.size(), hb.size()); i++) {
                String d = diferencia(ha.get(i), hb.get(i), donde);
                if (d != null) {
                    return d;
                }
            }
            if (ha.size() != hb.size()) {
                return donde + ": " + ha.size() + " hijos en vez de " + hb.size();
            }
            return null;
        }
        return a.getNodeValue().equals(b.getNodeValue()) ? null : ruta + ": texto «" + a.getNodeValue() + "» en vez de «" + b.getNodeValue() + "»";
    }

    private static int indice(Element e) {
        int n = 0;
        for (Node h = e.getPreviousSibling(); h != null; h = h.getPreviousSibling()) {
            n++;
        }
        return n;
    }

    private static String atributos(Element e) {
        NamedNodeMap m = e.getAttributes();
        TreeMap<String, String> r = new TreeMap<>();
        for (int i = 0; i < m.getLength(); i++) {
            Attr at = (Attr) m.item(i);
            String ns = at.getNamespaceURI();
            if ("http://www.w3.org/2000/xmlns/".equals(ns) || at.getName().startsWith("xmlns")) {
                continue;
            }
            r.put("{" + ns + "}" + at.getLocalName(), at.getValue());
        }
        return r.toString();
    }

    /** Hijos con los textos contiguos unidos (un texto vacío no cuenta). */
    private static List<Node> hijos(Node n) {
        List<Node> r = new ArrayList<>();
        for (Node h = n.getFirstChild(); h != null; h = h.getNextSibling()) {
            if (h.getNodeType() == Node.COMMENT_NODE) {
                continue;
            }
            r.add(h);
        }
        return r;
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    void cadaWordDeLaWebEsElDelOriginal() throws IOException {
        JsonNode todo = casos();
        JsonNode empresas = todo.get("empresas");
        int comparados = 0;
        for (JsonNode caso : todo.get("casos")) {
            String nombre = caso.get("nombre").asText();
            if (caso.has("error")) {
                continue;
            }
            MotorWord.Resultado r = construir(caso, empresas);
            assertEquals(caso.get("dias").asInt(), r.diasTrabajados(), nombre + ": días trabajados");
            assertEquals(caso.get("min_manana").asInt(), r.minutosManana(), nombre + ": minutos de mañana");
            assertEquals(caso.get("min_tarde").asInt(), r.minutosTarde(), nombre + ": minutos de tarde");

            Map<String, byte[]> esperado = partes(FirmaParaDocumentoTest.recurso(nombre + ".docx"));
            Map<String, byte[]> obtenido = partes(r.documento().guardar());
            Document dEsperado = DocumentoWord.leerXml(esperado.get("word/document.xml"));
            Document dObtenido = DocumentoWord.leerXml(obtenido.get("word/document.xml"));
            assertEquals(null, diferencia(dObtenido, dEsperado, ""), nombre + ": word/document.xml (obtenido frente a esperado)");

            assertEquals(null, diferencia(DocumentoWord.leerXml(obtenido.get("word/_rels/document.xml.rels")),
                    DocumentoWord.leerXml(esperado.get("word/_rels/document.xml.rels")), ""), nombre + ": relaciones");
            assertEquals(esperado.keySet(), obtenido.keySet(), nombre + ": partes del archivo");
            for (String parte : esperado.keySet()) {
                if (parte.startsWith("word/media/image") && !parte.equals("word/media/image1.png")) {
                    igualesConTolerancia(nombre + " " + parte, esperado.get(parte), obtenido.get(parte));
                } else if (!parte.equals("word/document.xml") && !parte.equals("word/_rels/document.xml.rels")
                        && !parte.equals("[Content_Types].xml") && !parte.equals("docProps/core.xml")
                        && !parte.equals("word/settings.xml") && !parte.equals("word/styles.xml") && !parte.equals("_rels/.rels")) {
                    assertTrue(java.util.Arrays.equals(esperado.get(parte), obtenido.get(parte)), nombre + ": " + parte);
                }
            }
            try (org.apache.poi.xwpf.usermodel.XWPFDocument poi = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                    new ByteArrayInputStream(r.documento().guardar()))) { // un lector de Word independiente lo abre
                assertEquals(esperadoTablas(esperado), poi.getTables().size(), nombre + ": tablas");
            }
            comparados++;
        }
        assertEquals(9, comparados);
    }

    private static int esperadoTablas(Map<String, byte[]> esperado) {
        return DocumentoWord.leerXml(esperado.get("word/document.xml")).getElementsByTagNameNS(XmlDocx.W, "tbl").getLength() > 0
                ? XmlDocx.hijosW(XmlDocx.hijoW(DocumentoWord.leerXml(esperado.get("word/document.xml")).getDocumentElement(), "body"), "tbl").size() : 0;
    }

    private void igualesConTolerancia(String que, byte[] esperado, byte[] obtenido) {
        Imagen a = firmas.decodificar(esperado), b = firmas.decodificar(obtenido);
        assertEquals(a.ancho, b.ancho, que + ": ancho");
        assertEquals(a.alto, b.alto, que + ": alto");
        int maxDif = 0;
        for (int i = 0; i < a.argb.length; i++) {
            if ((a.argb[i] >>> 24) == 0 && (b.argb[i] >>> 24) == 0) {
                continue;
            }
            for (int s = 0; s <= 24; s += 8) {
                maxDif = Math.max(maxDif, Math.abs(((a.argb[i] >>> s) & 0xFF) - ((b.argb[i] >>> s) & 0xFF)));
            }
        }
        assertTrue(maxDif <= 1, que + ": diferencia máxima " + maxDif);
    }

    @Test
    void losErroresDeDatosTienenElMismoMensajeQueElOriginal() throws IOException {
        JsonNode todo = casos();
        int comprobados = 0;
        for (JsonNode caso : todo.get("casos")) {
            if (!caso.has("error")) {
                continue;
            }
            String nombre = caso.get("nombre").asText();
            DatosNoValidosException e;
            if (caso.get("peticion").isNull()) {
                JsonNode baseTmp = null;
                for (JsonNode c : todo.get("casos")) {
                    if (c.get("nombre").asText().equals("completo")) {
                        baseTmp = c.get("peticion");
                    }
                }
                final JsonNode base = baseTmp;
                DatosRegistro d = datos(base, todo.get("empresas"));
                DatosRegistro sinCif = new DatosRegistro(d.trabajador(), d.nif(), d.numAfiliacion(), d.empresa(), "", " ",
                        d.centroTrabajo(), d.localidad(), d.horarioManana(), d.horarioTarde(), d.estadoDias(), d.horasDia(),
                        d.fechaFirma());
                e = assertThrows(DatosNoValidosException.class, () -> motor.construir(sinCif, 2026, 10,
                        Base64.getDecoder().decode(base.get("firma_png_base64").asText())), nombre);
            } else {
                e = assertThrows(DatosNoValidosException.class, () -> construir(caso, todo.get("empresas")), nombre);
            }
            assertEquals(caso.get("error").asText(), e.getMessage(), nombre);
            comprobados++;
        }
        assertEquals(4, comprobados);
    }

    @Test
    void elCaracterEspecialSeEscapaEnElXml() throws IOException {
        JsonNode todo = casos();
        for (JsonNode caso : todo.get("casos")) {
            if (caso.get("nombre").asText().equals("caracteres_especiales")) {
                Map<String, byte[]> p = partes(construir(caso, todo.get("empresas")).documento().guardar());
                String xml = new String(p.get("word/document.xml"), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(xml.contains("A&amp;B &lt;Ñandú&gt; \"S.L.\""), "empresa escapada");
                assertTrue(YearMonth.of(2026, 10).lengthOfMonth() == 31);
            }
        }
    }
}
