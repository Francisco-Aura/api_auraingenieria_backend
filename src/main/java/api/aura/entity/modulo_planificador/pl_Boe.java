package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Un conjunto de peajes y cargos de la electricidad publicado en el BOE («a BOE»), con el nombre que se le da y la fecha desde la que vale.
 * Puede haber varios en el mismo año, o uno cada dos. Sus precios están en {@link pl_PrecioRegulado}.
 */
@Entity
@Table(name = "pl_boes")
@Getter
@Setter
@NoArgsConstructor
public class pl_Boe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String nombre = "";

    /** Vale desde este día hasta que empiece el siguiente. */
    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;
}
