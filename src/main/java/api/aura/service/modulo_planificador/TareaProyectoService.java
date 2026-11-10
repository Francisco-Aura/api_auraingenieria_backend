package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.entity.modulo_planificador.pl_Proyecto;
import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import api.aura.repository.modulo_planificador.pl_ProyectoRepository;
import api.aura.repository.modulo_planificador.pl_TareaProyectoRepository;
import api.aura.service.modulo_planificador.ActividadService.Entrada;
import api.aura.service.modulo_planificador.dropbox.DropboxAlmacen;
import api.aura.service.modulo_planificador.dropbox.DropboxError;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Tareas de un proyecto: alta, cambios con sus reglas de estado, entregables, adjuntos y planificación (equivale a routes.py/tasks). */
@Service
@RequiredArgsConstructor
public class TareaProyectoService {

    static final List<String> ESTADOS = List.of("todo", "doing", "paused", "blocked", "done");
    private static final String SIN_IDENTIFICAR = "Sin identificar";
    private static final Pattern ENLACE = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

    private final pl_TareaProyectoRepository tareas;
    private final pl_ProyectoRepository proyectos;
    private final PlanificacionService planificacion;
    private final ProgresoService progreso;
    private final ActividadService actividad;
    private final AvisosService avisos;
    private final DropboxAlmacen dropbox;
    private final EntityManager em;

    // ------------------------------------------------------------------ lectura

    /** Al consultar, lo que ya venció y no está hecho se prorroga hasta hoy y arrastra a sus sucesoras. */
    @Transactional
    public List<Map<String, Object>> listar(String proyectoId) {
        Map<String, pl_TareaProyecto> delProyecto = delProyecto(proyectoId);
        List<pl_TareaProyecto> cambiadas = planificacion.prorrogarVencidas(delProyecto, LocalDate.now());
        if (!cambiadas.isEmpty()) {
            tareas.saveAll(cambiadas);
        }
        return delProyecto.values().stream()
                .sorted(Comparator.comparing(pl_TareaProyecto::getInicio).thenComparingInt(pl_TareaProyecto::getPosicion))
                .map(Mapas::tarea).toList();
    }

    // --------------------------------------------------------------------- alta

    @Transactional
    public Map<String, Object> crear(String proyectoId, Map<String, Object> cuerpo) {
        proyectos.findById(proyectoId).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        String nombre = texto(cuerpo.get("name"));
        if (nombre == null || nombre.isBlank()) {
            throw invalido("Falta el nombre de la tarea");
        }
        LocalDate inicio = fechaObligatoria(cuerpo.get("start"), "start");
        LocalDate fin = fechaObligatoria(cuerpo.get("end"), "end");
        if (fin.isBefore(inicio)) {
            throw invalido("La fecha de fin no puede ser anterior al inicio");
        }
        Map<String, pl_TareaProyecto> delProyecto = delProyecto(proyectoId);
        pl_TareaProyecto t = new pl_TareaProyecto();
        t.setId(UUID.randomUUID().toString());
        t.setProyectoId(proyectoId);
        t.setNombre(nombre);
        t.setDescripcion(textoODefecto(cuerpo.get("description"), ""));
        t.setFase(textoODefecto(cuerpo.get("phase"), ""));
        t.setDepartamento(textoODefecto(cuerpo.get("department"), ""));
        t.setHito(Boolean.TRUE.equals(cuerpo.get("milestone")));
        t.setInicio(inicio);
        t.setFin(fin);
        t.setProgreso(porcentaje(cuerpo.get("progress")));
        t.setEstado(textoODefecto(cuerpo.get("status"), "todo"));
        t.setDependeDe(sinRepetir(lista(cuerpo.get("depends_on"))));
        t.setLista(listaMapas(cuerpo.get("checklist")));
        t.setPosicion(delProyecto.size());
        t.setEntregables(normalizarEntregables(listaMapas(cuerpo.get("deliverables")), Set.of()));
        t.setCreadoEl(ahora());
        delProyecto.put(t.getId(), t);
        planificacion.validarDependencias(t, delProyecto);
        List<pl_TareaProyecto> cambiadas = planificacion.replanificar(delProyecto);
        // Plazo de referencia de lo recién planificado
        t.setFinPrevisto(t.getFin());
        cambiadas.forEach(c -> c.setFinPrevisto(c.getFin()));
        tareas.save(t);
        actividad.registrar(proyectoId, actor(), "structure", "Añadió la tarea", t,
                ActividadService.fecha(t.getInicio()) + " – " + ActividadService.fecha(t.getFin()));
        return Mapas.tarea(t);
    }

