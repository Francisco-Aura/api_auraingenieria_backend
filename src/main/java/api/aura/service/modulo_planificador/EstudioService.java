package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.entity.modulo_planificador.pl_Estudio;
import api.aura.entity.modulo_planificador.pl_Presupuesto;
import api.aura.repository.modulo_planificador.pl_PresupuestoRepository;
import api.aura.entity.modulo_planificador.pl_PrecioRegulado;
import api.aura.repository.modulo_planificador.pl_EstudioRepository;
import api.aura.entity.modulo_planificador.pl_Boe;
import api.aura.repository.modulo_planificador.pl_BoeRepository;
import api.aura.repository.modulo_planificador.pl_PrecioReguladoRepository;
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
    /** El id que trae un estudio creado en el navegador (un UUID): letras, números y guiones. */
    private static final java.util.regex.Pattern ID_VALIDO = java.util.regex.Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    private final pl_EstudioRepository estudios;
    private final pl_PresupuestoRepository presupuestos;
    private final api.aura.repository.modulo_planificador.pl_PropuestaRepository propuestas;
    private final pl_PrecioReguladoRepository preciosRegulados;
    private final pl_BoeRepository boes;
    private final PvgisService pvgis;
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

    /**
     * «Guardar y compartir»: el estudio se guarda en el servidor, donde lo ven y lo modifican todos. Llega entero (cliente, datos y
     * curva) con el id que tenía en el navegador, para que siga siendo el mismo; si ya está guardado, 409.
     */
    @Transactional
    public Map<String, Object> crear(Map<String, Object> cuerpo) {
        pl_Estudio e = new pl_Estudio();
        String id = cuerpo.get("id") == null ? "" : String.valueOf(cuerpo.get("id")).strip();
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
        } else if (!ID_VALIDO.matcher(id).matches()) {
            throw invalido("El identificador del estudio no es válido.");
        } else if (estudios.existsById(id)) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Este estudio ya está guardado y compartido.");
        }
        e.setId(id);
        e.setDatos(datosIniciales(""));
        if (!(cuerpo.get("customer") instanceof Map<?, ?>)) {
            // Solo con el nombre (como antes de elegir el cliente): queda como cliente potencial
            String cliente = texto(cuerpo, "cliente", 255);
            if (cliente.isEmpty()) throw invalido("Elige el cliente del estudio (uno existente o un potencial).");
            ponerCliente(e, Map.of("kind", "potencial", "name", cliente));
        }
        String aviso = aplicarEstado(e, cuerpo);
        LocalDateTime ahora = ahora();
        e.setCreadoEl(ahora);
        e.setCreadoPor(actor());
        e.setActualizadoEl(ahora);
        e.setActualizadoPor(actor());
        e.setVersion(1);
        estudios.save(e);
        return detalle(e, aviso);
    }

    /**
     * Calcula un estudio que llega entero sin guardarlo: es lo que usan los estudios locales (solo en el navegador) y la copia de
     * trabajo de un estudio compartido mientras no se pulsa «Guardar cambios».
     */
    public Map<String, Object> calcularSinGuardar(Map<String, Object> cuerpo) {
        pl_Estudio e = transitorio(cuerpo);
        return detalle(e, aplicarEstado(e, cuerpo));
    }

    /**
     * Las recomendaciones de la biblioteca para un estudio que llega entero: cada texto con los números del estudio y si se sugiere
     * (según su condición). No guarda nada.
     */
    public Map<String, Object> recomendacionesDe(Map<String, Object> cuerpo) {
        pl_Estudio e = transitorio(cuerpo);
        aplicarEstado(e, cuerpo);
        Curva curva = curvaDe(e);
        if (curva == null) throw invalido("Falta la curva de consumo: sube el SIPS o la curva de la distribuidora.");
        Calculo calculo = Calculo.de(e.getDatos(), curva, reguladosDe(e.getDatos()), solarDe(e.getDatos()));
        List<Map<String, Object>> lista = new ArrayList<>();
        for (var r : RecomendacionesGeneradas.de(calculo, optimaDe(e.getDatos(), calculo))) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", r.id());
            f.put("titulo", r.titulo());
            f.put("texto", r.texto());
            f.put("sugerida", true);
            lista.add(f);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recomendaciones", lista);
        return m;
    }

    /** Lee el SIPS sin guardarlo: el suministro, la tarifa, las potencias y las lecturas (sin el titular ni su dirección). */
    public Map<String, Object> leerSips(byte[] contenido) {
        return datosDelSips(Sips.leer(contenido));
    }

    /** Lee la curva horaria sin guardarla: el año tipo, como texto, y de qué fichero sale. */
    public Map<String, Object> leerCurva(byte[] contenido, String nombre) {
        Curva c = Curva.leer(contenido, nombre);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("texto", c.comoTexto());
        m.put("estimada", false);
        m.put("origen", limitar(nombre == null ? "" : nombre, 255));
        return m;
    }

    /** El informe de un estudio que llega entero, sin guardarlo (HTML o PDF). */
    public PropuestaService.Documento informeSinGuardar(Map<String, Object> cuerpo, boolean enPdf) {
        pl_Estudio e = transitorio(cuerpo);
        aplicarEstado(e, cuerpo);
        List<Informe.CategoriaSel> modulos = modulosDe(cuerpo.get("informe"));
        return enPdf ? documentoPdf(e, modulos) : new PropuestaService.Documento(informe(e, modulos).getBytes(java.nio.charset.StandardCharsets.UTF_8), nombreArchivo(e) + ".html");
    }

    /**
     * Los módulos que se piden para el informe: categorías en su orden y, en cada una, sus módulos (las gráficas con su SVG, que luego se
     * limpia). Null si no se pide nada: entonces va el informe completo de siempre.
     */
    private static List<Informe.CategoriaSel> modulosDe(Object o) {
        if (!(o instanceof Map<?, ?> m) || !(m.get("categorias") instanceof List<?> cats)) return null;
        if (cats.size() > 12) throw invalido("Demasiadas categorías en el informe.");
        List<Informe.CategoriaSel> r = new ArrayList<>();
        int total = 0;
        for (Object c : cats) {
            if (!(c instanceof Map<?, ?> cm) || !(cm.get("modulos") instanceof List<?> mods)) continue;
            List<Informe.ModuloSel> ms = new ArrayList<>();
            for (Object x : mods) {
                if (!(x instanceof Map<?, ?> mm) || !(mm.get("id") instanceof String id)) continue;
                if (++total > 40) throw invalido("Demasiados módulos en el informe.");
                List<String> svgs = new ArrayList<>();
                if (mm.get("svgs") instanceof List<?> l) {
                    if (l.size() > 4) throw invalido("Demasiadas gráficas en un módulo.");
                    for (Object v : l) if (v instanceof String sv) svgs.add(sv);
                }
                String titulo = mm.get("titulo") instanceof String t ? limitar(t, 255) : null;
                String texto = mm.get("texto") instanceof String t ? limitar(t, 6000) : null;
                ms.add(new Informe.ModuloSel(id, svgs, titulo, texto));
            }
            r.add(new Informe.CategoriaSel(String.valueOf(cm.get("id")), ms));
        }
        return r;
    }

    private pl_Estudio transitorio(Map<String, Object> cuerpo) {
        pl_Estudio e = new pl_Estudio();
        e.setId(cuerpo.get("id") == null ? "" : limitar(String.valueOf(cuerpo.get("id")), 64));
        e.setDatos(datosIniciales(""));
        LocalDateTime ahora = ahora();
        e.setCreadoEl(ahora);
        e.setActualizadoEl(ahora);
        e.setActualizadoPor(actor());
        return e;
    }

    /**
     * Pone en el estudio lo que llega entero: cliente, proyecto, observaciones, datos (también los del SIPS) y curva. Una curva real
     * llega como texto; si no la hay, con SIPS se estima. Después calcula. Devuelve el aviso de la estimación, si lo hay.
     */
    @SuppressWarnings("unchecked")
    private String aplicarEstado(pl_Estudio e, Map<String, Object> cuerpo) {
        if (cuerpo.get("customer") instanceof Map<?, ?> c) ponerCliente(e, c);
        if (cuerpo.containsKey("observaciones")) e.setObservaciones(texto(cuerpo, "observaciones", 5000));
        if (cuerpo.containsKey("proyecto")) ponerProyecto(e, texto(cuerpo, "proyecto", 80));
        if (cuerpo.get("datos") instanceof Map<?, ?> d) {
            Map<String, Object> datos = new LinkedHashMap<>(e.getDatos());
            fusionar(datos, (Map<String, Object>) d);
            fusionarSips(datos, (Map<String, Object>) d);
            datos.put("cliente", e.getCliente());
            e.setDatos(datos);
            e.setCups(String.valueOf(datos.getOrDefault("cups", "")));
        }
        String aviso = null;
        Map<String, Object> curva = mapaDe(cuerpo.get("curva"));
        String texto = curva.get("texto") == null ? "" : String.valueOf(curva.get("texto"));
        if (!texto.isBlank() && !Boolean.TRUE.equals(curva.get("estimada"))) {
            try {
                Curva.desdeTexto(texto, false, "");
            } catch (RuntimeException ex) {
                throw invalido("La curva de consumo no tiene el formato esperado: vuelve a subirla.");
            }
            e.setCurva(texto);
            e.setCurvaEstimada(false);
            e.setCurvaOrigen(corto(curva.get("origen"), 255));
        } else if (e.getDatos().get("sips") instanceof Map<?, ?>) {
            aviso = estimarCurva(e);
        } else {
            e.setCurva(null);
            e.setCurvaEstimada(false);
            e.setCurvaOrigen("");
        }
        calcular(e);
        return aviso;
    }

    /**
     * Guarda los cambios de un estudio compartido. Si llega la versión con la que se abrió y otra persona guardó después, avisa (409); con
     * «force» se guarda encima. Si llega la curva, el estudio llega entero (copia de trabajo del navegador).
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> actualizar(String id, Map<String, Object> cuerpo) {
        pl_Estudio e = buscar(id);
        exigirCambioPermitido(e, cuerpo);
        if (cuerpo.get("version") != null && !Boolean.TRUE.equals(cuerpo.get("force"))) {
            int version = (int) num(cuerpo, "version", -1);
            if (version != e.getVersion()) {
                String quien = e.getActualizadoPor().isBlank() ? "Otra persona" : e.getActualizadoPor();
                throw new ErrorApi(HttpStatus.CONFLICT, quien + " ha guardado una versión más reciente de este estudio mientras lo tenías abierto.");
            }
        }
        if (cuerpo.containsKey("curva")) {
            String aviso = aplicarEstado(e, cuerpo);
            guardado(e);
            return detalle(e, aviso);
        }
        if (cuerpo.get("customer") instanceof Map<?, ?> c) {
            ponerCliente(e, c);
        } else if (cuerpo.containsKey("cliente")) {
            String cliente = texto(cuerpo, "cliente", 255);
            if (cliente.isEmpty()) throw invalido("Escribe el nombre del cliente.");
            ponerCliente(e, Map.of("kind", "potencial", "name", cliente));
        }
        if (cuerpo.containsKey("estado")) {
            String estado = String.valueOf(cuerpo.get("estado"));
            if (!ESTADOS.containsKey(estado)) throw invalido("Estado no válido.");
            e.setEstado(estado);
        }
        if (cuerpo.containsKey("observaciones")) e.setObservaciones(texto(cuerpo, "observaciones", 5000));
        if (cuerpo.containsKey("proyecto")) ponerProyecto(e, texto(cuerpo, "proyecto", 80));
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
        guardado(e);
        return detalle(e, aviso);
    }

    /** Anota quién y cuándo guardó, sube la versión y lo guarda. */
    private void guardado(pl_Estudio e) {
        e.setActualizadoEl(ahora());
        e.setActualizadoPor(actor());
        e.setVersion(e.getVersion() + 1);
        estudios.save(e);
    }

    /**
     * Un estudio aceptado queda bloqueado: solo se le puede cambiar el estado (para reabrirlo), y eso no si alguno de sus presupuestos
     * ya tiene una propuesta entregada o validada.
     */
    private void exigirCambioPermitido(pl_Estudio e, Map<String, Object> cuerpo) {
        if (!"aceptado".equals(e.getEstado())) return;
        boolean soloEstado = cuerpo.keySet().stream().allMatch(k -> Set.of("estado", "version", "force").contains(k));
        if (!soloEstado) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Un estudio aceptado está bloqueado: cambia su estado para poder modificarlo.");
        }
        if (cuerpo.containsKey("estado") && !"aceptado".equals(String.valueOf(cuerpo.get("estado"))) && conPropuestaEnCurso(e.getId())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Un presupuesto de este estudio tiene una propuesta entregada o validada: no se reabre el estudio.");
        }
    }

    private boolean conPropuestaEnCurso(String estudioId) {
        return presupuestos.findByEstudioId(estudioId).stream().anyMatch(b -> propuestas.findByPresupuestoId(b.getId()).stream()
                .anyMatch(p -> "enviada".equals(p.getEstado()) || "aprobada".equals(p.getEstado())));
    }

    @Transactional
    public void eliminar(String id) {
        pl_Estudio e = buscar(id);
        if ("aceptado".equals(e.getEstado())) {
            throw new ErrorApi(HttpStatus.CONFLICT, "Un estudio aceptado está bloqueado: cambia su estado para poder eliminarlo.");
        }
        // Los presupuestos asociados se quedan sin estudio (no se borran)
        for (pl_Presupuesto p : presupuestos.findByEstudioId(id)) {
            p.setEstudioId("");
            presupuestos.save(p);
        }
        estudios.delete(e);
    }

    // ------------------------------------------------------------------ ficheros

    /** Lo que se guarda del SIPS: el suministro, la tarifa, las potencias y las lecturas. El titular y su dirección no se almacenan. */
    private static Map<String, Object> datosDelSips(Sips s) {
        Map<String, Object> datos = new LinkedHashMap<>();
        Map<String, String> sum = s.suministro();
        datos.put("cups", limitar(sum.getOrDefault("CUPS", ""), 40));
        datos.put("tarifa", limitar(sum.getOrDefault("Tarifa", ""), 20));
        datos.put("localidad", limitar(sum.getOrDefault("Localidad", ""), 120));
        datos.put("provincia", limitar(sum.getOrDefault("Provincia", ""), 120));
        datos.put("cnae", limitar(sum.getOrDefault("CNAE", ""), 120));
        List<Double> pot = new ArrayList<>();
        for (double p : s.potencias()) pot.add(p);
        datos.put("potencias", pot);
        Map<String, Object> lecturas = s.aMapa();
        lecturas.remove("suministro");
        datos.put("sips", lecturas);
        return datos;
    }

    /**
     * Los datos del SIPS que llegan del navegador (los leyó antes «leerSips»): CNAE, potencias y lecturas. Vacíos o null, se quitan.
     * Las lecturas se comprueban leyéndolas como lo hará la estimación de la curva.
     */
    private static void fusionarSips(Map<String, Object> destino, Map<String, Object> nuevo) {
        if (nuevo.containsKey("cnae")) destino.put("cnae", corto(nuevo.get("cnae"), 120));
        if (nuevo.containsKey("potencias")) {
            Object p = nuevo.get("potencias");
            if (p == null || (p instanceof List<?> l && l.isEmpty())) {
                destino.remove("potencias");
            } else {
                List<Object> seis = seisValores(p, 0, 1_000_000, "La potencia contratada de P", false);
                destino.put("potencias", seis);
            }
        }
        if (nuevo.containsKey("sips")) {
            Object s = nuevo.get("sips");
            if (!(s instanceof Map<?, ?> m) || m.isEmpty()) {
                destino.remove("sips");
            } else {
                Object lecturas = m.get("lecturas");
                if (lecturas instanceof List<?> l && l.size() > 240) throw invalido("El SIPS trae demasiadas lecturas.");
                try {
                    Sips.lecturasDeMapa(lecturas);
                    Numeros.aNumero(m.get("kwhAnual") == null ? 0 : m.get("kwhAnual"));
                } catch (RuntimeException ex) {
                    throw invalido("Los datos del SIPS no tienen el formato esperado: vuelve a subirlo.");
                }
                Map<String, Object> limpio = new LinkedHashMap<>();
                limpio.put("potencias", m.get("potencias"));
                limpio.put("kwhAnual", m.get("kwhAnual"));
                limpio.put("lecturas", lecturas);
                destino.put("sips", limpio);
            }
        }
    }

    // ------------------------------------------------------------------ informe

    @Transactional(readOnly = true)
    public PropuestaService.Documento html(String id) {
        pl_Estudio e = buscar(id);
        return new PropuestaService.Documento(informe(e).getBytes(java.nio.charset.StandardCharsets.UTF_8), nombreArchivo(e) + ".html");
    }

    @Transactional(readOnly = true)
    public PropuestaService.Documento pdf(String id) {
        return documentoPdf(buscar(id));
    }

    private PropuestaService.Documento documentoPdf(pl_Estudio e) {
        return documentoPdf(e, null);
    }

    private PropuestaService.Documento documentoPdf(pl_Estudio e, List<Informe.CategoriaSel> modulos) {
        String html = informe(e, modulos);
        try {
            return new PropuestaService.Documento(pdf.generar(html), nombreArchivo(e) + ".pdf");
        } catch (PropuestaPdf.SinNavegador ex) {
            throw new ErrorApi(HttpStatus.NOT_IMPLEMENTED, ex.getMessage());
        } catch (RuntimeException ex) {
            throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el PDF: " + ex.getMessage());
        }
    }

    private String informe(pl_Estudio e) {
        return informe(e, null);
    }

    private String informe(pl_Estudio e, List<Informe.CategoriaSel> modulos) {
        Curva curva = curvaDe(e);
        if (curva == null) throw invalido("Falta la curva de consumo: sube el SIPS o la curva de la distribuidora.");
        Calculo calculo = Calculo.de(e.getDatos(), curva, reguladosDe(e.getDatos()), solarDe(e.getDatos()));
        return modulos == null ? Informe.generar(calculo, LocalDate.now()) : Informe.generar(calculo, LocalDate.now(), modulos);
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
        CurvaEstimada.Horario h = horarioDe(hor);
        try {
            List<Sips.Lectura> lecturas = Sips.lecturasDeMapa(sips.get("lecturas"));
            // Con la tarifa conocida, cada periodo del SIPS se reparte solo entre sus horas; sin ella, solo por el horario
            Curva c = CurvaEstimada.crear(lecturas, num(sips, "kwhAnual", 0), h, Periodos.normalizar(datos.get("tarifa") == null ? null : String.valueOf(datos.get("tarifa"))));
            e.setCurva(c.comoTexto());
            e.setCurvaEstimada(true);
            e.setCurvaOrigen(c.origen());
            // La curva sale bien, pero se contrasta con el reparto por periodos del SIPS y se avisa si no cuadra
            return CurvaEstimada.avisoPeriodos(c, lecturas, String.valueOf(datos.getOrDefault("tarifa", "3.0TD")));
        } catch (ErrorApi ex) {
            e.setCurva(null);
            e.setCurvaEstimada(false);
            e.setCurvaOrigen("");
            return ex.getMessage();
        }
    }

    /** El horario guardado en los datos (el de siempre y las jornadas distintas de algunos meses). */
    private static CurvaEstimada.Horario horarioDe(Map<String, Object> hor) {
        List<CurvaEstimada.Excepcion> excepciones = new ArrayList<>();
        if (hor.get("excepciones") instanceof List<?> lista) {
            for (Object o : lista) {
                Map<String, Object> x = mapaDe(o);
                List<Integer> meses = new ArrayList<>();
                if (x.get("meses") instanceof List<?> ms) for (Object m : ms) meses.add(((Number) m).intValue());
                excepciones.add(new CurvaEstimada.Excepcion(meses, (int) num(x, "ini", 7), (int) num(x, "fin", 19), num(x, "fraccionFuera", 0.15), num(x, "fraccionFinde", 0.10), tramosDe(x)));
            }
        }
        return new CurvaEstimada.Horario((int) num(hor, "ini", 7), (int) num(hor, "fin", 19), num(hor, "fraccionFuera", 0.15), num(hor, "fraccionFinde", 0.10), excepciones, tramosDe(hor));
    }

    /** Las franjas extra guardadas de un horario o jornada (además de la de {@code ini} a {@code fin}). */
    private static List<CurvaEstimada.Tramo> tramosDe(Map<String, Object> m) {
        List<CurvaEstimada.Tramo> r = new ArrayList<>();
        if (m.get("tramos") instanceof List<?> lista) {
            for (Object o : lista) {
                Map<String, Object> t = mapaDe(o);
                r.add(new CurvaEstimada.Tramo((int) num(t, "ini", 0), (int) num(t, "fin", 1)));
            }
        }
        return r;
    }

    /** Las franjas extra que llegan, comprobadas: hasta cuatro, dentro de 0 a 24 h y sin solaparse con la principal ni entre sí. */
    @SuppressWarnings("unchecked")
    private static List<Object> tramosComprobados(Object o, int ini, int fin) {
        if (o == null) return new ArrayList<>();
        if (!(o instanceof List<?> lista) || lista.size() > 4) throw invalido("Las franjas extra del horario son una lista de hasta cuatro.");
        List<Object> r = new ArrayList<>();
        List<int[]> todas = new ArrayList<>();
        todas.add(new int[] {ini, fin});
        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> m)) throw invalido("Una franja del horario no es válida.");
            Map<String, Object> in = (Map<String, Object>) m;
            int a = (int) rango(in, "ini", 0, 23, "La hora de inicio");
            int b = (int) rango(in, "fin", 1, 24, "La hora de fin");
            if (b <= a) throw invalido("La hora de fin debe ser posterior a la de inicio.");
            for (int[] t : todas) if (a < t[1] && t[0] < b) throw invalido("Las franjas del horario no pueden solaparse.");
            todas.add(new int[] {a, b});
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("ini", a);
            t.put("fin", b);
            r.add(t);
        }
        return r;
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
        e.setResultado(Calculo.de(datos, curva, reguladosDe(datos), solarDe(datos)).aMapa());
    }

    /**
     * La inclinación que más produce en la ubicación del estudio (con la misma orientación), buscada con pocas consultas a PVGIS.
     * Null si la producción no sale de PVGIS (sin ubicación) o si PVGIS no responde: la recomendación sale igual, sin esa comparación.
     */
    private InclinacionOptima.Resultado optimaDe(Map<String, Object> datos, Calculo c) {
        if (!c.conPvgis) return null;
        Map<String, Object> ins = mapaDe(datos.get("instalacion"));
        if (ins.get("latitud") == null || ins.get("longitud") == null) return null;
        double lat = num(ins, "latitud", 0), lon = num(ins, "longitud", 0);
        String orientacion = c.orientacionElegida;
        int azimut = (int) Math.round(c.azimut);
        try {
            return InclinacionOptima.buscar((int) Math.round(c.inclinacion), a -> pvgis.produccionAnual(lat, lon, orientacion, a, azimut));
        } catch (ErrorApi e) {
            return null;
        }
    }

    /** Producción solar real (PVGIS) de la ubicación y la orientación del estudio. Sin ubicación se usa el modelo sencillo. */
    private PerfilSolar solarDe(Map<String, Object> datos) {
        Map<String, Object> ins = mapaDe(datos.get("instalacion"));
        Double lat = ins.get("latitud") == null ? null : num(ins, "latitud", 0), lon = ins.get("longitud") == null ? null : num(ins, "longitud", 0);
        return pvgis.perfil(lat, lon, String.valueOf(ins.getOrDefault("orientacion", "este-oeste")), num(ins, "inclinacion", 15), num(ins, "azimut", 0));
    }

    /** Lugares que coinciden con un texto, para poner la ubicación de la instalación. */
    public List<Map<String, Object>> buscarLugar(String texto) {
        return pvgis.buscarLugar(texto);
    }

    /**
     * Peajes y cargos del BOE de la tarifa del estudio. Se usa el BOE que el estudio tenga elegido; si no elige ninguno (o el elegido ya no
     * existe), el vigente hoy: el que empezó más recientemente. Si ninguno ha empezado todavía se usa el más antiguo, con un aviso. Null si
     * la tarifa no se reconoce o no hay ningún BOE con sus precios.
     */
    private PreciosRegulados reguladosDe(Map<String, Object> datos) {
        String tarifa = Periodos.normalizar(datos.get("tarifa") == null ? null : String.valueOf(datos.get("tarifa")));
        if (tarifa == null) return null;
        List<pl_Boe> todos = boes.findAllByOrderByVigenteDesdeDescIdDesc();
        if (todos.isEmpty()) return null;
        Map<String, Object> eco = mapaDe(datos.get("economia"));
        pl_Boe elegido = null;
        String aviso = null;
        if (!vacio(eco.get("boeId"))) {
            int id = (int) num(eco, "boeId", -1);
            elegido = todos.stream().filter(b -> b.getId() == id).findFirst().orElse(null);
            if (elegido == null) aviso = "El BOE elegido para este estudio ya no existe: se usa el vigente.";
        }
        if (elegido == null) {
            LocalDate hoy = LocalDate.now();
            elegido = todos.stream().filter(b -> !b.getVigenteDesde().isAfter(hoy)).findFirst().orElse(null);
            if (elegido == null) {
                elegido = todos.get(todos.size() - 1);
                aviso = "Ningún BOE está vigente todavía: se usa «" + elegido.getNombre() + "», que vale desde el " + elegido.getVigenteDesde() + ".";
            }
        }
        double[] energia = new double[6], potencia = new double[6];
        Set<String> referencias = new LinkedHashSet<>();
        boolean hayPrecios = false;
        for (pl_PrecioRegulado r : preciosRegulados.findByBoeIdAndTarifa(elegido.getId(), tarifa)) {
            hayPrecios = true;
            double[] destino = "energia".equals(r.getTermino()) ? energia : "potencia".equals(r.getTermino()) ? potencia : null;
            if (destino == null) continue;
            java.math.BigDecimal[] v = r.periodos();
            for (int i = 0; i < 6; i++) if (v[i] != null) destino[i] += v[i].doubleValue();
            if (!r.getReferencia().isBlank()) referencias.add(r.getReferencia());
        }
        if (!hayPrecios) return null;
        return new PreciosRegulados(elegido.getNombre(), elegido.getVigenteDesde(), tarifa, energia, potencia, String.join(", ", referencias), aviso);
    }

    // ------------------------------------------------------------------ cliente

    /**
     * El cliente del estudio, como en el presupuestador: uno de la intranet («existente», con su id) o un cliente potencial (sin id,
     * con su nombre y, si se tienen, CIF, teléfono y email). El nombre va también a la columna «cliente» para la lista y el informe.
     */
    private static void ponerCliente(pl_Estudio e, Map<?, ?> c) {
        String tipo = String.valueOf(c.get("kind"));
        String nombre = c.get("name") == null ? "" : String.valueOf(c.get("name")).strip();
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
            if (idIntranet == null) throw invalido("Falta el cliente de la intranet al que se vincula el estudio.");
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
            throw invalido("Elige el cliente del estudio (uno existente o un potencial).");
        }
        e.setCliente(nombre);
        e.setClienteId(idIntranet);
        e.setClienteTipo(tipo);
        Map<String, Object> datos = new LinkedHashMap<>(e.getDatos());
        datos.put("cliente", nombre);
        datos.put("customer", cliente);
        e.setDatos(datos);
    }

    /** Nombre del proyecto (como en el presupuestador: «Nave 2, fotovoltaica»); opcional. */
    private static void ponerProyecto(pl_Estudio e, String proyecto) {
        Map<String, Object> datos = new LinkedHashMap<>(e.getDatos());
        datos.put("proyecto", proyecto);
        e.setDatos(datos);
    }

    private static String corto(Object v, int max) {
        String t = v == null ? "" : String.valueOf(v).strip();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** El cliente tal como lo usa la pantalla; los estudios de antes de elegirlo pasan por clientes potenciales con su nombre. */
    private static Map<String, Object> clienteDe(pl_Estudio e) {
        if (e.getDatos().get("customer") instanceof Map<?, ?> m && m.get("kind") != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            m.forEach((k, v) -> c.put(String.valueOf(k), v));
            return c;
        }
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("kind", "potencial");
        c.put("name", e.getCliente());
        c.put("cif", "");
        c.put("phone", "");
        c.put("email", "");
        return c;
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
        ins.put("latitud", null);
        ins.put("longitud", null);
        ins.put("inclinacion", 15);
        ins.put("azimut", 0);
        ins.put("produccionEspecifica", null);
        d.put("instalacion", ins);
        Map<String, Object> eco = new LinkedHashMap<>();
        eco.put("precioWp", 0.85);
        eco.put("precioBateriaKwh", 250);
        eco.put("inversionTotal", null);
        eco.put("presupuestoId", "");
        eco.put("presupuestoTotal", null);
        eco.put("presupuestoKwp", null);
        eco.put("precioEnergia", 0.10);
        eco.put("modo", "red");
        eco.put("precioGasoil", 1.20);
        eco.put("consumoGrupo", 0.30);
        eco.put("mantenimientoGrupo", 0.03);
        eco.put("mantenimientoPct", 1.0);
        eco.put("mantenimiento", null);
        eco.put("ahorroPotencia", 0);
        eco.put("modoPrecio", "medio");
        eco.put("preciosPeriodo", Arrays.asList(null, null, null, null, null, null));
        eco.put("incluyePeajes", false);
        eco.put("boeId", null);
        eco.put("reduccionPotencia", Arrays.asList(0, 0, 0, 0, 0, 0));
        eco.put("financiacionMeses", 0);
        eco.put("financiacionTae", 9.8);
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
                if (!o.equals("sur") && !o.equals("este-oeste") && !o.equals("personalizada")) throw invalido("La orientación debe ser sur, este-oeste o personalizada.");
                ins.put("orientacion", o);
            }
            if (in.containsKey("latitud")) ins.put("latitud", vacio(in.get("latitud")) ? null : rango(in, "latitud", -90, 90, "La latitud"));
            if (in.containsKey("longitud")) ins.put("longitud", vacio(in.get("longitud")) ? null : rango(in, "longitud", -180, 180, "La longitud"));
            if (in.containsKey("inclinacion")) ins.put("inclinacion", vacio(in.get("inclinacion")) ? 15 : rango(in, "inclinacion", 0, 90, "La inclinación"));
            if (in.containsKey("azimut")) ins.put("azimut", vacio(in.get("azimut")) ? 0 : rango(in, "azimut", -180, 180, "El azimut"));
            if (in.containsKey("produccionEspecifica")) {
                ins.put("produccionEspecifica", vacio(in.get("produccionEspecifica")) ? null : rango(in, "produccionEspecifica", 500, 2500, "La producción específica (kWh/kWp·año)"));
            }
            destino.put("instalacion", ins);
        }
        if (nuevo.get("economia") instanceof Map<?, ?> m) {
            Map<String, Object> eco = new LinkedHashMap<>(mapaDe(destino.get("economia")));
            Map<String, Object> in = (Map<String, Object>) m;
            for (Object[] c : new Object[][]{{"precioWp", 0.05, 5.0, "El precio por Wp"}, {"precioBateriaKwh", 0.0, 3000.0, "El precio de la batería (€/kWh)"},
                    {"precioEnergia", 0.005, 1.0, "El precio de la energía (€/kWh)"}, {"precioGasoil", 0.2, 5.0, "El precio del gasoil (€/L)"}, {"consumoGrupo", 0.15, 1.0, "El consumo del grupo (L/kWh)"}, {"mantenimientoGrupo", 0.0, 2.0, "El mantenimiento del grupo (€/kWh)"}, {"mantenimientoPct", 0.0, 10.0, "El mantenimiento (% de la inversión)"},
                    {"ahorroPotencia", 0.0, 10_000_000.0, "El ahorro en potencia"}, {"reposicion", 0.0, 100_000_000.0, "El cambio de equipos"}}) {
                String k = (String) c[0];
                if (in.containsKey(k)) eco.put(k, rango(in, k, (double) c[1], (double) c[2], (String) c[3]));
            }
            for (Object[] c : new Object[][]{{"inversionTotal", 1.0e9, "La inversión total"}, {"mantenimiento", 1.0e7, "El mantenimiento anual"},
                    {"presupuestoTotal", 1.0e9, "El importe del presupuesto de referencia"}, {"presupuestoKwp", 100_000.0, "Los kWp del presupuesto de referencia"}}) {
                String k = (String) c[0];
                if (in.containsKey(k)) eco.put(k, vacio(in.get(k)) ? null : rango(in, k, 0, (double) c[1], (String) c[2]));
            }
            // El presupuesto del que sale el coste de la instalación (su id, para saber cuál es; el importe y los kWp son los de cuando se eligió)
            if (in.containsKey("presupuestoId")) eco.put("presupuestoId", limitar(String.valueOf(in.get("presupuestoId") == null ? "" : in.get("presupuestoId")).strip(), 64));
            if (in.containsKey("reposicionAno")) eco.put("reposicionAno", (int) rango(in, "reposicionAno", 1, 25, "El año de cambio de equipos"));
            if (in.containsKey("modo")) {
                String modo = String.valueOf(in.get("modo"));
                if (!modo.equals("red") && !modo.equals("aislada")) throw invalido("La instalación es conectada a red o aislada con grupo electrógeno.");
                eco.put("modo", modo);
            }
            if (in.containsKey("modoPrecio")) {
                String modo = String.valueOf(in.get("modoPrecio"));
                if (!modo.equals("medio") && !modo.equals("periodos")) throw invalido("El precio de la energía va como precio medio o por periodos.");
                eco.put("modoPrecio", modo);
            }
            if (in.containsKey("financiacionMeses")) eco.put("financiacionMeses", vacio(in.get("financiacionMeses")) ? 0 : (int) rango(in, "financiacionMeses", 0, 240, "Los meses de financiación"));
            if (in.containsKey("financiacionTae")) eco.put("financiacionTae", vacio(in.get("financiacionTae")) ? 9.8 : rango(in, "financiacionTae", 0, 30, "El interés de la financiación (TAE)"));
            if (in.containsKey("incluyePeajes")) eco.put("incluyePeajes", Boolean.TRUE.equals(in.get("incluyePeajes")));
            if (in.containsKey("boeId")) {
                eco.put("boeId", vacio(in.get("boeId")) ? null : (int) rango(in, "boeId", 1, Integer.MAX_VALUE, "El BOE elegido"));
            }
            if (in.containsKey("preciosPeriodo")) eco.put("preciosPeriodo", seisValores(in.get("preciosPeriodo"), 0, 1, "El precio de la energía de P", true));
            if (in.containsKey("reduccionPotencia")) eco.put("reduccionPotencia", seisValores(in.get("reduccionPotencia"), 0, 1_000_000, "La reducción de potencia de P", false));
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
            if (in.containsKey("tramos") || in.containsKey("ini") || in.containsKey("fin")) {
                hor.put("tramos", tramosComprobados(in.containsKey("tramos") ? in.get("tramos") : hor.get("tramos"), (int) num(hor, "ini", 7), (int) num(hor, "fin", 19)));
            }
            if (in.containsKey("excepciones")) hor.put("excepciones", excepcionesDeHorario(in.get("excepciones")));
            destino.put("horario", hor);
        }
    }

    /** Las jornadas distintas de algunos meses, comprobadas: cada una con sus meses (1 a 12, sin repetirse entre jornadas) y su horario. */
    @SuppressWarnings("unchecked")
    private static List<Object> excepcionesDeHorario(Object o) {
        if (o == null) return new ArrayList<>();
        if (!(o instanceof List<?> lista) || lista.size() > 6) throw invalido("Las jornadas distintas por meses son una lista de hasta seis.");
        List<Object> r = new ArrayList<>();
        List<Integer> usados = new ArrayList<>();
        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> m)) throw invalido("Una jornada distinta no es válida.");
            Map<String, Object> in = (Map<String, Object>) m;
            if (!(in.get("meses") instanceof List<?> ms) || ms.isEmpty()) throw invalido("Elige a qué meses se aplica cada jornada distinta.");
            List<Object> meses = new ArrayList<>();
            for (Object mes : ms) {
                if (!(mes instanceof Number n) || n.intValue() < 1 || n.intValue() > 12) throw invalido("Los meses van de 1 a 12.");
                if (usados.contains(n.intValue())) throw invalido("Un mes no puede tener dos jornadas distintas.");
                usados.add(n.intValue());
                meses.add(n.intValue());
            }
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("meses", meses);
            x.put("ini", (int) rango(in, "ini", 0, 23, "La hora de inicio"));
            x.put("fin", (int) rango(in, "fin", 1, 24, "La hora de fin"));
            x.put("fraccionFuera", rango(in, "fraccionFuera", 0, 1, "El consumo fuera de horario"));
            x.put("fraccionFinde", rango(in, "fraccionFinde", 0, 1, "El consumo en fin de semana"));
            if (num(x, "fin", 19) <= num(x, "ini", 7)) throw invalido("La hora de fin debe ser posterior a la de inicio.");
            x.put("tramos", tramosComprobados(in.get("tramos"), (int) num(x, "ini", 7), (int) num(x, "fin", 19)));
            r.add(x);
        }
        return r;
    }

    /** Seis valores P1 a P6 comprobados; los vacíos quedan a null (o a 0 si {@code vaciosNulos} es false). */
    private static List<Object> seisValores(Object o, double min, double max, String nombre, boolean vaciosNulos) {
        if (!(o instanceof List<?> l) || l.size() > 6) throw invalido(nombre + "1 a P6 deben ser una lista de seis valores.");
        List<Object> r = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Object v = i < l.size() ? l.get(i) : null;
            if (vacio(v)) {
                r.add(vaciosNulos ? null : 0);
            } else {
                Map<String, Object> m = new HashMap<>();
                m.put("v", v);
                r.add(rango(m, "v", min, max, nombre + (i + 1)));
            }
        }
        return r;
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
        m.put("customer", clienteDe(e));
        Object proyecto = e.getDatos().get("proyecto");
        m.put("proyecto", proyecto == null ? "" : String.valueOf(proyecto));
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
        m.put("version", e.getVersion());
        m.put("creadoPor", e.getCreadoPor());
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
        // La curva real va entera: es parte del estudio que se guarda en el navegador (la estimada se vuelve a sacar del SIPS)
        if (!e.isCurvaEstimada() && e.getCurva() != null && !e.getCurva().isBlank()) curva.put("texto", e.getCurva());
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
