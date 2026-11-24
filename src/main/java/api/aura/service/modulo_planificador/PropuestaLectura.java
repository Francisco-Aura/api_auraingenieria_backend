package api.aura.service.modulo_planificador;

import api.aura.service.modulo_planificador.OfertaPdf.Trozo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lectura de la propuesta de Aura en PDF (la presentación que se descarga de la propuesta o del presupuestador): saca los datos para
 * crear el proyecto —cliente, nombre, importe, potencia, módulos, inversor, batería, estructura y forma de pago—.
 *
 * <p>La propuesta es la misma presentación para todos los clientes: sus diapositivas, sus etiquetas y su orden son conocidos, y los datos
 * del cliente van en sitios fijos. Por eso cada dato se lee de su diapositiva (que se reconoce por su título) y, en la página técnica,
 * por su posición: cada valor está justo debajo de su etiqueta. Lo que la propuesta no lleva (porque el presupuesto no tenía equipos, por
 * ejemplo) no se inventa: queda en {@code missing}. La respuesta tiene la misma forma que la de {@link OfertaPdf} para que el formulario
 * de proyecto nuevo la trate igual, más la forma de pago ({@code payment_plan}).
 */
@Component
@RequiredArgsConstructor
public class PropuestaLectura {

    private static final int U = Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNICODE_CASE;
    private static final Pattern ESPACIOS = Pattern.compile("\\s+", U);
    private static final Pattern IMPORTE = Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:\\.\\d{3})+|\\d+),(\\d{2})\\s*€", U);
    private static final Pattern PORCENTAJE = Pattern.compile("^(\\d{1,3}(?:,\\d{1,2})?)\\s*%$", U);
    private static final Pattern KWP = Pattern.compile("(\\d{1,4}(?:\\.\\d{3})*(?:,\\d{1,3})?)\\s*kWp", U);
    private static final Pattern ENTERO = Pattern.compile("^\\d{1,4}$");
    private static final Pattern UNIDADES = Pattern.compile("^(.*?)\\s*·\\s*(\\d{1,3})\\s+unidad(?:es)?$", U);
    private static final String SEPARADOR_NOMBRE = " · ";

    /** Las etiquetas de las cuatro cifras grandes de la página técnica (la cifra está encima) y de sus fichas (el valor está debajo). */
    private static final List<String> ETIQUETAS_CIFRAS = List.of("Potencia pico total", "Módulos fotovoltaicos", "Potencia por módulo", "Inversores");
    private static final List<String> ETIQUETAS_FICHAS = List.of("Tipo de instalación", "Módulo", "Inversor", "Baterías", "Estructura");
    /** Tolerancia (en puntos) para decir que dos textos están alineados a la izquierda en la misma tarjeta. */
    private static final double ALINEADO = 6;

    private static final List<List<Object>> PAGO_HABITUAL = List.of(
            List.of("A la firma del contrato", "Reserva del material", 30.0),
            List.of("A la recepción del material", "Inicio de obra", 40.0),
            List.of("A la colocación de los paneles", "Avance de obra", 20.0),
            List.of("A la puesta en marcha", "Al acabar la instalación", 10.0));

    private final OfertaPdf pdf;

    // ----------------------------------------------------------------------------------- utilidades

    private static String junto(String t) {
        return ESPACIOS.matcher(t.strip()).replaceAll(" ");
    }

    private static Map<String, Object> hallado(String valor, String fuente) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", valor);
        m.put("source", fuente);
        m.put("confidence", "alta");
        return m;
    }

    private static List<String> lineas(String pagina) {
        List<String> salida = new ArrayList<>();
        for (String cruda : pagina.split("\\R")) {
            if (!cruda.isBlank()) salida.add(junto(cruda));
        }
        return salida;
    }

    /** El índice de la primera página cuyo texto cumple el patrón, o -1. */
    private static int paginaCon(List<String> paginas, String patron) {
        Pattern p = Pattern.compile(patron, U | Pattern.CASE_INSENSITIVE);
        for (int i = 0; i < paginas.size(); i++) {
            if (p.matcher(paginas.get(i)).find()) return i;
        }
        return -1;
    }

    /** «112.400,00» → 112400.0; «150,00» → 150.0 (formato español: punto de millares, coma decimal). */
    static double numeroEs(String t) {
        return Double.parseDouble(t.replace(".", "").replace(',', '.'));
    }

    // -------------------------------------------------------------------------------------- lectura

    /** Lee la propuesta (PDF). Sin texto (un escaneo) devuelve kind «scan»; un PDF con texto que no es una propuesta de Aura, error. */
    public Map<String, Object> analizar(byte[] datos, String nombre) {
        List<String> paginas = pdf.leerPaginas(datos);
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("filename", OfertaPdf.nombreSeguro(nombre));
        if (paginas.stream().allMatch(String::isBlank)) {
            salida.put("kind", "scan");
            salida.put("fields", new LinkedHashMap<>());
            salida.put("project", new LinkedHashMap<>());
            salida.put("missing", List.of());
            salida.put("notes", List.of());
            salida.put("client", new LinkedHashMap<>());
            salida.put("extras", new LinkedHashMap<>());
            return salida;
        }
        int tecnica = paginaCon(paginas, "Especificaciones\\s+t[eé]cnicas\\s+de\\s+tu\\s+proyecto");
        Map<Integer, List<Trozo>> disposicion = tecnica < 0 ? Map.of() : pdf.leerTrozos(datos, List.of(tecnica));
        Map<String, Object> propuesta = extraer(paginas, disposicion);
        if (propuesta == null) {
            throw new OfertaPdf.DocumentoNoSoportado("Este PDF no parece una propuesta de Aura. Sube la propuesta que se descarga desde el presupuestador "
                    + "(«Descargar presentación»), no el presupuesto ni otro documento.");
        }
        salida.put("kind", "proposal");
        salida.putAll(propuesta);
        return salida;
    }

    /** Los datos de la propuesta (campos del contrato, proyecto, cliente, forma de pago, avisos…); null si el texto no es una propuesta de Aura. */
    public static Map<String, Object> extraer(List<String> paginas, Map<Integer, List<Trozo>> disposicion) {
        int portada = paginaCon(paginas, "Nombre\\s+de\\s+la\\s+empresa\\s*:");
        int tecnica = paginaCon(paginas, "Especificaciones\\s+t[eé]cnicas\\s+de\\s+tu\\s+proyecto");
        int economica = paginaCon(paginas, "Importe\\s+total");
        int firma = paginaCon(paginas, "Por\\s+parte\\s+del\\s+cliente");
        // La diapositiva técnica la tiene toda propuesta de Aura (el presupuesto de oferta antiguo también tiene «Nombre de la empresa», pero no esta)
        if (tecnica < 0) return null;

        Map<String, Map<String, Object>> campos = new LinkedHashMap<>();
        Map<String, Map<String, Object>> proyecto = new LinkedHashMap<>();
        Map<String, String> cliente = new LinkedHashMap<>();
        List<String> avisos = new ArrayList<>();

        // ---- Portada: «Nombre de la empresa: Cliente · Proyecto», «CIF: …» y «Dirección: …» (estos dos solo si se conocen)
        String nombreCompleto = null;
        if (portada >= 0) {
            List<String> ls = lineas(paginas.get(portada));
            for (int i = 0; i < ls.size(); i++) {
                Matcher m = Pattern.compile("^Nombre\\s+de\\s+la\\s+empresa\\s*:\\s*(.*)$", U | Pattern.CASE_INSENSITIVE).matcher(ls.get(i));
                if (!m.find()) continue;
                StringBuilder nombre = new StringBuilder(m.group(1));
                // El nombre puede ocupar varias líneas: sigue hasta el CIF, la dirección o la web
                for (int j = i + 1; j < ls.size() && !ls.get(j).matches("(?iU)^(CIF\\s*:|Direcci[oó]n\\s*:|auraingenieria\\.es).*"); j++) {
                    nombre.append(' ').append(ls.get(j));
                }
                nombreCompleto = junto(nombre.toString());
                break;
            }
            Matcher cif = Pattern.compile("(?m)^\\s*CIF\\s*:\\s*(.+)$", U | Pattern.CASE_INSENSITIVE).matcher(paginas.get(portada));
            if (cif.find()) cliente.put("cif", junto(cif.group(1)));
            Matcher dir = Pattern.compile("(?m)^\\s*Direcci[oó]n\\s*:\\s*(.+)$", U | Pattern.CASE_INSENSITIVE).matcher(paginas.get(portada));
            if (dir.find()) cliente.put("address", junto(dir.group(1)));
        }
        // El cliente tal cual (sin el proyecto) está en la página de firma: «Por parte del cliente» y su nombre
        String nombreCliente = null;
        if (firma >= 0) {
            List<String> ls = lineas(paginas.get(firma));
            int i = ls.indexOf("Por parte del cliente");
            StringBuilder sb = new StringBuilder();
            for (int j = i + 1; i >= 0 && j < ls.size() && !ls.get(j).toLowerCase().startsWith("firma de la empresa"); j++) {
                sb.append(' ').append(ls.get(j));
            }
            if (!junto(sb.toString()).isEmpty()) nombreCliente = junto(sb.toString());
        }
        if (nombreCliente == null && nombreCompleto != null) {
            int corte = nombreCompleto.indexOf(SEPARADOR_NOMBRE);
            nombreCliente = corte > 0 ? nombreCompleto.substring(0, corte) : nombreCompleto;
        }
        if (nombreCliente != null) cliente.put("name", nombreCliente);
        if (nombreCompleto != null) {
            String fuente = "Portada (pág. " + (portada + 1) + "): Nombre de la empresa: " + nombreCompleto;
            proyecto.put("name", hallado(nombreCompleto, fuente));
            String empresa = nombreCliente == null ? null : OfertaPdf.limpiarEmpresa(nombreCliente);
            if (empresa != null) proyecto.put("company", hallado(empresa, fuente));
        }

        // ---- Página técnica: cifras (el valor está encima de su etiqueta) y fichas (el valor está debajo)
        boolean bateriaDeclarada = false;
        if (tecnica >= 0) {
            List<Trozo> trozos = disposicion.getOrDefault(tecnica, List.of());
            String fuente = "Propuesta técnica (pág. " + (tecnica + 1) + ")";
            String kwp = cifra(trozos, "Potencia pico total");
            Matcher mk = kwp == null ? null : KWP.matcher(kwp);
            if (mk != null && mk.find()) {
                campos.put("potencia_kwp", hallado(OfertaPdf.formatoNumero(numeroEs(mk.group(1))), fuente + ": Potencia pico total · " + kwp));
            }
            String paneles = cifra(trozos, "Módulos fotovoltaicos");
            if (paneles != null && ENTERO.matcher(paneles).matches()) {
                campos.put("paneles_uds", hallado(paneles, fuente + ": Módulos fotovoltaicos · " + paneles));
            }
            String wp = cifra(trozos, "Potencia por módulo");

            String modulo = ficha(trozos, "Módulo");
            if (modulo != null) campos.put("paneles_modelo", hallado(modulo, fuente + ": Módulo · " + modulo));
            else avisos.add("La propuesta no indica el modelo del módulo" + (wp == null ? "" : " (solo " + wp + " por módulo)") + ": complétalo a mano.");

            String inversor = ficha(trozos, "Inversor");
            String unidades = cifra(trozos, "Inversores");
            if (inversor != null) {
                Matcher mu = UNIDADES.matcher(inversor);
                if (mu.find()) {
                    inversor = mu.group(1).strip();
                    unidades = mu.group(2);
                }
                if (!inversor.isEmpty()) campos.put("inversor_modelo", hallado(inversor, fuente + ": Inversor · " + ficha(trozos, "Inversor")));
            } else {
                avisos.add("La propuesta no indica el inversor: complétalo a mano.");
            }
            if (unidades != null && ENTERO.matcher(unidades).matches() && !unidades.equals("0")) {
                campos.put("inversor_uds", hallado(unidades, fuente + ": Inversores · " + unidades));
            }

            String bateria = ficha(trozos, "Baterías");
            if (bateria != null) {
                if (bateria.toLowerCase().matches("(?iU)^(no incluidas?|sin bater[ií]as?|no)$")) {
                    bateriaDeclarada = true;
                    avisos.add("La propuesta indica que no lleva baterías.");
                } else {
                    bateriaDeclarada = true;
                    campos.put("bateria", hallado(bateria, fuente + ": Baterías · " + bateria));
                }
            }

            String estructura = ficha(trozos, "Estructura");
            if (estructura != null) {
                campos.put("estructura", hallado(estructura.replace(" · ", ", "), fuente + ": Estructura · " + estructura));
            }
        }

        // ---- Propuesta económica: importe total (sin IVA) y forma de pago
        Map<String, Object> pago = null;
        if (economica >= 0) {
            List<String> ls = lineas(paginas.get(economica));
            int total = ls.indexOf("Importe total");
            Double importe = null;
            String textoImporte = null;
            for (int i = total + 1; total >= 0 && i < ls.size() && !ls.get(i).toLowerCase().startsWith("forma de pago"); i++) {
                Matcher m = IMPORTE.matcher(ls.get(i));
                if (m.find()) {
                    importe = numeroEs(m.group(1) + "," + m.group(2));
                    textoImporte = junto(m.group(0));
                    break;
                }
            }
            if (importe != null && importe > 0) {
                campos.put("importe_total", hallado(OfertaPdf.formatoNumero(importe), "Propuesta económica (pág. " + (economica + 1) + "): Importe total · " + textoImporte));
                avisos.add("El importe de la propuesta va sin IVA.");
            }
            pago = formaDePago(ls, importe, avisos, economica + 1);
        }

        // ---- Garantías del módulo (van al contrato): «12 años de garantía al producto», «30 años de garantía de producción al 87,4 % …»
        Map<String, String> extras = new LinkedHashMap<>();
        int garantias = paginaCon(paginas, "Garant[ií]as\\s+de\\s+la\\s+instalaci[oó]n");
        if (garantias >= 0) {
            String plano = junto(paginas.get(garantias));
            Matcher m = Pattern.compile("(\\d{1,2})\\s*a[ñn]os?\\s+de\\s+garant[ií]a\\s+al\\s+producto", U | Pattern.CASE_INSENSITIVE).matcher(plano);
            if (m.find()) extras.put("garantia_producto", m.group(1));
            m = Pattern.compile("(\\d{1,2})\\s*a[ñn]os?\\s+de\\s+garant[ií]a\\s+de\\s+producci[oó]n\\s+al\\s+(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*%", U | Pattern.CASE_INSENSITIVE).matcher(plano);
            if (m.find()) {
                extras.put("garantia_produccion", m.group(1));
                extras.put("garantia_potencia", m.group(2));
            }
        }

        List<String> faltan = new ArrayList<>();
        for (String k : List.of("importe_total", "potencia_kwp", "paneles_uds", "paneles_modelo", "inversor_uds", "inversor_modelo", "estructura")) {
            if (!campos.containsKey(k)) faltan.add(k);
        }
        if (!campos.containsKey("bateria") && !bateriaDeclarada) faltan.add("bateria");

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("fields", campos);
        salida.put("project", proyecto);
        salida.put("missing", faltan);
        salida.put("notes", avisos);
        salida.put("client", cliente);
        salida.put("extras", extras);
        if (pago != null) salida.put("payment_plan", pago);
        return salida;
    }

    // ----------------------------------------------------------------------------- página técnica

    private static boolean esEtiqueta(Trozo t) {
        String texto = junto(t.texto());
        return ETIQUETAS_CIFRAS.contains(texto) || ETIQUETAS_FICHAS.contains(texto);
    }

    private static Trozo etiqueta(List<Trozo> trozos, String texto) {
        return trozos.stream().filter(t -> junto(t.texto()).equals(texto)).findFirst().orElse(null);
    }

    /** La cifra grande de una de las cuatro tarjetas de arriba: el texto más grande justo encima de su etiqueta, alineado con ella. */
    static String cifra(List<Trozo> trozos, String texto) {
        Trozo e = etiqueta(trozos, texto);
        if (e == null) return null;
        return trozos.stream()
                .filter(t -> Math.abs(t.x() - e.x()) < ALINEADO && t.y() > e.y() && t.tamano() > e.tamano() * 1.5)
                .min(Comparator.comparingDouble(t -> t.y() - e.y()))
                .map(t -> junto(t.texto())).orElse(null);
    }

    /**
     * El valor de una ficha (debajo de su etiqueta, en negrita y más grande que ella): una o varias líneas alineadas con la etiqueta, hasta la
     * siguiente etiqueta o el texto pequeño de detalle. Null si la ficha está en blanco (la propuesta no tenía ese dato).
     */
    static String ficha(List<Trozo> trozos, String texto) {
        Trozo e = etiqueta(trozos, texto);
        if (e == null) return null;
        List<Trozo> columna = new ArrayList<>(trozos.stream().filter(t -> Math.abs(t.x() - e.x()) < ALINEADO && t.y() < e.y()).toList());
        columna.sort(Comparator.comparingDouble(t -> -t.y())); // de arriba abajo
        StringBuilder valor = new StringBuilder();
        for (Trozo t : columna) {
            if (esEtiqueta(t) || t.tamano() < e.tamano() * 1.25) break;
            valor.append(valor.length() == 0 ? "" : " ").append(junto(t.texto()));
        }
        return valor.length() == 0 ? null : valor.toString();
    }

    // --------------------------------------------------------------------------------- forma de pago

    /**
     * La forma de pago: cuatro tarjetas con su porcentaje, a qué corresponde, la nota y el importe de cada una. Devuelve el plan con el formato
     * del proyecto ({@code mode} y {@code stages}); null si no se reconoce o los porcentajes no suman 100.
     */
    private static Map<String, Object> formaDePago(List<String> ls, Double total, List<String> avisos, int pagina) {
        int desde = -1;
        for (int i = 0; i < ls.size(); i++) {
            if (ls.get(i).toLowerCase().startsWith("forma de pago")) desde = i;
        }
        if (desde < 0) return null;
        List<Map<String, Object>> etapas = new ArrayList<>();
        for (int i = desde + 1; i < ls.size(); i++) {
            Matcher p = PORCENTAJE.matcher(ls.get(i));
            if (!p.find()) continue;
            String etiqueta = i + 1 < ls.size() ? ls.get(i + 1) : "";
            String nota = i + 2 < ls.size() && !IMPORTE.matcher(ls.get(i + 2)).find() && !PORCENTAJE.matcher(ls.get(i + 2)).find() ? ls.get(i + 2) : "";
            if (etiqueta.isEmpty() || PORCENTAJE.matcher(etiqueta).find()) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("label", etiqueta);
            e.put("note", nota);
            e.put("percent", numeroEs(p.group(1)));
            etapas.add(e);
        }
        double suma = etapas.stream().mapToDouble(e -> (double) e.get("percent")).sum();
        if (etapas.isEmpty() || Math.abs(suma - 100) > 0.01) {
            if (!etapas.isEmpty()) avisos.add("Los porcentajes de la forma de pago de la propuesta suman " + OfertaPdf.formatoNumero(suma) + " %, no 100: indícala a mano.");
            return null;
        }
        boolean habitual = etapas.size() == PAGO_HABITUAL.size();
        for (int i = 0; habitual && i < etapas.size(); i++) {
            habitual = etapas.get(i).get("label").equals(PAGO_HABITUAL.get(i).get(0)) && etapas.get(i).get("percent").equals(PAGO_HABITUAL.get(i).get(2));
        }
        StringJoiner partes = new StringJoiner(" / ");
        etapas.forEach(e -> partes.add(OfertaPdf.formatoNumero((double) e.get("percent"))));
        if (habitual) {
            avisos.add("Forma de pago: " + partes + " %, como en el contrato modelo.");
        } else {
            avisos.add("Forma de pago distinta de la habitual (30/40/20/10): " + partes + " % (pág. " + pagina + ").");
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("mode", habitual ? "standard" : "custom");
        plan.put("stages", etapas);
        return plan;
    }
}
