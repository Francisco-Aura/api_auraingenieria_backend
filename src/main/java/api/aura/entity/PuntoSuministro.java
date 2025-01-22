package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.List;

@Entity
@Table(name = "PuntoSuministro")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PuntoSuministro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "idCliente")
    private Cliente cliente;

    @Column(length = 250)
    private String clienteNombre;

    @Column(length = 100)
    private String direccion;

    @Column(length = 10)
    private String codigoPostal;

    @Column(length = 100)
    private String poblacion;

    @Column(length = 100)
    private String provincia;

    @Column(length = 100)
    private String CUPSluz;

    @Column(length = 100)
    private String CUPSgas;

    @Column(length = 100)
    private String userTelemedida;

    @Column(length = 100)
    private String passTelemedida;

    @Temporal(TemporalType.DATE)
    private Date fechaBaja;

    @Temporal(TemporalType.DATE)
    private Date fechaInstalacion;

    @Column(length = 100)
    private String modem;

    private Integer propiedadModem;

    @Column(length = 100)
    private String contador;

    private Integer propiedadContador;

    @Column(length = 200)
    private String observaciones;

    // Relación con servicios
    @OneToMany(mappedBy = "puntoSuministro", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Servicio> servicios;

    // Getters y setters omitidos para brevedad
}
