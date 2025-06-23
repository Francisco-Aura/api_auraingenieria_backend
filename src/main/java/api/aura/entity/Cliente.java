package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

    @Column(length = 100)
    private String userTelemedida;
    @Column(length = 100)
    private String passTelemedida;

    // Campos de instalaciones - Batería de Condensadores
    @Column(length = 10)
    private String bateriaCondensadores; // SI/NO

    @Column(length = 100)
    private String potenciaBateria;

    @Column(length = 100)
    private String modeloBateria;

    // Campos de instalaciones - Instalación Fotovoltaica
    @Column(length = 10)
    private String instalacionFV; // SI/NO

    @Column(length = 100)
    private String kwInversor;

    @Column(length = 100)
    private String kwPanelesSolares;

    @ManyToOne
    @JoinColumn(name = "TBmonitorizacion_id")
    private AppMonitorizacion appMonitorizacion;

    // Campos de instalaciones - Almacenamiento Energético
    @Column(length = 10)
    private String bateriaAlmacenamiento; // SI/NO

    @Column(length = 100)
    private String potenciaBateriaAlmacenamiento;

    @Column(length = 10)
    private String backup; // SI/NO @Column(length = 10)
    private String peaksaving; // SI/NO

    @ManyToOne
    @JoinColumn(name = "TBIntegracionSistemasFV_id")
    private IntegracionSistemasFV integracionSistemasFV;

    // Servicios del cliente
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean gestionEnergetica = false;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean telemedida = false;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean monitorizacionEnergetica = false;

    @ManyToOne
    @JoinColumn(name = "idUsuario")
    private Usuario usuario;

    // Relación con puntos de suministro
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<PuntoSuministro> puntosSuministro;

}
