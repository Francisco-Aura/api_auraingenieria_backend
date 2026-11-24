package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.entity.modulo_planificador.pl_Presupuesto;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Los datos de la propuesta al cliente tomados de un presupuesto validado y del catálogo (equivale a `proposal_data` de proposal_pptx.py y
 * `proposal_html_data` de proposal_html.py). Lo que no se sabe va vacío.
 */
public final class PropuestaDatos {

    private static final Map<String, String> ESTRUCTURA_POR_CUBIERTA = Map.ofEntries(
            Map.entry("teja", "coplanar"), Map.entry("chapa", "coplanar"), Map.entry("chapa_minionda", "coplanar"),
            Map.entry("chapa_grecada", "coplanar"), Map.entry("sandwich", "coplanar"), Map.entry("plana", "lastrada"),
            Map.entry("suelo", "hincada"), Map.entry("parking", "marquesina"), Map.entry("pergola", "marquesina"),
            Map.entry("flotante", "marquesina_flotante"));
    private static final Map<String, String> SUMINISTRO = Map.of("monofasico", "Monofásico", "trifasico", "Trifásico");
    // Años de garantía cuando el inventario no la trae, en el orden de las tarjetas de la propuesta
    static final int GARANTIA_MODULO_POR_DEFECTO = 30;
    static final int GARANTIA_INVERSOR_POR_DEFECTO = 5;
    static final int GARANTIA_INSTALACION = 2;
    static final int GARANTIA_BATERIA_POR_DEFECTO = 10;
    private static final Pattern NUMERO =Pattern.compile("\\d+(?:[.,]\\d+)?");

    private PropuestaDatos() {
    }

    private static Map<String, Object> mapa(Object v) {
        @SuppressWarnings("unchecked") Map<String, Object> m = v instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
        return m;
    }

    private static List<Map<String, Object>> lineas(pl_Presupuesto b) {
        List<Map<String, Object>> salida = new ArrayList<>();
        if (b.getDatos().get("lines") instanceof List<?> l) {
            for (Object o : l) if (o instanceof Map<?, ?>) salida.add(mapa(o));
        }
        return salida;
    }

