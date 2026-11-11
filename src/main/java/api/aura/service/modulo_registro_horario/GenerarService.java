package api.aura.service.modulo_registro_horario;

import api.aura.config.ErrorApi;
import api.aura.dto.modulo_registro_horario.DocumentoGenerado;
import api.aura.dto.modulo_registro_horario.EmpresaDTO;
import api.aura.dto.modulo_registro_horario.EmpresasRespuesta;
import api.aura.dto.modulo_registro_horario.GenerarRequest;
import api.aura.service.modulo_registro_horario.documento.DatosNoValidosException;
import api.aura.service.modulo_registro_horario.documento.DatosRegistro;
import api.aura.service.modulo_registro_horario.documento.MotorWord;
import api.aura.service.modulo_registro_horario.documento.PdfRegistro;
import api.aura.service.modulo_registro_horario.documento.ReglasJornada;
import api.aura.service.modulo_registro_horario.firma.FirmaService;
import api.aura.service.modulo_registro_horario.firma.TrabajosPesados;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Genera el registro de jornada del mes (PDF o Word) con los datos que manda la pantalla. Servicio sin datos personales:
 * todo se hace en memoria y no se guarda nada (ni NIF, ni nº de afiliación, ni firmas) en disco ni en el log.
 */
@Service
public class GenerarService {

