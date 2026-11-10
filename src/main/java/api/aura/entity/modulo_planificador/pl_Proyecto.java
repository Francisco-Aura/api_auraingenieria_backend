package api.aura.entity.modulo_planificador;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Proyecto: contrato firmado en marcha, con sus tareas, su contrato de referencia y su presupuesto inicial. */
@Entity
@Table(name = "pl_proyectos")
@Getter
@Setter
@NoArgsConstructor
public class pl_Proyecto {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "propietario_id", nullable = false, length = 64)
    private String propietarioId = "local";

    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String descripcion = "";

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    /** Guías y fichas del proyecto (copiadas de la plantilla): [{"title","body"}]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documentos", nullable = false)
    private List<Map<String, Object>> documentos = new ArrayList<>();

    /** Contrato inicial de referencia (importe, equipos, potencia); se normaliza en la Fase 5. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "linea_base", nullable = false)
    private Map<String, Object> lineaBase = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cambios", nullable = false)
    private List<Map<String, Object>> cambios = new ArrayList<>();

    /** Presupuesto inicial (foto de la tabla de costes al firmar el contrato). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "presupuesto", nullable = false)
    private Map<String, Object> presupuesto = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "partes_contrato", nullable = false)
    private Map<String, Object> partesContrato = new LinkedHashMap<>();

    @Column(name = "contrato_generado_el")
    private LocalDateTime contratoGeneradoEl;

    @Column(name = "archivo_contrato", nullable = false, length = 500)
    private String archivoContrato = "";

    /** Forma de pago: {"mode","stages":[{"label","note","percent"}]}; vacío = la habitual. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_pagos", nullable = false)
    private Map<String, Object> planPagos = new LinkedHashMap<>();

    @Column(name = "creado_el", nullable = false)
    private LocalDateTime creadoEl;

    /** Carpeta del proyecto en Dropbox (ruta completa). Vacío = sin carpeta. */
    @Column(name = "ruta_dropbox", nullable = false, length = 500)
    private String rutaDropbox = "";

    @Column(name = "eliminado_el")
    private LocalDateTime eliminadoEl;
}
