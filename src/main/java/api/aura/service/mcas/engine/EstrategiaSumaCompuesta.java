package api.aura.service.mcas.engine;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasValorRangoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** COMPOSITE_SUM: suma lineas declarativas sin ejecutar expresiones arbitrarias. */
@Component
public class EstrategiaSumaCompuesta implements EstrategiaCalculo {
    private final ObjectMapper mapper;

    public EstrategiaSumaCompuesta(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String getNombre() { return "COMPOSITE_SUM"; }

    @Override
    public List<ResultadoCalculo.LineaResultado> calcular(ContextoCalculo ctx, McasReglaComision regla,
                                                          McasValorRangoRepository rangoRepo) {
        try {
            JsonNode lineasJson = mapper.readTree(regla.getCalculoJson()).get("lineas");
            if (lineasJson == null || !lineasJson.isArray() || lineasJson.isEmpty())
                throw new McasCalculoException("COMPOSITE_SUM: calculo_json.lineas es obligatorio");
            List<ResultadoCalculo.LineaResultado> lineas = new ArrayList<>();
            BigDecimal acumulado = BigDecimal.ZERO;
            for (JsonNode linea : lineasJson) {
                String metodo = texto(linea, "metodo");
                BigDecimal importe;
                BigDecimal base = null;
                BigDecimal tasa = null;
                BigDecimal porcentaje = null;
                switch (metodo) {
                    case "FIXED_AMOUNT" -> importe = decimal(linea, "importe");
                    case "RATE_PER_MWH" -> {
                        exigir(ctx.getConsumoAnualMwh(), "consumo_anual");
                        base = ctx.getConsumoAnualMwh();
                        tasa = decimal(linea, "tasa_eur_mwh");
                        importe = base.multiply(tasa, MathContext.DECIMAL128);
                    }
                    case "FEE_PERCENTAGE" -> {
                        exigir(ctx.getConsumoAnualMwh(), "consumo_anual");
                        exigir(ctx.getFeeEurMwh(), "FEE");
                        base = ctx.getConsumoAnualMwh();
                        tasa = ctx.getFeeEurMwh();
                        porcentaje = porcentaje(linea, "porcentaje_aplicacion_fee");
                        importe = base.multiply(tasa, MathContext.DECIMAL128)
                                .multiply(porcentaje, MathContext.DECIMAL128);
                    }
                    case "PERCENTAGE_OF_PREVIOUS" -> {
                        porcentaje = porcentaje(linea, "porcentaje");
                        base = acumulado;
                        importe = acumulado.multiply(porcentaje, MathContext.DECIMAL128);
                    }
                    default -> throw new McasCalculoException(
                            "COMPOSITE_SUM: metodo de linea no soportado: " + metodo);
                }
                lineas.add(ResultadoCalculo.LineaResultado.builder()
                        .tipoLinea("COMPONENTE").descripcion(linea.path("descripcion").asText(metodo))
                        .metodo(metodo).valorBase(base).valorTasa(tasa).valorPorcentaje(porcentaje)
                        .importe(importe).idRegla(regla.getId()).build());
                acumulado = acumulado.add(importe);
            }
            return lineas;
        } catch (McasDatosRequeridosException e) {
            throw e;
        } catch (McasCalculoException e) {
            throw e;
        } catch (Exception e) {
            throw new McasCalculoException("COMPOSITE_SUM: calculo_json invalido: " + e.getMessage());
        }
    }

    private String texto(JsonNode node, String campo) {
        if (!node.hasNonNull(campo) || node.get(campo).asText().isBlank())
            throw new McasCalculoException("COMPOSITE_SUM: falta " + campo);
        return node.get(campo).asText();
    }

    private BigDecimal decimal(JsonNode node, String campo) {
        if (!node.hasNonNull(campo) || !node.get(campo).isNumber())
            throw new McasCalculoException("COMPOSITE_SUM: falta numero " + campo);
        return node.get(campo).decimalValue();
    }

    private BigDecimal porcentaje(JsonNode node, String campo) {
        BigDecimal valor = decimal(node, campo);
        if (valor.signum() < 0 || valor.compareTo(BigDecimal.ONE) > 0)
            throw new McasCalculoException("COMPOSITE_SUM: " + campo + " debe estar entre 0 y 1");
        return valor;
    }

    private void exigir(Object valor, String campo) {
        if (valor == null)
            throw new McasDatosRequeridosException("COMPOSITE_SUM: " + campo + " requerido", List.of(campo));
    }
}
