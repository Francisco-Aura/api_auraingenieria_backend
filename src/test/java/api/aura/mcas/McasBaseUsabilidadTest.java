package api.aura.mcas;

import api.aura.dto.mcas.McasCampo;
import api.aura.dto.mcas.McasIncidenciaDTO;
import api.aura.dto.mcas.McasResultadoLoteDTO;
import api.aura.dto.mcas.McasResumenContratoDTO;
import api.aura.dto.mcas.McasVistaPreviaDTO;
import api.aura.entity.Contrato;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.repository.ContratoRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasPropuestaRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.service.mcas.engine.McasDatosRequeridosException;
import api.aura.service.mcas.engine.ResultadoCalculo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paso 0 del plan de usabilidad (30/09/2026): columnas nuevas, consultas, tipos
 * del motor y DTO que comparten los agentes. Cada prueba deshace sus datos
 * (transacción con rollback), así que no altera los recuentos de otras clases.
 */
@SpringBootTest
@Transactional
class McasBaseUsabilidadTest {

    private static final String MIGRACION = "V20260930_1_MCAS_Usabilidad.sql";

    @Autowired private McasConjuntoReglasRepository conjuntos;
    @Autowired private McasReglaComisionRepository reglas;
    @Autowired private McasValorRangoRepository rangos;
    @Autowired private McasPropuestaRepository propuestas;
    @Autowired private ContratoRepository contratos;
    @Autowired private ObjectMapper mapper;
    @PersistenceContext private EntityManager em;

    // ============================================================
    // Migración y entidades
    // ============================================================

