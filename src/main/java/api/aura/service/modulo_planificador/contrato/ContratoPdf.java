package api.aura.service.modulo_planificador.contrato;

import com.lowagie.text.*;
import com.lowagie.text.List;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Dibuja un Word (.docx) sencillo como PDF, sin necesitar Word ni LibreOffice (equivale a docx_pdf.py).
 *
 * <p>No es un motor de maquetación completo: dibuja párrafos, tablas simples, numeración y el logotipo de la cabecera tal y como los define
 * el documento (estilos, negrita, tamaños, alineación, sangrías). Está pensado para el contrato que genera la aplicación a partir de su
 * modelo. Con el cuerpo del contrato (todo lo que va tras la portada) se normaliza el espaciado: los párrafos vacíos del modelo se
 * sustituyen por un espaciado fijo según el tipo de párrafo. Los títulos no se quedan solos al final de una página y el bloque de firmas
 * va siempre entero en una misma página.
 */
@Component
public class ContratoPdf {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final double TAMANO_POR_DEFECTO = 11.0;
    private static final String NBSP = " ";
    /** Espaciado del cuerpo del contrato (puntos): {antes, después}. */
    private static final Map<String, double[]> ESPACIADO = Map.of(
            "title", new double[]{14, 8}, "doc-title", new double[]{0, 14}, "h1", new double[]{16, 6},
            "h2", new double[]{10, 4}, "item", new double[]{0, 3}, "body", new double[]{0, 7});
    /** Altura reservada para la firma de cada parte. */
    private static final double ESPACIO_FIRMA = 80.0;
    private static final Pattern ESPACIOS_SEGUIDOS = Pattern.compile(" {2,}");

