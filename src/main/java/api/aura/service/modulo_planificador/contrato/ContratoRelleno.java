package api.aura.service.modulo_planificador.contrato;

import api.aura.service.modulo_planificador.ContratoProyectoService;
import api.aura.service.modulo_planificador.Numeros;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Relleno del contrato (MODELO_2) con los datos del proyecto (equivale a contract_generate.py).
 *
 * <p>Se parte del Word modelo tal cual ({@code plantillas/MODELO_2.docx}): se rellenan sus huecos «______», el precio, la forma de pago
 * y el año, y se añade el anexo 1 con el presupuesto. El texto legal del modelo no se toca. Lo que no se conoce se deja en blanco para
 * completarlo a mano. El resultado es un Word interno que {@link ContratoPdf} dibuja como PDF: el Word no se entrega.
 */
@Component
@RequiredArgsConstructor
public class ContratoRelleno {

    /** Los 16 huecos del modelo, en orden de aparición en el documento. */
    static final List<String> HUECOS = List.of(
            "kw_nominal", "kwp", "cubierta_de", "representante", "dni", "empresa", "domicilio", "cif", "instalacion",
            "kw_nominal", "kwp", "lugar_firma", "dia", "mes", "firmante", "firmante_dni");

    /** Importes que ya trae escritos el modelo (de un contrato anterior), en el orden de la forma de pago. */
    private static final String TOTAL_EJEMPLO_EN_LETRAS = "Veintinueve mil doscientos sesenta Euros";
    private static final String TOTAL_EJEMPLO = "29.260,00";
    private static final List<String> PAGOS_EJEMPLO = List.of("8.778", "11.704", "5.852", "2.926");
    /** Lugar de la firma si no se indica otro (la contratista es de Lorca y la jurisdicción del contrato también). */
    public static final String LUGAR_FIRMA_POR_DEFECTO = "Lorca";

