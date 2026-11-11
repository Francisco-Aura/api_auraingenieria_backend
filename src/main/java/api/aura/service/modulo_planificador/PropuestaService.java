package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.entity.modulo_planificador.pl_Presupuesto;
import api.aura.entity.modulo_planificador.pl_Propuesta;
import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import api.aura.repository.modulo_planificador.pl_PropuestaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Propuesta al cliente: segunda fase de un presupuesto validado. Es un objeto aparte del presupuesto, con versiones:
 * si el cliente la rechaza se crea otra sin tocar el presupuesto validado.
 */
@Service
@RequiredArgsConstructor
public class PropuestaService {

    private static final List<String> EN_CURSO = List.of("borrador", "enviada");
    private static final Pattern PATRON_LOGO =
            Pattern.compile("^data:(image/(?:png|jpeg|webp|svg\\+xml));base64,([A-Za-z0-9+/=]+)$");
    private static final int LOGO_CLIENTE_MAX_BYTES = 1_500_000;

    private final pl_PropuestaRepository propuestas;
    private final pl_EquipoRepository equipos;
    private final PresupuestoService presupuestos;
    private final PropuestaHtml propuestaHtml;
    private final PropuestaPptx propuestaPptx;

    /** Las propuestas de un presupuesto, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(String presupuestoId) {
        presupuestos.buscar(presupuestoId);
        List<Map<String, Object>> salida = new ArrayList<>();
        for (pl_Propuesta p : propuestas.findByPresupuestoIdOrderByVersionDesc(presupuestoId)) {
            salida.add(dto(p));
        }
        return salida;
    }

    /** Crea la propuesta (borrador) de un presupuesto validado. Solo una en curso a la vez, y ninguna más si ya hay una aprobada. */
    @Transactional
    public Map<String, Object> crear(String presupuestoId) {
        pl_Presupuesto b = presupuestos.buscar(presupuestoId);
        if (!"validado".equals(b.getEstado())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Solo se hace la propuesta al cliente de un presupuesto validado.");
        }
        List<pl_Propuesta> existentes = propuestas.findByPresupuestoId(b.getId());
        if (existentes.stream().anyMatch(p -> "aprobada".equals(p.getEstado()))) {
            throw new ErrorApi(HttpStatus.CONFLICT, "El cliente ya aprobó una propuesta de este presupuesto.");
        }
        if (existentes.stream().anyMatch(p -> EN_CURSO.contains(p.getEstado()))) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Ya hay una propuesta en curso: termínala antes de crear otra.");
        }
        // El logo del cliente es el mismo en todas las versiones: la nueva parte del de la anterior
        pl_Propuesta ultima = existentes.stream().max(Comparator.comparingInt(pl_Propuesta::getVersion)).orElse(null);
        pl_Propuesta p = new pl_Propuesta();
        p.setId(UUID.randomUUID().toString());
        p.setPresupuestoId(b.getId());
        p.setVersion((ultima == null ? 0 : ultima.getVersion()) + 1);
        p.setCreadoEl(PresupuestoService.ahora());
        p.setCreadoPor(nombreActor());
        p.setLogoCliente(ultima == null ? "" : ultima.getLogoCliente());
        propuestas.save(p);
        return dto(p);
    }

    /** Anota que la propuesta se ha enviado al cliente. */
    @Transactional
    public Map<String, Object> enviar(String id) {
        pl_Propuesta p = buscar(id);
        if (!"borrador".equals(p.getEstado())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "La propuesta ya está " + p.getEstado() + ".");
        }
        p.setEstado("enviada");
        p.setEnviadoEl(PresupuestoService.ahora());
        p.setEnviadoPor(nombreActor());
        propuestas.save(p);
        return dto(p);
    }

    @Transactional
    public Map<String, Object> aprobar(String id, String nota) {
        return decidir(buscar(id), "aprobada", nota);
    }

    /** Anota que el cliente rechazó la propuesta (con el motivo, si se sabe): se podrá crear otra versión. */
    @Transactional
    public Map<String, Object> rechazar(String id, String nota) {
        return decidir(buscar(id), "rechazada", nota);
    }

    @Transactional
    public Map<String, Object> guardarLogo(String id, String logo) {
        pl_Propuesta p = buscar(id);
        if (logo == null || logo.length() > LOGO_CLIENTE_MAX_BYTES * 4 / 3 || !PATRON_LOGO.matcher(logo).matches()) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "El logo tiene que ser una imagen PNG, JPG, WebP o SVG de menos de 1,5 MB");
        }
        p.setLogoCliente(logo);
        propuestas.save(p);
        return dto(p);
    }

    @Transactional
    public Map<String, Object> borrarLogo(String id) {
        pl_Propuesta p = buscar(id);
        p.setLogoCliente("");
        propuestas.save(p);
        return dto(p);
    }

    /** Elimina una propuesta que sigue en borrador (las enviadas y contestadas quedan como historial). */
    @Transactional
    public void eliminar(String id) {
        pl_Propuesta p = propuestas.findById(id).orElse(null);
        if (p == null) {
            return;
        }
        if (!"borrador".equals(p.getEstado())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Solo se elimina una propuesta que sigue en borrador.");
        }
        pl_Presupuesto b = presupuestos.buscarOpcional(p.getPresupuestoId());
        if (b != null && !presupuestos.esPropietario(b)) {
            throw new ErrorApi(HttpStatus.FORBIDDEN, "Solo " + b.getPropietario() + " puede eliminar esta propuesta.");
        }
        propuestas.delete(p);
    }

    /**
     * Las garantías que faltan en el inventario para los equipos del presupuesto (módulo, inversor y batería): hoy la regla
     * está desactivada (la descarga ya no se bloquea si faltan) y se conserva por si se reactiva.
     */
    List<String> garantiasQueFaltan(pl_Presupuesto b) {
        Map<String, pl_Equipo> porId = equipos.findAll().stream().collect(Collectors.toMap(pl_Equipo::getId, e -> e));
        Map<String, pl_Equipo> primero = new LinkedHashMap<>();
        if (b.getDatos().get("lines") instanceof List<?> lineas) {
            for (Object l : lineas) {
                if (l instanceof Map<?, ?> m && m.get("itemId") != null) {
                    pl_Equipo e = porId.get(String.valueOf(m.get("itemId")));
                    if (e != null && List.of("modulos", "inversores", "baterias").contains(e.getCategoria())) {
                        primero.putIfAbsent(e.getCategoria(), e);
                    }
                }
            }
        }
        List<String> faltan = new ArrayList<>();
        pl_Equipo modulo = primero.get("modulos");
        if (modulo != null) {
            if (anios(modulo, "garantia_anos") <= 0) {
                faltan.add("la garantía del producto del módulo " + nombre(modulo));
            }
            if (anios(modulo, "garantia_prod_anos") <= 0) {
                faltan.add("la garantía de producción del módulo " + nombre(modulo));
            }
        }
        if (primero.containsKey("inversores") && anios(primero.get("inversores"), "garantia_anos") <= 0) {
            faltan.add("la garantía del inversor " + nombre(primero.get("inversores")));
        }
        if (primero.containsKey("baterias") && anios(primero.get("baterias"), "garantia_anos") <= 0) {
            faltan.add("la garantía de la batería " + nombre(primero.get("baterias")));
        }
        return faltan;
    }

    // ---------------------------------------------------------------- internos

    private pl_Propuesta buscar(String id) {
        return propuestas.findById(id).orElseThrow(() ->
                new ErrorApi(HttpStatus.NOT_FOUND, "La propuesta ya no existe: puede que la hayan eliminado."));
    }

    private Map<String, Object> decidir(pl_Propuesta p, String estado, String nota) {
        if (!"enviada".equals(p.getEstado())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Solo contesta el cliente a una propuesta enviada.");
        }
        p.setEstado(estado);
        p.setDecididoEl(PresupuestoService.ahora());
        p.setDecididoPor(nombreActor());
        String limpia = nota == null ? "" : nota.strip();
        p.setNota(limpia.length() > 1000 ? limpia.substring(0, 1000) : limpia);
        propuestas.save(p);
        return dto(p);
    }

    private static String nombreActor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        return u == null || u.nombre() == null ? "" : u.nombre();
    }

    private static String nombre(pl_Equipo e) {
        String n = (e.getMarca() + " " + e.getModelo()).strip();
        return n.isEmpty() ? "sin nombre" : n;
    }

    /** El número de un campo del inventario escrito a mano («12», «87,4», «12 años»); 0 si no hay. */
    static double anios(pl_Equipo e, String clave) {
        Object valor = e.getAtributos().get(clave);
        Matcher m = Pattern.compile("\\d+(?:[.,]\\d+)?").matcher(valor == null ? "" : String.valueOf(valor));
        return m.find() ? Double.parseDouble(m.group().replace(',', '.')) : 0.0;
    }

    static Map<String, Object> dto(pl_Propuesta p) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", p.getId());
        r.put("budget_id", p.getPresupuestoId());
        r.put("version", p.getVersion());
        r.put("status", p.getEstado());
        r.put("note", p.getNota());
        r.put("created_at", PresupuestoService.iso(p.getCreadoEl()));
        r.put("created_by", p.getCreadoPor());
        r.put("sent_at", p.getEnviadoEl() == null ? null : PresupuestoService.iso(p.getEnviadoEl()));
        r.put("sent_by", p.getEnviadoPor());
        r.put("decided_at", p.getDecididoEl() == null ? null : PresupuestoService.iso(p.getDecididoEl()));
        r.put("decided_by", p.getDecididoPor());
        r.put("client_logo", p.getLogoCliente());
        return r;
    }

    // ---------------------------------------------------------------------------------- documentos

    /** Un archivo generado y el nombre con el que se descarga. */
    public record Documento(byte[] contenido, String nombre) {
    }

    private Map<String, pl_Equipo> catalogo() {
        Map<String, pl_Equipo> mapa = new HashMap<>();
        equipos.findAll().forEach(e -> mapa.put(e.getId(), e));
        return mapa;
    }

    private static String nombreDeArchivo(pl_Presupuesto b, pl_Propuesta p) {
        String nombre = b.getNombre() == null || b.getNombre().isEmpty() ? "presupuesto" : b.getNombre();
        return ("Propuesta " + nombre + " v" + p.getVersion()).replaceAll("[\\\\/:*?\"<>|]+", " ").strip();
    }

    private pl_Presupuesto presupuestoDe(pl_Propuesta p) {
        return presupuestos.buscar(p.getPresupuestoId());
    }

    /** El PowerPoint de la propuesta, con los datos del presupuesto validado al que pertenece. */
    @Transactional(readOnly = true)
    public Documento pptx(String id) {
        pl_Propuesta p = buscar(id);
        pl_Presupuesto b = presupuestoDe(p);
        try {
            return new Documento(propuestaPptx.generar(PropuestaDatos.paraPptx(b, catalogo())), nombreDeArchivo(b, p) + ".pptx");
        } catch (RuntimeException e) {
            throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el PowerPoint: " + e.getMessage());
        }
    }

    /**
     * La presentación de la propuesta (HTML animado, imprimible a PDF), con todo lo que se puede tomar del presupuesto validado y con las
     * imágenes incrustadas: un solo archivo que se descarga y se puede mandar. Lo que el presupuesto no tiene va en blanco.
     */
    @Transactional(readOnly = true)
    public Documento html(String id) {
        pl_Propuesta p = buscar(id);
        pl_Presupuesto b = presupuestoDe(p);
        try {
            Map<String, Object> datos = PropuestaDatos.paraHtml(b, catalogo());
            datos.put("logo", p.getLogoCliente());
            return new Documento(propuestaHtml.generar(datos).getBytes(java.nio.charset.StandardCharsets.UTF_8), nombreDeArchivo(b, p) + ".html");
        } catch (RuntimeException e) {
            throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar la presentación: " + e.getMessage());
        }
    }
}
