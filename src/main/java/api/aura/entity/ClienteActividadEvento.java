package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "clientes_actividad")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteActividadEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Integer clienteId;

    @Column(name = "punto_suministro_id")
    private Integer puntoSuministroId;

    @Column(name = "contrato_id")
    private Integer contratoId;

    @Column(name = "servicio_id")
    private Integer servicioId;

    @Column(name = "tipo_evento", nullable = false, length = 80)
    private String tipoEvento;

    @Column(nullable = false, length = 2000)
    private String descripcion;

    @Column(nullable = false, length = 40)
    private String origen;

    @Column(length = 100)
    private String cups;

    @Builder.Default
    @Column(name = "solo_nivel_uno", nullable = false)
    private Boolean soloNivelUno = false;

    @Column(name = "fecha_evento", nullable = false, updatable = false)
    private LocalDateTime fechaEvento;

    @PrePersist
    public void onCreate() {
        if (this.fechaEvento == null) {
            this.fechaEvento = LocalDateTime.now();
        }
        if (this.soloNivelUno == null) {
            this.soloNivelUno = false;
        }
    }
}
