package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

import api.aura.entity.tablas_maestras.TBMetodoPago;
import api.aura.entity.tablas_maestras.TBPagoPlazos;

@Entity
@Table(name = "ServiciosPagos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiciosPagos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "IdServicio")
    private Servicio servicio;

    @ManyToOne
    @JoinColumn(name = "IdMetodoPago")
    private TBMetodoPago metodoPago;

    @ManyToOne
    @JoinColumn(name = "IdPagoPlazos")
    private TBPagoPlazos pagoPlazos;

    @Temporal(TemporalType.DATE)
    private Date primerPagoFecha;
    private Double primerPagoImporte;
    private Boolean primerPagoPagado;

    @Temporal(TemporalType.DATE)
    private Date segundoPagoFecha;
    private Double segundoPagoImporte;
    private Boolean segundoPagoPagado;

    @Temporal(TemporalType.DATE)
    private Date tercerPagoFecha;
    private Double tercerPagoImporte;
    private Boolean tercerPagoPagado;

    @Temporal(TemporalType.DATE)
    private Date cuartoPagoFecha;
    private Double cuartoPagoImporte;
    private Boolean cuartoPagoPagado;

    @Temporal(TemporalType.DATE)
    private Date quintoPagoFecha;
    private Double quintoPagoImporte;
    private Boolean quintoPagoPagado;
    @Temporal(TemporalType.DATE)
    private Date sextoPagoFecha;
    private Double sextoPagoImporte;
    private Boolean sextoPagoPagado;

    @Temporal(TemporalType.DATE)
    private Date septimoPagoFecha;
    private Double septimoPagoImporte;
    private Boolean septimoPagoPagado;

    @Temporal(TemporalType.DATE)
    private Date octavoPagoFecha;
    private Double octavoPagoImporte;
    private Boolean octavoPagoPagado;

    @Temporal(TemporalType.DATE)
    private Date novenoPagoFecha;
    private Double novenoPagoImporte;
    private Boolean novenoPagoPagado;

    @Temporal(TemporalType.DATE)
    private Date decimoPagoFecha;
    private Double decimoPagoImporte;
    private Boolean decimoPagoPagado;

    @Temporal(TemporalType.DATE)
    private Date decimoprimerPagoFecha;
    private Double decimoprimerPagoImporte;
    private Boolean decimoprimerPagoPagado;

    @Temporal(TemporalType.DATE)
    private Date decimosegundoPagoFecha;
    private Double decimosegundoPagoImporte;
    private Boolean decimosegundoPagoPagado;

    private Double importe;
    private Double descuento;
    private Double importeFinal;
}
