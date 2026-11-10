package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pl_marcas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class pl_Marca {

    @Id
    @Column(length = 60)
    private String nombre;

    /** Imagen en data URI (PNG, JPG, WebP o SVG); vacío si no se ha subido. */
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String logo = "";
}
