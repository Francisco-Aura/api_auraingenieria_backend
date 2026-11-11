package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static api.aura.service.modulo_planificador.TareaProyectoService.invalido;

/**
 * Facturación del contrato: qué se factura en cada pago y qué pasa cuando el presupuesto cambia (equivale a billing.py).
 *
 * <p>Cada pago de la forma de pago tiene su tarea de facturar (las cuatro de la plantilla y, si hay más de cuatro pagos, tareas nuevas al
 * final de la fase 6). La tarea guarda el importe de su pago, así que una factura ya emitida no cambia nunca.
 *
 * <p>Cuando un cambio modifica el importe del contrato: si quedan pagos por empezar, la diferencia se reparte entre ellos en proporción
 * a su importe; si ya no queda ninguno, se añade al final de la fase 6 una tarea para facturar la ampliación; y un aumento puede
 * marcarse para cobrarse aparte («new»), con esa misma tarea aunque queden pagos pendientes.
 */
@Service
@RequiredArgsConstructor
public class FacturacionService {

    static final String FASE_PAGOS = "FASE 6 — Legalización y trámites";
    static final String DEPARTAMENTO_PAGOS = "ADMINISTRACIÓN";

    /** Lo que hay que anotar en la actividad: (categoría, resumen, detalle, tarea). */
    public record Anotacion(String tipo, String resumen, String detalle, pl_TareaProyecto tarea) {
    }

    private final ContratoProyectoService contrato;
    private final PlanificacionService planificacion;

    /** Las tareas de facturar, por orden de pago. */
    static List<pl_TareaProyecto> tareasDePago(Map<String, pl_TareaProyecto> tareas) {
        return tareas.values().stream().filter(t -> t.getEtapaPago() != null)
                .sorted(Comparator.comparingInt(pl_TareaProyecto::getEtapaPago)).toList();
    }

    // ------------------------------------------------------------ crear la tarea de un pago

    private static List<Map<String, Object>> entregablesDePago(String etiqueta) {
        List<Map<String, Object>> lista = new ArrayList<>();
        lista.add(entregable("Factura: " + etiqueta));
        lista.add(entregable("Justificante de transferencia"));
        return lista;
    }

