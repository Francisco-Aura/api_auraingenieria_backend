package api.aura.service.modulo_registro_horario.documento;

import api.aura.service.modulo_registro_horario.documento.DatosRegistro.Tramo;
import api.aura.service.modulo_registro_horario.documento.FirmaParaDocumento.Preparada;
import api.aura.service.modulo_registro_horario.documento.ReglasJornada.Jornada;
import api.aura.service.modulo_registro_horario.firma.FirmaService;
import api.aura.service.modulo_registro_horario.firma.Imagen;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static api.aura.service.modulo_registro_horario.documento.ReglasJornada.aMinutos;
import static api.aura.service.modulo_registro_horario.documento.ReglasJornada.fmtHoras;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.W;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.attrW;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.attrWInt;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.descendientesW;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.hijoW;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.hijosW;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.insertarDespues;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.quitar;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.setW;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.texto;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.w;

/**
 * Rellena la plantilla mensual del registro de jornada (cabecera, una fila por día con horas y firma, totales, fecha y
 * firmas del pie). Es el motor del Registro Horario anterior ({@code registro_horas.py}) escrito en Java: sobre el mismo
 * XML del Word y con las mismas medidas, para que el documento salga igual.
 */
@org.springframework.stereotype.Component
public final class MotorWord {

    /** Resultado de rellenar el mes: el Word y los totales. */
    public record Resultado(DocumentoWord documento, int diasTrabajados, int minutosManana, int minutosTarde) {
        public int minutosTotales() {
            return minutosManana + minutosTarde;
        }
    }

    // Bloque de firmas del pie: sangría y anchos (twips) de las dos columnas, como en la plantilla
    private static final int PIE_SANGRIA = 330, PIE_ANCHO_IZQ = 6134, PIE_ANCHO_DER = 4544;

    // Alturas de fila de la tabla de días (en twips; 1 cm = 567 twips). La hoja debe seguir cabiendo en una página, así
    // que las filas de días trabajados crecen solo con la holgura que quede tras compactar lo demás.
    private static final int ALTO_FILA_BASE = 252;     // la de la plantilla
    private static final int ALTO_FILA_LIBRE = 215;    // fines de semana, festivos y días que el mes no tiene (filas vacías)
    private static final int MARGEN_SEGURIDAD = 340;   // ~0,6 cm que se dejan libres al final de la hoja
    private static final int EXTRA_MAX = 230;          // tope de crecimiento por fila (~0,4 cm)
    private static final double PADDING_FIRMA_CM = 0.10; // aire entre la firma y el borde de su fila

    // Opciones de firma que usa la web (las del escritorio eran configurables)
    private static final boolean QUITAR_FONDO_BLANCO = true;
    private static final double ALTO_FINAL_CM = 2.0;

    private final FirmaService firmas;

    public MotorWord(FirmaService firmas) {
        this.firmas = firmas;
    }

    // ---------------------------------------------------------------- punto de entrada

    /**
     * Rellena la plantilla en memoria con los datos del mes y la firma (bytes de un PNG).
     *
     * @throws DatosNoValidosException si faltan datos, las horas no cuadran o no hay ningún día trabajado
     */
    public Resultado construir(DatosRegistro cfg, int anio, int mes, byte[] firma) {
        comprobarCompleto(cfg, firma);
        Imagen imagenFirma = firmas.decodificar(firma);
        DocumentoWord doc = DocumentoWord.desdePlantilla();
        rellenarCabecera(doc, cfg, anio, mes);
        int[] totales = rellenarDias(doc, cfg, anio, mes, imagenFirma);
        if (totales[0] == 0) {
            throw new DatosNoValidosException("no hay ningún día trabajado en el periodo");
        }
        rellenarFecha(doc, cfg, anio, mes);
        reconstruirPie(doc, imagenFirma);
        return new Resultado(doc, totales[0], totales[1], totales[2]);
    }

