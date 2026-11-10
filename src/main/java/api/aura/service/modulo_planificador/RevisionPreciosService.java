package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.entity.modulo_planificador.pl_Meta;
import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import api.aura.repository.modulo_planificador.pl_MetaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Revisión de precios: a los 15 días de la ÚLTIMA revisión de cualquier producto toca avisar a Ingeniería.
 * Aquí el estado, el marcado de productos revisados y el envío del aviso (lo lanza {@link VencimientosService} cada día).
 */
@Service
@RequiredArgsConstructor
public class RevisionPreciosService {

    public static final int PLAZO_DIAS = 15;
    static final String GRUPO = "ingenieria";
    static final String META_BASE = "price_review_baseline";
    static final String META_AVISADO = "price_review_notified";

    private final pl_EquipoRepository equipos;
    private final pl_MetaRepository meta;
    private final AvisosPort avisos;

    @Transactional
    public Map<String, Object> estado(LocalDate hoy) {
        LocalDate ultima = ultimaRevision(hoy);
        LocalDate toca = ultima.plusDays(PLAZO_DIAS);
        String avisado = meta.findById(META_AVISADO).map(pl_Meta::getValor).orElse(null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("last_review", ultima.toString());
        r.put("due_date", toca.toString());
        r.put("days_since", ChronoUnit.DAYS.between(ultima, hoy));
        r.put("days_left", ChronoUnit.DAYS.between(hoy, toca));
        r.put("notified", ultima.toString().equals(avisado));
        r.put("reviewed_products", equipos.countByPrecioRevisadoElIsNotNull());
        r.put("plazo", PLAZO_DIAS);
        return r;
    }

    private LocalDate ultimaRevision(LocalDate hoy) {
        LocalDate ultima = equipos.ultimaRevision();
        if (ultima != null) {
            return ultima;
        }
        return meta.findById(META_BASE).map(m -> LocalDate.parse(m.getValor())).orElseGet(() -> {
            meta.save(new pl_Meta(META_BASE, hoy.toString()));
            return hoy;
        });
    }

    /**
     * Avisa a Ingeniería si han pasado 15 días desde el último día de revisión y aún no se había avisado por esa fecha (equivale a
     * price_review.run). Con {@code soloMarcar} da por avisado sin enviar. Si falla el envío no se anota y se reintenta otro día.
     */
    @Transactional
    public Map<String, Object> avisar(LocalDate hoy, boolean soloMarcar) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("price_review_sent", 0);
        r.put("price_review_marked", 0);
        r.put("price_review_failed", 0);
        LocalDate ultima = ultimaRevision(hoy);
        if (hoy.isBefore(ultima.plusDays(PLAZO_DIAS))) {
            return r; // todavía dentro del plazo
        }
        if (ultima.toString().equals(meta.findById(META_AVISADO).map(pl_Meta::getValor).orElse(null))) {
            return r; // ya se avisó por esta revisión: no se repite hasta que haya una nueva
        }
        if (soloMarcar) {
            meta.save(new pl_Meta(META_AVISADO, ultima.toString()));
            r.put("price_review_marked", 1);
            return r;
        }
        Map<String, Object> aviso = new LinkedHashMap<>();
        aviso.put("source", "planificador");
        aviso.put("group", GRUPO);
        aviso.put("groupName", AvisosService.nombreGrupo(GRUPO));
        aviso.put("excludeUserId", null);
        aviso.put("excludeUser", null);
        aviso.put("title", "Planificador · Precios del inventario");
        aviso.put("message", mensaje(ultima, hoy));
        // Al pulsar el aviso se abre el planificador en el inventario
        aviso.put("link", "revisar=precios");
        aviso.put("projectId", null);
        aviso.put("projectName", "Inventario");
        aviso.put("kind", "revision-precios");
        aviso.put("nextTasks", List.of());
        try {
            avisos.enviar(aviso);
        } catch (AvisoError e) {
            r.put("price_review_failed", 1);
            return r;
        }
        meta.save(new pl_Meta(META_AVISADO, ultima.toString()));
        r.put("price_review_sent", 1);
        return r;
    }

    static String mensaje(LocalDate ultima, LocalDate hoy) {
        return "Toca revisar los precios de los productos del inventario: la última revisión fue el "
                + ultima.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " (hace " + ChronoUnit.DAYS.between(ultima, hoy) + " días).";
    }

    /** Da por revisados hoy los precios de estos productos, sin cambiarlos. */
    @Transactional
    public Map<String, Object> marcarRevisados(List<String> ids, LocalDate hoy) {
        List<pl_Equipo> productos = equipos.findAllById(ids == null ? List.of() : ids);
        productos.forEach(e -> e.setPrecioRevisadoEl(hoy));
        equipos.saveAll(productos);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("reviewed", productos.size());
        r.put("date", hoy.toString());
        return r;
    }
}