    private static Map<String, Object> entregable(String etiqueta) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("key", UUID.randomUUID().toString());
        e.put("label", etiqueta);
        e.put("na", false);
        e.put("na_reason", "");
        e.put("locked", true);
        return e;
    }

    private static String descripcionDePago(String etiqueta, double importe) {
        return "Pago: " + etiqueta + "\nImporte (sin IVA): " + Numeros.euros(importe) + "\n\n"
                + "Administración factura este pago y confirma el cobro. Solo ejecutar cuando la tarea de pago "
                + "anterior esté completada.";
    }

    /** Tarea de facturar un pago más, al final de la fase 6 (tras la última tarea de pago). */
    private pl_TareaProyecto nuevaTareaDePago(String proyectoId, Map<String, pl_TareaProyecto> tareas, int etapa, String etiqueta, double importe, String titulo) {
        List<pl_TareaProyecto> existentes = tareasDePago(tareas);
        pl_TareaProyecto anterior = existentes.isEmpty() ? null : existentes.get(existentes.size() - 1);
        // Se copia el formato de una tarea de pago de la fase 6 (la última de la plantilla) para que sea igual.
        pl_TareaProyecto referencia = null;
        for (int i = existentes.size() - 1; i >= 0; i--) {
            if (FASE_PAGOS.equals(existentes.get(i).getFase())) {
                referencia = existentes.get(i);
                break;
            }
        }
        if (referencia == null) {
            referencia = anterior;
        }

        LocalDate base = anterior != null ? anterior.getInicio()
                : tareas.values().stream().map(pl_TareaProyecto::getInicio).min(Comparator.naturalOrder()).orElse(LocalDate.now());
        int posicion = anterior != null ? anterior.getPosicion() + 1 : tareas.size();
        // Hueco en el orden de la plantilla: lo que iba después pasa un puesto más abajo.
        for (pl_TareaProyecto t : tareas.values()) {
            if (t.getPosicion() >= posicion) {
                t.setPosicion(t.getPosicion() + 1);
            }
        }

        pl_TareaProyecto t = new pl_TareaProyecto();
        t.setId(UUID.randomUUID().toString());
        t.setProyectoId(proyectoId);
        t.setNombre(titulo);
        t.setDescripcion(descripcionDePago(etiqueta, importe));
        t.setFase(referencia != null ? referencia.getFase() : FASE_PAGOS);
        t.setDepartamento(referencia != null ? referencia.getDepartamento() : DEPARTAMENTO_PAGOS);
        t.setPosicion(posicion);
        t.setInicio(base);
        t.setFin(base);
        t.setUsa(new ArrayList<>(List.of("importe_total")));
        t.setEtapaPago(etapa);
        t.setImportePago(Numeros.redondear2(importe));
        t.setEtiquetaPago(etiqueta);
        t.setCarpetaDropbox(referencia != null ? referencia.getCarpetaDropbox() : "");
        t.setDependeDe(anterior != null ? new ArrayList<>(List.of(anterior.getId())) : new ArrayList<>());
        t.setEntregables(entregablesDePago(etiqueta));
        t.setCreadoEl(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        tareas.put(t.getId(), t);
        // Lo que esperaba a la tarea de pago anterior (el cierre económico) espera también a esta.
        if (anterior != null) {
            for (pl_TareaProyecto otra : tareas.values()) {
                if (!otra.getId().equals(t.getId()) && otra.getDependeDe().contains(anterior.getId()) && !otra.getDependeDe().contains(t.getId())) {
                    List<String> deps = new ArrayList<>(otra.getDependeDe());
                    deps.add(t.getId());
                    otra.setDependeDe(deps);
                }
            }
        }
        List<pl_TareaProyecto> movidas = planificacion.replanificar(tareas);
        // Replanificar la estructura cambia el plazo de referencia de lo que se mueve.
        t.setFinPrevisto(t.getFin());
        for (pl_TareaProyecto m : movidas) {
            m.setFinPrevisto(m.getFin());
        }
        return t;
    }

    /** Quita una tarea de pago. Lo que dependía de ella pasa a depender de lo que ella esperaba, para no perder el orden. */
    private static void quitarTareaDePago(Map<String, pl_TareaProyecto> tareas, pl_TareaProyecto quitada) {
        for (pl_TareaProyecto otra : tareas.values()) {
            if (!otra.getId().equals(quitada.getId()) && otra.getDependeDe().contains(quitada.getId())) {
                List<String> deps = new ArrayList<>();
                for (String d : otra.getDependeDe()) {
                    if (!d.equals(quitada.getId())) deps.add(d);
                }
                for (String d : quitada.getDependeDe()) {
                    if (!otra.getDependeDe().contains(d) && !d.equals(otra.getId())) deps.add(d);
                }
                otra.setDependeDe(deps);
            }
        }
        tareas.remove(quitada.getId());
    }

    /** Una tarea de pago de la plantilla que se queda con otro pago: se renombra y se reescriben su descripción y su documentación. */
    private static void reorientarTareaDePago(pl_TareaProyecto t, int indice, int total, Map<String, Object> etapa) {
        String etiqueta = (String) etapa.get("label");
        t.setNombre(total == 1 ? "Emitir factura del pago único y confirmar cobro"
                : "Emitir factura pago " + (indice + 1) + ": " + etiqueta + " y confirmar cobro");
        t.setDescripcion(descripcionDePago(etiqueta, (double) etapa.get("amount")));
        t.setEntregables(entregablesDePago(etiqueta));
    }

    public record Enlace(List<pl_TareaProyecto> nuevas, List<String> quitadas) {
    }

    /**
     * Une las tareas de facturar de la plantilla con los pagos de la forma de pago. Pone a cada una su importe. Si hay más de cuatro
     * pagos, crea las tareas de los demás. Si hay menos de cuatro, se quedan las últimas tareas de pago y se quitan las anteriores.
     */
    @SuppressWarnings("unchecked")
    public Enlace enlazarPagos(String proyectoId, Map<String, pl_TareaProyecto> tareas, Map<String, Object> plan, Double total) {
        List<Map<String, Object>> etapas = (List<Map<String, Object>>) contrato.calendarioPagos(plan, total).get("stages");
        List<pl_TareaProyecto> nuevas = new ArrayList<>();
        List<String> quitadas = new ArrayList<>();

        List<pl_TareaProyecto> existentes = tareasDePago(tareas);
        if (etapas.size() < existentes.size()) {
            int sobran = existentes.size() - etapas.size();
            for (pl_TareaProyecto fuera : existentes.subList(0, sobran)) {
                quitarTareaDePago(tareas, fuera);
                quitadas.add(fuera.getId());
            }
            List<pl_TareaProyecto> quedan = existentes.subList(sobran, existentes.size());
            for (int i = 0; i < quedan.size(); i++) {
                quedan.get(i).setEtapaPago(i);
                reorientarTareaDePago(quedan.get(i), i, etapas.size(), etapas.get(i));
            }
            // Con menos tareas, lo que iba detrás se adelanta: se replanifica (y el nuevo plazo pasa a ser el de referencia)
            for (pl_TareaProyecto movida : planificacion.replanificar(tareas)) {
                movida.setFinPrevisto(movida.getFin());
            }
        }

        for (pl_TareaProyecto t : tareasDePago(tareas)) {
            if (t.getEtapaPago() < etapas.size()) {
                Map<String, Object> etapa = etapas.get(t.getEtapaPago());
                t.setImportePago((Double) etapa.get("amount"));
                t.setEtiquetaPago((String) etapa.get("label"));
            } else {
                t.setEtapaPago(null);
            }
        }
        for (int i = 4; i < etapas.size(); i++) {
            Map<String, Object> etapa = etapas.get(i);
            nuevas.add(nuevaTareaDePago(proyectoId, tareas, i, (String) etapa.get("label"), (double) etapa.get("amount"),
                    "Emitir factura pago " + (i + 1) + ": " + etapa.get("label") + " y confirmar cobro"));
        }
        return new Enlace(nuevas, quitadas);
    }

    // ------------------------------------------------------- reparto de lo que cambia el importe

    /**
     * Reparte `delta` entre los pagos en proporción a su importe (a partes iguales si todos valen 0). Una rebaja nunca deja un pago por
     * debajo de 0: lo que no cabe en uno pasa a los demás. El céntimo que sobra o falta por el redondeo se lo queda el último pago.
     */
    static double[] repartirProporcional(double delta, double[] importes) {
        int n = importes.length;
        double[] pesos = new double[n];
        double suma = 0;
        for (int i = 0; i < n; i++) {
            pesos[i] = Math.max(importes[i], 0.0);
            suma += pesos[i];
        }
        if (suma <= 0) {
            Arrays.fill(pesos, 1.0);
        }
        double[] partes = new double[n];
        List<Integer> activos = new ArrayList<>();
        for (int i = 0; i < n; i++) activos.add(i);
        double restante = delta;
        Integer absorbente = null;
        while (!activos.isEmpty()) {
            double pesoTotal = 0;
            for (int i : activos) pesoTotal += pesos[i];
            Map<Integer, Double> deseado = new HashMap<>();
            for (int i : activos) deseado.put(i, restante * pesos[i] / pesoTotal);
            List<Integer> vaciados = new ArrayList<>();
            for (int i : activos) {
                if (restante < 0 && importes[i] + partes[i] + deseado.get(i) < 0) vaciados.add(i);
            }
            if (vaciados.isEmpty()) {
                for (int i : activos) partes[i] += deseado.get(i);
                absorbente = activos.get(activos.size() - 1);
                break;
            }
            for (int i : vaciados) {
                double quita = -(importes[i] + partes[i]); // lo que le queda para llegar a 0
                partes[i] += quita;
                restante -= quita;
                activos.remove(Integer.valueOf(i));
            }
        }
        double total = 0;
        for (int i = 0; i < n; i++) {
            partes[i] = Numeros.redondear2(partes[i]);
            total += partes[i];
        }
        if (absorbente != null) {
            partes[absorbente] = Numeros.redondear2(partes[absorbente] + (delta - total));
        }
        return partes;
    }

    /** Deshace el reparto de un cambio. Si una factura afectada ya está hecha, no se puede. */
    @SuppressWarnings("unchecked")
    private static void deshacer(Map<String, Object> asignacion, Map<String, pl_TareaProyecto> tareas, int numero) {
        Object repartido = asignacion.get("tasks");
        if (repartido instanceof Map<?, ?> m) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                pl_TareaProyecto t = tareas.get(e.getKey());
                double delta = ((Number) e.getValue()).doubleValue();
                if (t == null || t.getImportePago() == null) {
                    continue;
                }
                if (!"todo".equals(t.getEstado()) && Math.abs(delta) >= 0.005) {
                    throw invalido("No se puede modificar ni eliminar el cambio nº " + numero + ": la factura «" + t.getNombre()
                            + "» ya está en marcha o hecha");
                }
                t.setImportePago(Numeros.redondear2(t.getImportePago() - delta));
            }
        }
        Object extraId = asignacion.get("new_task_id");
        if (extraId instanceof String id && !id.isEmpty() && tareas.containsKey(id)) {
            pl_TareaProyecto extra = tareas.get(id);
            if (!"todo".equals(extra.getEstado())) {
                throw invalido("No se puede modificar ni eliminar el cambio nº " + numero + ": su factura «" + extra.getNombre() + "» ya está en marcha");
            }
            tareas.remove(id);
            for (pl_TareaProyecto otra : tareas.values()) {
                if (otra.getDependeDe().contains(id)) {
                    otra.setDependeDe(otra.getDependeDe().stream().filter(d -> !d.equals(id)).collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
                }
            }
        }
    }

    /** Reparte el importe de un cambio nuevo. Devuelve cómo se ha repartido y lo que hay que anotar. */
    private Map<String, Object> repartir(String proyectoId, Map<String, Object> cambio, Map<String, pl_TareaProyecto> tareas, List<Anotacion> anotaciones) {
        double delta = ((Number) cambio.get("impact")).doubleValue();
        Map<String, Object> asignacion = new LinkedHashMap<>();
        Map<String, Object> repartido = new LinkedHashMap<>();
        asignacion.put("tasks", repartido);
        asignacion.put("new_task_id", null);
        List<pl_TareaProyecto> pagos = tareasDePago(tareas);
        if (delta == 0 || pagos.isEmpty()) {
            return asignacion;
        }

        // Un pago cuya tarea ya se ha empezado no cambia de importe: la diferencia va a los pagos siguientes, los que aún no se han empezado.
        // Un aumento puede cobrarse aparte (payment_mode «new»): en vez de repartirse, se factura en un pago nuevo.
        boolean pagoPropio = delta > 0 && "new".equals(cambio.get("payment_mode"));
        List<pl_TareaProyecto> pendientes = pagoPropio ? List.of() : pagos.stream().filter(t -> "todo".equals(t.getEstado())).toList();
        if (!pendientes.isEmpty()) {
            double[] importes = pendientes.stream().mapToDouble(t -> t.getImportePago() == null ? 0.0 : t.getImportePago()).toArray();
            double[] partes = repartirProporcional(delta, importes);
            for (int i = 0; i < pendientes.size(); i++) {
                if (partes[i] == 0) {
                    continue;
                }
                pl_TareaProyecto t = pendientes.get(i);
                t.setImportePago(Numeros.redondear2((t.getImportePago() == null ? 0.0 : t.getImportePago()) + partes[i]));
                repartido.put(t.getId(), partes[i]);
            }
            int cuantos = repartido.size();
            if (cuantos > 0) {
                String signo = delta > 0 ? "+" : "";
                StringJoiner detalle = new StringJoiner(", ");
                for (String id : repartido.keySet()) {
                    pl_TareaProyecto t = tareas.get(id);
                    detalle.add((t.getEtiquetaPago().isEmpty() ? t.getNombre() : t.getEtiquetaPago()) + ": " + Numeros.euros(t.getImportePago()));
                }
                anotaciones.add(new Anotacion("contract",
                        "Repartió " + signo + Numeros.euros(delta) + " del cambio entre " + cuantos + (cuantos == 1 ? " pago pendiente" : " pagos pendientes"),
                        detalle.toString(), null));
            }
            return asignacion;
        }

        if (delta > 0) {
            String motivo = ContratoProyectoService.motivo(cambio);
            String etiqueta = "Ampliación: " + (motivo.length() > 60 ? motivo.substring(0, 60) + "…" : motivo);
            int etapa = pagos.get(pagos.size() - 1).getEtapaPago() + 1;
            pl_TareaProyecto t = nuevaTareaDePago(proyectoId, tareas, etapa, etiqueta, delta,
                    "Emitir factura de ampliación: " + (motivo.length() > 50 ? motivo.substring(0, 50) : motivo) + " y confirmar cobro");
            asignacion.put("new_task_id", t.getId());
            anotaciones.add(new Anotacion("structure", "Añadió la tarea de facturar la ampliación",
                    "Importe: " + Numeros.euros(delta) + " · " + (pagoPropio ? "se cobra en un pago nuevo, como se eligió al registrar el cambio"
                    : "todos los pagos anteriores ya estaban facturados"), t));
        }
        return asignacion;
    }

    public record Reparto(List<Map<String, Object>> cambios, List<Anotacion> anotaciones) {
    }

    /**
     * Ajusta los pagos a los cambios del contrato tras guardarlos. Los cambios que no varían conservan su reparto; los nuevos o con otro
     * importe se reparten ahora y los eliminados deshacen el suyo. Lanza un 422 si hubiera que tocar una factura ya hecha.
     */
    @SuppressWarnings("unchecked")
    public Reparto sincronizarReparto(String proyectoId, List<Map<String, Object>> antiguos, List<Map<String, Object>> nuevos, Map<String, pl_TareaProyecto> tareas) {
        Map<String, Map<String, Object>> antiguosPorId = new HashMap<>();
        for (Map<String, Object> c : antiguos) antiguosPorId.put((String) c.get("id"), c);
        Map<String, Map<String, Object>> nuevosPorId = new HashMap<>();
        for (Map<String, Object> c : nuevos) nuevosPorId.put((String) c.get("id"), c);

        int numero = 0;
        for (Map<String, Object> viejo : antiguos) {
            numero++;
            Map<String, Object> reemplazo = nuevosPorId.get(viejo.get("id"));
            if (reemplazo == null || !sinCambios(antiguosPorId, reemplazo)) {
                Object a = viejo.get("allocation");
                deshacer(a instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(), tareas, numero);
            }
        }

        List<Map<String, Object>> salida = new ArrayList<>();
        List<Anotacion> anotaciones = new ArrayList<>();
        for (Map<String, Object> cambio : nuevos) {
            Map<String, Object> con = new LinkedHashMap<>(cambio);
            if (sinCambios(antiguosPorId, cambio)) {
                Object a = antiguosPorId.get(cambio.get("id")).get("allocation");
                con.put("allocation", a instanceof Map<?, ?> m && !m.isEmpty() ? m : new LinkedHashMap<>());
            } else {
                con.put("allocation", repartir(proyectoId, cambio, tareas, anotaciones));
            }
            salida.add(con);
        }
        return new Reparto(salida, anotaciones);
    }

    private static boolean sinCambios(Map<String, Map<String, Object>> antiguosPorId, Map<String, Object> cambio) {
        Map<String, Object> viejo = antiguosPorId.get(cambio.get("id"));
        if (viejo == null) {
            return false;
        }
        double antes = viejo.get("impact") instanceof Number n ? n.doubleValue() : 0.0;
        double ahora = ((Number) cambio.get("impact")).doubleValue();
        // Cambiar cómo se cobra (repartido o en un pago nuevo) obliga a volver a repartir
        return Math.abs(antes - ahora) < 0.005 && modoPago(viejo).equals(modoPago(cambio));
    }

    private static String modoPago(Map<String, Object> c) {
        Object m = c.get("payment_mode");
        return m == null || "".equals(m) ? "split" : String.valueOf(m);
    }

    // ------------------------------------------------------------------------------ lo que se enseña

    /**
     * Forma de pago con el importe de cada pago y si ya se ha facturado. Con tareas de pago, los importes son los de las tareas; sin ellas
     * (proyectos antiguos), se calcula sobre el importe vigente.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> vistaPagos(Map<String, Object> plan, Map<String, pl_TareaProyecto> tareas, double total) {
        Map<String, Object> normalizado = contrato.normalizarPlanPagos(plan);
        List<pl_TareaProyecto> enlazadas = tareasDePago(tareas);
        Map<String, Object> salida = new LinkedHashMap<>();
        if (enlazadas.isEmpty() || enlazadas.stream().allMatch(t -> t.getImportePago() == null)) {
            Map<String, Object> calendario = contrato.calendarioPagos(plan, total);
            salida.put("mode", calendario.get("mode"));
            salida.put("total", calendario.get("total"));
            List<Map<String, Object>> etapas = new ArrayList<>();
            for (Map<String, Object> s : (List<Map<String, Object>>) calendario.get("stages")) {
                Map<String, Object> e = new LinkedHashMap<>(s);
                e.put("done", false);
                e.put("task_id", null);
                e.put("extra", false);
                etapas.add(e);
            }
            salida.put("stages", etapas);
            return salida;
        }

        List<Map<String, Object>> etapasPlan = (List<Map<String, Object>>) (List<?>) normalizado.get("stages");
        List<Map<String, Object>> filas = new ArrayList<>();
        double suma = 0;
        for (pl_TareaProyecto t : enlazadas) {
            double importe = Numeros.redondear2(t.getImportePago() == null ? 0.0 : t.getImportePago());
            boolean enPlan = t.getEtapaPago() < etapasPlan.size();
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("label", enPlan ? etapasPlan.get(t.getEtapaPago()).get("label") : t.getEtiquetaPago());
            fila.put("note", enPlan ? etapasPlan.get(t.getEtapaPago()).getOrDefault("note", "") : "");
            fila.put("percent", total != 0 ? Numeros.redondear2(importe / total * 100) : 0.0);
            fila.put("amount", importe);
            fila.put("done", "done".equals(t.getEstado()));
            fila.put("task_id", t.getId());
            fila.put("extra", !enPlan);
            filas.add(fila);
            suma += importe;
        }
        salida.put("mode", normalizado.get("mode"));
        salida.put("total", Numeros.redondear2(suma));
        salida.put("stages", filas);
        return salida;
    }
}
