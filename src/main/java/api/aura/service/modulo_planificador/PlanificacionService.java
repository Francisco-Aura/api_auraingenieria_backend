package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/** Reglas de dependencias entre tareas (fin → inicio) y recálculo de fechas (equivale a scheduling.py). */
@Service
public class PlanificacionService {

    private static ErrorApi error(String mensaje) {
        return new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }

    /** Orden en el que cada tarea aparece después de sus predecesoras; null si hay un ciclo. */
    List<String> ordenTopologico(Map<String, pl_TareaProyecto> tareas) {
        Map<String, Integer> gradoEntrada = new LinkedHashMap<>();
        Map<String, List<String>> sucesoras = new LinkedHashMap<>();
        tareas.keySet().forEach(id -> { gradoEntrada.put(id, 0); sucesoras.put(id, new ArrayList<>()); });
        tareas.forEach((id, t) -> {
            for (String dep : t.getDependeDe()) {
                if (tareas.containsKey(dep)) {
                    gradoEntrada.merge(id, 1, Integer::sum);
                    sucesoras.get(dep).add(id);
                }
            }
        });
        List<String> listas = new ArrayList<>();
        gradoEntrada.forEach((id, n) -> { if (n == 0) listas.add(id); });
        List<String> orden = new ArrayList<>();
        while (!listas.isEmpty()) {
            String id = listas.remove(listas.size() - 1);
            orden.add(id);
            for (String siguiente : sucesoras.get(id)) {
                if (gradoEntrada.merge(siguiente, -1, Integer::sum) == 0) {
                    listas.add(siguiente);
                }
            }
        }
        return orden.size() == tareas.size() ? orden : null;
    }

    /** Comprueba las dependencias de `tarea` (que ya debe figurar en `proyecto`). */
    public void validarDependencias(pl_TareaProyecto tarea, Map<String, pl_TareaProyecto> proyecto) {
        for (String dep : tarea.getDependeDe()) {
            if (dep.equals(tarea.getId())) {
                throw error("Una tarea no puede depender de sí misma");
            }
            if (!proyecto.containsKey(dep)) {
                throw error("Solo se puede depender de tareas del mismo proyecto");
            }
        }
        if (ordenTopologico(proyecto) == null) {
            throw error("La dependencia crearía un ciclo");
        }
    }

    /**
     * Prorroga lo que ya debería haber terminado y arrastra a lo que depende de ello. Una tarea no hecha cuyo fin quedó
     * atrás se alarga hasta hoy (conserva su inicio). No toca `finPrevisto`, que permite medir el retraso.
     */
    public List<pl_TareaProyecto> prorrogarVencidas(Map<String, pl_TareaProyecto> proyecto, LocalDate hoy) {
        List<pl_TareaProyecto> cambiadas = new ArrayList<>();
        for (pl_TareaProyecto t : proyecto.values()) {
            if (!"done".equals(t.getEstado()) && t.getFin().isBefore(hoy)) {
                t.setFin(hoy);
                cambiadas.add(t);
            }
        }
        if (!cambiadas.isEmpty()) {
            for (pl_TareaProyecto t : replanificar(proyecto)) {
                if (!cambiadas.contains(t)) {
                    cambiadas.add(t);
                }
            }
        }
        return cambiadas;
    }

    /** Empuja hacia delante las tareas que empiezan antes de que terminen sus predecesoras (conserva la duración). */
    public List<pl_TareaProyecto> replanificar(Map<String, pl_TareaProyecto> proyecto) {
        List<String> orden = ordenTopologico(proyecto);
        if (orden == null) {
            throw error("Las dependencias contienen un ciclo");
        }
        List<pl_TareaProyecto> cambiadas = new ArrayList<>();
        for (String id : orden) {
            pl_TareaProyecto t = proyecto.get(id);
            LocalDate masTemprano = null;
            for (String dep : t.getDependeDe()) {
                pl_TareaProyecto p = proyecto.get(dep);
                if (p != null && (masTemprano == null || p.getFin().isAfter(masTemprano))) {
                    masTemprano = p.getFin();
                }
            }
            if (masTemprano == null) {
                continue;
            }
            LocalDate primero = masTemprano.plusDays(1);
            if (t.getInicio().isBefore(primero)) {
                long desplazamiento = java.time.temporal.ChronoUnit.DAYS.between(t.getInicio(), primero);
                t.setInicio(t.getInicio().plusDays(desplazamiento));
                t.setFin(t.getFin().plusDays(desplazamiento));
                cambiadas.add(t);
            }
        }
        return cambiadas;
    }
}
