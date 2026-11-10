package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Proyecto;
import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import api.aura.repository.modulo_planificador.pl_TareaProyectoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Avisos entre tareas (equivale a notify.py): al terminar una, se avisa a quien tiene que hacer las siguientes.
 *
 * <p>Cuando una tarea pasa a «hecha», se buscan las tareas que dependen de ella y que con esto quedan desbloqueadas (todas sus
 * predecesoras hechas y todavía sin empezar). El grupo de cada una es el departamento de la tarea y se envía UN aviso por grupo.
 * No se avisa a quien acaba de terminar la tarea. Si la tarea se reabre y se termina otra vez, no se repite el aviso a quien
 * ya lo recibió, salvo que se reabriera por un cambio del contrato. Un fallo al avisar NUNCA impide terminar la tarea.
 */
@Service
@RequiredArgsConstructor
public class AvisosService {

    /** Nombres para mostrar de los grupos; la clave (sin tildes ni mayúsculas) es lo que se envía a la intranet. */
    private static final Map<String, String> NOMBRES_GRUPO = Map.of(
            "administracion", "Administración", "ingenieria", "Ingeniería", "coordinacion", "Coordinación", "comercial", "Comercial");
    static final String SIN_IDENTIFICAR = "Sin identificar";
    /** La notificación de la intranet admite 255 caracteres. */
    public static final int MAX_MENSAJE = 240;

    private static final Pattern MARCAS = Pattern.compile("\\p{M}");
    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");

    private final AvisosPort puerto;
    private final pl_TareaProyectoRepository tareas;
    private final ActividadService actividad;

    /** Un aviso por grupo, con todas sus tareas nuevas. */
    record Aviso(String grupo, List<pl_TareaProyecto> siguientes, Map<String, Object> contenido) {
    }

    // ------------------------------------------------------------------ funciones puras

    /** «ADMINISTRACIÓN» → «administracion»: sin tildes, mayúsculas ni espacios. */
    static String claveGrupo(String etiqueta) {
        String texto = Normalizer.normalize(etiqueta == null ? "" : etiqueta, Normalizer.Form.NFD);
        texto = MARCAS.matcher(texto).replaceAll("").toLowerCase(Locale.ROOT);
        return NO_ALFANUMERICO.matcher(texto).replaceAll("");
    }

    /** Grupos de un departamento. «ADMINISTRACIÓN/COORDINACIÓN» son dos; sin departamento, ninguno. */
    static List<String> gruposDe(String departamento) {
        List<String> claves = new ArrayList<>();
        for (String parte : (departamento == null ? "" : departamento).split("/", -1)) {
            String clave = claveGrupo(parte);
            if (!clave.isEmpty() && !claves.contains(clave)) {
                claves.add(clave);
            }
        }
        return claves;
    }

    static String nombreGrupo(String clave) {
        String nombre = NOMBRES_GRUPO.get(clave);
        if (nombre != null) {
            return nombre;
        }
        return clave.isEmpty() ? clave : clave.substring(0, 1).toUpperCase(Locale.ROOT) + clave.substring(1).toLowerCase(Locale.ROOT);
    }

    private static int largo(String texto) {
        return texto.codePointCount(0, texto.length());
    }

    private static String primeros(String texto, int n) {
        return texto.substring(0, texto.offsetByCodePoints(0, Math.min(n, largo(texto))));
    }

    static String recortar(String texto, int limite) {
        return largo(texto) <= limite ? texto : primeros(texto, limite - 1).stripTrailing() + "…";
    }

    /** Tareas que pueden empezar ahora que {@code terminada} está hecha y de las que aún no se ha avisado. */
    static List<pl_TareaProyecto> desbloqueadas(pl_TareaProyecto terminada, Map<String, pl_TareaProyecto> tareas) {
        return tareas.values().stream()
                .filter(t -> t.getDependeDe().contains(terminada.getId())
                        && "todo".equals(t.getEstado())
                        && !terminada.getAvisadas().contains(t.getId())
                        && t.getDependeDe().stream().allMatch(d -> !tareas.containsKey(d) || "done".equals(tareas.get(d).getEstado())))
                .sorted(Comparator.comparingInt(pl_TareaProyecto::getPosicion).thenComparing(pl_TareaProyecto::getInicio))
                .toList();
    }

    /** «X ha terminado «A» (Proyecto). Ya puede empezar «B».» sin pasarse de largo (se recortan las tareas). */
    static String mensaje(String quien, String terminada, String proyecto, List<String> siguientes) {
        String cabeza = quien + " ha terminado «" + terminada + "» (" + proyecto + "). ";
        if (siguientes.size() == 1) {
            return recortar(cabeza + "Ya puede empezar «" + siguientes.get(0) + "».", MAX_MENSAJE);
        }
        // Varias tareas: las que quepan y «y N más» para el resto
        for (int mostradas = siguientes.size(); mostradas > 0; mostradas--) {
            StringJoiner nombres = new StringJoiner(", ");
            siguientes.subList(0, mostradas).forEach(n -> nombres.add("«" + n + "»"));
            String extra = mostradas < siguientes.size() ? " y " + (siguientes.size() - mostradas) + " más" : "";
            String mensaje = cabeza + "Ya pueden empezar: " + nombres + extra + ".";
            if (largo(mensaje) <= MAX_MENSAJE) {
                return mensaje;
            }
        }
        // Ni una entera cabe: se acorta el nombre de la primera, si queda sitio útil
        String cola = "» y " + (siguientes.size() - 1) + " más.";
        String prefijo = cabeza + "Ya pueden empezar: «";
        int sitio = MAX_MENSAJE - largo(prefijo) - largo(cola);
        if (sitio >= 15) {
            return prefijo + recortar(siguientes.get(0), sitio) + cola;
        }
        return recortar(cabeza + "Ya pueden empezar " + siguientes.size() + " tareas.", MAX_MENSAJE);
    }