    // ------------------------------------------------------------------- cambio

    @Transactional
    public Map<String, Object> actualizar(String id, Map<String, Object> cambios) {
        pl_TareaProyecto t = buscar(id);
        return Mapas.tarea(aplicarCambios(t, new LinkedHashMap<>(cambios)));
    }

    private pl_TareaProyecto aplicarCambios(pl_TareaProyecto t, Map<String, Object> cambios) {
        Map<String, Object> antes = ActividadService.fotoTarea(t);
        String estadoPrevio = t.getEstado();
        Set<String> noAplicabaAntes = t.getEntregables().stream().filter(d -> Boolean.TRUE.equals(d.get("na")))
                .map(d -> (String) d.get("key")).collect(Collectors.toSet());
        cambios.values().removeIf(Objects::isNull);  // ningún campo admite null: se ignora
        if (cambios.containsKey("status") && !ESTADOS.contains(String.valueOf(cambios.get("status")))) {
            throw invalido("Estado no válido");
        }
        if (cambios.containsKey("depends_on")) {
            cambios.put("depends_on", sinRepetir(lista(cambios.get("depends_on"))));
        }
        if (cambios.containsKey("deliverables")) {
            Map<String, String> bloqueados = new LinkedHashMap<>();
            t.getEntregables().stream().filter(d -> Boolean.TRUE.equals(d.get("locked")))
                    .forEach(d -> bloqueados.put((String) d.get("key"), (String) d.get("label")));
            List<Map<String, Object>> normalizados = normalizarEntregables(listaMapas(cambios.get("deliverables")), bloqueados.keySet());
            Set<String> claves = normalizados.stream().map(d -> (String) d.get("key")).collect(Collectors.toSet());
            for (String clave : bloqueados.keySet()) {
                if (!claves.contains(clave)) {
                    throw invalido("«" + bloqueados.get(clave) + "» es un elemento requerido y no se puede quitar: márcalo como «No aplica» y explica por qué");
                }
            }
            cambios.put("deliverables", normalizados);
        }
        if (cambios.containsKey("change_acks")) {
            pl_Proyecto p = proyectos.findById(t.getProyectoId()).orElse(null);
            Set<String> conocidos = new HashSet<>();
            if (p != null) {
                p.getCambios().forEach(c -> conocidos.add(String.valueOf(c.get("id"))));
            }
            cambios.put("change_acks", sinRepetir(lista(cambios.get("change_acks"))).stream().filter(conocidos::contains).toList());
        }

        copiarCampos(t, cambios);
        // Primero el avance (marcar subtareas puede completar la tarea) y después las reglas de estado, que son las que exigen
        // la documentación; así no hay forma de darla por hecha sin aportarla
        progreso.aplicar(t, cambios.keySet(), entregablesPendientes(t).isEmpty());
        reglasDeEstado(t, estadoPrevio, cambios);
        if (t.getFin().isBefore(t.getInicio())) {
            throw invalido("La fecha de fin no puede ser anterior al inicio");
        }

        // Validar dependencias y empujar a las tareas que dependan de esta (o a esta, si se adelantó)
        Map<String, pl_TareaProyecto> delProyecto = delProyecto(t.getProyectoId());
        delProyecto.put(t.getId(), t);
        planificacion.validarDependencias(t, delProyecto);
        List<pl_TareaProyecto> cambiadas = planificacion.replanificar(delProyecto);

        // Cambiar fechas o dependencias a mano es replanificar: el nuevo fin pasa a ser el plazo de referencia
        if (cambios.containsKey("start") || cambios.containsKey("end") || cambios.containsKey("depends_on")) {
            t.setFinPrevisto(t.getFin());
            cambiadas.forEach(c -> c.setFinPrevisto(c.getFin()));
        }

        Map<String, String> nombres = new LinkedHashMap<>();
        delProyecto.values().forEach(x -> nombres.put(x.getId(), x.getNombre()));
        int desplazadas = (int) cambiadas.stream().filter(x -> !x.getId().equals(t.getId())).count();
        actividad.registrarTodo(t.getProyectoId(), actor(), actividad.describirCambioTarea(antes, t, nombres, desplazadas), t);
        tareas.save(t);

        if (!"done".equals(estadoPrevio) && "done".equals(t.getEstado())) {
            avisarSiguientes(t);
        }
        // Dar un documento por «No aplica» lo resuelve igual que aportarlo: si era lo último que faltaba, la tarea se cierra
        boolean nuevoNoAplica = cambios.containsKey("deliverables")
                && t.getEntregables().stream().anyMatch(d -> Boolean.TRUE.equals(d.get("na")) && !noAplicabaAntes.contains(d.get("key")));
        return nuevoNoAplica ? cerrarSiSeCumple(t, true) : t;
    }

