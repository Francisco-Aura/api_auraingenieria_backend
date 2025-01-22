package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "Contratos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Temporal(TemporalType.DATE)
    private Date fecha;

    @Temporal(TemporalType.DATE)
    private Date fechaEnvio;

    @Temporal(TemporalType.DATE)
    private Date fechaInicio;

    @Temporal(TemporalType.DATE)
    private Date fechaFin;

    @ManyToOne
    @JoinColumn(name = "idCliente")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "idPuntoSuministro")
    private PuntoSuministro puntoSuministro;

    @Column(length = 100)
    private String contrato;

    private Boolean autoconsumo;

    private Boolean denunciaContrato;

    @Temporal(TemporalType.DATE)
    private Date fechaDenunciaContrato;

    @Column(length = 200)
    private String observaciones;
}
