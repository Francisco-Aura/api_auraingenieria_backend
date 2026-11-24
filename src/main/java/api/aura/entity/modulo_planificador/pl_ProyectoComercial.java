package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Proyecto comercial: un cliente y el nombre de su proyecto, creado antes que sus estudios y presupuestos (no es el proyecto en marcha,
 * {@link pl_Proyecto}, que nace al firmar el contrato). Estudios y presupuestos del mismo cliente y con el mismo nombre de proyecto
 * cuelgan de él en la lista; mientras no tiene ninguno, sale como proyecto vacío.
 */
@Entity
@Table(name = "pl_proyectos_comerciales")
@Getter
@Setter
@NoArgsConstructor
public class pl_ProyectoComercial {

    @Id
    @Column(length = 64)
    private String id;

    /** Cliente + proyecto normalizados: no puede haber dos iguales. */
    @Column(nullable = false, unique = true)
    private String clave = "";

    /** «existente» (de la intranet) o «potencial». */
    @Column(name = "cliente_tipo", nullable = false, length = 20)
    private String clienteTipo = "potencial";

    /** Id del cliente en la intranet; nulo si es un cliente potencial. */
    @Column(name = "cliente_id")
    private Integer clienteId;

    @Column(name = "cliente_nombre", nullable = false)
    private String clienteNombre = "";

    /** El cliente entero tal como lo usa la pantalla (tipo, id, nombre, CIF…): con él se precargan sus estudios y presupuestos. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cliente", nullable = false)
    private Map<String, Object> cliente = new LinkedHashMap<>();

    @Column(nullable = false, length = 80)
    private String nombre = "";

    /** Logo de la empresa del cliente (data URI): lo heredan los presupuestos que se crean desde la propuesta. Vacío = sin logo. */
    @Lob
    @Column(nullable = false)
    private String logo = "";

    /** Encaje de los paneles (data URI), como el logo. */
    @Lob
    @Column(nullable = false)
    private String encaje = "";

    @Column(name = "creado_el", nullable = false)
    private LocalDateTime creadoEl;

    @Column(name = "creado_por", nullable = false)
    private String creadoPor = "";
}
