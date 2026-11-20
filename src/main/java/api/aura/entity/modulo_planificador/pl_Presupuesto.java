package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Presupuesto del presupuestador (borrador compartido, enviado, validado o descartado). */
@Entity
@Table(name = "pl_presupuestos")
@Getter
@Setter
@NoArgsConstructor
public class pl_Presupuesto {

    @Id
    @Column(length = 64)
    private String id;

    /** Cliente / proyecto. */
    @Column(nullable = false)
    private String nombre = "";

    /** Quién lo creó (nombre en la intranet); vacío si no se pudo identificar. */
    @Column(nullable = false)
    private String propietario = "";

    /** Id de ese usuario en la intranet, si venía. */
    @Column(name = "propietario_id", nullable = false, length = 64)
    private String propietarioId = "";

    /** borrador, enviado (bloquea la edición), validado o descartado. */
    @Column(nullable = false, length = 20)
    private String estado = "borrador";

    /** Las variantes de una misma oferta comparten este id. */
    @Column(name = "oferta_id", nullable = false, length = 64)
    private String ofertaId = "";

    @Column(name = "cliente_id")
    private Integer clienteId;

    /** «existente», «potencial» o vacío (presupuestos anteriores a este campo). */
    @Column(name = "cliente_tipo", nullable = false, length = 20)
    private String clienteTipo = "";

    /** Estudio al que está asociado (mismo cliente y mismo proyecto); vacío si no lo está. */
    @org.hibernate.annotations.ColumnDefault("''")
    @Column(name = "estudio_id", nullable = false, length = 64)
    private String estudioId = "";

    /** Propuesta (pl_proyectos_comerciales) a la que pertenece; vacío si el nombre no tiene proyecto. */
    @org.hibernate.annotations.ColumnDefault("''")
    @Column(name = "proyecto_id", nullable = false, length = 64)
    private String proyectoId = "";

    @Column(name = "nombre_variante", nullable = false, length = 60)
    private String nombreVariante = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos", nullable = false)
    private Map<String, Object> datos = new LinkedHashMap<>();

    @Column(nullable = false)
    private double kwp;

    @Column(nullable = false)
    private int paneles;

    @Column(name = "total_presupuesto", nullable = false)
    private double totalPresupuesto;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "creado_el", nullable = false)
    private LocalDateTime creadoEl;

    @Column(name = "actualizado_el", nullable = false)
    private LocalDateTime actualizadoEl;

    @Column(name = "actualizado_por", nullable = false)
    private String actualizadoPor = "";
}