    private static double aDouble(Object v) {
        try {
            return v == null || "".equals(v) || Boolean.FALSE.equals(v) ? 0.0 : v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** El número de un campo del inventario escrito a mano («12», «87,4», «12 años»); 0 si no hay. */
    static double numero(Object valor) {
        Matcher m = NUMERO.matcher(valor == null ? "" : String.valueOf(valor));
        return m.find() ? Double.parseDouble(m.group().replace(",", ".")) : 0.0;
    }

    private record Par(String nombre, double cantidad) {
    }

    /** Los datos de la propuesta para el PowerPoint. */
    public static Map<String, Object> paraPptx(pl_Presupuesto b, Map<String, pl_Equipo> equipos) {
        Map<String, Object> datos = b.getDatos();
        Map<String, Object> cliente = mapa(datos.get("customer"));
        Map<String, Object> encuesta = mapa(datos.get("survey"));
        Map<String, List<Par>> porCategoria = new LinkedHashMap<>();
        for (Map<String, Object> linea : lineas(b)) {
            Object itemId = linea.get("itemId");
            pl_Equipo item = itemId == null ? null : equipos.get(String.valueOf(itemId));
            if (item == null) continue;
            // Muchos modelos ya empiezan por la marca («AIKO 615»): no se repite
            String marca = item.getModelo().toLowerCase().startsWith(item.getMarca().toLowerCase()) ? "" : item.getMarca();
            String nombre = (marca + " " + item.getModelo()).strip();
            if (nombre.isEmpty()) nombre = linea.get("model") == null ? "" : String.valueOf(linea.get("model"));
            porCategoria.computeIfAbsent(item.getCategoria(), k -> new ArrayList<>()).add(new Par(nombre, aDouble(linea.get("qty"))));
        }
        String panel = porCategoria.containsKey("modulos") ? porCategoria.get("modulos").get(0).nombre() : "";
        List<Par> inversores = porCategoria.getOrDefault("inversores", List.of());
        List<Par> baterias = porCategoria.getOrDefault("baterias", List.of());
        String nombreCliente = cliente.get("name") != null && !"".equals(cliente.get("name")) ? String.valueOf(cliente.get("name")) : b.getNombre();
        int cuantosInversores = (int) inversores.stream().mapToDouble(Par::cantidad).sum();
        StringJoiner inv = new StringJoiner(" + ");
        inversores.forEach(p -> inv.add(p.nombre()));
        StringJoiner bat = new StringJoiner(", ");
        baterias.forEach(p -> bat.add(p.nombre() + " × " + (int) p.cantidad()));

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("client", nombreCliente == null ? "" : nombreCliente);
        salida.put("address", ""); // la intranet no la da al planificador: se rellena a mano en el PowerPoint si hace falta
        salida.put("kind", "Autoconsumo");
        salida.put("kwp", b.getKwp());
        salida.put("panels", b.getPaneles());
        salida.put("panel", panel.isEmpty() ? "—" : panel);
        salida.put("inverter", inv.length() == 0 ? "—" : inv.toString());
        salida.put("inverter_count", cuantosInversores == 0 ? 1 : cuantosInversores);
        salida.put("battery", bat.toString());
        ponerEstructuras(salida, estructurasDeCubiertas(encuesta));
        salida.put("total", b.getTotalPresupuesto());
        return salida;
    }

    /**
     * La estructura habitual de cada cubierta del presupuesto (puede tener varias: nave con teja y plana), sin repetir y en su orden.
     * Un presupuesto anterior solo trae `roof`. Sin cubierta conocida, lista vacía.
     */
    private static List<String> estructurasDeCubiertas(Map<String, Object> encuesta) {
        List<String> cubiertas = new ArrayList<>();
        if (encuesta.get("roofs") instanceof List<?> l) {
            for (Object o : l) cubiertas.add(String.valueOf(o));
        }
        if (cubiertas.isEmpty()) cubiertas.add(String.valueOf(encuesta.getOrDefault("roof", "")));
        List<String> salida = new ArrayList<>();
        Map<String, Object> disposiciones = mapa(encuesta.get("roofLayouts"));
        for (String c : cubiertas) {
            String estructura = ESTRUCTURA_POR_CUBIERTA.get(c);
            // Una cubierta de estructura coplanar a la que se ha elegido inclinada (chapa minionda) lleva estructura inclinada
            if ("coplanar".equals(estructura) && "inclinada".equals(disposiciones.get(c))) estructura = "inclinada";
            if (estructura != null && !salida.contains(estructura)) salida.add(estructura);
        }
        return salida;
    }

    private static final Map<String, String> NOMBRE_CUBIERTA = Map.ofEntries(
            Map.entry("teja", "Teja"), Map.entry("chapa", "Chapa"), Map.entry("chapa_minionda", "Chapa minionda"),
            Map.entry("chapa_grecada", "Chapa grecada"), Map.entry("sandwich", "Panel sándwich"), Map.entry("plana", "Plana"),
            Map.entry("suelo", "Suelo"), Map.entry("parking", "Parking"), Map.entry("pergola", "Pérgola"), Map.entry("flotante", "Flotante"));
    private static final Map<String, String> NOMBRE_DISPOSICION = Map.of("coplanar", "Coplanar", "inclinada", "Inclinada");

    /**
     * Cada cubierta del presupuesto con su disposición (coplanar o inclinada) y los paneles que lleva: [{name, layout, panels}]. Los paneles
     * solo van con varias cubiertas y un reparto completo (enteros que suman los paneles del presupuesto); lo que no se sabe va vacío o 0.
     * Lista vacía si no hay nada que enseñar (un presupuesto anterior, sin disposición ni reparto).
     */
    static List<Map<String, Object>> detalleDeCubiertas(Map<String, Object> encuesta, int paneles) {
        List<String> cubiertas = new ArrayList<>();
        if (encuesta.get("roofs") instanceof List<?> l) {
            for (Object o : l) if (!cubiertas.contains(String.valueOf(o))) cubiertas.add(String.valueOf(o));
        }
        if (cubiertas.isEmpty() && encuesta.get("roof") != null) cubiertas.add(String.valueOf(encuesta.get("roof")));
        Map<String, Object> disposiciones = mapa(encuesta.get("roofLayouts"));
        Map<String, Object> reparto = mapa(encuesta.get("roofPanels"));
        long suma = 0;
        boolean completo = cubiertas.size() > 1;
        for (String c : cubiertas) {
            if (reparto.get(c) instanceof Number n && n.doubleValue() >= 0 && n.doubleValue() == Math.rint(n.doubleValue())) suma += n.longValue();
            else completo = false;
        }
        completo = completo && suma == paneles;
        List<Map<String, Object>> salida = new ArrayList<>();
        boolean algo = false;
        for (String c : cubiertas) {
            Map<String, Object> fila = new LinkedHashMap<>();
            String disposicion = NOMBRE_DISPOSICION.getOrDefault(String.valueOf(disposiciones.get(c)), "");
            int cuantos = completo ? ((Number) reparto.get(c)).intValue() : 0;
            fila.put("name", NOMBRE_CUBIERTA.getOrDefault(c, c));
            fila.put("layout", disposicion);
            fila.put("panels", cuantos);
            algo |= !disposicion.isEmpty() || cuantos > 0;
            salida.add(fila);
        }
        return algo ? salida : List.of();
    }

    /**
     * `structure` = la primera estructura (como siempre: lo que lee quien solo conoce una); `structures` = todas, y solo va si hay más de
     * una, para que la propuesta de una sola cubierta quede exactamente igual que antes.
     */
    private static void ponerEstructuras(Map<String, Object> salida, List<String> estructuras) {
        salida.put("structure", estructuras.isEmpty() ? "" : estructuras.get(0));
        salida.remove("structures");
        if (estructuras.size() > 1) salida.put("structures", List.copyOf(estructuras));
    }

    /** «marquesina_flotante» o «marquesina» si alguna estructura del presupuesto lo es (por su nombre o su montaje); si no, vacío. */
    private static String marquesina(pl_Presupuesto b, Map<String, pl_Equipo> equipos) {
        String encontrada = "";
        for (Map<String, Object> linea : lineas(b)) {
            Object itemId = linea.get("itemId");
            pl_Equipo item = itemId == null ? null : equipos.get(String.valueOf(itemId));
            if (item == null || !"estructura".equals(item.getCategoria())) continue;
            String texto = (item.getModelo() + " " + item.getAtributos().getOrDefault("montaje", "")).toLowerCase();
            if (texto.contains("marquesina")) {
                if (texto.contains("flotante")) return "marquesina_flotante";
                encontrada = "marquesina";
            }
        }
        return encontrada;
    }

    /** Las garantías de los equipos del presupuesto, tal como están en el inventario (0 = sin dato). */
    private static Map<String, Object> garantias(pl_Presupuesto b, Map<String, pl_Equipo> equipos) {
        Map<String, Map<String, Object>> primero = new HashMap<>();
        for (Map<String, Object> linea : lineas(b)) {
            Object itemId = linea.get("itemId");
            pl_Equipo item = itemId == null ? null : equipos.get(String.valueOf(itemId));
            if (item != null && Set.of("modulos", "inversores", "baterias").contains(item.getCategoria())) {
                primero.putIfAbsent(item.getCategoria(), item.getAtributos());
            }
        }
        Map<String, Object> mod = primero.getOrDefault("modulos", Map.of());
        Map<String, Object> inv = primero.getOrDefault("inversores", Map.of());
        Map<String, Object> bat = primero.getOrDefault("baterias", Map.of());
        double productoModulo = numero(mod.get("garantia_anos"));
        double produccionModulo = numero(mod.get("garantia_prod_anos"));
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("panel_years", productoModulo);
        // Garantía vacía en el inventario: la habitual (en el orden de la propuesta: módulo 30, inversor 5, instalación 2, batería 10)
        g.put("panel_prod_years", productoModulo <= 0 && produccionModulo <= 0 ? GARANTIA_MODULO_POR_DEFECTO : produccionModulo);
        g.put("panel_prod_pct", numero(mod.get("garantia_prod_pct")));
        double inversor = numero(inv.get("garantia_anos"));
        g.put("inverter_years", inversor <= 0 && primero.containsKey("inversores") ? GARANTIA_INVERSOR_POR_DEFECTO : inversor);
        double bateria = numero(bat.get("garantia_anos"));
        g.put("battery_years", bateria <= 0 && primero.containsKey("baterias") ? GARANTIA_BATERIA_POR_DEFECTO : bateria);
        g.put("battery_cycles", numero(bat.get("ciclos")));
        g.put("install_years", GARANTIA_INSTALACION); // la que da Aura, no depende de ningún equipo
        return g;
    }

    /** «4 strings de 20 paneles» o «2 strings: 20 + 5 paneles»; vacío si el presupuesto no tiene los strings repartidos o no suman sus paneles. */
    public static String resumenDeStrings(Object crudo, int panelesDelPresupuesto) {
        List<Integer> tamanos = new ArrayList<>();
        if (crudo instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    int paneles = (int) aDouble(m.get("panels"));
                    if (paneles > 0) tamanos.add(paneles);
                }
            }
        }
        if (tamanos.isEmpty()) return "";
        // Un reparto que no suma los paneles del presupuesto es de antes de cambiarlos: mejor en blanco que un dato falso
        if (panelesDelPresupuesto > 0 && tamanos.stream().mapToInt(Integer::intValue).sum() != panelesDelPresupuesto) return "";
        if (tamanos.size() == 1) return "1 string de " + tamanos.get(0) + " paneles";
        if (new HashSet<>(tamanos).size() == 1) return tamanos.size() + " strings de " + tamanos.get(0) + " paneles";
        if (tamanos.size() <= 4) {
            StringJoiner sj = new StringJoiner(" + ");
            tamanos.forEach(n -> sj.add(String.valueOf(n)));
            return tamanos.size() + " strings: " + sj + " paneles";
        }
        // Con muchos strings no se listan uno a uno: se agrupan por tamaño («6 de 17 y 14 de 16 paneles»)
        List<Integer> grupos = new ArrayList<>(new TreeSet<>(tamanos));
        Collections.reverse(grupos);
        if (grupos.size() > 3) {
            return tamanos.size() + " strings de " + Collections.min(tamanos) + " a " + Collections.max(tamanos) + " paneles";
        }
        StringJoiner sj = new StringJoiner(" y ");
        for (int g : grupos) sj.add(Collections.frequency(tamanos, g) + " de " + g);
        return tamanos.size() + " strings: " + sj + " paneles";
    }

