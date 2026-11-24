package api.aura.dto.mcas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Problema de una tabla de comisiones que impide activarla (comprobación y
 * activación en Configuración MCAS). {@code idRegla} y {@code orden} llevan a la
 * regla y a la celda; pueden ser null si la incidencia es del conjunto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McasIncidenciaDTO {
    private Integer idRegla;
    /** Nombre legible de la regla (o su código si no tiene nombre). */
    private String regla;
    private Integer orden;
    private String mensaje;
}
