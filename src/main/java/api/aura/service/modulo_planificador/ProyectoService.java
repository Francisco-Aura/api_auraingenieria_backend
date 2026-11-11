package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.entity.modulo_planificador.pl_Proyecto;
import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import api.aura.repository.modulo_planificador.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static api.aura.service.modulo_planificador.TareaProyectoService.*;

/**
 * Proyectos: alta (con plantilla y borrador), datos, plantillas, actividad y borrado. El contrato, los cambios, la forma de pago y
 * el presupuesto inicial se guardan tal cual llegan; su normalización y reparto de pagos es de la Fase 5, y las carpetas de
 * Dropbox, de la Fase 6.
 */
@Service
@RequiredArgsConstructor
public class ProyectoService {

    private static final String SIN_IDENTIFICAR = "Sin identificar";

    private final pl_ProyectoRepository proyectos;
    private final pl_TareaProyectoRepository tareas;
    private final pl_BorradorRepository borradores;
    private final pl_ActividadRepository actividades;
    private final pl_DevolucionMaterialRepository devoluciones;
    private final PlantillaService plantillas;
    private final ActividadService actividad;
    private final ContratoProyectoService contrato;
    private final PresupuestoProyectoService presupuestoProyecto;
    private final FacturacionService facturacion;
    private final EfectosCambioService efectosCambio;
    private final DocumentosService documentos;
    private final ContratoDocumentoService contratoDocumento;

    // ------------------------------------------------------------------ lectura

