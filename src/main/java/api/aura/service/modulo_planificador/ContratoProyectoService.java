package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import static api.aura.service.modulo_planificador.TareaProyectoService.invalido;

/**
 * Contrato de referencia del proyecto y registro de cambios (equivale a contract.py).
 *
 * <p>El contrato inicial fija unos datos (importe, equipos, potencia). Cada cambio puede modificar equipos y/o el importe; con ellos se
 * calculan los valores vigentes y qué tareas se ven afectadas.
 */
@Service
@RequiredArgsConstructor
public class ContratoProyectoService {

    /** Datos del contrato que pueden cambiar. «importe_total» solo cambia a través del impacto económico. */
    public static final List<Map<String, Object>> CAMPOS = List.of(
            campo("importe_total", "Importe total del contrato (sin IVA)", "money"),
            campo("paneles_uds", "Paneles (unidades)", "int"),
            campo("paneles_modelo", "Paneles (modelo)", "text"),
            campo("inversor_uds", "Inversor (unidades)", "int"),
            campo("inversor_modelo", "Inversor (modelo)", "text"),
            campo("estructura", "Estructura", "text"),
            campo("bateria", "Batería (si aplica)", "text"),
            campo("potencia_kwp", "Potencia total (kWp)", "number"));
    static final Map<String, Map<String, Object>> CAMPO_POR_CLAVE = CAMPOS.stream()
            .collect(Collectors.toMap(c -> (String) c.get("key"), c -> c, (a, b) -> a, LinkedHashMap::new));
    static final List<String> MODIFICABLES = CAMPOS.stream().map(c -> (String) c.get("key")).filter(k -> !k.equals("importe_total")).toList();

    /** Forma de pago habitual del contrato modelo. */
    static final List<Map<String, Object>> PAGOS_ESTANDAR = List.of(
            etapa("A la firma del contrato", "Reserva del material", 30.0),
            etapa("A la recepción del material", "Inicio de obra", 40.0),
            etapa("A la colocación de los paneles", "Avance de obra", 20.0),
            etapa("A la puesta en marcha", "Al acabar la instalación", 10.0));
    static final int MAX_ETAPAS_PAGO = 12;
    private static final Set<String> ETIQUETAS_ANTIGUAS = PAGOS_ESTANDAR.stream()
            .map(s -> junto(((String) s.get("label")).toLowerCase())).collect(Collectors.toSet());

    private final PresupuestoProyectoService presupuestoProyecto;

    private static Map<String, Object> campo(String clave, String etiqueta, String tipo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", clave);
        m.put("label", etiqueta);
        m.put("type", tipo);
        return m;
    }