    static final int MAX_FIRMA_BYTES = 2 * 1024 * 1024;
    private static final Pattern HORA = Pattern.compile("([01]?\\d|2[0-3]):[0-5]\\d");
    private static final Pattern FECHA = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]");
    private static final Set<String> ESTADOS = Set.of("MANANA", "TARDE", "FESTIVO", "VACACIONES", "PERMISO", "ENFERMEDAD", "LIBRE");
    private static final Logger log = LoggerFactory.getLogger(GenerarService.class);

    private final EmpresasConfig empresas;
    private final FirmaService firmas;
    private final MotorWord motor;
    private final TrabajosPesados trabajos;

    public GenerarService(EmpresasConfig empresas, FirmaService firmas, MotorWord motor, TrabajosPesados trabajos) {
        this.empresas = empresas;
        this.firmas = firmas;
        this.motor = motor;
        this.trabajos = trabajos;
    }

    // ---------------------------------------------------------------- validación

    private static ErrorApi invalido(String campo) {
        return new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY,
                "Hay un dato no válido (" + campo + "). Revísalo e inténtalo de nuevo.");
    }

    private static ErrorApi error422(String mensaje) {
        return new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }

    /** Texto obligatorio de 1 a {@code max} caracteres, sin caracteres de control (no caben en el XML); se devuelve sin espacios a los lados. */
    private static String texto(String valor, String campo, int max) {
        if (valor == null || valor.isEmpty() || valor.length() > max) {
            throw invalido(campo);
        }
        String limpio = valor.strip();
        if (limpio.isEmpty() || CONTROL.matcher(limpio).find()) {
            throw invalido(campo);
        }
        return limpio;
    }

    /** «H:MM» o «HH:MM» → «HH:MM». */
    private static String hhmm(String hora, String campo) {
        if (hora == null || !HORA.matcher(hora).matches()) {
            throw invalido(campo);
        }
        String[] p = hora.split(":");
        return String.format("%02d:%s", Integer.parseInt(p[0]), p[1]);
    }

    private static DatosRegistro.Tramo tramo(GenerarRequest.Tramo t, String campo) {
        if (t == null) {
            throw invalido(campo);
        }
        return new DatosRegistro.Tramo(hhmm(t.ini(), campo + " › ini"), hhmm(t.fin(), campo + " › fin"));
    }

    /** Los días recibidos (de estado_dias y horas_dia) deben ser fechas del mes pedido. */
    private static void comprobarDiasDelMes(int anio, int mes, Iterable<String> fechas) {
        String prefijo = String.format("%d-%02d-", anio, mes);
        for (String k : fechas) {
            LocalDate d;
            try {
                if (!FECHA.matcher(k).matches()) {
                    throw new DateTimeException(k);
                }
                d = LocalDate.parse(k);
            } catch (DateTimeException e) {
                throw error422("Fecha no válida: " + k.substring(0, Math.min(12, k.length())) + ".");
            }
            if (!k.startsWith(prefijo) || d.getDayOfMonth() > YearMonth.of(anio, mes).lengthOfMonth()) {
                throw error422("El día " + k + " no pertenece a "
                        + ReglasJornada.MESES[mes - 1].toLowerCase(Locale.ROOT) + " de " + anio + ".");
            }
        }
    }

    /** Nombre del archivo: Reg_horas_Nombre_Apellidos_AAAA-MM.pdf (con tildes; sin lo que los sistemas de archivos no admiten). */
    public static String nombreArchivo(String trabajador, int anio, int mes, String extension) {
        String limpio = trabajador.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", "");
        limpio = limpio.replaceAll("[.,;]+", " ");
        String nombre = String.join("_", limpio.strip().isEmpty() ? new String[0] : limpio.strip().split("(?U)\\s+"));
        if (nombre.codePointCount(0, nombre.length()) > 80) {
            nombre = nombre.substring(0, nombre.offsetByCodePoints(0, 80));
        }
        nombre = nombre.replaceAll("^_+|_+$", "");
        return String.format("Reg_horas_%s_%d-%02d.%s", nombre, anio, mes, extension);
    }

    /** Como {@code urllib.parse.quote}: UTF-8 con %XX, dejando solo letras, cifras y «_.-~». */
    public static String codificarNombre(String nombre) {
        return URLEncoder.encode(nombre, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    // ---------------------------------------------------------------- generar

    /**
     * Rellena la plantilla con los datos recibidos y devuelve el PDF (o el Word). No guarda nada.
     *
     * @throws ErrorApi 422 si algo no vale (con un mensaje que se muestra a la persona) o 500 si el documento no se pudo hacer
     */
    public DocumentoGenerado generar(GenerarRequest r) {
        // ---- formato de la petición (los mismos límites que el servicio anterior)
        if (r.anio() == null || r.anio() < 2000 || r.anio() > 2100) {
            throw invalido("anio");
        }
        if (r.mes() == null || r.mes() < 1 || r.mes() > 12) {
            throw invalido("mes");
        }
        int anio = r.anio(), mes = r.mes();
        String trabajador = texto(r.trabajador(), "trabajador", 120);
        String nif = texto(r.nif(), "nif", 30);
        String afiliacion = texto(r.numAfiliacion(), "num_afiliacion", 30);
        String empresaElegida = texto(r.empresa(), "empresa", 200);
        if (r.horarioBase() == null) {
            throw invalido("horario_base");
        }
        DatosRegistro.Tramo horaManana = tramo(r.horarioBase().manana(), "horario_base › manana");
        DatosRegistro.Tramo horaTarde = tramo(r.horarioBase().tarde(), "horario_base › tarde");
        if (r.estadoDias() == null || r.estadoDias().size() > 31) {
            throw invalido("estado_dias");
        }
        for (Map.Entry<String, String> e : r.estadoDias().entrySet()) {
            if (e.getKey() == null || !ESTADOS.contains(e.getValue())) {
                throw invalido("estado_dias");
            }
        }
        Map<String, GenerarRequest.HorasDia> horasEntrada = r.horasDia() == null ? Map.of() : r.horasDia();
        if (horasEntrada.size() > 31) {
            throw invalido("horas_dia");
        }
        Map<String, DatosRegistro.HorasDia> horas = new LinkedHashMap<>();
        for (Map.Entry<String, GenerarRequest.HorasDia> e : horasEntrada.entrySet()) {
            GenerarRequest.HorasDia h = e.getValue();
            if (h == null) {
                throw invalido("horas_dia");
            }
            horas.put(e.getKey(), new DatosRegistro.HorasDia(
                    h.manana() == null ? null : tramo(h.manana(), "horas_dia › manana"),
                    h.tarde() == null ? null : tramo(h.tarde(), "horas_dia › tarde")));
        }
        String firma64 = r.firmaPngBase64();
        if (firma64 == null || firma64.isEmpty() || firma64.length() > (int) (MAX_FIRMA_BYTES * 1.4)) {
            throw invalido("firma_png_base64");
        }
        String fechaFirma = r.fechaFirma() == null ? "ultimo_dia" : r.fechaFirma();
        if (!fechaFirma.equals("ultimo_dia")) {
            try {
                if (!FECHA.matcher(fechaFirma).matches()) {
                    throw new DateTimeException(fechaFirma);
                }
                LocalDate.parse(fechaFirma);
            } catch (DateTimeException e) {
                throw invalido("fecha_firma");
            }
        }
        String formato = r.formato() == null ? "pdf" : r.formato();
        if (!formato.equals("pdf") && !formato.equals("docx")) {
            throw invalido("formato");
        }

        // ---- lo que depende del mes, la lista de empresas y la imagen de la firma
        comprobarDiasDelMes(anio, mes, r.estadoDias().keySet());
        comprobarDiasDelMes(anio, mes, horasEntrada.keySet());
        EmpresasRespuesta config = empresas.respuesta();
        EmpresaDTO emp = config.empresas().stream().filter(e -> e.texto().equals(empresaElegida)).findFirst()
                .orElseThrow(() -> error422("La empresa elegida no está en la lista. Vuelve a elegirla."));
        byte[] png;
        try {
            png = Base64.getDecoder().decode(firma64);
        } catch (IllegalArgumentException e) {
            throw error422("La firma no es una imagen válida.");
        }
        if (png.length > MAX_FIRMA_BYTES) {
            throw error422("La imagen de la firma pesa demasiado.");
        }
        firmas.comprobarPng(png); // FirmaError (422) si no es una imagen legible o está vacía

        DatosRegistro datos = new DatosRegistro(trabajador, nif, afiliacion, emp.empresa(), emp.cif(), emp.ccc(),
                emp.centroTrabajo(), emp.localidad().isEmpty() ? config.localidad() : emp.localidad(),
                horaManana, horaTarde, new LinkedHashMap<>(r.estadoDias()), horas, fechaFirma);

        // ---- el documento (de dos en dos como mucho: el servidor tiene poca memoria)
        byte[] contenido;
        MotorWord.Resultado resultado;
        try {
            Object[] hecho = trabajos.ejecutar(() -> {
                MotorWord.Resultado res = motor.construir(datos, anio, mes, png);
                byte[] bytes = formato.equals("docx") ? res.documento().guardar() : PdfRegistro.generar(res.documento());
                return new Object[]{res, bytes};
            });
            resultado = (MotorWord.Resultado) hecho[0];
            contenido = (byte[]) hecho[1];
        } catch (DatosNoValidosException e) { // datos que no cuadran (horas, ningún día trabajado...): se explican a la persona
            String m = e.getMessage();
            throw error422(m.substring(0, 1).toUpperCase(Locale.ROOT) + m.substring(1));
        } catch (ErrorApi e) {
            throw e;
        } catch (RuntimeException e) { // el motivo real no se vuelca al log: podría llevar datos personales
            log.error("Error generando un documento ({})", e.getClass().getSimpleName());
            throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el documento.");
        }
        String tipo = formato.equals("docx") ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/pdf";
        return new DocumentoGenerado(contenido, tipo, nombreArchivo(trabajador, anio, mes, formato), formato,
                resultado.diasTrabajados(), ReglasJornada.fmtHoras(resultado.minutosTotales()));
    }
}
