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
    private Date fechaPago;

    @Column
    private Integer idGestora;

    @Column
    private Integer idProducto;

    @Column
    private Integer idComercializadora;

    @Column(length = 500)
    private String observaciones;

    @Column(length = 100)
    private String cups;

    @Temporal(TemporalType.DATE)
    private Date fechaBaja;
}