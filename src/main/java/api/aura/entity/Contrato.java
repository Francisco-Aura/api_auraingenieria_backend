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

    @Column(length = 255)
    private String nombreArchivo;

    @Column(length = 500)
    private String rutaArchivoContrato;

    @Column(length = 100)
    private String tipoMimeArchivo;

    private Long tamanioArchivo;

    // -----------------------------------------------
    // Campos MCAS — todos nullable para compatibilidad
    // -----------------------------------------------

    /** FEE manual del contrato (€/MWh u otra unidad definida en mcas_fee_unidad) */
    @Column(name = "mcas_fee_eur_mwh", precision = 18, scale = 6)
    private java.math.BigDecimal mcasFeeEurMwh;

    @Column(name = "mcas_fee_unidad", length = 20)
    private String mcasFeeUnidad;

    /** FIJO o INDEXADO */
    @Column(name = "mcas_tipo_precio", length = 10)
    private String mcasTipoPrecio;

    /** LUZ o GAS */
    @Column(name = "mcas_tipo_suministro", length = 5)
    private String mcasTipoSuministro;

    /** RESIDENCIAL o PYME */
    @Column(name = "mcas_segmento", length = 20)
    private String mcasSegmento;

    @Column(name = "mcas_es_renovacion")
    private Boolean mcasEsRenovacion;

    /** FIRMADA, TACITA, OTRA */
    @Column(name = "mcas_tipo_renovacion", length = 20)
    private String mcasTipoRenovacion;

    /** DENTRO, FUERA, NA */
    @Column(name = "mcas_ambito", length = 10)
    private String mcasAmbito;

    @Column(name = "mcas_id_gestora")
    private Integer mcasIdGestora;

    @Column(name = "mcas_id_producto_comercial")
    private Integer mcasIdProductoComercial;

    @Column(name = "mcas_zona_comision", length = 50)
    private String mcasZonaComision;

    @Column(name = "mcas_potencia_calculo", precision = 18, scale = 6)
    private java.math.BigDecimal mcasPotenciaCalculo;

    /** Número de pagos manual — tiene prioridad sobre sugerencia de regla */
    @Column(name = "mcas_numero_pagos")
    private Integer mcasNumeroPagos;

    @Temporal(TemporalType.DATE)
    @Column(name = "mcas_fecha_primera_aplicacion")
    private java.util.Date mcasFechaPrimeraAplicacion;

    @Column(name = "mcas_alta_nueva")
    private Boolean mcasAltaNueva;

    @Temporal(TemporalType.DATE)
    @Column(name = "mcas_fecha_ultima_relacion_comercializadora")
    private java.util.Date mcasFechaUltimaRelacionComercializadora;
}
