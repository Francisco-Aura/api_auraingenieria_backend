package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.dto.modulo_planificador.PresupuestoGuardarRequest;
import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.entity.modulo_planificador.pl_Presupuesto;
import api.aura.entity.modulo_planificador.pl_Propuesta;
import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import api.aura.repository.modulo_planificador.pl_PropuestaRepository;
import api.aura.repository.modulo_planificador.pl_ProyectoRepository;
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
    static final String SIN_LOGO = "Sube el logo de la empresa del cliente para descargar la propuesta: sale en la presentación.";
    static final String SIN_ENCAJE = "Sube el encaje de los paneles en el proyecto para descargar la propuesta: sale en la presentación.";

    private final pl_PropuestaRepository propuestas;
    private final pl_ProyectoRepository proyectos;
    private final pl_EquipoRepository equipos;
    private final PresupuestoService presupuestos;
    private final PropuestaHtml propuestaHtml;
    private final PropuestaPptx propuestaPptx;
    private final PropuestaPdf propuestaPdf;

    /** Las propuestas de un presupuesto, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(String presupuestoId) {
        presupuestos.buscar(presupuestoId);
        List<Map<String, Object>> salida = new ArrayList<>();
        // Con el logo y el encaje ya puestos (en la propuesta o en los parámetros del presupuesto) no hace falta pedirlos para descargar la presentación
        Map<String, Object> datosPresupuesto = presupuestos.buscar(presupuestoId).getDatos();
        boolean logoDelPresupuesto = !PropuestaDatos.logoValido(datosPresupuesto == null ? null : datosPresupuesto.get("clientLogo")).isEmpty();
        boolean encajeDelPresupuesto = !PropuestaDatos.logoValido(datosPresupuesto == null ? null : datosPresupuesto.get("encaje")).isEmpty();
        for (pl_Propuesta p : propuestas.findByPresupuestoIdOrderByVersionDesc(presupuestoId)) {
            Map<String, Object> d = dto(p);
            d.put("has_logo", logoDelPresupuesto || !p.getLogoCliente().isEmpty());
            // El proyecto que se creó a partir de ella (si lo hay): la pantalla ofrece crearlo o lo enseña
            d.put("project", proyectos.findFirstByPropuestaIdAndEliminadoElIsNull(p.getId()).map(x -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", x.getId());
                m.put("name", x.getNombre());
                return (Object) m;
            }).orElse(null));
            d.put("has_encaje", encajeDelPresupuesto || !p.getEncajeCliente().isEmpty());
            salida.add(d);
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
            throw new ErrorApi(HttpStatus.CONFLICT, "Este presupuesto ya tiene una propuesta validada.");
        }
        if (existentes.stream().anyMatch(p -> EN_CURSO.contains(p.getEstado()))) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Ya hay una propuesta en curso: termínala antes de crear otra.");
        }
        // El logo del cliente y el encaje son los mismos en todas las versiones: la nueva parte de los de la anterior
        pl_Propuesta ultima = existentes.stream().max(Comparator.comparingInt(pl_Propuesta::getVersion)).orElse(null);
        pl_Propuesta p = new pl_Propuesta();
        p.setId(UUID.randomUUID().toString());
        p.setPresupuestoId(b.getId());
        p.setVersion((ultima == null ? 0 : ultima.getVersion()) + 1);
        p.setCreadoEl(PresupuestoService.ahora());
        p.setCreadoPor(nombreActor());
        p.setLogoCliente(ultima == null ? "" : ultima.getLogoCliente());
        p.setEncajeCliente(ultima == null ? "" : ultima.getEncajeCliente());
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
        // Entregada la propuesta, el estudio queda a la espera de la respuesta del cliente
        presupuestos.avanzarEstudio(presupuestos.buscarOpcional(p.getPresupuestoId()), "pte_cliente");
        return dto(p);
    }

    /** Valida la propuesta entregada. Solo quien valida los presupuestos, y con el presupuesto aprobado y su estudio aceptado. */
    @Transactional
    public Map<String, Object> aprobar(String id, String nota) {
        presupuestos.exigirValidador();
        pl_Propuesta p = buscar(id);
        if ("enviada".equals(p.getEstado())) {
            pl_Presupuesto b = presupuestos.buscar(p.getPresupuestoId());
            if (!presupuestos.listoParaValidar(b)) {
                throw new ErrorApi(HttpStatus.CONFLICT, "Para validar la propuesta, el presupuesto tiene que estar aprobado y tener un estudio asociado que no esté rechazado ni cancelado.");
            }
            // Validada la propuesta, el estudio queda aceptado (y bloqueado)
            presupuestos.avanzarEstudio(b, "aceptado");
        }
        return decidir(p, "aprobada", nota);
    }

    /** Anula la propuesta entregada (con el motivo, si se sabe): se podrá crear otra versión. Solo quien valida los presupuestos. */
    @Transactional
    public Map<String, Object> rechazar(String id, String nota) {
        presupuestos.exigirValidador();
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

    /** El encaje de los paneles de esta propuesta: manda sobre el de los parámetros del presupuesto. */
    @Transactional
    public Map<String, Object> guardarEncaje(String id, String encaje) {
        pl_Propuesta p = buscar(id);
        if (encaje == null || encaje.length() > LOGO_CLIENTE_MAX_BYTES * 4 / 3 || !PATRON_LOGO.matcher(encaje).matches()) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "El encaje tiene que ser una imagen PNG, JPG, WebP o SVG de menos de 1,5 MB");
        }
        p.setEncajeCliente(encaje);
        propuestas.save(p);
        return dto(p);
    }

    @Transactional
    public Map<String, Object> borrarEncaje(String id) {
        pl_Propuesta p = buscar(id);
        p.setEncajeCliente("");
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
            throw new ErrorApi(HttpStatus.CONFLICT, "Solo se valida o se anula una propuesta entregada.");
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
        r.put("client_encaje", p.getEncajeCliente());
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
    /**
     * La misma presentación, pero del presupuesto en curso tal como está en pantalla (cliente, datos, kWp, paneles y total, como al guardarlo):
     * es el documento para el cliente que se saca al terminar de confeccionar el presupuesto, sin esperar a validarlo ni a crear la propuesta.
     * No guarda nada. El logo y el encaje son los de los parámetros del presupuesto: se pueden crear sin ellos, pero no descargar la propuesta.
     */
    @Transactional(readOnly = true)
    public Documento htmlDeBorrador(PresupuestoGuardarRequest cuerpo) {
        pl_Presupuesto b = new pl_Presupuesto();
        b.setNombre(cuerpo.getNombre() == null ? "" : cuerpo.getNombre());
        b.setDatos(cuerpo.getDatos());
        b.setKwp(cuerpo.getKwp());
        b.setPaneles(cuerpo.getPaneles());
        b.setTotalPresupuesto(cuerpo.getTotalPresupuesto());
        try {
            Map<String, Object> datos = PropuestaDatos.paraHtml(b, catalogo());
            exigirLogo(datos); // el de los parámetros del presupuesto
            exigirEncaje(datos);
            String nombre = b.getNombre().isEmpty() ? "presupuesto" : b.getNombre();
            return new Documento(propuestaHtml.generar(datos).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    ("Propuesta " + nombre).replaceAll("[\\\\/:*?\"<>|]+", " ").strip() + ".html");
        } catch (ErrorApi e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar la presentación: " + e.getMessage());
        }
    }

    /** La propuesta del presupuesto en curso en PDF (la presentación impresa con Chrome). Sin navegador en el servidor: 501 y el cliente cae al cuadro de impresión. */
    public Documento pdfDeBorrador(PresupuestoGuardarRequest cuerpo) {
        Documento html = htmlDeBorrador(cuerpo);
        try {
            byte[] pdf = propuestaPdf.generar(new String(html.contenido(), java.nio.charset.StandardCharsets.UTF_8));
            return new Documento(pdf, html.nombre().replaceFirst("\\.html$", ".pdf"));
        } catch (PropuestaPdf.SinNavegador e) {
            throw new ErrorApi(HttpStatus.NOT_IMPLEMENTED, e.getMessage());
        } catch (RuntimeException e) {
            throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el PDF: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Documento html(String id) {
        pl_Propuesta p = buscar(id);
        pl_Presupuesto b = presupuestoDe(p);
        try {
            Map<String, Object> datos = PropuestaDatos.paraHtml(b, catalogo());
            // El logo subido en la propuesta manda; si no hay, el de los parámetros del presupuesto (ya está en los datos)
            if (p.getLogoCliente() != null && !p.getLogoCliente().isEmpty()) datos.put("logo", p.getLogoCliente());
            // Lo mismo con el encaje de los paneles
            if (p.getEncajeCliente() != null && !p.getEncajeCliente().isEmpty()) datos.put("encaje", p.getEncajeCliente());
            exigirLogo(datos);
            exigirEncaje(datos);
            return new Documento(propuestaHtml.generar(datos).getBytes(java.nio.charset.StandardCharsets.UTF_8), nombreDeArchivo(b, p) + ".html");
        } catch (ErrorApi e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar la presentación: " + e.getMessage());
        }
    }

    private static final Map<String, String> NOMBRE_ESTRUCTURA = Map.of("coplanar", "Coplanar", "lastrada", "Lastrada", "hincada", "Hincada",
            "marquesina", "Marquesina", "marquesina_flotante", "Marquesina flotante", "inclinada", "Inclinada");

    /**
     * Lo que ya se sabe para crear el proyecto de una propuesta validada, con las claves del formulario de proyecto nuevo: el nombre, los datos
     * del contrato de referencia (`baseline`: importe, paneles, inversor, estructura, batería y potencia, sacados del presupuesto) y la empresa
     * del cliente (`contract_party`). No guarda nada. Solo de una propuesta validada y que todavía no tenga proyecto.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> datosParaProyecto(String id) {
        pl_Propuesta p = buscar(id);
        exigirValidada(p);
        proyectos.findFirstByPropuestaIdAndEliminadoElIsNull(p.getId()).ifPresent(x -> {
            throw new ErrorApi(HttpStatus.CONFLICT, "Esta propuesta ya tiene su proyecto: «" + x.getNombre() + "».");
        });
        pl_Presupuesto b = presupuestoDe(p);
        Map<String, Object> d = PropuestaDatos.paraHtml(b, catalogo());
        String cliente = String.valueOf(d.getOrDefault("client", ""));
        String referencia = String.valueOf(d.getOrDefault("project", ""));
        Map<String, Object> contrato = new LinkedHashMap<>();
        contrato.put("importe_total", b.getTotalPresupuesto() > 0 ? b.getTotalPresupuesto() : null);
        contrato.put("paneles_uds", b.getPaneles() > 0 ? b.getPaneles() : null);
        contrato.put("paneles_modelo", vacioANulo(d.get("panel")));
        int inversores = d.get("inverter_count") instanceof Number n ? n.intValue() : 0;
        contrato.put("inversor_uds", inversores > 0 ? inversores : null);
        contrato.put("inversor_modelo", vacioANulo(d.get("inverter")));
        List<?> estructuras = d.get("structures") instanceof List<?> l ? l : List.of(d.getOrDefault("structure", ""));
        contrato.put("estructura", vacioANulo(estructuras.stream().map(String::valueOf).filter(e -> !e.isEmpty())
                .map(e -> NOMBRE_ESTRUCTURA.getOrDefault(e, e)).distinct().collect(java.util.stream.Collectors.joining(", "))));
        contrato.put("bateria", vacioANulo(d.get("battery")));
        contrato.put("potencia_kwp", b.getKwp() > 0 ? b.getKwp() : null);
        contrato.values().removeIf(Objects::isNull);
        Map<String, Object> parte = new LinkedHashMap<>();
        if (!cliente.isBlank()) parte.put("empresa", cliente);
        if (d.get("cif") != null) parte.put("cif", String.valueOf(d.get("cif")));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("proposal_id", p.getId());
        r.put("name", referencia.isBlank() ? cliente : cliente + " · " + referencia);
        r.put("baseline", contrato);
        r.put("contract_party", parte);
        return r;
    }

    private static Object vacioANulo(Object v) {
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }

    /** Solo una propuesta validada (aprobada por el cliente) da un proyecto. */
    static void exigirValidada(pl_Propuesta p) {
        if (!"aprobada".equals(p.getEstado())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Solo se puede crear un proyecto desde una propuesta validada.");
        }
    }

    /** La propuesta guardada en PDF (la presentación impresa con Chrome, con la página de «Aceptación y firma»). Sin navegador en el servidor: 501 y el cliente cae al cuadro de impresión. */
    @Transactional(readOnly = true)
    public Documento pdf(String id) {
        Documento html = html(id);
        try {
            byte[] pdf = propuestaPdf.generar(new String(html.contenido(), java.nio.charset.StandardCharsets.UTF_8));
            return new Documento(pdf, html.nombre().replaceFirst("\\.html$", ".pdf"));
        } catch (PropuestaPdf.SinNavegador e) {
            throw new ErrorApi(HttpStatus.NOT_IMPLEMENTED, e.getMessage());
        } catch (RuntimeException e) {
            throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el PDF: " + e.getMessage());
        }
    }

    /** El presupuesto se puede crear sin logo del cliente, pero la propuesta no se descarga sin él: sale en la presentación. */
    private static void exigirLogo(Map<String, Object> datos) {
        Object logo = datos.get("logo");
        if (logo == null || String.valueOf(logo).isEmpty()) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, SIN_LOGO);
        }
    }

    /** El encaje de los paneles es obligatorio para descargar la propuesta (como el logo): lleva su diapositiva en la presentación. */
    private static void exigirEncaje(Map<String, Object> datos) {
        Object encaje = datos.get("encaje");
        if (encaje == null || String.valueOf(encaje).isEmpty()) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, SIN_ENCAJE);
        }
    }
}
