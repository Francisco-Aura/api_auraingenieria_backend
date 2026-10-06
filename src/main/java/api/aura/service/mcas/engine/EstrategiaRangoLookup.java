package api.aura.service.mcas.engine;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.repository.mcas.McasValorRangoRepository;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** RANGE_LOOKUP: busca por una dimensión numérica en mcas_valores_rango */
@Component
public class EstrategiaRangoLookup implements EstrategiaCalculo {

    private static final List<String> DIMENSIONES_NUMERICAS = List.of(
            "consumo_kwh", "consumo_mwh", "potencia_kw");
    private static final List<String> DIMENSIONES_TEXTO = List.of(
            "tarifa_acceso", "codigo_producto", "segmento", "tipo_suministro", "zona_comision");

    @Override public String getNombre() { return "RANGE_LOOKUP"; }

    @Override
    public List<ResultadoCalculo.LineaResultado> calcular(ContextoCalculo ctx, McasReglaComision regla,
                                                          McasValorRangoRepository rangoRepo) {
        List<McasValorRango> rangos = rangoRepo.findByIdReglaOrderByOrdenAsc(regla.getId());
        if (rangos.isEmpty())
            throw new McasCalculoException("RANGE_LOOKUP: sin valores en mcas_valores_rango para regla " + regla.getId());

        String dim = rangos.get(0).getDimension();
        validarDimension(dim);
        // Todas las filas de la regla tienen que mirar la misma dimension: si no, las
        // que no coinciden con la primera se descartarian en silencio y la comision
        // saldria calculada solo con parte de la tabla.
        if (rangos.stream().anyMatch(r -> !dim.equals(r.getDimension())))
            throw new McasCalculoException("RANGE_LOOKUP: la regla " + regla.getId()
                    + " mezcla dimensiones ('" + dim + "' y otras); revisar configuracion");
        BigDecimal input = resolverInputNumerico(ctx, dim);
        String inputTexto = resolverInputTexto(ctx, dim);
        if (input == null && inputTexto == null)
            throw new McasDatosRequeridosException("RANGE_LOOKUP: dato requerido '" + dim + "'", List.of(dim));

        List<McasValorRango> coincidentes = rangos.stream()
                .filter(r -> coincideValor(r, input, inputTexto))
                .collect(Collectors.toList());
        if (coincidentes.size() > 1)
            throw new McasCalculoException("RANGE_LOOKUP: " + coincidentes.size()
                    + " rangos coinciden para '" + dim + "'=" + input + "; configuracion ambigua");
        if (coincidentes.size() == 1) {
            McasValorRango r = coincidentes.get(0);
            return List.of(ResultadoCalculo.LineaResultado.builder()
                    .tipoLinea("COMISION_BASE").descripcion("Tabla de rangos: " + dim + " = " + (input != null ? FormatoMcas.numero(input) : inputTexto))
                    .metodo("RANGE_LOOKUP").valorBase(input)
                    .importe(r.getValorResultado()).idRegla(regla.getId()).build());
        }
        // Un valor que la tabla no contempla (p. ej. «Consultar» en el Excel) no es un
        // error de configuración: la comisión se completa a mano (v2 §14).
        throw new McasRevisionManualException(describirValor(dim, input, inputTexto)
                + " fuera de la tabla de la regla " + nombreRegla(regla) + ": complete la comisión manualmente");
    }

    /** Código de la regla para los mensajes (o su id si aún no tiene código). */
    static String nombreRegla(McasReglaComision regla) {
        return regla.getCodigo() != null ? regla.getCodigo() : "#" + regla.getId();
    }

    /** Valor del contrato en una dimensión de tabla, en palabras: «Consumo anual 250.000 kWh». */
    static String describirValor(String dim, BigDecimal numero, String texto) {
        return switch (dim) {
            case "consumo_kwh" -> "Consumo anual " + FormatoMcas.numero(numero) + " kWh";
            case "consumo_mwh" -> "Consumo anual " + FormatoMcas.numero(numero) + " MWh";
            case "potencia_kw" -> "Potencia " + FormatoMcas.numero(numero) + " kW";
            case "tarifa_acceso" -> "Tarifa de acceso " + texto;
            case "codigo_producto" -> "Producto " + texto;
            case "segmento" -> "Segmento " + texto;
            case "tipo_suministro" -> "Suministro " + texto;
            case "zona_comision" -> "Zona " + texto;
            default -> dim + " " + (numero != null ? FormatoMcas.numero(numero) : texto);
        };
    }

    public static void validarDimension(String dim) {
        if (!DIMENSIONES_NUMERICAS.contains(dim) && !DIMENSIONES_TEXTO.contains(dim))
            throw new McasCalculoException("dimension de lookup no soportada: '" + dim + "'");
    }

    public static boolean esDimensionTexto(String dim) { return DIMENSIONES_TEXTO.contains(dim); }

    public static BigDecimal resolverInputNumerico(ContextoCalculo ctx, String dim) {
        return switch (dim) {
            case "consumo_kwh" -> ctx.getConsumoAnualKwh();
            case "consumo_mwh" -> ctx.getConsumoAnualMwh();
            case "potencia_kw" -> ctx.getPotenciaCalculoKw();
            default -> null;
        };
    }

    public static String resolverInputTexto(ContextoCalculo ctx, String dim) {
        return switch (dim) {
            case "tarifa_acceso" -> ctx.getCodigoTarifaAcceso();
            case "codigo_producto" -> ctx.getCodigoProductoComercial();
            case "segmento" -> ctx.getSegmento();
            case "tipo_suministro" -> ctx.getTipoSuministro();
            case "zona_comision" -> ctx.getZonaComision();
            default -> null;
        };
    }

    public static boolean coincideValor(McasValorRango r, BigDecimal numero, String texto) {
        if (esDimensionTexto(r.getDimension())) {
            if (texto == null || r.getValorCategoria() == null) return false;
            // La tarifa se compara normalizada («2.0 TD» = «2.0TD»); el resto, sin mayúsculas.
            return "tarifa_acceso".equals(r.getDimension())
                    ? McasTexto.mismaTarifa(r.getValorCategoria(), texto)
                    : r.getValorCategoria().equalsIgnoreCase(texto);
        }
        return numero != null && coincideRango(r, numero);
    }

    public static boolean coincideRango(McasValorRango r, BigDecimal valor) {
        if (r.getValorMin() != null) {
            int cmp = valor.compareTo(r.getValorMin());
            if (Boolean.TRUE.equals(r.getMinInclusivo()) ? cmp < 0 : cmp <= 0) return false;
        }
        if (r.getValorMax() != null) {
            int cmp = valor.compareTo(r.getValorMax());
            if (Boolean.TRUE.equals(r.getMaxInclusivo()) ? cmp > 0 : cmp >= 0) return false;
        }
        return true;
    }
}
