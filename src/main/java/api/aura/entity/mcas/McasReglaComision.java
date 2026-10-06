package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "mcas_reglas_comision")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasReglaComision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "id_conjunto_reglas", nullable = false)
    private Integer idConjuntoReglas;
    @Column(nullable = false, length = 100)
    private String codigo;
    /** Nombre legible para el administrador («Iris · pyme luz · 44 % del FEE») (V20260930_1). */
    @Column(length = 150)
    private String nombre;
    @Column(length = 1000)
    private String descripcion;
    @Column(nullable = false)
    @Builder.Default
    private Integer prioridad = 100;
    @Column(name = "metodo_calculo", nullable = false, length = 50)
    private String metodoCalculo;
    @Column(name = "condiciones_json", columnDefinition = "TEXT")
    private String condicionesJson;
    @Column(name = "calculo_json", columnDefinition = "TEXT")
    private String calculoJson;
    @Column(name = "politica_pago_json", columnDefinition = "TEXT")
    private String politicaPagoJson;
    @Column(name = "motivo_manual", length = 500)
    private String motivoManual;
    @Column(name = "localizador_origen", length = 250)
    private String localizadorOrigen;
    @Builder.Default
    private Boolean activo = true;
}
