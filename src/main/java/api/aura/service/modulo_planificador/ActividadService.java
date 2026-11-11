package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Actividad;
import api.aura.entity.modulo_planificador.pl_Proyecto;
import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import api.aura.repository.modulo_planificador.pl_ActividadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Registro de actividad: descripción en español de lo que cambia el personal (equivale a activity.py). */
@Service
@RequiredArgsConstructor
public class ActividadService {

    /** Una entrada del registro: tipo, resumen y detalle. */
    public record Entrada(String tipo, String resumen, String detalle) {
    }

    public static final Map<String, String> ETIQUETAS_ESTADO = Map.of(
            "todo", "Pendiente", "doing", "En curso", "paused", "Pausada", "blocked", "Bloqueada", "done", "Hecha");

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ISO_SEGUNDOS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    private static LocalDateTime ultima;

    private final pl_ActividadRepository actividades;

    /** Hora de la entrada, siempre posterior a la anterior: el registro se ordena por esta hora. */
    private static synchronized LocalDateTime siguienteHora() {
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (ultima != null && !ahora.isAfter(ultima)) {
            ahora = ultima.plusNanos(1000);
        }
        ultima = ahora;
        return ahora;
    }

    /** Añade una entrada; se guarda junto con el cambio que describe (misma transacción). */
    public void registrar(String proyectoId, String actor, String tipo, String resumen, pl_TareaProyecto tarea, String detalle) {
        pl_Actividad a = new pl_Actividad();
        a.setId(UUID.randomUUID().toString());
        a.setProyectoId(proyectoId);
        a.setTareaId(tarea == null ? null : tarea.getId());
        a.setNombreTarea(tarea == null ? "" : tarea.getNombre());
        a.setFecha(siguienteHora());
        a.setActor(actor);
        a.setTipo(tipo);
        a.setResumen(resumen);
        a.setDetalle(detalle == null ? "" : detalle);
        actividades.save(a);
    }

    public void registrar(String proyectoId, String actor, String tipo, String resumen) {
        registrar(proyectoId, actor, tipo, resumen, null, "");
    }

    /** La lista se muestra de lo más nuevo a lo más viejo: se escriben al revés para que salgan en el orden descrito. */
    public void registrarTodo(String proyectoId, String actor, List<Entrada> entradas, pl_TareaProyecto tarea) {
        List<Entrada> alReves = new ArrayList<>(entradas);
        Collections.reverse(alReves);
        for (Entrada e : alReves) {
            registrar(proyectoId, actor, e.tipo(), e.resumen(), tarea, e.detalle());
        }
    }

    public List<Map<String, Object>> listar(String proyectoId, int limite) {
        int tope = Math.max(1, Math.min(limite, 2000));
        List<Map<String, Object>> salida = new ArrayList<>();
        for (pl_Actividad a : actividades.findByProyectoIdOrderByFechaDesc(proyectoId, PageRequest.of(0, tope))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("at", a.getFecha().atOffset(ZoneOffset.UTC).format(ISO_SEGUNDOS));
            m.put("actor", a.getActor());
            m.put("kind", a.getTipo());
            m.put("summary", a.getResumen());
            m.put("detail", a.getDetalle());
            m.put("task_id", a.getTareaId());
            m.put("task_name", a.getNombreTarea());
            salida.add(m);
        }
        return salida;
    }

    // ------------------------------------------------------------------ formato

    static String fecha(LocalDate d) {
        return d == null ? "sin fecha" : d.format(DIA);
    }

    // ------------------------------------------------------------------- tareas

