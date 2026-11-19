package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.dto.modulo_planificador.PresupuestoGuardarRequest;
import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.entity.modulo_planificador.pl_Estudio;
import api.aura.entity.modulo_planificador.pl_Presupuesto;
import api.aura.entity.modulo_planificador.pl_Propuesta;
import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import api.aura.repository.modulo_planificador.pl_EstudioRepository;
import api.aura.repository.modulo_planificador.pl_PresupuestoRepository;
import api.aura.repository.modulo_planificador.pl_PropuestaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Presupuestos del presupuestador: borradores compartidos, envío, variantes, validación y descarte.
 * Estados: borrador → enviado (bloquea la edición) → validado (solo quien valida) o descartado.
 */
@Service
@RequiredArgsConstructor
public class PresupuestoService {

    /** Desconectado: al validar, un cliente potencial ya NO se da de alta en la intranet (sigue siendo potencial en el presupuesto). */
    static final boolean REGISTRAR_CLIENTES_POTENCIALES = false;

    private static final int MAX_DATOS = 1_500_000;    private static final Pattern ID_VALIDO = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final int NIVEL_ADMIN = 1;
    private static final String SIN_IDENTIFICAR = "Sin identificar";
    /** El nombre de un presupuesto es «Cliente · Proyecto»: lo que va tras este separador es el proyecto. */
    private static final String SEPARADOR_PROYECTO = " · ";
    private static final Pattern VERSION_FINAL = Pattern.compile("\\s*(\\(copia\\)|v\\d+)$");
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    private final pl_PresupuestoRepository presupuestos;
    private final pl_PropuestaRepository propuestas;
    private final pl_EstudioRepository estudios;
    private final pl_EquipoRepository equipos;
    private final ClientesIntranetPort clientes;
    private final ObjectMapper json;

    /** Ids de la intranet de quienes validan presupuestos (el jefe). */
    @Value("${planificador.validadores:1}")
    private String validadores;

    // ----------------------------------------------------------------- consulta

