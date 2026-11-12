package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "mcas_lineas_propuesta")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasLineaPropuesta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "id_propuesta", nullable = false)
    private Long idPropuesta;
    @Column(name = "tipo_linea", nullable = false, length = 50)
    private String tipoLinea;
    @Column(length = 500)
    private String descripcion;
    @Column(length = 50)
    private String metodo;
    @Column(name = "valor_base", precision = 18, scale = 6)
    private BigDecimal valorBase;
    @Column(name = "valor_tasa", precision = 18, scale = 6)
    private BigDecimal valorTasa;
    @Column(name = "valor_porcentaje", precision = 18, scale = 6)
    private BigDecimal valorPorcentaje;
    @Column(name = "importe_calculado", nullable = false, precision = 18, scale = 6)
    private BigDecimal importeCalculado;
    @Column(name = "importe_definitivo", precision = 18, scale = 6)
    private BigDecimal importeDefinitivo;
    @Column(name = "id_regla")
    private Integer idRegla;
    @Builder.Default
    private Integer orden = 1;
}