    private static final Pattern PATRON_LOGO = Pattern.compile("^data:(image/(?:png|jpeg|webp|svg\\+xml));base64,([A-Za-z0-9+/=]+)$");
    private static final int LOGO_MAX_CARACTERES = 2_000_000;

    /** El logo si es una imagen en data URI de tamaño razonable; si no, vacío. */
    public static String logoValido(Object valor) {
        String logo = valor == null ? "" : String.valueOf(valor);
        return logo.length() <= LOGO_MAX_CARACTERES && PATRON_LOGO.matcher(logo).matches() ? logo : "";
    }

    /** Todo lo que la presentación HTML puede tomar del presupuesto. */
    public static Map<String, Object> paraHtml(pl_Presupuesto b, Map<String, pl_Equipo> equipos) {
        Map<String, Object> salida = paraPptx(b, equipos);
        Map<String, Object> datos = b.getDatos();
        Map<String, Object> encuesta = mapa(datos.get("survey"));
        Object referencia = datos.get("reference");
        salida.put("project", referencia == null ? "" : String.valueOf(referencia).strip());
        // CIF y logo de la empresa del cliente (parámetros iniciales del presupuesto): solo van si están, para no dejar claves vacías
        Object cif = datos.get("cif");
        if ((cif == null || String.valueOf(cif).isBlank()) && mapa(datos.get("customer")).get("cif") != null) cif = mapa(datos.get("customer")).get("cif");
        if (cif != null && !String.valueOf(cif).isBlank()) salida.put("cif", String.valueOf(cif).strip());
        String logo = logoValido(datos.get("clientLogo"));
        if (!logo.isEmpty()) salida.put("logo", logo);
        // «Encaje»: imagen con la disposición de los paneles en el proyecto (obligatorio para descargar: lo exige PropuestaService); la propuesta añade su diapositiva
        String encaje = logoValido(datos.get("encaje"));
        if (!encaje.isEmpty()) salida.put("encaje", encaje);
        // Disposición (coplanar / inclinada) y paneles de cada cubierta: solo va si hay algo que enseñar
        List<Map<String, Object>> cubiertas = detalleDeCubiertas(encuesta, b.getPaneles());
        if (!cubiertas.isEmpty()) salida.put("roofs", cubiertas);
        salida.put("supply", SUMINISTRO.getOrDefault(String.valueOf(encuesta.getOrDefault("supply", "")), ""));
        salida.put("strings", resumenDeStrings(datos.get("strings"), b.getPaneles()));
        salida.put("warranty", garantias(b, equipos));
        // Si el presupuesto lleva una estructura de marquesina, esa es la que se marca (la cubierta solo da la habitual)
        // (con varias cubiertas, esa ocupa el lugar de la marquesina de la cubierta que sea; si ninguna lo es y hay más de una, se añade)
        String tipo = marquesina(b, equipos);
        if (!tipo.isEmpty()) {
            List<String> estructuras = new ArrayList<>(estructurasDeCubiertas(encuesta));
            int primera = -1;
            for (int i = estructuras.size() - 1; i >= 0; i--) {
                if (estructuras.get(i).startsWith("marquesina")) {
                    if (primera >= 0) estructuras.remove(primera);
                    primera = i;
                }
            }
            if (primera >= 0) estructuras.set(primera, tipo);
            else if (estructuras.size() <= 1) estructuras = new ArrayList<>(List.of(tipo));
            else estructuras.add(tipo);
            ponerEstructuras(salida, estructuras);
        }
        // Valores sin dato: en blanco, no un guion (la plantilla decide qué enseñar)
        for (String clave : List.of("panel", "inverter")) {
            if ("—".equals(salida.get(clave))) salida.put(clave, "");
        }
        if ("".equals(salida.get("inverter"))) salida.put("inverter_count", 0); // sin inversor elegido no se inventa uno
        return salida;
    }
}
