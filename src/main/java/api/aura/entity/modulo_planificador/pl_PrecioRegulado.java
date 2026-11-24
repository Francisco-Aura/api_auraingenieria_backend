package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Un precio regulado de la electricidad («a BOE») de un BOE ({@link pl_Boe}) y una tarifa de acceso: el peaje (CNMC) o el cargo
 * (Ministerio) del término de energía (€/kWh) o de potencia (€/kW·año), periodo a periodo. Los periodos que la tarifa no tiene quedan a nulo.
 */
@Entity
@Table(name = "pl_precios_regulados", uniqueConstraints = @UniqueConstraint(name = "uk_pl_precios_regulados", columnNames = {"boe_id", "tarifa", "termino", "concepto"}))
@Getter
@Setter
@NoArgsConstructor
public class pl_PrecioRegulado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** El BOE al que pertenece este precio. */
    @Column(name = "boe_id", nullable = false)
    private Integer boeId;

    /** Tarifa de acceso sin espacios: 2.0TD, 3.0TD, 6.1TD… */
    @Column(nullable = false, length = 10)
    private String tarifa;

    /** «energia» (€/kWh) o «potencia» (€/kW·año). */
    @Column(nullable = false, length = 10)
    private String termino;

    /** «peaje» o «cargo». */
    @Column(nullable = false, length = 10)
    private String concepto;

    @Column(precision = 12, scale = 6)
    private BigDecimal p1;
    @Column(precision = 12, scale = 6)
    private BigDecimal p2;
    @Column(precision = 12, scale = 6)
    private BigDecimal p3;
    @Column(precision = 12, scale = 6)
    private BigDecimal p4;
    @Column(precision = 12, scale = 6)
    private BigDecimal p5;
    @Column(precision = 12, scale = 6)
    private BigDecimal p6;

    /** Disposición del BOE de la que sale (BOE-A-…). */
    @Column(nullable = false)
    private String referencia = "";

    public BigDecimal[] periodos() {
        return new BigDecimal[]{p1, p2, p3, p4, p5, p6};
    }
}
