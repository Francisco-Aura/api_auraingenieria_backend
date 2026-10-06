package api.aura.service.mcas.engine;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.repository.mcas.McasValorRangoRepository;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MATRIX_LOOKUP: cruce de dos dimensiones (ej: consumo × potencia).
 * Cada grupo de filas con mismo "orden" representa una celda de la matriz.
 */
@Component
public class EstrategiaMatrizLookup implements EstrategiaCalculo {

    @Override public String getNombre() { return "MATRIX_LOOKUP"; }

    @Override
    public List<ResultadoCalculo.LineaResultado> calcular(ContextoCalculo ctx, McasReglaComision regla,
                                                          McasValorRangoRepository rangoRepo) {
        List<McasValorRango> todos = rangoRepo.findByIdReglaOrderByOrdenAsc(regla.getId());
        if (todos.isEmpty())
            throw new McasCalculoException("MATRIX_LOOKUP: sin valores para regla " + regla.getId());

        // Se piden a la vez todas las dimensiones que faltan, no solo la primera.
        List<String> faltan = new java.util.ArrayList<>();
        todos.stream().map(McasValorRango::getDimension).distinct().forEach(dim -> {
            EstrategiaRangoLookup.validarDimension(dim);
            if (EstrategiaRangoLookup.resolverInputNumerico(ctx, dim) == null
                    && EstrategiaRangoLookup.resolverInputTexto(ctx, dim) == null)
                faltan.add(dim);
        });
        if (!faltan.isEmpty())
            throw new McasDatosRequeridosException("MATRIX_LOOKUP: dato requerido '" + String.join("', '", faltan) + "'",
                    faltan);

        Map<Integer, List<McasValorRango>> celdas = todos.stream()
                .collect(Collectors.groupingBy(McasValorRango::getOrden));

        List<Map.Entry<Integer, List<McasValorRango>>> coincidentes = celdas.entrySet().stream()
                .filter(e -> coincideCelda(e.getValue(), ctx))
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
        if (coincidentes.size() > 1)
            throw new McasCalculoException("MATRIX_LOOKUP: " + coincidentes.size()
                    + " celdas coinciden; configuracion ambigua");
        if (coincidentes.size() == 1) {
            Map.Entry<Integer, List<McasValorRango>> e = coincidentes.get(0);
            // Se comparan por valor (compareTo): 10.0 y 10.00 son el mismo importe,
            // aunque equals los considere distintos por la escala.
            List<BigDecimal> resultados = e.getValue().stream()
                    .map(McasValorRango::getValorResultado)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toCollection(java.util.TreeSet::new))
                    .stream().collect(Collectors.toList());
            if (resultados.isEmpty())
                throw new McasCalculoException("MATRIX_LOOKUP: la celda " + e.getKey()
                        + " no tiene importe configurado");
            if (resultados.size() != 1)
                throw new McasCalculoException("MATRIX_LOOKUP: la celda " + e.getKey()
                        + " contiene resultados distintos");
            return List.of(ResultadoCalculo.LineaResultado.builder()
                    .tipoLinea("COMISION_BASE").descripcion("Matriz: celda " + e.getKey())
                    .metodo("MATRIX_LOOKUP").valorBase(ctx.getConsumoAnualKwh())
                    .importe(resultados.get(0)).idRegla(regla.getId()).build());
        }
        // Sin celda para los datos del contrato (fuera de la tabla, «N. Aplica»,
        // «Consultar»): la comisión se completa a mano (v2 §14). Solo son errores
        // de configuración las celdas ambiguas o sin importe.
        List<String> valores = todos.stream().map(McasValorRango::getDimension).distinct()
                .map(dim -> EstrategiaRangoLookup.describirValor(dim,
                        EstrategiaRangoLookup.resolverInputNumerico(ctx, dim),
                        EstrategiaRangoLookup.resolverInputTexto(ctx, dim)))
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        for (int i = 1; i < valores.size(); i++)
            valores.set(i, Character.toLowerCase(valores.get(i).charAt(0)) + valores.get(i).substring(1));
        throw new McasRevisionManualException(String.join(" y ", valores) + " fuera de la tabla de la regla "
                + EstrategiaRangoLookup.nombreRegla(regla) + ": complete la comisión manualmente");
    }

    private boolean coincideCelda(List<McasValorRango> dims, ContextoCalculo ctx) {
        for (McasValorRango r : dims) {
            BigDecimal numero = EstrategiaRangoLookup.resolverInputNumerico(ctx, r.getDimension());
            String texto = EstrategiaRangoLookup.resolverInputTexto(ctx, r.getDimension());
            if (!EstrategiaRangoLookup.coincideValor(r, numero, texto)) return false;
        }
        return true;
    }
}