    /** Los proyectos, cada uno con `finished`: tiene tareas y todas están hechas. No se guarda ningún estado: se deduce de las tareas. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        Map<String, long[]> totales = new HashMap<>();  // proyecto → {tareas, hechas}
        for (pl_TareaProyecto t : tareas.findAll()) {
            long[] c = totales.computeIfAbsent(t.getProyectoId(), k -> new long[2]);
            c[0]++;
            if ("done".equals(t.getEstado())) {
                c[1]++;
            }
        }
        List<Map<String, Object>> salida = new ArrayList<>();
        for (pl_Proyecto p : proyectos.findByEliminadoElIsNullOrderByCreadoElAsc()) {
            long[] c = totales.getOrDefault(p.getId(), new long[2]);
            Map<String, Object> m = Mapas.proyecto(p);
            m.put("finished", c[0] > 0 && c[1] == c[0]);
            salida.add(m);
        }
        return salida;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(String id) {
        return Mapas.proyecto(activo(id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> actividad(String id, int limite) {
        proyectos.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        return actividad.listar(id, limite);
    }

    // --------------------------------------------------------------------- alta

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> crear(Map<String, Object> cuerpo) {
        String nombre = texto(cuerpo.get("name"));
        if (nombre == null) {
            throw invalido("Falta el nombre del proyecto");
        }
        nombre = nombre.strip();
        if (nombre.isEmpty()) {
            throw invalido("El proyecto necesita un nombre");
        }
        pl_Proyecto p = new pl_Proyecto();
        p.setId(UUID.randomUUID().toString());
        p.setNombre(nombre);
        p.setDescripcion(textoODefecto(cuerpo.get("description"), ""));
        p.setFechaInicio(cuerpo.get("start_date") == null ? LocalDate.now() : fechaObligatoria(cuerpo.get("start_date"), "start_date"));
        p.setLineaBase(contrato.normalizarLineaBase(mapa(cuerpo.get("baseline"))));
        p.setPlanPagos(contrato.normalizarPlanPagos(mapa(cuerpo.get("payment_plan"))));
        p.setPartesContrato(ContratoDocumentoService.limpiarPartes(mapa(cuerpo.get("contract_party"))));
        p.setCreadoEl(ahora());
        String actor = actor();

        // Crear desde un borrador lo consume: si otra persona ya lo convirtió en proyecto (o lo descartó), no se vuelve a crear
        String borradorId = texto(cuerpo.get("draft_id"));
        if (borradorId != null && !borradorId.isEmpty()) {
            if (!borradores.existsById(borradorId)) {
                throw new ErrorApi(HttpStatus.CONFLICT, "Este borrador ya no existe: otra persona lo habrá convertido en proyecto o lo habrá descartado. "
                        + "Mira la lista de proyectos antes de crearlo otra vez.");
            }
            borradores.deleteById(borradorId);
        }

        String plantillaId = texto(cuerpo.get("template_id"));
        if (plantillaId != null && !plantillaId.isEmpty()) {
            Map<String, Object> plantilla = plantillas.obtener(plantillaId).orElseThrow(() -> invalido("Plantilla no encontrada"));
            PlantillaService.Contenido contenido = plantillas.construir(plantilla, p.getId(), p.getFechaInicio(), new HashSet<>(lista(cuerpo.get("exclude_steps"))));
            p.setDocumentos(contenido.documentos());
            List<pl_TareaProyecto> pasos = enlazarPagos(p, contenido.tareas());
            proyectos.save(p);
            proyectos.flush();
            tareas.saveAll(pasos);
            actividad.registrar(p.getId(), actor, "project", "Creó el proyecto", null, "Con los " + pasos.size() + " pasos de la plantilla");
        } else {
            proyectos.save(p);
            actividad.registrar(p.getId(), actor, "project", "Creó el proyecto");
        }
        // La carpeta de Dropbox nunca impide crear el proyecto: si falla, queda anotado y se puede reintentar
        documentos.carpetaAlCrear(p, cuerpo);
        // El contrato es obligatorio. Si se generó en la pantalla de alta, ahora se archiva en la carpeta del proyecto; si no, el proyecto
        // queda con el contrato pendiente de generar (se hace desde el propio proyecto).
        proyectos.save(p);
        if (Boolean.TRUE.equals(cuerpo.get("contract_generated"))) {
            contratoDocumento.generarAlCrear(p);
        }
        return Mapas.proyecto(p);
    }

    /** Carga los pasos de una plantilla en un proyecto que todavía no tiene tareas. */
    @Transactional
    public Map<String, Object> aplicarPlantilla(String id, Map<String, Object> cuerpo) {
        pl_Proyecto p = proyectos.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        if (tareas.countByProyectoId(id) > 0) {
            throw new ErrorApi(HttpStatus.CONFLICT, "El proyecto ya tiene tareas");
        }
        String plantillaId = texto(cuerpo.get("template_id"));
        if (plantillaId == null) {
            throw invalido("Falta la plantilla");
        }
        Map<String, Object> plantilla = plantillas.obtener(plantillaId).orElseThrow(() -> invalido("Plantilla no encontrada"));
        LocalDate inicio = cuerpo.get("start_date") != null ? fechaObligatoria(cuerpo.get("start_date"), "start_date")
                : p.getFechaInicio() != null ? p.getFechaInicio() : LocalDate.now();
        PlantillaService.Contenido contenido = plantillas.construir(plantilla, p.getId(), inicio, new HashSet<>(lista(cuerpo.get("exclude_steps"))));
        p.setFechaInicio(inicio);
        if (p.getDocumentos().isEmpty()) {
            p.setDocumentos(contenido.documentos());
        }
        List<pl_TareaProyecto> pasos = enlazarPagos(p, contenido.tareas());
        proyectos.save(p);
        tareas.saveAll(pasos);
        actividad.registrar(id, actor(), "structure", "Cargó los pasos de la plantilla", null, pasos.size() + " tareas");
        return Mapas.proyecto(p);
    }

