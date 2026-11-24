package api.aura.service.mcas.engine;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Números de las descripciones del desglose en formato español (58,5 MWh; 44 %). */
final class FormatoMcas {

    private FormatoMcas() {}

    static String numero(BigDecimal valor) {
        if (valor == null) return "—";
        DecimalFormat formato = new DecimalFormat("#,##0.######", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-ES")));
        formato.setGroupingSize(3);
        return formato.format(valor);
    }

    /** Factor del motor (0.44) como porcentaje legible (44 %). */
    static String porcentaje(BigDecimal factor) {
        return factor == null ? "—" : numero(factor.movePointRight(2)) + " %";
    }
}
