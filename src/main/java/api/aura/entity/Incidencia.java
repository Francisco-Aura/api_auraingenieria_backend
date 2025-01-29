package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "Incidencias")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 50)
    private String tipo;

    @Column(length = 250)
    private String descripcion;

    @ManyToOne
    @JoinColumn(name = "idCliente")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "idPuntoSuministro")
    private PuntoSuministro puntoSuministro;

    @Temporal(TemporalType.DATE)
    private Date fecha;

    @Temporal(TemporalType.DATE)
    private Date fechaResolucion;

    @Column(length = 50)
    private String referenciada;
}