    @Test
    void la_migracion_es_idempotente_y_anade_las_columnas_con_su_tipo() throws Exception {
        String sql = Files.readString(Path.of("src", "main", "resources", "BBDD", MIGRACION), StandardCharsets.UTF_8);

        assertThat(sql).contains("CÓMO EJECUTARLO").contains("information_schema.columns");
        Matcher addColumn = Pattern.compile("ADD\\s+COLUMN\\s+(?!IF\\s+NOT\\s+EXISTS)", Pattern.CASE_INSENSITIVE).matcher(sql);
        assertThat(addColumn.find()).as("todo ADD COLUMN lleva IF NOT EXISTS").isFalse();

        assertThat(columnasAnadidas(sql, "mcas_conjuntos_reglas")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "nombre", "VARCHAR(150)", "descripcion", "VARCHAR(1000)", "id_conjunto_origen", "INT"));
        assertThat(columnasAnadidas(sql, "mcas_reglas_comision")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "nombre", "VARCHAR(150)", "descripcion", "VARCHAR(1000)"));
    }

    @Test
    void conjunto_guarda_nombre_descripcion_y_origen_y_sale_en_camelCase() throws Exception {
        McasConjuntoReglas origen = conjunto("BASE-ORIGEN");
        McasConjuntoReglas copia = conjuntos.save(McasConjuntoReglas.builder()
                .codigo("BASE-COPIA-" + System.nanoTime()).idComercializadora(1)
                .vigenciaDesde(LocalDate.of(2026, 10, 1))
                .nombre("N".repeat(150)).descripcion("D".repeat(1000)).idConjuntoOrigen(origen.getId()).build());
        conjuntos.flush();
        em.clear();

        McasConjuntoReglas leido = conjuntos.findById(copia.getId()).orElseThrow();
        assertThat(leido.getNombre()).hasSize(150);
        assertThat(leido.getDescripcion()).hasSize(1000);
        assertThat(leido.getIdConjuntoOrigen()).isEqualTo(origen.getId());

        JsonNode json = mapper.readTree(mapper.writeValueAsString(leido));
        assertThat(json.get("nombre").asText()).hasSize(150);
        assertThat(json.get("descripcion").asText()).hasSize(1000);
        assertThat(json.get("idConjuntoOrigen").asInt()).isEqualTo(origen.getId());

        McasConjuntoReglas recibido = mapper.readValue(
                "{\"nombre\":\"Endesa 2026\",\"descripcion\":\"Tabla de marzo\",\"idConjuntoOrigen\":7}",
                McasConjuntoReglas.class);
        assertThat(recibido.getNombre()).isEqualTo("Endesa 2026");
        assertThat(recibido.getDescripcion()).isEqualTo("Tabla de marzo");
        assertThat(recibido.getIdConjuntoOrigen()).isEqualTo(7);
    }

    @Test
    void regla_guarda_nombre_y_descripcion_y_sale_en_camelCase() throws Exception {
        McasReglaComision regla = regla(conjunto("BASE-REGLA"), "R1");
        regla.setNombre("Iris · pyme luz · 44 % del FEE");
        regla.setDescripcion("E".repeat(1000));
        reglas.saveAndFlush(regla);
        em.clear();

        McasReglaComision leida = reglas.findById(regla.getId()).orElseThrow();
        assertThat(leida.getNombre()).isEqualTo("Iris · pyme luz · 44 % del FEE");
        assertThat(leida.getDescripcion()).hasSize(1000);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(leida));
        assertThat(json.get("nombre").asText()).isEqualTo("Iris · pyme luz · 44 % del FEE");
        assertThat(json.has("descripcion")).isTrue();
    }

    // ============================================================
    // Repositorios
    // ============================================================

    @Test
    void contarPorEstado_devuelve_estado_y_total_solo_de_los_estados_pedidos() {
        List<String> estados = List.of("PENDIENTE_VALIDACION", "RECHAZADO", "EN_EDICION");
        Map<String, Long> antes = recuento(estados);

        propuesta("PENDIENTE_VALIDACION", null);
        propuesta("PENDIENTE_VALIDACION", null);
        propuesta("RECHAZADO", null);
        propuesta("FINALIZADO_APROBADO", null);

        Map<String, Long> despues = recuento(estados);
        assertThat(despues.keySet()).isSubsetOf(estados);
        assertThat(despues.getOrDefault("PENDIENTE_VALIDACION", 0L) - antes.getOrDefault("PENDIENTE_VALIDACION", 0L))
                .isEqualTo(2L);
        assertThat(despues.getOrDefault("RECHAZADO", 0L) - antes.getOrDefault("RECHAZADO", 0L)).isEqualTo(1L);
        assertThat(despues.getOrDefault("EN_EDICION", 0L)).isEqualTo(antes.getOrDefault("EN_EDICION", 0L));
        assertThat(propuestas.contarPorEstado(List.of())).isEmpty();
    }

    @Test
    void existsByIdConjuntoReglas_indica_si_el_conjunto_tiene_propuestas() {
        McasConjuntoReglas usado = conjunto("BASE-USADO");
        McasConjuntoReglas libre = conjunto("BASE-LIBRE");
        propuesta("PENDIENTE_VALIDACION", usado.getId());

        assertThat(propuestas.existsByIdConjuntoReglas(usado.getId())).isTrue();
        assertThat(propuestas.existsByIdConjuntoReglas(libre.getId())).isFalse();
    }

    @Test
    void dimensiones_distintas_de_varias_reglas_en_una_consulta() {
        McasConjuntoReglas conjunto = conjunto("BASE-DIM");
        McasReglaComision matriz = regla(conjunto, "MATRIZ");
        McasReglaComision zona = regla(conjunto, "ZONA");
        McasReglaComision sinTabla = regla(conjunto, "FIJA");
        valor(matriz, "consumo_kwh", 1);
        valor(matriz, "potencia_kw", 1);
        valor(matriz, "consumo_kwh", 2);
        valor(matriz, "potencia_kw", 2);
        valor(zona, "zona_comision", 1);

        List<McasValorRangoRepository.DimensionRegla> filas =
                rangos.dimensionesPorReglas(List.of(matriz.getId(), zona.getId(), sinTabla.getId()));

        assertThat(filas).extracting(f -> f.getIdRegla() + ":" + f.getDimension()).containsExactly(
                matriz.getId() + ":consumo_kwh", matriz.getId() + ":potencia_kw", zona.getId() + ":zona_comision");
        assertThat(rangos.dimensionesPorReglas(List.of())).isEmpty();
    }

    @Test
    void contratos_por_numero_exacto_limitados_a_10_y_el_mas_reciente_primero() {
        String numero = "BASE-" + System.nanoTime();
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) ids.add(contrato(numero).getId());
        contrato(numero + "-OTRO");

        List<Contrato> encontrados = contratos.findTop10ByContratoOrderByIdDesc(numero);

        assertThat(encontrados).hasSize(10).allMatch(c -> numero.equals(c.getContrato()));
        assertThat(encontrados.get(0).getId()).isEqualTo(ids.get(11));
    }

    @Test
    void contratos_que_empiezan_por_limitados_a_10_sin_comodines() {
        String prefijo = "PFX" + System.nanoTime();
        for (int i = 0; i < 11; i++) contrato(prefijo + "-" + i);
        contrato("X" + prefijo);
        contrato(prefijo.substring(0, 5) + "_Z");

        List<Contrato> encontrados = contratos.findTop10ByContratoStartingWithOrderByIdDesc(prefijo);
        assertThat(encontrados).hasSize(10).allMatch(c -> c.getContrato().startsWith(prefijo));
        assertThat(encontrados.get(0).getContrato()).isEqualTo(prefijo + "-10");

        // «_» y «%» se buscan literalmente, no como comodines de LIKE
        String conGuion = prefijo.substring(0, 5) + "_";
        assertThat(contratos.findTop10ByContratoStartingWithOrderByIdDesc(conGuion))
                .extracting(Contrato::getContrato).containsExactly(conGuion + "Z");
        assertThat(contratos.findTop10ByContratoStartingWithOrderByIdDesc("%" + prefijo)).isEmpty();
    }

    // ============================================================
    // Motor
    // ============================================================

    @Test
    void datos_requeridos_lleva_los_campos_y_conserva_el_constructor_anterior() {
        McasDatosRequeridosException conCampos =
                new McasDatosRequeridosException("Faltan datos", List.of("consumo_kwh", "fee"));
        assertThat(conCampos.getMessage()).isEqualTo("Faltan datos");
        assertThat(conCampos.getCampos()).containsExactly("consumo_kwh", "fee");

        McasDatosRequeridosException anterior = new McasDatosRequeridosException("RATE_PER_MWH: consumo_anual requerido");
        assertThat(anterior.getMessage()).isEqualTo("RATE_PER_MWH: consumo_anual requerido");
        assertThat(anterior.getCampos()).isEmpty();
        assertThat(new McasDatosRequeridosException("x", null).getCampos()).isEmpty();
    }

    @Test
    void resultado_calculo_trae_campos_faltantes_vacios_por_defecto() {
        assertThat(ResultadoCalculo.builder().build().getCamposFaltantes()).isNotNull().isEmpty();
        assertThat(ResultadoCalculo.builder().camposFaltantes(List.of("fee")).build().getCamposFaltantes())
                .containsExactly("fee");
    }

    // ============================================================
    // DTO: campos exactos del contrato de API
    // ============================================================

    @Test
    void resumen_de_contrato_con_los_campos_del_contrato_de_api() throws Exception {
        McasResumenContratoDTO dto = McasResumenContratoDTO.builder()
                .moduloActivo(true).hayPropuesta(true).idPropuesta(12L).estado("PENDIENTE_VALIDACION")
                .importe(new BigDecimal("571.26")).numeroPagos(1).queFalta(List.of("FEE del contrato"))
                .mensaje("Comisión pendiente de aprobar: 571,26 € en 1 pago.")
                .actualizadoEn(LocalDateTime.of(2026, 9, 30, 10, 15)).servicioCreado(false).build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(campos(json)).containsExactlyInAnyOrder("moduloActivo", "hayPropuesta", "idPropuesta", "estado",
                "importe", "numeroPagos", "queFalta", "mensaje", "actualizadoEn", "servicioCreado");
        assertThat(json.get("moduloActivo").asBoolean()).isTrue();
        assertThat(json.get("importe").decimalValue()).isEqualByComparingTo("571.26");
        assertThat(json.get("actualizadoEn").asText()).isEqualTo("2026-09-30T10:15:00");
        assertThat(json.get("servicioCreado").isBoolean()).isTrue();
        assertThat(McasResumenContratoDTO.builder().build().getQueFalta()).isEmpty();
    }

    @Test
    void vista_previa_con_los_campos_del_contrato_de_api() throws Exception {
        McasVistaPreviaDTO dto = McasVistaPreviaDTO.builder()
                .estado(McasVistaPreviaDTO.Estado.CALCULADO).mensaje("Comisión estimada: 571,26 € en 1 pago.")
                .importe(new BigDecimal("571.26")).numeroPagos(1)
                .calendario(List.of(McasVistaPreviaDTO.Cuota.builder()
                        .numero(1).fecha(LocalDate.of(2026, 10, 1)).importe(new BigDecimal("571.26")).build()))
                .regla("Iris · pyme luz · 44 % del FEE").avisos(List.of())
                .campos(List.of(McasVistaPreviaDTO.Campo.builder()
                        .clave(McasCampo.FEE).etiqueta("FEE del contrato").falta(true)
                        .valores(List.of()).usadoPor(List.of("Iris · pyme luz")).build()))
                .build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(campos(json)).containsExactlyInAnyOrder("estado", "mensaje", "importe", "numeroPagos",
                "calendario", "regla", "avisos", "campos");
        assertThat(json.get("estado").asText()).isEqualTo("CALCULADO");
        assertThat(campos(json.get("calendario").get(0))).containsExactlyInAnyOrder("numero", "fecha", "importe");
        assertThat(json.get("calendario").get(0).get("fecha").asText()).isEqualTo("2026-10-01");
        JsonNode campo = json.get("campos").get(0);
        assertThat(campos(campo)).containsExactlyInAnyOrder("clave", "etiqueta", "falta", "valores", "usadoPor");
        assertThat(campo.get("clave").asText()).isEqualTo("FEE");
        assertThat(campo.get("falta").asBoolean()).isTrue();

        assertThat(McasVistaPreviaDTO.Estado.values()).extracting(Enum::name).containsExactly(
                "CALCULADO", "REQUIERE_DATOS", "REQUIERE_REVISION_MANUAL", "NO_COMISIONABLE", "ERROR_CALCULO",
                "SIN_REGLAS", "DESACTIVADO", "YA_TIENE_COMISION", "SIN_TIPO_PRECIO");
        McasVistaPreviaDTO vacio = McasVistaPreviaDTO.builder().build();
        assertThat(vacio.getCalendario()).isEmpty();
        assertThat(vacio.getAvisos()).isEmpty();
        assertThat(vacio.getCampos()).isEmpty();
    }

    @Test
    void enum_McasCampo_con_las_claves_del_contrato_de_api_y_su_etiqueta() {
        assertThat(McasCampo.values()).extracting(Enum::name).containsExactly("TIPO_PRECIO", "TIPO_SUMINISTRO",
                "SEGMENTO", "ES_RENOVACION", "TIPO_RENOVACION", "ALTA_NUEVA", "AMBITO", "ZONA_COMISION",
                "TARIFA_ACCESO", "PRODUCTO", "CONSUMO", "POTENCIA", "FEE", "GESTORA");
        assertThat(McasCampo.FEE.getEtiqueta()).isEqualTo("FEE del contrato");
        assertThat(McasCampo.desdeCodigo("consumo_mwh")).contains(McasCampo.CONSUMO);
        assertThat(McasCampo.desdeCodigo("codigo_producto")).contains(McasCampo.PRODUCTO);
        assertThat(McasCampo.desdeCodigo("tipo_precio_FIJO_o_INDEXADO")).contains(McasCampo.TIPO_PRECIO);
        assertThat(McasCampo.desdeCodigo("Zona_Comision")).contains(McasCampo.ZONA_COMISION);
        assertThat(McasCampo.desdeCodigo("numero_contrato")).isEmpty();
        assertThat(McasCampo.desdeCodigo(null)).isEmpty();
    }

    @Test
    void incidencia_y_resultado_de_lote_con_los_campos_del_contrato_de_api() throws Exception {
        JsonNode incidencia = mapper.readTree(mapper.writeValueAsString(McasIncidenciaDTO.builder()
                .idRegla(7).regla("Endesa · pyme luz 3.0TD").orden(12).mensaje("La celda 12 no tiene importe").build()));
        assertThat(campos(incidencia)).containsExactlyInAnyOrder("idRegla", "regla", "orden", "mensaje");

        JsonNode ok = mapper.readTree(mapper.writeValueAsString(
                McasResultadoLoteDTO.ok(1L, "FINALIZADO_APROBADO", "Servicio #55 creado")));
        assertThat(campos(ok)).containsExactlyInAnyOrder("id", "ok", "estado", "mensaje");
        assertThat(ok.get("ok").asBoolean()).isTrue();
        assertThat(ok.get("id").asLong()).isEqualTo(1L);

        JsonNode error = mapper.readTree(mapper.writeValueAsString(
                McasResultadoLoteDTO.error(2L, "DESACTUALIZADO", "El contrato ha cambiado")));
        assertThat(error.get("ok").asBoolean()).isFalse();
        assertThat(error.get("estado").asText()).isEqualTo("DESACTUALIZADO");
    }

    // ============================================================
    // Auxiliares
    // ============================================================

    /** Columnas «nombre → TIPO» de los ALTER TABLE ... ADD COLUMN IF NOT EXISTS de una tabla. */
    private Map<String, String> columnasAnadidas(String sql, String tabla) {
        Matcher alter = Pattern.compile("ALTER\\s+TABLE\\s+" + tabla + "\\b(.*?);",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql);
        Map<String, String> columnas = new HashMap<>();
        while (alter.find()) {
            Matcher col = Pattern.compile("ADD\\s+COLUMN\\s+IF\\s+NOT\\s+EXISTS\\s+(\\w+)\\s+(\\w+(?:\\(\\d+\\))?)",
                    Pattern.CASE_INSENSITIVE).matcher(alter.group(1));
            while (col.find()) columnas.put(col.group(1), col.group(2).toUpperCase());
        }
        return columnas;
    }

    private Map<String, Long> recuento(Collection<String> estados) {
        return propuestas.contarPorEstado(estados).stream()
                .collect(Collectors.toMap(f -> (String) f[0], f -> ((Number) f[1]).longValue()));
    }

    private Set<String> campos(JsonNode json) {
        Set<String> nombres = new java.util.HashSet<>();
        json.fieldNames().forEachRemaining(nombres::add);
        return nombres;
    }

    private McasConjuntoReglas conjunto(String prefijo) {
        return conjuntos.save(McasConjuntoReglas.builder().codigo(prefijo + "-" + System.nanoTime())
                .idComercializadora(1).vigenciaDesde(LocalDate.of(2026, 1, 1)).build());
    }

    private McasReglaComision regla(McasConjuntoReglas conjunto, String codigo) {
        return reglas.save(McasReglaComision.builder().idConjuntoReglas(conjunto.getId())
                .codigo(codigo).metodoCalculo("MATRIX_LOOKUP").build());
    }

    private void valor(McasReglaComision regla, String dimension, int orden) {
        rangos.save(McasValorRango.builder().idRegla(regla.getId()).dimension(dimension)
                .valorMin(BigDecimal.ZERO).valorResultado(BigDecimal.TEN).orden(orden).build());
    }

    private McasPropuesta propuesta(String estado, Integer idConjunto) {
        return propuestas.save(McasPropuesta.builder().idContrato(-1).tipoEvento("NUEVO")
                .claveIdempotencia("BASE-" + System.nanoTime()).estado(estado).idConjuntoReglas(idConjunto).build());
    }

    private Contrato contrato(String numero) {
        return contratos.save(Contrato.builder().contrato(numero).build());
    }
}
