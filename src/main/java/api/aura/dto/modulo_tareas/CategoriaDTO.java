package api.aura.dto.modulo_tareas;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaDTO {
    private Long id;
    private String nombre;
    private String codigo;
    private String descripcion;
    private List<SubCategoriaDTO> subcategorias;
}