    private static Map<String, Object> etapa(String etiqueta, String nota, double porcentaje) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", etiqueta);
        m.put("note", nota);
        m.put("percent", porcentaje);
        return m;
    }

    /** Colapsa los espacios (equivale a " ".join(texto.split())). */
    public static String junto(String texto) {
        String t = texto == null ? "" : texto.strip();
        return t.isEmpty() ? "" : String.join(" ", t.split("\\s+"));
    }

    // ---------------------------------------------------------------- datos del contrato

    /** Valor tal y como se guarda: número para cantidades, texto para modelos; null si está vacío. */
    private static Object convertir(String clave, Object valor) {
        String tipo = (String) CAMPO_POR_CLAVE.get(clave).get("type");
        String etiqueta = (String) CAMPO_POR_CLAVE.get(clave).get("label");
        if (valor == null || (valor instanceof String s && s.isBlank())) {
            return null;
        }
        if (tipo.equals("money") || tipo.equals("number") || tipo.equals("int")) {
            double n;
            try {
                n = Numeros.aNumero(valor);
            } catch (NumberFormatException e) {
                throw invalido("«" + etiqueta + "» debe ser un número");
            }
            if (!Numeros.finito(n) || n < 0) {
                throw invalido("«" + etiqueta + "» debe ser un número positivo");
            }
            if (tipo.equals("int")) {
                if (n != Math.rint(n) && n != (long) n) {
                    throw invalido("«" + etiqueta + "» debe ser un número entero");
                }
                return (int) n;
            }
            return Numeros.redondear2(n);
        }
        return String.valueOf(valor).strip();
    }

    public Map<String, Object> normalizarLineaBase(Map<String, Object> crudo) {
        Map<String, Object> salida = new LinkedHashMap<>();
        if (crudo != null) {
            crudo.forEach((clave, valor) -> {
                if (CAMPO_POR_CLAVE.containsKey(clave)) {
                    salida.put(clave, convertir(clave, valor));
                }
            });
        }
        return salida;
    }

    // ------------------------------------------------------------------------ forma de pago

    private static String textoEtapa(String etiqueta, String nota) {
        etiqueta = junto(etiqueta);
        nota = junto(nota);
        if (!nota.isEmpty() && (ETIQUETAS_ANTIGUAS.contains(junto(etiqueta.toLowerCase())) || etiqueta.toLowerCase().equals("a la finalización de la obra"))) {
            return nota;
        }
        return etiqueta;
    }

    private static String cadena(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** {"mode": "standard"|"custom", "stages": [{"label", "note", "percent"}]} validado. */
    public Map<String, Object> normalizarPlanPagos(Map<String, Object> crudo) {
        Map<String, Object> salida = new LinkedHashMap<>();
        if (crudo == null || crudo.isEmpty() || !"custom".equals(crudo.get("mode"))) {
            salida.put("mode", "standard");
            salida.put("stages", PAGOS_ESTANDAR.stream().map(s -> (Object) new LinkedHashMap<>(s)).toList());
            return salida;
        }
        List<Map<String, Object>> etapas = new ArrayList<>();
        Object items = crudo.get("stages");
        for (Object o : items instanceof List<?> l ? l : List.of()) {
            Map<?, ?> item = (Map<?, ?>) o;
            String etiqueta = textoEtapa(cadena(item.get("label")), cadena(item.get("note")));
            if (etiqueta.isEmpty()) {
                throw invalido("Indica a qué corresponde cada pago");
            }
            double porcentaje;
            try {
                porcentaje = Numeros.redondear2(Numeros.aNumero(item.get("percent") == null ? "" : item.get("percent")));
            } catch (NumberFormatException e) {
                throw invalido("El porcentaje de «" + etiqueta + "» debe ser un número");
            }
            if (!Numeros.finito(porcentaje) || porcentaje <= 0 || porcentaje > 100) {
                throw invalido("El porcentaje de «" + etiqueta + "» debe estar entre 0 y 100");
            }
            etapas.add(etapa(etiqueta, "", porcentaje));
        }
        if (etapas.isEmpty()) {
            throw invalido("La forma de pago necesita al menos un pago");
        }
        if (etapas.size() > MAX_ETAPAS_PAGO) {
            throw invalido("Como máximo " + MAX_ETAPAS_PAGO + " pagos");
        }
        double total = Numeros.redondear2(etapas.stream().mapToDouble(e -> (double) e.get("percent")).sum());
        if (Math.abs(total - 100) > 0.01) {
            throw invalido("Los porcentajes de los pagos suman " + Numeros.g(total) + " %: deben sumar exactamente 100 %");
        }
        salida.put("mode", "custom");
        salida.put("stages", new ArrayList<Object>(etapas));
        return salida;
    }

    /** Forma de pago con el importe de cada etapa sobre el total vigente (el último absorbe el redondeo). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calendarioPagos(Map<String, Object> plan, Double total) {
        Map<String, Object> normalizado = normalizarPlanPagos(plan);
        double importe = total == null ? 0.0 : total;
        List<Map<String, Object>> etapas = (List<Map<String, Object>>) (List<?>) normalizado.get("stages");
        List<Map<String, Object>> salida = new ArrayList<>();
        double asignado = 0.0;
        for (int i = 0; i < etapas.size(); i++) {
            double valor;
            if (i == etapas.size() - 1) {
                valor = Numeros.redondear2(importe - asignado);
            } else {
                valor = Numeros.redondear2(importe * (double) etapas.get(i).get("percent") / 100);
                asignado = Numeros.redondear2(asignado + valor);
            }
            Map<String, Object> e = new LinkedHashMap<>(etapas.get(i));
            e.put("amount", valor);
            salida.add(e);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mode", normalizado.get("mode"));
        r.put("stages", salida);
        r.put("total", Numeros.redondear2(importe));
        return r;
    }

    // ----------------------------------------------------------------------------- cambios

    /** Motivo del cambio: lo único que se describe (los antiguos tenían «what»). */
    public static String motivo(Map<String, Object> cambio) {
        Object r = cambio.get("reason");
        if (r != null && !"".equals(r)) {
            return String.valueOf(r).strip();
        }
        Object w = cambio.get("what");
        return w == null ? "" : String.valueOf(w).strip();
    }

    /** Valida cada cambio y los deja ordenados por fecha (estable) con su identificador. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> normalizarCambios(List<Map<String, Object>> crudos) {
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Map<String, Object> item : crudos == null ? List.<Map<String, Object>>of() : crudos) {
            String motivo = motivo(item);
            if (motivo.isEmpty()) {
                throw invalido("Indica el motivo del cambio");
            }
            LocalDate dia;
            try {
                dia = LocalDate.parse(cadena(item.get("date")));
            } catch (DateTimeParseException e) {
                throw invalido("La fecha del cambio no es válida");
            }
            double impacto;
            try {
                Object i = item.get("impact");
                impacto = Numeros.redondear2(Numeros.aNumero(i == null || Integer.valueOf(0).equals(i) || "".equals(i) ? 0 : i));
            } catch (NumberFormatException e) {
                throw invalido("El impacto económico debe ser un número (+ o -)");
            }
            if (!Numeros.finito(impacto)) {
                throw invalido("El impacto económico debe ser un número (+ o -)");
            }
            // Todo cambio que registra una persona se acepta: no hay validación aparte ni cambios «pendientes».
            Map<String, Object> cambiado = new LinkedHashMap<>();
            Object set = item.get("set");
            if (set instanceof Map<?, ?> sm) {
                sm.forEach((k, v) -> {
                    if (MODIFICABLES.contains(String.valueOf(k))) {
                        Object c = convertir(String.valueOf(k), v);
                        if (c != null) {
                            cambiado.put(String.valueOf(k), c);
                        }
                    }
                });
            }
            Map<String, Object> entrada = new LinkedHashMap<>();
            Object id = item.get("id");
            entrada.put("id", id == null || "".equals(id) ? UUID.randomUUID().toString() : String.valueOf(id));
            entrada.put("date", dia.toString());
            entrada.put("reason", motivo);
            entrada.put("impact", impacto);
            entrada.put("set", cambiado);
            // Un aumento puede cobrarse en un pago nuevo en vez de repartirse entre los pendientes (ver FacturacionService)
            if ("new".equals(cadena(item.get("payment_mode")).strip()) && impacto > 0) {
                entrada.put("payment_mode", "new");
            }
            Map<String, Object> presupuesto = presupuestoProyecto.normalizarCambio(item.get("budget"));
            if (!presupuesto.isEmpty()) {
                entrada.put("budget", presupuesto);
            }
            salida.add(entrada);
        }
        salida.sort(Comparator.comparing(c -> (String) c.get("date")));
        return salida;
    }

    // ----------------------------------------------------------------------------- cálculo

    private static double importe(Object v) {
        return v instanceof Number n ? n.doubleValue() : v == null || "".equals(v) ? 0.0 : Double.parseDouble(String.valueOf(v));
    }

    /** Valores vigentes, resumen económico y tareas afectadas por cada cambio. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcular(Map<String, Object> lineaBase, List<Map<String, Object>> cambios, List<pl_TareaProyecto> tareas) {
        Map<String, Object> vigente = new LinkedHashMap<>(lineaBase);
        double original = importe(lineaBase.get("importe_total"));
        double corriente = original;
        double modificaciones = 0.0;
        List<pl_TareaProyecto> abiertas = tareas.stream().filter(t -> !"done".equals(t.getEstado())).toList();
        Map<String, List<String>> pendientesPorTarea = new LinkedHashMap<>();
        List<Map<String, Object>> resultados = new ArrayList<>();

        int numero = 0;
        for (Map<String, Object> cambio : cambios) {
            numero++;
            double impacto = importe(cambio.get("impact"));
            corriente = Numeros.redondear2(corriente + impacto);
            modificaciones = Numeros.redondear2(modificaciones + impacto);

            // Solo los equipos y la potencia afectan a las tareas. El importe no: se aplica a los pagos que quedan por facturar
            Map<String, Object> fijados = (Map<String, Object>) cambio.get("set");
            Set<String> tocados = new TreeSet<>(fijados.keySet());
            vigente.putAll(fijados);

            String idCambio = (String) cambio.get("id");
            List<String> afectadas = tareas.stream().filter(t -> t.getUsa().stream().anyMatch(tocados::contains)).map(pl_TareaProyecto::getId).toList();
            List<String> pendientes = abiertas.stream()
                    .filter(t -> afectadas.contains(t.getId()) && !t.getCambiosRevisados().contains(idCambio)).map(pl_TareaProyecto::getId).toList();
            for (String id : pendientes) {
                pendientesPorTarea.computeIfAbsent(id, k -> new ArrayList<>()).add(idCambio);
            }

            Map<String, Object> asignacion = cambio.get("allocation") instanceof Map<?, ?> a ? (Map<String, Object>) a : Map.of();
            Map<String, Object> efecto = new LinkedHashMap<>();
            Object tareasEfecto = asignacion.get("tasks");
            efecto.put("tasks", tareasEfecto == null || (tareasEfecto instanceof Map<?, ?> m && m.isEmpty()) ? new LinkedHashMap<>() : tareasEfecto);
            efecto.put("new_task", asignacion.get("new_task_id"));

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", idCambio);
            r.put("date", cambio.get("date"));
            r.put("reason", motivo(cambio));
            r.put("impact", cambio.get("impact"));
            r.put("set", fijados);
            r.put("budget", cambio.get("budget"));
            Object modo = cambio.get("payment_mode");
            r.put("payment_mode", modo == null || "".equals(modo) ? "split" : modo);
            r.put("number", numero);
            r.put("new_total", corriente);
            r.put("touched", new ArrayList<>(tocados));
            r.put("affected_tasks", afectadas);
            r.put("pending_tasks", pendientes);
            r.put("payment_effect", efecto);
            resultados.add(r);
        }

        vigente.put("importe_total", original != 0 || !cambios.isEmpty() ? (Object) corriente : lineaBase.get("importe_total"));
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("original", original);
        resumen.put("modifications", modificaciones);
        resumen.put("current_total", corriente);
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("fields", CAMPOS);
        salida.put("baseline", lineaBase);
        salida.put("current", vigente);
        salida.put("summary", resumen);
        salida.put("changes", resultados);
        salida.put("pending_by_task", pendientesPorTarea);
        return salida;
    }
}
