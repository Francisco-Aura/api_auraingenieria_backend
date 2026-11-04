package api.aura.service.mcas;

import api.aura.dto.mcas.McasCampo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Textos MCAS para quien graba el contrato o valida la propuesta: sin códigos
 * internos, en usted y con tildes. La causa técnica se conserva en la traza y en
 * la auditoría; aquí solo se redacta lo que se enseña en pantalla.
 *
 * <p>Sin estado: todos los métodos son estáticos.</p>
 */
public final class McasMensajes {

    private static final String SE_REVISA_A_MANO = "; la comisión se revisará manualmente.";
    private static final String ERROR_CONFIGURACION =
            "La tabla de comisiones tiene un error de configuración; avise al administrador para que la revise.";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String AVISE_ADMINISTRADOR = "Avise al administrador para que lo revise.";

    /** Estados de la propuesta con la etiqueta de la pantalla (mcasEstados.js). */
    private static final Map<String, String> ESTADOS = Map.ofEntries(
            Map.entry("PENDIENTE_CALCULO", "Pendiente de cálculo"),
            Map.entry("REQUIERE_DATOS", "Faltan datos"),
            Map.entry("REQUIERE_REVISION_MANUAL", "Revisión manual"),
            Map.entry("ERROR_CALCULO", "Error de cálculo"),
            Map.entry("PENDIENTE_VALIDACION", "Pendiente de validación"),
            Map.entry("RECHAZADO", "Rechazada"),
            Map.entry("EN_EDICION", "En edición"),
            Map.entry("FINALIZADO_APROBADO", "Aprobada"),
            Map.entry("FINALIZADO_EDITADO", "Aprobada con edición"),
            Map.entry("FINALIZADO_NO_COMISIONABLE", "No comisionable"),
            Map.entry("DESACTUALIZADO", "Desactualizada"),
            Map.entry("CANCELADO", "Cancelada"),
            Map.entry("SUPERADO", "Superada"));

    /**
     * Etiquetas que no son un campo de la pestaña MCAS o que describen un valor no
     * válido (las de {@code McasProposalService.camposFaltantes}). Claves en minúsculas.
     */
    private static final Map<String, String> ETIQUETAS = Map.ofEntries(
            Map.entry("numero_contrato", "Número de contrato"),
            Map.entry("fecha_inicio", "Fecha de inicio"),
            Map.entry("comercializadora", "Comercializadora"),
            Map.entry("consumo_anual_no_negativo", "Consumo anual (no puede ser negativo)"),
            Map.entry("fee_no_negativo", "FEE del contrato (no puede ser negativo)"),
            Map.entry("unidad_fee", "Unidad del FEE"),
            Map.entry("unidad_fee_eur_mwh", "Unidad del FEE (debe ser €/MWh)"),
            Map.entry("potencia_no_negativa", "Potencias contratadas (no pueden ser negativas)"),
            Map.entry("producto_comercial_activo_y_compatible", "Tarifa de precios (producto) válida para este contrato"),
            Map.entry("numero_pagos_1_a_12", "Número de pagos (entre 1 y 12)"));

    // Causas técnicas que enumeran datos que faltan (también las lee camposDe).
    private static final Pattern LISTA_INVALIDOS = Pattern.compile("^Faltan o son inv[aá]lidos: (.*)$", Pattern.DOTALL);
    private static final Pattern LISTA_REGLAS =
            Pattern.compile("^Faltan datos requeridos por las reglas: (.*)$", Pattern.DOTALL);
    /** «X: dato requerido 'a'» o, si faltan varios a la vez, «X: dato requerido 'a', 'b'». */
    private static final Pattern DATO_REQUERIDO =
            Pattern.compile("^[A-Z_]+: dato requerido ('[^']*'(?:, '[^']*')*)$", Pattern.DOTALL);
    private static final Pattern ENTRE_COMILLAS = Pattern.compile("'([^']*)'");
    private static final Pattern CAMPO_REQUERIDO = Pattern.compile("^[A-Z_]+: (.+) requerido$", Pattern.DOTALL);

