package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Material sobrante de un proyecto que vuelve al almacén (suma al stock del producto). */
@Entity
@Table(name = "pl_devoluciones_material")
@Getter
@Setter
@NoArgsConstructor
public class pl_DevolucionMaterial {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "proyecto_id", nullable = false, length = 64)
    private String proyectoId;

    @Column(name = "equipo_id", nullable = false, length = 64)
    private String equipoId;

    @Column(nullable = false)
    private String etiqueta = "";

    @Column(nullable = false, length = 40)
    private String unidad = "ud";

    @Column(nullable = false)
    private double cantidad;

    @Column(nullable = false)
    private String nota = "";

    @Column(name = "por", nullable = false)
    private String por = "";

    @Column(nullable = false)
    private LocalDateTime fecha;
}
