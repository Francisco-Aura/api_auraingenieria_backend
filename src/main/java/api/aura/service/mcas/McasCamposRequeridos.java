package api.aura.service.mcas;

import api.aura.dto.mcas.McasCampo;
import api.aura.dto.mcas.McasVistaPreviaDTO;
import api.aura.entity.mcas.McasReglaComision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

import static api.aura.dto.mcas.McasCampo.*;

/**
 * Datos del contrato que piden las reglas de una tabla de comisiones, sacados de
 * la propia configuración (no de una lista fija por comercializadora):
 * <ul>
 *   <li>cada clave de {@code condiciones_json} es un campo, y su valor, una opción;</li>
 *   <li>el método pide los suyos (% del FEE: consumo y FEE; €/MWh: consumo);</li>
 *   <li>las tablas piden sus dimensiones, y sus categorías son las opciones
 *   (zona ZBR/ZAR, oferta...);</li>
 *   <li>la gestora, si algún conjunto publicado de la comercializadora es de una gestora.</li>
 * </ul>
 * Función pura: no consulta la base de datos ni guarda estado.
 */
public final class McasCamposRequeridos {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Claves de condiciones_json y el campo del contrato que comparan. */
    private static final Map<String, McasCampo> CONDICIONES = Map.ofEntries(
            Map.entry("tipo_suministro", TIPO_SUMINISTRO), Map.entry("segmento", SEGMENTO),
            Map.entry("es_renovacion", ES_RENOVACION), Map.entry("tipo_renovacion", TIPO_RENOVACION),
            Map.entry("alta_nueva", ALTA_NUEVA), Map.entry("ambito", AMBITO),
            Map.entry("zona_comision", ZONA_COMISION), Map.entry("tarifa_acceso", TARIFA_ACCESO),
            Map.entry("codigo_producto", PRODUCTO),
            Map.entry("consumo_min_kwh", CONSUMO), Map.entry("consumo_max_kwh", CONSUMO),
            Map.entry("consumo_min_mwh", CONSUMO), Map.entry("consumo_max_mwh", CONSUMO),
            Map.entry("potencia_min_kw", POTENCIA), Map.entry("potencia_max_kw", POTENCIA));

    /** Condiciones cuyo valor es una opción del desplegable (las demás son sí/no o rangos). */
    private static final Set<String> CONDICIONES_CON_OPCIONES = Set.of(
            "tipo_suministro", "segmento", "tipo_renovacion", "ambito", "zona_comision", "tarifa_acceso",
            "codigo_producto");

    /** Dimensiones de mcas_valores_rango y el campo del contrato que miran. */
    private static final Map<String, McasCampo> DIMENSIONES = Map.of(
            "consumo_kwh", CONSUMO, "consumo_mwh", CONSUMO, "potencia_kw", POTENCIA,
            "tarifa_acceso", TARIFA_ACCESO, "codigo_producto", PRODUCTO, "segmento", SEGMENTO,
            "tipo_suministro", TIPO_SUMINISTRO, "zona_comision", ZONA_COMISION);

    private static final Set<String> METODOS_TABLA = Set.of("RANGE_LOOKUP", "MATRIX_LOOKUP", "MULTI_DIMENSION_LOOKUP");

    private McasCamposRequeridos() {}

