package api.aura.dto.modulo_planificador;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** Cuerpo para guardar un presupuesto (alta y cambios); claves JSON como en el Planificador actual. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PresupuestoGuardarRequest {

    /** Solo al crear: el que ya tenía como presupuesto local. */
    @Size(max = 64)
    @JsonProperty("id")
    private String id;

    @Size(max = 200)
    @JsonProperty("name")
    private String nombre = "";

    @NotNull
    @JsonProperty("data")
    private Map<String, Object> datos;

    @Size(max = 20)
    @JsonProperty("customer_kind")
    private String clienteTipo = "";

    @JsonProperty("customer_id")
    private Integer clienteId;

    @PositiveOrZero
    @JsonProperty("kwp")
    private double kwp = 0.0;

    @PositiveOrZero
    @JsonProperty("panels")
    private int paneles = 0;

    @JsonProperty("budget_total")
    private double totalPresupuesto = 0.0;

    /** La versión que se tenía abierta: si otra persona guardó después, se avisa (409). */
    @JsonProperty("version")
    private Integer version;

    /** Guardar encima de lo que haya, aunque alguien haya guardado antes. */
    @JsonProperty("force")
    private boolean forzar = false;
}
