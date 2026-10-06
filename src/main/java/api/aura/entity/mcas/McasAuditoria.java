package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "mcas_auditoria")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasAuditoria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "id_propuesta")
    private Long idPropuesta;
    @Column(name = "id_contrato")
    private Integer idContrato;
    @Column(name = "tipo_evento", nullable = false, length = 100)
    private String tipoEvento;
    @Column(name = "estado_anterior", length = 30)
    private String estadoAnterior;
    @Column(name = "estado_nuevo", length = 30)
    private String estadoNuevo;
    @Column(name = "datos_json", columnDefinition = "MEDIUMTEXT")
    private String datosJson;
    @Column(length = 250)
    private String usuario;
    @Column(name = "fecha_evento", nullable = false)
    @Builder.Default
    private LocalDateTime fechaEvento = LocalDateTime.now();
    @Column(name = "id_correlacion", length = 100)
    private String idCorrelacion;
}
