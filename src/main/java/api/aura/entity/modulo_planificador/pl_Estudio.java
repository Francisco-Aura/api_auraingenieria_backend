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

    @Column(nullable = false)
    private String cliente = "";

    @Column(nullable = false, length = 40)
    private String cups = "";

    @Column(nullable = false, length = 40)
    private String estado = "en_estudio";

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

    @Column(name = "actualizado_el", nullable = false)
    private LocalDateTime actualizadoEl;

    @Column(name = "actualizado_por", nullable = false)
    private String actualizadoPor = "";
}
