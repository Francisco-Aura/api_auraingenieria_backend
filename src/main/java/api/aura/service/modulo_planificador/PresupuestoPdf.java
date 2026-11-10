package api.aura.service.modulo_planificador;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import java.util.List;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * PDF del presupuesto del presupuestador, en dos versiones (equivale a budget_pdf.py):
 * <ul>
 * <li>{@code interno}: el presupuesto de costes tal como se ve en pantalla (filas por partida con cantidad, precio y total, el coste por Wp,
 * el precio de venta y el beneficio). Es para uso de la empresa.</li>
 * <li>{@code cliente}: solo lo que se enseña a quien compra: la instalación y su precio de venta, sin costes ni beneficio ni partidas
 * administrativas.</li>
 * </ul>
 * Los importes se recalculan aquí a partir de las filas (el navegador solo manda lo que hay en pantalla).
 */
@Component
public class PresupuestoPdf {

    private static final float MM = 72f / 25.4f;
    private static final Color TINTA = Color.decode("#1f2937");
    private static final Color APAGADO = Color.decode("#6b7280");
    private static final Color LINEA = Color.decode("#d1d5db");
    private static final Color CABECERA = Color.decode("#f3f4f6");
    private static final Color ACENTO = Color.decode("#fcd34d");
    /** Lo que no se enseña a un cliente: los costes de la empresa que no son equipos ni instalación. */
    private static final Set<String> GRUPOS_OCULTOS_AL_CLIENTE = Set.of("administrativos");
    private static final Set<String> EQUIPOS_OCULTOS_AL_CLIENTE = Set.of("Mano de obra", "Logística");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // --------------------------------------------------------------------------------- formato

    /** Un número de lo que mande el navegador; lo que no lo sea cuenta como 0. */
    public static double f(Object valor) {
        double n;
        try {
            if (valor == null || Boolean.FALSE.equals(valor) || "".equals(valor)) return 0.0;
            n = valor instanceof Number x ? x.doubleValue() : Double.parseDouble(String.valueOf(valor).strip());
        } catch (NumberFormatException e) {
            return 0.0;
        }
        return Numeros.finito(n) ? n : 0.0;
    }

    private static String decimales(double n, int cifras) {
        return new BigDecimal(n).setScale(cifras, RoundingMode.HALF_EVEN).toPlainString();
    }

