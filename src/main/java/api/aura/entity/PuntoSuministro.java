package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

    /*
    // Relación con servicios
    @OneToMany(mappedBy = "puntoSuministro", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore // Evita la serialización de esta propiedad
    private List<Servicio> servicios;
     */

}
