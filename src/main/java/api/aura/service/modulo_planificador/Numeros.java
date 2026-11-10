package api.aura.service.modulo_planificador;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

/** Números del contrato y los pagos: lectura (también en formato español), redondeo y texto de importes. */
public final class Numeros {

    private static final Pattern DECIMAL = Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$");

    private Numeros() {
    }

    /** Acepta 1234.5, 1234,5 y el formato español 1.234,50. Lanza NumberFormatException si no es un número. */
    public static double aNumero(Object valor) {
        if (valor instanceof Number n) {
            return n.doubleValue();
        }
        String texto = String.valueOf(valor).strip().replace(" ", "");
        if (texto.contains(",")) {
            texto = texto.replace(".", "").replace(",", ".");
        }
        if (!DECIMAL.matcher(texto).matches()) {
            throw new NumberFormatException(texto);
        }
        return Double.parseDouble(texto);
    }

    /** Redondea a `decimales` como round() de Python (mitades al par). */
    public static double redondear(double valor, int decimales) {
        if (Double.isNaN(valor) || Double.isInfinite(valor)) {
            return valor;
        }
        return BigDecimal.valueOf(valor).setScale(decimales, RoundingMode.HALF_EVEN).doubleValue();
    }

    public static double redondear2(double valor) {
        return redondear(valor, 2);
    }

    public static boolean finito(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /** 1234.5 → «1.234,50 €» (negativos con el signo delante). */
    public static String euros(double valor) {
        BigDecimal abs = BigDecimal.valueOf(Math.abs(valor)).setScale(2, RoundingMode.HALF_EVEN);
        String formateado = String.format(Locale.US, "%,.2f", abs);
        int punto = formateado.lastIndexOf('.');
        String entero = formateado.substring(0, punto).replace(",", ".");
        return (valor < 0 ? "-" : "") + entero + "," + formateado.substring(punto + 1) + " €";
    }

    /** Equivale a f"{valor:g}" de Python: 6 cifras significativas y sin ceros de más (12.0 → «12»). */
    public static String g(double valor) {
        if (valor == 0) {
            return "0";
        }
        BigDecimal redondeado = new BigDecimal(valor).round(new MathContext(6, RoundingMode.HALF_EVEN));
        int exponente = redondeado.precision() - redondeado.scale() - 1;
        if (exponente < -4 || exponente >= 6) {
            String mantisa = redondeado.unscaledValue().abs().toString();
            mantisa = mantisa.replaceAll("0+$", "");
            String cifras = mantisa.length() > 1 ? mantisa.charAt(0) + "." + mantisa.substring(1) : mantisa;
            return (valor < 0 ? "-" : "") + cifras + "e" + (exponente < 0 ? "-" : "+") + String.format("%02d", Math.abs(exponente));
        }
        String texto = redondeado.stripTrailingZeros().toPlainString();
        return texto;
    }
}