    /** Enlace que abre el planificador en la tarea: «proyecto=…&tarea=…». */
    static String enlace(String proyectoId, String tareaId) {
        return "proyecto=" + URLEncoder.encode(proyectoId, StandardCharsets.UTF_8) + "&tarea=" + URLEncoder.encode(tareaId, StandardCharsets.UTF_8);
    }

    /** Los avisos que corresponden por haber terminado {@code terminada}: uno por grupo, con todas sus tareas nuevas. */
    static List<Aviso> planificar(pl_TareaProyecto terminada, Map<String, pl_TareaProyecto> tareas, pl_Proyecto proyecto, String actor, Integer actorId) {
        Map<String, List<pl_TareaProyecto>> porGrupo = new LinkedHashMap<>();
        for (pl_TareaProyecto siguiente : desbloqueadas(terminada, tareas)) {
            for (String clave : gruposDe(siguiente.getDepartamento())) {
                porGrupo.computeIfAbsent(clave, k -> new ArrayList<>()).add(siguiente);
            }
        }
        boolean conocido = actor != null && !actor.isEmpty() && !SIN_IDENTIFICAR.equals(actor);
        String quien = conocido ? actor : "Alguien";
        List<Aviso> avisos = new ArrayList<>();
        porGrupo.forEach((clave, siguientes) -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("source", "planificador");
            c.put("group", clave);
            c.put("groupName", nombreGrupo(clave));
            // A quien acaba de terminar no se le avisa a sí mismo (por id si se conoce; si no, por nombre).
            c.put("excludeUserId", actorId);
            c.put("excludeUser", conocido ? actor : null);
            c.put("title", recortar("Planificador · " + proyecto.getNombre(), 250));
            c.put("message", mensaje(quien, terminada.getNombre(), proyecto.getNombre(), siguientes.stream().map(pl_TareaProyecto::getNombre).toList()));
            // Al pulsar el aviso en la intranet se abre el planificador en la primera tarea que se desbloquea
            c.put("link", enlace(proyecto.getId(), siguientes.get(0).getId()));
            c.put("projectId", proyecto.getId());
            c.put("projectName", proyecto.getNombre());
            c.put("finishedTaskId", terminada.getId());
            c.put("finishedTaskName", terminada.getNombre());
            c.put("nextTasks", siguientes.stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", d.getId());
                m.put("name", d.getNombre());
                return m;
            }).toList());
            c.put("finishedBy", conocido ? actor : null);
            avisos.add(new Aviso(clave, siguientes, c));
        });
        return avisos;
    }

    // ------------------------------------------------------------------ envío

    /**
     * Avisa a los grupos que tienen que hacer lo que {@code terminada} desbloquea, y lo anota en el registro. Una tarea siguiente
     * se da por avisada solo cuando TODOS sus grupos han recibido el aviso; si alguno falla, queda sin marcar y se vuelve a
     * intentar la próxima vez que se termine la tarea.
     */
    public void tareaTerminada(pl_TareaProyecto terminada, Map<String, pl_TareaProyecto> delProyecto, pl_Proyecto proyecto, String actor, Integer actorId) {
        if (!puerto.activo()) {
            return;
        }
        List<Aviso> avisos = planificar(terminada, delProyecto, proyecto, actor, actorId);
        if (avisos.isEmpty()) {
            return;
        }
        Set<String> entregados = new HashSet<>();
        for (Aviso aviso : avisos) {
            StringJoiner nombres = new StringJoiner(", ");
            aviso.siguientes().forEach(d -> nombres.add("«" + d.getNombre() + "»"));
            String etiqueta = nombreGrupo(aviso.grupo());
            try {
                puerto.enviar(aviso.contenido());
            } catch (AvisoError e) {
                actividad.registrar(proyecto.getId(), actor, "status", "No se pudo avisar a " + etiqueta, terminada, e.getMessage());
                continue;
            }
            entregados.add(aviso.grupo());
            String verbo = aviso.siguientes().size() == 1 ? "ya puede empezar" : "ya pueden empezar";
            actividad.registrar(proyecto.getId(), actor, "status", "Avisó a " + etiqueta + ": " + verbo + " " + nombres,
                    terminada, "Por haber terminado «" + terminada.getNombre() + "»");
        }

        Map<String, Set<String>> gruposNecesarios = new LinkedHashMap<>();
        for (Aviso aviso : avisos) {
            for (pl_TareaProyecto d : aviso.siguientes()) {
                gruposNecesarios.computeIfAbsent(d.getId(), k -> new LinkedHashSet<>()).add(aviso.grupo());
            }
        }
        List<String> hechas = gruposNecesarios.entrySet().stream().filter(e -> entregados.containsAll(e.getValue())).map(Map.Entry::getKey).toList();
        if (!hechas.isEmpty()) {
            List<String> avisadas = new ArrayList<>(terminada.getAvisadas());
            avisadas.addAll(hechas);
            terminada.setAvisadas(avisadas);
        }
        tareas.save(terminada);
    }
}
