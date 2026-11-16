package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.entity.modulo_planificador.pl_Estudio;
import api.aura.repository.modulo_planificador.pl_EstudioRepository;
import api.aura.service.modulo_planificador.estudio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static api.aura.service.modulo_planificador.TareaProyectoService.invalido;

/** Estudios fotovoltaicos: registro con estado y observaciones, datos de entrada (SIPS y curva), cálculo y informe. */
@Service
@RequiredArgsConstructor
public class EstudioService {

    /** Estados del estudio, de que se prepara a que se cierra. */
    public static final Map<String, String> ESTADOS = new LinkedHashMap<>();

    static {
        ESTADOS.put("en_estudio", "En estudio");
        ESTADOS.put("pte_doc", "Pendiente de documentación del cliente");
        ESTADOS.put("entregado", "Informe entregado");
        ESTADOS.put("pte_cliente", "Pendiente de respuesta del cliente");
        ESTADOS.put("aceptado", "Aceptado");
        ESTADOS.put("rechazado", "Rechazado por el cliente");
        ESTADOS.put("cancelado", "Cancelado / sin interés");
    }

    private static final String NO_EXISTE = "Estudio no encontrado";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    private final pl_EstudioRepository estudios;
    private final PropuestaPdf pdf;

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return estudios.findAllByOrderByActualizadoElDesc(PageRequest.of(0, 500)).stream().map(EstudioService::resumen).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(String id) {
        return detalle(buscar(id), null);
    }

    // ------------------------------------------------------------------ alta, cambios y baja

