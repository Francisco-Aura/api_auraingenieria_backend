package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pl_proveedores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class pl_Proveedor {

    @Id
    @Column(length = 80)
    private String nombre;
}
