package api.aura.service.modulo_planificador;

import lombok.RequiredArgsConstructor;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * PowerPoint de la propuesta al cliente de un presupuesto validado (A4 apaisado, con la imagen de Aura; equivale a proposal_pptx.py).
 *
 * <p>Reproduce las páginas variables del modelo (portada, propuesta técnica, equipos, estructura, garantías y propuesta económica) con los
 * datos del presupuesto. Los fondos son las imágenes del modelo. El resultado es un .pptx editable: se puede retocar a mano antes de mandarlo.
 */
@Component
@RequiredArgsConstructor
public class PropuestaPptx {

    private static final Color AMARILLO = new Color(0xFF, 0xDD, 0x00);
    private static final Color GRIS = new Color(0x9D, 0x9E, 0xA1);
    private static final Color ETIQUETA = new Color(0x8A, 0x8B, 0x8E);
    private static final Color TINTA = new Color(0x1F, 0x1F, 0x21);
    private static final Color BLANCO = Color.WHITE;
    private static final String FUENTE = "Arial";
    private static final double ANCHO = 11.693;
    private static final double ALTO = 8.268;

    private static final List<String[]> ESTRUCTURAS = List.of(
            new String[]{"coplanar", "Coplanar a cubierta"}, new String[]{"inclinada", "Estructura inclinada"}, new String[]{"lastrada", "Lastrada sin perforación"},
            new String[]{"hincada", "Postes hincados en terreno"}, new String[]{"marquesina", "Marquesina"});
    private static final List<String[]> GARANTIAS = List.of(
            new String[]{"30", "años", "Garantía de producción", "12 años de garantía al producto. 30 años de garantía de producción al 87,4 % de la potencia inicial."},
            new String[]{"5", "años", "Garantía del producto", "5 años de garantía del producto sobre el inversor solar, cubriendo defectos de fabricación y fallos técnicos del equipo."},
            new String[]{"2", "años", "Garantía de la instalación", "Aura se compromete con el cliente dando una garantía de 2 años a la instalación, que puede verse ampliada por la firma de un contrato de mantenimiento."},
            new String[]{"10", "años", "Garantía de la batería", "10 años de garantía del sistema de almacenamiento con 8.000 ciclos garantizados de carga y descarga."});
    private static final List<String[]> PAGOS = List.of(
            new String[]{"A la firma del contrato", "Reserva del material"}, new String[]{"A la recepción del material", "Inicio de obra"},
            new String[]{"A la colocación de los paneles", "Avance de obra"}, new String[]{"A la puesta en marcha", "Al acabar la instalación"});

    private final PropuestaHtml activos;

    private static String num(double valor, int cifras) {
        String t = String.format(Locale.US, "%,." + cifras + "f", valor);
        return t.replace(",", "X").replace(".", ",").replace("X", ".");
    }

    private static String eur(double valor) {
        return num(valor, 2) + " €";
    }

    private static double pt(double pulgadas) {
        return pulgadas * 72.0;
    }

    private static Rectangle2D caja(double x, double y, double w, double h) {
        return new Rectangle2D.Double(pt(x), pt(y), pt(w), pt(h));
    }

    private static XSLFTextBox texto(XSLFSlide diapositiva, double x, double y, double w, double h, String texto, double tamano, boolean negrita, Color color,
                                     TextParagraph.TextAlign alineacion, VerticalAlignment ancla) {
        XSLFTextBox caja = diapositiva.createTextBox();
        caja.setAnchor(caja(x, y, w, h));
        caja.setWordWrap(true);
        caja.setVerticalAlignment(ancla);
        caja.setLeftInset(0);
        caja.setRightInset(0);
        caja.setTopInset(0);
        caja.setBottomInset(0);
        caja.clearText();
        for (String linea : texto.split("\n", -1)) {
            XSLFTextParagraph p = caja.addNewTextParagraph();
            p.setTextAlign(alineacion);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(linea);
            r.setFontFamily(FUENTE);
            r.setFontSize(tamano);
            r.setBold(negrita);
            r.setFontColor(color);
        }
        return caja;
    }

    private static XSLFTextBox texto(XSLFSlide d, double x, double y, double w, double h, String t, double tamano, boolean negrita, Color color) {
        return texto(d, x, y, w, h, t, tamano, negrita, color, TextParagraph.TextAlign.LEFT, VerticalAlignment.TOP);
    }

    private static XSLFAutoShape forma(XSLFSlide d, ShapeType tipo, double x, double y, double w, double h, Color relleno, Color linea) {
        XSLFAutoShape s = d.createAutoShape();
        s.setShapeType(tipo);
        s.setAnchor(caja(x, y, w, h));
        s.setFillColor(relleno); // null = sin relleno
        if (linea == null) {
            s.setLineColor(null);
        } else {
            s.setLineColor(linea);
            s.setLineWidth(1.5);
        }
        return s;
    }

