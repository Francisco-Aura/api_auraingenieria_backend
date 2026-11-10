package api.aura.dto.modulo_planificador;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Equipo con las mismas claves JSON que el Planificador actual (en inglés y con guion bajo). */
public record EquipoDTO(
        @JsonProperty("id") String id,
        @JsonProperty("position") int posicion,
        @JsonProperty("category") String categoria,
        @JsonProperty("group") String partida,
        @JsonProperty("brand") String marca,
        @JsonProperty("model") String modelo,
        @JsonProperty("supplier") String proveedor,
        @JsonProperty("unit") String unidad,
        @JsonProperty("unit_price") double precioUnitario,
        @JsonProperty("per_module") boolean porModulo,
        @JsonProperty("default_qty") double cantidadDefecto,
        @JsonProperty("attrs") Map<String, Object> atributos,
        @JsonProperty("notes") String notas,
        @JsonProperty("sample") boolean muestra,
        @JsonProperty("stock") double stock,
        @JsonProperty("stock_min") double stockMinimo,
        @JsonProperty("datasheet") String fichaTecnica,
        @JsonProperty("offers") List<Map<String, Object>> ofertas,
        @JsonProperty("price_reviewed_at") LocalDate precioRevisadoEl) {
}
