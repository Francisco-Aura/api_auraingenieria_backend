package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Estudio fotovoltaico: datos de entrada, resultado calculado y curva horaria de consumo (en Wh separados por comas). */
@Entity
@Table(name = "pl_estudios")
@Getter
@Setter
@NoArgsConstructor
public class pl_Estudio {

    @Id
    @Column(length = 64)
    private String id;

    /** Nombre del cliente (el de la intranet o el del cliente potencial), para la lista y el informe. */
    @Column(nullable = false)
    private String cliente = "";

    /** Id del cliente en la intranet; nulo si es un cliente potencial. */
    @Column(name = "cliente_id")
    private Integer clienteId;

    /** «existente» (de la intranet) o «potencial». */
    @Column(name = "cliente_tipo", nullable = false, length = 20)
    private String clienteTipo = "potencial";

    /** Propuesta (pl_proyectos_comerciales) a la que pertenece; vacío si el estudio no tiene proyecto. */
    @org.hibernate.annotations.ColumnDefault("''")
    @Column(name = "proyecto_id", nullable = false, length = 64)
    private String proyectoId = "";

    @Column(nullable = false, length = 40)
    private String cups = "";

    @Column(nullable = false, length = 40)
    private String estado = "en_estudio";

    /** Sube con cada guardado: si otra persona guardó mientras se tenía abierto, el guardado avisa (409). */
    @Column(nullable = false)
    private int version = 1;

    @Lob
    @Column(nullable = false)
    private String observaciones = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos", nullable = false)
    private Map<String, Object> datos = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultado", nullable = false)
    private Map<String, Object> resultado = new LinkedHashMap<>();

    @Lob
    @Column(name = "curva")
    private String curva;

    @Column(name = "curva_estimada", nullable = false)
    private boolean curvaEstimada;

    @Column(name = "curva_origen", nullable = false)
    private String curvaOrigen = "";

    @Column(name = "creado_el", nullable = false)
    private LocalDateTime creadoEl;

    /** Quien lo guardó y compartió por primera vez. */
    @Column(name = "creado_por", nullable = false)
    private String creadoPor = "";

    @Column(name = "actualizado_el", nullable = false)
    private LocalDateTime actualizadoEl;

    @Column(name = "actualizado_por", nullable = false)
    private String actualizadoPor = "";
}
