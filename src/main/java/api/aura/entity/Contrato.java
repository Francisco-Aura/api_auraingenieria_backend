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

    @Column
    private int idTarifaLuz;

    @Column
    private int idTarifaGas;

    @Column
    private Double potencia1;

    @Column
    private Double potencia2;

    @Column
    private Double potencia3;

    @Column
    private Double potencia4;

    @Column
    private Double potencia5;

    @Column
    private Double potencia6;

    @Column
    private Double consumoAnual;

    @Column
    private Integer exencionImpuestoElectricidad;

    @Column
    private Integer idComercializadoraActual;

    @Column
    private Integer idComercializadoraAnterior;

    @Column(length = 10000)
    private String observaciones;

    @Temporal(TemporalType.DATE)
    private Date fechaBaja;

    @Column
    private Integer autoconsumo;

    @Column
    private Integer denunciaContrato;

    @Temporal(TemporalType.DATE)
    private Date fechaDenunciaContrato;

    //Usuario para facturación online
    @Column(length = 200)
    private String userFO;

    //Contraseña para facturación online
    @Column(length = 200)
    private String passwordFO;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String comentarioRenovacion;
}