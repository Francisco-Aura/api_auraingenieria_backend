package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    @JsonProperty("CUPSluz")
    private String CUPSluz;

    @Column(length = 100)
    @JsonProperty("CUPSgas")
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

    @ManyToOne
    @JoinColumn(name = "id_distribuidora")
    private Distribuidora distribuidora;

    @Column(name = "potencia_bie")
    private Double potenciaBIE;

    @Column(name = "potencia_tecnica_red")
    private Double potenciaTecnicaRed;

    @Column(name = "apm_centro_transformacion", length = 255)
    private String apmCentroTransformacion;

    @ManyToOne
    @JoinColumn(name = "id_tipo_contador")
    private TipoContador tipoContador;

    @ManyToOne
    @JoinColumn(name = "id_tipo_comunicacion")
    private TipoComunicacion tipoComunicacion;

    @Column(name = "numero_contador", length = 255)
    private String numeroContador;

    @Column(name = "numero_telefono", length = 20)
    private String numeroTelefono;

    @Column(name = "direccion_enlace", length = 255)
    private String direccionEnlace;

    @Column(name = "punto_medida", length = 255)
    private String puntoMedida;

    @Column(length = 255)
    private String clave;

    @ManyToOne
    @JoinColumn(name = "id_propiedad")
    private Propiedad propiedad;

    @Column(name = "exencion_impuesto_electrico", columnDefinition = "BIT(1)")
    private Boolean exencionImpuestoElectrico;

    @Column(name = "porcentaje_concesion")
    private Double porcentajeConcesion;

    @Temporal(TemporalType.DATE)
    @Column(name = "fecha_concesion")
    private Date fechaConcesion;

    @Column(name = "autoconsumo", length = 255)
    private String autoconsumo;

    @Temporal(TemporalType.DATE)
    @Column(name = "fecha_activacion_vertido")
    private Date fechaActivacionVertido;

    @Column(name = "usuario_monitorizacion", length = 255)
    private String usuarioMonitorizacion;

    @Column(name = "pass_monitorizacion", length = 255)
    private String passMonitorizacion;

    /*
    // Relación con servicios
    @OneToMany(mappedBy = "puntoSuministro", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore // Evita la serialización de esta propiedad
    private List<Servicio> servicios;
     */

}
