package api.aura.service.modulo_planificador;

import java.text.Normalizer;
import java.util.*;

/**
 * Partidas, categorías y listas cerradas del catálogo de equipos (equivale a equipment.py del Planificador actual).
 * Las claves de «filters» y «specs» son claves de los atributos del equipo.
 */
public final class CatalogoEquipos {

    private CatalogoEquipos() {
    }

    public record Partida(String key, String label) {
    }

    private record Categoria(String key, String label, List<String> partidas, String[][] filtros, String[][] datos) {
    }

    public static final List<Partida> PARTIDAS = List.of(
            new Partida("proyecto", "Proyecto"),
            new Partida("modulos", "Módulos y montaje"),
            new Partida("electricidad", "Electricidad"),
            new Partida("administrativos", "Costes administrativos"));

    private static final String[][] NADA = {};

    private static final List<Categoria> CATEGORIAS = List.of(
            new Categoria("modulos", "Módulos", List.of("modulos"),
                    new String[][]{{"potencia_wp", "Potencia (Wp)"}, {"tecnologia", "Tecnología"}, {"marco", "Marco"}},
                    new String[][]{{"garantia_anos", "Garantía del producto (años)"},
                            {"garantia_prod_anos", "Garantía de producción (años)"},
                            {"garantia_prod_pct", "Potencia garantizada al final (%)"}, {"voc_v", "Voc (V)"},
                            {"isc_a", "Isc (A)"}, {"vmp_v", "Vmp (V)"}, {"imp_a", "Imp (A)"},
                            {"largo_mm", "Largo (mm)"}, {"ancho_mm", "Ancho (mm)"}, {"grosor_mm", "Grosor del marco (mm)"}}),
            new Categoria("estructura", "Estructura", List.of("modulos"),
                    new String[][]{{"cubierta", "Cubierta"}, {"montaje", "Tipo de montaje"}, {"componente", "Componente"}},
                    new String[][]{{"modulos_por_kit", "Módulos por kit"}, {"uds_por_panel", "Uds. mínimas por panel"},
                            {"grosor_min_mm", "Marco admitido, mín. (mm)"}, {"grosor_max_mm", "Marco admitido, máx. (mm)"}}),
            new Categoria("inversores", "Inversores", List.of("electricidad"),
                    new String[][]{{"tipo", "Tipo"}, {"potencia_kw", "Potencia (kW)"}, {"fases", "Fases"}, {"hibrido", "Híbrido"}},
                    new String[][]{{"garantia_anos", "Garantía del producto (años)"}, {"mppt_min_v", "MPPT mín. (V)"},
                            {"mppt_max_v", "MPPT máx. (V)"}, {"vmax_v", "Tensión máx. de entrada (V)"},
                            {"imax_mppt_a", "Corriente máx. por MPPT (A)"},
                            {"imax_mppt_1string_a", "Corriente máx. por MPPT con un solo string (A)"}, {"n_mppt", "Nº de MPPT"},
                            {"prot_cc", "Protecciones CC integradas"}, {"bateria_tension", "Tensión de batería admitida"},
                            {"potencia_dc_max_kw", "Potencia DC máx. de entrada (kW)"},
                            {"bat_kwh_max", "Capacidad de batería máx. (kWh)"},
                            {"paneles_por_unidad", "Paneles por unidad (micro u optimizador)"}}),
            new Categoria("baterias", "Baterías", List.of("electricidad"),
                    new String[][]{{"componente", "Componente"}, {"capacidad_kwh", "Capacidad (kWh)"}, {"tension", "Tensión"}},
                    new String[][]{{"garantia_anos", "Garantía del producto (años)"}, {"ciclos", "Ciclos garantizados"},
                            {"compatible", "Compatible con"},
                            {"modulos_max", "Módulos de batería máx. por controlador"}}),
            new Categoria("contadores", "Contadores", List.of("electricidad"),
                    new String[][]{{"fases", "Fases"}}, NADA),
            new Categoria("monitorizacion", "Monitorización", List.of("electricidad"),
                    new String[][]{{"conexion", "Conexión"}}, NADA),
            new Categoria("cableado", "Cableado", List.of("electricidad"),
                    new String[][]{{"tipo", "Tipo"}, {"seccion_mm2", "Sección (mm²)"}, {"color", "Color"}}, NADA),
            new Categoria("protecciones", "Protecciones", List.of("electricidad"),
                    new String[][]{{"tipo", "Tipo"}, {"elemento", "Elemento"}},
                    new String[][]{{"tension_v", "Tensión (V)"}, {"corriente_a", "Corriente / calibre (A)"},
                            {"corriente_min_a", "Corriente mínima si es regulable (A)"}, {"polos", "Polos"},
                            {"sensibilidad_ma", "Sensibilidad (mA)"}, {"modulos_din", "Módulos DIN (cuadro)"}}),
            new Categoria("tierra", "Puesta a tierra", List.of("electricidad"),
                    new String[][]{{"componente", "Componente"}}, new String[][]{{"seccion_mm2", "Sección (mm²)"}}),
            new Categoria("auxiliar", "Material auxiliar", List.of("electricidad"),
                    new String[][]{{"componente", "Componente"}}, NADA),
            new Categoria("mano_obra", "Mano de obra", List.of("modulos", "electricidad"),
                    new String[][]{{"trabajo", "Trabajo"}}, NADA),
            new Categoria("logistica", "Logística", List.of("modulos"),
                    new String[][]{{"tipo", "Tipo"}}, NADA),
            new Categoria("proyecto", "Proyecto", List.of("proyecto"),
                    new String[][]{{"tramite", "Trámite"}}, NADA),
            new Categoria("administrativos", "Administrativos", List.of("administrativos"),
                    new String[][]{{"tipo", "Tipo"}}, NADA));