    /** «Propuesta» en gris y «Técnica» sobre una barra amarilla, como en el modelo. */
    private static void titulo(XSLFSlide d, String gris, String resaltado, String subtitulo, double x, double y) {
        double tamano = 36;
        double anchoGris = 0.255 * gris.length();
        texto(d, x, y, anchoGris + 0.2, 0.7, gris, tamano, true, GRIS, TextParagraph.TextAlign.LEFT, VerticalAlignment.MIDDLE);
        XSLFAutoShape barra = forma(d, ShapeType.RECT, x + anchoGris + 0.1, y + 0.02, 0.27 * resaltado.length() + 0.3, 0.66, AMARILLO, null);
        barra.setLeftInset(pt(0.1));
        barra.setRightInset(pt(0.1));
        barra.setVerticalAlignment(VerticalAlignment.MIDDLE);
        barra.clearText();
        XSLFTextParagraph p = barra.addNewTextParagraph();
        XSLFTextRun r = p.addNewTextRun();
        r.setText(resaltado);
        r.setFontFamily(FUENTE);
        r.setFontSize(tamano);
        r.setBold(true);
        r.setFontColor(BLANCO);
        if (!subtitulo.isEmpty()) {
            texto(d, x, y + 0.85, 8, 0.4, subtitulo, 16, false, ETIQUETA);
        }
    }

    private XSLFSlide enBlanco(XMLSlideShow ppt, String fondo) {
        XSLFSlide d = ppt.createSlide();
        byte[] bytes = activos.imagen(fondo);
        if (bytes == null) throw new IllegalStateException("Falta la imagen de la propuesta: " + fondo);
        PictureData.PictureType tipo = fondo.endsWith(".png") ? PictureData.PictureType.PNG : PictureData.PictureType.JPEG;
        XSLFPictureShape imagen = d.createPicture(ppt.addPicture(bytes, tipo));
        imagen.setAnchor(caja(0, 0, ANCHO, ALTO));
        return d;
    }

    private static double d(Object v) {
        return v instanceof Number n ? n.doubleValue() : v == null || "".equals(v) ? 0.0 : Double.parseDouble(String.valueOf(v));
    }

    private static String t(Object v, String defecto) {
        return v == null || "".equals(v) ? defecto : String.valueOf(v);
    }

