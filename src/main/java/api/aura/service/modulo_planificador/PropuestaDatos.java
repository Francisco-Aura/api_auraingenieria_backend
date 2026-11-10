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

    private static final Map<String, String> ESTRUCTURA_POR_CUBIERTA = Map.of("teja", "coplanar", "chapa", "coplanar", "plana", "lastrada", "suelo", "hincada");
    private static final Map<String, String> SUMINISTRO = Map.of("monofasico", "Monofásico", "trifasico", "Trifásico");
    private static final Pattern NUMERO = Pattern.compile("\\d+(?:[.,]\\d+)?");

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
        salida.put("structure", ESTRUCTURA_POR_CUBIERTA.getOrDefault(String.valueOf(encuesta.getOrDefault("roof", "")), ""));
        salida.put("total", b.getTotalPresupuesto());
        return salida;
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
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("panel_years", numero(mod.get("garantia_anos")));
        g.put("panel_prod_years", numero(mod.get("garantia_prod_anos")));
        g.put("panel_prod_pct", numero(mod.get("garantia_prod_pct")));
        g.put("inverter_years", numero(inv.get("garantia_anos")));
        g.put("battery_years", numero(bat.get("garantia_anos")));
        g.put("battery_cycles", numero(bat.get("ciclos")));
        g.put("install_years", 2); // la que da Aura, no depende de ningún equipo
        return g;
    }

    /** «4 strings de 20 paneles» o «2 strings: 20 + 5 paneles»; vacío si el presupuesto no tiene los strings repartidos. */
    static String resumenDeStrings(Object crudo) {
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

    /** Todo lo que la presentación HTML puede tomar del presupuesto. */
    public static Map<String, Object> paraHtml(pl_Presupuesto b, Map<String, pl_Equipo> equipos) {
        Map<String, Object> salida = paraPptx(b, equipos);
        Map<String, Object> datos = b.getDatos();
        Map<String, Object> encuesta = mapa(datos.get("survey"));
        Object referencia = datos.get("reference");
        salida.put("project", referencia == null ? "" : String.valueOf(referencia).strip());
        salida.put("supply", SUMINISTRO.getOrDefault(String.valueOf(encuesta.getOrDefault("supply", "")), ""));
        salida.put("strings", resumenDeStrings(datos.get("strings")));
        salida.put("warranty", garantias(b, equipos));
        // Si el presupuesto lleva una estructura de marquesina, esa es la que se marca (la cubierta solo da la habitual)
        String tipo = marquesina(b, equipos);
        if (!tipo.isEmpty()) salida.put("structure", tipo);
        // Valores sin dato: en blanco, no un guion (la plantilla decide qué enseñar)
        for (String clave : List.of("panel", "inverter")) {
            if ("—".equals(salida.get(clave))) salida.put(clave, "");
        }
        if ("".equals(salida.get("inverter"))) salida.put("inverter_count", 0); // sin inversor elegido no se inventa uno
        return salida;
    }
}
