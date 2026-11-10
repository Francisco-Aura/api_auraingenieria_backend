package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "pl_historial_precios")
@Getter
@Setter
@NoArgsConstructor
public class pl_HistorialPrecio {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "equipo_id", nullable = false, length = 64)
    private String equipoId;

    @Column(nullable = false)
    private double precio;

    @Column(nullable = false)
    private String proveedor = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ofertas", nullable = false)
    private List<Map<String, Object>> ofertas = new ArrayList<>();

    /** Quién hizo el cambio. */
    @Column(name = "por", nullable = false)
    private String por = "";

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
