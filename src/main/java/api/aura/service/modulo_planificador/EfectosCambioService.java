package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import api.aura.service.modulo_planificador.FacturacionService.Anotacion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Efecto de los cambios del contrato sobre las tareas (equivale a change_effects.py).
 *
 * <p>Cambiar equipos o potencia afecta a las tareas que usan esos datos: las abiertas quedan marcadas para revisar y las que ya estaban
 * hechas se reabren, porque lo que hicieron ya no es lo que dice el contrato. El dinero no reabre nada: se aplica a los pagos que quedan
 * por facturar (ver FacturacionService).
 */
@Service
@RequiredArgsConstructor
public class EfectosCambioService {

    private final ProgresoService progreso;

    /** Reabre las tareas hechas que usan algún dato que un cambio nuevo (o modificado) ha tocado. */
    @SuppressWarnings("unchecked")
    public List<Anotacion> reabrirPorCambios(List<Map<String, Object>> antiguos, List<Map<String, Object>> nuevos, Map<String, pl_TareaProyecto> tareas) {
        Map<String, Map<String, Object>> antiguosPorId = new HashMap<>();
        for (Map<String, Object> c : antiguos) antiguosPorId.put((String) c.get("id"), c);
        List<Anotacion> anotaciones = new ArrayList<>();
        int numero = 0;
        for (Map<String, Object> cambio : nuevos) {
            numero++;
            Map<String, Object> previo = antiguosPorId.get(cambio.get("id")) == null ? Map.of()
                    : antiguosPorId.get(cambio.get("id")).get("set") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            // Solo cuentan los datos nuevos o con otro valor: guardar de nuevo un cambio igual no reabre nada.
            Set<String> tocados = new TreeSet<>();
            ((Map<String, Object>) cambio.get("set")).forEach((k, v) -> {
                if (!Objects.equals(previo.get(k), v)) tocados.add(k);
            });
            if (tocados.isEmpty()) {
                continue;
            }
            StringJoiner etiquetas = new StringJoiner(", ");
            for (String k : tocados) {
                if (ContratoProyectoService.CAMPO_POR_CLAVE.containsKey(k)) etiquetas.add((String) ContratoProyectoService.CAMPO_POR_CLAVE.get(k).get("label"));
            }
            for (pl_TareaProyecto t : tareas.values()) {
                if (!"done".equals(t.getEstado()) || t.getUsa().stream().noneMatch(tocados::contains)) {
                    continue;
                }
                t.setEstado("doing");
                t.setCompletadaEl(null);
                t.setAvisadas(new ArrayList<>()); // al volver a terminarla por el cambio, se vuelve a avisar a las siguientes
                progreso.aplicar(t, Set.of("status"), true);
                // Vuelve a quedar pendiente de revisar este cambio.
                String id = (String) cambio.get("id");
                t.setCambiosRevisados(t.getCambiosRevisados().stream().filter(a -> !a.equals(id)).collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
                anotaciones.add(new Anotacion("status", "Reabrió la tarea por el cambio nº " + numero + " del contrato", etiquetas.toString(), t));
            }
        }
        return anotaciones;
    }
}