    /** No se genera ningún documento con campos obligatorios vacíos ni sin la firma de cada día. */
    static void comprobarCompleto(DatosRegistro d, byte[] firma) {
        List<String> falta = new ArrayList<>();
        if (vacio(d.trabajador())) {
            falta.add("nombre del trabajador");
        }
        if (vacio(d.nif())) {
            falta.add("NIF");
        }
        if (vacio(d.numAfiliacion())) {
            falta.add("nº de afiliación");
        }
        if (vacio(d.empresa())) {
            falta.add("empresa");
        }
        if (vacio(d.cif())) {
            falta.add("CIF de la empresa");
        }
        if (vacio(d.ccc())) {
            falta.add("CCC");
        }
        if (vacio(d.centroTrabajo())) {
            falta.add("centro de trabajo");
        }
        if (firma == null || firma.length == 0) {
            falta.add("imagen de la firma");
        }
        if (!falta.isEmpty()) {
            throw new DatosNoValidosException("faltan datos obligatorios: " + String.join(", ", falta));
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    // ---------------------------------------------------------------- celdas y texto

    private static Element parrafo(Element tc) {
        return hijosW(tc, "p").get(0);
    }

    private static Element rpr(Document d, int tam, boolean negrita, boolean cursiva) {
        Element rpr = w(d, "rPr");
        Element fuentes = w(d, "rFonts");
        for (String a : new String[]{"ascii", "hAnsi", "eastAsia", "cs"}) {
            setW(fuentes, a, "Arial");
        }
        rpr.appendChild(fuentes);
        if (negrita) {
            rpr.appendChild(w(d, "b"));
        }
        if (cursiva) {
            rpr.appendChild(w(d, "i"));
        }
        rpr.appendChild(w(d, "color", "val", "000000"));
        rpr.appendChild(w(d, "sz", "val", String.valueOf(tam)));
        rpr.appendChild(w(d, "szCs", "val", String.valueOf(tam)));
        return rpr;
    }

    /** Sustituye el contenido de la celda por texto centrado en Arial. */
    private static void escribirCelda(Element tc, String texto, int tam, boolean negrita, boolean cursiva) {
        Document d = tc.getOwnerDocument();
        Element p = parrafo(tc);
        for (Element r : hijosW(p, "r")) {
            p.removeChild(r);
        }
        Element ppr = hijoW(p, "pPr");
        if (ppr == null) {
            ppr = w(d, "pPr");
            p.insertBefore(ppr, p.getFirstChild());
        }
        List<Element> viejos = new ArrayList<>(hijosW(ppr, "jc"));
        viejos.addAll(hijosW(ppr, "spacing"));
        viejos.addAll(hijosW(ppr, "rPr"));
        viejos.forEach(ppr::removeChild);
        ppr.appendChild(w(d, "spacing", "before", "0", "after", "0", "line", "240", "lineRule", "auto"));
        ppr.appendChild(w(d, "jc", "val", "center"));
        ppr.appendChild(rpr(d, tam, false, false)); // la marca de párrafo también cuenta para la altura de línea
        Element r = w(d, "r");
        r.appendChild(rpr(d, tam, negrita, cursiva));
        Element t = w(d, "t");
        t.setTextContent(texto);
        r.appendChild(t);
        p.appendChild(r);
    }

    private static void escribirCelda(Element tc, String texto) {
        escribirCelda(tc, texto, 18, false, false);
    }

    /** Inserta la imagen (en línea) al final del párrafo, en un run nuevo, con el tamaño dado en EMU. */
    private static void anadirImagen(DocumentoWord doc, Element parrafo, byte[] png, long ancho, long alto) {
        Document d = doc.xml();
        String rId = doc.anadirImagen(png);
        int id = doc.siguienteId();
        Element r = w(d, "r");
        Element dibujo = w(d, "drawing");
        Element inline = XmlDocx.crear(d, XmlDocx.WP, "wp", "inline");
        Element extension = XmlDocx.crear(d, XmlDocx.WP, "wp", "extent");
        extension.setAttribute("cx", String.valueOf(ancho));
        extension.setAttribute("cy", String.valueOf(alto));
        inline.appendChild(extension);
        Element docPr = XmlDocx.crear(d, XmlDocx.WP, "wp", "docPr");
        docPr.setAttribute("id", String.valueOf(id));
        docPr.setAttribute("name", "Picture " + id);
        inline.appendChild(docPr);
        Element marco = XmlDocx.crear(d, XmlDocx.WP, "wp", "cNvGraphicFramePr");
        Element candado = XmlDocx.crear(d, XmlDocx.A, "a", "graphicFrameLocks");
        candado.setAttribute("noChangeAspect", "1");
        marco.appendChild(candado);
        inline.appendChild(marco);
        Element grafico = XmlDocx.crear(d, XmlDocx.A, "a", "graphic");
        Element datos = XmlDocx.crear(d, XmlDocx.A, "a", "graphicData");
        datos.setAttribute("uri", XmlDocx.PIC);
        Element pic = XmlDocx.crear(d, XmlDocx.PIC, "pic", "pic");
        Element nv = XmlDocx.crear(d, XmlDocx.PIC, "pic", "nvPicPr");
        Element cNvPr = XmlDocx.crear(d, XmlDocx.PIC, "pic", "cNvPr");
        cNvPr.setAttribute("id", "0");
        cNvPr.setAttribute("name", "image.png");
        nv.appendChild(cNvPr);
        nv.appendChild(XmlDocx.crear(d, XmlDocx.PIC, "pic", "cNvPicPr"));
        pic.appendChild(nv);
        Element relleno = XmlDocx.crear(d, XmlDocx.PIC, "pic", "blipFill");
        Element blip = XmlDocx.crear(d, XmlDocx.A, "a", "blip");
        blip.setAttributeNS(XmlDocx.R, "r:embed", rId);
        relleno.appendChild(blip);
        Element estirar = XmlDocx.crear(d, XmlDocx.A, "a", "stretch");
        estirar.appendChild(XmlDocx.crear(d, XmlDocx.A, "a", "fillRect"));
        relleno.appendChild(estirar);
        pic.appendChild(relleno);
        Element sp = XmlDocx.crear(d, XmlDocx.PIC, "pic", "spPr");
        Element xfrm = XmlDocx.crear(d, XmlDocx.A, "a", "xfrm");
        Element off = XmlDocx.crear(d, XmlDocx.A, "a", "off");
        off.setAttribute("x", "0");
        off.setAttribute("y", "0");
        xfrm.appendChild(off);
        Element ext = XmlDocx.crear(d, XmlDocx.A, "a", "ext");
        ext.setAttribute("cx", String.valueOf(ancho));
        ext.setAttribute("cy", String.valueOf(alto));
        xfrm.appendChild(ext);
        sp.appendChild(xfrm);
        Element geometria = XmlDocx.crear(d, XmlDocx.A, "a", "prstGeom");
        geometria.setAttribute("prst", "rect");
        sp.appendChild(geometria);
        pic.appendChild(sp);
        datos.appendChild(pic);
        grafico.appendChild(datos);
        inline.appendChild(grafico);
        dibujo.appendChild(inline);
        r.appendChild(dibujo);
        parrafo.appendChild(r);
    }

    private static long cm(double cm) {
        return (long) (cm * 360000); // EMU, truncado como Cm() de python-docx
    }

    /** Inserta la firma (en línea) centrada en la celda. */
    private static void firmarCelda(DocumentoWord doc, Element tc, Preparada firma, double altoCm) {
        escribirCelda(tc, "", 8, false, false);
        long alto = cm(altoCm);
        long ancho = (long) (alto * firma.aspecto());
        long tope = cm(4.5);
        if (ancho > tope) {
            ancho = tope;
            alto = (long) (tope / firma.aspecto());
        }
        anadirImagen(doc, parrafo(tc), firma.png(), ancho, alto);
    }

    private static Element copiarRpr(Element run) {
        Element rpr = hijoW(run, "rPr");
        return rpr != null ? (Element) rpr.cloneNode(true) : w(run.getOwnerDocument(), "rPr");
    }

    /** Añade el valor tras la etiqueta de la celda de cabecera (usa el tabulador de la plantilla). */
    private static void anadirValor(Element tc, String valor) {
        Document d = tc.getOwnerDocument();
        Element p = parrafo(tc);
        List<Element> runs = new ArrayList<>();
        for (Element r : hijosW(p, "r")) {
            if (hijoW(r, "t") != null) {
                runs.add(r);
            }
        }
        Element modelo = runs.get(0);
        boolean hayTab = runs.stream().anyMatch(r -> hijoW(r, "tab") != null);
        if (!hayTab) {
            Element tabRun = w(d, "r");
            tabRun.appendChild(copiarRpr(modelo));
            tabRun.appendChild(w(d, "tab"));
            insertarDespues(runs.get(runs.size() - 1), tabRun);
            runs.set(runs.size() - 1, tabRun);
        }
        Element nuevo = w(d, "r");
        Element rpr = copiarRpr(modelo);
        for (String tag : new String[]{"sz", "szCs"}) { // los valores en letra pequeña (7 pt), como los de la empresa en la plantilla
            for (Element viejo : hijosW(rpr, tag)) {
                setW(viejo, "val", "14");
            }
        }
        nuevo.appendChild(rpr);
        Element t = w(d, "t");
        t.setAttributeNS(XmlDocx.XML, "xml:space", "preserve");
        t.setTextContent(valor);
        nuevo.appendChild(t);
        insertarDespues(runs.get(runs.size() - 1), nuevo);
    }

    // ---------------------------------------------------------------- relleno

    private static List<Element> tablas(Element cuerpo) {
        return hijosW(cuerpo, "tbl");
    }

    private static Element celda(Element tabla, int fila, int columna) {
        return hijosW(hijosW(tabla, "tr").get(fila), "tc").get(columna);
    }

    private static void rellenarCabecera(DocumentoWord doc, DatosRegistro d, int anio, int mes) {
        Element t0 = tablas(doc.cuerpo()).get(0);
        record Dato(int fila, int columna, String valor) {
        }
        List<Dato> celdas = List.of( // (fila, columna) -> valor
                new Dato(1, 0, d.cif()), new Dato(0, 1, d.trabajador()), new Dato(1, 1, d.nif()),
                new Dato(2, 1, d.numAfiliacion()), new Dato(3, 0, d.ccc()),
                new Dato(3, 1, String.format("%02d/%d", mes, anio))); // mm/aaaa
        for (Dato c : celdas) {
            if (!vacio(c.valor())) {
                anadirValor(celda(t0, c.fila(), c.columna()), c.valor());
            }
        }
        record Cabecera(int fila, String valor) {
        }
        for (Cabecera c : List.of(new Cabecera(0, d.empresa()), new Cabecera(2, d.centroTrabajo()))) {
            if (!vacio(c.valor())) {
                Element tc = celda(t0, c.fila(), 0);
                Element p = parrafo(tc);
                List<Element> runs = new ArrayList<>();
                for (Element r : hijosW(p, "r")) {
                    if (hijoW(r, "t") != null) {
                        runs.add(r);
                    }
                }
                for (Element extra : runs.subList(1, runs.size())) { // se conserva solo "Etiqueta:<tab>" y se pone el valor nuevo
                    p.removeChild(extra);
                }
                anadirValor(tc, c.valor());
            }
        }
    }

    /** Texto de un párrafo como lo da python-docx: tabuladores como \t y saltos como \n. */
    private static String textoParrafo(Element p) {
        StringBuilder sb = new StringBuilder();
        for (Element h : XmlDocx.hijos(p)) {
            if (XmlDocx.esW(h, "r")) {
                textoRun(h, sb);
            } else if (XmlDocx.esW(h, "hyperlink")) {
                hijosW(h, "r").forEach(r -> textoRun(r, sb));
            }
        }
        return sb.toString();
    }

    private static void textoRun(Element r, StringBuilder sb) {
        for (Element h : XmlDocx.hijos(r)) {
            if (XmlDocx.esW(h, "t")) {
                sb.append(h.getTextContent());
            } else if (XmlDocx.esW(h, "tab")) {
                sb.append('\t');
            } else if (XmlDocx.esW(h, "br") || XmlDocx.esW(h, "cr")) {
                sb.append('\n');
            }
        }
    }

    private static void rellenarFecha(DocumentoWord doc, DatosRegistro cfg, int anio, int mes) {
        String modo = cfg.fechaFirma() == null ? "ultimo_dia" : cfg.fechaFirma();
        LocalDate f;
        if (modo.equals("ultimo_dia")) {
            f = YearMonth.of(anio, mes).atEndOfMonth();
        } else if (modo.equals("hoy")) {
            f = LocalDate.now();
        } else {
            f = LocalDate.parse(modo);
        }
        Element par = hijosW(doc.cuerpo(), "p").stream().filter(p -> textoParrafo(p).strip().startsWith("En\t"))
                .findFirst().orElseThrow(() -> new IllegalStateException("La plantilla no tiene la línea de la fecha"));
        java.util.Map<String, String> sustituciones = new java.util.HashMap<>();
        sustituciones.put("31", String.valueOf(f.getDayOfMonth()));
        sustituciones.put("OCTUBRE", ReglasJornada.MESES[f.getMonthValue() - 1]);
        sustituciones.put("2026", String.valueOf(f.getYear()));
        if (!vacio(cfg.localidad())) {
            sustituciones.put("LORCA", cfg.localidad());
        }
        for (Element t : descendientesW(par, "t")) {
            String nuevo = sustituciones.get(t.getTextContent());
            if (nuevo != null) {
                t.setTextContent(nuevo);
            }
        }
    }

    // ---------------------------------------------------------------- alturas

    /** Altura estimada (twips) de un párrafo del cuerpo. */
    static int altoParrafo(Element p) {
        Element ppr = hijoW(p, "pPr");
        int antes = 0, despues = 0;
        Integer linea = null;
        if (ppr != null) {
            Element sp = hijoW(ppr, "spacing");
            if (sp != null) {
                antes = attrWInt(sp, "before", 0);
                despues = attrWInt(sp, "after", 0);
                String l = attrW(sp, "line");
                if (l != null && !l.isEmpty() && "exact".equals(attrW(sp, "lineRule"))) {
                    linea = Integer.parseInt(l.strip());
                }
            }
        }
        if (linea == null) {
            int mx = 22; // fuente por defecto: 11 pt (en medios puntos)
            Element rprMarca = ppr != null ? hijoW(ppr, "rPr") : null;
            if (rprMarca != null && hijoW(rprMarca, "sz") != null) {
                mx = attrWInt(hijoW(rprMarca, "sz"), "val", 0);
            }
            for (Element r : descendientesW(p, "r")) {
                Element rpr = hijoW(r, "rPr");
                if (rpr != null && hijoW(rpr, "sz") != null && hijoW(r, "t") != null) {
                    mx = Math.max(mx, attrWInt(hijoW(rpr, "sz"), "val", 0));
                }
            }
            linea = (int) (mx / 2.0 * 1.15 * 20); // línea sencilla ≈ 1,15 × cuerpo de letra
        }
        return antes + linea + despues;
    }

    /** Altura estimada de todo el contenido de la hoja (párrafos y tablas). */
    private static int contenidoTwips(DocumentoWord doc) {
        int total = 0;
        for (Element el : XmlDocx.hijos(doc.cuerpo())) {
            if (XmlDocx.esW(el, "p")) {
                total += altoParrafo(el);
            } else if (XmlDocx.esW(el, "tbl")) {
                for (Element tr : hijosW(el, "tr")) {
                    Element h = hijoW(tr, "trPr", "trHeight");
                    total += h != null ? Integer.parseInt(attrW(h, "val").strip()) : 250;
                }
            }
        }
        return total;
    }

    private static int areaUtilTwips(DocumentoWord doc) {
        Element sp = descendientesW(doc.cuerpo(), "sectPr").get(0);
        Element pg = hijoW(sp, "pgSz"), mar = hijoW(sp, "pgMar");
        return attrWInt(pg, "h", 0) - attrWInt(mar, "top", 0) - attrWInt(mar, "bottom", 0);
    }

    private static void fijarAltoFila(Element tr, int twips) {
        Document d = tr.getOwnerDocument();
        Element trpr = hijoW(tr, "trPr");
        if (trpr == null) {
            trpr = w(d, "trPr");
            tr.insertBefore(trpr, tr.getFirstChild());
        }
        Element h = hijoW(trpr, "trHeight");
        if (h == null) {
            h = w(d, "trHeight");
            trpr.appendChild(h);
        }
        setW(h, "val", String.valueOf(twips));
        setW(h, "hRule", "exact");
    }

    private static void centrarVertical(Element tc) {
        Document d = tc.getOwnerDocument();
        Element tcpr = hijoW(tc, "tcPr");
        for (Element v : hijosW(tcpr, "vAlign")) {
            tcpr.removeChild(v);
        }
        Element v = w(d, "vAlign", "val", "center");
        // vAlign va tras los elementos que el esquema coloca antes que él (tcW, gridSpan, bordes, sombreado...)
        List<String> antes = List.of("tcW", "gridSpan", "hMerge", "vMerge", "tcBorders", "shd", "noWrap", "tcMar",
                "textDirection", "tcFitText");
        Element ultimo = null;
        for (Element e : XmlDocx.hijos(tcpr)) {
            if (W.equals(e.getNamespaceURI()) && antes.contains(e.getLocalName())) {
                ultimo = e;
            }
        }
        if (ultimo != null) {
            insertarDespues(ultimo, v);
        } else {
            tcpr.insertBefore(v, tcpr.getFirstChild());
        }
    }

    /** Una sola casilla a lo ancho de las 4 columnas de horas, con el texto centrado y en negrita. */
    private static void combinarHoras(Element tr, String texto) {
        Document d = tr.getOwnerDocument();
        List<Element> tcs = hijosW(tr, "tc");
        int ancho = 0;
        for (Element tc : tcs.subList(1, 5)) {
            ancho += Integer.parseInt(attrW(hijoW(tc, "tcPr", "tcW"), "w").strip());
        }
        for (Element tc : tcs.subList(2, 5)) {
            tr.removeChild(tc);
        }
        Element tcpr = hijoW(tcs.get(1), "tcPr");
        Element tcw = hijoW(tcpr, "tcW");
        setW(tcw, "w", String.valueOf(ancho));
        for (Element g : hijosW(tcpr, "gridSpan")) {
            tcpr.removeChild(g);
        }
        insertarDespues(tcw, w(d, "gridSpan", "val", "4"));
        escribirCelda(tcs.get(1), texto, 18, true, false);
        centrarVertical(tcs.get(1));
    }

    private static boolean sinTexto(Element el) {
        return XmlDocx.esW(el, "p") && texto(el).isBlank() && descendientesW(el, "drawing").isEmpty()
                && hijoW(el, "pPr", "sectPr") == null;
    }

    /** Quita las líneas en blanco sobrantes entre la fecha y el texto legal (deja una). */
    private static void quitarBlancosTrasFecha(DocumentoWord doc) {
        List<Element> cuerpo = XmlDocx.hijos(doc.cuerpo());
        int ini = -1, fin = -1;
        for (int i = 0; i < cuerpo.size() && ini < 0; i++) {
            if (XmlDocx.esW(cuerpo.get(i), "p") && texto(cuerpo.get(i)).startsWith("En")) {
                ini = i;
            }
        }
        for (int i = 0; i < cuerpo.size() && fin < 0; i++) {
            if (XmlDocx.esW(cuerpo.get(i), "p") && texto(cuerpo.get(i)).startsWith("Registro realizado")) {
                fin = i;
            }
        }
        if (ini < 0 || fin < 0 || fin <= ini) {
            return;
        }
        List<Element> vacios = new ArrayList<>();
        for (Element el : cuerpo.subList(ini + 1, fin)) {
            if (sinTexto(el)) {
                vacios.add(el);
            }
        }
        List<Element> quitados = vacios.isEmpty() ? List.of() : vacios.subList(1, vacios.size());
        int altura = 0;
        for (Element el : quitados) {
            altura += altoParrafo(el);
        }
        for (Element el : quitados) {
            quitar(el);
        }
        // Las líneas bajo la fecha son un dibujo anclado al párrafo del texto legal (con desplazamiento negativo,
        // calculado para el espacio original). Al subir ese párrafo hay que compensar el desplazamiento.
        compensarLineas(cuerpo.get(fin), altura);
    }

    private static final Pattern MARGEN_SUPERIOR = Pattern.compile("margin-top:(-?[\\d.]+)pt");

    /** Baja el anclaje de los dibujos del párrafo {@code twips}, tanto en DrawingML como en su alternativa VML. */
    private static void compensarLineas(Element parrafo, int twips) {
        for (Element off : XmlDocx.descendientes(parrafo, XmlDocx.WP, "positionV")) {
            Element pos = XmlDocx.hijo(off, XmlDocx.WP, "posOffset");
            if (pos != null && "paragraph".equals(off.getAttribute("relativeFrom"))) {
                pos.setTextContent(String.valueOf(Long.parseLong(pos.getTextContent().strip()) + (long) (twips * 635.0)));
            }
        }
        for (Element el : XmlDocx.todos(parrafo)) {
            String estilo = el.hasAttributeNS(null, "style") ? el.getAttributeNS(null, "style") : null;
            if (estilo != null && estilo.contains("margin-top:")) {
                Matcher m = MARGEN_SUPERIOR.matcher(estilo);
                StringBuilder sb = new StringBuilder();
                while (m.find()) {
                    BigDecimal valor = new BigDecimal(Double.parseDouble(m.group(1)) + twips / 20.0)
                            .setScale(2, RoundingMode.HALF_EVEN); // como el «:.2f» de Python
                    m.appendReplacement(sb, Matcher.quoteReplacement("margin-top:" + valor.toPlainString() + "pt"));
                }
                m.appendTail(sb);
                el.setAttributeNS(null, "style", sb.toString());
            }
        }
    }

    /**
     * Reparte la altura de la hoja: filas vacías bajas y filas de días trabajados lo más altas posible. {@code trabaja}
     * es una lista de 31 booleanos (uno por fila de día). Devuelve el alto (twips) de las filas trabajadas.
     */
    private static int ajustarAlturas(DocumentoWord doc, Element tabla, boolean[] trabaja) {
        quitarBlancosTrasFecha(doc);
        List<Element> filas = hijosW(tabla, "tr").subList(2, 33);
        int n = 0;
        for (int i = 0; i < filas.size(); i++) {
            fijarAltoFila(filas.get(i), trabaja[i] ? ALTO_FILA_BASE : ALTO_FILA_LIBRE);
            if (trabaja[i]) {
                n++;
            }
        }
        if (n == 0) {
            return ALTO_FILA_BASE;
        }
        int holgura = areaUtilTwips(doc) - contenidoTwips(doc) - MARGEN_SEGURIDAD;
        int extra = Math.max(0, Math.min(EXTRA_MAX, Math.floorDiv(holgura, n)));
        for (int i = 0; i < filas.size(); i++) {
            if (trabaja[i]) {
                fijarAltoFila(filas.get(i), ALTO_FILA_BASE + extra);
            }
        }
        return ALTO_FILA_BASE + extra;
    }

    /** Devuelve {días trabajados, minutos de mañana, minutos de tarde}. */
    private int[] rellenarDias(DocumentoWord doc, DatosRegistro cfg, int anio, int mes, Imagen imagenFirma) {
        Element tabla = tablas(doc.cuerpo()).get(1);
        int nDias = YearMonth.of(anio, mes).lengthOfMonth();
        List<Element> filas = hijosW(tabla, "tr").subList(2, 33);

        // Primero se decide qué días se trabajan, para repartir la altura de la hoja entre las filas
        List<Jornada> plan = new ArrayList<>();
        for (Element fila : filas) {
            int num = Integer.parseInt(hijosW(fila, "tc").get(0).getTextContent().strip());
            plan.add(num <= nDias ? ReglasJornada.jornadaDelDia(LocalDate.of(anio, mes, num), cfg) : new Jornada(List.of(), null));
        }
        boolean[] trabaja = new boolean[plan.size()];
        for (int i = 0; i < trabaja.length; i++) {
            trabaja[i] = !plan.get(i).tramos().isEmpty();
        }
        int altoFila = ajustarAlturas(doc, tabla, trabaja);
        double altoFirmaCm = Math.max(0.30, altoFila * 2.54 / 1440 - PADDING_FIRMA_CM);
        Preparada firma = FirmaParaDocumento.preparar(imagenFirma, QUITAR_FONDO_BLANCO, altoFirmaCm);

        int totM = 0, totT = 0, trabajados = 0;
        for (int i = 0; i < filas.size(); i++) {
            Element fila = filas.get(i);
            Jornada j = plan.get(i);
            List<Element> tcs = hijosW(fila, "tc");
            if (j.tramos().isEmpty()) {
                if (j.nota() != null) {
                    combinarHoras(fila, j.nota());
                }
                continue;
            }
            for (Element tc : tcs) {
                centrarVertical(tc);
            }
            for (int k = 0; k < j.tramos().size(); k++) {
                Tramo t = j.tramos().get(k);
                escribirCelda(tcs.get(1 + 2 * k), t.ini());
                escribirCelda(tcs.get(2 + 2 * k), t.fin());
                int dur = aMinutos(t.fin()) - aMinutos(t.ini());
                if (k == 0) {
                    totM += dur;
                } else {
                    totT += dur;
                }
            }
            trabajados++;
            firmarCelda(doc, tcs.get(5), firma, altoFirmaCm);
        }
        // Filas TOTAL / HRAS.: celdas combinadas verticalmente, se escribe en la primera.
        List<Element> tot = hijosW(hijosW(tabla, "tr").get(33), "tc");
        escribirCelda(tot.get(1), fmtHoras(totM), 18, true, false);
        escribirCelda(tot.get(2), fmtHoras(totT), 18, true, false);
        escribirCelda(tot.get(3), fmtHoras(totM + totT), 18, true, false);
        return new int[]{trabajados, totM, totT};
    }

    // ---------------------------------------------------------------- pie de firmas

    private static boolean esBlanco(Element el) {
        return sinTexto(el);
    }

    private static Element fragmento(Document destino, String xml) {
        Document d = DocumentoWord.leerXml(("<raiz xmlns:w=\"" + W + "\">" + xml + "</raiz>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return (Element) destino.importNode(XmlDocx.primerHijoElemento(d.getDocumentElement()), true);
    }

    private static String parrafoEtiqueta(String texto) {
        return "<w:p><w:pPr><w:spacing w:before=\"209\" w:after=\"5\" w:line=\"178\" w:lineRule=\"exact\"/></w:pPr>"
                + "<w:r><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:eastAsia=\"Arial\" w:cs=\"Arial\"/>"
                + "<w:color w:val=\"000000\"/><w:sz w:val=\"16\"/><w:szCs w:val=\"16\"/><w:lang w:val=\"es-es\"/></w:rPr>"
                + "<w:t>" + texto + "</w:t></w:r></w:p>";
    }

    private static String celdaFirma(int ancho, String etiqueta) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + ancho + "\" w:type=\"dxa\"/></w:tcPr>" + parrafoEtiqueta(etiqueta)
                + "<w:p><w:pPr><w:spacing w:before=\"40\" w:after=\"0\"/></w:pPr></w:p></w:tc>";
    }

    /** Firma en línea, bajo la etiqueta, en el segundo párrafo de la celda. */
    private void ponerFirmaEn(DocumentoWord doc, Element tc, Imagen imagenFirma, double altoCm, double anchoMaxCm) {
        Preparada firma = FirmaParaDocumento.preparar(imagenFirma, QUITAR_FONDO_BLANCO, altoCm);
        double ancho = cm(altoCm) * firma.aspecto();
        double alto = cm(altoCm);
        if (ancho > cm(anchoMaxCm)) {
            ancho = cm(anchoMaxCm);
            alto = cm(anchoMaxCm) / firma.aspecto();
        }
        anadirImagen(doc, hijosW(tc, "p").get(1), firma.png(), (long) ancho, (long) alto);
    }

    /**
     * Rehace «Firma de la empresa: / Firma del trabajador:» como una tabla de dos columnas sin bordes. En la plantilla la
     * segunda etiqueta se colocaba con un tabulador, que cada programa interpreta a su manera (a veces las dos etiquetas
     * salían pegadas). La tabla se coloca igual en todos y deja el mismo espacio de antes.
     */
    private void reconstruirPie(DocumentoWord doc, Imagen imagenFirma) {
        List<Element> cuerpo = XmlDocx.hijos(doc.cuerpo());
        int i = -1;
        for (int k = 0; k < cuerpo.size(); k++) {
            if (XmlDocx.esW(cuerpo.get(k), "p") && texto(cuerpo.get(k)).startsWith("Firma de la empresa")) {
                i = k;
                break;
            }
        }
        if (i < 0) {
            return;
        }
        int j = i + 1;
        while (j < cuerpo.size() && XmlDocx.esW(cuerpo.get(j), "p") && esBlanco(cuerpo.get(j))) { // las líneas en blanco de debajo eran el espacio para firmar
            j++;
        }
        List<Element> sustituidos = cuerpo.subList(i, j);
        int alto = 0; // la tabla ocupa lo mismo: la hoja no cambia de altura
        for (Element el : sustituidos) {
            alto += altoParrafo(el);
        }
        StringBuilder nil = new StringBuilder(), cero = new StringBuilder();
        for (String lado : new String[]{"top", "left", "bottom", "right", "insideH", "insideV"}) {
            nil.append("<w:").append(lado).append(" w:val=\"nil\"/>");
        }
        for (String lado : new String[]{"top", "left", "bottom", "right"}) {
            cero.append("<w:").append(lado).append(" w:w=\"0\" w:type=\"dxa\"/>");
        }
        Element tabla = fragmento(doc.xml(),
                "<w:tbl><w:tblPr><w:tblW w:w=\"" + (PIE_ANCHO_IZQ + PIE_ANCHO_DER) + "\" w:type=\"dxa\"/>"
                        + "<w:tblInd w:w=\"" + PIE_SANGRIA + "\" w:type=\"dxa\"/><w:tblBorders>" + nil + "</w:tblBorders>"
                        + "<w:tblLayout w:type=\"fixed\"/><w:tblCellMar>" + cero + "</w:tblCellMar></w:tblPr>"
                        + "<w:tblGrid><w:gridCol w:w=\"" + PIE_ANCHO_IZQ + "\"/><w:gridCol w:w=\"" + PIE_ANCHO_DER + "\"/></w:tblGrid>"
                        + "<w:tr><w:trPr><w:cantSplit/><w:trHeight w:val=\"" + alto + "\" w:hRule=\"exact\"/></w:trPr>"
                        + celdaFirma(PIE_ANCHO_IZQ, "Firma de la empresa:") + celdaFirma(PIE_ANCHO_DER, "Firma del trabajador:")
                        + "</w:tr></w:tbl>");
        sustituidos.get(0).getParentNode().insertBefore(tabla, sustituidos.get(0));
        for (Element el : new ArrayList<>(sustituidos)) {
            quitar(el);
        }
        List<Element> celdas = hijosW(hijosW(tabla, "tr").get(0), "tc");
        Element celdaTrabajador = celdas.get(1);
        // la firma crece hasta 2 cm de alto (o 6 cm de ancho)
        ponerFirmaEn(doc, celdaTrabajador, imagenFirma, ALTO_FINAL_CM, 6.0);
    }
}
