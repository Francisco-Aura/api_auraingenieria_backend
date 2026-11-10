package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;

/** Valores sueltos del módulo (sustituye a app_meta del Planificador actual). */
@Entity
@Table(name = "pl_meta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class pl_Meta {

    @Id
    @Column(name = "clave", length = 100)
    private String clave;

    @Column(name = "valor", length = 500)
    private String valor;
}
