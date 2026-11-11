package api.aura.service.modulo_registro_horario.documento;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/** Utilidades para recorrer y modificar el XML de un Word (DOM con espacios de nombres), con los nombres que usaba el motor anterior. */
final class XmlDocx {

    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    static final String PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture";
    static final String WPS = "http://schemas.microsoft.com/office/word/2010/wordprocessingShape";
    static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    static final String XML = "http://www.w3.org/XML/1998/namespace";

    private XmlDocx() {
    }

    static Element crear(Document d, String ns, String prefijo, String local) {
        return d.createElementNS(ns, prefijo + ":" + local);
    }

    /** Elemento de Word ({@code w:xxx}) sin hijos. */
    static Element w(Document d, String local) {
        return crear(d, W, "w", local);
    }

    /** Elemento de Word con atributos {@code w:nombre="valor"} (pares nombre, valor). */
    static Element w(Document d, String local, String... atributos) {
        Element e = w(d, local);
        for (int i = 0; i < atributos.length; i += 2) {
            setW(e, atributos[i], atributos[i + 1]);
        }
        return e;
    }

    static boolean es(Node n, String ns, String local) {
        return n instanceof Element e && ns.equals(e.getNamespaceURI()) && local.equals(e.getLocalName());
    }

    static boolean esW(Node n, String local) {
        return es(n, W, local);
    }

    /** Hijos directos que son elementos. */
    static List<Element> hijos(Node padre) {
        List<Element> r = new ArrayList<>();
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                r.add(e);
            }
        }
        return r;
    }

    /** Hijos directos con ese nombre (como {@code findall("w:xxx")}). */
    static List<Element> hijos(Node padre, String ns, String local) {
        List<Element> r = new ArrayList<>();
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (es(n, ns, local)) {
                r.add((Element) n);
            }
        }
        return r;
    }

    static List<Element> hijosW(Node padre, String local) {
        return hijos(padre, W, local);
    }

    /** Primer hijo directo con ese nombre, o {@code null} (como {@code find("w:xxx")}). */
    static Element hijo(Node padre, String ns, String local) {
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (es(n, ns, local)) {
                return (Element) n;
            }
        }
        return null;
    }

    static Element hijoW(Node padre, String local) {
        return hijo(padre, W, local);
    }

    /** Ruta de dos niveles, como {@code find("w:a/w:b")}. */
    static Element hijoW(Node padre, String local1, String local2) {
        Element a = hijoW(padre, local1);
        return a == null ? null : hijoW(a, local2);
    }

    /** Todos los descendientes con ese nombre, en orden del documento (sin incluir el propio elemento). */
    static List<Element> descendientes(Node raiz, String ns, String local) {
        List<Element> r = new ArrayList<>();
        recorrer(raiz, ns, local, r);
        return r;
    }

    private static void recorrer(Node n, String ns, String local, List<Element> r) {
        for (Node h = n.getFirstChild(); h != null; h = h.getNextSibling()) {
            if (h instanceof Element e) {
                if (ns == null || es(e, ns, local)) {
                    r.add(e);
                }
                recorrer(e, ns, local, r);
            }
        }
    }

    static List<Element> descendientesW(Node raiz, String local) {
        return descendientes(raiz, W, local);
    }

    /** Todos los elementos descendientes, incluido el propio (como {@code el.iter()}). */
    static List<Element> todos(Element raiz) {
        List<Element> r = new ArrayList<>();
        r.add(raiz);
        recorrer(raiz, null, null, r);
        return r;
    }

    /** Texto de todos los {@code w:t} del elemento, pegados. */
    static String texto(Element e) {
        StringBuilder sb = new StringBuilder();
        for (Element t : descendientesW(e, "t")) {
            sb.append(t.getTextContent());
        }
        return sb.toString();
    }

    /** Valor del atributo {@code w:nombre}, o {@code null} si no está. */
    static String attrW(Element e, String nombre) {
        return e.hasAttributeNS(W, nombre) ? e.getAttributeNS(W, nombre) : null;
    }

    static int attrWInt(Element e, String nombre, int porDefecto) {
        String v = attrW(e, nombre);
        return v == null ? porDefecto : Integer.parseInt(v.strip());
    }

    static void setW(Element e, String nombre, String valor) {
        e.setAttributeNS(W, "w:" + nombre, valor);
    }

    static void insertarDespues(Node referencia, Node nuevo) {
        referencia.getParentNode().insertBefore(nuevo, referencia.getNextSibling());
    }

    static void quitar(Node n) {
        n.getParentNode().removeChild(n);
    }

    static Element primerHijoElemento(Node n) {
        for (Node h = n.getFirstChild(); h != null; h = h.getNextSibling()) {
            if (h instanceof Element e) {
                return e;
            }
        }
        return null;
    }

    static List<Node> lista(NodeList l) {
        List<Node> r = new ArrayList<>();
        for (int i = 0; i < l.getLength(); i++) {
            r.add(l.item(i));
        }
        return r;
    }
}