    /** Reglas al cambiar de estado. */
    private void reglasDeEstado(pl_TareaProyecto t, String previo, Map<String, Object> cambios) {
        // Una tarea iniciada no vuelve a Pendiente, y solo se pausa o bloquea lo que ya está en marcha
        if ("todo".equals(t.getEstado()) && !"todo".equals(previo)) {
            throw invalido("Una tarea iniciada no puede volver a Pendiente: para retomarla, ponla En curso");
        }
        if (("paused".equals(t.getEstado()) || "blocked".equals(t.getEstado())) && "todo".equals(previo)) {
            throw invalido("Primero hay que iniciar la tarea (En curso) para poder pausarla o bloquearla");
        }
        if ("blocked".equals(t.getEstado()) && t.getMotivoEstado().isBlank() && t.getNotaEstado().isBlank()) {
            throw invalido("Indica el motivo del bloqueo");
        }
        // Al salir del bloqueo, el motivo deja de tener sentido
        if ("blocked".equals(previo) && !"blocked".equals(t.getEstado()) && !cambios.containsKey("status_reason")) {
            t.setMotivoEstado("");
            t.setNotaEstado("");
        }
        if ("done".equals(t.getEstado()) && !"done".equals(previo)) {
            // Las subtareas son solo de seguimiento: lo que impide finalizar son los documentos obligatorios
            List<String> pendientes = entregablesPendientes(t);
            if (!pendientes.isEmpty()) {
                throw invalido("Faltan por aportar: " + String.join("; ", pendientes));
            }
            t.setCompletadaEl(LocalDate.now());
        } else if (!"done".equals(t.getEstado()) && "done".equals(previo)) {
            t.setCompletadaEl(null);
        }
    }

    private void copiarCampos(pl_TareaProyecto t, Map<String, Object> c) {
        if (c.containsKey("name")) t.setNombre(String.valueOf(c.get("name")));
        if (c.containsKey("description")) t.setDescripcion(String.valueOf(c.get("description")));
        if (c.containsKey("phase")) t.setFase(String.valueOf(c.get("phase")));
        if (c.containsKey("department")) t.setDepartamento(String.valueOf(c.get("department")));
        if (c.containsKey("start")) t.setInicio(fechaObligatoria(c.get("start"), "start"));
        if (c.containsKey("end")) t.setFin(fechaObligatoria(c.get("end"), "end"));
        if (c.containsKey("progress")) t.setProgreso(porcentaje(c.get("progress")));
        if (c.containsKey("status")) t.setEstado(String.valueOf(c.get("status")));
        if (c.containsKey("status_reason")) t.setMotivoEstado(String.valueOf(c.get("status_reason")));
        if (c.containsKey("status_note")) t.setNotaEstado(String.valueOf(c.get("status_note")));
        if (c.containsKey("closing_note")) t.setNotaCierre(String.valueOf(c.get("closing_note")));
        if (c.containsKey("depends_on")) t.setDependeDe(new ArrayList<>(lista(c.get("depends_on"))));
        if (c.containsKey("checklist")) t.setLista(listaMapas(c.get("checklist")));
        if (c.containsKey("deliverables")) t.setEntregables(listaMapas(c.get("deliverables")));
        if (c.containsKey("change_acks")) t.setCambiosRevisados(new ArrayList<>(lista(c.get("change_acks"))));
    }

    // ---------------------------------------------------------------- entregables

