package api.aura.dto.modulo_planificador;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Alta de un equipo; claves JSON como en el Planificador actual. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EquipoCrearRequest {

    @NotNull
    @JsonProperty("category")
    private String categoria;

    @NotNull
    @JsonProperty("group")
    private String partida;

    @JsonProperty("brand")
    private String marca = "";

    @JsonProperty("model")
    private String modelo = "";

    @JsonProperty("supplier")
    private String proveedor = "";

    @JsonProperty("unit")
    private String unidad = "ud";

    @PositiveOrZero
    @JsonProperty("unit_price")
    private double precioUnitario = 0.0;

    @JsonProperty("per_module")
    private boolean porModulo = false;

    @PositiveOrZero
    @JsonProperty("default_qty")
    private double cantidadDefecto = 1.0;

    @JsonProperty("attrs")
    private Map<String, Object> atributos = new LinkedHashMap<>();

    @JsonProperty("notes")
    private String notas = "";

    @PositiveOrZero
    @JsonProperty("stock")
    private double stock = 0.0;

    @PositiveOrZero
    @JsonProperty("stock_min")
    private double stockMinimo = 0.0;

    @JsonProperty("datasheet")
    private String fichaTecnica = "";

    @Size(max = 30)
    @JsonProperty("offers")
    private List<Map<String, Object>> ofertas = new ArrayList<>();
}