    /** Formatos técnicos conocidos, en orden: el primero que encaja decide. */
    private static final List<Traduccion> TRADUCCIONES = List.of(
            new Traduccion(LISTA_INVALIDOS, m ->
                    "Complete o corrija estos datos del contrato: "
                            + enumerar(listaCampos(separar(m.group(1))).stream().map(McasMensajes::minuscula).toList())
                            + "."),
            new Traduccion("^MCAS_ENABLED=false$", m ->
                    "El cálculo automático de comisiones está desactivado" + SE_REVISA_A_MANO),
            new Traduccion("^Fase FIJO no habilitada\\b.*$", m ->
                    "El cálculo automático de los contratos a precio fijo está desactivado" + SE_REVISA_A_MANO),
            new Traduccion("^Contratos INDEXADO no habilitados\\b.*$", m ->
                    "El cálculo automático de los contratos indexados aún no está activado" + SE_REVISA_A_MANO),
            new Traduccion("^Sin conjunto de reglas PUBLICADO para comercializadora=\\S* fecha=(\\S*)$", m ->
                    "Esta comercializadora no tiene una tabla de comisiones activa en la fecha del contrato"
                            + fechaEntreParentesis(m.group(1)) + SE_REVISA_A_MANO),
            new Traduccion("^\\d+ conjuntos PUBLICADOS coinciden con igual especificidad.*$", m ->
                    "Esta comercializadora tiene varias tablas de comisiones activas a la vez; "
                            + "avise al administrador para que lo revise."),
            new Traduccion("^Conjunto '.*' sin reglas activas$", m ->
                    "La tabla de comisiones de esta comercializadora no tiene reglas activas" + SE_REVISA_A_MANO),
            new Traduccion("^Ninguna regla coincide en conjunto '.*'$", m ->
                    "Ninguna regla de la tabla de comisiones se aplica a este contrato" + SE_REVISA_A_MANO),
            new Traduccion("^Solapamiento: .*$", m ->
                    "Varias reglas de la tabla de comisiones se aplican a la vez a este contrato; "
                            + "avise al administrador para que revise la tabla."),
            new Traduccion(LISTA_REGLAS, m -> faltanDatos(separar(m.group(1)))),
            new Traduccion(DATO_REQUERIDO, m -> faltanDatos(entreComillas(m.group(1)))),
            new Traduccion(CAMPO_REQUERIDO, m -> faltanDatos(List.of(m.group(1)))),
            new Traduccion("^(.+) fuera de la tabla de la regla .+: complete la comisi[oó]n manualmente\\.?$", m ->
                    m.group(1) + " fuera de la tabla de comisiones: complete la comisión manualmente."),
            new Traduccion("^Regla marcada como MANUAL$", m ->
                    "La tabla de comisiones indica que esta comisión se completa manualmente."),
            new Traduccion("^Este caso no genera comisi[oó]n$", m ->
                    "Según la tabla de comisiones, este contrato no genera comisión."),
            new Traduccion("^La pol[ií]tica de pago de la regla .+ no se puede leer; se reparte en un solo pago$", m ->
                    "No se ha podido leer el número de pagos de la regla; la comisión se reparte en un solo pago."),
            new Traduccion("^Sin pol[ií]tica de retroces[ií][oó]n asignada: .*\\bcoinciden\\b.*$", m ->
                    "No se ha podido mostrar la retrocesión potencial: hay varias políticas de retrocesión "
                            + "publicadas que encajan con este contrato. " + AVISE_ADMINISTRADOR),
            new Traduccion("^Sin pol[ií]tica de retroces[ií][oó]n asignada\\b.*$", m ->
                    "No se ha podido mostrar la retrocesión potencial. " + AVISE_ADMINISTRADOR),
            new Traduccion("^Error interno\\b.*$", m ->
                    "No se ha podido calcular la comisión por un error inesperado; avise al administrador."),
            new Traduccion("^(?:(?:FIXED_AMOUNT|RANGE_LOOKUP|MATRIX_LOOKUP|MULTI_DIMENSION_LOOKUP|RATE_PER_MWH"
                    + "|FEE_PERCENTAGE|COMPOSITE_SUM|MANUAL|NOT_COMMISSIONABLE): "
                    + "|Estrategia no registrada: |dimension de lookup no soportada: ).*$", m -> ERROR_CONFIGURACION));

    private McasMensajes() {}

    /**
     * Nombre para el usuario de un dato técnico: {@code consumo_kwh} → «Consumo
     * anual», {@code fee} → «FEE del contrato», {@code tipo_precio_FIJO_o_INDEXADO}
     * → «Tipo de precio». Acepta también los nombres de {@link McasCampo}. Un código
     * desconocido sale sin guiones bajos («dato_nuevo» → «Dato nuevo»); null → «».
     */
    public static String campo(String codigo) {
        if (codigo == null || codigo.isBlank()) return "";
        String etiqueta = ETIQUETAS.get(codigo.trim().toLowerCase(Locale.ROOT));
        if (etiqueta != null) return etiqueta;
        return McasCampo.desdeCodigo(codigo).map(McasCampo::getEtiqueta).orElseGet(() -> legible(codigo));
    }

    /** Etiquetas de varios códigos, sin repetir y en el orden recibido (consumo en kWh y en MWh son un solo dato). */
    public static List<String> listaCampos(Collection<String> codigos) {
        if (codigos == null) return List.of();
        Set<String> etiquetas = new LinkedHashSet<>();
        for (String codigo : codigos) {
            String etiqueta = campo(codigo);
            if (!etiqueta.isEmpty()) etiquetas.add(etiqueta);
        }
        return List.copyOf(etiquetas);
    }