    // ------------------------------------------------------------------- cambio

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> actualizar(String id, Map<String, Object> cambios) {
        pl_Proyecto p = proyectos.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        Map<String, Object> antes = ActividadService.fotoProyecto(p);
        if (cambios.get("name") != null) p.setNombre(String.valueOf(cambios.get("name")));
        if (cambios.get("description") != null) p.setDescripcion(String.valueOf(cambios.get("description")));
        if (cambios.get("start_date") != null) p.setFechaInicio(fechaObligatoria(cambios.get("start_date"), "start_date"));
        if (cambios.get("documents") != null) p.setDocumentos(listaMapas(cambios.get("documents")));

        Map<String, Object> presupuestoNuevo = null;
        boolean hayPresupuesto = cambios.containsKey("budget");
        if (cambios.containsKey("baseline")) {
            p.setLineaBase(contrato.normalizarLineaBase(mapa(cambios.get("baseline"))));
        }
        if (cambios.containsKey("payment_plan")) {
            p.setPlanPagos(contrato.normalizarPlanPagos(mapa(cambios.get("payment_plan"))));
        }
        if (hayPresupuesto) {
            presupuestoNuevo = presupuestoProyecto.normalizar(mapa(cambios.get("budget")));
            // El presupuesto inicial es la base de las variaciones de los cambios: con cambios ya hechos sobre él no se toca
            List<Map<String, Object>> cambiosActuales = cambios.containsKey("changes") && cambios.get("changes") != null ? listaMapas(cambios.get("changes")) : p.getCambios();
            if (cambiosActuales.stream().anyMatch(c -> c.get("budget") instanceof Map<?, ?> m && !m.isEmpty())) {
                throw invalido("Ya hay cambios registrados sobre el presupuesto: no se puede sustituir el inicial");
            }
        }
        List<FacturacionService.Anotacion> anotaciones = new ArrayList<>();
        List<pl_TareaProyecto> aGuardar = List.of();
        Set<String> aBorrar = Set.of();
        if (cambios.containsKey("changes")) {
            // Cada cambio de importe se reparte entre los pagos pendientes, o genera la tarea de facturarlo.
            Map<String, pl_TareaProyecto> mapaTareas = new LinkedHashMap<>();
            tareas.findByProyectoId(id).forEach(t -> mapaTareas.put(t.getId(), t));
            Set<String> conocidas = new HashSet<>(mapaTareas.keySet());
            List<Map<String, Object>> normalizados = contrato.normalizarCambios(cambios.get("changes") == null ? List.of() : listaMapas(cambios.get("changes")));
            // Ningún cambio puede dejar una cantidad del presupuesto por debajo de cero
            Map<String, Object> base = hayPresupuesto ? presupuestoNuevo : p.getPresupuesto();
            boolean conPresupuesto = normalizados.stream().anyMatch(c -> c.get("budget") instanceof Map<?, ?> m && !m.isEmpty());
            if (conPresupuesto && (base == null || base.isEmpty())) {
                throw invalido("Antes de registrar cambios sobre el presupuesto hay que guardar el presupuesto inicial");
            }
            presupuestoProyecto.calcular(base, normalizados);
            FacturacionService.Reparto reparto = facturacion.sincronizarReparto(id, p.getCambios(), normalizados, mapaTareas);
            anotaciones.addAll(reparto.anotaciones());
            // Los datos de equipos o potencia que cambian reabren las tareas hechas que los usan.
            anotaciones.addAll(efectosCambio.reabrirPorCambios(p.getCambios(), reparto.cambios(), mapaTareas));
            p.setCambios(reparto.cambios());
            aBorrar = new HashSet<>(conocidas);
            aBorrar.removeAll(mapaTareas.keySet());
            aGuardar = new ArrayList<>(mapaTareas.values());
        }
        if (hayPresupuesto) {
            p.setPresupuesto(presupuestoNuevo);
        }
        String actor = actor();
        actividad.registrarTodo(id, actor, actividad.describirCambioProyecto(antes, p), null);
        if (hayPresupuesto) {
            int lineas = presupuestoNuevo.get("lines") instanceof List<?> l ? l.size() : 0;
            actividad.registrar(id, actor, "structure", lineas > 0 ? "Guardó el presupuesto inicial del proyecto" : "Quitó el presupuesto del proyecto", null,
                    lineas > 0 ? lineas + " líneas" : "");
        }
        proyectos.save(p);
        if (!aBorrar.isEmpty()) {
            tareas.deleteAllById(aBorrar);
        }
        tareas.saveAll(aGuardar);
        for (FacturacionService.Anotacion a : anotaciones) {
            actividad.registrar(id, actor, a.tipo(), a.resumen(), a.tarea(), a.detalle());
        }
        return Mapas.proyecto(p);
    }