    /** Convierte el Word en PDF. */
    public byte[] convertir(byte[] docx) {
        try {
            return new Dibujante(docx).dibujar();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF del contrato: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------------ XML

    private static boolean es(Node n, String nombre) {
        return n instanceof Element e && W.equals(e.getNamespaceURI()) && nombre.equals(e.getLocalName());
    }

    private static java.util.List<Element> hijos(Element padre, String nombre) {
        java.util.List<Element> salida = new ArrayList<>();
        if (padre == null) return salida;
        NodeList lista = padre.getChildNodes();
        for (int i = 0; i < lista.getLength(); i++) {
            if (es(lista.item(i), nombre)) salida.add((Element) lista.item(i));
        }
        return salida;
    }

    private static Element hijo(Element padre, String... ruta) {
        Element actual = padre;
        for (String nombre : ruta) {
            if (actual == null) return null;
            java.util.List<Element> h = hijos(actual, nombre);
            actual = h.isEmpty() ? null : h.get(0);
        }
        return actual;
    }

    private static String attr(Element e, String nombre) {
        return e != null && e.hasAttributeNS(W, nombre) ? e.getAttributeNS(W, nombre) : null;
    }

    private static Double twips(String valor) {
        try {
            return valor == null ? null : Integer.parseInt(valor.strip()) / 20.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static java.util.List<Element> descendientes(Element raiz, String nombre) {
        java.util.List<Element> salida = new ArrayList<>();
        NodeList lista = raiz.getElementsByTagNameNS(W, nombre);
        for (int i = 0; i < lista.getLength(); i++) salida.add((Element) lista.item(i));
        return salida;
    }

    private static Boolean bandera(Element e) {
        if (e == null) return null;
        String v = attr(e, "val");
        return v == null || !Set.of("0", "false", "off", "none").contains(v);
    }

    /** Propiedades de texto presentes en un {@code <w:rPr>} (solo las que aparecen). */
    private static Map<String, Object> rpr(Element el) {
        Map<String, Object> salida = new HashMap<>();
        if (el == null) return salida;
        Element fuentes = hijo(el, "rFonts");
        if (fuentes != null) {
            String nombre = attr(fuentes, "ascii") != null && !attr(fuentes, "ascii").isEmpty() ? attr(fuentes, "ascii") : attr(fuentes, "hAnsi");
            if (nombre != null && !nombre.isEmpty()) salida.put("font", nombre);
        }
        for (String[] par : new String[][]{{"b", "bold"}, {"i", "italic"}, {"caps", "caps"}}) {
            Boolean f = bandera(hijo(el, par[0]));
            if (f != null) salida.put(par[1], f);
        }
        Element subrayado = hijo(el, "u");
        if (subrayado != null) {
            String v = attr(subrayado, "val");
            salida.put("underline", !"none".equals(v == null ? "single" : v));
        }
        Element tamano = hijo(el, "sz");
        if (tamano != null && attr(tamano, "val") != null && !attr(tamano, "val").isEmpty()) {
            salida.put("size", Integer.parseInt(attr(tamano, "val")) / 2.0);
        }
        Element color = hijo(el, "color");
        if (color != null && attr(color, "val") != null && attr(color, "val").matches("[0-9A-Fa-f]{6}")) {
            salida.put("color", attr(color, "val"));
        }
        Element vertical = hijo(el, "vertAlign");
        if (vertical != null) salida.put("vert", attr(vertical, "val"));
        return salida;
    }

    private static Map<String, Object> ppr(Element el) {
        Map<String, Object> salida = new HashMap<>();
        if (el == null) return salida;
        Element jc = hijo(el, "jc");
        if (jc != null) salida.put("jc", attr(jc, "val"));
        Element ind = hijo(el, "ind");
        if (ind != null) {
            for (String[] par : new String[][]{{"left", "left"}, {"start", "left"}, {"right", "right"}, {"end", "right"}, {"firstLine", "first"}, {"hanging", "hanging"}}) {
                Double v = twips(attr(ind, par[0]));
                if (v != null) salida.put(par[1], v);
            }
        }
        Element espaciado = hijo(el, "spacing");
        if (espaciado != null) {
            for (String[] par : new String[][]{{"before", "before"}, {"after", "after"}}) {
                Double v = twips(attr(espaciado, par[0]));
                if (v != null) salida.put(par[1], v);
            }
            String linea = attr(espaciado, "line");
            String regla = attr(espaciado, "lineRule");
            if (linea != null && !linea.isEmpty() && (regla == null || regla.isEmpty() || regla.equals("auto"))) {
                salida.put("line", Integer.parseInt(linea) / 240.0);
            }
        }
        Element num = hijo(el, "numPr");
        if (num != null) {
            Element id = hijo(num, "numId");
            Element nivel = hijo(num, "ilvl");
            if (id != null) salida.put("numId", attr(id, "val"));
            if (nivel != null) salida.put("ilvl", Integer.parseInt(attr(nivel, "val") == null ? "0" : attr(nivel, "val")));
        }
        return salida;
    }

    private static double num(Map<String, Object> m, String clave, double defecto) {
        Object v = m.get(clave);
        return v instanceof Number n ? n.doubleValue() : defecto;
    }

    private static boolean tiene(Map<String, Object> m, String clave) {
        return m.containsKey(clave);
    }

    // ---------------------------------------------------------------------------------- estilos

    private static class Estilos {
        record Estilo(String based, Map<String, Object> ppr, Map<String, Object> rpr) {
        }

        final Map<String, Estilo> estilos = new HashMap<>();
        String parrafoPorDefecto;
        final Map<String, Object> rprBase = new HashMap<>(Map.of("size", TAMANO_POR_DEFECTO));

        Estilos(Element raiz) {
            if (raiz == null) return;
            rprBase.putAll(rpr(hijo(raiz, "docDefaults", "rPrDefault", "rPr")));
            for (Element st : hijos(raiz, "style")) {
                String id = attr(st, "styleId");
                Element basado = hijo(st, "basedOn");
                estilos.put(id, new Estilo(basado == null ? null : attr(basado, "val"), ppr(hijo(st, "pPr")), rpr(hijo(st, "rPr"))));
                if ("paragraph".equals(attr(st, "type")) && "1".equals(attr(st, "default"))) parrafoPorDefecto = id;
            }
        }

        /** (propiedades de párrafo, de texto) de un estilo, siguiendo la cadena «basado en». */
        java.util.List<Map<String, Object>> resolver(String estiloId) {
            java.util.List<Estilo> cadena = new ArrayList<>();
            Set<String> vistos = new HashSet<>();
            String id = estiloId != null ? estiloId : parrafoPorDefecto;
            while (id != null && estilos.containsKey(id) && !vistos.contains(id)) {
                vistos.add(id);
                cadena.add(estilos.get(id));
                id = estilos.get(id).based();
            }
            Map<String, Object> p = new HashMap<>();
            Map<String, Object> r = new HashMap<>(rprBase);
            if (estiloId != null && parrafoPorDefecto != null && !vistos.contains(parrafoPorDefecto)) {
                Estilo defecto = estilos.get(parrafoPorDefecto);
                if (defecto != null) cadena.add(defecto);
            }
            Collections.reverse(cadena);
            for (Estilo e : cadena) {
                p.putAll(e.ppr());
                r.putAll(e.rpr());
            }
            return java.util.List.of(p, r);
        }
    }

    // ------------------------------------------------------------------------------- numeración

    private static class Numeracion {
        record Nivel(String formato, String texto, int inicio, double izquierda, double colgante) {
        }

        final Map<String, Map<Integer, Nivel>> niveles = new HashMap<>();
        final Map<String, String> numAAbstracto = new HashMap<>();
        final Map<String, int[]> contadores = new HashMap<>();

        Numeracion(Element raiz) {
            if (raiz == null) return;
            for (Element ab : hijos(raiz, "abstractNum")) {
                Map<Integer, Nivel> mapa = new HashMap<>();
                for (Element lvl : hijos(ab, "lvl")) {
                    Element fmt = hijo(lvl, "numFmt");
                    Element texto = hijo(lvl, "lvlText");
                    Element inicio = hijo(lvl, "start");
                    Map<String, Object> sangria = ppr(hijo(lvl, "pPr"));
                    mapa.put(Integer.parseInt(attr(lvl, "ilvl") == null ? "0" : attr(lvl, "ilvl")),
                            new Nivel(fmt != null ? attr(fmt, "val") : "decimal", texto != null && attr(texto, "val") != null ? attr(texto, "val") : "",
                                    inicio != null ? Integer.parseInt(attr(inicio, "val") == null ? "1" : attr(inicio, "val")) : 1,
                                    num(sangria, "left", 0), num(sangria, "hanging", 0)));
                }
                niveles.put(attr(ab, "abstractNumId"), mapa);
            }
            for (Element n : hijos(raiz, "num")) {
                Element ab = hijo(n, "abstractNumId");
                if (ab != null) numAAbstracto.put(attr(n, "numId"), attr(ab, "val"));
            }
        }

        static String formato(String fmt, int n) {
            switch (fmt) {
                case "lowerLetter":
                    return String.valueOf((char) ('a' + (n - 1) % 26));
                case "upperLetter":
                    return String.valueOf((char) ('A' + (n - 1) % 26));
                case "lowerRoman":
                case "upperRoman":
                    int[] valores = {10, 9, 5, 4, 1};
                    String[] simbolos = {"x", "ix", "v", "iv", "i"};
                    StringBuilder sb = new StringBuilder();
                    int resto = n;
                    for (int i = 0; i < valores.length; i++) {
                        while (resto >= valores[i]) {
                            sb.append(simbolos[i]);
                            resto -= valores[i];
                        }
                    }
                    return fmt.equals("upperRoman") ? sb.toString().toUpperCase() : sb.toString();
                default:
                    return String.valueOf(n);
            }
        }

        record Etiqueta(String texto, double izquierda, double colgante) {
        }

        /** Texto de la viñeta o número, sangría izquierda y colgante; avanza el contador. */
        Etiqueta etiqueta(String numId, int nivel) {
            String abstracto = numAAbstracto.get(numId);
            Map<Integer, Nivel> mapa = niveles.get(abstracto == null ? "" : abstracto);
            if (mapa == null || mapa.isEmpty() || !mapa.containsKey(nivel)) return null;
            int[] c = contadores.computeIfAbsent(abstracto, k -> new int[9]);
            c[nivel] = c[nivel] != 0 ? c[nivel] + 1 : mapa.get(nivel).inicio();
            for (int d = nivel + 1; d < 9; d++) c[d] = 0;
            Nivel info = mapa.get(nivel);
            String texto;
            if (info.formato().equals("bullet")) {
                texto = "•";
            } else {
                Matcher m = Pattern.compile("%(\\d)").matcher(info.texto());
                StringBuilder sb = new StringBuilder();
                int ultimo = 0;
                while (m.find()) {
                    int lvl = Integer.parseInt(m.group(1)) - 1;
                    Nivel n = mapa.getOrDefault(lvl, info);
                    sb.append(info.texto(), ultimo, m.start()).append(formato(n.formato(), c[lvl] != 0 ? c[lvl] : n.inicio()));
                    ultimo = m.end();
                }
                texto = sb.append(info.texto().substring(ultimo)).toString();
            }
            return new Etiqueta(texto, info.izquierda(), info.colgante());
        }
    }

    // ---------------------------------------------------------------------------------- fuentes

    private static final Map<String, BaseFont> FUENTES = new HashMap<>();

    private static BaseFont fuente(String nombre, boolean negrita, boolean cursiva) {
        String n = nombre == null ? "" : nombre.toLowerCase();
        String familia = n.contains("courier") ? "Courier" : n.contains("times") ? "Times" : "Helvetica";
        String base;
        switch (familia) {
            case "Courier":
                base = negrita && cursiva ? "Courier-BoldOblique" : negrita ? "Courier-Bold" : cursiva ? "Courier-Oblique" : "Courier";
                break;
            case "Times":
                base = negrita && cursiva ? "Times-BoldItalic" : negrita ? "Times-Bold" : cursiva ? "Times-Italic" : "Times-Roman";
                break;
            default:
                base = negrita && cursiva ? "Helvetica-BoldOblique" : negrita ? "Helvetica-Bold" : cursiva ? "Helvetica-Oblique" : "Helvetica";
        }
        return FUENTES.computeIfAbsent(base, b -> {
            try {
                return BaseFont.createFont(b, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // ------------------------------------------------------------------------------------ dibujo

    /** Un trozo de la historia del documento: un elemento, un salto de página, y si debe ir junto con el siguiente. */
    private record Bloque(com.lowagie.text.Element elemento, boolean salto, boolean juntoConSiguiente) {
        static Bloque de(com.lowagie.text.Element e, boolean junto) {
            return new Bloque(e, false, junto);
        }

        static Bloque deSalto() {
            return new Bloque(null, true, false);
        }
    }

    private record Marcado(java.util.List<Chunk> trozos, double mayor, String plano, boolean todoNegrita) {
    }

    private static class Dibujante {
        private final byte[] docx;
        private final Estilos estilos;
        private final Numeracion numeracion;
        private final Element raiz;
        private byte[] logo;

        Dibujante(byte[] docx) throws IOException {
            this.docx = docx;
            Map<String, byte[]> partes = new HashMap<>();
            try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(docx))) {
                ZipEntry e;
                while ((e = z.getNextEntry()) != null) partes.put(e.getName(), z.readAllBytes());
            }
            this.estilos = new Estilos(partes.containsKey("word/styles.xml") ? analizar(partes.get("word/styles.xml")) : null);
            this.numeracion = new Numeracion(partes.containsKey("word/numbering.xml") ? analizar(partes.get("word/numbering.xml")) : null);
            this.raiz = analizar(partes.get("word/document.xml"));
            if (partes.containsKey("word/header1.xml") && partes.containsKey("word/_rels/header1.xml.rels")) {
                String rels = new String(partes.get("word/_rels/header1.xml.rels"), java.nio.charset.StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("Target=\"(media/[^\"]+)\"").matcher(rels);
                if (m.find() && partes.containsKey("word/" + m.group(1))) this.logo = partes.get("word/" + m.group(1));
            }
        }

        private static Element analizar(byte[] xml) throws IOException {
            try {
                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                f.setNamespaceAware(true);
                f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
            } catch (Exception e) {
                throw new IOException("El Word del contrato no se puede leer", e);
            }
        }

        // -- texto ------------------------------------------------------------------------------

        private static java.util.List<Element> tramos(Element padre) {
            java.util.List<Element> salida = new ArrayList<>();
            NodeList hijos = padre.getChildNodes();
            for (int i = 0; i < hijos.getLength(); i++) {
                Node n = hijos.item(i);
                if (es(n, "r")) {
                    salida.add((Element) n);
                } else if (es(n, "hyperlink") || es(n, "smartTag") || es(n, "sdt") || es(n, "sdtContent") || es(n, "ins")) {
                    salida.addAll(tramos((Element) n));
                }
            }
            return salida;
        }

        private static boolean saltoDePagina(Element p) {
            for (Element br : descendientes(p, "br")) {
                if ("page".equals(attr(br, "type"))) return true;
            }
            return false;
        }

        private Marcado marcar(Element p, Map<String, Object> rprParrafo) {
            java.util.List<Chunk> trozos = new ArrayList<>();
            StringBuilder plano = new StringBuilder();
            java.util.List<Boolean> negritas = new ArrayList<>();
            double mayor = 0;
            for (Element tramo : tramos(p)) {
                Map<String, Object> r = new HashMap<>(rprParrafo);
                Element estilo = hijo(tramo, "rPr", "rStyle");
                if (estilo != null) r.putAll(estilos.resolver(attr(estilo, "val")).get(1));
                r.putAll(rpr(hijo(tramo, "rPr")));
                StringBuilder sb = new StringBuilder();
                NodeList nodos = tramo.getChildNodes();
                for (int i = 0; i < nodos.getLength(); i++) {
                    Node nodo = nodos.item(i);
                    if (es(nodo, "t")) {
                        sb.append(nodo.getTextContent());
                    } else if (es(nodo, "tab")) {
                        sb.append('\t');
                    } else if (es(nodo, "br") || es(nodo, "cr")) {
                        if (!"page".equals(attr((Element) nodo, "type"))) sb.append('\n');
                    }
                }
                String texto = sb.toString();
                if (texto.isEmpty()) continue;
                if (Boolean.TRUE.equals(r.get("caps"))) texto = texto.toUpperCase();
                plano.append(texto);
                boolean negrita = Boolean.TRUE.equals(r.get("bold"));
                if (!texto.isBlank()) negritas.add(negrita);
                String cuerpo = texto.replace("\t", NBSP.repeat(6));
                Matcher m = ESPACIOS_SEGUIDOS.matcher(cuerpo);
                StringBuilder colapsado = new StringBuilder();
                int ultimo = 0;
                while (m.find()) {
                    colapsado.append(cuerpo, ultimo, m.start()).append(NBSP.repeat(m.group().length()));
                    ultimo = m.end();
                }
                cuerpo = colapsado.append(cuerpo.substring(ultimo)).toString();
                if (cuerpo.startsWith(" ")) cuerpo = NBSP + cuerpo.substring(1);
                double tamano = num(r, "size", TAMANO_POR_DEFECTO);
                mayor = Math.max(mayor, tamano);
                boolean superindice = "superscript".equals(r.get("vert"));
                Color color = r.get("color") instanceof String c ? Color.decode("#" + c) : Color.BLACK;
                Font fuenteTexto = new Font(fuente((String) r.get("font"), negrita, Boolean.TRUE.equals(r.get("italic"))), (float) (superindice ? tamano * 0.7 : tamano), Font.NORMAL, color);
                String[] lineas = cuerpo.split("\n", -1);
                for (int i = 0; i < lineas.length; i++) {
                    if (i > 0) trozos.add(Chunk.NEWLINE);
                    if (lineas[i].isEmpty()) continue;
                    Chunk c = new Chunk(lineas[i], fuenteTexto);
                    if (Boolean.TRUE.equals(r.get("underline"))) c.setUnderline(0.5f, -2f);
                    if (superindice) c.setTextRise((float) (tamano * 0.4));
                    trozos.add(c);
                }
            }
            return new Marcado(trozos, mayor, plano.toString(), !negritas.isEmpty() && negritas.stream().allMatch(b -> b));
        }

        private String plano(Element p) {
            return marcar(p, new HashMap<>()).plano();
        }

        private java.util.List<Map<String, Object>> propiedadesDe(Element p) {
            Element ppr = hijo(p, "pPr");
            Element estilo = hijo(ppr, "pStyle");
            java.util.List<Map<String, Object>> resuelto = estilos.resolver(estilo == null ? null : attr(estilo, "val"));
            Map<String, Object> pp = new HashMap<>(resuelto.get(0));
            pp.putAll(ppr(ppr));
            return java.util.List.of(pp, resuelto.get(1));
        }

        private boolean esTituloDeClausula(Element p) {
            java.util.List<Map<String, Object>> props = propiedadesDe(p);
            Marcado m = marcar(p, props.get(1));
            Object numId = props.get(0).get("numId");
            return m.todoNegrita() && numId != null && !"0".equals(numId) && m.plano().strip().length() < 90;
        }

        // -- párrafos -----------------------------------------------------------------------------

        private static Paragraph espacio(double alto) {
            Paragraph p = new Paragraph(" ", new Font(Font.HELVETICA, 1));
            p.setLeading((float) Math.max(alto, 1));
            return p;
        }

        private java.util.List<Bloque> parrafo(Element p, boolean cuerpo) {
            java.util.List<Map<String, Object>> props = propiedadesDe(p);
            Map<String, Object> pp = props.get(0);
            Map<String, Object> estiloRpr = props.get(1);
            Map<String, Object> marcaRpr = new HashMap<>(estiloRpr);
            Element ppr = hijo(p, "pPr");
            if (ppr != null) marcaRpr.putAll(rpr(hijo(ppr, "rPr")));

            Marcado m = marcar(p, estiloRpr);
            double tamano = m.mayor() != 0 ? m.mayor() : num(marcaRpr, "size", TAMANO_POR_DEFECTO);
            double interlineado = tamano * 1.2 * num(pp, "line", 1.0);
            boolean vacio = m.plano().replace(NBSP, " ").isBlank();
            if (saltoDePagina(p) && vacio) return java.util.List.of(Bloque.deSalto());
            if (vacio) {
                // Los párrafos vacíos del modelo separan de forma desigual: en el cuerpo se descartan.
                return cuerpo ? java.util.List.of() : java.util.List.of(Bloque.de(espacio(interlineado + num(pp, "before", 0) + num(pp, "after", 0)), false));
            }

            Object numId = pp.get("numId");
            boolean numerado = numId != null && !"0".equals(numId);
            boolean centrado = "center".equals(pp.get("jc"));
            String texto = m.plano().strip();

            String tipo = "body";
            double antes;
            double despues;
            if (cuerpo) {
                if (m.todoNegrita() && numerado && texto.length() < 90) tipo = "h1";
                else if (m.todoNegrita() && texto.matches("\\S+ Pago\\.?")) tipo = "h2";
                else if (m.todoNegrita() && centrado) tipo = texto.length() < 40 ? "title" : "doc-title";
                else if (numerado) tipo = "item";
                antes = ESPACIADO.get(tipo)[0];
                despues = ESPACIADO.get(tipo)[1];
            } else {
                antes = num(pp, "before", 0);
                despues = num(pp, "after", 0);
            }

            double izquierda = num(pp, "left", 0);
            double primera = num(pp, "first", 0) - num(pp, "hanging", 0);
            String viñeta = null;
            double sangriaViñeta = 0;
            if (numerado) {
                Numeracion.Etiqueta etiqueta = numeracion.etiqueta((String) numId, (int) num(pp, "ilvl", 0));
                if (etiqueta != null) {
                    if (!tiene(pp, "left")) izquierda = etiqueta.izquierda();
                    sangriaViñeta = Math.max(izquierda - (etiqueta.colgante() != 0 ? etiqueta.colgante() : 18.0), 0);
                    viñeta = etiqueta.texto();
                    primera = 0;
                }
            }
            int alineacion = switch (String.valueOf(pp.getOrDefault("jc", "left"))) {
                case "center" -> com.lowagie.text.Element.ALIGN_CENTER;
                case "right" -> com.lowagie.text.Element.ALIGN_RIGHT;
                case "both" -> com.lowagie.text.Element.ALIGN_JUSTIFIED;
                default -> com.lowagie.text.Element.ALIGN_LEFT;
            };
            boolean junto = tipo.equals("h1") || tipo.equals("h2") || tipo.equals("title");
            com.lowagie.text.Element resultado;
            if (viñeta != null) {
                List lista = new List(false, (float) (izquierda - sangriaViñeta));
                lista.setIndentationLeft((float) sangriaViñeta);
                lista.setIndentationRight((float) num(pp, "right", 0));
                ListItem item = new ListItem((float) interlineado);
                m.trozos().forEach(item::add);
                item.setAlignment(alineacion);
                item.setSpacingBefore((float) antes);
                item.setSpacingAfter((float) despues);
                item.setListSymbol(new Chunk(viñeta, new Font(fuente((String) marcaRpr.get("font"), Boolean.TRUE.equals(marcaRpr.get("bold")), false), (float) tamano)));
                lista.add(item);
                resultado = lista;
            } else {
                Paragraph par = new Paragraph((float) interlineado);
                m.trozos().forEach(par::add);
                par.setAlignment(alineacion);
                par.setIndentationLeft((float) izquierda);
                par.setIndentationRight((float) num(pp, "right", 0));
                par.setFirstLineIndent((float) primera);
                par.setSpacingBefore((float) antes);
                par.setSpacingAfter((float) despues);
                resultado = par;
            }
            return java.util.List.of(Bloque.de(resultado, junto));
        }

        // -- firmas -------------------------------------------------------------------------------

        private record Firmas(int indiceFecha, java.util.List<String[]> filas) {
        }

        /** (posición de la línea de fecha, filas «parte 1 / parte 2»), o null si no tiene la forma esperada. */
        private Firmas analizarFirmas(java.util.List<Element> lineas) {
            java.util.List<String> textos = new ArrayList<>();
            for (Element p : lineas) textos.add(plano(p).replace(NBSP, " "));
            int indiceFecha = -1;
            for (int i = 0; i < textos.size(); i++) {
                if (Pattern.compile("^\\s*En\\b").matcher(textos.get(i)).find()) {
                    indiceFecha = i;
                    break;
                }
            }
            if (indiceFecha < 0) return null;
            java.util.List<String[]> filas = new ArrayList<>();
            for (String t : textos.subList(indiceFecha + 1, textos.size())) {
                if (t.isBlank()) continue;
                java.util.List<String> partes = new ArrayList<>();
                for (String x : t.strip().split("\\s{3,}")) if (!x.strip().isEmpty()) partes.add(x.strip());
                if (partes.size() != 2) return null; // no es la forma esperada: se deja como estaba
                filas.add(new String[]{partes.get(0), partes.get(1)});
            }
            return filas.size() >= 2 ? new Firmas(indiceFecha, filas) : null;
        }

        private static Paragraph textoFirma(String t, double despues) {
            Paragraph p = new Paragraph(11.5f, t, new Font(fuente("Helvetica", false, false), 9));
            p.setSpacingAfter((float) despues);
            return p;
        }

        private java.util.List<Bloque> bloqueDeFirmas(java.util.List<Element> antes, Element cierre, java.util.List<Element> lineas, double ancho) {
            Firmas f = analizarFirmas(lineas);
            if (f == null) return null;
            String[] cabecera = f.filas().get(0);
            java.util.List<String[]> resto = f.filas().subList(1, f.filas().size());
            // Columnas: sangría, firma del contratista, hueco, firma del promotor. Las líneas de firma van solo bajo las columnas de las partes.
            double sangria = 18.0;
            double hueco = 36.0;
            double columna = (ancho - sangria - hueco - 10.0) / 2;
            PdfPTable firmas = new PdfPTable(new float[]{(float) sangria, (float) columna, (float) hueco, (float) columna});
            firmas.setTotalWidth((float) (sangria + hueco + 2 * columna));
            firmas.setLockedWidth(true);
            firmas.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_LEFT);
            Color gris = Color.decode("#444444");
            firmas.addCell(celda(null, 0, 0));
            firmas.addCell(celda(textoFirma(cabecera[0], 0), 0, 0));
            firmas.addCell(celda(null, 0, 0));
            firmas.addCell(celda(textoFirma(cabecera[1], 0), 0, 0));
            firmas.addCell(celda(null, 0, 0));
            PdfPCell l1 = celda(null, 0, 0);
            l1.setFixedHeight((float) ESPACIO_FIRMA);
            l1.setBorder(Rectangle.BOTTOM);
            l1.setBorderColorBottom(gris);
            l1.setBorderWidthBottom(0.6f);
            firmas.addCell(l1);
            firmas.addCell(celda(null, 0, 0));
            PdfPCell l2 = celda(null, 0, 0);
            l2.setFixedHeight((float) ESPACIO_FIRMA);
            l2.setBorder(Rectangle.BOTTOM);
            l2.setBorderColorBottom(gris);
            l2.setBorderWidthBottom(0.6f);
            firmas.addCell(l2);
            firmas.addCell(celda(null, 5, 0));
            PdfPCell a = celda(null, 5, 0);
            PdfPCell b = celda(null, 5, 0);
            for (String[] par : resto) {
                a.addElement(textoFirma(par[0], 2));
                b.addElement(textoFirma(par[1], 2));
            }
            firmas.addCell(a);
            firmas.addCell(celda(null, 5, 0));
            firmas.addCell(b);

            java.util.List<com.lowagie.text.Element> flujo = new ArrayList<>();
            for (Element anterior : antes) for (Bloque x : parrafo(anterior, true)) if (x.elemento() != null) flujo.add(x.elemento());
            if (cierre != null) for (Bloque x : parrafo(cierre, true)) if (x.elemento() != null) flujo.add(x.elemento());
            for (Bloque x : parrafo(lineas.get(f.indiceFecha()), true)) if (x.elemento() != null) flujo.add(x.elemento());
            flujo.add(espacio(22));
            flujo.add(firmas);
            // Todo junto: si no cabe en lo que queda de página, pasa entero a la siguiente.
            return java.util.List.of(Bloque.de(juntar(flujo), false));
        }

        private static PdfPCell celda(com.lowagie.text.Element contenido, double arriba, double abajo) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(0);
            c.setPaddingTop((float) arriba);
            c.setPaddingBottom((float) abajo);
            c.setUseAscender(true);
            if (contenido != null) c.addElement(contenido);
            return c;
        }

        /** Varios elementos en una tabla de una celda que no se parte entre páginas. */
        private static PdfPTable juntar(java.util.List<com.lowagie.text.Element> elementos) {
            PdfPTable t = new PdfPTable(1);
            t.setWidthPercentage(100);
            t.setKeepTogether(true);
            PdfPCell c = celda(null, 0, 0);
            // Dentro de una celda se pierde el espacio de encima del primer párrafo: pasa a ser el margen de la celda
            if (!elementos.isEmpty()) {
                com.lowagie.text.Element primero = elementos.get(0);
                if (primero instanceof Paragraph p) {
                    c.setPaddingTop(p.getSpacingBefore());
                    p.setSpacingBefore(0);
                } else if (primero instanceof List l && !l.getItems().isEmpty() && l.getItems().get(0) instanceof ListItem item) {
                    c.setPaddingTop(item.getSpacingBefore());
                    item.setSpacingBefore(0);
                }
            }
            for (com.lowagie.text.Element e : elementos) c.addElement(e);
            t.addCell(c);
            return t;
        }

        // -- tablas ---------------------------------------------------------------------------------

        private com.lowagie.text.Element tabla(Element tbl, double ancho) {
            java.util.List<Element> filas = hijos(tbl, "tr");
            java.util.List<Double> anchos = new ArrayList<>();
            java.util.List<java.util.List<Element>> contenido = new ArrayList<>();
            int columnas = 0;
            for (int r = 0; r < filas.size(); r++) {
                java.util.List<Element> celdas = hijos(filas.get(r), "tc");
                columnas = Math.max(columnas, celdas.size());
                for (Element tc : celdas) {
                    if (r == 0) {
                        Element w = hijo(tc, "tcPr", "tcW");
                        Double v = w == null ? null : twips(attr(w, "w"));
                        anchos.add(v == null ? 0.0 : v);
                    }
                }
            }
            if (filas.isEmpty() || columnas == 0) return null;
            double total = anchos.stream().mapToDouble(Double::doubleValue).sum();
            PdfPTable t = new PdfPTable(columnas);
            t.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_LEFT);
            float[] relativos = new float[columnas];
            for (int i = 0; i < columnas; i++) relativos[i] = total > 0 && i < anchos.size() ? (float) (double) anchos.get(i) : 1f;
            try {
                t.setWidths(relativos);
            } catch (DocumentException e) {
                throw new IllegalStateException(e);
            }
            t.setTotalWidth((float) (total > 0 ? Math.min(ancho, total) : ancho));
            t.setLockedWidth(true);
            t.setHeaderRows(1);
            for (Element tr : filas) {
                java.util.List<Element> celdas = hijos(tr, "tc");
                for (int i = 0; i < columnas; i++) {
                    PdfPCell c = new PdfPCell();
                    c.setBorderWidth(0.5f);
                    c.setBorderColor(Color.decode("#808080"));
                    c.setPaddingLeft(5);
                    c.setPaddingRight(5);
                    c.setPaddingTop(3);
                    c.setPaddingBottom(3);
                    if (i < celdas.size()) {
                        boolean alguno = false;
                        for (Element p : hijos(celdas.get(i), "p")) {
                            for (Bloque b : parrafo(p, false)) {
                                if (b.elemento() != null) {
                                    c.addElement(b.elemento());
                                    alguno = true;
                                }
                            }
                        }
                        if (!alguno) c.setPhrase(new Phrase(""));
                    } else {
                        c.setPhrase(new Phrase(""));
                    }
                    t.addCell(c);
                }
            }
            return t;
        }

        // -- documento --------------------------------------------------------------------------------

        /** Gestiona la cabecera (logotipo) y el pie de las páginas que no son la portada. */
        private static class Pagina extends PdfPageEventHelper {
            boolean principal;
            final byte[] logo;
            final double izquierda;
            final double derecha;
            final double alto;
            final double ancho;

            Pagina(byte[] logo, double izquierda, double derecha, double ancho, double alto) {
                this.logo = logo;
                this.izquierda = izquierda;
                this.derecha = derecha;
                this.ancho = ancho;
                this.alto = alto;
            }

            @Override
            public void onEndPage(PdfWriter writer, com.lowagie.text.Document documento) {
                if (!principal) return;
                PdfContentByte cb = writer.getDirectContent();
                try {
                    if (logo != null) {
                        Image imagen = Image.getInstance(logo);
                        imagen.scaleToFit(120, 62.5f);
                        imagen.setAbsolutePosition((float) (izquierda - 28 + (120 - imagen.getScaledWidth()) / 2), (float) (alto - 76 + (62.5 - imagen.getScaledHeight()) / 2));
                        cb.addImage(imagen);
                    }
                    cb.saveState();
                    cb.beginText();
                    cb.setFontAndSize(fuente("Helvetica", false, false), 8);
                    cb.setColorFill(Color.decode("#555555"));
                    cb.showTextAligned(com.lowagie.text.Element.ALIGN_LEFT, "Energía de futuro", (float) izquierda, 30, 0);
                    cb.showTextAligned(com.lowagie.text.Element.ALIGN_RIGHT, String.valueOf(documento.getPageNumber() - 1), (float) (ancho - derecha), 30, 0);
                    cb.endText();
                    cb.restoreState();
                } catch (DocumentException | IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        private static double margen(Element mar, String nombre, double defecto) {
            Double v = mar == null ? null : twips(attr(mar, nombre));
            return v == null || v == 0 ? defecto : v;
        }

        byte[] dibujar() throws IOException, DocumentException {
            Element cuerpo = hijo(raiz, "body");
            Element sect = hijo(cuerpo, "sectPr");
            Element tam = hijo(sect, "pgSz");
            Element mar = hijo(sect, "pgMar");
            Double anchoPagina = tam == null ? null : twips(attr(tam, "w"));
            Double altoPagina = tam == null ? null : twips(attr(tam, "h"));
            double pagAncho = anchoPagina == null || anchoPagina == 0 ? 595.0 : anchoPagina;
            double pagAlto = altoPagina == null || altoPagina == 0 ? 842.0 : altoPagina;
            double izquierda = margen(mar, "left", 54);
            double derecha = margen(mar, "right", 54);
            double arriba = margen(mar, "top", 62);
            // Margen inferior mínimo para que quepa el pie de página.
            double abajo = Math.max(margen(mar, "bottom", 48), 54);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            com.lowagie.text.Document documento = new com.lowagie.text.Document(new Rectangle((float) pagAncho, (float) pagAlto), (float) izquierda, (float) derecha, (float) arriba, (float) abajo);
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            Pagina evento = new Pagina(logo, izquierda, derecha, pagAncho, pagAlto);
            escritor.setPageEvent(evento);
            documento.addTitle("Contrato");
            documento.open();

            double ancho = pagAncho - izquierda - derecha;
            java.util.List<Element> hijosCuerpo = new ArrayList<>();
            NodeList lista = cuerpo.getChildNodes();
            for (int i = 0; i < lista.getLength(); i++) if (lista.item(i) instanceof Element e) hijosCuerpo.add(e);

            // Bloque de firmas: desde «En ____, __ de ____ de 2026» hasta el anexo (salto de página) o el final.
            int inicioFirmas = -1;
            Pattern linea = Pattern.compile("\\s*En\\b.*\\bde\\s+\\d{4}\\s*");
            for (int i = 0; i < hijosCuerpo.size(); i++) {
                Element c = hijosCuerpo.get(i);
                if (es(c, "p") && linea.matcher(plano(c).replace(NBSP, " ")).matches()) {
                    inicioFirmas = i;
                    break;
                }
            }
            int finFirmas = hijosCuerpo.size();
            if (inicioFirmas >= 0) {
                for (int j = inicioFirmas + 1; j < hijosCuerpo.size(); j++) {
                    Element c = hijosCuerpo.get(j);
                    if (!es(c, "p") || saltoDePagina(c)) {
                        finFirmas = j;
                        break;
                    }
                }
            }
            Object[] argumentosFirmas = null;
            Set<Integer> movidos = new TreeSet<>();
            if (inicioFirmas >= 0) {
                // Con las firmas van el párrafo de cierre («Y en prueba de conformidad…») y la última cláusula entera (título incluido).
                int primero = inicioFirmas;
                for (int j = inicioFirmas - 1; j >= 0; j--) {
                    Element c = hijosCuerpo.get(j);
                    if (!es(c, "p")) break;
                    if (plano(c).strip().isEmpty()) continue;
                    primero = j;
                    if (esTituloDeClausula(c)) break;
                }
                java.util.List<Element> antes = new ArrayList<>();
                for (Element c : hijosCuerpo.subList(primero, inicioFirmas)) if (es(c, "p")) antes.add(c);
                Element cierre = null;
                for (int i = antes.size() - 1; i >= 0; i--) {
                    if (plano(antes.get(i)).strip().startsWith("Y en prueba")) {
                        cierre = antes.get(i);
                        break;
                    }
                }
                final Element cierreFinal = cierre;
                antes.removeIf(c -> c == cierreFinal);
                java.util.List<Element> lineas = new ArrayList<>();
                for (Element c : hijosCuerpo.subList(inicioFirmas, finFirmas)) if (es(c, "p")) lineas.add(c);
                if (analizarFirmas(lineas) != null) {
                    argumentosFirmas = new Object[]{antes, cierre, lineas};
                    for (int i = primero; i < finFirmas; i++) movidos.add(i);
                }
            }

            java.util.List<Bloque> historia = new ArrayList<>();
            boolean enPortada = true;
            for (int i = 0; i < hijosCuerpo.size(); i++) {
                Element hijoCuerpo = hijosCuerpo.get(i);
                if (argumentosFirmas != null && movidos.contains(i)) {
                    if (i == ((TreeSet<Integer>) movidos).first()) {
                        // Se dibuja aquí, en su sitio, para que la numeración de las cláusulas siga su orden.
                        @SuppressWarnings("unchecked") java.util.List<Element> antes = (java.util.List<Element>) argumentosFirmas[0];
                        @SuppressWarnings("unchecked") java.util.List<Element> lineas = (java.util.List<Element>) argumentosFirmas[2];
                        java.util.List<Bloque> b = bloqueDeFirmas(antes, (Element) argumentosFirmas[1], lineas, ancho);
                        if (b != null) historia.addAll(b);
                    }
                    continue;
                }
                if (es(hijoCuerpo, "p")) {
                    historia.addAll(parrafo(hijoCuerpo, !enPortada));
                    if (hijo(hijoCuerpo, "pPr", "sectPr") != null) {
                        // Fin de la portada: el resto va en páginas con cabecera y pie.
                        enPortada = false;
                        historia.add(Bloque.deSalto());
                        historia.add(new Bloque(null, true, true)); // marca: a partir de aquí, páginas principales
                    }
                } else if (es(hijoCuerpo, "tbl")) {
                    com.lowagie.text.Element t = tabla(hijoCuerpo, ancho);
                    if (t != null) historia.add(Bloque.de(t, false));
                }
            }

            java.util.List<com.lowagie.text.Element> pendientes = new ArrayList<>();
            for (Bloque b : historia) {
                if (b.salto()) {
                    volcar(documento, pendientes);
                    if (b.juntoConSiguiente()) {
                        evento.principal = true; // marca de «portada terminada»
                    } else {
                        documento.newPage();
                    }
                    continue;
                }
                pendientes.add(b.elemento());
                if (!b.juntoConSiguiente()) volcar(documento, pendientes);
            }
            volcar(documento, pendientes);
            documento.close();
            return salida.toByteArray();
        }

        /** Añade lo pendiente; si son varios elementos (un título y lo que le sigue), van juntos en la misma página. */
        private static void volcar(com.lowagie.text.Document documento, java.util.List<com.lowagie.text.Element> pendientes) throws DocumentException {
            if (pendientes.isEmpty()) return;
            if (pendientes.size() == 1) {
                documento.add(pendientes.get(0));
            } else {
                documento.add(juntar(new ArrayList<>(pendientes)));
            }
            pendientes.clear();
        }
    }
}