    /** «Foto» de la tarea antes de modificarla: al compararla después se describe lo que cambió de verdad. */
    public static Map<String, Object> fotoTarea(pl_TareaProyecto t) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("status", t.getEstado());
        f.put("status_reason", t.getMotivoEstado());
        f.put("status_note", t.getNotaEstado());
        f.put("closing_note", t.getNotaCierre());
        f.put("start", t.getInicio());
        f.put("end", t.getFin());
        f.put("depends_on", new ArrayList<>(t.getDependeDe()));
        f.put("checklist", copiaLista(t.getLista()));
        f.put("deliverables", copiaLista(t.getEntregables()));
        return f;
    }

    private static List<Map<String, Object>> copiaLista(List<Map<String, Object>> origen) {
        List<Map<String, Object>> c = new ArrayList<>();
        origen.forEach(m -> c.add(new LinkedHashMap<>(m)));
        return c;
    }

    @SuppressWarnings("unchecked")
    public List<Entrada> describirCambioTarea(Map<String, Object> antes, pl_TareaProyecto t, Map<String, String> nombres, int desplazadas) {
        List<Entrada> salida = new ArrayList<>();
        String estadoAntes = (String) antes.get("status");
        if (!estadoAntes.equals(t.getEstado())) {
            String motivo = String.join(" · ", Arrays.stream(new String[]{t.getMotivoEstado().trim(), t.getNotaEstado().trim()}).filter(x -> !x.isEmpty()).toList());
            String detalle = "";
            if (("blocked".equals(t.getEstado()) || "paused".equals(t.getEstado())) && !motivo.isEmpty()) {
                detalle = "Motivo: " + motivo;
            } else if ("done".equals(t.getEstado()) && !t.getNotaCierre().trim().isEmpty()) {
                detalle = "Comentario de cierre: " + t.getNotaCierre().trim();
            }
            if ("todo".equals(estadoAntes) && "done".equals(t.getEstado())) {
                // Queda constancia de que nadie la puso En curso: sirve para saber cuándo se trabajó de verdad
                String nota = "Se dio por hecha sin pasar por En curso";
                detalle = detalle.isEmpty() ? nota : detalle + " · " + nota;
            }
            salida.add(new Entrada("status", "Cambió el estado: " + ETIQUETAS_ESTADO.getOrDefault(estadoAntes, estadoAntes) + " → "
                    + ETIQUETAS_ESTADO.getOrDefault(t.getEstado(), t.getEstado()), detalle));
        } else {
            if (("blocked".equals(t.getEstado()) || "paused".equals(t.getEstado()))
                    && (!antes.get("status_reason").equals(t.getMotivoEstado()) || !antes.get("status_note").equals(t.getNotaEstado()))) {
                String motivo = String.join(" · ", Arrays.stream(new String[]{t.getMotivoEstado().trim(), t.getNotaEstado().trim()}).filter(x -> !x.isEmpty()).toList());
                salida.add(new Entrada("status", "Modificó el motivo del estado", motivo.isEmpty() ? "" : "Motivo: " + motivo));
            }
            if (!antes.get("closing_note").equals(t.getNotaCierre())) {
                salida.add(new Entrada("status", "Modificó el comentario de cierre", t.getNotaCierre().trim()));
            }
        }

        LocalDate inicioAntes = (LocalDate) antes.get("start");
        LocalDate finAntes = (LocalDate) antes.get("end");
        String plural = desplazadas == 1 ? "tarea dependiente" : "tareas dependientes";
        if (!inicioAntes.equals(t.getInicio()) || !finAntes.equals(t.getFin())) {
            String detalle = "Antes: " + fecha(inicioAntes) + " – " + fecha(finAntes);
            if (desplazadas > 0) {
                detalle += " · Desplazó " + desplazadas + " " + plural;
            }
            salida.add(new Entrada("schedule", "Cambió las fechas: " + fecha(t.getInicio()) + " – " + fecha(t.getFin()), detalle));
        } else if (desplazadas > 0) {
            salida.add(new Entrada("schedule", "Desplazó " + desplazadas + " " + plural, ""));
        }

        List<String> depsAntes = (List<String>) antes.get("depends_on");
        for (String dep : t.getDependeDe()) {
            if (!depsAntes.contains(dep)) {
                salida.add(new Entrada("schedule", "Añadió la dependencia de «" + nombres.getOrDefault(dep, "otra tarea") + "»", ""));
            }
        }
        for (String dep : depsAntes) {
            if (!t.getDependeDe().contains(dep)) {
                salida.add(new Entrada("schedule", "Quitó la dependencia de «" + nombres.getOrDefault(dep, "otra tarea") + "»", ""));
            }
        }

        List<Map<String, Object>> viejos = (List<Map<String, Object>>) antes.get("checklist");
        List<Map<String, Object>> nuevos = t.getLista();
        boolean mismosTextos = viejos.size() == nuevos.size();
        for (int i = 0; mismosTextos && i < viejos.size(); i++) {
            mismosTextos = Objects.equals(viejos.get(i).get("text"), nuevos.get(i).get("text"));
        }
        if (mismosTextos) {
            for (int i = 0; i < viejos.size(); i++) {
                boolean antesHecha = Boolean.TRUE.equals(viejos.get(i).get("done"));
                boolean ahoraHecha = Boolean.TRUE.equals(nuevos.get(i).get("done"));
                if (antesHecha != ahoraHecha) {
                    salida.add(new Entrada("checklist", (ahoraHecha ? "Marcó" : "Desmarcó") + " la subtarea «" + nuevos.get(i).getOrDefault("text", "") + "»", ""));
                }
            }
        } else if (!viejos.equals(nuevos)) {
            salida.add(new Entrada("checklist", "Modificó la lista de subtareas", "Ahora tiene " + nuevos.size()));
        }

        Map<String, Map<String, Object>> entViejos = new LinkedHashMap<>();
        ((List<Map<String, Object>>) antes.get("deliverables")).forEach(d -> entViejos.put((String) d.get("key"), d));
        Map<String, Map<String, Object>> entNuevos = new LinkedHashMap<>();
        t.getEntregables().forEach(d -> entNuevos.put((String) d.get("key"), d));
        entNuevos.forEach((clave, d) -> {
            if (!entViejos.containsKey(clave)) {
                salida.add(new Entrada("docs", "Añadió el elemento requerido «" + d.get("label") + "»", ""));
            } else if (!Objects.equals(d.get("na"), entViejos.get(clave).get("na"))) {
                if (Boolean.TRUE.equals(d.get("na"))) {
                    salida.add(new Entrada("docs", "Marcó «" + d.get("label") + "» como «No aplica»", "Motivo: " + d.getOrDefault("na_reason", "")));
                } else {
                    salida.add(new Entrada("docs", "Volvió a requerir «" + d.get("label") + "»", ""));
                }
            }
        });
        entViejos.forEach((clave, d) -> {
            if (!entNuevos.containsKey(clave)) {
                salida.add(new Entrada("docs", "Quitó el elemento requerido «" + d.get("label") + "»", ""));
            }
        });
        return salida;
    }

    public Entrada describirAdjunto(String accion, Map<String, Object> item, pl_TareaProyecto t) {
        String tipo = switch (String.valueOf(item.get("kind"))) {
            case "file" -> "el archivo";
            case "link" -> "el enlace";
            case "note" -> "la nota";
            default -> "el elemento";
        };
        String requerido = t.getEntregables().stream().filter(d -> Objects.equals(d.get("key"), item.get("requirement")))
                .map(d -> (String) d.get("label")).findFirst().orElse("");
        boolean enDropbox = "dropbox".equals(item.get("storage"));
        if ("add".equals(accion)) {
            List<String> partes = new ArrayList<>();
            if (!requerido.isEmpty()) partes.add("Para: " + requerido);
            if (enDropbox) partes.add("En Dropbox: " + item.getOrDefault("path", ""));
            return new Entrada("docs", "Aportó " + tipo + " «" + item.getOrDefault("name", "") + "»", String.join(" · ", partes));
        }
        // Quitarlo del planificador no lo borra de Dropbox: eso lo hace administración
        List<String> partes = new ArrayList<>();
        if (!requerido.isEmpty()) partes.add("Era para: " + requerido);
        if (enDropbox) partes.add("El archivo sigue en Dropbox");
        return new Entrada("docs", "Quitó " + tipo + " «" + item.getOrDefault("name", "") + "»", String.join(" · ", partes));
    }

    // ----------------------------------------------------------------- proyecto

    public static Map<String, Object> fotoProyecto(pl_Proyecto p) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", p.getNombre());
        f.put("description", p.getDescripcion());
        f.put("start_date", p.getFechaInicio());
        f.put("documents", copiaLista(p.getDocumentos()));
        return f;
    }

    /** Qué cambió en el proyecto. Los cambios del contrato y la forma de pago se describen en la Fase 5. */
    @SuppressWarnings("unchecked")
    public List<Entrada> describirCambioProyecto(Map<String, Object> antes, pl_Proyecto p) {
        List<Entrada> salida = new ArrayList<>();
        if (!antes.get("name").equals(p.getNombre())) {
            salida.add(new Entrada("project", "Cambió el nombre del proyecto", "«" + antes.get("name") + "» → «" + p.getNombre() + "»"));
        }
        if (!antes.get("description").equals(p.getDescripcion())) {
            salida.add(new Entrada("project", "Modificó la descripción del proyecto", ""));
        }
        if (!Objects.equals(antes.get("start_date"), p.getFechaInicio())) {
            salida.add(new Entrada("project", "Cambió la fecha de inicio: " + fecha(p.getFechaInicio()), "Antes: " + fecha((LocalDate) antes.get("start_date"))));
        }
        if (!antes.get("documents").equals(p.getDocumentos())) {
            salida.add(new Entrada("project", "Modificó las guías y fichas del proyecto", ""));
        }
        return salida;
    }
}
