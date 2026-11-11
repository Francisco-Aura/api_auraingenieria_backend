package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.entity.modulo_planificador.pl_Proyecto;
import api.aura.repository.modulo_planificador.pl_ProyectoRepository;
import api.aura.service.modulo_planificador.contrato.ContratoPdf;
import api.aura.service.modulo_planificador.contrato.ContratoRelleno;
import api.aura.service.modulo_planificador.dropbox.DropboxAlmacen;
import api.aura.service.modulo_planificador.dropbox.DropboxEstructura;
import api.aura.service.modulo_planificador.dropbox.DropboxError;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * El contrato de un proyecto, solo en PDF: se genera con los datos guardados en él y se archiva en Dropbox (equivale a contract_project.py
 * y a las rutas de contrato de routes.py, sin el Word: el Word modelo es interno y no se entrega ni se guarda).
 *
 * <p>Es obligatorio: un proyecto nace con el contrato pendiente de generar. Se puede generar al crearlo, en la pantalla de alta, o después,
 * desde el propio proyecto; en ambos casos se guarda solo en la carpeta de contrato del proyecto en Dropbox.
 */
@Service
@RequiredArgsConstructor
public class ContratoDocumentoService {

    /** Datos de las partes y del lugar que se guardan en el proyecto (los del formulario del contrato). */
    public static final List<String> CLAVES_PARTES = List.of("empresa", "cif", "domicilio", "representante", "dni", "cubierta_de", "instalacion",
            "kw_nominal", "lugar_firma", "dia", "mes", "firmante", "firmante_dni", "garantia_producto", "garantia_produccion", "garantia_potencia");
    /** Con la contratista del modelo (AURA RENOVABLES, S.L.U.): así se llaman los contratos en la carpeta de contrato. */
    static final String ETIQUETA_CONTRATISTA = "AURA RENOVABLES";
    private static final String SIN_IDENTIFICAR = "Sin identificar";

    private final pl_ProyectoRepository proyectos;
    private final ContratoProyectoService contrato;
    private final ContratoRelleno relleno;
    private final ContratoPdf dibujo;
    private final DropboxAlmacen dropbox;
    private final ActividadService actividad;

    /** Solo los datos conocidos, como texto y sin espacios de más. */
    public static Map<String, Object> limpiarPartes(Map<String, Object> crudo) {
        Map<String, Object> salida = new LinkedHashMap<>();
        for (String clave : CLAVES_PARTES) {
            Object v = crudo == null ? null : crudo.get(clave);
            if (v != null && !String.valueOf(v).isBlank()) {
                String t = String.valueOf(v).strip();
                salida.put(clave, t.length() > 300 ? t.substring(0, 300) : t);
            }
        }
        return salida;
    }

    /** 12.5 → «12,5»; 20.0 → «20»; el texto se deja como está. */
    private static String texto(Object v) {
        if (v == null) return "";
        if (v instanceof Double d) {
            return d == Math.rint(d) ? String.valueOf((long) (double) d) : String.valueOf(d).replace(".", ",");
        }
        return String.valueOf(v).strip();
    }

