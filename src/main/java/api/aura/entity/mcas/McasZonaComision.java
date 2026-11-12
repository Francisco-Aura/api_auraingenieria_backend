package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Una zona de comisión de una comercializadora y las provincias que la forman (reunión del
 * 04/11/2026 y respuesta del cliente a la duda 5): en Iberdrola, las provincias de la zona ZAR
 * y, como zona del resto, la ZBR. La zona de un contrato sale de la provincia de su punto de
 * suministro. Ver {@code McasZonasService}.
 */
@Entity @Table(name = "mcas_zonas_comision")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasZonaComision {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_comercializadora", nullable = false)
    private Integer idComercializadora;

    /** Código de la zona, el mismo que usan las reglas y las tablas de importes (ZAR, ZBR...). */
    @Column(nullable = false, length = 20)
    private String zona;

    /** Códigos de sus provincias (las dos cifras del código postal). Vacía si es la zona del resto. */
    @Builder.Default
    @Convert(converter = ListaCodigos.class)
    @Column(length = 200)
    private List<String> provincias = new ArrayList<>();

    /** Zona de todas las provincias que no estén en otra zona de la comercializadora. */
    @Builder.Default
    @Column(name = "es_resto", nullable = false)
    private Boolean esResto = false;

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    /** «03,30,46» en la columna, lista en el código. */
    @Converter
    public static class ListaCodigos implements AttributeConverter<List<String>, String> {
        @Override
        public String convertToDatabaseColumn(List<String> codigos) {
            return codigos == null || codigos.isEmpty() ? null : String.join(",", codigos);
        }

        @Override
        public List<String> convertToEntityAttribute(String columna) {
            if (columna == null || columna.isBlank()) return new ArrayList<>();
            return new ArrayList<>(Arrays.stream(columna.split(",")).map(String::trim).filter(c -> !c.isEmpty()).toList());
        }
    }
}