    // ---------------------------------------------------------------- contrato y presupuesto

    /** Contrato de referencia, valores vigentes, resumen económico y tareas afectadas por cada cambio. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> verContrato(String id) {
        pl_Proyecto p = proyectos.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        List<pl_TareaProyecto> lista = tareas.findByProyectoId(id);
        Map<String, Object> resultado = contrato.calcular(p.getLineaBase(), p.getCambios(), lista);
        Map<String, pl_TareaProyecto> porId = new LinkedHashMap<>();
        lista.forEach(t -> porId.put(t.getId(), t));
        double total = (double) ((Map<String, Object>) resultado.get("summary")).get("current_total");
        resultado.put("payments", facturacion.vistaPagos(p.getPlanPagos(), porId, total));
        return resultado;
    }

    /** Presupuesto del proyecto con los cambios aplicados: líneas (inicial, variación, vigente) y totales. */
    @Transactional(readOnly = true)
    public Map<String, Object> verPresupuesto(String id) {
        pl_Proyecto p = activo(id);
        return presupuestoProyecto.calcular(p.getPresupuesto(), p.getCambios());
    }

    private List<pl_TareaProyecto> enlazarPagos(pl_Proyecto p, List<pl_TareaProyecto> pasos) {
        Map<String, pl_TareaProyecto> mapa = new LinkedHashMap<>();
        pasos.forEach(t -> mapa.put(t.getId(), t));
        double total = p.getLineaBase().get("importe_total") instanceof Number n ? n.doubleValue() : 0.0;
        facturacion.enlazarPagos(p.getId(), mapa, p.getPlanPagos(), total);
        return new ArrayList<>(mapa.values());
    }

    // ------------------------------------------------------------------- borrado

    /**
     * Elimina el proyecto de verdad y al momento, con sus tareas, su registro y las devoluciones de material. No hay papelera: para
     * evitar borrados por descuido hay que escribir el nombre exacto del proyecto. Lo que esté en Dropbox no se toca.
     */
    @Transactional
    public Map<String, Object> eliminar(String id, String confirmacion) {
        pl_Proyecto p = activo(id);
        if (!(confirmacion == null ? "" : confirmacion.strip()).equals(p.getNombre().strip())) {
            throw invalido("Para eliminarlo hay que escribir el nombre exacto del proyecto");
        }
        tareas.deleteByProyectoId(id);
        actividades.deleteByProyectoId(id);
        // El registro de lo devuelto se va con el proyecto; el stock que sumó se queda: el material está en el almacén
        devoluciones.deleteByProyectoId(id);
        proyectos.delete(p);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        return r;
    }

    // ------------------------------------------------------------------ internos

    pl_Proyecto activo(String id) {
        pl_Proyecto p = proyectos.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        if (p.getEliminadoEl() != null) {
            throw new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado");
        }
        return p;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object v) {
        if (v == null) {
            return new LinkedHashMap<>();
        }
        if (!(v instanceof Map<?, ?> m)) {
            throw invalido("Se esperaba un objeto");
        }
        return new LinkedHashMap<>((Map<String, Object>) m);
    }

    private static String actor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        return u == null || u.nombre() == null || u.nombre().isBlank() ? SIN_IDENTIFICAR : u.nombre();
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
