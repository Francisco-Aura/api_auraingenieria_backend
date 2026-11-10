package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Borrador compartido del alta de proyecto. */
@Entity
@Table(name = "pl_borradores")
@Getter
@Setter
@NoArgsConstructor
public class pl_Borrador {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String nombre = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos", nullable = false)
    private Map<String, Object> datos = new LinkedHashMap<>();

    @Column(name = "actualizado_el", nullable = false)
    private LocalDateTime actualizadoEl;

    @Column(name = "actualizado_por", nullable = false)
    private String actualizadoPor = "";
}