    /** Deja cada elemento requerido con clave, etiqueta y, si no aplica, el porqué. Los de la plantilla no se pueden quitar. */
    private List<Map<String, Object>> normalizarEntregables(List<Map<String, Object>> items, Set<String> bloqueadas) {
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Map<String, Object> crudo : items) {
            String etiqueta = String.valueOf(crudo.getOrDefault("label", "")).trim();
            if (etiqueta.isEmpty()) {
                continue;
            }
            String clave = crudo.get("key") == null || String.valueOf(crudo.get("key")).isEmpty() ? UUID.randomUUID().toString() : String.valueOf(crudo.get("key"));
            boolean na = Boolean.TRUE.equals(crudo.get("na"));
            String motivo = String.valueOf(crudo.getOrDefault("na_reason", "")).trim();
            if (na && motivo.isEmpty()) {
                throw invalido("Indica por qué «" + etiqueta + "» no aplica");
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("key", clave);
            d.put("label", etiqueta);
            d.put("na", na);
            d.put("na_reason", motivo);
            d.put("locked", bloqueadas.contains(clave));
            salida.add(d);
        }
        return salida;
    }

    private List<String> entregablesPendientes(pl_TareaProyecto t) {
        Set<Object> aportados = t.getAdjuntos().stream().map(a -> a.get("requirement")).collect(Collectors.toSet());
        return t.getEntregables().stream().filter(d -> !Boolean.TRUE.equals(d.get("na")) && !aportados.contains(d.get("key")))
                .map(d -> (String) d.get("label")).toList();
    }

    // ------------------------------------------------------------------ adjuntos

    /** Aporta un enlace o una nota (los archivos van a Dropbox: Fase 6). */
    @Transactional
    public Map<String, Object> aportar(String id, Map<String, Object> cuerpo) {
        pl_TareaProyecto t = buscar(id);
        String requisito = textoODefecto(cuerpo.get("requirement"), "");
        comprobarRequisito(t, requisito);
        String tipo = textoODefecto(cuerpo.get("kind"), "");
        String nombre = textoODefecto(cuerpo.get("name"), "").trim();
        Map<String, Object> item = new LinkedHashMap<>();
        if ("link".equals(tipo)) {
            String url = textoODefecto(cuerpo.get("url"), "").trim();
            if (!ENLACE.matcher(url).find()) {
                throw invalido("El enlace debe empezar por http:// o https://");
            }
            item.put("kind", "link");
            item.put("name", nombre.isEmpty() ? url : nombre);
            item.put("url", url);
        } else if ("note".equals(tipo)) {
            String texto = textoODefecto(cuerpo.get("text"), "").trim();
            if (texto.isEmpty()) {
                throw invalido("La nota está vacía");
            }
            item.put("kind", "note");
            item.put("name", nombre.isEmpty() ? "Nota" : nombre);
            item.put("text", texto);
        } else {
            throw invalido("Tipo de elemento no válido");
        }
        item.put("id", UUID.randomUUID().toString());
        item.put("requirement", requisito);
        item.put("created_at", ahoraIso());
        List<Map<String, Object>> nuevos = new ArrayList<>(t.getAdjuntos());
        nuevos.add(item);
        t.setAdjuntos(nuevos);
        actividad.registrarTodo(t.getProyectoId(), actor(), List.of(actividad.describirAdjunto("add", item, t)), t);
        iniciarSiPendiente(t, "aportar un elemento");
        tareas.save(t);
        return Mapas.tarea(cerrarSiSeCumple(t, false));
    }

    @Transactional
    public Map<String, Object> quitarAdjunto(String id, String adjuntoId) {
        pl_TareaProyecto t = buscar(id);
        Map<String, Object> item = t.getAdjuntos().stream().filter(a -> adjuntoId.equals(a.get("id"))).findFirst()
                .orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Elemento no encontrado"));
        actividad.registrarTodo(t.getProyectoId(), actor(), List.of(actividad.describirAdjunto("remove", item, t)), t);
        t.setAdjuntos(t.getAdjuntos().stream().filter(a -> !adjuntoId.equals(a.get("id"))).collect(Collectors.toCollection(ArrayList::new)));
        tareas.save(t);
        return Mapas.tarea(t);
    }

    static final int MAX_BYTES_ARCHIVO = 25 * 1024 * 1024; // 25 MB por archivo

