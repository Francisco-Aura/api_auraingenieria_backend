package api.aura.dto.mcas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Comisión estimada de un contrato sin guardarlo ({@code POST /api/mcas/vista-previa}).
 * Nunca crea propuestas ni auditoría; sin traza ni códigos internos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McasVistaPreviaDTO {

    public enum Estado {
        CALCULADO, REQUIERE_DATOS, REQUIERE_REVISION_MANUAL, NO_COMISIONABLE, ERROR_CALCULO,
        SIN_REGLAS, DESACTIVADO, YA_TIENE_COMISION, SIN_TIPO_PRECIO
    }

    private Estado estado;
    /** Frase para el usuario («Comisión estimada: 571,26 € en 1 pago.»). */
    private String mensaje;
    private BigDecimal importe;
    private Integer numeroPagos;
    @Builder.Default
    private List<Cuota> calendario = List.of();
    /** Nombre legible de la regla aplicada (o su código si no tiene nombre). */
    private String regla;
    @Builder.Default
    private List<String> avisos = List.of();
    /** Datos que piden las reglas de la comercializadora, con los que faltan marcados. */
    @Builder.Default
    private List<Campo> campos = List.of();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cuota {
        private Integer numero;
        private LocalDate fecha;
        private BigDecimal importe;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Campo {
        private McasCampo clave;
        private String etiqueta;
        private boolean falta;
        /** Valores posibles según las reglas (opciones del desplegable); vacío si es libre. */
        @Builder.Default
        private List<String> valores = List.of();
        /** Nombres legibles de las reglas que lo usan. */
        @Builder.Default
        private List<String> usadoPor = List.of();
    }
}