    /** Características con lista cerrada: las reglas del presupuestador se apoyan en ellas. */
    private static final Map<String, Map<String, List<String>>> OPCIONES = Map.of(
            // Para qué cubierta es una estructura («Todas» vale para cualquiera; «Chapa» a secas, para las dos chapas)
            "estructura", Map.of(
                    "cubierta", List.of("Todas", "Teja", "Chapa minionda", "Chapa grecada", "Chapa", "Plana", "Suelo", "Parking",
                            "Flotante", "Pérgola", "Panel sándwich")),
            "protecciones", Map.of(
                    "tipo", List.of("CC", "AC", "Cuadro", "Transformador"),
                    "elemento", List.of("Conjunto", "Fusible", "Portafusibles", "Fusible con portafusibles",
                            "Seccionador", "DPS", "Cuadro", "Magnetotérmico", "Diferencial", "Relé diferencial",
                            "Transformador")));

    /** Valores que se escribían antes de tener lista cerrada y ahora se llaman de otra forma. */
    private static final Map<String, Map<String, Map<String, String>>> ANTIGUAS = Map.of(
            "protecciones", Map.of("elemento", Map.of("fusibles por string", "Fusible con portafusibles")));

    /** Sin mayúsculas, acentos ni espacios de más. */
    static String plano(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return String.join(" ", sinAcentos.toLowerCase().trim().split("\\s+"));
    }

    public static List<String> opciones(String categoria, String clave) {
        return OPCIONES.getOrDefault(categoria, Map.of()).get(clave);
    }

    /** El valor de la lista cerrada que corresponde al escrito, o null si no hay lista o el valor no está en ella. */
    public static String opcionCanonica(String categoria, String clave, Object valor) {
        List<String> lista = opciones(categoria, clave);
        if (lista == null) {
            return null;
        }
        String plano = plano(String.valueOf(valor));
        for (String o : lista) {
            if (plano(o).equals(plano)) {
                return o;
            }
        }
        return ANTIGUAS.getOrDefault(categoria, Map.of()).getOrDefault(clave, Map.of()).get(plano);
    }

    public static Set<String> clavesCategoria() {
        Set<String> claves = new LinkedHashSet<>();
        CATEGORIAS.forEach(c -> claves.add(c.key()));
        return claves;
    }

    public static boolean partidaExiste(String clave) {
        return PARTIDAS.stream().anyMatch(p -> p.key().equals(clave));
    }

    public static boolean categoriaAdmitePartida(String categoria, String partida) {
        return CATEGORIAS.stream().anyMatch(c -> c.key().equals(categoria) && c.partidas().contains(partida));
    }

    public static Map<String, List<String>> clavesConLista(String categoria) {
        return OPCIONES.getOrDefault(categoria, Map.of());
    }

    /** Categorías en el formato JSON del Planificador actual. */
    public static List<Map<String, Object>> categorias() {
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Categoria c : CATEGORIAS) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", c.key());
            m.put("label", c.label());
            m.put("groups", c.partidas());
            m.put("filters", campos(c.key(), c.filtros()));
            m.put("specs", campos(c.key(), c.datos()));
            salida.add(m);
        }
        return salida;
    }

    private static List<Map<String, Object>> campos(String categoria, String[][] definicion) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (String[] d : definicion) {
            Map<String, Object> campo = new LinkedHashMap<>();
            campo.put("key", d[0]);
            campo.put("label", d[1]);
            List<String> opciones = opciones(categoria, d[0]);
            if (opciones != null) {
                campo.put("options", opciones);
            }
            lista.add(campo);
        }
        return lista;
    }
}
