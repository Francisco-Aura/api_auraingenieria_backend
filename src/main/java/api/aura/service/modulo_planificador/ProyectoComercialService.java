package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.entity.modulo_planificador.pl_ProyectoComercial;
import api.aura.repository.modulo_planificador.pl_ProyectoComercialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import static api.aura.service.modulo_planificador.PresupuestoService.ahora;
import static api.aura.service.modulo_planificador.PresupuestoService.claveTexto;
import static api.aura.service.modulo_planificador.PresupuestoService.iso;
import static api.aura.service.modulo_planificador.TareaProyectoService.invalido;

/** Proyectos de la lista «Presupuestos y estudios»: se crean con su cliente y, después, se les añaden estudios y presupuestos. */
@Service
@RequiredArgsConstructor
public class ProyectoComercialService {

    private static final int MAX_PROYECTO = 80;
    private static final int LOGO_MAX_CARACTERES = 2_000_000;
    private static final Pattern PATRON_IMAGEN = Pattern.compile("^data:image/(?:png|jpeg|webp|svg\\+xml);base64,[A-Za-z0-9+/=]+$");

    private final pl_ProyectoComercialRepository proyectos;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return proyectos.findAllByOrderByCreadoElDesc(PageRequest.of(0, 500)).stream().map(ProyectoComercialService::resumen).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(String id) {
        return conImagenes(buscar(id));
    }

    /**
     * Cuerpo: {@code customer} (el cliente, existente o potencial), {@code project} (su nombre) y, si se tienen, {@code cif} (el de la
     * empresa; manda sobre el del cliente), {@code logo} y {@code encaje} (imágenes en data URI).
     */
    @Transactional
    public Map<String, Object> crear(Map<String, Object> cuerpo) {
        if (!(cuerpo.get("customer") instanceof Map<?, ?> c)) {
            throw invalido("Elige el cliente de la propuesta (uno existente o un potencial).");
        }
        String nombre = corto(cuerpo.get("project"), 1000);
        if (nombre.isEmpty()) throw invalido("Pon el nombre de la propuesta.");
        if (nombre.length() > MAX_PROYECTO) throw invalido("El nombre de la propuesta admite como máximo " + MAX_PROYECTO + " caracteres.");

        pl_ProyectoComercial p = new pl_ProyectoComercial();
        p.setId(UUID.randomUUID().toString());
        ponerCliente(p, c);
        p.setNombre(nombre);
        ponerEmpresa(p, cuerpo);
        String cliente = "existente".equals(p.getClienteTipo()) ? String.valueOf(p.getClienteId()) : claveTexto(p.getClienteNombre());
        p.setClave(p.getClienteTipo() + "|" + cliente + "|" + claveTexto(nombre));
        if (proyectos.existsByClave(p.getClave())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Este cliente ya tiene una propuesta con ese nombre.");
        }
        p.setCreadoEl(ahora());
        p.setCreadoPor(actor());
        proyectos.save(p);
        return conImagenes(p);
    }

    /** Cambia el CIF, el logo y el encaje (los que lleguen); el cliente y el nombre no se tocan. */
    @Transactional
    public Map<String, Object> actualizar(String id, Map<String, Object> cuerpo) {
        pl_ProyectoComercial p = buscar(id);
        ponerEmpresa(p, cuerpo);
        proyectos.save(p);
        return conImagenes(p);
    }

