package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Proyecto;
import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import api.aura.repository.modulo_planificador.pl_ProyectoRepository;
import api.aura.repository.modulo_planificador.pl_TareaProyectoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Avisos de vencimiento (equivale a due_notices.py): el día que vence una tarea y cuando sigue retrasada, al grupo de la tarea.
 * Se ejecuta una vez al día (ver {@link VencimientosProgramador}).
 *
 * <ul>
 * <li>Cuentan las tareas sin empezar o en curso (no las hechas, pausadas ni bloqueadas) de proyectos no eliminados.</li>
 * <li>El día que vence una tarea (su fecha de fin) se avisa a su grupo: un aviso por proyecto y grupo, con las tareas que vencen.
 * Si el día de vencer no se ejecutó (fin de semana), el aviso sale la primera vez que se ve vencida.</li>
 * <li>Si sigue retrasada a los 3 días, un segundo aviso. Una tarea con más de 10 días de retraso no avisa (se da por avisada).</li>
 * <li>Cada aviso se anota en la tarea ({@code marcasVencimiento}) con la fecha de fin a la que se refiere: no se repite, y si la
 * tarea se replanifica, la nueva fecha vuelve a avisar. Si la intranet falla, no se anota y se reintenta al día siguiente.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class VencimientosService {

    static final int DIAS_RETRASO = 3;
    static final int MAX_DIAS_RETRASO = 10;
    static final String ACTOR = "Planificador";
    private static final List<String> ABIERTAS = List.of("todo", "doing");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final AvisosPort puerto;
    private final pl_TareaProyectoRepository tareas;
    private final pl_ProyectoRepository proyectos;
    private final ActividadService actividad;
    private final RevisionPreciosService precios;

    private record Lote(String proyectoId, String tipo, String grupo) implements Comparable<Lote> {
        @Override
        public int compareTo(Lote o) {
            return Comparator.comparing(Lote::proyectoId).thenComparing(Lote::tipo).thenComparing(Lote::grupo).compare(this, o);
        }
    }

    private record Par(String tipo, String grupo) {
    }

    // ------------------------------------------------------------------ funciones puras

    static List<String> marcas(pl_TareaProyecto t, boolean vence, boolean retraso) {
        List<String> salida = new ArrayList<>(t.getMarcasVencimiento());
        String[][] banderas = {{"due", String.valueOf(vence)}, {"late3", String.valueOf(retraso)}};
        for (String[] b : banderas) {
            String marca = b[0] + ":" + t.getFin();
            if (Boolean.parseBoolean(b[1]) && !salida.contains(marca)) {
                salida.add(marca);
            }
        }
        return salida;
    }

    static String textoTarea(pl_TareaProyecto t, LocalDate hoy, String tipo) {
        long dias = ChronoUnit.DAYS.between(t.getFin(), hoy);
        if ("due".equals(tipo)) {
            return dias == 0 ? "«" + t.getNombre() + "»" : "«" + t.getNombre() + "» (venció el " + DIA_MES.format(t.getFin()) + ")";
        }
        return "«" + t.getNombre() + "» (" + dias + " días)";
    }

    /** «Hoy vence «A» (Proyecto).» / «Siguen con retraso: «A» (3 días), «B» (4 días) (Proyecto).» */
    static String mensaje(String proyecto, String tipo, List<String> textos) {
        String cabeza;
        if ("due".equals(tipo)) {
            cabeza = textos.size() == 1 && !textos.get(0).contains("venció") ? "Hoy vence " : "Vencen: ";
        } else {
            cabeza = textos.size() == 1 ? "Sigue con retraso: " : "Siguen con retraso: ";
        }
        String cola = " (" + proyecto + ").";
        for (int mostradas = textos.size(); mostradas > 0; mostradas--) {
            String extra = mostradas < textos.size() ? " y " + (textos.size() - mostradas) + " más" : "";
            String mensaje = cabeza + String.join(", ", textos.subList(0, mostradas)) + extra + cola;
            if (mensaje.codePointCount(0, mensaje.length()) <= AvisosService.MAX_MENSAJE) {
                return mensaje;
            }
        }
        return AvisosService.recortar(cabeza + textos.size() + " tareas" + cola, AvisosService.MAX_MENSAJE);
    }

    // ------------------------------------------------------------------ ejecución

    /**
     * Envía los avisos de vencimiento (y el de revisión de precios) que corresponden a {@code hoy}. Con {@code soloMarcar} no
     * envía nada: da por avisado lo que ya vence (para activarlo sin ruido). Devuelve un resumen.
     */
    @Transactional
    public Map<String, Object> ejecutar(LocalDate hoy, boolean soloMarcar) {
        Map<String, Object> resumen = new LinkedHashMap<>();
        if (!soloMarcar && !puerto.activo()) {
            return resumen; // avisos apagados: no se envía nada
        }
        resumen.putAll(avisarTareas(hoy, soloMarcar));
        resumen.putAll(precios.avisar(hoy, soloMarcar));
        return resumen;
    }

    Map<String, Object> avisarTareas(LocalDate hoy, boolean soloMarcar) {
        int enviados = 0;
        int tareasAvisadas = 0;
        int marcadas = 0;
        int fallidos = 0;

        List<pl_TareaProyecto> abiertas = tareas.findByEstadoIn(ABIERTAS);
        Map<String, pl_Proyecto> delProyecto = new HashMap<>();
        Set<String> sinProyecto = new HashSet<>();
        Map<Lote, List<pl_TareaProyecto>> lotes = new TreeMap<>();
        Map<String, Set<Par>> necesarias = new HashMap<>(); // tarea → (tipo, grupo) por avisar

        for (pl_TareaProyecto t : abiertas) {
            if (!delProyecto.containsKey(t.getProyectoId()) && !sinProyecto.contains(t.getProyectoId())) {
                Optional<pl_Proyecto> p = proyectos.findById(t.getProyectoId());
                if (p.isPresent()) {
                    delProyecto.put(t.getProyectoId(), p.get());
                } else {
                    sinProyecto.add(t.getProyectoId());
                }
            }
            pl_Proyecto proyecto = delProyecto.get(t.getProyectoId());
            if (proyecto == null || proyecto.getEliminadoEl() != null || t.getFin() == null) {
                continue;
            }
            long dias = ChronoUnit.DAYS.between(t.getFin(), hoy);
            if (dias < 0) {
                continue;
            }
            String tipo;
            if (dias >= DIAS_RETRASO) {
                if (t.getMarcasVencimiento().contains("late3:" + t.getFin())) {
                    continue;
                }
                tipo = dias <= MAX_DIAS_RETRASO ? "late" : "stale";
            } else {
                if (t.getMarcasVencimiento().contains("due:" + t.getFin())) {
                    continue;
                }
                tipo = "due";
            }
            if (soloMarcar || "stale".equals(tipo)) {
                t.setMarcasVencimiento(marcas(t, true, !"due".equals(tipo)));
                tareas.save(t);
                marcadas++;
                continue;
            }
            List<String> grupos = AvisosService.gruposDe(t.getDepartamento());
            if (grupos.isEmpty()) {
                continue; // sin departamento no hay a quién avisar
            }
            for (String grupo : grupos) {
                lotes.computeIfAbsent(new Lote(t.getProyectoId(), tipo, grupo), k -> new ArrayList<>()).add(t);
                necesarias.computeIfAbsent(t.getId(), k -> new LinkedHashSet<>()).add(new Par(tipo, grupo));
            }
        }

        Map<String, Object> resumen = new LinkedHashMap<>();
        if (soloMarcar) {
            resumen.put("sent", 0);
            resumen.put("tasks", 0);
            resumen.put("marked", marcadas);
            resumen.put("failed", 0);
            return resumen;
        }

        Map<String, Set<Par>> entregadas = new HashMap<>();
        for (Map.Entry<Lote, List<pl_TareaProyecto>> entrada : lotes.entrySet()) {
            Lote lote = entrada.getKey();
            pl_Proyecto proyecto = delProyecto.get(lote.proyectoId());
            List<pl_TareaProyecto> deLote = new ArrayList<>(entrada.getValue());
            deLote.sort(Comparator.comparingInt(pl_TareaProyecto::getPosicion).thenComparing(pl_TareaProyecto::getInicio));
            boolean vence = "due".equals(lote.tipo());
            List<String> textos = deLote.stream().map(t -> textoTarea(t, hoy, vence ? "due" : "late")).toList();
            Map<String, Object> aviso = new LinkedHashMap<>();
            aviso.put("source", "planificador");
            aviso.put("group", lote.grupo());
            aviso.put("groupName", AvisosService.nombreGrupo(lote.grupo()));
            aviso.put("excludeUserId", null);
            aviso.put("excludeUser", null);
            aviso.put("title", AvisosService.recortar("Planificador · " + proyecto.getNombre(), 250));
            aviso.put("message", mensaje(proyecto.getNombre(), lote.tipo(), textos));
            aviso.put("link", AvisosService.enlace(proyecto.getId(), deLote.get(0).getId()));
            aviso.put("projectId", proyecto.getId());
            aviso.put("projectName", proyecto.getNombre());
            aviso.put("kind", vence ? "vencimiento" : "retraso");
            aviso.put("nextTasks", deLote.stream().map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.getId());
                m.put("name", t.getNombre());
                return m;
            }).toList());
            String etiqueta = AvisosService.nombreGrupo(lote.grupo());
            try {
                puerto.enviar(aviso);
            } catch (AvisoError e) {
                fallidos++;
                actividad.registrar(proyecto.getId(), ACTOR, "schedule", "No se pudo avisar a " + etiqueta + " del vencimiento", null, e.getMessage());
                continue;
            }
            enviados++;
            String verbo = vence ? "vencen hoy" : "siguen retrasadas";
            for (pl_TareaProyecto t : deLote) {
                entregadas.computeIfAbsent(t.getId(), k -> new HashSet<>()).add(new Par(lote.tipo(), lote.grupo()));
                String resumenActividad = deLote.size() == 1
                        ? "Avisó a " + etiqueta + ": " + verbo + " «" + t.getNombre() + "»"
                        : "Avisó a " + etiqueta + ": " + verbo + " «" + t.getNombre() + "» (con otras)";
                actividad.registrar(proyecto.getId(), ACTOR, "schedule", resumenActividad, t, textoTarea(t, hoy, vence ? "due" : "late"));
            }
        }

        // Una tarea se da por avisada solo cuando todos sus grupos han recibido el aviso
        for (pl_TareaProyecto t : abiertas) {
            Set<Par> falta = necesarias.get(t.getId());
            if (falta != null && entregadas.getOrDefault(t.getId(), Set.of()).containsAll(falta)) {
                boolean retraso = falta.stream().anyMatch(p -> "late".equals(p.tipo()));
                t.setMarcasVencimiento(marcas(t, true, retraso));
                tareas.save(t);
                tareasAvisadas++;
            }
        }
        resumen.put("sent", enviados);
        resumen.put("tasks", tareasAvisadas);
        resumen.put("marked", marcadas);
        resumen.put("failed", fallidos);
        return resumen;
    }
}
