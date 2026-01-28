package api.aura.entity.modulo_tareas;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "modulotareas_estados")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class mt_TareaEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(length = 7)
    private String colorHex;

    @Column(nullable = false)
    private Boolean estadoFinal;

    @Column(nullable = false)
    private Integer orden;

    @Column(nullable = false)
    private Boolean cuentaTiempo;

    @Column(nullable = false)
    private Boolean esBloqueo;
}
