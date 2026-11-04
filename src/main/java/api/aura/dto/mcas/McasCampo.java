package api.aura.dto.mcas;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Datos MCAS del contrato que pueden pedir las reglas (clave de
 * {@code McasVistaPreviaDTO.Campo}). El frontend los mapea a los campos del
 * formulario de contratos (mcasCamposContrato.js).
 */
public enum McasCampo {
    TIPO_PRECIO("Tipo de precio", "tipo_precio_FIJO_o_INDEXADO"),
    TIPO_SUMINISTRO("Tipo de suministro", "tipo_suministro_LUZ_o_GAS"),
    SEGMENTO("Segmento"),
    ES_RENOVACION("Renovación"),
    TIPO_RENOVACION("Tipo de renovación"),
    ALTA_NUEVA("Alta nueva"),
    AMBITO("Ámbito"),
    ZONA_COMISION("Zona de comisión"),
    TARIFA_ACCESO("Tarifa de acceso"),
    PRODUCTO("Tarifa de precios (producto)", "codigo_producto", "producto_comercial_activo_y_compatible"),
    CONSUMO("Consumo anual", "consumo_kwh", "consumo_mwh", "consumo_anual", "consumo_anual_no_negativo"),
    // Siempre la mayor contratada: el usuario la cambia en P1…P6 (pestaña Contrato)
    POTENCIA("Potencias contratadas (P1 a P6)", "potencia_kw", "potencia_no_negativa"),
    FEE("FEE del contrato", "fee_eur_mwh", "FEE del contrato", "fee_no_negativo", "unidad_fee", "unidad_fee_EUR_MWH"),
    GESTORA("Gestora", "id_gestora");

    private static final Map<String, McasCampo> POR_CODIGO = new HashMap<>();

    static {
        for (McasCampo campo : values()) {
            POR_CODIGO.put(clave(campo.name()), campo);
            for (String codigo : campo.codigos) POR_CODIGO.put(clave(codigo), campo);
        }
    }

    private final String etiqueta;
    private final String[] codigos;

    McasCampo(String etiqueta, String... codigos) {
        this.etiqueta = etiqueta;
        this.codigos = codigos;
    }

    /** Nombre del dato para el usuario («FEE del contrato»). */
    public String getEtiqueta() { return etiqueta; }

    /**
     * Campo al que se refiere un código técnico del motor o del cálculo
     * ({@code consumo_kwh}, {@code codigo_producto}, {@code tipo_precio_FIJO_o_INDEXADO},
     * {@code fee_no_negativo}...) o el propio nombre del enum, sin distinguir
     * mayúsculas. Vacío si no es un dato de la pestaña MCAS (número de contrato,
     * fecha de inicio, comercializadora...).
     */
    public static Optional<McasCampo> desdeCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return Optional.empty();
        return Optional.ofNullable(POR_CODIGO.get(clave(codigo)));
    }

    private static String clave(String codigo) {
        return codigo.trim().toLowerCase(Locale.ROOT);
    }
}
