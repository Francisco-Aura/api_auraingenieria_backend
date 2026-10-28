package api.aura.dto.mcas;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Resultado de probar un conjunto de reglas con un contrato real sin generar
 * propuesta (Configuración MCAS › Probar con un contrato).
 */
@Value
@Builder
public class McasSimulacionDTO {
    Integer idContrato;
    String numeroContrato;
    Integer idConjunto;
    String codigoConjunto;
    /** Nombre legible de la tabla (su código si no tiene nombre). */
    String nombreConjunto;
    String estadoConjunto;
    /** CALCULADO, REQUIERE_DATOS, REQUIERE_REVISION_MANUAL, NO_COMISIONABLE o ERROR_CALCULO. */
    String resultado;
    BigDecimal importe;
    Integer numeroPagos;
    /** Código de la regla aplicada (se mantiene por compatibilidad). */
    String reglaAplicada;
    /** Nombre legible de la regla aplicada (su código si no tiene nombre). */
    String nombreRegla;
    String metodoCalculo;
    /** Explicación del motor en texto para el usuario: datos que faltan, reglas solapadas, motivo manual... */
    List<String> mensajes;
    /** Diferencias entre el contrato y la tabla que impedirían usarla en el cálculo real. */
    List<String> avisos;
    List<Linea> lineas;
    List<Cuota> calendario;
    /** Datos del contrato tal como los ve el motor. */
    Map<String, Object> datosContrato;

    public record Linea(String descripcion, String metodo, BigDecimal importe) {}

    public record Cuota(int numero, LocalDate fecha, BigDecimal importe) {}
}