    /** Todo lo que necesita el relleno: el presupuesto inicial, la forma de pago y los datos guardados. */
    Map<String, Object> datos(pl_Proyecto p, Map<String, Object> partes) {
        Map<String, Object> guardadas = new LinkedHashMap<>(p.getPartesContrato());
        guardadas.putAll(limpiarPartes(partes));
        Map<String, Object> base = p.getLineaBase();
        Map<String, Object> d = new LinkedHashMap<>();
        for (String clave : CLAVES_PARTES) {
            d.put(clave, guardadas.getOrDefault(clave, ""));
        }
        // El firmante del promotor es su representante, salvo que se indique otro.
        d.put("firmante", guardadas.get("firmante") != null && !"".equals(guardadas.get("firmante")) ? guardadas.get("firmante") : guardadas.getOrDefault("representante", ""));
        d.put("firmante_dni", guardadas.get("firmante_dni") != null && !"".equals(guardadas.get("firmante_dni")) ? guardadas.get("firmante_dni") : guardadas.getOrDefault("dni", ""));
        d.put("kwp", texto(base.get("potencia_kwp")));
        d.put("paneles_uds", texto(base.get("paneles_uds")));
        d.put("paneles_modelo", texto(base.get("paneles_modelo")));
        d.put("inversor_uds", texto(base.get("inversor_uds")));
        d.put("inversor_modelo", texto(base.get("inversor_modelo")));
        d.put("estructura", texto(base.get("estructura")));
        d.put("bateria", texto(base.get("bateria")));
        d.put("importe_total", texto(base.get("importe_total")));
        d.put("payment_plan", contrato.normalizarPlanPagos(p.getPlanPagos()));
        d.put("anio", String.valueOf((p.getFechaInicio() != null ? p.getFechaInicio() : LocalDate.now()).getYear()));
        return d;
    }

    /** PDF del contrato del proyecto, y los datos que faltan (si faltan, no hay contrato). */
    record Resultado(byte[] pdf, List<String> faltan) {
    }

    Resultado construir(pl_Proyecto p, Map<String, Object> partes) {
        Map<String, Object> d = datos(p, partes);
        List<String> faltan = ContratoRelleno.camposQueFaltan(d);
        if (!faltan.isEmpty()) {
            return new Resultado(new byte[0], faltan);
        }
        try {
            return new Resultado(dibujo.convertir(relleno.generar(d, LocalDate.now())), List.of());
        } catch (IllegalArgumentException e) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /** Cómo se llama el proyecto en Dropbox sin la fecha: «ALPEMA_PUNTA BALLENA» (de su carpeta, si la tiene). */
    String etiquetaDeProyecto(pl_Proyecto p, Map<String, Object> partes) {
        if (!p.getRutaDropbox().isEmpty()) {
            String ultimo = p.getRutaDropbox().replaceAll("/+$", "");
            ultimo = ultimo.substring(ultimo.lastIndexOf('/') + 1);
            String etiqueta = ultimo.replaceFirst("^\\d{6}_", "").strip();
            if (!etiqueta.isEmpty()) return etiqueta;
        }
        String empresa = String.valueOf(datos(p, partes).getOrDefault("empresa", ""));
        String limpio = DropboxEstructura.limpiarComponente(empresa).toUpperCase();
        return limpio.isEmpty() ? "FOTOVOLTAICA" : limpio;
    }

    /** «CONTRATO INSTALACION FV_AURA RENOVABLES_ALPEMA_PUNTA BALLENA.pdf», como en la carpeta de contrato. */
    String nombreDelPdf(pl_Proyecto p, Map<String, Object> partes) {
        return DropboxEstructura.limpiarNombreArchivo("CONTRATO INSTALACION FV_" + ETIQUETA_CONTRATISTA + "_" + etiquetaDeProyecto(p, partes) + ".pdf");
    }

    private static String actor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        return u == null || u.nombre() == null || u.nombre().isBlank() ? SIN_IDENTIFICAR : u.nombre();
    }

    private pl_Proyecto activo(String id) {
        pl_Proyecto p = proyectos.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        if (p.getEliminadoEl() != null) {
            throw new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado");
        }
        return p;
    }

    private static String mensajeFaltan(List<String> faltan) {
        return "Faltan datos para generar el contrato: " + String.join(", ", faltan) + ".";
    }