    /** «Falta un dato para calcular la comisión: FEE del contrato.» o «Faltan datos …: a, b y c.» */
    public static String faltanDatos(Collection<String> codigos) {
        List<String> etiquetas = listaCampos(codigos).stream().map(McasMensajes::minuscula).toList();
        if (etiquetas.isEmpty()) return "Faltan datos para calcular la comisión.";
        if (etiquetas.size() == 1) return "Falta un dato para calcular la comisión: " + etiquetas.get(0) + ".";
        return "Faltan datos para calcular la comisión: " + enumerar(etiquetas) + ".";
    }

    /**
     * Traduce una causa técnica del motor o del cálculo a una frase para el
     * usuario. Es idempotente (traducir un texto ya traducido lo deja igual) y
     * devuelve sin cambios lo que no reconoce, incluido null.
     */
    public static String paraOperador(String causaTecnica) {
        if (causaTecnica == null || causaTecnica.isBlank()) return causaTecnica;
        String causa = causaTecnica.trim();
        for (Traduccion traduccion : TRADUCCIONES) {
            Matcher m = traduccion.patron().matcher(causa);
            if (m.matches()) return traduccion.texto().apply(m);
        }
        return causaTecnica;
    }

    /** Estado de una propuesta como lo enseña la pantalla («PENDIENTE_VALIDACION» ⇒ «Pendiente de validación»). */
    public static String estadoPropuesta(String estado) {
        if (estado == null || estado.isBlank()) return "";
        String etiqueta = ESTADOS.get(estado.trim().toUpperCase(Locale.ROOT));
        return etiqueta != null ? etiqueta : legible(estado.trim().toLowerCase(Locale.ROOT));
    }

    /** Importe en formato español: «1.234,50 €». */
    public static String importe(BigDecimal importe) {
        if (importe == null) return "";
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-ES")));
        formato.setGroupingSize(3);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(importe) + " €";
    }

    /** «571,26 € en 1 pago», «771,58 € en 12 pagos» (sin pagos indicados, uno). */
    public static String importeEnPagos(BigDecimal importe, Integer pagos) {
        int n = pagos != null && pagos > 0 ? pagos : 1;
        return importe(importe) + " en " + n + (n == 1 ? " pago" : " pagos");
    }

    /**
     * Códigos técnicos de los datos que faltan según una causa técnica del motor o
     * del cálculo («Faltan o son invalidos: a, b», «…: dato requerido 'x'»…). Sirve
     * para las propuestas antiguas, que no guardaban la lista aparte; vacío si la
     * causa no habla de datos que faltan.
     */
    public static List<String> camposDe(String causaTecnica) {
        if (causaTecnica == null || causaTecnica.isBlank()) return List.of();
        String causa = causaTecnica.trim();
        for (Pattern patron : List.of(LISTA_INVALIDOS, LISTA_REGLAS)) {
            Matcher m = patron.matcher(causa);
            if (m.matches()) return separar(m.group(1));
        }
        Matcher dato = DATO_REQUERIDO.matcher(causa);
        if (dato.matches()) return entreComillas(dato.group(1));
        Matcher campo = CAMPO_REQUERIDO.matcher(causa);
        if (campo.matches()) return List.of(campo.group(1));
        return List.of();
    }

    /** Textos entre comillas simples: «'a', 'b'» → [a, b]. */
    private static List<String> entreComillas(String lista) {
        List<String> valores = new java.util.ArrayList<>();
        Matcher m = ENTRE_COMILLAS.matcher(lista);
        while (m.find()) valores.add(m.group(1));
        return valores;
    }

    // -----------------------------------------------
    // Auxiliares
    // -----------------------------------------------

    private record Traduccion(Pattern patron, Function<Matcher, String> texto) {
        Traduccion(String regex, Function<Matcher, String> texto) {
            this(Pattern.compile(regex, Pattern.DOTALL), texto);
        }
    }

    private static List<String> separar(String lista) {
        return Arrays.stream(lista.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** «a», «a y b», «a, b y c». */
    private static String enumerar(List<String> partes) {
        if (partes.size() <= 1) return String.join("", partes);
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
    }

    /** Primera letra en minúscula para enumerar dentro de una frase, salvo siglas (FEE, CUPS). */
    private static String minuscula(String etiqueta) {
        if (etiqueta.length() < 2 || !Character.isUpperCase(etiqueta.charAt(0))
                || Character.isUpperCase(etiqueta.charAt(1))) return etiqueta;
        return Character.toLowerCase(etiqueta.charAt(0)) + etiqueta.substring(1);
    }

    private static String legible(String codigo) {
        String texto = codigo.trim().replace('_', ' ').replaceAll("\\s+", " ");
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static String fechaEntreParentesis(String iso) {
        try {
            return " (" + LocalDate.parse(iso).format(FECHA) + ")";
        } catch (DateTimeParseException e) {
            return "";
        }
    }
}
