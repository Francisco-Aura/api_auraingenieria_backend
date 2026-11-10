package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Registro de actividad de un proyecto. Sin clave foránea a la tarea: el registro sobrevive a la tarea eliminada. */
@Entity
@Table(name = "pl_actividad")
@Getter
@Setter
@NoArgsConstructor
public class pl_Actividad {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "proyecto_id", nullable = false, length = 64)
    private String proyectoId;

    @Column(name = "tarea_id", length = 64)
    private String tareaId;

    @Column(name = "nombre_tarea", nullable = false)
    private String nombreTarea = "";

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false)
    private String actor = "";

    /** project, structure, status, schedule, checklist, docs, contract, material… */
    @Column(nullable = false, length = 30)
    private String tipo = "";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String resumen;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String detalle = "";
}
