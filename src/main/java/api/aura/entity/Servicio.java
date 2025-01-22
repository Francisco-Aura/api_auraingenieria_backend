package api.aura.entity;

import java.sql.Date;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Servicios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Temporal(TemporalType.DATE)
    private Date fecha;

    @ManyToOne
    @JoinColumn(name = "idCliente")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "idPuntoSuministro")
    private PuntoSuministro puntoSuministro;

    @Column(length = 250)
    private String descripcion;

    @Temporal(TemporalType.DATE)
    private Date fechaInicioServicio;

    @Temporal(TemporalType.DATE)
    private Date fechaFinServicio;

    @Temporal(TemporalType.DATE)
    private Date fechaPagoComercial;

    @Temporal(TemporalType.DATE)
    private Date fechaBaja;

}
