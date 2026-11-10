package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "pl_equipos")
@Getter
@Setter
@NoArgsConstructor
public class pl_Equipo {

    @Id
    @Column(length = 64)
    private String id;

    /** Orden dentro del catálogo. */
    @Column(nullable = false)
    private int posicion;

    /** Pestaña de la calculadora (modulos, inversores…). */
    @Column(nullable = false, length = 40)
    private String categoria;

    /** Partida del presupuesto a la que suma (columna «group» del Planificador actual). */
    @Column(nullable = false, length = 40)
    private String partida;

    @Column(nullable = false)
    private String marca = "";

    @Column(nullable = false)
    private String modelo = "";

    @Column(nullable = false)
    private String proveedor = "";

    @Column(nullable = false, length = 40)
    private String unidad = "ud";

    @Column(name = "precio_unitario", nullable = false)
    private double precioUnitario;

    @Column(name = "por_modulo", nullable = false)
    private boolean porModulo;

    @Column(name = "cantidad_defecto", nullable = false)
    private double cantidadDefecto = 1.0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "atributos", nullable = false)
    private Map<String, Object> atributos = new LinkedHashMap<>();

    @Column(columnDefinition = "TEXT", nullable = false)
    private String notas = "";

    /** Dato de prueba, no sale del Excel. */
    @Column(nullable = false)
    private boolean muestra;

    @Column(nullable = false)
    private double stock;

    @Column(name = "stock_minimo", nullable = false)
    private double stockMinimo;

    /** Ficha técnica en Dropbox: ruta relativa «MARCA/archivo.pdf»; vacío = sin ficha. */
    @Column(name = "ficha_tecnica", nullable = false, length = 500)
    private String fichaTecnica = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ofertas", nullable = false)
    private List<Map<String, Object>> ofertas = new ArrayList<>();

    @Column(name = "precio_revisado_el")
    private LocalDate precioRevisadoEl;
}