    /**
     * Sube un archivo: los documentos van SOLO a Dropbox, a la carpeta del proyecto; el servidor no guarda archivos. Sin carpeta de
     * Dropbox en el proyecto no se puede subir nada (el aviso dice cómo crearla).
     */
    @Transactional
    public Map<String, Object> subirArchivo(String id, String nombre, String requisito, String tipoContenido, byte[] contenido) {
        pl_TareaProyecto t = buscar(id);
        comprobarRequisito(t, requisito == null ? "" : requisito);
        pl_Proyecto p = proyectos.findById(t.getProyectoId()).orElse(null);
        if (p == null || p.getRutaDropbox().isEmpty()) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Este proyecto no tiene carpeta en Dropbox y el servidor no guarda archivos: "
                    + "crea la carpeta desde la cabecera del proyecto para poder subirlos");
        }
        if (contenido.length == 0) {
            throw invalido("El archivo está vacío");
        }
        if (contenido.length > MAX_BYTES_ARCHIVO) {
            throw new ErrorApi(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el máximo de 25 MB");
        }
        if (!dropbox.habilitado()) {
            throw new ErrorApi(HttpStatus.SERVICE_UNAVAILABLE, "Dropbox no está disponible en este servidor: no se puede guardar el archivo");
        }
        Map<String, Object> guardado;
        try {
            guardado = dropbox.guardarArchivo(p.getRutaDropbox(), t.getCarpetaDropbox(), nombre, contenido);
        } catch (DropboxError e) {
            throw new ErrorApi(HttpStatus.BAD_GATEWAY, "No se pudo guardar en Dropbox: " + e.getMessage());
        }
        String ruta = String.valueOf(guardado.get("path"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", UUID.randomUUID().toString());
        item.put("requirement", requisito == null ? "" : requisito);
        item.put("kind", "file");
        item.put("storage", "dropbox");
        item.put("name", ruta.substring(ruta.lastIndexOf('/') + 1)); // Dropbox puede haberlo renombrado si ya había uno igual
        item.put("path", ruta);
        item.put("size", contenido.length);
        item.put("content_type", tipoContenido == null || tipoContenido.isBlank() ? "application/octet-stream" : tipoContenido);
        item.put("created_at", ahoraIso());
        List<Map<String, Object>> nuevos = new ArrayList<>(t.getAdjuntos());
        nuevos.add(item);
        t.setAdjuntos(nuevos);
        actividad.registrarTodo(t.getProyectoId(), actor(), List.of(actividad.describirAdjunto("add", item, t)), t);
        iniciarSiPendiente(t, "aportar un archivo");
        tareas.save(t);
        return Mapas.tarea(cerrarSiSeCumple(t, false));
    }

    /** Enlace temporal de Dropbox para abrir un archivo aportado (el servidor no guarda ni reenvía el archivo). */
    @Transactional(readOnly = true)
    public String enlaceDeArchivo(String id, String adjuntoId) {
        pl_TareaProyecto t = buscar(id);
        Map<String, Object> item = t.getAdjuntos().stream().filter(a -> adjuntoId.equals(a.get("id")) && "file".equals(a.get("kind"))).findFirst().orElse(null);
        if (item == null || !"dropbox".equals(item.get("storage"))) {
            throw new ErrorApi(HttpStatus.NOT_FOUND, "Archivo no encontrado");
        }
        if (!dropbox.habilitado()) {
            throw new ErrorApi(HttpStatus.SERVICE_UNAVAILABLE, "Dropbox no está disponible en este servidor");
        }
        try {
            return dropbox.enlaceTemporal(String.valueOf(item.get("path")));
        } catch (DropboxError e) {
            throw new ErrorApi(HttpStatus.BAD_GATEWAY, "No se pudo abrir el archivo en Dropbox: " + e.getMessage());
        }
    }

    /** Quien aporta algo a una tarea Pendiente ya está trabajando en ella: pasa a En curso. */
    private void iniciarSiPendiente(pl_TareaProyecto t, String que) {
        if (!"todo".equals(t.getEstado())) {
            return;
        }
        t.setEstado("doing");
        progreso.aplicar(t, Set.of("status"), false);
        actividad.registrar(t.getProyectoId(), actor(), "status", "Cambió el estado: Pendiente → En curso", t, "Al " + que);
    }

    /**
     * Al resolver el último documento obligatorio, la tarea se da por hecha (y sus subtareas, por marcadas). Solo si estaba
     * Pendiente o En curso y tiene algún documento obligatorio. Se hace por la misma vía que marcarla a mano.
     */
    private pl_TareaProyecto cerrarSiSeCumple(pl_TareaProyecto t, boolean porNoAplica) {
        if (!"todo".equals(t.getEstado()) && !"doing".equals(t.getEstado())) {
            return t;
        }
        if (t.getEntregables().isEmpty() || !entregablesPendientes(t).isEmpty()) {
            return t;
        }
        if (!porNoAplica && t.getEntregables().stream().noneMatch(d -> !Boolean.TRUE.equals(d.get("na")))) {
            return t;
        }
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Map<String, Object> item : t.getLista()) {
            Map<String, Object> hecho = new LinkedHashMap<>(item);
            hecho.put("done", true);
            lista.add(hecho);
        }
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("status", "done");
        cambios.put("checklist", lista);
        try {
            return aplicarCambios(t, cambios);
        } catch (ErrorApi e) {
            em.refresh(t);  // lo hecho queda guardado y la tarea sigue como estaba
            return t;
        }
    }

    // ------------------------------------------------------------------- borrado

    @Transactional
    public void eliminar(String id) {
        pl_TareaProyecto t = buscar(id);
        actividad.registrar(t.getProyectoId(), actor(), "structure", "Eliminó la tarea", t, "");
        for (pl_TareaProyecto otra : delProyecto(t.getProyectoId()).values()) {
            if (otra.getDependeDe().contains(id)) {
                otra.setDependeDe(otra.getDependeDe().stream().filter(d -> !d.equals(id)).collect(Collectors.toCollection(ArrayList::new)));
                tareas.save(otra);
            }
        }
        tareas.delete(t);
    }

    // ------------------------------------------------------------------ internos

    private void avisarSiguientes(pl_TareaProyecto t) {
        try {
            pl_Proyecto p = proyectos.findById(t.getProyectoId()).orElse(null);
            if (p != null) {
                UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
                avisos.tareaTerminada(t, delProyecto(t.getProyectoId()), p, actor(), u == null ? null : u.id());
            }
        } catch (RuntimeException e) {
            // el aviso es secundario: un fallo al avisar nunca impide terminar la tarea
        }
    }

    pl_TareaProyecto buscar(String id) {
        return tareas.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Tarea no encontrada"));
    }

    private Map<String, pl_TareaProyecto> delProyecto(String proyectoId) {
        Map<String, pl_TareaProyecto> m = new LinkedHashMap<>();
        tareas.findByProyectoId(proyectoId).forEach(t -> m.put(t.getId(), t));
        return m;
    }

    private void comprobarRequisito(pl_TareaProyecto t, String requisito) {
        if (!requisito.isEmpty() && t.getEntregables().stream().noneMatch(d -> requisito.equals(d.get("key")))) {
            throw invalido("Ese elemento no está entre los requeridos de la tarea");
        }
    }

    static ErrorApi invalido(String mensaje) {
        return new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }

    private static String actor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        return u == null || u.nombre() == null || u.nombre().isBlank() ? SIN_IDENTIFICAR : u.nombre();
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static String ahoraIso() {
        return java.time.OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx"));
    }

    static String texto(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    static String textoODefecto(Object v, String defecto) {
        return v == null ? defecto : String.valueOf(v);
    }

    static LocalDate fechaObligatoria(Object v, String campo) {
        if (v == null) {
            throw invalido("Falta la fecha «" + campo + "»");
        }
        try {
            return LocalDate.parse(String.valueOf(v));
        } catch (DateTimeParseException e) {
            throw invalido("La fecha «" + campo + "» no es válida (AAAA-MM-DD)");
        }
    }

    private static int porcentaje(Object v) {
        if (v == null) {
            return 0;
        }
        if (!(v instanceof Number n) || n.intValue() < 0 || n.intValue() > 100) {
            throw invalido("progress: tiene que estar entre 0 y 100");
        }
        return n.intValue();
    }

    @SuppressWarnings("unchecked")
    static List<String> lista(Object v) {
        if (v == null) {
            return new ArrayList<>();
        }
        if (!(v instanceof List<?> l)) {
            throw invalido("Se esperaba una lista");
        }
        List<String> salida = new ArrayList<>();
        l.forEach(x -> salida.add(String.valueOf(x)));
        return salida;
    }

    static List<String> sinRepetir(List<String> ids) {
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> listaMapas(Object v) {
        if (v == null) {
            return new ArrayList<>();
        }
        if (!(v instanceof List<?> l)) {
            throw invalido("Se esperaba una lista");
        }
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Object o : l) {
            if (!(o instanceof Map<?, ?> m)) {
                throw invalido("Cada elemento de la lista tiene que ser un objeto");
            }
            salida.add(new LinkedHashMap<>((Map<String, Object>) m));
        }
        return salida;
    }
}
