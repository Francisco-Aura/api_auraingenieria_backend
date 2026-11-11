package api.aura.service.modulo_registro_horario.documento;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.w3c.dom.Element;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static api.aura.service.modulo_registro_horario.documento.XmlDocx.A;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.R;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.W;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.WP;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.WPS;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.attrW;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.descendientes;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.descendientesW;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.hijo;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.hijoW;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.hijosW;
import static api.aura.service.modulo_registro_horario.documento.XmlDocx.texto;

/**
 * Dibuja el registro de jornada en PDF a partir del Word ya relleno. No convierte el .docx (eso exigiría tener Word):
 * lee su contenido (cabecera, tabla de días, firmas, fecha y texto legal) y lo dibuja con las mismas medidas, así que el
 * PDF y el Word llevan siempre los mismos datos. Es el {@code registro_pdf.py} del Registro Horario anterior en Java.
 */
public final class PdfRegistro {

    private static final double EMU_PT = 12700;
    private static final Color GRIS = new Color(0.9f, 0.9f, 0.9f); // E6E6E6, el sombreado de la plantilla
    private static final double MARGEN_CELDA = 5.4;               // 108 twips, el margen interno por defecto de las celdas
    private static final float A4_ANCHO = 595.2755905511812f, A4_ALTO = 841.8897637795277f;

    /** Un trozo de un párrafo: texto con su tamaño y negrita, o un tabulador. */
    private record Segmento(boolean tabulador, String texto, double tam, boolean negrita) {
    }

    private record Espaciado(int antes, Integer linea, int despues) {
    }

    /** Una celda de la tabla con sus combinaciones: fila, columna inicial, nº de columnas y de filas. */
    private record CeldaTabla(int fila, int col, int nCols, int nFilas, Element tc) {
    }

    private final DocumentoWord doc;
    private final Document pdf;
    private final PdfContentByte lienzo;
    private final BaseFont normal, negrita;
    private final double alto = A4_ALTO, ancho = A4_ANCHO;
    private final double mIzq, mDer, mSup;
    private Double baseFecha = null;

    private PdfRegistro(DocumentoWord doc, ByteArrayOutputStream salida) throws DocumentException, IOException {
        this.doc = doc;
        this.pdf = new Document(new Rectangle(A4_ANCHO, A4_ALTO), 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(pdf, salida);
        pdf.addTitle("Registro de jornada");
        pdf.open();
        this.lienzo = writer.getDirectContent();
        this.normal = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        this.negrita = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        Element sp = descendientesW(doc.cuerpo(), "sectPr").get(0);
        Element mar = hijoW(sp, "pgMar");
        this.mIzq = pt(attrW(mar, "left"));
        this.mDer = pt(attrW(mar, "right"));
        this.mSup = pt(attrW(mar, "top"));
    }

    /** El PDF del documento Word ya relleno. Si el contenido no cabe en una página lanza {@link DatosNoValidosException}. */
    public static byte[] generar(DocumentoWord doc) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            PdfRegistro d = new PdfRegistro(doc, salida);
            d.dibujar();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
        return salida.toByteArray();
    }

    // ---------------------------------------------------------------- utilidades

    private static double pt(String twips) {
        return Integer.parseInt(twips.strip()) / 20.0;
    }

    private static double pt(int twips) {
        return twips / 20.0;
    }

    /** Convierte «y desde arriba» a la coordenada del PDF. */
    private double Y(double y) {
        return alto - y;
    }

    private BaseFont fuente(boolean negrita) {
        return negrita ? this.negrita : normal;
    }

    private double anchoTexto(String t, double tam, boolean negrita) {
        return fuente(negrita).getWidthPoint(t, (float) tam);
    }

    private void escribir(double x, double yBase, String t, double tam, boolean negrita) {
        lienzo.beginText();
        lienzo.setFontAndSize(fuente(negrita), (float) tam);
        lienzo.setTextMatrix((float) x, (float) Y(yBase));
        lienzo.showText(t);
        lienzo.endText();
    }

    private void centrado(double xCentro, double yBase, String t, double tam, boolean negrita) {
        escribir(xCentro - anchoTexto(t, tam, negrita) / 2, yBase, t, tam, negrita);
    }

    private void rectangulo(double x, double y, double w, double h, boolean relleno) {
        lienzo.rectangle((float) x, (float) y, (float) w, (float) h);
        if (relleno) {
            lienzo.fillStroke();
        } else {
            lienzo.stroke();
        }
    }

