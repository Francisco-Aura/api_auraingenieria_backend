package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "mcas_valores_rango")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasValorRango {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "id_regla", nullable = false)
    private Integer idRegla;
    @Column(nullable = false, length = 50)
    private String dimension;
    @Column(name = "valor_min", precision = 18, scale = 6)
    private BigDecimal valorMin;
    @Column(name = "valor_max", precision = 18, scale = 6)
    private BigDecimal valorMax;
    @Column(name = "min_inclusivo", nullable = false)
    @Builder.Default
    private Boolean minInclusivo = true;
    @Column(name = "max_inclusivo", nullable = false)
    @Builder.Default
    private Boolean maxInclusivo = false;
    @Column(name = "valor_categoria", length = 100)
    private String valorCategoria;
    @Column(name = "valor_resultado", nullable = false, precision = 18, scale = 6)
    private BigDecimal valorResultado;
    @Column(name = "unidad_resultado", length = 20)
    private String unidadResultado;
    @Builder.Default
    private Integer orden = 1;
}
