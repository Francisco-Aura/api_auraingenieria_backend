package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

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

    @Temporal(TemporalType.DATE)
    private Date primerPagoFecha;

    private Double primerPagoImporte;

    private Boolean primerPagoPagado;

    @Temporal(TemporalType.DATE)
    private Date segundoPagoFecha;

    private Double segundoPagoImporte;

    private Boolean segundoPagoPagado;

    // Se agregan los siguientes campos siguiendo la misma estructura
    @Temporal(TemporalType.DATE)
    private Date tercerPagoFecha;

    private Double tercerPagoImporte;

    private Boolean tercerPagoPagado;

    private Double importe;
    private Double descuento;
    private Double importeFinal;
}