    /** Dibuja la imagen del dibujo (en línea) con su tamaño; devuelve (ancho, alto) en pt. */
    private double[] imagen(Element dibujo, double x, double yTop) {
        Element ext = descendientes(dibujo, WP, "extent").get(0);
        double w = Long.parseLong(ext.getAttribute("cx")) / EMU_PT, h = Long.parseLong(ext.getAttribute("cy")) / EMU_PT;
        String rid = descendientes(dibujo, A, "blip").get(0).getAttributeNS(R, "embed");
        try {
            Image img = Image.getInstance(doc.imagen(rid));
            lienzo.addImage(img, (float) w, 0, 0, (float) h, (float) x, (float) Y(yTop + h));
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo dibujar la firma en el PDF", e);
        }
        return new double[]{w, h};
    }

    // ---------------------------------------------------------------- lectura del Word

    private static List<Segmento> segmentos(Element p) {
        List<Segmento> out = new ArrayList<>();
        for (Element r : descendientesW(p, "r")) {
            Element rpr = hijoW(r, "rPr");
            double sz = 11.0;
            boolean neg = false;
            if (rpr != null) {
                Element s = hijoW(rpr, "sz");
                if (s != null) {
                    sz = Integer.parseInt(attrW(s, "val").strip()) / 2.0;
                }
                neg = hijoW(rpr, "b") != null;
            }
            for (Element h : XmlDocx.hijos(r)) {
                if (XmlDocx.esW(h, "t") && !h.getTextContent().isEmpty()) {
                    out.add(new Segmento(false, h.getTextContent(), sz, neg));
                } else if (XmlDocx.esW(h, "tab")) {
                    out.add(new Segmento(true, null, 0, false));
                }
            }
        }
        return out;
    }

    private static List<Integer> tabuladores(Element p) {
        Element ppr = hijoW(p, "pPr");
        List<Integer> r = new ArrayList<>();
        if (ppr == null) {
            return r;
        }
        for (Element t : descendientesW(ppr, "tab")) {
            String pos = attrW(t, "pos");
            if (pos != null && !pos.isEmpty()) {
                r.add(Integer.parseInt(pos.strip()));
            }
        }
        return r;
    }

    private static int sangria(Element p, String lado) {
        Element ppr = hijoW(p, "pPr");
        Element ind = ppr == null ? null : hijoW(ppr, "ind");
        return ind == null ? 0 : XmlDocx.attrWInt(ind, lado, 0);
    }

    private static Espaciado espaciado(Element p) {
        Element ppr = hijoW(p, "pPr");
        Element sp = ppr == null ? null : hijoW(ppr, "spacing");
        if (sp == null) {
            return new Espaciado(0, null, 0);
        }
        String l = attrW(sp, "line");
        Integer linea = l != null && !l.isEmpty() && "exact".equals(attrW(sp, "lineRule")) ? Integer.valueOf(l.strip()) : null;
        return new Espaciado(XmlDocx.attrWInt(sp, "before", 0), linea, XmlDocx.attrWInt(sp, "after", 0));
    }

    /** Parte el texto en líneas que quepan en {@code anchoMax} (por palabras), como {@code simpleSplit} de ReportLab. */
    private List<String> partir(String t, double tam, boolean negrita, double anchoMax) {
        List<String> lineas = new ArrayList<>();
        for (String linea : t.split("\n", -1)) {
            double espacio = anchoTexto(" ", tam, negrita);
            List<String> actual = new ArrayList<>();
            double w = -espacio;
            for (String palabra : linea.strip().isEmpty() ? new String[0] : linea.strip().split("\\s+")) {
                double lp = anchoTexto(palabra, tam, negrita);
                if (w + espacio + lp <= anchoMax || actual.isEmpty()) {
                    actual.add(palabra);
                    w = w + espacio + lp;
                } else {
                    lineas.add(String.join(" ", actual));
                    actual = new ArrayList<>(List.of(palabra));
                    w = lp;
                }
            }
            if (!actual.isEmpty()) {
                lineas.add(String.join(" ", actual));
            }
        }
        return lineas;
    }

    // ---------------------------------------------------------------- párrafos