    /**
     * `datos`: client, address, kind, kwp, panels, panel, inverter, inverter_count, battery, structure (clave de las estructuras), total (€ sin IVA)
     * y payment (porcentajes de los cuatro pagos, por defecto 30/40/20/10).
     */
    public byte[] generar(Map<String, Object> datos) {
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ppt.setPageSize(new Dimension((int) Math.round(pt(ANCHO)), (int) Math.round(pt(ALTO))));

            // 1 · Portada: la imagen del modelo con el nombre y la dirección del cliente en la línea amarilla
            XSLFSlide s = enBlanco(ppt, "portada.jpg");
            texto(s, 0.7, 5.5, 5.4, 0.35, "Nombre de la empresa: " + t(datos.get("client"), ""), 14, true, ETIQUETA);
            if (!t(datos.get("address"), "").isEmpty()) {
                texto(s, 0.7, 5.9, 5.4, 0.6, "Dirección: " + datos.get("address"), 14, false, ETIQUETA);
            }

            // 2 · Propuesta técnica
            s = enBlanco(ppt, "fondo.png");
            titulo(s, "Propuesta", "Técnica", "Especificaciones técnicas de tu proyecto", 0.65, 1.0);
            String[][] filas = {
                    {"Tipo de instalación:", t(datos.get("kind"), "Autoconsumo")},
                    {"Potencia pico total:", num(d(datos.get("kwp")), 2) + " kWp"},
                    {"Nº de módulos:", String.valueOf((int) d(datos.get("panels")))},
                    {"Baterías:", t(datos.get("battery"), "No")},
                    {"Inversores:", String.valueOf(datos.get("inverter_count") == null ? 1 : (int) d(datos.get("inverter_count")))}};
            for (int i = 0; i < filas.length; i++) {
                texto(s, 0.7, 3.2 + i * 0.72, 3.6, 0.4, filas[i][0], 18, false, ETIQUETA);
                texto(s, 4.4, 3.2 + i * 0.72, 6.0, 0.4, filas[i][1], 18, true, TINTA);
            }

            // 3 · Equipos
            s = enBlanco(ppt, "fondo.png");
            titulo(s, "Equipos", "seleccionados", "Los principales componentes de tu instalación", 0.65, 1.0);
            int inversores = datos.get("inverter_count") == null ? 1 : (int) d(datos.get("inverter_count"));
            String[][] tarjetas = {
                    {"Módulos fotovoltaicos", t(datos.get("panel"), "—") + "\n" + (int) d(datos.get("panels")) + " unidades"},
                    {"Inversor", t(datos.get("inverter"), "—") + "\n" + inversores + " unidad" + (inversores != 1 ? "es" : "")},
                    {"Baterías", t(datos.get("battery"), "No incluidas")}};
            for (int i = 0; i < tarjetas.length; i++) {
                double y = 3.0 + i * 1.5;
                forma(s, ShapeType.ROUND_RECT, 0.7, y, 8.6, 1.2, null, GRIS);
                forma(s, ShapeType.ROUND_RECT, 0.7, y, 0.18, 1.2, AMARILLO, null);
                texto(s, 1.2, y + 0.15, 7.8, 0.4, tarjetas[i][0], 16, false, ETIQUETA);
                texto(s, 1.2, y + 0.55, 7.8, 0.6, tarjetas[i][1], 18, true, TINTA);
            }

            // 4 · Estructura
            s = enBlanco(ppt, "fondo.png");
            titulo(s, "Tipo de", "estructura", "La solución prevista para tus paneles", 0.65, 1.0);
            // Una estructura por cubierta si el presupuesto tiene varias («structures»); si no, la única («structure»)
            Set<String> elegidas = new HashSet<>();
            if (datos.get("structures") instanceof List<?> varias) varias.forEach(e -> elegidas.add(String.valueOf(e)));
            else elegidas.add(t(datos.get("structure"), ""));
            for (int i = 0; i < ESTRUCTURAS.size(); i++) {
                double x = 0.7 + i * 2.15;
                boolean on = elegidas.contains(ESTRUCTURAS.get(i)[0]);
                forma(s, ShapeType.ROUND_RECT, x, 3.2, 1.95, 2.4, on ? AMARILLO : null, on ? AMARILLO : GRIS);
                texto(s, x + 0.15, 3.35, 1.65, 1.6, ESTRUCTURAS.get(i)[1], 15, true, on ? TINTA : ETIQUETA, TextParagraph.TextAlign.CENTER, VerticalAlignment.MIDDLE);
                if (on) {
                    texto(s, x + 0.15, 5.0, 1.65, 0.4, "✓ Incluida", 13, true, TINTA, TextParagraph.TextAlign.CENTER, VerticalAlignment.TOP);
                }
            }

            // 5 · Garantías
            s = enBlanco(ppt, "fondo.png");
            titulo(s, "Nuestras", "garantías", "Tu inversión, protegida", 0.65, 1.0);
            for (int i = 0; i < GARANTIAS.size(); i++) {
                double x = 0.7 + i * 2.65;
                String[] g = GARANTIAS.get(i);
                texto(s, x, 3.0, 2.4, 1.0, g[0], 54, true, AMARILLO);
                texto(s, x, 4.05, 2.4, 0.4, g[1], 16, true, GRIS);
                texto(s, x, 4.6, 2.4, 0.5, g[2], 15, true, TINTA);
                texto(s, x, 5.15, 2.4, 2.2, g[3], 12, false, ETIQUETA);
            }

            // 6 · Propuesta económica
            s = enBlanco(ppt, "fondo.png");
            texto(s, 0.7, 0.7, 6, 0.4, "Condiciones económicas", 16, false, ETIQUETA);
            titulo(s, "Propuesta", "Económica", "", 0.65, 1.1);
            double total = d(datos.get("total"));
            texto(s, 6.3, 2.3, 4.7, 0.5, "Importe total", 24, true, GRIS, TextParagraph.TextAlign.RIGHT, VerticalAlignment.TOP);
            texto(s, 6.0, 2.85, 5.0, 0.9, eur(total), 44, true, TINTA, TextParagraph.TextAlign.RIGHT, VerticalAlignment.TOP);
            forma(s, ShapeType.RECT, 6.9, 3.85, 4.1, 0.03, AMARILLO, null);
            texto(s, 6.3, 3.95, 4.7, 0.4, "IVA no incluido", 14, false, ETIQUETA, TextParagraph.TextAlign.RIGHT, VerticalAlignment.TOP);
            texto(s, 0.7, 4.55, 4, 0.4, "Forma de pago", 16, false, ETIQUETA);
            List<?> porcentajes = datos.get("payment") instanceof List<?> l && !l.isEmpty() ? l : List.of(30, 40, 20, 10);
            for (int i = 0; i < Math.min(PAGOS.size(), porcentajes.size()); i++) {
                double x = 0.7 + (i % 2) * 5.4;
                double y = 5.1 + (i / 2) * 1.45;
                boolean on = i % 2 == 0;
                double pct = d(porcentajes.get(i));
                String pctTexto = pct == Math.rint(pct) ? String.valueOf((long) pct) : String.valueOf(pct);
                forma(s, ShapeType.ROUND_RECT, x, y, 0.95, 0.95, on ? AMARILLO : GRIS, null);
                texto(s, x, y, 0.95, 0.95, pctTexto + " %", 18, true, on ? TINTA : BLANCO, TextParagraph.TextAlign.CENTER, VerticalAlignment.MIDDLE);
                texto(s, x + 1.15, y + 0.1, 4.0, 0.4, PAGOS.get(i)[0], 16, true, TINTA);
                texto(s, x + 1.15, y + 0.5, 4.0, 0.4, PAGOS.get(i)[1], 14, false, ETIQUETA);
                if (total != 0) {
                    texto(s, x + 1.15, y + 0.78, 4.0, 0.3, eur(total * pct / 100), 11, false, GRIS);
                }
            }

            ppt.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el PowerPoint: " + e.getMessage());
        }
    }
}
