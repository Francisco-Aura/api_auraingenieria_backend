package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.List;

@Entity
@Table(name = "Clientes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 250)
    private String nombre;

    @Column(length = 50, unique = true)
    private String cif;

    @Column(length = 200)
    private String direccion;

    @Column(length = 100)
    private String poblacion;

    @Column(length = 100)
    private String provincia;

    @Column(length = 10)
    private String codigoPostal;

    @Column(length = 100)
    private String nombreAutorizado;

    @Column(length = 100)
    private String telefonoAutorizado;

    @Column(length = 100)
    private String dniAutorizado;

    @Column(length = 100)
    private String emailAutorizado;

    @Column(length = 100)
    private String nombreContacto1;

    @Column(length = 100)
    private String cargoContacto1;

    @Column(length = 100)
    private String telefonoContacto1;

    @Column(length = 100)
    private String emailContacto1;

    @Column(length = 100)
    private String nombreContacto2;

    @Column(length = 100)
    private String cargoContacto2;

    @Column(length = 100)
    private String telefonoContacto2;

    @Column(length = 100)
    private String emailContacto2;

    @Column(length = 100)
    private String nombreContacto3;

    @Column(length = 100)
    private String cargoContacto3;

    @Column(length = 100)
    private String telefonoContacto3;

    @Column(length = 100)
    private String emailContacto3;

    @Column(length = 100)
    private String iban;

    @Lob
    private String notas;

    @Temporal(TemporalType.DATE)
    private Date fechaAlta;

    @Temporal(TemporalType.DATE)
    private Date fechaBaja;

    @Column(length = 100)
    private String nombreComercial;

    private Integer codigoSector;

    private Integer codigoSubtipologia;

    @Column(length = 100)
    private String actividad;

    @ManyToOne
    @JoinColumn(name = "idUsuario")
    private Usuario usuario;

    // Relación con puntos de suministro
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PuntoSuministro> puntosSuministro;

}