    /** Dibuja un párrafo del cuerpo (título, fecha, texto legal) y devuelve su altura en pt. */
    private double parrafo(Element p, double y) {
        String texto = texto(p).strip();
        Espaciado esp = espaciado(p);
        List<Segmento> segs = segmentos(p);
        if (texto.isEmpty()) {
            return pt(MotorWord.altoParrafo(p));
        }
        double x0 = mIzq + pt(sangria(p, "left"));
        double lPt = esp.linea() != null && esp.linea() != 0 ? pt(esp.linea()) : 12.0;
        double base = y + pt(esp.antes()) + 0.78 * lPt;
        List<Double> tabs = new ArrayList<>();
        for (int t : tabuladores(p)) {
            tabs.add(mIzq + pt(t));
        }
        if (!tabs.isEmpty()) { // la línea de la fecha: cada trozo en su tabulador
            double x = x0;
            int idx = 0;
            for (Segmento s : segs) {
                if (s.tabulador()) {
                    if (idx < tabs.size()) {
                        x = tabs.get(idx++);
                    }
                } else {
                    escribir(x, base, s.texto(), s.tam(), s.negrita());
                    x += anchoTexto(s.texto(), s.tam(), s.negrita());
                }
            }
            baseFecha = base;
            return pt(esp.antes()) + lPt + pt(esp.despues());
        }
        double tam = 0;
        boolean neg = false;
        for (Segmento s : segs) {
            if (!s.tabulador()) {
                tam = Math.max(tam, s.tam());
                neg |= s.negrita();
            }
        }
        double anchoUtil = ancho - mDer - mIzq - pt(sangria(p, "left")) - pt(sangria(p, "right"));
        List<String> lineas = partir(texto, tam, neg, anchoUtil);
        for (int i = 0; i < lineas.size(); i++) {
            escribir(x0, base + i * lPt, lineas.get(i), tam, neg);
        }
        return pt(esp.antes()) + lPt * lineas.size() + pt(esp.despues());
    }

    /** Los trazos bajo los campos de la fecha: dibujo anclado al párrafo legal en la plantilla. */
    private void lineasFecha(Element parrafoLegal) {
        List<Element> anclas = descendientes(parrafoLegal, WP, "anchor");
        if (anclas.isEmpty()) {
            return;
        }
        Element grupo = anclas.get(0);
        Element posH = hijo(grupo, WP, "positionH");
        double xGrupo = Long.parseLong(hijo(posH, WP, "posOffset").getTextContent().strip()) / EMU_PT;
        lienzo.setLineWidth(0.25f);
        for (Element wsp : descendientes(parrafoLegal, WPS, "wsp")) {
            Element xfrm = descendientes(wsp, A, "xfrm").get(0);
            double x = xGrupo + Long.parseLong(hijo(xfrm, A, "off").getAttribute("x")) / EMU_PT;
            double w = Long.parseLong(hijo(xfrm, A, "ext").getAttribute("cx")) / EMU_PT;
            double y = Y(baseFecha + 3);
            lienzo.moveTo((float) x, (float) y);
            lienzo.lineTo((float) (x + w), (float) y);
            lienzo.stroke();
        }
    }

    // ---------------------------------------------------------------- tablas

    private record Rejilla(List<Integer> columnas, List<Double> alturas) {
        double total() {
            return alturas.stream().mapToDouble(Double::doubleValue).sum();
        }
    }

    private static Rejilla rejilla(Element tbl) {
        List<Integer> cols = new ArrayList<>();
        Element grid = hijoW(tbl, "tblGrid");
        for (Element g : hijosW(grid, "gridCol")) {
            cols.add(Integer.parseInt(attrW(g, "w").strip()));
        }
        List<Double> alturas = new ArrayList<>();
        for (Element tr : hijosW(tbl, "tr")) {
            Element h = hijoW(tr, "trPr", "trHeight");
            alturas.add(h != null ? pt(attrW(h, "val")) : 12.5);
        }
        return new Rejilla(cols, alturas);
    }

    private double xTabla(Element tbl) {
        Element tp = hijoW(tbl, "tblPr", "tblpPr");
        if (tp != null && attrW(tp, "tblpX") != null && !attrW(tp, "tblpX").isEmpty()) {
            return pt(attrW(tp, "tblpX"));
        }
        Element ind = hijoW(tbl, "tblPr", "tblInd");
        return mIzq + (ind != null ? pt(attrW(ind, "w")) : 0);
    }