    /** Los borradores de todos los usuarios, del modificado más reciente al más antiguo (sin el contenido). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        Map<String, pl_Propuesta> ultimas = ultimasPropuestas(null);
        return presupuestos.findAllByOrderByActualizadoElDesc().stream()
                .map(b -> resumen(b, ultimas.get(b.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(String id) {
        pl_Presupuesto b = buscar(id);
        return conDatos(b, ultimasPropuestas(List.of(id)).get(id));
    }

    /**
     * Lo que tiene pendiente quien valida: las ofertas enviadas que esperan validación (una oferta con varias variantes cuenta
     * una vez) y las propuestas ya enviadas al cliente que esperan su respuesta. A quien no valida se le devuelve 0.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> pendientesDeValidar() {
        Map<String, Object> r = new LinkedHashMap<>();
        if (!esValidador()) {
            r.put("validator", false);
            r.put("count", 0);
            r.put("to_validate", 0);
            r.put("proposals_sent", 0);
            return r;
        }
        // Lo del validador son las propuestas entregadas cuyo presupuesto está aprobado y su estudio aceptado: ya se pueden validar
        int porValidar = (int) propuestas.findAll().stream().filter(p -> "enviada".equals(p.getEstado()))
                .filter(p -> presupuestos.findById(p.getPresupuestoId()).filter(this::estudioAceptado).isPresent()).count();
        r.put("validator", true);
        r.put("count", porValidar);
        r.put("to_validate", porValidar);
        r.put("proposals_sent", porValidar);
        return r;
    }

    // --------------------------------------------------------------------- alta

    @Transactional
    public Map<String, Object> crear(PresupuestoGuardarRequest cuerpo) {
        comprobarDatos(cuerpo);
        if (cuerpo.getId() != null && !ID_VALIDO.matcher(cuerpo.getId()).matches()) {
            throw invalido("Identificador de presupuesto no válido.");
        }
        if (cuerpo.getId() != null && presupuestos.existsById(cuerpo.getId())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Este presupuesto ya está guardado como borrador.");
        }
        String quien = nombreActor();
        pl_Presupuesto b = nuevo();
        b.setId(cuerpo.getId() != null && !cuerpo.getId().isEmpty() ? cuerpo.getId() : UUID.randomUUID().toString());
        b.setNombre(cuerpo.getNombre().strip());
        b.setPropietario(quien);
        b.setPropietarioId(idActor() == null ? "" : String.valueOf(idActor()));
        b.setClienteId(cuerpo.getClienteId());
        b.setClienteTipo(cuerpo.getClienteTipo());
        b.setDatos(cuerpo.getDatos());
        b.setKwp(cuerpo.getKwp());
        b.setPaneles(cuerpo.getPaneles());
        b.setTotalPresupuesto(cuerpo.getTotalPresupuesto());
        b.setActualizadoPor(quien);
        String estudio = cuerpo.getEstudioId() == null ? "" : cuerpo.getEstudioId().strip();
        if (!estudio.isEmpty()) {
            pl_Estudio e = estudios.findById(estudio).orElseThrow(() ->
                    new ErrorApi(HttpStatus.NOT_FOUND, "El estudio ya no existe: puede que lo hayan eliminado."));
            String motivo = motivoIncompatible(b, e);
            if (motivo != null) {
                throw new ErrorApi(HttpStatus.CONFLICT, motivo);
            }
            b.setEstudioId(estudio);
        }
        asignarOferta(b, false);
        presupuestos.save(b);
        return conDatos(b, null);
    }

    /** Guarda los cambios de un borrador. Cualquier usuario puede; si otra persona guardó desde que se abrió, avisa (409). */
    @Transactional
    public Map<String, Object> actualizar(String id, PresupuestoGuardarRequest cuerpo) {
        comprobarDatos(cuerpo);
        pl_Presupuesto b = buscar(id);
        if (!"borrador".equals(b.getEstado())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Un presupuesto " + b.getEstado() + " no se edita: duplícalo como variante para cambiar algo.");
        }
        if (!cuerpo.isForzar() && !Objects.equals(cuerpo.getVersion(), b.getVersion())) {
            String quien = b.getActualizadoPor().isEmpty() ? "Otra persona" : b.getActualizadoPor();
            throw new ErrorApi(HttpStatus.CONFLICT, quien + " ha guardado una versión más reciente de este presupuesto mientras lo tenías abierto.");
        }
        String proyectoAntes = claveProyecto(b.getNombre());
        String tipoAntes = b.getClienteTipo();
        Integer clienteAntes = b.getClienteId();
        b.setNombre(cuerpo.getNombre().strip());
        b.setClienteId(cuerpo.getClienteId());
        b.setClienteTipo(cuerpo.getClienteTipo());
        b.setDatos(cuerpo.getDatos());
        b.setKwp(cuerpo.getKwp());
        b.setPaneles(cuerpo.getPaneles());
        b.setTotalPresupuesto(cuerpo.getTotalPresupuesto());
        boolean cambioDeProyecto = !proyectoAntes.equals(claveProyecto(b.getNombre()))
                || !Objects.equals(tipoAntes, b.getClienteTipo()) || !Objects.equals(clienteAntes, b.getClienteId());
        if (cambioDeProyecto && !b.getEstudioId().isEmpty()) {
            estudios.findById(b.getEstudioId()).ifPresent(e -> {
                String motivo = motivoIncompatible(b, e);
                if (motivo != null) {
                    throw new ErrorApi(HttpStatus.CONFLICT, "Está asociado a un estudio. Quita la asociación antes de cambiar el cliente o el proyecto. " + motivo);
                }
            });
        }
        asignarOferta(b, cambioDeProyecto);
        marcarCambio(b, null);
        presupuestos.save(b);
        return conDatos(b, ultimasPropuestas(List.of(id)).get(id));
    }

    // ------------------------------------------------------------------- estados

