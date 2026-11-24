package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "mcas_conjuntos_reglas")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasConjuntoReglas {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, unique = true, length = 100)
    private String codigo;
    /** Nombre legible para el administrador («Endesa 2026-10»); el código sigue siendo la clave (V20260930_1). */
    @Column(length = 150)
    private String nombre;
    @Column(length = 1000)
    private String descripcion;
    /** Conjunto del que se copió con «Nueva versión»; sin clave foránea (V20260930_1). */
    @Column(name = "id_conjunto_origen")
    private Integer idConjuntoOrigen;
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String version = "1.0";
    @Column(name = "id_comercializadora", nullable = false)
    private Integer idComercializadora;
    @Column(name = "id_gestora")
    private Integer idGestora;
    @Column(name = "fase_precio", nullable = false, length = 10)
    @Builder.Default
    private String fasePrecio = "FIJO";
    @Column(name = "vigencia_desde", nullable = false)
    private LocalDate vigenciaDesde;
    @Column(name = "vigencia_hasta")
    private LocalDate vigenciaHasta;
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "BORRADOR";
    @Column(name = "archivo_origen", length = 250)
    private String archivoOrigen;
    @Column(name = "hoja_origen", length = 100)
    private String hojaOrigen;
    @Column(name = "fecha_interna_origen")
    private LocalDate fechaInternaOrigen;
    @Column(name = "hash_origen", length = 64)
    private String hashOrigen;
    @Column(name = "creado_por", length = 250)
    private String creadoPor;
    @Column(name = "aprobado_por", length = 250)
    private String aprobadoPor;
    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();
    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;
}
