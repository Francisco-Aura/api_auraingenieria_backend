package api.aura.dto.modulo_tareas;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoTareaDTO {
    private Long id;
    private String nombre;
    private String colorHex;
    private Boolean estadoFinal;
    private Boolean cuentaTiempo;
    private Boolean estadoBloqueo;
}