    private static final List<String> MESES = List.of("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre",
            "octubre", "noviembre", "diciembre");
    private static final List<String> ORDINALES = List.of("Primer", "Segundo", "Tercer", "Cuarto", "Quinto", "Sexto", "Séptimo", "Octavo",
            "Noveno", "Décimo", "Undécimo", "Duodécimo");
    private static final String[] UNIDADES = {"cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once",
            "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós",
            "veintitrés", "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
    private static final String[] DECENAS = {"", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
    private static final String[] CENTENAS = {"", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos",
            "setecientos", "ochocientos", "novecientos"};

    private static final Pattern T = Pattern.compile("(<w:t(?: [^>]*)?>)(.*?)(</w:t>)", Pattern.DOTALL);
    private static final Pattern PARRAFO = Pattern.compile("<w:p\\b[^>]*>.*?</w:p>", Pattern.DOTALL);
    private static final String ROJO = "<w:color w:val=\"FF0000\"/>";

    /** Datos sin los que no se genera el contrato (la fecha y el lugar de la firma se dejan en blanco a propósito, y la batería puede no existir). */
    private static final List<String[]> OBLIGATORIOS = List.of(
            new String[]{"empresa", "Empresa promotora"}, new String[]{"cif", "CIF"}, new String[]{"domicilio", "Domicilio social"},
            new String[]{"representante", "Representante legal"}, new String[]{"dni", "DNI del representante"},
            new String[]{"instalacion", "Dirección de la instalación"}, new String[]{"cubierta_de", "Cubierta de…"},
            new String[]{"kw_nominal", "Potencia nominal (kW)"}, new String[]{"kwp", "Potencia total (kWp)"}, new String[]{"importe_total", "Importe total"},
            new String[]{"paneles_uds", "Paneles (unidades)"}, new String[]{"paneles_modelo", "Paneles (modelo)"},
            new String[]{"inversor_uds", "Inversor (unidades)"}, new String[]{"inversor_modelo", "Inversor (modelo)"}, new String[]{"estructura", "Estructura"});

    private final ContratoProyectoService contrato;

    // --------------------------------------------------------------------------------------- números

    private static String menosDeMil(int n) {
        if (n == 100) {
            return "cien";
        }
        List<String> partes = new ArrayList<>();
        if (n >= 100) {
            partes.add(CENTENAS[n / 100]);
            n %= 100;
        }
        if (n < 30) {
            if (n != 0 || partes.isEmpty()) {
                partes.add(UNIDADES[n]);
            }
        } else {
            int dec = n / 10;
            int uni = n % 10;
            partes.add(DECENAS[dec] + (uni != 0 ? " y " + UNIDADES[uni] : ""));
        }
        return String.join(" ", partes);
    }

    /** 12345 → «doce mil trescientos cuarenta y cinco». */
    public static String numeroEnLetras(long n) {
        if (n == 0) {
            return "cero";
        }
        List<String> partes = new ArrayList<>();
        long millones = n / 1_000_000;
        long resto = n % 1_000_000;
        long miles = resto / 1000;
        long unidades = resto % 1000;
        if (millones != 0) {
            partes.add(millones == 1 ? "un millón" : numeroEnLetras(millones) + " millones");
        }
        if (miles != 0) {
            partes.add(miles == 1 ? "mil" : menosDeMil((int) miles) + " mil");
        }
        if (unidades != 0) {
            partes.add(menosDeMil((int) unidades));
        }
        return String.join(" ", partes);
    }

    /** 38700 → «Treinta y ocho mil setecientos Euros»; con céntimos: «… Euros con cincuenta céntimos». */
    public static String eurosEnLetras(double importe) {
        long centimosTotal = (long) Math.rint(importe * 100);
        long euros = centimosTotal / 100;
        long centimos = centimosTotal % 100;
        String palabras = numeroEnLetras(euros);
        // «veintiún mil», «treinta y un mil»: el uno apocopado ante «mil»/«millones»
        palabras = palabras.replaceAll("veintiuno( mil| millones)", "veintiún$1");
        palabras = palabras.replaceAll("\\buno( mil| millones)", "un$1");
        String texto = palabras + " Euros";
        if (centimos != 0) {
            texto += " con " + numeroEnLetras(centimos) + " céntimos";
        }
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** 11704 → «11.704,00». */
    public static String formatoEs(double importe) {
        String[] partes = BigDecimal.valueOf(importe).setScale(2, RoundingMode.HALF_EVEN).toPlainString().split("\\.");
        String entero = partes[0];
        StringBuilder grupos = new StringBuilder();
        while (entero.length() > 3) {
            grupos.insert(0, "." + entero.substring(entero.length() - 3));
            entero = entero.substring(0, entero.length() - 3);
        }
        return entero + grupos + "," + partes[1];
    }

    private static String textoNumero(double valor) {
        String t = String.format(Locale.US, "%,.2f", valor).replaceAll("0+$", "").replaceAll("\\.$", "");
        return t.replace(",", "§").replace(".", ",").replace("§", ".");
    }

    // ------------------------------------------------------------------------------------------- XML

    private static String escapar(String t) {
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Aplica `f` a cada coincidencia (sin interpretar «$» ni «\» del resultado). */
    private static String sustituir(Pattern p, String entrada, Function<MatchResult, String> f) {
        Matcher m = p.matcher(entrada);
        StringBuilder sb = new StringBuilder();
        int ultimo = 0;
        while (m.find()) {
            sb.append(entrada, ultimo, m.start()).append(f.apply(m));
            ultimo = m.end();
        }
        return sb.append(entrada.substring(ultimo)).toString();
    }

    private static String textoParrafo(String xml) {
        Matcher m = T.matcher(xml);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            sb.append(m.group(2));
        }
        return sb.toString();
    }

    /** Rellena los huecos «____» en el orden en que aparecen. Cada hueco es una tira de guiones bajos, sola o dentro de una frase. */
    private static String rellenarHuecos(String xml, Map<String, String> valores) {
        int[] contador = {0};
        Pattern hueco = Pattern.compile("_+(\\.)?");
        String salida = sustituir(T, xml, m -> {
            String etiquetaApertura = m.group(1);
            String texto = m.group(2);
            String cierre = m.group(3);
            if (!texto.contains("_")) {
                return m.group();
            }
            String rellenado = sustituir(hueco, texto, g -> {
                int i = contador[0]++;
                String valor = i < HUECOS.size() ? valores.getOrDefault(HUECOS.get(i), "").strip() : "";
                if (valor.isEmpty()) {
                    return g.group(); // sin dato: el hueco se queda para rellenarlo a mano
                }
                if (i == 2) {
                    valor = valor.toUpperCase(); // el título «…SOBRE CUBIERTA DE ____» va en mayúsculas
                }
                String punto = g.group(1) == null ? "" : g.group(1);
                if (!punto.isEmpty() && valor.endsWith(".")) {
                    punto = ""; // «S.L.» + el punto que ya cierra la frase del modelo: un solo punto
                }
                return escapar(valor) + punto;
            });
            if (rellenado.equals(texto)) {
                return m.group();
            }
            return "<w:t xml:space=\"preserve\">" + rellenado + cierre;
        });
        if (contador[0] != HUECOS.size()) {
            throw new IllegalArgumentException("El modelo de contrato no tiene los " + HUECOS.size() + " huecos esperados (tiene " + contador[0]
                    + "): la plantilla ha cambiado.");
        }
        return salida;
    }

    private record Cambio(String xml, int veces) {
    }

    private static Cambio reemplazarTextos(String xml, String viejo, String nuevo) {
        int[] cuenta = {0};
        String salida = sustituir(T, xml, m -> {
            String texto = m.group(2);
            if (texto.contains(viejo)) {
                cuenta[0]++;
                texto = texto.replace(viejo, escapar(nuevo));
            }
            return m.group(1) + texto + m.group(3);
        });
        return new Cambio(salida, cuenta[0]);
    }

    /** Importes que el modelo parte en varios nodos: «11.704» «,» «00» « €». Los deja como «15.480,00». */
    private static Cambio reemplazarImporteDividido(String xml, String enteroViejo, double nuevo) {
        String completo = escapar(formatoEs(nuevo));
        List<int[]> posiciones = new ArrayList<>();
        List<String> textos = new ArrayList<>();
        Matcher m = T.matcher(xml);
        while (m.find()) {
            posiciones.add(new int[]{m.start(2), m.end(2)});
            textos.add(m.group(2));
        }
        for (int i = 0; i < textos.size(); i++) {
            if (!textos.get(i).equals(enteroViejo)) {
                continue;
            }
            List<Integer> siguientes = new ArrayList<>();
            StringBuilder juntos = new StringBuilder();
            for (int k = i + 1; k < Math.min(i + 4, textos.size()); k++) {
                siguientes.add(k);
                juntos.append(textos.get(k));
            }
            if (!Pattern.compile("^,\\d{2}").matcher(juntos).find()) {
                continue;
            }
            Map<Integer, String> nuevoTexto = new TreeMap<>(Comparator.reverseOrder());
            nuevoTexto.put(i, completo);
            int porCortar = 3; // «,00»
            for (int k : siguientes) {
                String texto = textos.get(k);
                int corte = Math.min(porCortar, texto.length());
                nuevoTexto.put(k, texto.substring(corte));
                porCortar -= corte;
                if (porCortar == 0) {
                    break;
                }
            }
            String salida = xml;
            // De atrás hacia delante, para que las posiciones de los nodos anteriores no cambien.
            for (Map.Entry<Integer, String> e : nuevoTexto.entrySet()) {
                int[] pos = posiciones.get(e.getKey());
                salida = salida.substring(0, pos[0]) + e.getValue() + salida.substring(pos[1]);
            }
            return new Cambio(salida, 1);
        }
        return new Cambio(xml, 0);
    }

    /** Todo el texto del cuerpo en negro: el modelo marca en rojo lo que hay que cambiar a mano, y una vez relleno no debe quedar rojo. */
    private static String todoNegro(String xml) {
        return sustituir(Pattern.compile("<w:color\\b[^>]*/>"), xml, m -> {
            String etiqueta = m.group();
            if (Pattern.compile("w:val=\"(000000|auto)\"", Pattern.CASE_INSENSITIVE).matcher(etiqueta).find() && !etiqueta.contains("w:themeColor")) {
                return etiqueta;
            }
            return "<w:color w:val=\"000000\"/>";
        });
    }

    /** Quita los «//» (y el rojo, si lo hay) con que el modelo señala los importes que hay que cambiar. */
    private static String quitarMarcas(String xml) {
        return sustituir(PARRAFO, xml, m -> {
            String parrafo = m.group();
            if (!textoParrafo(parrafo).contains("//")) {
                return parrafo;
            }
            parrafo = sustituir(T, parrafo, t -> t.group(1) + t.group(2).replace("//", "") + t.group(3));
            return parrafo.replace(ROJO, "");
        });
    }

    /** Cambia una palabra suelta del modelo (p. ej. «NAVE» de la portada), con su texto exacto, y le quita el rojo. */
    private static String rellenarPalabraRoja(String xml, String viejo, String nuevo) {
        return sustituir(Pattern.compile("<w:r\\b[^>]*>.*?</w:r>", Pattern.DOTALL), xml, m -> {
            String tramo = m.group();
            if (!textoParrafo(tramo).equals(viejo)) {
                return tramo;
            }
            tramo = tramo.replace(ROJO, "");
            Matcher t = T.matcher(tramo);
            if (t.find()) {
                return tramo.substring(0, t.start()) + t.group(1) + escapar(nuevo) + t.group(3) + tramo.substring(t.end());
            }
            return tramo;
        });
    }

    private static String propiedadesPrimerTramo(String parrafo) {
        Matcher m = Pattern.compile("<w:r\\b[^>]*>\\s*(<w:rPr>.*?</w:rPr>)", Pattern.DOTALL).matcher(parrafo);
        return m.find() ? m.group(1) : "";
    }

    private static String propiedadesParrafo(String parrafo) {
        Matcher m = Pattern.compile("<w:pPr>.*?</w:pPr>", Pattern.DOTALL).matcher(parrafo);
        return m.find() ? m.group() : "";
    }

    private static String tramo(String texto, String rpr, Boolean negrita) {
        String props = rpr;
        if (Boolean.TRUE.equals(negrita) && !props.contains("<w:b/>")) {
            props = !props.isEmpty() ? props.replaceFirst("<w:rPr>", "<w:rPr><w:b/>") : "<w:rPr><w:b/></w:rPr>";
        }
        if (Boolean.FALSE.equals(negrita)) {
            props = props.replace("<w:b/>", "").replace("<w:bCs/>", "");
        }
        return "<w:r>" + props + "<w:t xml:space=\"preserve\">" + escapar(texto) + "</w:t></w:r>";
    }

    /** Deja un párrafo con un único texto, conservando su formato (y sin rojo). */
    private static String reescribirParrafo(String xml, String empiezaPor, String texto) {
        return sustituir(PARRAFO, xml, m -> {
            String parrafo = m.group();
            if (!textoParrafo(parrafo).strip().startsWith(empiezaPor)) {
                return parrafo;
            }
            String rpr = propiedadesPrimerTramo(parrafo).replace(ROJO, "");
            return "<w:p>" + propiedadesParrafo(parrafo) + tramo(texto, rpr, false) + "</w:p>";
        });
    }

    /** Texto que cierra el párrafo de un pago personalizado: lo escrito, con minúscula inicial; sin preposición pasa a «en concepto de …». */
    static String concepto(String etiqueta) {
        String texto = ContratoProyectoService.junto(etiqueta).replaceAll("[ .;,]+$", "");
        String minuscula = texto.isEmpty() ? texto : Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
        if (!Pattern.compile("^(?:a|al|en|tras|una vez|con|por|durante|antes|contra|previo|previa|según|hasta|desde)\\b").matcher(minuscula).find()) {
            minuscula = "en concepto de " + minuscula;
        }
        return minuscula;
    }

    private static Cambio fijarImporteDelModelo(String xml, int indice, double valor) {
        String base = PAGOS_EJEMPLO.get(indice);
        Cambio c = reemplazarTextos(xml, base + ",00", formatoEs(valor));
        if (c.veces() > 0) {
            return c;
        }
        return reemplazarImporteDividido(xml, base, valor);
    }

    /** Frases del contrato que hablan de varios pagos y, con un único pago, pasan al singular. */
    private static final List<String[]> FRASES_UN_PAGO = List.of(
            new String[]{"Se entregan facturas de cada pago por separado.", "Se entrega factura del pago."},
            new String[]{"podrá hacer efectivas las cantidades señaladas mediante", "podrá hacer efectiva la cantidad señalada mediante"},
            new String[]{"llegado el momento de los acordados pagos", "llegado el momento del acordado pago"},
            new String[]{"El incumplimiento de alguno de los pagos a su vencimiento", "El incumplimiento del pago a su vencimiento"});

    private static String parrafosDePago(String titulo, Map<String, Object> etapa, String ppTitulo, String rTitulo, String ppCuerpo, String rCuerpo) {
        return "<w:p>" + ppTitulo + tramo(titulo, rTitulo, null) + "</w:p><w:p>" + ppCuerpo
                + tramo("El PROMOTOR abonará al CONTRATISTA previa presentación de la correspondiente factura por parte del CONTRATISTA, la cantidad de ", rCuerpo, false)
                + tramo(formatoEs((double) etapa.get("amount")) + " €", rCuerpo, true)
                + tramo(", más IVA, " + concepto((String) etapa.get("label")) + ".", rCuerpo, false)
                + "</w:p><w:p></w:p>";
    }

    private static int indiceDeParrafo(List<String> textos, String prefijo) {
        for (int i = 0; i < textos.size(); i++) {
            if (textos.get(i).strip().startsWith(prefijo)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No se encuentra «" + prefijo + "» en el modelo de contrato.");
    }

    /**
     * Sustituye los cuatro pagos del modelo por los indicados, todos con la misma fórmula del modelo y el concepto escrito por quien genera el
     * contrato. Con un único pago, «Pago único» y el resto del contrato en singular.
     */
    private static String bloquePagosPersonalizados(String xml, List<Map<String, Object>> etapas) {
        List<int[]> posiciones = new ArrayList<>();
        List<String> parrafos = new ArrayList<>();
        List<String> textos = new ArrayList<>();
        Matcher m = PARRAFO.matcher(xml);
        while (m.find()) {
            posiciones.add(new int[]{m.start(), m.end()});
            parrafos.add(m.group());
            textos.add(textoParrafo(m.group()));
        }
        int primero = indiceDeParrafo(textos, "Primer Pago");
        int fin = indiceDeParrafo(textos, "El PROMOTOR podrá hacer efectivas");
        // Formato del título y del cuerpo: el del segundo pago del modelo (el primero lleva un párrafo más)
        int titulo = indiceDeParrafo(textos, "Segundo Pago");
        int cuerpo = -1;
        for (int i = titulo; i < textos.size(); i++) {
            if (textos.get(i).startsWith("El PROMOTOR abonará")) {
                cuerpo = i;
                break;
            }
        }
        if (cuerpo < 0) {
            throw new IllegalArgumentException("No se encuentra el cuerpo de un pago en el modelo de contrato.");
        }
        String ppT = propiedadesParrafo(parrafos.get(titulo));
        String rT = propiedadesPrimerTramo(parrafos.get(titulo));
        String ppC = propiedadesParrafo(parrafos.get(cuerpo));
        String rC = propiedadesPrimerTramo(parrafos.get(cuerpo));

        StringBuilder bloque = new StringBuilder();
        if (etapas.size() == 1) {
            bloque.append(parrafosDePago("Pago único.", etapas.get(0), ppT, rT, ppC, rC));
        } else {
            for (int n = 0; n < etapas.size(); n++) {
                String ordinal = n < ORDINALES.size() ? ORDINALES.get(n) : (n + 1) + ".º";
                bloque.append(parrafosDePago(ordinal + " Pago.", etapas.get(n), ppT, rT, ppC, rC));
            }
        }
        String salida = xml.substring(0, posiciones.get(primero)[0]) + bloque + xml.substring(posiciones.get(fin)[0]);
        if (etapas.size() == 1) {
            for (String[] f : FRASES_UN_PAGO) {
                salida = reemplazarTextos(salida, f[0], f[1]).xml();
            }
        }
        return salida;
    }

    private static final String SANGRIA_HABITUAL = "<w:ind w:left=\"0\" w:right=\"-25\" w:firstLine=\"0\"/>";

    /** Los párrafos de los pagos pasan a la sangría habitual del cuerpo (conservaban una sangría francesa del modelo). */
    private static String sangriaHabitualDePagos(String xml) {
        return sustituir(PARRAFO, xml, m -> {
            String parrafo = m.group();
            String t = textoParrafo(parrafo).strip();
            if (!t.startsWith("El PROMOTOR abonará") && !t.startsWith("El PROMOTOR podrá hacer efectiv")) {
                return parrafo;
            }
            Matcher s = Pattern.compile("<w:ind\\b[^>]*/>").matcher(parrafo);
            return s.find() ? parrafo.substring(0, s.start()) + SANGRIA_HABITUAL + parrafo.substring(s.end()) : parrafo;
        });
    }

    private static String parrafoSimple(String texto, boolean negrita, int tamano) {
        // El estilo «Normal» del modelo es Courier New: el anexo usa Arial, como el cuerpo del contrato.
        String rpr = "<w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\"/>" + (negrita ? "<w:b/>" : "") + "<w:sz w:val=\"" + tamano + "\"/></w:rPr>";
        return "<w:p><w:r>" + rpr + "<w:t xml:space=\"preserve\">" + escapar(texto) + "</w:t></w:r></w:p>";
    }

    private static String tabla(List<String[]> filas, int[] anchos) {
        StringBuilder borde = new StringBuilder();
        for (String lado : new String[]{"top", "left", "bottom", "right", "insideH", "insideV"}) {
            borde.append("<w:").append(lado).append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"808080\"/>");
        }
        StringBuilder cuerpo = new StringBuilder();
        for (int i = 0; i < filas.size(); i++) {
            StringBuilder celdas = new StringBuilder();
            for (int j = 0; j < filas.get(i).length; j++) {
                celdas.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(anchos[j]).append("\" w:type=\"dxa\"/></w:tcPr>")
                        .append(parrafoSimple(filas.get(i)[j], i == 0, 20)).append("</w:tc>");
            }
            cuerpo.append("<w:tr>").append(celdas).append("</w:tr>");
        }
        int suma = Arrays.stream(anchos).sum();
        return "<w:tbl><w:tblPr><w:tblW w:w=\"" + suma + "\" w:type=\"dxa\"/><w:tblBorders>" + borde + "</w:tblBorders></w:tblPr>" + cuerpo + "</w:tbl>";
    }

    private static String anexo(List<String[]> filas, List<Map<String, Object>> pagos, String titulo) {
        StringBuilder out = new StringBuilder("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
        out.append(parrafoSimple("ANEXO 1 — PRESUPUESTO", true, 28));
        out.append(parrafoSimple(titulo, false, 22));
        List<String[]> t1 = new ArrayList<>();
        t1.add(new String[]{"Concepto", "Cantidad", "Descripción"});
        t1.addAll(filas);
        out.append(tabla(t1, new int[]{3200, 1800, 4200}));
        out.append(parrafoSimple("", false, 22));
        out.append(parrafoSimple("Forma de pago", true, 22));
        List<String[]> t2 = new ArrayList<>();
        t2.add(new String[]{"Pago", "%", "Importe (sin IVA)"});
        for (Map<String, Object> s : pagos) {
            t2.add(new String[]{(String) s.get("label"), textoNumero((double) s.get("percent")) + " %", formatoEs((double) s.get("amount")) + " €"});
        }
        out.append(tabla(t2, new int[]{5200, 1200, 2800}));
        return out.toString();
    }

    private static String limpio(Object v) {
        return v == null || "".equals(v) ? "" : String.valueOf(v).strip();
    }

    /** Nombres de los datos obligatorios que faltan para generar el contrato. */
    public static List<String> camposQueFaltan(Map<String, Object> datos) {
        List<String> faltan = new ArrayList<>();
        for (String[] c : OBLIGATORIOS) {
            if (limpio(datos.get(c[0])).isEmpty()) {
                faltan.add(c[1]);
            }
        }
        return faltan;
    }

    // ------------------------------------------------------------------------------------- generar

    private static byte[] plantilla() {
        try (InputStream in = ContratoRelleno.class.getResourceAsStream("/plantillas/MODELO_2.docx")) {
            if (in == null) {
                throw new IllegalStateException("Falta la plantilla del contrato (plantillas/MODELO_2.docx).");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la plantilla del contrato.");
        }
    }

    private static String leerDocumento(byte[] docx) throws IOException {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().equals("word/document.xml")) {
                    return new String(z.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("La plantilla del contrato no tiene word/document.xml.");
    }

    /** Contrato en Word (interno). `datos`: campos del contrato, `importe_total`, `payment_plan` y equipos. */
    @SuppressWarnings("unchecked")
    public byte[] generar(Map<String, Object> datos, LocalDate hoy) {
        byte[] original = plantilla();
        String total_crudo = limpio(datos.get("importe_total")).replace(" ", "");
        Double total = null;
        if (!total_crudo.isEmpty()) {
            String texto = total_crudo.contains(",") ? total_crudo.replace(".", "").replace(",", ".") : total_crudo;
            try {
                total = Numeros.redondear2(Double.parseDouble(texto));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("El importe total no es un número válido");
            }
        }
        String anio = limpio(datos.get("anio")).isEmpty() ? String.valueOf(hoy.getYear()) : limpio(datos.get("anio"));
        // Fecha de la firma: la que se indique; si no, la de hoy. Con día y mes indicados se usa el año del contrato; sin ellos, el de hoy.
        String diaDado = limpio(datos.get("dia"));
        String mesDado = limpio(datos.get("mes"));
        String diaFirma = diaDado.isEmpty() ? String.valueOf(hoy.getDayOfMonth()) : diaDado;
        String mesFirma = mesDado.isEmpty() ? MESES.get(hoy.getMonthValue() - 1) : mesDado;
        String anioFirma = !limpio(datos.get("anio_firma")).isEmpty() ? limpio(datos.get("anio_firma"))
                : (!diaDado.isEmpty() && !mesDado.isEmpty() ? anio : String.valueOf(hoy.getYear()));
        Map<String, Object> plan = contrato.normalizarPlanPagos(datos.get("payment_plan") instanceof Map<?, ?> m ? (Map<String, Object>) m : null);
        List<Map<String, Object>> calendario = total != null ? (List<Map<String, Object>>) contrato.calendarioPagos(plan, total).get("stages") : List.of();

        String xml;
        try {
            xml = leerDocumento(original);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la plantilla del contrato.");
        }

        Map<String, String> valores = new HashMap<>();
        for (String k : new HashSet<>(HUECOS)) {
            valores.put(k, limpio(datos.get(k)));
        }
        valores.put("dia", diaFirma);
        valores.put("mes", mesFirma);
        if (valores.get("lugar_firma").isEmpty()) {
            valores.put("lugar_firma", LUGAR_FIRMA_POR_DEFECTO); // el domicilio de la contratista; se puede cambiar
        }
        xml = rellenarHuecos(xml, valores);

        // Portada: «NAVE» y «nave» van en rojo en el modelo para cambiarlos por a qué pertenece la cubierta.
        if (!valores.get("cubierta_de").isEmpty()) {
            xml = rellenarPalabraRoja(xml, "NAVE", valores.get("cubierta_de").toUpperCase());
            xml = rellenarPalabraRoja(xml, "nave", valores.get("cubierta_de"));
        }

        // Garantía de los módulos (depende de la marca; la propuesta la indica).
        String producto = limpio(datos.get("garantia_producto"));
        String produccion = limpio(datos.get("garantia_produccion"));
        String potencia = limpio(datos.get("garantia_potencia"));
        if (!producto.isEmpty() && !produccion.isEmpty() && !potencia.isEmpty()) {
            int anios;
            try {
                anios = Integer.parseInt(producto);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("La garantía de producto tiene que ser un número de años");
            }
            xml = reescribirParrafo(xml, "La garantía otorgada por los módulos solares",
                    "La garantía otorgada por los módulos solares, será de " + numeroEnLetras(anios) + " (" + producto + ") años de garantía de producto y "
                            + produccion + " años de garantía de la potencia nominal al " + potencia + "% a partir del “período de garantía”.");
        }

        if (total != null) {
            xml = quitarMarcas(xml);
            Cambio letras = reemplazarTextos(xml, TOTAL_EJEMPLO_EN_LETRAS, eurosEnLetras(total));
            Cambio cifras = reemplazarTextos(letras.xml(), TOTAL_EJEMPLO, formatoEs(total));
            xml = cifras.xml();
            if (letras.veces() == 0 || cifras.veces() == 0) {
                throw new IllegalArgumentException("El modelo de contrato no tiene el importe esperado: la plantilla ha cambiado.");
            }
            if ("standard".equals(plan.get("mode"))) {
                // Mismo texto legal del modelo; solo cambian los cuatro importes.
                for (int i = 0; i < Math.min(calendario.size(), PAGOS_EJEMPLO.size()); i++) {
                    Cambio c = fijarImporteDelModelo(xml, i, (double) calendario.get(i).get("amount"));
                    if (c.veces() == 0) {
                        throw new IllegalArgumentException("El modelo de contrato no tiene el pago «" + PAGOS_EJEMPLO.get(i) + "»: la plantilla ha cambiado.");
                    }
                    xml = c.xml();
                }
            } else {
                xml = bloquePagosPersonalizados(xml, calendario);
            }
        }

        // Año: «2.025» de la portada (en varios nodos) y «de 2025» de la fecha de firma.
        final String anioPortada = anio;
        xml = sustituir(PARRAFO, xml, m -> {
            String parrafo = m.group();
            if (!textoParrafo(parrafo).strip().equals("2.025")) {
                return parrafo;
            }
            boolean[] primero = {true};
            return sustituir(T, parrafo, t -> {
                if (primero[0]) {
                    primero[0] = false;
                    return t.group(1) + anioPortada + t.group(3);
                }
                return t.group(1) + t.group(3);
            });
        });
        xml = reemplazarTextos(xml, " 2025", " " + anioFirma).xml(); // «… de 2025» de la fecha de la firma

        // Anexo 1: el presupuesto con los equipos y el importe.
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[]{"Módulos fotovoltaicos", unidades(datos, "paneles_uds"), limpio(datos.get("paneles_modelo"))});
        filas.add(new String[]{"Inversor", unidades(datos, "inversor_uds"), limpio(datos.get("inversor_modelo"))});
        filas.add(new String[]{"Estructura soporte", "", limpio(datos.get("estructura"))});
        filas.add(new String[]{"Batería de almacenamiento", "", limpio(datos.get("bateria")).isEmpty() ? "No incluida" : limpio(datos.get("bateria"))});
        filas.add(new String[]{"Potencia pico instalada", limpio(datos.get("kwp")).isEmpty() ? "" : limpio(datos.get("kwp")) + " kWp", ""});
        filas.add(new String[]{"Precio total (sin IVA)", "", total != null ? formatoEs(total) + " €" : ""});
        String titulo = "Instalación solar fotovoltaica para autoconsumo" + (limpio(datos.get("empresa")).isEmpty() ? "" : " — " + limpio(datos.get("empresa")));
        xml = sangriaHabitualDePagos(xml);
        int corte = xml.lastIndexOf("<w:sectPr");
        xml = xml.substring(0, corte) + anexo(filas, calendario, titulo) + xml.substring(corte);
        xml = todoNegro(xml);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zout = new ZipOutputStream(out);
             ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(original))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                byte[] contenido = zin.readAllBytes();
                if (e.getName().equals("word/document.xml")) {
                    contenido = xml.getBytes(StandardCharsets.UTF_8);
                }
                zout.putNextEntry(new ZipEntry(e.getName()));
                zout.write(contenido);
                zout.closeEntry();
            }
            zout.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo preparar el contrato.");
        }
    }

    private static String unidades(Map<String, Object> datos, String clave) {
        String v = limpio(datos.get(clave));
        return v.isEmpty() ? "" : v + " uds";
    }
}
