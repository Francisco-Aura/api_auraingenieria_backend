package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cómo cobra la comisión una comercializadora: en cuántos pagos y cuándo llega el primero.
 * Sin gestora son sus condiciones generales; con gestora, las de esa gestora (p. ej. Nexo,
 * que junta varios meses en una liquidación) y ganan a las generales.
 * Ver {@code McasCondicionesCobroService}.
 */
@Entity @Table(name = "mcas_condiciones_cobro")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasCondicionCobro {

    /** Día del primer cobro (y de los siguientes, uno cada mes). */
    public enum DiaCobro {
        /** El mismo día del mes que la fecha de inicio del contrato. */
        MISMO_DIA,
        /** El último día del mes. */
        ULTIMO_DIA_MES,
        /** El día 1 del mes. */
        DIA_1
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_comercializadora", nullable = false)
    private Integer idComercializadora;

    /** Vacío: condiciones generales de la comercializadora. */
    @Column(name = "id_gestora")
    private Integer idGestora;

    /** 1 a 12; vacío: el que diga la regla o un pago. */
    @Column(name = "numero_pagos")
    private Integer numeroPagos;

    /** Meses desde la fecha de inicio del contrato hasta el primer cobro (0 a 24). */
    @Builder.Default
    @Column(name = "meses_hasta_primer_cobro", nullable = false)
    private Integer mesesHastaPrimerCobro = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "dia_primer_cobro", nullable = false, length = 20)
    private DiaCobro diaPrimerCobro = DiaCobro.MISMO_DIA;

    @Column(length = 500)
    private String observaciones;

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
}
