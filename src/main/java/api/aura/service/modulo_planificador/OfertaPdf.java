package api.aura.service.modulo_planificador;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lectura del presupuesto de la oferta (PDF) y extracción de los datos del contrato inicial (equivale a budget_extract.py).
 *
 * <p>El presupuesto es una presentación comercial: los datos están como texto, pero sin sus etiquetas (las etiquetas «Potencia pico total:»,
 * «Nº de módulos:»… son parte de la imagen de la página). Por eso se reconocen por su forma —«51,6 kwp», «Longi 645 wp», «38.700,00€»— y por
 * el orden en que aparecen en la página técnica. Todo lo que se devuelve lleva el fragmento del que sale para poder comprobarlo.
 */
@Component
public class OfertaPdf {

    static final int MAX_PAGINAS = 80;
    private static final int U = Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNICODE_CASE;
    private static final Pattern HUECO = Pattern.compile("_{2,}");
    private static final Pattern FORMA_JURIDICA = Pattern.compile(
            "[\\s,]+(?:S\\.?\\s?L\\.?\\s?U\\.?|S\\.?\\s?L\\.?\\s?L\\.?|S\\.?\\s?L\\.?|S\\.?\\s?A\\.?\\s?U\\.?|S\\.?\\s?A\\.?|S\\.?\\s?C\\.?|C\\.?\\s?B\\.?)\\.?\\s*$",
            U | Pattern.CASE_INSENSITIVE);
    private static final Pattern CALLE = Pattern.compile(
            "^(?:avda|avenida|calle|c/|ctra|carretera|pol[ií]gono|pol\\.|camino|plaza|paseo|ronda|urb|urbanizaci[oó]n|parcela|partida|diputaci[oó]n|paraje|km)\\b",
            U | Pattern.CASE_INSENSITIVE);
    private static final Pattern MARCADOR = Pattern.compile("^(?:x{2,}|-+|—+|–+|n/?a|no|ninguna?|\\.+)$", U | Pattern.CASE_INSENSITIVE);
    private static final Pattern LINEA_KWP = Pattern.compile("^(\\d{1,4}(?:[.,]\\d{1,3})?)\\s*kw\\s?p(?:ico)?$", U | Pattern.CASE_INSENSITIVE);
    private static final Pattern LINEA_PANEL = Pattern.compile("\\b(\\d{3,4})\\s*w\\s?p\\b", U | Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPORTE = Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:\\.\\d{3})+|\\d+),(\\d{2})\\s*(?:€|euros?\\b|eur\\b)", U | Pattern.CASE_INSENSITIVE);
    private static final Pattern PALABRAS_BATERIA = Pattern.compile("bater[ií]a|luna\\s?2000|powerwall|\\bkwh\\b|\\bbyd\\b|almacenamiento", U | Pattern.CASE_INSENSITIVE);
    private static final Pattern PORCENTAJE = Pattern.compile("(?<![\\d.,])(\\d{1,3})\\s*%", U);
    private static final Pattern ESPACIOS = Pattern.compile("\\s+", U);

    /** El archivo no se puede leer; el mensaje explica qué hacer. */
    public static class DocumentoNoSoportado extends IllegalArgumentException {
        public DocumentoNoSoportado(String mensaje) {
            super(mensaje);
        }
    }

    /** Un trozo de texto con su posición (texto, x, y, tamaño). */
    public record Trozo(String texto, double x, double y, double tamano) {
    }

    // ----------------------------------------------------------------------------- utilidades

    private static String junto(String t) {
        String limpio = ESPACIOS.matcher(t.strip()).replaceAll(" ");
        return limpio;
    }

    private static Map<String, Object> hallado(String valor, String fuente, String confianza) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", valor);
        m.put("source", fuente);
        m.put("confidence", confianza);
        return m;
    }

    /** Valor para un campo numérico del formulario: sin ceros sobrantes y con punto decimal. */
    static String formatoNumero(double valor) {
        String t = new java.math.BigDecimal(valor).setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString();
        return t.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Nombre de la empresa sin su forma jurídica («Solar Vega, S.L.U.» → «Solar Vega»). */
    public static String limpiarEmpresa(String crudo) {
        String nombre = junto(crudo);
        if (!Pattern.compile("[A-Za-zÁÉÍÓÚÑáéíóúñ]{2}").matcher(nombre).find() || HUECO.matcher(nombre).find()) return null;
        nombre = FORMA_JURIDICA.matcher(nombre).replaceAll("").replaceAll("^[ ,.]+|[ ,.]+$", "");
        return nombre.isEmpty() ? null : nombre;
    }

    /** «Avda. Juan Carlos I, nº 120, 30800 Lorca (Murcia)» → «Lorca». */
    public static String localidadDeDireccion(String direccion) {
        if (HUECO.matcher(direccion).find()) return null;
        direccion = direccion.replaceAll("\\([^)]*\\)", "");
        String candidata = null;
        for (String parte : direccion.split(",")) {
            parte = parte.strip();
            if (parte.isEmpty()) continue;
            parte = parte.replaceFirst("^\\d{4,5}\\s+", ""); // código postal delante
            if (Pattern.compile("\\d").matcher(parte).find() || CALLE.matcher(parte).find() || Pattern.compile("^n[ºo°]", U | Pattern.CASE_INSENSITIVE).matcher(parte).find()) continue;
            candidata = parte;
        }
        return candidata;
    }

    // ------------------------------------------------------------------------- leer el PDF

    /** Texto de cada página. Un PDF escaneado (solo imagen) devuelve páginas vacías. */
    public List<String> leerPaginas(byte[] datos) {
        try (PDDocument documento = Loader.loadPDF(datos)) {
            if (documento.isEncrypted()) throw new DocumentoNoSoportado("El PDF está protegido con contraseña y no se puede leer.");
            int total = Math.min(documento.getNumberOfPages(), MAX_PAGINAS);
            List<String> paginas = new ArrayList<>();
            for (int i = 1; i <= total; i++) {
                try {
                    PDFTextStripper lector = new PDFTextStripper();
                    lector.setStartPage(i);
                    lector.setEndPage(i);
                    paginas.add(lector.getText(documento));
                } catch (IOException | RuntimeException e) { // una página rota no debe impedir leer las demás
                    paginas.add("");
                }
            }
            return paginas;
        } catch (DocumentoNoSoportado e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new DocumentoNoSoportado("No se ha podido leer el PDF: parece dañado.");
        }
    }

    /** Palabras con su posición, juntando las que van seguidas en la misma línea en un solo trozo (como cada texto que dibuja el PDF). */
    private static final class Colector extends PDFTextStripper {
        final List<Trozo> palabras = new ArrayList<>();
        final List<Double> finales = new ArrayList<>();

        Colector() throws IOException {
            super();
        }

        @Override
        protected void writeString(String texto, List<TextPosition> posiciones) {
            if (texto.isBlank() || posiciones.isEmpty()) return;
            TextPosition primera = posiciones.get(0);
            TextPosition ultima = posiciones.get(posiciones.size() - 1);
            palabras.add(new Trozo(texto, primera.getXDirAdj(), primera.getPageHeight() - primera.getYDirAdj(), primera.getFontSizeInPt()));
            finales.add((double) (ultima.getXDirAdj() + ultima.getWidthDirAdj()));
        }
    }

    /**
     * Trozos de texto con su posición (texto, x, y, tamaño) de las páginas indicadas (índices desde 0). Sirve para leer lo que solo se entiende
     * por dónde está: la X que marca el tipo de estructura. Sin posiciones solo se pierde la estructura.
     */
    public Map<Integer, List<Trozo>> leerTrozos(byte[] datos, List<Integer> indices) {
        Map<Integer, List<Trozo>> salida = new HashMap<>();
        try (PDDocument documento = Loader.loadPDF(datos)) {
            for (int indice : indices) {
                Colector c = new Colector();
                c.setSortByPosition(false);
                c.setStartPage(indice + 1);
                c.setEndPage(indice + 1);
                c.getText(documento);
                salida.put(indice, unirPalabras(c.palabras, c.finales));
            }
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
        return salida;
    }

    private static List<Trozo> unirPalabras(List<Trozo> palabras, List<Double> finales) {
        List<Trozo> salida = new ArrayList<>();
        Trozo actual = null;
        double finActual = 0;
        for (int i = 0; i < palabras.size(); i++) {
            Trozo p = palabras.get(i);
            if (actual != null && Math.abs(actual.y() - p.y()) < 1 && p.x() - finActual < p.tamano() * 0.8 && p.x() >= actual.x()) {
                actual = new Trozo(actual.texto() + " " + p.texto(), actual.x(), actual.y(), actual.tamano());
            } else {
                if (actual != null) salida.add(actual);
                actual = p;
            }
            finActual = finales.get(i);
        }
        if (actual != null) salida.add(actual);
        return salida;
    }

    // ----------------------------------------------------------------------------- extracción

    private static double ancho(Trozo c) {
        return c.texto().length() * c.tamano() * 0.5; // los anchos reales no se conocen: se estiman por el tamaño
    }

    private static String unirTrozos(List<Trozo> partes) {
        TreeMap<Double, List<Trozo>> filas = new TreeMap<>(Comparator.reverseOrder());
        List<Trozo> ordenadas = new ArrayList<>(partes);
        ordenadas.sort(Comparator.comparingDouble(c -> -c.y()));
        for (Trozo parte : ordenadas) {
            Double clave = null;
            for (Double y : filas.keySet()) {
                if (Math.abs(y - parte.y()) < 3) {
                    clave = y;
                    break;
                }
            }
            filas.computeIfAbsent(clave == null ? parte.y() : clave, k -> new ArrayList<>()).add(parte);
        }
        List<String> lineas = new ArrayList<>();
        for (List<Trozo> fila : filas.values()) {
            fila.sort(Comparator.comparingDouble(Trozo::x));
            StringBuilder sb = new StringBuilder();
            fila.forEach(p -> sb.append(p.texto()));
            lineas.add(sb.toString());
        }
        return junto(String.join(" ", lineas));
    }

    /** Tipo de estructura: el texto que hay bajo la X, en su misma columna. Devuelve {nombre, texto de cabecera} o null si no se puede saber. */
    static String[] estructuraPorPosicion(List<Trozo> trozos) {
        List<Trozo> marcas = trozos.stream().filter(c -> c.texto().strip().equalsIgnoreCase("x")).toList();
        if (marcas.size() != 1) return null; // ninguna X (o varias): que lo indique una persona
        Trozo marca = marcas.get(0);
        double centroMarca = marca.x() + ancho(marca) / 2;
        List<Trozo> otros = trozos.stream().filter(c -> c != marca).toList();
        List<Trozo> encima = otros.stream().filter(c -> c.y() > marca.y()).toList();
        if (encima.isEmpty()) return null;
        double yCabecera = encima.stream().mapToDouble(Trozo::y).min().getAsDouble(); // la fila de cabeceras más cercana a la X
        List<Trozo> cabeceras = encima.stream().filter(c -> Math.abs(c.y() - yCabecera) < 3).sorted(Comparator.comparingDouble(Trozo::x)).toList();
        if (cabeceras.isEmpty()) return null;
        double[] centros = cabeceras.stream().mapToDouble(c -> c.x() + ancho(c) / 2).toArray();
        int columna = 0;
        for (int i = 1; i < centros.length; i++) {
            if (Math.abs(centros[i] - centroMarca) < Math.abs(centros[columna] - centroMarca)) columna = i;
        }
        double bajo = columna > 0 ? (centros[columna - 1] + centros[columna]) / 2 : Double.NEGATIVE_INFINITY;
        double alto = columna + 1 < centros.length ? (centros[columna] + centros[columna + 1]) / 2 : Double.POSITIVE_INFINITY;
        String textoCabecera = unirTrozos(List.of(cabeceras.get(columna)));
        List<Trozo> debajo = otros.stream().filter(c -> c.y() < marca.y() && bajo <= c.x() + ancho(c) / 2 && c.x() + ancho(c) / 2 < alto).toList();
        String nombre = debajo.isEmpty() ? textoCabecera : unirTrozos(debajo);
        return nombre.isEmpty() ? null : new String[]{nombre, textoCabecera};
    }

    private static List<String> lineas(String pagina) {
        List<String> salida = new ArrayList<>();
        for (String cruda : pagina.split("\\R")) {
            if (!cruda.isBlank()) salida.add(junto(cruda));
        }
        return salida;
    }

    private static double importe(String entero, String decimales) {
        return Double.parseDouble(entero.replace(".", "") + "." + decimales);
    }

    /** Páginas que parecen mostrar las opciones de estructura (para leer dónde está la X). */
    public static List<Integer> paginasDeEstructura(List<String> paginas) {
        List<Integer> salida = new ArrayList<>();
        for (int i = 0; i < paginas.size(); i++) {
            if (Pattern.compile("marquesina", U | Pattern.CASE_INSENSITIVE).matcher(paginas.get(i)).find()
                    && Pattern.compile("lastrada", U | Pattern.CASE_INSENSITIVE).matcher(paginas.get(i)).find()) {
                salida.add(i);
            }
        }
        return salida;
    }

    private record Importe(double valor, String texto, int pagina) {
    }

    /** Datos del presupuesto (campos del contrato, proyecto, avisos…); null si no parece un presupuesto. */
    public static Map<String, Object> extraer(List<String> paginas, Map<Integer, List<Trozo>> disposicion) {
        Map<String, Map<String, Object>> campos = new LinkedHashMap<>();
        Map<String, Map<String, Object>> proyecto = new LinkedHashMap<>();
        List<String> avisos = new ArrayList<>();
        Map<String, String> cliente = new LinkedHashMap<>();
        String textoCompleto = String.join("\n", paginas);

        // ---- Cliente (primera página: «Nombre de la empresa: …», «Dirección: …»)
        Matcher m = Pattern.compile("Nombre\\s+de\\s+la\\s+empresa\\s*:\\s*([^\\n]+)", U | Pattern.CASE_INSENSITIVE).matcher(textoCompleto);
        if (m.find()) cliente.put("name", junto(m.group(1)));
        m = Pattern.compile("Direcci[oó]n\\s*:\\s*([^\\n]+)", U | Pattern.CASE_INSENSITIVE).matcher(textoCompleto);
        if (m.find()) cliente.put("address", junto(m.group(1)));

        String empresa = cliente.containsKey("name") ? limpiarEmpresa(cliente.get("name")) : null;
        if (empresa != null) proyecto.put("company", hallado(empresa, "Nombre de la empresa: " + cliente.get("name"), "alta"));
        String localidad = cliente.containsKey("address") ? localidadDeDireccion(cliente.get("address")) : null;
        if (localidad != null) proyecto.put("locality", hallado(localidad, "Dirección: " + cliente.get("address"), "media"));
        if (empresa != null) {
            proyecto.put("name", hallado("Instalación " + empresa + (localidad != null ? " " + localidad : ""), "Empresa y localidad del presupuesto", "media"));
        }

        // ---- Propuesta técnica
        int numeroTecnica = -1;
        List<String> lineasTecnicas = null;
        int posicion = -1;
        buscar:
        for (int n = 0; n < paginas.size(); n++) {
            List<String> ls = lineas(paginas.get(n));
            for (int i = 0; i < ls.size(); i++) {
                if (LINEA_KWP.matcher(ls.get(i)).find()) {
                    numeroTecnica = n;
                    lineasTecnicas = ls;
                    posicion = i;
                    break buscar;
                }
            }
        }
        Boolean bateriasDeclaradas = null;
        if (lineasTecnicas != null) {
            String fuente = "Propuesta técnica (pág. " + (numeroTecnica + 1) + "): " + String.join(" · ", lineasTecnicas);
            Matcher kwp = LINEA_KWP.matcher(lineasTecnicas.get(posicion));
            kwp.find();
            campos.put("potencia_kwp", hallado(formatoNumero(Double.parseDouble(kwp.group(1).replace(",", "."))), fuente, "alta"));
            String modulos = en(lineasTecnicas, posicion + 1);
            String baterias = en(lineasTecnicas, posicion + 2);
            String inversores = en(lineasTecnicas, posicion + 3);
            if (modulos != null && modulos.matches("(?U)\\d{1,4}")) campos.put("paneles_uds", hallado(modulos, fuente, "alta"));
            if (inversores != null && inversores.matches("(?U)\\d{1,3}")) campos.put("inversor_uds", hallado(inversores, fuente, "alta"));
            if (baterias != null) {
                String bajo = baterias.toLowerCase();
                if (Set.of("no", "0", "sin baterías", "sin baterias").contains(bajo)) {
                    bateriasDeclaradas = false;
                    avisos.add("La propuesta técnica indica que no lleva baterías.");
                } else if (bajo.equals("sí") || bajo.equals("si") || bajo.matches("(?U)\\d{1,2}")) {
                    bateriasDeclaradas = true;
                }
            }
        }

        // ---- Equipos: marca y modelo
        for (int n = 0; n < paginas.size(); n++) {
            if (n == numeroTecnica) continue;
            List<String> ls = lineas(paginas.get(n));
            String lineaPanel = ls.stream().filter(l -> LINEA_PANEL.matcher(l).find()).findFirst().orElse(null);
            if (lineaPanel == null) continue;
            String fuente = "Equipos (pág. " + (n + 1) + "): " + String.join(" · ", ls);
            String panel = Pattern.compile("\\bwp\\b", U | Pattern.CASE_INSENSITIVE).matcher(junto(lineaPanel)).replaceAll("Wp");
            campos.put("paneles_modelo", hallado(panel, fuente, "alta"));
            List<String> resto = ls.stream().filter(l -> !l.equals(lineaPanel) && !MARCADOR.matcher(l).find()).toList();
            List<String> deBateria = resto.stream().filter(l -> PALABRAS_BATERIA.matcher(l).find()).toList();
            List<String> deInversor = resto.stream().filter(l -> !deBateria.contains(l)).toList();
            if (!deInversor.isEmpty()) campos.put("inversor_modelo", hallado(String.join(" ", deInversor), fuente, "alta"));
            if (!deBateria.isEmpty()) {
                campos.put("bateria", hallado(String.join(" ", deBateria), fuente, "media"));
            } else if (Boolean.TRUE.equals(bateriasDeclaradas) && !deInversor.isEmpty()) {
                avisos.add("La propuesta técnica indica baterías, pero no se ha podido identificar su modelo: revisa el inversor y completa la batería a mano.");
            }
            break;
        }

        // ---- Importe y forma de pago
        List<Importe> importes = new ArrayList<>();
        for (int n = 0; n < paginas.size(); n++) {
            Matcher f = IMPORTE.matcher(paginas.get(n));
            while (f.find()) {
                double valor = importe(f.group(1), f.group(2));
                if (valor >= 1000) importes.add(new Importe(valor, junto(f.group(0)), n));
            }
        }
        if (!importes.isEmpty()) {
            Set<Double> distintos = new HashSet<>();
            importes.forEach(i -> distintos.add(i.valor()));
            Importe mayor = importes.stream().max(Comparator.comparingDouble(Importe::valor).thenComparing(Importe::texto).thenComparingInt(Importe::pagina)).get();
            campos.put("importe_total", hallado(formatoNumero(mayor.valor()), "Propuesta económica (pág. " + (mayor.pagina() + 1) + "): " + mayor.texto(),
                    distintos.size() == 1 ? "alta" : "media"));
            if (distintos.size() > 1) {
                avisos.add("Hay varios importes en el presupuesto; se ha tomado el mayor. Compruébalo antes de crear el proyecto.");
            }
            avisos.add("El importe del presupuesto va sin IVA.");
            for (String pagina : paginas) {
                if (PORCENTAJE.matcher(pagina).find() && IMPORTE.matcher(pagina).find()) {
                    List<Integer> partes = new ArrayList<>();
                    Matcher p = PORCENTAJE.matcher(pagina);
                    while (p.find()) partes.add(Integer.parseInt(p.group(1)));
                    if (partes.containsAll(List.of(30, 40, 20, 10))) {
                        avisos.add("Forma de pago: 30 / 40 / 20 / 10 %, como en el contrato modelo.");
                    } else {
                        StringJoiner sj = new StringJoiner(", ");
                        partes.forEach(s -> sj.add(s + " %"));
                        avisos.add("Forma de pago distinta de la habitual (30/40/20/10): " + sj);
                    }
                    break;
                }
            }
        }

        // Garantías de los módulos (para el contrato): «12 años de garantía al producto» y «30 años de garantía de producción al 87,4% de la potencia inicial».
        Map<String, String> extras = new LinkedHashMap<>();
        String plano = junto(textoCompleto);
        m = Pattern.compile("(\\d{1,2})\\s*años\\s+de\\s+garant[ií]a\\s+al\\s+producto", U | Pattern.CASE_INSENSITIVE).matcher(plano);
        if (m.find()) extras.put("garantia_producto", m.group(1));
        m = Pattern.compile("(\\d{1,2})\\s*años\\s+de\\s+garant[ií]a\\s+de\\s+producci[oó]n\\s+al\\s+(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*%", U | Pattern.CASE_INSENSITIVE).matcher(plano);
        if (m.find()) {
            extras.put("garantia_produccion", m.group(1));
            extras.put("garantia_potencia", m.group(2));
        }

        // Tipo de estructura: la X marcada sobre una de las opciones.
        List<Integer> deEstructura = paginasDeEstructura(paginas);
        if (!deEstructura.isEmpty()) {
            boolean encontrada = false;
            for (int n : deEstructura) {
                String[] r = estructuraPorPosicion(disposicion == null ? List.of() : disposicion.getOrDefault(n, List.of()));
                if (r != null) {
                    campos.put("estructura", hallado(r[0], "Estructura marcada con una X (pág. " + (n + 1) + "): " + r[1] + " → " + r[0], "alta"));
                    encontrada = true;
                    break;
                }
            }
            if (!encontrada) {
                avisos.add("El tipo de estructura va marcado con una X en el presupuesto, pero no se ha podido determinar cuál: indícalo a mano.");
            }
        }

        // Sin ningún dato reconocible no es un presupuesto (o es un PDF escaneado).
        if (campos.isEmpty() && proyecto.isEmpty()) return null;

        List<String> faltan = new ArrayList<>();
        for (String k : List.of("importe_total", "potencia_kwp", "paneles_uds", "paneles_modelo", "inversor_uds", "inversor_modelo", "estructura")) {
            if (!campos.containsKey(k)) faltan.add(k);
        }
        if (!campos.containsKey("bateria") && !Boolean.FALSE.equals(bateriasDeclaradas)) faltan.add("bateria");

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("fields", campos);
        salida.put("project", proyecto);
        salida.put("missing", faltan);
        salida.put("notes", avisos);
        salida.put("client", cliente);
        salida.put("extras", extras);
        return salida;
    }

    private static String en(List<String> lineas, int indice) {
        return indice >= 0 && indice < lineas.size() ? lineas.get(indice) : null;
    }

    /** Nombre de archivo sin rutas ni caracteres problemáticos. */
    public static String nombreSeguro(String nombre) {
        String[] trozos = (nombre == null ? "" : nombre.strip()).split("[\\\\/]", -1);
        String base = trozos[trozos.length - 1];
        String limpio = base.replaceAll("(?U)[^\\w.\\- ()áéíóúüñÁÉÍÓÚÜÑ]", "_").replaceAll("^[ .]+|[ .]+$", "");
        if (limpio.length() > 120) limpio = limpio.substring(0, 120);
        return limpio.isEmpty() ? "archivo" : limpio;
    }

    /**
     * Lee el presupuesto de la oferta (PDF) y devuelve los datos que se pueden leer. No guarda nada: el formulario los usa para rellenar el
     * contrato inicial y quien crea el proyecto los revisa antes de aceptarlos. Sin texto que leer (p. ej. un escaneo) devuelve kind «scan».
     */
    public Map<String, Object> analizar(byte[] datos, String nombre) {
        List<String> paginas = leerPaginas(datos);
        // Para leer la X que marca la estructura hace falta saber dónde está cada texto de esas páginas
        Map<Integer, List<Trozo>> disposicion = leerTrozos(datos, paginasDeEstructura(paginas));
        Map<String, Object> presupuesto = extraer(paginas, disposicion);
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("filename", nombreSeguro(nombre));
        if (presupuesto == null) {
            salida.put("kind", "scan");
            salida.put("fields", new LinkedHashMap<>());
            salida.put("project", new LinkedHashMap<>());
            salida.put("missing", List.of());
            salida.put("notes", List.of());
            salida.put("client", new LinkedHashMap<>());
            salida.put("extras", new LinkedHashMap<>());
        } else {
            salida.put("kind", "budget");
            salida.putAll(presupuesto);
        }
        return salida;
    }
}
