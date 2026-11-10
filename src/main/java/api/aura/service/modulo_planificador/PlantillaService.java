package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Plantillas de proyecto: pasos, duraciones y dependencias reutilizables (JSON en resources/plantillas). Al crear un proyecto
 * se copian sus pasos como tareas normales: cambiar la plantilla después no afecta a los proyectos ya creados.
 */
@Service
@RequiredArgsConstructor
public class PlantillaService {

    private final ObjectMapper json;
    private final PlanificacionService planificacion;

    private Map<String, Map<String, Object>> cache;

    /** Resultado de aplicar una plantilla: las tareas y los documentos del proyecto. */
    public record Contenido(List<pl_TareaProyecto> tareas, List<Map<String, Object>> documentos) {
    }

    private synchronized Map<String, Map<String, Object>> todas() {
        if (cache == null) {
            Map<String, Map<String, Object>> leidas = new TreeMap<>();
            try {
                for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:plantillas/*.json")) {
                    try (InputStream in = r.getInputStream()) {
                        Map<String, Object> t = json.readValue(in, new TypeReference<LinkedHashMap<String, Object>>() { });
                        String nombre = r.getFilename() == null ? "" : r.getFilename().replace(".json", "");
                        leidas.put(String.valueOf(t.getOrDefault("id", nombre)), t);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("No se pudieron leer las plantillas", e);
            }
            cache = leidas;
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> pasos(Map<String, Object> plantilla) {
        return (List<Map<String, Object>>) plantilla.get("steps");
    }

    public List<Map<String, Object>> listar() {
        List<Map<String, Object>> salida = new ArrayList<>();
        todas().forEach((id, t) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", t.get("name"));
            m.put("step_count", pasos(t).size());
            List<Map<String, Object>> opcionales = new ArrayList<>();
            for (Map<String, Object> s : pasos(t)) {
                if (Boolean.TRUE.equals(s.get("optional"))) {
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("key", s.get("key"));
                    o.put("name", s.get("name"));
                    o.put("default_included", s.getOrDefault("default_included", true));
                    opcionales.add(o);
                }
            }
            m.put("optional_steps", opcionales);
            salida.add(m);
        });
        return salida;
    }

    public Optional<Map<String, Object>> obtener(String id) {
        return Optional.ofNullable(todas().get(id));
    }

    /** Crea las tareas (con fechas calculadas desde `inicio`) y los documentos de un proyecto. */
    @SuppressWarnings("unchecked")
    public Contenido construir(Map<String, Object> plantilla, String proyectoId, LocalDate inicio, Set<String> excluir) {
        List<Map<String, Object>> pasos = pasos(plantilla).stream()
                .filter(s -> !(Boolean.TRUE.equals(s.get("optional")) && excluir.contains(String.valueOf(s.get("key"))))).toList();

        Map<String, pl_TareaProyecto> porClave = new LinkedHashMap<>();
        int posicion = 0;
        for (Map<String, Object> paso : pasos) {
            pl_TareaProyecto t = new pl_TareaProyecto();
            t.setId(UUID.randomUUID().toString());
            t.setProyectoId(proyectoId);
            t.setNombre(String.valueOf(paso.get("name")));
            t.setDescripcion(String.valueOf(paso.getOrDefault("description", "")));
            t.setFase(String.valueOf(paso.getOrDefault("phase", "")));
            t.setDepartamento(String.valueOf(paso.getOrDefault("department", "")));
            t.setHito(Boolean.TRUE.equals(paso.get("milestone")));
            Object etapa = paso.get("payment_stage");
            t.setEtapaPago(etapa instanceof Number n ? n.intValue() : null);
            t.setCarpetaDropbox(String.valueOf(paso.getOrDefault("dropbox_folder", "")));
            t.setTipo(String.valueOf(paso.getOrDefault("kind", "")));
            t.setUsa(new ArrayList<>((List<String>) paso.getOrDefault("uses", List.of())));
            t.setPosicion(posicion++);
            t.setInicio(inicio);
            int duracion = paso.get("duration") instanceof Number n ? Math.max(n.intValue(), 1) : 1;
            t.setFin(inicio.plusDays(duracion - 1L));
            List<Map<String, Object>> lista = new ArrayList<>();
            for (String texto : (List<String>) paso.getOrDefault("checklist", List.of())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("text", texto);
                item.put("done", false);
                lista.add(item);
            }
            t.setLista(lista);
            List<Map<String, Object>> entregables = new ArrayList<>();
            for (String etiqueta : (List<String>) paso.getOrDefault("deliverables", List.of())) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("key", UUID.randomUUID().toString());
                d.put("label", etiqueta);
                d.put("na", false);
                d.put("na_reason", "");
                d.put("locked", true);
                entregables.add(d);
            }
            t.setEntregables(entregables);
            t.setCreadoEl(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
            porClave.put(String.valueOf(paso.get("key")), t);
        }

        // Dependencias entre las tareas incluidas (las que apuntan a pasos excluidos se descartan)
        for (Map<String, Object> paso : pasos) {
            List<String> deps = new ArrayList<>();
            for (String d : (List<String>) paso.getOrDefault("depends_on", List.of())) {
                if (porClave.containsKey(d)) {
                    deps.add(porClave.get(d).getId());
                }
            }
            porClave.get(String.valueOf(paso.get("key"))).setDependeDe(deps);
        }

        // Todas arrancan en la fecha inicial; el recálculo empuja cada una tras sus predecesoras
        Map<String, pl_TareaProyecto> porId = new LinkedHashMap<>();
        porClave.values().forEach(t -> porId.put(t.getId(), t));
        planificacion.replanificar(porId);

        // El plazo de referencia de cada paso es el que resulta de la planificación inicial
        porClave.values().forEach(t -> t.setFinPrevisto(t.getFin()));

        List<Map<String, Object>> documentos = new ArrayList<>();
        for (Map<String, Object> d : (List<Map<String, Object>>) plantilla.getOrDefault("documents", List.of())) {
            documentos.add(new LinkedHashMap<>(d));
        }
        return new Contenido(new ArrayList<>(porClave.values()), documentos);
    }
}