    /** Rectángulos de la tabla con sus combinaciones. */
    private static List<CeldaTabla> celdas(Element tbl) {
        List<Element> filas = hijosW(tbl, "tr");
        List<CeldaTabla> res = new ArrayList<>();
        for (int ri = 0; ri < filas.size(); ri++) {
            int col = 0;
            for (Element tc : hijosW(filas.get(ri), "tc")) {
                Element pr = hijoW(tc, "tcPr");
                Element span = pr == null ? null : hijoW(pr, "gridSpan");
                int n = span != null ? XmlDocx.attrWInt(span, "val", 1) : 1;
                Element vm = pr == null ? null : hijoW(pr, "vMerge");
                if (vm != null && attrW(vm, "val") == null) { // continuación de una combinación vertical
                    col += n;
                    continue;
                }
                int nFilas = 1;
                if (vm != null) { // empieza una combinación vertical: contar las filas que abarca
                    for (Element tr2 : filas.subList(ri + 1, filas.size())) {
                        int c0 = 0;
                        Element encontrada = null;
                        for (Element t2 : hijosW(tr2, "tc")) {
                            if (c0 == col) {
                                encontrada = t2;
                                break;
                            }
                            Element p2 = hijoW(t2, "tcPr");
                            Element s2 = p2 == null ? null : hijoW(p2, "gridSpan");
                            c0 += s2 != null ? XmlDocx.attrWInt(s2, "val", 1) : 1;
                        }
                        Element v2 = encontrada == null ? null : hijoW(encontrada, "tcPr", "vMerge");
                        if (v2 != null && attrW(v2, "val") == null) {
                            nFilas++;
                        } else {
                            break;
                        }
                    }
                }
                res.add(new CeldaTabla(ri, col, n, nFilas, tc));
                col += n;
            }
        }
        return res;
    }

    private double tablaCabecera(Element tbl, double y) {
        Rejilla rj = rejilla(tbl);
        double x0 = xTabla(tbl);
        List<Double> xs = new ArrayList<>(List.of(x0));
        for (int w : rj.columnas()) {
            xs.add(xs.get(xs.size() - 1) + pt(w));
        }
        lienzo.setLineWidth(0.5f);
        double yy = y;
        List<Element> filas = hijosW(tbl, "tr");
        for (int ri = 0; ri < filas.size(); ri++) {
            double h = rj.alturas().get(ri);
            List<Element> tcs = hijosW(filas.get(ri), "tc");
            for (int ci = 0; ci < tcs.size(); ci++) {
                double x = xs.get(ci);
                rectangulo(x, Y(yy + h), xs.get(ci + 1) - x, h, false);
                Element p = hijosW(tcs.get(ci), "p").get(0);
                double base = x + MARGEN_CELDA;
                double cursor = base + pt(sangria(p, "left"));
                List<Integer> tabs = tabuladores(p);
                int usados = 0;
                List<Segmento> segs = segmentos(p);
                double tamMax = 9;
                boolean hayTexto = false;
                for (Segmento s : segs) {
                    if (!s.tabulador()) {
                        tamMax = hayTexto ? Math.max(tamMax, s.tam()) : s.tam();
                        hayTexto = true;
                    }
                }
                double yBase = yy + h / 2 + 0.32 * tamMax;
                for (Segmento s : segs) {
                    if (s.tabulador()) {
                        if (usados < tabs.size()) {
                            cursor = base + pt(tabs.get(usados));
                            usados++;
                        }
                    } else if (!s.texto().strip().isEmpty()) {
                        escribir(cursor, yBase, s.texto(), s.tam(), s.negrita());
                        cursor += anchoTexto(s.texto(), s.tam(), s.negrita());
                    }
                }
            }
            yy += h;
        }
        return rj.total();
    }

    private double tablaDias(Element tbl, double y) {
        Rejilla rj = rejilla(tbl);
        double x0 = xTabla(tbl);
        List<Double> xs = new ArrayList<>(List.of(x0));
        for (int w : rj.columnas()) {
            xs.add(xs.get(xs.size() - 1) + pt(w));
        }
        List<Double> tops = new ArrayList<>(List.of(y));
        for (double h : rj.alturas()) {
            tops.add(tops.get(tops.size() - 1) + h);
        }
        lienzo.setLineWidth(0.5f);
        for (CeldaTabla c : celdas(tbl)) {
            double x = xs.get(c.col()), anchoCelda = xs.get(c.col() + c.nCols()) - xs.get(c.col());
            double top = tops.get(c.fila()), altoCelda = tops.get(c.fila() + c.nFilas()) - tops.get(c.fila());
            Element pr = hijoW(c.tc(), "tcPr");
            Element sombreado = pr == null ? null : hijoW(pr, "shd");
            boolean gris = sombreado != null && attrW(sombreado, "color") != null && !attrW(sombreado, "color").equals("auto");
            if (gris) {
                lienzo.setColorFill(GRIS);
                rectangulo(x, Y(top + altoCelda), anchoCelda, altoCelda, true);
                lienzo.setColorFill(Color.BLACK);
            } else {
                rectangulo(x, Y(top + altoCelda), anchoCelda, altoCelda, false);
            }
            List<Element> dibujos = descendientesW(c.tc(), "drawing");
            if (!dibujos.isEmpty()) { // la firma del día, centrada en su celda
                Element ext = descendientes(dibujos.get(0), WP, "extent").get(0);
                double wImg = Long.parseLong(ext.getAttribute("cx")) / EMU_PT, hImg = Long.parseLong(ext.getAttribute("cy")) / EMU_PT;
                imagen(dibujos.get(0), x + (anchoCelda - wImg) / 2, top + (altoCelda - hImg) / 2);
                continue;
            }
            List<Segmento> segs = new ArrayList<>();
            for (Segmento s : segmentos(hijosW(c.tc(), "p").get(0))) {
                if (!s.tabulador() && !s.texto().strip().isEmpty()) {
                    segs.add(s);
                }
            }
            if (segs.isEmpty()) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            boolean neg = false;
            for (Segmento s : segs) {
                sb.append(s.texto());
                neg |= s.negrita();
            }
            String t = sb.toString().strip();
            double tam = segs.get(0).tam();
            if (t.equals("TOTAL") || t.equals("HRAS.")) {
                escribir(x + MARGEN_CELDA, top + altoCelda / 2 + 0.32 * tam, t, tam, neg);
            } else {
                centrado(x + anchoCelda / 2, top + altoCelda / 2 + 0.32 * tam, t, tam, neg);
            }
        }
        return rj.total();
    }

