package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Proyecto;
import api.aura.entity.modulo_planificador.pl_TareaProyecto;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSON de proyectos y tareas con las mismas claves que el Planificador actual. */
public final class Mapas {

    private Mapas() {
    }

    /** Hora UTC sin zona, como la devolvía el Planificador actual (2026-11-05T09:08:21.123456). */
    static String hora(LocalDateTime h) {
        return h == null ? null : h.toString();
    }

    public static Map<String, Object> proyecto(pl_Proyecto p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("owner_id", p.getPropietarioId());
        m.put("name", p.getNombre());
        m.put("description", p.getDescripcion());
        m.put("start_date", p.getFechaInicio());
        m.put("documents", p.getDocumentos());
        m.put("baseline", p.getLineaBase());
        m.put("changes", p.getCambios());
        m.put("budget", p.getPresupuesto());
        m.put("contract_party", p.getPartesContrato());
        m.put("contract_generated_at", hora(p.getContratoGeneradoEl()));
        m.put("contract_file", p.getArchivoContrato());
        m.put("payment_plan", p.getPlanPagos());
        m.put("created_at", hora(p.getCreadoEl()));
        m.put("dropbox_path", p.getRutaDropbox());
        m.put("proposal_id", p.getPropuestaId());
        m.put("deleted_at", hora(p.getEliminadoEl()));
        return m;
    }

    public static Map<String, Object> tarea(pl_TareaProyecto t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("project_id", t.getProyectoId());
        m.put("name", t.getNombre());
        m.put("description", t.getDescripcion());
        m.put("phase", t.getFase());
        m.put("department", t.getDepartamento());
        m.put("position", t.getPosicion());
        m.put("milestone", t.isHito());
        m.put("start", t.getInicio());
        m.put("end", t.getFin());
        m.put("planned_end", t.getFinPrevisto());
        m.put("progress", t.getProgreso());
        m.put("status", t.getEstado());
        m.put("status_reason", t.getMotivoEstado());
        m.put("status_note", t.getNotaEstado());
        m.put("completed_at", t.getCompletadaEl());
        m.put("closing_note", t.getNotaCierre());
        m.put("deliverables", t.getEntregables());
        m.put("attachments", t.getAdjuntos());
        m.put("uses", t.getUsa());
        m.put("change_acks", t.getCambiosRevisados());
        m.put("payment_stage", t.getEtapaPago());
        m.put("payment_amount", t.getImportePago());
        m.put("payment_label", t.getEtiquetaPago());
        m.put("dropbox_folder", t.getCarpetaDropbox());
        m.put("notified", t.getAvisadas());
        m.put("due_marks", t.getMarcasVencimiento());
        m.put("depends_on", t.getDependeDe());
        m.put("checklist", t.getLista());
        m.put("kind", t.getTipo());
        m.put("created_at", hora(t.getCreadoEl()));
        return m;
    }
}
