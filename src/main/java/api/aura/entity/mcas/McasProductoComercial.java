package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name = "mcas_productos_comerciales")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasProductoComercial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "id_comercializadora", nullable = false)
    private Integer idComercializadora;
    @Column(name = "id_gestora")
    private Integer idGestora;
    @Column(nullable = false, length = 100)
    private String codigo;
    @Column(nullable = false, length = 250)
    private String nombre;
    @Column(name = "tipo_precio", nullable = false, length = 10)
    private String tipoPrecio;
    @Column(name = "tipo_suministro", nullable = false, length = 5)
    private String tipoSuministro;
    @Column(name = "codigo_zona", length = 50)
    private String codigoZona;
    @Column(name = "codigo_go", length = 50)
    private String codigoGo;
    @Column(name = "activo_desde")
    private LocalDate activoDesde;
    @Column(name = "activo_hasta")
    private LocalDate activoHasta;
    @Builder.Default
    private Boolean activo = true;
}