    /** Etiquetas «Firma de la empresa:» / «Firma del trabajador:» (sin bordes) y la firma bajo la del trabajador. */
    private double bloqueFirmas(Element tbl, double y) {
        Rejilla rj = rejilla(tbl);
        double x = xTabla(tbl);
        List<Element> tcs = new ArrayList<>();
        for (Element tr : hijosW(tbl, "tr")) {
            tcs.addAll(hijosW(tr, "tc"));
        }
        for (int i = 0; i < Math.min(tcs.size(), rj.columnas().size()); i++) {
            Element tc = tcs.get(i);
            List<Element> ps = hijosW(tc, "p");
            Espaciado esp = espaciado(ps.get(0));
            double tam = 8;
            boolean hayTexto = false;
            for (Segmento s : segmentos(ps.get(0))) {
                if (!s.tabulador()) {
                    tam = hayTexto ? Math.max(tam, s.tam()) : s.tam();
                    hayTexto = true;
                }
            }
            escribir(x, y + pt(esp.antes()) + 0.78 * pt(esp.linea()), texto(ps.get(0)).strip(), tam, false);
            List<Element> dibujos = descendientesW(tc, "drawing");
            if (!dibujos.isEmpty()) {
                double yImg = y + pt(esp.antes()) + pt(esp.linea()) + pt(esp.despues()) + pt(espaciado(ps.get(1)).antes());
                imagen(dibujos.get(0), x, yImg);
            }
            x += pt(rj.columnas().get(i));
        }
        return rj.total();
    }

    // ---------------------------------------------------------------- documento

    private void dibujar() {
        List<Element> cuerpo = new ArrayList<>();
        for (Element el : XmlDocx.hijos(doc.cuerpo())) {
            if (XmlDocx.esW(el, "p") || XmlDocx.esW(el, "tbl")) {
                cuerpo.add(el);
            }
        }
        Element legal = null;
        for (Element el : cuerpo) {
            if (XmlDocx.esW(el, "p") && texto(el).startsWith("Registro realizado")) {
                legal = el;
                break;
            }
        }
        double y = mSup;
        for (Element el : cuerpo) {
            if (XmlDocx.esW(el, "p")) {
                y += parrafo(el, y);
                if (legal != null && el != legal && texto(el).startsWith("En") && baseFecha != null && baseFecha != 0) {
                    lineasFecha(legal); // los trazos bajo la fecha, justo después de escribirla
                }
            } else {
                String primera = texto(hijosW(el, "tr").get(0));
                if (primera.startsWith("Empresa")) {
                    y += tablaCabecera(el, y);
                } else if (primera.startsWith("DIA")) {
                    y += tablaDias(el, y);
                } else if (primera.startsWith("Firma de la empresa")) {
                    y += bloqueFirmas(el, y);
                } else {
                    y += rejilla(el).total();
                }
            }
        }
        double abajo = pt(attrW(hijoW(descendientesW(doc.cuerpo(), "sectPr").get(0), "pgMar"), "bottom"));
        if (y > alto - abajo + 0.5) {
            pdf.close();
            throw new DatosNoValidosException("el contenido no cabe en una sola página");
        }
        pdf.close();
    }
}