    /**
     * Genera el contrato del proyecto, lo guarda en Dropbox (si hay carpeta) y lo da por generado. Lanza un 422 con lo que falta si los datos
     * no bastan. Un fallo de Dropbox no impide darlo por generado: se avisa y se puede generar de nuevo.
     */
    Map<String, Object> generarYArchivar(pl_Proyecto p, String actor) {
        Resultado r = construir(p, null);
        if (!r.faltan().isEmpty()) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, mensajeFaltan(r.faltan()));
        }
        List<String> guardados = new ArrayList<>();
        String aviso = "";
        try {
            if (!p.getRutaDropbox().isEmpty() && dropbox.habilitado()) {
                guardados.add((String) dropbox.guardarArchivo(p.getRutaDropbox(), DropboxEstructura.CARPETA_CONTRATO, nombreDelPdf(p, null), r.pdf()).get("path"));
            }
        } catch (DropboxError e) {
            aviso = "No se pudo guardar el contrato en Dropbox: " + e.getMessage();
        }
        p.setContratoGeneradoEl(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        p.setArchivoContrato(guardados.isEmpty() ? "" : guardados.get(0));
        String detalle = !guardados.isEmpty() ? "Guardado en Dropbox: " + String.join(", ", guardados)
                : !aviso.isEmpty() ? aviso : "No se guardó en Dropbox: el proyecto no tiene carpeta";
        actividad.registrar(p.getId(), actor, "contract", "Generó el contrato", null, detalle);
        proyectos.save(p);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("saved", guardados);
        m.put("warning", aviso);
        return m;
    }

    /** Al crear un proyecto con el contrato generado en la pantalla de alta: si no se puede, queda anotado y pendiente de generar. */
    public void generarAlCrear(pl_Proyecto p) {
        try {
            generarYArchivar(p, actor());
        } catch (ErrorApi e) {
            actividad.registrar(p.getId(), actor(), "contract", "No se pudo generar el contrato", null, e.getMessage());
        }
    }

    /** Genera el contrato con los datos del proyecto (más los que se completen aquí, que se conservan aunque falte algo) y lo guarda en Dropbox. */
    @Transactional(noRollbackFor = ErrorApi.class)
    public Map<String, Object> generar(String id, Map<String, Object> cuerpo) {
        pl_Proyecto p = activo(id);
        @SuppressWarnings("unchecked")
        Map<String, Object> partes = cuerpo.get("party") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        // Lo que se escribe se conserva aunque falte algo: no hay que volver a teclearlo.
        Map<String, Object> unidas = new LinkedHashMap<>(p.getPartesContrato());
        unidas.putAll(limpiarPartes(partes));
        p.setPartesContrato(unidas);
        proyectos.saveAndFlush(p);
        Map<String, Object> resultado = generarYArchivar(p, actor());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("project", Mapas.proyecto(p));
        m.putAll(resultado);
        return m;
    }

    /** El PDF del contrato con los datos guardados. No guarda nada. */
    public record Descarga(byte[] pdf, String nombre) {
    }

    @Transactional(readOnly = true)
    public Descarga descargar(String id, String formato) {
        pl_Proyecto p = activo(id);
        if (!"pdf".equalsIgnoreCase(formato == null || formato.isBlank() ? "pdf" : formato)) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "El contrato solo se entrega en PDF");
        }
        Resultado r = construir(p, null);
        if (!r.faltan().isEmpty()) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, mensajeFaltan(r.faltan()));
        }
        return new Descarga(r.pdf(), nombreDelPdf(p, null));
    }

    /** Contrato con los datos indicados, en PDF. No guarda nada (se usa en la pantalla de alta, antes de crear el proyecto). */
    public Descarga generarSinGuardar(Map<String, Object> datos, String formato) {
        if (!"pdf".equalsIgnoreCase(formato == null || formato.isBlank() ? "pdf" : formato)) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "El contrato solo se entrega en PDF");
        }
        List<String> faltan = ContratoRelleno.camposQueFaltan(datos);
        if (!faltan.isEmpty()) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, mensajeFaltan(faltan));
        }
        byte[] pdf;
        try {
            pdf = dibujo.convertir(relleno.generar(datos, LocalDate.now()));
        } catch (IllegalArgumentException e) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        String empresa = String.valueOf(datos.getOrDefault("empresa", "")).replaceAll("[\\/:*?\"<>|]+", " ").strip();
        return new Descarga(pdf, "Contrato " + (empresa.isEmpty() ? "fotovoltaica" : empresa) + ".pdf");
    }
}
