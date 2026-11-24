package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Producción horaria de 1 kWp que dio PVGIS para una ubicación, inclinación y azimut (caché: se pide una sola vez). */
@Entity
@Table(name = "pl_pvgis_series", uniqueConstraints = @UniqueConstraint(name = "uk_pl_pvgis_series", columnNames = {"lat_c", "lon_c", "angulo", "azimut"}))
@Getter
@Setter
@NoArgsConstructor
public class pl_PvgisSerie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Latitud en centésimas de grado. */
    @Column(name = "lat_c", nullable = false)
    private Integer latC;

    @Column(name = "lon_c", nullable = false)
    private Integer lonC;

    /** Inclinación de los paneles (0 horizontal, 90 vertical). */
    @Column(nullable = false)
    private Integer angulo;

    /** Azimut: 0 sur, -90 este, 90 oeste. */
    @Column(nullable = false)
    private Integer azimut;

    /** Las 8760 horas del año tipo en kWh, separadas por comas. */
    @Lob
    @Column(nullable = false)
    private String datos;
}