    /**
     * Campos que usan las reglas, en el orden de {@link McasCampo}.
     *
     * @param reglas              reglas activas de la tabla de comisiones
     * @param tablas              por id de regla: dimensión → categorías de sus tramos (vacío si es numérica)
     * @param conjuntosConGestora nombres de los conjuntos publicados de la comercializadora que son de una gestora
     * @param faltan              campos que el cálculo necesita y el contrato no trae; se marcan {@code falta}
     *                            y se añaden aunque ninguna regla los nombre (p. ej. el tipo de precio)
     * @param comercializadora    nombre de la comercializadora, para las reglas sin nombre legible
     */
    public static List<McasVistaPreviaDTO.Campo> derivar(List<McasReglaComision> reglas,
                                                         Map<Integer, Map<String, Set<String>>> tablas,
                                                         List<String> conjuntosConGestora, Set<McasCampo> faltan,
                                                         String comercializadora) {
        Map<McasCampo, Acumulado> campos = new EnumMap<>(McasCampo.class);
        for (McasReglaComision regla : reglas == null ? List.<McasReglaComision>of() : reglas) {
            String nombre = nombreRegla(regla, comercializadora);
            condiciones(regla).fields().forEachRemaining(c -> {
                McasCampo campo = CONDICIONES.get(c.getKey());
                if (campo == null || c.getValue().isNull()) return;
                Acumulado a = campos.computeIfAbsent(campo, k -> new Acumulado()).usadoPor(nombre);
                if (CONDICIONES_CON_OPCIONES.contains(c.getKey())) a.valor(c.getValue().asText());
            });
            for (McasCampo campo : camposDelMetodo(regla))
                campos.computeIfAbsent(campo, k -> new Acumulado()).usadoPor(nombre);
            if (METODOS_TABLA.contains(regla.getMetodoCalculo()) && tablas != null)
                tablas.getOrDefault(regla.getId(), Map.of()).forEach((dimension, categorias) -> {
                    McasCampo campo = DIMENSIONES.get(dimension);
                    if (campo == null) return;
                    Acumulado a = campos.computeIfAbsent(campo, k -> new Acumulado()).usadoPor(nombre);
                    categorias.forEach(a::valor);
                });
        }
        if (conjuntosConGestora != null && !conjuntosConGestora.isEmpty()) {
            Acumulado gestora = campos.computeIfAbsent(GESTORA, k -> new Acumulado());
            conjuntosConGestora.forEach(gestora::usadoPor);
        }
        Set<McasCampo> pendientes = faltan == null ? Set.of() : faltan;
        pendientes.forEach(campo -> campos.computeIfAbsent(campo, k -> new Acumulado()));

        List<McasVistaPreviaDTO.Campo> resultado = new ArrayList<>();
        campos.forEach((campo, a) -> resultado.add(McasVistaPreviaDTO.Campo.builder()
                .clave(campo).etiqueta(campo.getEtiqueta()).falta(pendientes.contains(campo))
                .valores(List.copyOf(a.valores)).usadoPor(List.copyOf(a.usadoPor)).build()));
        return resultado;
    }

    /**
     * Nombre de la regla para quien no es administrador: el legible o, si no lo
     * tiene, «Regla de la tabla de <comercializadora>». Nunca el código interno.
     */
    public static String nombreRegla(McasReglaComision regla, String comercializadora) {
        if (regla.getNombre() != null && !regla.getNombre().isBlank()) return regla.getNombre().trim();
        return comercializadora != null && !comercializadora.isBlank()
                ? "Regla de la tabla de " + comercializadora.trim() : "Regla de la tabla de comisiones";
    }

    private static JsonNode condiciones(McasReglaComision regla) {
        if (regla.getCondicionesJson() == null || regla.getCondicionesJson().isBlank()) return JSON.createObjectNode();
        try {
            JsonNode nodo = JSON.readTree(regla.getCondicionesJson());
            return nodo.isObject() ? nodo : JSON.createObjectNode();
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

    private static List<McasCampo> camposDelMetodo(McasReglaComision regla) {
        String metodo = regla.getMetodoCalculo();
        if ("FEE_PERCENTAGE".equals(metodo)) return List.of(CONSUMO, FEE);
        if ("RATE_PER_MWH".equals(metodo)) return List.of(CONSUMO);
        if (!"COMPOSITE_SUM".equals(metodo) || regla.getCalculoJson() == null) return List.of();
        Set<McasCampo> campos = new LinkedHashSet<>();
        try {
            for (JsonNode linea : JSON.readTree(regla.getCalculoJson()).path("lineas")) {
                String metodoLinea = linea.path("metodo").asText("");
                if ("FEE_PERCENTAGE".equals(metodoLinea)) { campos.add(CONSUMO); campos.add(FEE); }
                if ("RATE_PER_MWH".equals(metodoLinea)) campos.add(CONSUMO);
            }
        } catch (Exception e) {
            // Un calculo_json ilegible no pide nada: el validador de la tabla ya lo señala.
        }
        return List.copyOf(campos);
    }

    /** Opciones (ordenadas) y reglas (en orden de prioridad) de un campo. */
    private static final class Acumulado {
        private final Set<String> valores = new TreeSet<>();
        private final Set<String> usadoPor = new LinkedHashSet<>();

        Acumulado usadoPor(String nombre) {
            if (nombre != null) usadoPor.add(nombre);
            return this;
        }

        void valor(String valor) {
            if (valor != null && !valor.isBlank()) valores.add(valor.trim());
        }
    }
}
