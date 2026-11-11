package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/** El progreso de una tarea no se edita a mano: se deduce del estado y de sus subtareas (equivale a progress.py). */
@Service
public class ProgresoService {

    /**
     * Ajusta estado y progreso tras un cambio de `status` o `checklist` (las claves del cambio van en `cambios`).
     * Hecha → 100 %; Pendiente → 0 %; En curso → % de subtareas marcadas (50 % sin subtareas). Marcar subtareas sin tocar el
     * estado lo actualiza: todas → Hecha (si `puedeCompletar`), alguna → En curso.
     */
    public void aplicar(pl_TareaProyecto t, Set<String> cambios, boolean puedeCompletar) {
        var items = t.getLista();
        long hechas = items.stream().filter(i -> Boolean.TRUE.equals(i.get("done"))).count();

        if (cambios.contains("checklist") && !cambios.contains("status") && !items.isEmpty()) {
            if (hechas == items.size()) {
                if (puedeCompletar) {
                    t.setEstado("done");
                } else if ("todo".equals(t.getEstado()) || "done".equals(t.getEstado())) {
                    t.setEstado("doing");
                }
            } else if (hechas > 0 && "todo".equals(t.getEstado())) {
                t.setEstado("doing");
            }
        }

        // En pausa o bloqueada, la tarea conserva el avance que llevaba.
        if ("paused".equals(t.getEstado()) || "blocked".equals(t.getEstado())) {
            return;
        }
        if (cambios.contains("status") || cambios.contains("checklist")) {
            if ("done".equals(t.getEstado())) {
                t.setProgreso(100);
            } else if ("todo".equals(t.getEstado())) {
                t.setProgreso(0);
            } else if (!items.isEmpty()) {
                t.setProgreso((int) Math.round(100.0 * hechas / items.size()));
            } else {
                t.setProgreso(50);
            }
        }
    }
}
