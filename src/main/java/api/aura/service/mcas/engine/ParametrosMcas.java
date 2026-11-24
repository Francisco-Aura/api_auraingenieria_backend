package api.aura.service.mcas.engine;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Lectura de los parametros de {@code calculo_json} con mensajes que nombran el
 * dato que falta. Sin esto, un JSON incompleto acababa en un NullPointerException
 * envuelto como «error en calculo_json — null», que no dice nada a quien configura
 * las reglas.
 */
final class ParametrosMcas {

    private ParametrosMcas() {}

    static BigDecimal decimal(JsonNode calculo, String metodo, String campo) {
        JsonNode valor = calculo == null ? null : calculo.get(campo);
        if (valor == null || valor.isNull() || !valor.isNumber())
            throw new McasCalculoException(metodo + ": falta el numero '" + campo + "' en calculo_json");
        return valor.decimalValue();
    }

    /** Factor entre 0 y 1 (0,44 = 44 %), con el mismo criterio que COMPOSITE_SUM. */
    static BigDecimal factor(JsonNode calculo, String metodo, String campo) {
        BigDecimal valor = decimal(calculo, metodo, campo);
        if (valor.signum() < 0 || valor.compareTo(BigDecimal.ONE) > 0)
            throw new McasCalculoException(metodo + ": '" + campo + "' debe estar entre 0 y 1");
        return valor;
    }
}