    private static String agrupar(String plano) {
        boolean negativo = plano.startsWith("-");
        String sin = negativo ? plano.substring(1) : plano;
        int punto = sin.indexOf('.');
        String entero = punto < 0 ? sin : sin.substring(0, punto);
        String resto = punto < 0 ? "" : sin.substring(punto);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entero.length(); i++) {
            if (i > 0 && (entero.length() - i) % 3 == 0) sb.append('.');
            sb.append(entero.charAt(i));
        }
        return (negativo ? "-" : "") + sb + resto.replace('.', ',');
    }

    static String eur(double n) {
        return agrupar(decimales(n, 2)) + " €";
    }

    static String num(double n) {
        String t = decimales(n, 2);
        t = t.replaceAll("0+$", "").replaceAll("\\.$", "");
        return agrupar(t);
    }

    private static String ratio(double n) {
        return decimales(n, 3).replace('.', ',');
    }

    private static String uno(double n) {
        return decimales(n, 1).replace('.', ',');
    }

    // ------------------------------------------------------------------------------- strings

    private record Cadena(int paneles, int entrada) {
    }

    /** Los strings que mande el navegador como (paneles, entrada); se descartan los que no tengan paneles. */
    private static List<Cadena> cadenas(Object crudo) {
        List<Cadena> salida = new ArrayList<>();
        if (crudo instanceof List<?> lista) {
            for (Object o : lista) {
                if (o instanceof Map<?, ?> m) {
                    int paneles = (int) f(m.get("panels"));
                    if (paneles >= 1) salida.add(new Cadena(paneles, Math.max(0, (int) f(m.get("input")))));
                }
            }
        }
        return salida;
    }

    /** «1 string de 12 paneles», «2 strings de 12 paneles» o «2 strings: 20 + 5 paneles». */
    static String resumenDeCadenas(List<Cadena> cadenas) {
        List<Integer> tamanos = cadenas.stream().map(Cadena::paneles).toList();
        if (tamanos.size() == 1) return "1 string de " + tamanos.get(0) + " paneles";
        if (new HashSet<>(tamanos).size() == 1) return tamanos.size() + " strings de " + tamanos.get(0) + " paneles";
        StringJoiner sj = new StringJoiner(" + ");
        tamanos.forEach(n -> sj.add(String.valueOf(n)));
        return tamanos.size() + " strings: " + sj + " paneles";
    }

    /** Qué va a cada entrada del inversor: «E1 20 · E2 5» (con varios strings en una entrada, «E1 12 + 12»). */
    static String entradasDeCadenas(List<Cadena> cadenas) {
        Map<Integer, List<Integer>> porEntrada = new TreeMap<>((a, b) -> a == 0 ? (b == 0 ? 0 : 1) : b == 0 ? -1 : Integer.compare(a, b));
        for (Cadena c : cadenas) porEntrada.computeIfAbsent(c.entrada(), k -> new ArrayList<>()).add(c.paneles());
        StringJoiner partes = new StringJoiner(" · ");
        porEntrada.forEach((entrada, tamanos) -> {
            StringJoiner sj = new StringJoiner(" + ");
            tamanos.forEach(n -> sj.add(String.valueOf(n)));
            partes.add((entrada == 0 ? "Sin asignar" : "E" + entrada) + " " + sj);
        });
        return partes.toString();
    }

    // ------------------------------------------------------------------------------ dibujo

    private static BaseFont base(boolean negrita) {
        try {
            return BaseFont.createFont(negrita ? BaseFont.HELVETICA_BOLD : BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Font fuente(float tamano, boolean negrita, Color color) {
        return new Font(base(negrita), tamano, Font.NORMAL, color);
    }

    private static Paragraph texto(String t, float tamano, float interlineado, boolean negrita, Color color, int alineacion) {
        Paragraph p = new Paragraph(interlineado, t, fuente(tamano, negrita, color));
        p.setAlignment(alineacion);
        return p;
    }

    private static Paragraph celda(String t, boolean derecha) {
        return texto(t, 8.5f, 10.5f, false, TINTA, derecha ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
    }

    private static Paragraph cabecera(String t, boolean derecha) {
        return texto(t, 8.5f, 10.5f, true, TINTA, derecha ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
    }

    private static PdfPCell casilla(Paragraph contenido, int vertical, float relleno, Color fondo, float lineaAbajo, Color colorLineaAbajo, float lineaArriba, Color colorLineaArriba) {
        PdfPCell c = new PdfPCell();
        if (contenido != null) c.addElement(contenido); else c.setPhrase(new Phrase(""));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(relleno);
        c.setPaddingBottom(relleno);
        c.setPaddingLeft(6);
        c.setPaddingRight(6);
        c.setVerticalAlignment(vertical);
        c.setUseAscender(true);
        c.setUseDescender(true);
        if (fondo != null) c.setBackgroundColor(fondo);
        int borde = Rectangle.NO_BORDER;
        if (lineaAbajo > 0) {
            borde |= Rectangle.BOTTOM;
            c.setBorderWidthBottom(lineaAbajo);
            c.setBorderColorBottom(colorLineaAbajo);
        }
        if (lineaArriba > 0) {
            borde |= Rectangle.TOP;
            c.setBorderWidthTop(lineaArriba);
            c.setBorderColorTop(colorLineaArriba);
        }
        c.setBorder(borde);
        return c;
    }

    private static PdfPTable tabla(float[] anchosMm, int alineacion) {
        float[] anchos = new float[anchosMm.length];
        float total = 0;
        for (int i = 0; i < anchos.length; i++) {
            anchos[i] = anchosMm[i] * MM;
            total += anchos[i];
        }
        PdfPTable t = new PdfPTable(anchos);
        t.setTotalWidth(total);
        t.setLockedWidth(true);
        t.setHorizontalAlignment(alineacion);
        return t;
    }

    /** Estilo de pie de página: «Presupuesto · fecha» y «Página N». */
    private static class Pie extends PdfPageEventHelper {
        private final LocalDate hoy;

        Pie(LocalDate hoy) {
            this.hoy = hoy;
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            PdfContentByte cb = escritor.getDirectContent();
            cb.saveState();
            cb.beginText();
            cb.setFontAndSize(base(false), 8);
            cb.setColorFill(APAGADO);
            cb.showTextAligned(Element.ALIGN_LEFT, "Presupuesto · " + hoy.format(DIA), 20 * MM, 10 * MM, 0);
            cb.showTextAligned(Element.ALIGN_RIGHT, "Página " + documento.getPageNumber(), PageSize.A4.getWidth() - 20 * MM, 10 * MM, 0);
            cb.endText();
            cb.restoreState();
        }
    }

    private record Linea(String grupo, String equipo, String modelo, String unidad, double cantidad, double precio) {
    }

    /** `datos`: client, moduleWp, moduleCount, saleRatio, strings y lines (group, equipment, model, unit, qty, price). */
    public byte[] generar(Map<String, Object> datos, String modo, LocalDate hoy) {
        boolean interno = "interno".equals(modo);
        List<Linea> lineas = new ArrayList<>();
        if (datos.get("lines") instanceof List<?> lista) {
            for (Object o : lista) {
                if (!(o instanceof Map<?, ?> m)) continue;
                lineas.add(new Linea(cadena(m.get("group"), ""), cadena(m.get("equipment"), ""), cadena(m.get("model"), ""), cadena(m.get("unit"), "ud"), f(m.get("qty")), f(m.get("price"))));
            }
        }
        double wp = f(datos.get("moduleWp")) * f(datos.get("moduleCount"));
        double venta = f(datos.get("saleRatio"));
        double coste = lineas.stream().mapToDouble(l -> l.cantidad() * l.precio()).sum();
        double presupuesto = venta * wp;
        double beneficio = presupuesto - coste;
        String cliente = datos.get("client") == null ? "" : String.valueOf(datos.get("client")).strip();

        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document documento = new Document(PageSize.A4, 20 * MM, 20 * MM, 18 * MM, 18 * MM);
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            escritor.setPageEvent(new Pie(hoy));
            documento.addTitle("Presupuesto");
            documento.open();

            documento.add(texto("Presupuesto" + (interno ? " de costes" : ""), 20, 24, true, TINTA, Element.ALIGN_LEFT));
            documento.add(texto((cliente.isEmpty() ? "Sin cliente indicado" : cliente) + " · " + hoy.format(DIA) + (interno ? " · Documento interno" : ""),
                    10, 12, false, APAGADO, Element.ALIGN_LEFT));
            Paragraph espacio = new Paragraph(" ", fuente(1, false, TINTA));
            espacio.setLeading(8);
            documento.add(espacio);

            // Datos generales de la instalación
            PdfPTable datosTabla = tabla(new float[]{50, 70}, Element.ALIGN_LEFT);
            filaDato(datosTabla, "Potencia instalada", "<b>" + num(wp / 1000) + " kWp</b>");
            filaDato(datosTabla, "Paneles", num(f(datos.get("moduleCount"))) + " × " + num(f(datos.get("moduleWp"))) + " Wp");
            List<Cadena> cadenas = cadenas(datos.get("strings"));
            if (!cadenas.isEmpty()) {
                filaDato(datosTabla, "Strings", resumenDeCadenas(cadenas));
                if (interno) filaDato(datosTabla, "Distribución", entradasDeCadenas(cadenas));
            }
            documento.add(datosTabla);

            if (interno) {
                for (CatalogoEquipos.Partida partida : CatalogoEquipos.PARTIDAS) {
                    List<Linea> delGrupo = lineas.stream().filter(l -> l.grupo().equals(partida.key())).toList();
                    if (delGrupo.isEmpty()) continue;
                    double subtotal = delGrupo.stream().mapToDouble(l -> l.cantidad() * l.precio()).sum();
                    Paragraph titulo = texto(partida.label(), 11.5f, 15, true, TINTA, Element.ALIGN_LEFT);
                    titulo.setSpacingBefore(10);
                    titulo.setSpacingAfter(4);
                    documento.add(titulo);
                    PdfPTable t = tabla(new float[]{88, 20, 30, 32}, Element.ALIGN_LEFT);
                    t.setHeaderRows(1);
                    String[] cabeceras = {"Concepto", "Cant.", "Precio ud.", "Total"};
                    for (int i = 0; i < cabeceras.length; i++) {
                        t.addCell(casilla(cabecera(cabeceras[i], i > 0), Element.ALIGN_MIDDLE, 3, CABECERA, 0.4f, LINEA, 0, null));
                    }
                    for (Linea l : delGrupo) {
                        String nombre = !l.modelo().isEmpty() ? l.modelo() : !l.equipo().isEmpty() ? l.equipo() : "Fila sin nombre";
                        Paragraph concepto = new Paragraph(10.5f);
                        concepto.add(new Chunk(nombre + " ", fuente(8.5f, false, TINTA)));
                        concepto.add(new Chunk("· " + l.unidad(), fuente(8.5f, false, APAGADO)));
                        t.addCell(casilla(concepto, Element.ALIGN_MIDDLE, 3, null, 0.4f, LINEA, 0, null));
                        t.addCell(casilla(celda(num(l.cantidad()), true), Element.ALIGN_MIDDLE, 3, null, 0.4f, LINEA, 0, null));
                        t.addCell(casilla(celda(eur(l.precio()), true), Element.ALIGN_MIDDLE, 3, null, 0.4f, LINEA, 0, null));
                        t.addCell(casilla(celda(eur(l.cantidad() * l.precio()), true), Element.ALIGN_MIDDLE, 3, null, 0.4f, LINEA, 0, null));
                    }
                    t.addCell(casilla(null, Element.ALIGN_MIDDLE, 3, null, 0, null, 0.8f, TINTA));
                    t.addCell(casilla(null, Element.ALIGN_MIDDLE, 3, null, 0, null, 0.8f, TINTA));
                    t.addCell(casilla(cabecera("Subtotal", true), Element.ALIGN_MIDDLE, 3, null, 0, null, 0.8f, TINTA));
                    t.addCell(casilla(cabecera(eur(subtotal), true), Element.ALIGN_MIDDLE, 3, null, 0, null, 0.8f, TINTA));
                    documento.add(t);
                }
                String[][] totales = {
                        {"Coste total", eur(coste)},
                        {"Coste ratio", ratio(wp != 0 ? coste / wp : 0) + " €/Wp"},
                        {"Presupuesto de venta (" + num(venta) + " €/Wp × " + num(wp) + " Wp)", eur(presupuesto)},
                        {"Beneficio", eur(beneficio) + " (" + uno(presupuesto != 0 ? beneficio / presupuesto * 100 : 0) + " %)"}};
                PdfPTable t = tabla(new float[]{110, 60}, Element.ALIGN_RIGHT);
                for (int i = 0; i < totales.length; i++) {
                    Color fondo = i == 2 || i == 3 ? CABECERA : null;
                    t.addCell(casilla(celda(totales[i][0], false), Element.ALIGN_TOP, 4, fondo, 0.4f, LINEA, 0, null));
                    Paragraph valor = texto(totales[i][1], 8.5f, 10.5f, true, TINTA, Element.ALIGN_RIGHT);
                    t.addCell(casilla(valor, Element.ALIGN_TOP, 4, fondo, 0.4f, LINEA, 0, null));
                }
                t.setKeepTogether(true);
                espacio = new Paragraph(" ", fuente(1, false, TINTA));
                espacio.setLeading(14);
                documento.add(espacio);
                documento.add(t);
            } else {
                Map<String, List<Linea>> porEquipo = new LinkedHashMap<>();
                for (Linea l : lineas) {
                    if (GRUPOS_OCULTOS_AL_CLIENTE.contains(l.grupo()) || EQUIPOS_OCULTOS_AL_CLIENTE.contains(l.equipo())) continue;
                    porEquipo.computeIfAbsent(l.equipo().isEmpty() ? "Otros" : l.equipo(), k -> new ArrayList<>()).add(l);
                }
                Paragraph titulo = texto("Instalación", 11.5f, 15, true, TINTA, Element.ALIGN_LEFT);
                titulo.setSpacingBefore(10);
                titulo.setSpacingAfter(4);
                documento.add(titulo);
                if (!porEquipo.isEmpty()) {
                    PdfPTable t = tabla(new float[]{45, 95, 30}, Element.ALIGN_LEFT);
                    t.setHeaderRows(1);
                    t.addCell(casilla(cabecera("Equipo", false), Element.ALIGN_TOP, 3, CABECERA, 0.4f, LINEA, 0, null));
                    t.addCell(casilla(cabecera("Descripción", false), Element.ALIGN_TOP, 3, CABECERA, 0.4f, LINEA, 0, null));
                    t.addCell(casilla(cabecera("Cantidad", true), Element.ALIGN_TOP, 3, CABECERA, 0.4f, LINEA, 0, null));
                    for (Map.Entry<String, List<Linea>> e : porEquipo.entrySet()) {
                        for (int i = 0; i < e.getValue().size(); i++) {
                            Linea l = e.getValue().get(i);
                            String unidad = l.unidad().equals("módulo") && l.cantidad() != 1 ? "módulos" : l.unidad();
                            t.addCell(casilla(celda(i == 0 ? e.getKey() : "", false), Element.ALIGN_TOP, 3, null, 0.4f, LINEA, 0, null));
                            t.addCell(casilla(celda(l.modelo().isEmpty() ? "—" : l.modelo(), false), Element.ALIGN_TOP, 3, null, 0.4f, LINEA, 0, null));
                            t.addCell(casilla(celda(num(l.cantidad()) + " " + unidad, true), Element.ALIGN_TOP, 3, null, 0.4f, LINEA, 0, null));
                        }
                    }
                    documento.add(t);
                } else {
                    documento.add(texto("Sin equipos añadidos.", 8, 10, false, APAGADO, Element.ALIGN_LEFT));
                }
                espacio = new Paragraph(" ", fuente(1, false, TINTA));
                espacio.setLeading(6);
                documento.add(espacio);
                documento.add(texto("Incluye mano de obra, montaje, puesta en marcha y trámites necesarios.", 8, 10, false, APAGADO, Element.ALIGN_LEFT));
                espacio = new Paragraph(" ", fuente(1, false, TINTA));
                espacio.setLeading(16);
                documento.add(espacio);
                PdfPTable caja = tabla(new float[]{90, 80}, Element.ALIGN_RIGHT);
                Color amarillo = Color.decode("#fef3c7");
                PdfPCell izquierda = casilla(cabecera("Precio de la instalación", false), Element.ALIGN_MIDDLE, 9, amarillo, 0, null, 0, null);
                izquierda.setBorder(Rectangle.LEFT);
                izquierda.setBorderWidthLeft(4);
                izquierda.setBorderColorLeft(ACENTO);
                caja.addCell(izquierda);
                caja.addCell(casilla(texto(eur(presupuesto), 13, 16, true, TINTA, Element.ALIGN_RIGHT), Element.ALIGN_MIDDLE, 9, amarillo, 0, null, 0, null));
                caja.setKeepTogether(true);
                documento.add(caja);
                espacio = new Paragraph(" ", fuente(1, false, TINTA));
                espacio.setLeading(6);
                documento.add(espacio);
                documento.add(texto("Precio sin IVA.", 8, 10, false, APAGADO, Element.ALIGN_LEFT));
            }
            documento.close();
            return salida.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage());
        }
    }

    private static String cadena(Object v, String defecto) {
        return v == null ? defecto : String.valueOf(v);
    }

    /** Una fila «concepto / valor» de los datos generales; `<b>…</b>` pone el valor en negrita. */
    private static void filaDato(PdfPTable t, String concepto, String valor) {
        boolean negrita = valor.startsWith("<b>");
        String limpio = negrita ? valor.substring(3, valor.length() - 4) : valor;
        t.addCell(casilla(celda(concepto, false), Element.ALIGN_TOP, 3, null, 0.4f, LINEA, 0, null));
        t.addCell(casilla(negrita ? texto(limpio, 8.5f, 10.5f, true, TINTA, Element.ALIGN_RIGHT) : celda(limpio, true), Element.ALIGN_TOP, 3, null, 0.4f, LINEA, 0, null));
    }

    /** «Presupuesto_cliente_BONAQUIA_SL_261105»: el nombre del archivo, sin extensión. */
    public static String nombreBase(String cliente, String modo, LocalDate hoy) {
        String nombre = Pattern.compile("[^\\p{L}\\p{N}_\\- ]+").matcher(Normalizer.normalize(cliente == null ? "" : cliente, Normalizer.Form.NFC)).replaceAll("").strip().replace(" ", "_");
        if (nombre.isEmpty()) nombre = "sin_cliente";
        return "Presupuesto_" + ("interno".equals(modo) ? "interno" : "cliente") + "_" + nombre + "_" + hoy.format(DateTimeFormatter.ofPattern("yyMMdd"));
    }
}