    /** Marca el presupuesto como enviado al cliente: desde ahí no se edita (para cambiar algo, se duplica como variante). */
    @Transactional
    public Map<String, Object> enviar(String id) {
        pl_Presupuesto b = buscar(id);
        if (!"borrador".equals(b.getEstado())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "El presupuesto ya está " + b.getEstado() + ".");
        }
        // Los productos de ejemplo llevan precios inventados: un presupuesto que sale al cliente no puede incluirlos
        Set<String> ids = idsDeProductos(b);
        List<pl_Equipo> ejemplos = equipos.findAll().stream().filter(e -> e.isMuestra() && ids.contains(e.getId())).limit(5).toList();
        if (!ejemplos.isEmpty()) {
            String nombres = ejemplos.stream()
                    .map(e -> (e.getModelo().toLowerCase().startsWith(e.getMarca().toLowerCase()) ? e.getModelo() : e.getMarca() + " " + e.getModelo()).strip())
                    .collect(Collectors.joining(", "));
            throw new ErrorApi(HttpStatus.CONFLICT, "Hay productos de ejemplo, con precios inventados (" + nombres
                    + "): cámbialos o pon su precio real en el inventario antes de enviarlo.");
        }
        marcarCambio(b, "enviado");
        presupuestos.save(b);
        return resumen(b, null);
    }

    /** Cambia el nombre de una variante (vacío = «Versión N»). Es solo una etiqueta: vale en cualquier estado. */
    @Transactional
    public Map<String, Object> renombrarVariante(String id, String nombre) {
        pl_Presupuesto b = buscar(id);
        String limpio = nombre == null ? "" : nombre.strip();
        b.setNombreVariante(limpio.length() > 60 ? limpio.substring(0, 60) : limpio);
        presupuestos.save(b);
        return resumen(b, null);
    }

    /**
     * Asocia el presupuesto a un estudio (id vacío = quitar la asociación). Solo se asocian los del mismo cliente y del mismo
     * proyecto: así el grupo de la lista nunca queda incoherente. Es solo un enlace: vale en cualquier estado.
     */
    @Transactional
    public Map<String, Object> asociarEstudio(String id, String estudioId) {
        pl_Presupuesto b = buscar(id);
        if ("validado".equals(b.getEstado()) && !b.getEstudioId().isEmpty() && !Objects.equals(b.getEstudioId(), estudioId == null ? "" : estudioId.strip())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Un presupuesto aprobado está bloqueado: no se cambia su estudio.");
        }
        String destino = estudioId == null ? "" : estudioId.strip();
        if (!destino.isEmpty()) {
            pl_Estudio e = estudios.findById(destino).orElseThrow(() ->
                    new ErrorApi(HttpStatus.NOT_FOUND, "El estudio ya no existe: puede que lo hayan eliminado."));
            String motivo = motivoIncompatible(b, e);
            if (motivo != null) {
                throw new ErrorApi(HttpStatus.CONFLICT, motivo);
            }
        }
        b.setEstudioId(destino);
        presupuestos.save(b);
        return resumen(b, ultimasPropuestas(List.of(id)).get(id));
    }

    /** Por qué un presupuesto y un estudio no pueden asociarse (null si pueden). */
    static String motivoIncompatible(pl_Presupuesto b, pl_Estudio e) {
        boolean mismoCliente = "existente".equals(b.getClienteTipo())
                ? "existente".equals(e.getClienteTipo()) && Objects.equals(b.getClienteId(), e.getClienteId())
                : "potencial".equals(e.getClienteTipo()) && claveTexto(parteCliente(b.getNombre())).equals(claveTexto(e.getCliente()));
        if (!mismoCliente) {
            return "El estudio es de otro cliente: solo se asocian el presupuesto y el estudio del mismo cliente.";
        }
        Object proyecto = e.getDatos().get("proyecto");
        String delEstudio = claveTexto(proyecto == null ? "" : String.valueOf(proyecto));
        if (!delEstudio.equals(claveProyecto(b.getNombre()))) {
            return "El estudio es de otro proyecto: cambia el proyecto de uno de los dos para que coincidan.";
        }
        return null;
    }

    /** Copia el presupuesto como una variante nueva (borrador) de su misma oferta; si no tenía oferta, se crea. */
    @Transactional
    public Map<String, Object> duplicar(String id, String nombreVariante) {
        pl_Presupuesto origen = buscar(id);
        if ("descartado".equals(origen.getEstado())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Un presupuesto descartado no se duplica: duplica el validado o uno enviado.");
        }
        if (origen.getOfertaId().isEmpty()) {
            origen.setOfertaId(UUID.randomUUID().toString());
            presupuestos.save(origen);
        }
        String quien = nombreActor();
        pl_Presupuesto copia = nuevo();
        copia.setId(UUID.randomUUID().toString());
        copia.setNombre(origen.getNombre());
        copia.setPropietario(quien);
        copia.setPropietarioId(idActor() == null ? "" : String.valueOf(idActor()));
        copia.setOfertaId(origen.getOfertaId());
        copia.setEstudioId(origen.getEstudioId());
        copia.setClienteId(origen.getClienteId());
        copia.setClienteTipo(origen.getClienteTipo());
        copia.setDatos(copiaProfunda(origen.getDatos()));
        copia.setKwp(origen.getKwp());
        copia.setPaneles(origen.getPaneles());
        copia.setTotalPresupuesto(origen.getTotalPresupuesto());
        copia.setActualizadoPor(quien);
        String variante = nombreVariante == null ? "" : nombreVariante.strip();
        copia.setNombreVariante(variante.length() > 60 ? variante.substring(0, 60) : variante);
        presupuestos.save(copia);
        return conDatos(copia, null);
    }

    /**
     * El presupuesto se aprueba (estado interno «validado»): es el que acepta el cliente, queda bloqueado y el resto de su oferta se
     * descarta. Lo marca quien lo gestiona; la validación de la propuesta final es la que se reserva al validador. Si el cliente es
     * potencial, se da de alta en la intranet y las variantes de su oferta con ese mismo cliente quedan vinculadas a él.
     */
    @Transactional
    public Map<String, Object> validar(String id, String cif) {
        pl_Presupuesto b = buscar(id);
        if (!"enviado".equals(b.getEstado())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Solo se aprueba un presupuesto enviado al cliente.");
        }
        List<pl_Presupuesto> otros = oferta(b).stream().filter(o -> !o.getId().equals(b.getId())).toList();
        if (otros.stream().anyMatch(o -> "validado".equals(o.getEstado()))) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Esta oferta ya tiene un presupuesto aprobado.");
        }
        if (REGISTRAR_CLIENTES_POTENCIALES && "potencial".equals(b.getClienteTipo())) {
            List<pl_Presupuesto> toda = new ArrayList<>();
            toda.add(b);
            toda.addAll(otros);
            registrarCliente(b, toda, cif == null ? "" : cif.strip());
            presupuestos.saveAll(otros);
        }
        marcarCambio(b, "validado");
        presupuestos.save(b);
        for (pl_Presupuesto o : otros) {
            if ("borrador".equals(o.getEstado()) || "enviado".equals(o.getEstado())) {
                marcarCambio(o, "descartado");
                presupuestos.save(o);
            }
        }
        return resumen(b, null);
    }

    /** Deshace una aprobación (por error): el aprobado y los descartados de su oferta vuelven a «enviado». Solo el validador: reabre un presupuesto bloqueado. */
    @Transactional
    public Map<String, Object> desvalidar(String id) {
        exigirValidador();
        pl_Presupuesto b = buscar(id);
        if (!"validado".equals(b.getEstado())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "El presupuesto no está aprobado.");
        }
        List<pl_Propuesta> deEste = propuestas.findByPresupuestoId(b.getId());
        if (deEste.stream().anyMatch(p -> "enviada".equals(p.getEstado()) || "aprobada".equals(p.getEstado()))) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Este presupuesto tiene una propuesta entregada o validada: no se puede quitar su aprobación.");
        }
        // Los borradores de propuesta no aportan nada sin validación; las rechazadas se conservan como historial
        propuestas.deleteAll(deEste.stream().filter(p -> "borrador".equals(p.getEstado())).toList());
        for (pl_Presupuesto o : oferta(b)) {
            if ("validado".equals(o.getEstado()) || "descartado".equals(o.getEstado())) {
                marcarCambio(o, "enviado");
                presupuestos.save(o);
            }
        }
        return resumen(b, null);
    }

    /** Elimina un borrador. Solo quien lo creó o un usuario con nivel de acceso 1: modificarlo está abierto a todos, borrarlo no. */
    @Transactional
    public void eliminar(String id) {
        pl_Presupuesto b = presupuestos.findById(id).orElse(null);
        if (b == null) {
            return;
        }
        if (!esPropietario(b)) {
            throw new ErrorApi(HttpStatus.FORBIDDEN, "Solo " + b.getPropietario() + " puede eliminar este borrador.");
        }
        propuestas.deleteAll(propuestas.findByPresupuestoId(id));
        presupuestos.delete(b);
    }

    // ----------------------------------------------------------- uso del inventario

    /** Nombres de los presupuestos guardados que tienen alguna fila de este producto del inventario. */
    @Transactional(readOnly = true)
    public List<String> presupuestosQueUsan(String idEquipo) {
        return presupuestos.findAll().stream()
                .filter(b -> idsDeProductos(b).contains(idEquipo))
                .map(b -> b.getNombre().isEmpty() ? "Sin nombre" : b.getNombre()).toList();
    }

    // ------------------------------------------------------------------ internos

    public pl_Presupuesto buscarOpcional(String id) {
        return presupuestos.findById(id).orElse(null);
    }

    public pl_Presupuesto buscar(String id) {
        return presupuestos.findById(id).orElseThrow(() ->
                new ErrorApi(HttpStatus.NOT_FOUND, "El presupuesto ya no existe: puede que lo hayan eliminado."));
    }

    private static ErrorApi invalido(String mensaje) {
        return new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }

    private static pl_Presupuesto nuevo() {
        pl_Presupuesto b = new pl_Presupuesto();
        LocalDateTime ahora = ahora();
        b.setCreadoEl(ahora);
        b.setActualizadoEl(ahora);
        return b;
    }

    static LocalDateTime ahora() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    /** Fecha en ISO con la zona horaria (se guarda en UTC sin ella): el navegador la interpreta bien. */
    static String iso(LocalDateTime fecha) {
        return fecha.atOffset(ZoneOffset.UTC).format(ISO_UTC);
    }

    private void marcarCambio(pl_Presupuesto b, String estado) {
        if (estado != null) {
            b.setEstado(estado);
        }
        b.setVersion(b.getVersion() + 1);
        b.setActualizadoEl(ahora());
        b.setActualizadoPor(nombreActor());
    }

    private static String nombreActor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        String nombre = u == null || u.nombre() == null || u.nombre().isBlank() ? SIN_IDENTIFICAR : u.nombre();
        return SIN_IDENTIFICAR.equals(nombre) ? "" : nombre;
    }

    private static Integer idActor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        return u == null ? null : u.id();
    }

    private static Integer nivelActor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        return u == null ? null : u.nivelAcceso();
    }

    /** ¿El presupuesto está aprobado y su estudio asociado aceptado? Es lo que hace falta para validar la propuesta. */
    boolean estudioAceptado(pl_Presupuesto b) {
        return "validado".equals(b.getEstado()) && !b.getEstudioId().isEmpty()
                && estudios.findById(b.getEstudioId()).filter(e -> "aceptado".equals(e.getEstado())).isPresent();
    }

    private boolean esValidador() {
        Integer id = idActor();
        return id != null && Arrays.asList(validadores.split(",")).contains(String.valueOf(id));
    }

    /** Solo quien valida los presupuestos: lo usa también la propuesta final (validarla o anularla). */
    void exigirValidador() {
        if (!esValidador()) {
            throw new ErrorApi(HttpStatus.FORBIDDEN, "Solo quien valida los presupuestos puede hacerlo.");
        }
    }

    /** Quien lo creó (por id de la intranet o, sin id, por nombre) o un usuario con nivel de acceso 1. Si no se supo quién fue, cualquiera. */
    public boolean esPropietario(pl_Presupuesto b) {
        Integer nivel = nivelActor();
        if (nivel != null && nivel == NIVEL_ADMIN) {
            return true;
        }
        if (b.getPropietario().isEmpty() && b.getPropietarioId().isEmpty()) {
            return true;
        }
        String yo = nombreActor();
        Integer miId = idActor();
        if (!b.getPropietarioId().isEmpty() && miId != null) {
            return b.getPropietarioId().equals(String.valueOf(miId));
        }
        return !b.getPropietario().isEmpty() && yo.equalsIgnoreCase(b.getPropietario());
    }

    /**
     * La parte «proyecto» del nombre («Cliente · Proyecto»), normalizada para compararla: sin mayúsculas, sin tildes, sin espacios de
     * más y sin «v2» o «(copia)» al final (las copias antiguas los llevaban en el nombre). Vacía si el nombre no tiene proyecto.
     */
    static String claveProyecto(String nombre) {
        int i = nombre == null ? -1 : nombre.indexOf(SEPARADOR_PROYECTO);
        if (i < 0) {
            return "";
        }
        return VERSION_FINAL.matcher(claveTexto(nombre.substring(i + SEPARADOR_PROYECTO.length()))).replaceFirst("").strip();
    }

    /** La parte «cliente» del nombre («Cliente · Proyecto»); el nombre entero si no tiene proyecto. */
    private static String parteCliente(String nombre) {
        int i = nombre == null ? -1 : nombre.indexOf(SEPARADOR_PROYECTO);
        return nombre == null ? "" : i < 0 ? nombre : nombre.substring(0, i);
    }

    /** Un texto para compararlo: sin mayúsculas, sin tildes y sin espacios de más. */
    static String claveTexto(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    /**
     * Los presupuestos del mismo cliente y del mismo proyecto son variantes de una misma oferta: uno que coincide con otros entra en
     * su oferta (y se la da a los que no la tenían). Si cambia de cliente o de proyecto, sale de la oferta que compartía.
     * Sin proyecto en el nombre no se agrupa nada.
     */
    private void asignarOferta(pl_Presupuesto b, boolean cambioDeProyecto) {
        String clave = claveProyecto(b.getNombre());
        List<pl_Presupuesto> mismos = clave.isEmpty() ? List.of()
                : presupuestos.findByClienteTipoAndClienteId(b.getClienteTipo(), b.getClienteId()).stream()
                        .filter(o -> !o.getId().equals(b.getId()) && claveProyecto(o.getNombre()).equals(clave)).toList();
        if (!mismos.isEmpty()) {
            String oferta = mismos.stream().map(pl_Presupuesto::getOfertaId).filter(s -> !s.isEmpty()).findFirst()
                    .orElseGet(() -> UUID.randomUUID().toString());
            b.setOfertaId(oferta);
            for (pl_Presupuesto o : mismos) {
                if (o.getOfertaId().isEmpty()) {
                    o.setOfertaId(oferta);
                    presupuestos.save(o);
                }
            }
        } else if (cambioDeProyecto && !b.getOfertaId().isEmpty()
                && presupuestos.findByOfertaId(b.getOfertaId()).stream().anyMatch(o -> !o.getId().equals(b.getId()))) {
            b.setOfertaId("");
        }
    }

    private List<pl_Presupuesto> oferta(pl_Presupuesto b) {
        return b.getOfertaId().isEmpty() ? List.of(b) : presupuestos.findByOfertaId(b.getOfertaId());
    }

    private Map<String, pl_Propuesta> ultimasPropuestas(List<String> ids) {
        List<pl_Propuesta> filas = ids == null ? propuestas.findAllByOrderByVersionAsc() : propuestas.findByPresupuestoIdInOrderByVersionAsc(ids);
        Map<String, pl_Propuesta> ultimas = new HashMap<>();
        filas.forEach(p -> ultimas.put(p.getPresupuestoId(), p));  // la de mayor versión es la última en pisar
        return ultimas;
    }

    private void comprobarDatos(PresupuestoGuardarRequest c) {
        Object lineas = c.getDatos().get("lines");
        if (c.getDatos().containsKey("lines") && !(lineas instanceof List)) {
            throw invalido("El presupuesto no tiene el formato esperado.");
        }
        try {
            if (json.writeValueAsString(c.getDatos()).length() > MAX_DATOS) {
                throw new ErrorApi(HttpStatus.PAYLOAD_TOO_LARGE, "El presupuesto es demasiado grande para guardarlo.");
            }
        } catch (JsonProcessingException e) {
            throw invalido("El presupuesto no tiene el formato esperado.");
        }
        // Todo presupuesto lleva su cliente: uno que ya existe en la intranet (con su id) o un potencial (sin id)
        if ("existente".equals(c.getClienteTipo())) {
            if (c.getClienteId() == null) {
                throw invalido("Falta el cliente de la intranet al que se vincula el presupuesto.");
            }
        } else if ("potencial".equals(c.getClienteTipo())) {
            if (c.getClienteId() != null) {
                throw invalido("Un cliente potencial no tiene id en la intranet.");
            }
        } else {
            throw invalido("Elige el cliente del presupuesto (uno existente o un potencial).");
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> idsDeProductos(pl_Presupuesto b) {
        Object lineas = b.getDatos().get("lines");
        Set<String> ids = new HashSet<>();
        if (lineas instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m && m.get("itemId") != null) {
                    ids.add(String.valueOf(m.get("itemId")));
                }
            }
        }
        return ids;
    }

    private Map<String, Object> copiaProfunda(Map<String, Object> datos) {
        try {
            return json.readValue(json.writeValueAsString(datos), new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw invalido("El presupuesto no tiene el formato esperado.");
        }
    }

    /** Da de alta (o enlaza) al cliente potencial de `b` en la intranet y vincula a él las variantes con el mismo cliente. */
    @SuppressWarnings("unchecked")
    private void registrarCliente(pl_Presupuesto b, List<pl_Presupuesto> oferta, String cifDado) {
        Object c = b.getDatos().get("customer");
        Map<String, Object> cliente = c instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String nombre = texto(cliente.get("name"), b.getNombre()).strip();
        String cif = !cifDado.isEmpty() ? cifDado : texto(cliente.get("cif"), "").strip();
        if (cif.isEmpty()) {
            throw invalido("Falta el CIF del cliente: hace falta para darlo de alta en la intranet.");
        }
        Map<String, Object> alta;
        try {
            alta = clientes.crear(nombre, cif, texto(cliente.get("phone"), ""), texto(cliente.get("email"), ""));
        } catch (RuntimeException e) {
            throw new ErrorApi(HttpStatus.BAD_GATEWAY, "No se ha podido dar de alta al cliente: " + e.getMessage());
        }
        Map<String, Object> enlazado = new LinkedHashMap<>();
        enlazado.put("kind", "existente");
        enlazado.put("id", alta.get("id"));
        enlazado.put("name", alta.get("name"));
        enlazado.put("tradeName", alta.get("trade_name"));
        enlazado.put("cif", alta.get("cif"));
        for (pl_Presupuesto o : oferta) {
            Object oc = o.getDatos().get("customer");
            if ("potencial".equals(o.getClienteTipo()) && oc instanceof Map<?, ?> m
                    && texto(m.get("name"), "").equalsIgnoreCase(nombre)) {
                Map<String, Object> nuevos = new LinkedHashMap<>(o.getDatos());
                nuevos.put("customer", enlazado);
                o.setDatos(nuevos);
                o.setClienteTipo("existente");
                o.setClienteId(((Number) alta.get("id")).intValue());
                if (!o.getId().equals(b.getId())) {
                    o.setVersion(o.getVersion() + 1);
                }
            }
        }
    }

    private static String texto(Object valor, String porDefecto) {
        return valor == null || String.valueOf(valor).isEmpty() ? porDefecto : String.valueOf(valor);
    }

    // ------------------------------------------------------------------- formato

    static Map<String, Object> resumen(pl_Presupuesto b, pl_Propuesta propuesta) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", b.getId());
        r.put("name", b.getNombre());
        r.put("owner", b.getPropietario());
        r.put("owner_id", b.getPropietarioId());
        r.put("status", b.getEstado());
        r.put("offer_id", b.getOfertaId());
        r.put("study_id", b.getEstudioId());
        r.put("customer_id", b.getClienteId());
        r.put("customer_kind", b.getClienteTipo());
        r.put("variant_name", b.getNombreVariante());
        r.put("kwp", b.getKwp());
        r.put("panels", b.getPaneles());
        r.put("budget_total", b.getTotalPresupuesto());
        r.put("version", b.getVersion());
        r.put("created_at", iso(b.getCreadoEl()));
        r.put("updated_at", iso(b.getActualizadoEl()));
        r.put("updated_by", b.getActualizadoPor());
        r.put("proposal_status", propuesta == null ? "" : propuesta.getEstado());
        r.put("proposal_version", propuesta == null ? 0 : propuesta.getVersion());
        return r;
    }

    private static Map<String, Object> conDatos(pl_Presupuesto b, pl_Propuesta propuesta) {
        Map<String, Object> r = resumen(b, propuesta);
        r.put("data", b.getDatos());
        return r;
    }
}