    @Transactional
    public Map<String, Object> crear(Map<String, Object> cuerpo) {
        String cliente = texto(cuerpo, "cliente", 255);
        if (cliente.isEmpty()) throw invalido("Escribe el nombre del cliente.");
        pl_Estudio e = new pl_Estudio();
        e.setId(UUID.randomUUID().toString());
        e.setCliente(cliente);
        e.setObservaciones(texto(cuerpo, "observaciones", 5000));
        e.setDatos(datosIniciales(cliente));
        LocalDateTime ahora = ahora();
        e.setCreadoEl(ahora);
        e.setActualizadoEl(ahora);
        e.setActualizadoPor(actor());
        estudios.save(e);
        return detalle(e, null);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> actualizar(String id, Map<String, Object> cuerpo) {
        pl_Estudio e = buscar(id);
        if (cuerpo.containsKey("cliente")) {
            String cliente = texto(cuerpo, "cliente", 255);
            if (cliente.isEmpty()) throw invalido("Escribe el nombre del cliente.");
            e.setCliente(cliente);
        }
        if (cuerpo.containsKey("estado")) {
            String estado = String.valueOf(cuerpo.get("estado"));
            if (!ESTADOS.containsKey(estado)) throw invalido("Estado no válido.");
            e.setEstado(estado);
        }
        if (cuerpo.containsKey("observaciones")) e.setObservaciones(texto(cuerpo, "observaciones", 5000));
        boolean horarioCambiado = false;
        if (cuerpo.get("datos") instanceof Map<?, ?> d) {
            Map<String, Object> datos = new LinkedHashMap<>(e.getDatos());
            fusionar(datos, (Map<String, Object>) d);
            horarioCambiado = ((Map<String, Object>) d).containsKey("horario");
            datos.put("cliente", e.getCliente());
            e.setDatos(datos);
            e.setCups(String.valueOf(datos.getOrDefault("cups", "")));
        } else {
            Map<String, Object> datos = new LinkedHashMap<>(e.getDatos());
            datos.put("cliente", e.getCliente());
            e.setDatos(datos);
        }
        String aviso = null;
        if (horarioCambiado && e.isCurvaEstimada()) aviso = estimarCurva(e);
        calcular(e);
        e.setActualizadoEl(ahora());
        e.setActualizadoPor(actor());
        estudios.save(e);
        return detalle(e, aviso);
    }

    @Transactional
    public void eliminar(String id) {
        estudios.delete(buscar(id));
    }

    // ------------------------------------------------------------------ ficheros

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> subirSips(String id, byte[] contenido) {
        pl_Estudio e = buscar(id);
        Sips s = Sips.leer(contenido);
        Map<String, Object> datos = new LinkedHashMap<>(e.getDatos());
        Map<String, String> sum = s.suministro();
        datos.put("cups", limitar(sum.getOrDefault("CUPS", ""), 40));
        datos.put("tarifa", limitar(sum.getOrDefault("Tarifa", ""), 20));
        datos.put("localidad", limitar(sum.getOrDefault("Localidad", ""), 120));
        datos.put("provincia", limitar(sum.getOrDefault("Provincia", ""), 120));
        datos.put("cnae", limitar(sum.getOrDefault("CNAE", ""), 120));
        List<Double> pot = new ArrayList<>();
        for (double p : s.potencias()) pot.add(p);
        datos.put("potencias", pot);
        // Solo se guardan las lecturas y los consumos: el titular y su dirección no se almacenan
        Map<String, Object> guardado = s.aMapa();
        guardado.remove("suministro");
        datos.put("sips", guardado);
        e.setDatos(datos);
        e.setCups(String.valueOf(datos.get("cups")));
        String aviso = null;
        if (e.getCurva() == null || e.isCurvaEstimada()) aviso = estimarCurva(e);
        calcular(e);
        e.setActualizadoEl(ahora());
        e.setActualizadoPor(actor());
        estudios.save(e);
        return detalle(e, aviso);
    }

    @Transactional
    public Map<String, Object> subirCurva(String id, byte[] contenido, String nombre) {
        pl_Estudio e = buscar(id);
        Curva c = Curva.leer(contenido, nombre);
        e.setCurva(c.comoTexto());
        e.setCurvaEstimada(false);
        e.setCurvaOrigen(limitar(nombre == null ? "" : nombre, 255));
        calcular(e);
        e.setActualizadoEl(ahora());
        e.setActualizadoPor(actor());
        estudios.save(e);
        return detalle(e, null);
    }

    // ------------------------------------------------------------------ informe

    @Transactional(readOnly = true)
    public PropuestaService.Documento html(String id) {
        pl_Estudio e = buscar(id);
        return new PropuestaService.Documento(informe(e).getBytes(java.nio.charset.StandardCharsets.UTF_8), nombreArchivo(e) + ".html");
    }

    @Transactional(readOnly = true)
    public PropuestaService.Documento pdf(String id) {
        pl_Estudio e = buscar(id);
        String html = informe(e);
        try {
            return new PropuestaService.Documento(pdf.generar(html), nombreArchivo(e) + ".pdf");
        } catch (PropuestaPdf.SinNavegador ex) {
            throw new ErrorApi(HttpStatus.NOT_IMPLEMENTED, ex.getMessage());
        } catch (RuntimeException ex) {
            throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el PDF: " + ex.getMessage());
        }
    }

    private String informe(pl_Estudio e) {
        Curva curva = curvaDe(e);
        if (curva == null) throw invalido("Falta la curva de consumo: sube el SIPS o la curva de la distribuidora.");
        return Informe.generar(Calculo.de(e.getDatos(), curva), LocalDate.now());
    }

    // ------------------------------------------------------------------ cálculo

    private Curva curvaDe(pl_Estudio e) {
        if (e.getCurva() == null || e.getCurva().isBlank()) return null;
        return Curva.desdeTexto(e.getCurva(), e.isCurvaEstimada(), e.getCurvaOrigen());
    }

    /** Estima la curva con el SIPS y el horario. Devuelve un aviso si no se puede (el estudio se guarda igualmente). */
    @SuppressWarnings("unchecked")
    private String estimarCurva(pl_Estudio e) {
        Map<String, Object> datos = e.getDatos();
        Map<String, Object> sips = mapaDe(datos.get("sips"));
        Map<String, Object> hor = mapaDe(datos.get("horario"));
        CurvaEstimada.Horario h = new CurvaEstimada.Horario((int) num(hor, "ini", 7), (int) num(hor, "fin", 19), num(hor, "fraccionFuera", 0.15), num(hor, "fraccionFinde", 0.10));
        try {
            Curva c = CurvaEstimada.crear(Sips.lecturasDeMapa(sips.get("lecturas")), num(sips, "kwhAnual", 0), h);
            e.setCurva(c.comoTexto());
            e.setCurvaEstimada(true);
            e.setCurvaOrigen(c.origen());
            return null;
        } catch (ErrorApi ex) {
            e.setCurva(null);
            e.setCurvaEstimada(false);
            e.setCurvaOrigen("");
            return ex.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private void calcular(pl_Estudio e) {
        Curva curva = curvaDe(e);
        if (curva == null) {
            e.setResultado(new LinkedHashMap<>());
            return;
        }
        Map<String, Object> datos = new LinkedHashMap<>(e.getDatos());
        Map<String, Object> ins = new LinkedHashMap<>(mapaDe(datos.get("instalacion")));
        if (num(ins, "kwp", 0) <= 0) {
            ins.put("kwp", Calculo.kwpSugerido(curva, String.valueOf(ins.getOrDefault("orientacion", "este-oeste"))));
            datos.put("instalacion", ins);
            e.setDatos(datos);
        }
        e.setResultado(Calculo.de(datos, curva).aMapa());
    }

    // ------------------------------------------------------------------ validación de datos

    private static Map<String, Object> datosIniciales(String cliente) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("cliente", cliente);
        d.put("cups", "");
        d.put("tarifa", "");
        d.put("localidad", "");
        d.put("provincia", "");
        Map<String, Object> ins = new LinkedHashMap<>();
        ins.put("kwp", 0);
        ins.put("bateriaKwh", 0);
        ins.put("orientacion", "este-oeste");
        ins.put("produccionEspecifica", null);
        d.put("instalacion", ins);
        Map<String, Object> eco = new LinkedHashMap<>();
        eco.put("precioWp", 0.85);
        eco.put("precioBateriaKwh", 250);
        eco.put("inversionTotal", null);
        eco.put("precioEnergia", 0.10);
        eco.put("mantenimientoPct", 1.0);
        eco.put("mantenimiento", null);
        eco.put("ahorroPotencia", 0);
        eco.put("reposicionAno", 13);
        eco.put("reposicion", 0);
        d.put("economia", eco);
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("ini", 7);
        h.put("fin", 19);
        h.put("fraccionFuera", 0.15);
        h.put("fraccionFinde", 0.10);
        d.put("horario", h);
        return d;
    }

    /** Mezcla los datos que llegan con los guardados, comprobando cada valor. Solo se aceptan los campos conocidos. */
    @SuppressWarnings("unchecked")
    private static void fusionar(Map<String, Object> destino, Map<String, Object> nuevo) {
        for (String k : List.of("cups", "tarifa", "localidad", "provincia")) {
            if (nuevo.containsKey(k)) destino.put(k, limitar(String.valueOf(nuevo.get(k) == null ? "" : nuevo.get(k)).strip(), k.equals("cups") ? 40 : 120));
        }
        if (nuevo.get("instalacion") instanceof Map<?, ?> m) {
            Map<String, Object> ins = new LinkedHashMap<>(mapaDe(destino.get("instalacion")));
            Map<String, Object> in = (Map<String, Object>) m;
            if (in.containsKey("kwp")) ins.put("kwp", rango(in, "kwp", 0, 100_000, "La potencia de paneles (kWp)"));
            if (in.containsKey("bateriaKwh")) ins.put("bateriaKwh", rango(in, "bateriaKwh", 0, 100_000, "La capacidad de la batería (kWh)"));
            if (in.containsKey("orientacion")) {
                String o = String.valueOf(in.get("orientacion"));
                if (!o.equals("sur") && !o.equals("este-oeste")) throw invalido("La orientación debe ser sur o este-oeste.");
                ins.put("orientacion", o);
            }
            if (in.containsKey("produccionEspecifica")) {
                ins.put("produccionEspecifica", vacio(in.get("produccionEspecifica")) ? null : rango(in, "produccionEspecifica", 500, 2500, "La producción específica (kWh/kWp·año)"));
            }
            destino.put("instalacion", ins);
        }
        if (nuevo.get("economia") instanceof Map<?, ?> m) {
            Map<String, Object> eco = new LinkedHashMap<>(mapaDe(destino.get("economia")));
            Map<String, Object> in = (Map<String, Object>) m;
            for (Object[] c : new Object[][]{{"precioWp", 0.05, 5.0, "El precio por Wp"}, {"precioBateriaKwh", 0.0, 3000.0, "El precio de la batería (€/kWh)"},
                    {"precioEnergia", 0.005, 1.0, "El precio de la energía (€/kWh)"}, {"mantenimientoPct", 0.0, 10.0, "El mantenimiento (% de la inversión)"},
                    {"ahorroPotencia", 0.0, 10_000_000.0, "El ahorro en potencia"}, {"reposicion", 0.0, 100_000_000.0, "El cambio de equipos"}}) {
                String k = (String) c[0];
                if (in.containsKey(k)) eco.put(k, rango(in, k, (double) c[1], (double) c[2], (String) c[3]));
            }
            for (Object[] c : new Object[][]{{"inversionTotal", 1.0e9, "La inversión total"}, {"mantenimiento", 1.0e7, "El mantenimiento anual"}}) {
                String k = (String) c[0];
                if (in.containsKey(k)) eco.put(k, vacio(in.get(k)) ? null : rango(in, k, 0, (double) c[1], (String) c[2]));
            }
            if (in.containsKey("reposicionAno")) eco.put("reposicionAno", (int) rango(in, "reposicionAno", 1, 25, "El año de cambio de equipos"));
            destino.put("economia", eco);
        }
        if (nuevo.get("horario") instanceof Map<?, ?> m) {
            Map<String, Object> hor = new LinkedHashMap<>(mapaDe(destino.get("horario")));
            Map<String, Object> in = (Map<String, Object>) m;
            if (in.containsKey("ini")) hor.put("ini", (int) rango(in, "ini", 0, 23, "La hora de inicio"));
            if (in.containsKey("fin")) hor.put("fin", (int) rango(in, "fin", 1, 24, "La hora de fin"));
            if (in.containsKey("fraccionFuera")) hor.put("fraccionFuera", rango(in, "fraccionFuera", 0, 1, "El consumo fuera de horario"));
            if (in.containsKey("fraccionFinde")) hor.put("fraccionFinde", rango(in, "fraccionFinde", 0, 1, "El consumo en fin de semana"));
            if (num(hor, "fin", 19) <= num(hor, "ini", 7)) throw invalido("La hora de fin debe ser posterior a la de inicio.");
            destino.put("horario", hor);
        }
    }

    private static boolean vacio(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }

    private static double rango(Map<String, Object> m, String clave, double min, double max, String nombre) {
        double v;
        try {
            v = Numeros.aNumero(m.get(clave));
        } catch (NumberFormatException | NullPointerException e) {
            throw invalido(nombre + " debe ser un número.");
        }
        if (v < min || v > max) throw invalido(nombre + " debe estar entre " + sinCeros(min) + " y " + sinCeros(max) + ".");
        return v;
    }

    private static String sinCeros(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapaDe(Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    private static double num(Map<String, Object> m, String clave, double defecto) {
        Object v = m.get(clave);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return v == null ? defecto : Numeros.aNumero(v);
        } catch (NumberFormatException e) {
            return defecto;
        }
    }

    // ------------------------------------------------------------------ salida

    private static Map<String, Object> resumen(pl_Estudio e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("cliente", e.getCliente());
        m.put("cups", e.getCups());
        m.put("estado", e.getEstado());
        m.put("estadoNombre", ESTADOS.getOrDefault(e.getEstado(), e.getEstado()));
        m.put("observaciones", e.getObservaciones());
        Map<String, Object> r = mapaDe(e.getResultado());
        Map<String, Object> sim = mapaDe(r.get("simulacion")), fin = mapaDe(r.get("finanzas"));
        m.put("kwp", sim.get("kwp"));
        m.put("bateriaKwh", sim.get("bateriaKwh"));
        m.put("cobertura", sim.get("cobertura"));
        m.put("retorno", fin.get("retorno"));
        m.put("ahorroPrimerAno", fin.get("ahorroPrimerAno"));
        m.put("calculado", !r.isEmpty());
        m.put("curvaEstimada", e.isCurvaEstimada());
        m.put("tieneCurva", e.getCurva() != null && !e.getCurva().isBlank());
        m.put("actualizadoEl", e.getActualizadoEl().atOffset(ZoneOffset.UTC).format(ISO));
        m.put("actualizadoPor", e.getActualizadoPor());
        return m;
    }

    private static Map<String, Object> detalle(pl_Estudio e, String aviso) {
        Map<String, Object> m = resumen(e);
        m.put("datos", e.getDatos());
        m.put("resultado", e.getResultado());
        Map<String, Object> curva = new LinkedHashMap<>();
        curva.put("presente", e.getCurva() != null && !e.getCurva().isBlank());
        curva.put("estimada", e.isCurvaEstimada());
        curva.put("origen", e.getCurvaOrigen());
        m.put("curva", curva);
        List<Map<String, String>> estados = new ArrayList<>();
        ESTADOS.forEach((k, v) -> estados.add(Map.of("id", k, "nombre", v)));
        m.put("estados", estados);
        m.put("creadoEl", e.getCreadoEl().atOffset(ZoneOffset.UTC).format(ISO));
        if (aviso != null) m.put("aviso", aviso);
        return m;
    }

    private pl_Estudio buscar(String id) {
        return estudios.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, NO_EXISTE));
    }

    private static String texto(Map<String, Object> m, String clave, int max) {
        Object v = m.get(clave);
        String t = v == null ? "" : String.valueOf(v).strip();
        if (t.length() > max) throw invalido("El campo «" + clave + "» admite como máximo " + max + " caracteres.");
        return t;
    }

    private static String limitar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String nombreArchivo(pl_Estudio e) {
        String base = ("Estudio FV " + e.getCliente()).replaceAll("[\\\\/:*?\"<>|]+", " ").strip();
        return base.length() > 80 ? base.substring(0, 80).strip() : base;
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static String actor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        return u == null || u.nombre() == null || u.nombre().isBlank() ? "Sin identificar" : u.nombre();
    }
}
