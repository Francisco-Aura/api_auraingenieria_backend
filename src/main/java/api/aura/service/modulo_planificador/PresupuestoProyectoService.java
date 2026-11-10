package api.aura.service.modulo_planificador;

import org.springframework.stereotype.Service;

import java.util.*;

import static api.aura.service.modulo_planificador.TareaProyectoService.invalido;

/**
 * Presupuesto del proyecto y su variación con los cambios (equivale a budget.py).
 *
 * <p>El presupuesto inicial es una foto de la tabla de costes del presupuestador (líneas con equipo, precio y cantidad) tomada al firmar
 * el contrato. Cada cambio registrado puede llevar cuántas unidades se añaden o se quitan de cada línea (y de módulos). El vigente es
 * siempre el inicial más esas variaciones; nunca se guarda calculado.
 */
@Service
public class PresupuestoProyectoService {

    static final int MAX_LINEAS = 500;
    private static final List<String> TEXTOS_LINEA = List.of("group", "equipment", "model", "supplier", "unit", "notes");

    private static double numero(Object valor, String etiqueta, Double minimo) {
        double n;
        try {
            if (valor instanceof Number x) {
                n = x.doubleValue();
            } else if (valor instanceof String s) {
                n = Double.parseDouble(s.strip().replace(",", "."));
            } else {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw invalido("«" + etiqueta + "» debe ser un número");
        }
        if (!Numeros.finito(n) || (minimo != null && n < minimo)) {
            throw invalido("«" + etiqueta + "» debe ser un número" + (minimo != null && minimo == 0.0 ? " positivo" : ""));
        }
        return n;
    }

    private static String texto(Object v) {
        if (v == null || Boolean.FALSE.equals(v) || "".equals(v)) {
            return "";
        }
        return String.valueOf(v).strip();
    }

    private static Map<String, Object> limpiarLinea(Map<?, ?> crudo) {
        Map<String, Object> linea = new LinkedHashMap<>();
        for (String clave : TEXTOS_LINEA) {
            linea.put(clave, texto(crudo.get(clave)));
        }
        if (((String) linea.get("group")).isEmpty()) {
            throw invalido("Cada línea del presupuesto necesita su partida");
        }
        if (((String) linea.get("unit")).isEmpty()) {
            linea.put("unit", "ud");
        }
        Object itemId = crudo.get("itemId");
        boolean hay = itemId != null && !"".equals(itemId);
        linea.put("itemId", hay ? String.valueOf(itemId) : null);
        linea.put("auto", Boolean.TRUE.equals(crudo.get("auto")));
        linea.put("qty", Numeros.redondear(numero((crudo.get("qty") == null ? (Object) 0 : crudo.get("qty")), "Cantidad", 0.0), 3));
        linea.put("price", Numeros.redondear(numero((crudo.get("price") == null ? (Object) 0 : crudo.get("price")), "Precio", 0.0), 4));
        return linea;
    }

    /** Presupuesto inicial validado; vacío si no hay líneas. */
    public Map<String, Object> normalizar(Map<String, Object> crudo) {
        if (crudo == null || !(crudo.get("lines") instanceof List<?> items) || items.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<Map<String, Object>> lineas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (Object o : items) {
            Map<?, ?> item = (Map<?, ?>) o;
            Map<String, Object> linea = limpiarLinea(item);
            String id = texto(item.get("id"));
            if (id.isEmpty() || !vistos.add(id)) {
                throw invalido("Las líneas del presupuesto necesitan un identificador único");
            }
            Map<String, Object> con = new LinkedHashMap<>();
            con.put("id", id);
            con.putAll(linea);
            lineas.add(con);
        }
        if (lineas.size() > MAX_LINEAS) {
            throw invalido("Como máximo " + MAX_LINEAS + " líneas");
        }
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("client", texto(crudo.get("client")));
        salida.put("moduleWp", numero(crudo.getOrDefault("moduleWp", 0), "Potencia del módulo", 0.0));
        salida.put("moduleCount", (int) numero(crudo.getOrDefault("moduleCount", 0), "Número de módulos", 0.0));
        salida.put("saleRatio", numero(crudo.getOrDefault("saleRatio", 0), "Ratio de venta", 0.0));
        salida.put("lines", lineas);
        return salida;
    }

    /** Lo que un cambio añade o quita del presupuesto (sin comprobar aún contra el presupuesto del proyecto). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> normalizarCambio(Object crudo) {
        if (!(crudo instanceof Map<?, ?> m) || m.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object delta = m.get("module_delta");
        int deltaModulos = (int) numero(delta == null || Boolean.FALSE.equals(delta) || Integer.valueOf(0).equals(delta) ? 0 : delta, "Variación de módulos", null);
        List<Map<String, Object>> lineas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        if (m.get("lines") instanceof List<?> items) {
            for (Object o : items) {
                Map<?, ?> item = (Map<?, ?>) o;
                String id = texto(item.get("id"));
                if (id.isEmpty() || !vistos.add(id)) {
                    throw invalido("Cada línea del cambio necesita un identificador único");
                }
                Object d = item.get("qty_delta");
                double variacion = Numeros.redondear(numero(d == null || Boolean.FALSE.equals(d) ? 0 : d, "Variación de cantidad", null), 3);
                Object nueva = item.get("new");
                boolean hayNueva = nueva instanceof Map<?, ?> nm && !nm.isEmpty();
                if (variacion == 0 && !hayNueva) {
                    continue;
                }
                Map<String, Object> entrada = new LinkedHashMap<>();
                entrada.put("id", id);
                entrada.put("qty_delta", variacion);
                if (hayNueva) {
                    // Una línea que nace en un cambio no sigue al número de módulos: su cantidad es la que se pone
                    Map<String, Object> base = new LinkedHashMap<>((Map<String, Object>) nueva);
                    base.put("qty", 0);
                    base.put("auto", false);
                    entrada.put("new", limpiarLinea(base));
                }
                lineas.add(entrada);
            }
        }
        if (deltaModulos == 0 && lineas.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("module_delta", deltaModulos);
        salida.put("lines", lineas);
        return salida;
    }

    private static double d(Object v) {
        return v instanceof Number n ? n.doubleValue() : v == null ? 0.0 : Double.parseDouble(String.valueOf(v));
    }

    private static Map<String, Object> totales(Map<String, Object> base, int modulos, Map<String, Double> cantidades, List<Map<String, Object>> lineas) {
        double coste = 0.0;
        for (Map<String, Object> linea : lineas) {
            double cantidad = Boolean.TRUE.equals(linea.get("auto")) ? modulos : cantidades.getOrDefault((String) linea.get("id"), 0.0);
            coste += cantidad * d(linea.get("price"));
        }
        double wp = d(base.get("moduleWp")) * modulos;
        double presupuesto = d(base.get("saleRatio")) * wp;
        double beneficio = presupuesto - coste;
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("cost", Numeros.redondear2(coste));
        t.put("wp", Numeros.redondear2(wp));
        t.put("budget", Numeros.redondear2(presupuesto));
        t.put("profit", Numeros.redondear2(beneficio));
        t.put("margin", presupuesto > 0 ? Numeros.redondear(beneficio / presupuesto, 4) : 0.0);
        t.put("module_count", modulos);
        return t;
    }

    /**
     * Líneas con su cantidad inicial, la variación y la vigente, más los totales antes y después de cada cambio. Lanza un 422 si algún
     * cambio deja una cantidad (o el número de módulos) por debajo de cero.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcular(Map<String, Object> base, List<Map<String, Object>> cambios) {
        Map<String, Object> salida = new LinkedHashMap<>();
        if (base == null || !(base.get("lines") instanceof List<?> origen) || origen.isEmpty()) {
            salida.put("configured", false);
            return salida;
        }
        List<Map<String, Object>> lineas = new ArrayList<>();
        for (Object o : origen) {
            lineas.add(new LinkedHashMap<>((Map<String, Object>) o));
        }
        Map<String, Map<String, Object>> porId = new HashMap<>();
        Map<String, Double> cantidadInicial = new HashMap<>();
        Map<String, Double> cantidades = new HashMap<>();
        for (Map<String, Object> l : lineas) {
            porId.put((String) l.get("id"), l);
            cantidadInicial.put((String) l.get("id"), d(l.get("qty")));
            cantidades.put((String) l.get("id"), d(l.get("qty")));
        }
        Set<String> idsBase = new HashSet<>(porId.keySet());
        int modulosBase = (int) d(base.get("moduleCount"));
        int modulos = modulosBase;
        Map<String, Object> inicial = totales(base, modulos, cantidades, lineas);
        Map<String, Object> anterior = inicial;
        List<Map<String, Object>> porCambio = new ArrayList<>();
        Map<String, List<Integer>> tocadas = new HashMap<>();
        List<Integer> cambiosDeModulos = new ArrayList<>();

        int numero = 0;
        for (Map<String, Object> cambio : cambios) {
            numero++;
            Object extraCrudo = cambio.get("budget");
            if (!(extraCrudo instanceof Map<?, ?> extra) || extra.isEmpty()) {
                continue;
            }
            int deltaModulos = (int) d(extra.get("module_delta"));
            if (deltaModulos != 0) {
                cambiosDeModulos.add(numero);
            }
            modulos += deltaModulos;
            if (modulos < 0) {
                throw invalido("El cambio " + numero + " deja el número de módulos por debajo de cero");
            }
            if (extra.get("lines") instanceof List<?> items) {
                for (Object o : items) {
                    Map<String, Object> item = (Map<String, Object>) o;
                    String id = (String) item.get("id");
                    Map<String, Object> linea = porId.get(id);
                    if (linea == null) {
                        if (item.get("new") == null) {
                            throw invalido("El cambio " + numero + " se refiere a una línea que ya no existe");
                        }
                        linea = new LinkedHashMap<>();
                        linea.put("id", id);
                        linea.putAll((Map<String, Object>) item.get("new"));
                        linea.put("qty", 0.0);
                        lineas.add(linea);
                        porId.put(id, linea);
                        cantidadInicial.put(id, 0.0);
                        cantidades.put(id, 0.0);
                    }
                    cantidades.put(id, Numeros.redondear(cantidades.get(id) + d(item.get("qty_delta")), 3));
                    if (cantidades.get(id) < 0 && !Boolean.TRUE.equals(linea.get("auto"))) {
                        Object modelo = linea.get("model");
                        throw invalido("El cambio " + numero + " deja «" + (modelo != null && !"".equals(modelo) ? modelo : linea.get("equipment")) + "» por debajo de cero");
                    }
                    tocadas.computeIfAbsent(id, k -> new ArrayList<>()).add(numero);
                }
            }
            Map<String, Object> ahora = totales(base, modulos, cantidades, lineas);
            Map<String, Object> pc = new LinkedHashMap<>();
            pc.put("id", cambio.get("id"));
            pc.put("number", numero);
            pc.put("cost_delta", Numeros.redondear2(d(ahora.get("cost")) - d(anterior.get("cost"))));
            pc.put("budget_delta", Numeros.redondear2(d(ahora.get("budget")) - d(anterior.get("budget"))));
            pc.put("profit_delta", Numeros.redondear2(d(ahora.get("profit")) - d(anterior.get("profit"))));
            porCambio.add(pc);
            anterior = ahora;
        }

        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map<String, Object> linea : lineas) {
            boolean auto = Boolean.TRUE.equals(linea.get("auto"));
            String id = (String) linea.get("id");
            double inicio = auto ? modulosBase : cantidadInicial.get(id);
            double vigente = auto ? modulos : cantidades.get(id);
            double precio = d(linea.get("price"));
            Map<String, Object> fila = new LinkedHashMap<>(linea);
            fila.put("initial_qty", inicio);
            fila.put("delta", Numeros.redondear(vigente - inicio, 3));
            fila.put("current_qty", vigente);
            fila.put("initial_total", Numeros.redondear2(inicio * precio));
            fila.put("current_total", Numeros.redondear2(vigente * precio));
            // Las líneas que siguen a los módulos cambian con cada variación de módulos
            fila.put("changes", auto ? cambiosDeModulos : tocadas.getOrDefault(id, List.of()));
            fila.put("added", !idsBase.contains(id));
            filas.add(fila);
        }
        salida.put("configured", true);
        salida.put("client", base.get("client") == null ? "" : base.get("client"));
        salida.put("module_wp", base.get("moduleWp"));
        salida.put("sale_ratio", base.get("saleRatio"));
        salida.put("initial", inicial);
        salida.put("current", anterior);
        salida.put("lines", filas);
        salida.put("per_change", porCambio);
        return salida;
    }
}
