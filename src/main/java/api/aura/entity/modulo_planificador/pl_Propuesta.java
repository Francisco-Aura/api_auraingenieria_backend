package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Propuesta al cliente de un presupuesto validado (con versiones: si la rechaza se crea otra). */
@Entity
@Table(name = "pl_propuestas")
@Getter
@Setter
@NoArgsConstructor
public class pl_Propuesta {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "presupuesto_id", nullable = false, length = 64)
    private String presupuestoId;

    @Column(nullable = false)
    private int version = 1;

    /** borrador, enviada, aprobada o rechazada. */
    @Column(nullable = false, length = 20)
    private String estado = "borrador";

    /** Motivo del rechazo, condiciones de la aprobación… */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String nota = "";

    @Column(name = "creado_el", nullable = false)
    private LocalDateTime creadoEl;

    @Column(name = "creado_por", nullable = false)
    private String creadoPor = "";

    @Column(name = "enviado_el")
    private LocalDateTime enviadoEl;

    @Column(name = "enviado_por", nullable = false)
    private String enviadoPor = "";

    /** Cuándo contestó el cliente (aprobada o rechazada). */
    @Column(name = "decidido_el")
    private LocalDateTime decididoEl;

    @Column(name = "decidido_por", nullable = false)
    private String decididoPor = "";

    @Column(name = "logo_cliente", columnDefinition = "LONGTEXT", nullable = false)
    private String logoCliente = "";
}
