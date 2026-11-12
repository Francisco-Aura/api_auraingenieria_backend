package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Retrocesión de una comisión ya aprobada: el contrato se dio de baja (él o su punto) o cambió
 * de comercializadora antes de los 365 días. Nace PENDIENTE con el importe que sale de la
 * política; no toca nada hasta que un administrador la aprueba (entonces crea el servicio en
 * negativo y anula las cuotas sin cobrar posteriores a la baja) o la descarta.
 */
@Entity
@Table(name = "mcas_retrocesiones")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasRetrocesion {
    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String DESCARTADA = "DESCARTADA";

    public static final String BAJA_CONTRATO = "BAJA_CONTRATO";
    public static final String BAJA_PUNTO = "BAJA_PUNTO";
    public static final String CAMBIO_COMERCIALIZADORA = "CAMBIO_COMERCIALIZADORA";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Comisión aprobada que se retrocede: una retrocesión por comisión. */
    @Column(name = "id_propuesta", nullable = false, unique = true)
    private Long idPropuesta;
    @Column(name = "id_contrato", nullable = false)
    private Integer idContrato;
    @Column(name = "id_punto_suministro")
    private Integer idPuntoSuministro;
    @Column(name = "id_comercializadora")
    private Integer idComercializadora;
    @Column(nullable = false, length = 30)
    private String motivo;
    /** Contrato con la otra comercializadora, si el motivo es el cambio. */
    @Column(name = "id_contrato_nuevo")
    private Integer idContratoNuevo;
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;
    /** Día en que dejó de estar con la comercializadora. */
    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;
    @Column(name = "dias_activo", nullable = false)
    private Integer diasActivo;
    @Column(name = "id_politica")
    private Integer idPolitica;
    @Column(name = "importe_comision", precision = 18, scale = 2)
    private BigDecimal importeComision;
    /** Números de las cuotas sin cobrar posteriores a la baja, separados por comas. */
    @Column(name = "cuotas_anuladas", length = 60)
    private String cuotasAnuladas;
    @Column(name = "importe_cuotas_anuladas", precision = 18, scale = 2)
    private BigDecimal importeCuotasAnuladas;
    /** Lo que sale de la política; vacío si no hay política o ningún tramo encaja. */
    @Column(name = "importe_calculado", precision = 18, scale = 2)
    private BigDecimal importeCalculado;
    @Column(name = "importe_definitivo", precision = 18, scale = 2)
    private BigDecimal importeDefinitivo;
    /** Cómo se ha calculado, en palabras. */
    @Column(length = 500)
    private String detalle;
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = PENDIENTE;
    @Column(name = "motivo_descarte", length = 500)
    private String motivoDescarte;
    @Column(name = "id_servicio_creado")
    private Integer idServicioCreado;
    @Column(name = "creado_en", nullable = false)
    @Builder.Default
    private LocalDateTime creadoEn = LocalDateTime.now();
    @Column(name = "resuelto_por", length = 250)
    private String resueltoPor;
    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;
}
