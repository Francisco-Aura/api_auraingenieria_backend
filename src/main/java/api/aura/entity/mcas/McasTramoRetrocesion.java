package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Tramo de una política de retrocesión: tiempo desde la activación (en días o
 * meses) y lo que se devolvería si el contrato causa baja dentro de él.
 * PORCENTAJE y FACTOR se guardan como factor (0,6 = 60 %); SIN_CONFIRMAR («Sin
 * elegir» en pantalla) marca un tramo sin tipo de valor: la política no se publica así.
 */
@Entity
@Table(name = "mcas_tramos_retrocesion")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasTramoRetrocesion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "id_politica", nullable = false)
    private Integer idPolitica;
    @Column(nullable = false, length = 10)
    private String unidad;
    @Column(name = "desde_valor", nullable = false)
    private Integer desdeValor;
    @Column(name = "hasta_valor")
    private Integer hastaValor;
    // Los DEFAULT de columnDefinition solo sirven al esquema que genera Hibernate
    // en los tests (H2), para que los scripts SQL se comporten como en MariaDB.
    @Column(name = "desde_inclusivo", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean desdeInclusivo = true;
    @Column(name = "hasta_inclusivo", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean hastaInclusivo = false;
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal valor;
    @Column(name = "tipo_valor", nullable = false, length = 20)
    private String tipoValor;
    @Column(nullable = false, columnDefinition = "INT DEFAULT 100")
    @Builder.Default
    private Integer prioridad = 100;
}
