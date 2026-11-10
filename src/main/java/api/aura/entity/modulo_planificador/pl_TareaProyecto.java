package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tarea de un proyecto (separada de modulo_tareas del CRM: decisión del usuario en la Fase 4). */
@Entity
@Table(name = "pl_tareas_proyecto")
@Getter
@Setter
@NoArgsConstructor
public class pl_TareaProyecto {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "proyecto_id", nullable = false, length = 64)
    private String proyectoId;

    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String descripcion = "";

    @Column(nullable = false)
    private String fase = "";

    @Column(nullable = false)
    private String departamento = "";

    /** Orden dentro del proyecto (el de la plantilla). */
    @Column(nullable = false)
    private int posicion;

    @Column(nullable = false)
    private boolean hito;

    @Column(nullable = false)
    private LocalDate inicio;

    @Column(nullable = false)
    private LocalDate fin;

    /** Plazo de referencia: solo cambia cuando alguien replanifica a mano. Su diferencia con el fin es el retraso. */
    @Column(name = "fin_previsto")
    private LocalDate finPrevisto;

    @Column(nullable = false)
    private int progreso;

    /** todo | doing | paused | blocked | done. «blocked» exige un motivo. */
    @Column(nullable = false, length = 20)
    private String estado = "todo";

    @Column(name = "motivo_estado", nullable = false)
    private String motivoEstado = "";

    @Column(name = "nota_estado", columnDefinition = "TEXT", nullable = false)
    private String notaEstado = "";

    @Column(name = "completada_el")
    private LocalDate completadaEl;

    @Column(name = "nota_cierre", columnDefinition = "TEXT", nullable = false)
    private String notaCierre = "";

    /** Documentos o elementos que hay que aportar para poder finalizarla. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entregables", nullable = false)
    private List<Map<String, Object>> entregables = new ArrayList<>();

    /** Lo aportado: enlaces, notas y archivos. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "adjuntos", nullable = false)
    private List<Map<String, Object>> adjuntos = new ArrayList<>();

    /** Datos del contrato que esta tarea usa (p. ej. «paneles_uds»). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usa", nullable = false)
    private List<String> usa = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cambios_revisados", nullable = false)
    private List<String> cambiosRevisados = new ArrayList<>();

    /** Si es la tarea de facturar un pago del contrato (Fase 5). */
    @Column(name = "etapa_pago")
    private Integer etapaPago;

    @Column(name = "importe_pago")
    private Double importePago;

    @Column(name = "etiqueta_pago", nullable = false)
    private String etiquetaPago = "";

    @Column(name = "carpeta_dropbox", nullable = false)
    private String carpetaDropbox = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "avisadas", nullable = false)
    private List<String> avisadas = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "marcas_vencimiento", nullable = false)
    private List<String> marcasVencimiento = new ArrayList<>();

    /** Ids de las tareas que deben terminar antes de que esta pueda empezar (fin → inicio). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "depende_de", nullable = false)
    private List<String> dependeDe = new ArrayList<>();

    /** Subtareas simples: [{"text","done"}]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lista", nullable = false)
    private List<Map<String, Object>> lista = new ArrayList<>();

    /** Tarea con pantalla propia («material»); vacío = una tarea normal. */
    @Column(nullable = false, length = 40)
    private String tipo = "";

    @Column(name = "creado_el", nullable = false)
    private LocalDateTime creadoEl;
}
