package api.aura.entity.mcas;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "mcas_politicas_retrocesion")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class McasPoliticaRetrocesion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    /** Clave estable para semillas y administración (V20260929_1). */
    @Column(unique = true, length = 100)
    private String codigo;
    @Column(name = "id_comercializadora", nullable = false)
    private Integer idComercializadora;
    @Column(name = "id_gestora")
    private Integer idGestora;
    // Los DEFAULT de columnDefinition replican los de V20260913_1 en el esquema que
    // genera Hibernate en los tests (H2); en MariaDB no se usan (ddl-auto=none).
    @Column(nullable = false, length = 50, columnDefinition = "VARCHAR(50) DEFAULT '1.0'")
    private String version;
    @Column(name = "vigencia_desde", nullable = false)
    private LocalDate vigenciaDesde;
    @Column(name = "vigencia_hasta")
    private LocalDate vigenciaHasta;
    @Column(name = "tipo_suministro", length = 5)
    private String tipoSuministro;
    @Column(length = 20)
    private String segmento;
    @Column(name = "tarifa_acceso", length = 20)
    private String tarifaAcceso;
    @Column(nullable = false, length = 30)
    private String metodo;
    @Column(name = "limite_importe_pagado", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean limiteImportePagado;
    @Column(name = "archivo_origen", length = 250)
    private String archivoOrigen;
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'BORRADOR'")
    private String estado;
    /** Texto para el operador (avisos del origen, unidades sin confirmar...). */
    @Column(length = 500)
    private String descripcion;
}
