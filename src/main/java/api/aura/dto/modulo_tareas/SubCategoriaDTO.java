package api.aura.dto.modulo_tareas;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubCategoriaDTO {
    private Long id;
    private String nombre;
    private String codigo;
}
