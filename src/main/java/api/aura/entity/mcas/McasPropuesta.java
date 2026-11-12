package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "mcas_propuestas")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasPropuesta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "id_contrato", nullable = false)
    private Integer idContrato;
    @Column(name = "hash_contrato", length = 64)
    private String hashContrato;
    @Column(name = "tipo_evento", nullable = false, length = 50)
    private String tipoEvento;
    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 190)
    private String claveIdempotencia;
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String estado = "PENDIENTE_CALCULO";
    @Column(name = "id_conjunto_reglas")
    private Integer idConjuntoReglas;
    @Column(name = "id_politica_retrocesion")
    private Integer idPoliticaRetrocesion;
    @Column(name = "instantanea_json", columnDefinition = "MEDIUMTEXT")
    private String instantaneaJson;
    @Column(name = "datos_normalizados_json", columnDefinition = "TEXT")
    private String datosNormalizadosJson;
    @Column(name = "traza_calculo_json", columnDefinition = "MEDIUMTEXT")
    private String trazaCalculoJson;
    /** NUNCA sobrescribir — es el resultado automático original */
    @Column(name = "importe_calculado", precision = 18, scale = 6)
    private BigDecimal importeCalculado;
    @Column(name = "importe_definitivo", precision = 18, scale = 6)
    private BigDecimal importeDefinitivo;
    @Column(name = "numero_pagos")
    private Integer numeroPagos;
    @Column(name = "fecha_primera_aplicacion")
    private LocalDate fechaPrimeraAplicacion;
    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;
    @Column(name = "datos_editados_json", columnDefinition = "TEXT")
    private String datosEditadosJson;
    @Column(name = "advertencias_json", columnDefinition = "TEXT")
    private String advertenciasJson;
    @Column(name = "id_servicio_creado")
    private Integer idServicioCreado;
    @Column(name = "creado_por", length = 250)
    private String creadoPor;
    @Column(name = "creado_en", nullable = false)
    @Builder.Default
    private LocalDateTime creadoEn = LocalDateTime.now();
    @Column(name = "actualizado_por", length = 250)
    private String actualizadoPor;
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
    @Column(name = "aprobado_por", length = 250)
    private String aprobadoPor;
    @Column(name = "aprobado_en")
    private LocalDateTime aprobadoEn;
    @Version
    @Column(name = "version_optimista", nullable = false)
    @Builder.Default
    private Integer versionOptimista = 0;
}
