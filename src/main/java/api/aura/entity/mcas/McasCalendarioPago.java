package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name = "mcas_calendario_pagos")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasCalendarioPago {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "id_propuesta", nullable = false)
    private Long idPropuesta;
    @Column(name = "numero_pago", nullable = false)
    private Integer numeroPago;
    @Column(name = "fecha_pago")
    private LocalDate fechaPago;
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal importe;
    @Builder.Default
    private String estado = "PENDIENTE";
    @Builder.Default
    private String naturaleza = "DEFINITIVO";
}