    private pl_ProyectoComercial buscar(String id) {
        return proyectos.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "La propuesta ya no existe."));
    }

    /** CIF, logo y encaje: solo se cambia lo que llega (un logo vacío lo quita). */
    private static void ponerEmpresa(pl_ProyectoComercial p, Map<String, Object> cuerpo) {
        if (cuerpo.containsKey("cif")) {
            Map<String, Object> cliente = new LinkedHashMap<>(p.getCliente());
            cliente.put("cif", corto(cuerpo.get("cif"), 50));
            p.setCliente(cliente);
        }
        if (cuerpo.containsKey("logo")) p.setLogo(imagen(cuerpo.get("logo"), "logo"));
        if (cuerpo.containsKey("encaje")) p.setEncaje(imagen(cuerpo.get("encaje"), "encaje"));
    }

    private static String imagen(Object v, String que) {
        String t = v == null ? "" : String.valueOf(v).strip();
        if (t.isEmpty()) return "";
        if (t.length() > LOGO_MAX_CARACTERES || !PATRON_IMAGEN.matcher(t).matches()) {
            throw invalido("El " + que + " debe ser una imagen PNG, JPG, WebP o SVG de menos de 1,5 MB.");
        }
        return t;
    }

    /**
     * La propuesta de un presupuesto o de un estudio según su cliente y el nombre de su proyecto. Si ya tenía una y sigue siendo la
     * misma (mismo proyecto y mismo cliente, por id o por nombre) la conserva; si no, busca la del cliente y el proyecto o la crea.
     * Sin nombre de proyecto no hay propuesta: devuelve vacío. {@code clienteJson} es el cliente tal como lo usa la pantalla (opcional).
     */
    @Transactional
    public String resolver(String actualId, String tipo, Integer clienteId, String clienteNombre, Map<?, ?> clienteJson, String nombreProyecto) {
        String proyecto = corto(nombreProyecto, MAX_PROYECTO);
        if (proyecto.isEmpty()) {
            return "";
        }
        String tipoCliente = "existente".equals(tipo) && clienteId != null ? "existente" : "potencial";
        String nombreCliente = corto(clienteNombre, 255);
        if (actualId != null && !actualId.isEmpty()) {
            var actual = proyectos.findById(actualId);
            if (actual.isPresent() && mismaPropuesta(actual.get(), tipoCliente, clienteId, nombreCliente, proyecto)) {
                return actualId;
            }
        }
        String clave = claveDe(tipoCliente, clienteId, nombreCliente, proyecto);
        var existente = proyectos.findByClave(clave);
        if (existente.isPresent()) {
            return existente.get().getId();
        }
        pl_ProyectoComercial p = new pl_ProyectoComercial();
        p.setId(UUID.randomUUID().toString());
        p.setClave(clave);
        p.setClienteTipo(tipoCliente);
        p.setClienteId("existente".equals(tipoCliente) ? clienteId : null);
        p.setClienteNombre(nombreCliente);
        Map<String, Object> cliente = new LinkedHashMap<>();
        if (clienteJson != null && clienteJson.get("kind") != null) {
            clienteJson.forEach((k, v) -> cliente.put(String.valueOf(k), v));
        } else {
            cliente.put("kind", tipoCliente);
            if (clienteId != null && "existente".equals(tipoCliente)) cliente.put("id", clienteId);
            cliente.put("name", nombreCliente);
        }
        p.setCliente(cliente);
        p.setNombre(proyecto);
        p.setCreadoEl(ahora());
        p.setCreadoPor(actor());
        proyectos.save(p);
        return p.getId();
    }

    private static boolean mismaPropuesta(pl_ProyectoComercial p, String tipoCliente, Integer clienteId, String nombreCliente, String proyecto) {
        boolean mismoProyecto = claveTexto(p.getNombre()).equals(claveTexto(proyecto));
        boolean mismoCliente = "existente".equals(tipoCliente) && "existente".equals(p.getClienteTipo()) && Objects.equals(p.getClienteId(), clienteId)
                || claveTexto(p.getClienteNombre()).equals(claveTexto(nombreCliente));
        return mismoProyecto && mismoCliente;
    }

    /** La clave única de una propuesta: tipo de cliente, cliente (su id en la intranet o su nombre normalizado) y proyecto normalizado. */
    static String claveDe(String tipoCliente, Integer clienteId, String clienteNombre, String proyecto) {
        String cliente = "existente".equals(tipoCliente) ? String.valueOf(clienteId) : claveTexto(clienteNombre);
        return tipoCliente + "|" + cliente + "|" + claveTexto(proyecto);
    }

    /** Un proyecto se elimina como cosa vacía; sus estudios y presupuestos, si los tiene, no se tocan. */
    @Transactional
    public Map<String, Object> eliminar(String id) {
        proyectos.delete(buscar(id));
        return Map.of("id", id, "deleted", true);
    }

    /** El cliente como en estudios y presupuestos: uno de la intranet (con su id) o un potencial (sin id). */
    private static void ponerCliente(pl_ProyectoComercial p, Map<?, ?> c) {
        String tipo = String.valueOf(c.get("kind"));
        String nombre = corto(c.get("name"), 1000);
        if (nombre.isEmpty()) throw invalido("Pon el nombre del cliente.");
        if (nombre.length() > 255) throw invalido("El nombre del cliente admite como máximo 255 caracteres.");
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("kind", tipo);
        Integer idIntranet = null;
        if ("existente".equals(tipo)) {
            try {
                idIntranet = c.get("id") == null ? null : (int) Numeros.aNumero(c.get("id"));
            } catch (NumberFormatException ex) {
                idIntranet = null;
            }
            if (idIntranet == null) throw invalido("Falta el cliente de la intranet al que se vincula la propuesta.");
            cliente.put("id", idIntranet);
            cliente.put("name", nombre);
            cliente.put("tradeName", corto(c.get("tradeName"), 255));
            cliente.put("cif", corto(c.get("cif"), 50));
        } else if ("potencial".equals(tipo)) {
            if (c.get("id") != null) throw invalido("Un cliente potencial no tiene id en la intranet.");
            cliente.put("name", nombre);
            cliente.put("cif", corto(c.get("cif"), 50));
            cliente.put("phone", corto(c.get("phone"), 40));
            cliente.put("email", corto(c.get("email"), 120));
        } else {
            throw invalido("Elige el cliente de la propuesta (uno existente o un potencial).");
        }
        p.setClienteTipo(tipo);
        p.setClienteId(idIntranet);
        p.setClienteNombre(nombre);
        p.setCliente(cliente);
    }

    private static Map<String, Object> conImagenes(pl_ProyectoComercial p) {
        Map<String, Object> m = resumen(p);
        m.put("logo", p.getLogo());
        m.put("encaje", p.getEncaje());
        return m;
    }

    /** Sin las imágenes (pesan): la lista solo dice si las tiene. */
    private static Map<String, Object> resumen(pl_ProyectoComercial p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("customer", p.getCliente());
        m.put("project", p.getNombre());
        m.put("created_at", iso(p.getCreadoEl()));
        m.put("created_by", p.getCreadoPor());
        m.put("has_logo", !p.getLogo().isEmpty());
        m.put("has_encaje", !p.getEncaje().isEmpty());
        return m;
    }

    private static String corto(Object v, int max) {
        String t = v == null ? "" : String.valueOf(v).strip();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String actor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        return u == null || u.nombre() == null || u.nombre().isBlank() ? "" : u.nombre();
    }
}
