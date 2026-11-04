package api.aura.mcas;

import api.aura.controller.mcas.McasPropuestasController;
import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.entity.Servicio;
import api.aura.entity.mcas.McasAuditoria;
import api.aura.entity.mcas.McasCalendarioPago;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasPoliticaRetrocesion;
import api.aura.entity.mcas.McasProductoComercial;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasTramoRetrocesion;
import api.aura.entity.mcas.McasValorRango;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.entity.tablas_maestras.TBGestoras;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.entity.tablas_maestras.TBProductos;
import api.aura.entity.tablas_maestras.TBTarifasLuz;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.ServicioRepository;
import api.aura.repository.ServiciosPagosRepository;
import api.aura.repository.mcas.McasAuditoriaRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasPoliticaRetrocesionRepository;
import api.aura.repository.mcas.McasProductoComercialRepository;
import api.aura.repository.mcas.McasPropuestaRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasTramoRetrocesionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBGestorasRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import api.aura.repository.tablas_maestras.TBProductosRepository;
import api.aura.repository.tablas_maestras.TBTarifasLuzRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.McasContratoLocator;
import api.aura.service.mcas.McasLoteService;
import api.aura.service.mcas.McasProposalService;
import api.aura.service.mcas.McasResumenContratoService;
import api.aura.service.mcas.McasRetrocesionPolicyService;
import api.aura.dto.mcas.McasPropuestaDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Conformidad con lo pedido por el cliente (plan del 29/09/2026): el operador
 * solo revisa y aprueba, y ninguna comisión se queda sin salida.
 *
 * <p>Base de datos H2 y transacciones reales, sin mocks, como
 * {@link McasFlujoIntegrationTest}. Cada prueba crea su propia comercializadora
 * con un conjunto PUBLICADO: así las reglas de una prueba no se pisan con las de
 * otra ni con las de otras clases que comparten la misma base de datos.</p>
 */
@SpringBootTest
class McasConformidadClienteTest {

    @Autowired private McasProposalService mcas;
    @Autowired private ModuloConfiguracionService modulos;
    @Autowired private ClienteRepository clientes;
    @Autowired private ContratoRepository contratos;
    @Autowired private TBComercializadorasRepository comercializadoras;
    @Autowired private McasConjuntoReglasRepository conjuntos;
    @Autowired private McasReglaComisionRepository reglas;
    @Autowired private McasValorRangoRepository rangos;
    @Autowired private TBTarifasLuzRepository tarifasLuz;
    @Autowired private TBProductosRepository productosServicio;
    @Autowired private TBPagoPlazosRepository pagoPlazos;
    @Autowired private ServicioRepository servicios;
    @Autowired private ServiciosPagosRepository serviciosPagos;
    @Autowired private McasAuditoriaRepository auditorias;
    @Autowired private McasPropuestaRepository propuestas;
    @Autowired private TBGestorasRepository gestoras;
    @Autowired private McasProductoComercialRepository productosComerciales;
    @Autowired private McasPoliticaRetrocesionRepository politicas;
    @Autowired private McasTramoRetrocesionRepository tramos;
    @Autowired private McasPropuestasController propuestasController;
    @Autowired private ObjectMapper mapper;

    @BeforeEach
    void datosBase() {
        modulos.activar(McasProposalService.FLAG_ENABLED);
        modulos.activar(McasProposalService.FLAG_FIJO);
        if (productosServicio.findFirstByNombreContainingIgnoreCaseOrderByIdAsc("switching").isEmpty())
            productosServicio.save(TBProductos.builder().nombre("SWITCHING").build());
        for (int cuotas = 1; cuotas <= 2; cuotas++) {
            int n = cuotas;
            if (pagoPlazos.findFirstByNumeroCuotas(n).isEmpty())
                pagoPlazos.save(TBPagoPlazos.builder().nombre(n + " PLAZOS").numeroCuotas(n).build());
        }
    }

    // ============================================================
    // T1.1 · Motor
    // ============================================================

    @Test
    void fee_por_debajo_del_minimo_va_a_revision_manual_y_no_a_error() {
        Escenario e = escenario("COMERCIALIZADORA FEE MINIMO");
        e.regla("IT_FEE_44", "FEE_PERCENTAGE", "{\"tipo_suministro\":\"LUZ\"}",
                "{\"porcentaje_aplicacion_fee\":0.44,\"fee_minimo_eur_mwh\":5}");

        McasPropuesta p = calcular(e, c -> c.mcasFeeEurMwh(new BigDecimal("4")).mcasFeeUnidad("EUR_MWH"));

        assertThat(p.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(p.getAdvertenciasJson()).contains("mínimo").contains("manualmente");
    }

    @Test
    void valor_fuera_de_la_tabla_va_a_revision_manual() {
        Escenario e = escenario("COMERCIALIZADORA FUERA DE TABLA");
        McasReglaComision matriz = e.regla("IT_MATRIZ_3_0TD", "MATRIX_LOOKUP", "{\"tipo_suministro\":\"LUZ\"}", null);
        rango(matriz, 1, "consumo_kwh", "0", false, "5000", true, "100");
        rango(matriz, 1, "potencia_kw", "15", false, "30", true, "100");

        McasPropuesta p = calcular(e, c -> c.consumoAnual(4_000d).potencia1(10.0));

        assertThat(p.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        // Al usuario, sin códigos; la regla que lo decidió queda en la traza.
        assertThat(p.getAdvertenciasJson()).contains("fuera de la tabla").doesNotContain("IT_MATRIZ_3_0TD");
        assertThat(p.getTrazaCalculoJson()).contains("IT_MATRIZ_3_0TD");
    }

    @Test
    void la_tarifa_de_acceso_se_compara_normalizada() {
        Escenario e = escenario("COMERCIALIZADORA TARIFA");
        e.regla("IT_TARIFA_2_0TD", "FIXED_AMOUNT", "{\"tarifa_acceso\":\"2.0TD\"}", "{\"importe\":50}");
        TBTarifasLuz tarifa = tarifasLuz.save(TBTarifasLuz.builder().nombre("2.0 TD").build());

        McasPropuesta p = calcular(e, c -> c.idTarifaLuz(tarifa.getId()));

        assertThat(p.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        assertThat(p.getImporteCalculado()).isEqualByComparingTo("50.00");
    }

    @Test
    void sin_potencia_de_calculo_usa_la_mayor_potencia_contratada() {
        Escenario e = escenario("COMERCIALIZADORA POTENCIA");
        McasReglaComision porPotencia = e.regla("IT_POTENCIA", "RANGE_LOOKUP", "{\"tipo_suministro\":\"LUZ\"}", null);
        rango(porPotencia, 1, "potencia_kw", "0", true, "10", true, "88");
        rango(porPotencia, 2, "potencia_kw", "10", false, "15", true, "104.80");

        McasPropuesta p = calcular(e, c -> c.potencia1(12.5).potencia6(9.0));

        assertThat(p.getImporteCalculado()).isEqualByComparingTo("104.80");
        assertThat(p.getTrazaCalculoJson())
                .contains("\"potencia_origen\":\"MAYOR_CONTRATADA\"")
                .contains("\"potencia_calculo_kw\":\"12.5\"");

        // Una potencia de cálculo antigua guardada en el contrato ya no cuenta: siempre la mayor.
        McasPropuesta q = calcular(e, c -> c.potencia1(12.5).mcasPotenciaCalculo(new BigDecimal("8")));
        assertThat(q.getImporteCalculado()).isEqualByComparingTo("104.80");
        assertThat(q.getTrazaCalculoJson()).contains("\"potencia_origen\":\"MAYOR_CONTRATADA\"");
    }

    @Test
    void la_traza_guarda_regla_version_y_porcentaje() {
        Escenario e = escenario("COMERCIALIZADORA TRAZA");
        McasReglaComision regla = e.regla("IT_FEE_44", "FEE_PERCENTAGE", "{\"tipo_suministro\":\"LUZ\"}",
                "{\"porcentaje_aplicacion_fee\":0.44,\"fee_minimo_eur_mwh\":5}");
        TBTarifasLuz tarifa = tarifasLuz.save(TBTarifasLuz.builder().nombre("3.0TD").build());

        McasPropuesta p = calcular(e, c -> c.idTarifaLuz(tarifa.getId())
                .mcasFeeEurMwh(new BigDecimal("7")).mcasFeeUnidad("EUR_MWH"));

        assertThat(p.getImporteCalculado()).isEqualByComparingTo("571.26");       // 185,475 MWh × 7 × 44 %
        assertThat(p.getTrazaCalculoJson())
                .contains("\"regla_id\":" + regla.getId())
                .contains("\"regla\":\"IT_FEE_44\"")
                .contains("\"conjunto\":\"" + e.conjunto.getCodigo() + "\"")
                .contains("\"conjunto_version\":\"2\"")
                .contains("\"porcentaje_aplicacion_fee\":\"0.44")
                .contains("\"tarifa_acceso\":\"3.0TD\"");
    }

    @Test
    void la_revision_manual_por_regla_deja_en_la_traza_que_regla_la_envio() {
        Escenario e = escenario("COMERCIALIZADORA MANUAL TRAZA");
        e.reglaManual("IT_RENOVACION_MANUAL", "{\"es_renovacion\":true}", "Renovación: importe manual");

        McasPropuesta p = calcular(e, c -> c.mcasEsRenovacion(true).mcasTipoRenovacion("FIRMADA"));

        assertThat(p.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(p.getIdConjuntoReglas()).isEqualTo(e.conjunto.getId());
        assertThat(p.getTrazaCalculoJson())
                .contains("\"revision_manual\":\"Renovación: importe manual\"")
                .contains("\"regla\":\"IT_RENOVACION_MANUAL\"")
                .contains("\"metodo\":\"MANUAL\"");
    }

    // ============================================================
    // T1.2 · Flujo: rechazar → editar, completar manual, servicio con regla
    // ============================================================

    @Test
    void rechazar_deja_la_propuesta_en_edicion_y_finalizar_crea_el_servicio() {
        McasPropuesta p = propuestaPendiente();

        McasPropuesta r = mcas.rechazar(p.getId(), "Importe pactado distinto");

        assertThat(r.getEstado()).isEqualTo("EN_EDICION");
        assertThat(r.getMotivoRechazo()).isEqualTo("Importe pactado distinto");
        assertThat(auditoria(p.getId())).extracting(McasAuditoria::getTipoEvento)
                .containsSubsequence("PROPUESTA_RECHAZADA", "EDICION_ABIERTA");
        assertThat(auditoria(p.getId())).filteredOn(a -> "EDICION_ABIERTA".equals(a.getTipoEvento()))
                .singleElement().satisfies(a -> {
                    assertThat(a.getEstadoAnterior()).isEqualTo("RECHAZADO");
                    assertThat(a.getDatosJson()).contains("\"origen\":\"RECHAZADO\"");
                });

        McasPropuesta f = mcas.finalizarEditado(p.getId(), new BigDecimal("150"), 2, null, "Acuerdo comercial");

        assertThat(f.getEstado()).isEqualTo("FINALIZADO_EDITADO");
        assertThat(f.getImporteCalculado()).isEqualByComparingTo("120.00");     // el automático no se toca
        Servicio s = servicios.findById(f.getIdServicioCreado()).orElseThrow();
        assertThat(s.getImporte()).isEqualTo(150.0);
        assertThat(serviciosPagos.findByServicioId(s.getId()).orElseThrow().getSegundoPagoImporte()).isEqualTo(75.0);
    }

    @Test
    void una_propuesta_en_revision_manual_se_completa_a_mano_y_crea_el_servicio() {
        McasPropuesta p = propuestaEnRevisionManual();

        McasPropuesta editada = mcas.abrirEdicion(p.getId());
        assertThat(editada.getEstado()).isEqualTo("EN_EDICION");
        assertThat(auditoria(p.getId())).filteredOn(a -> "EDICION_ABIERTA".equals(a.getTipoEvento()))
                .singleElement().satisfies(a ->
                        assertThat(a.getDatosJson()).contains("\"origen\":\"REQUIERE_REVISION_MANUAL\""));

        McasPropuesta f = mcas.finalizarEditado(p.getId(), new BigDecimal("95.50"), null, null, "Importe de la comercializadora");

        assertThat(f.getEstado()).isEqualTo("FINALIZADO_EDITADO");
        assertThat(f.getImporteCalculado()).isNull();
        assertThat(f.getImporteDefinitivo()).isEqualByComparingTo("95.50");
        assertThat(f.getNumeroPagos()).isEqualTo(1);
        Servicio s = servicios.findById(f.getIdServicioCreado()).orElseThrow();
        assertThat(s.getImporte()).isEqualTo(95.5);
        assertThat(s.getFechaInicioServicio()).isNotNull();
        assertThat(s.getObservaciones()).contains("Importe introducido manualmente");
        assertThat(mcas.obtenerLineas(p.getId())).singleElement().satisfies(l -> {
            assertThat(l.getTipoLinea()).isEqualTo("AJUSTE");
            assertThat(l.getDescripcion()).isEqualTo("Importe introducido manualmente");
            assertThat(l.getImporteCalculado()).isEqualByComparingTo("0");
            assertThat(l.getImporteDefinitivo()).isEqualByComparingTo("95.50");
        });
        assertThat(mcas.obtenerCalendario(p.getId())).singleElement()
                .satisfies(c -> assertThat(c.getImporte()).isEqualByComparingTo("95.50"));
    }

    @Test
    void una_propuesta_sin_calculo_usa_la_fecha_y_los_pagos_del_contrato() {
        // Sin tipo de suministro la propuesta se crea en REQUIERE_DATOS sin pasar por
        // el motor: no tiene fecha de primera aplicación ni número de pagos.
        Escenario e = escenario("COMERCIALIZADORA SIN CALCULO");
        McasPropuesta p = calcular(e, c -> c.mcasTipoSuministro(null).mcasNumeroPagos(2));
        assertThat(p.getEstado()).isEqualTo("REQUIERE_DATOS");
        assertThat(p.getFechaPrimeraAplicacion()).isNull();
        assertThat(p.getNumeroPagos()).isEqualTo(2);        // «Completar manualmente» precarga los del contrato

        mcas.abrirEdicion(p.getId());
        McasPropuesta f = mcas.finalizarEditado(p.getId(), new BigDecimal("80"), null, null, "Datos revisados a mano");

        assertThat(f.getFechaPrimeraAplicacion()).isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(f.getNumeroPagos()).isEqualTo(2);
        assertThat(mcas.obtenerCalendario(p.getId())).extracting(McasCalendarioPago::getFechaPago)
                .containsExactly(LocalDate.of(2026, 3, 16), LocalDate.of(2026, 4, 16));
        Servicio s = servicios.findById(f.getIdServicioCreado()).orElseThrow();
        assertThat(s.getFechaInicioServicio().toString()).isEqualTo("2026-03-16");
    }

    @Test
    void no_se_completa_a_mano_si_el_contrato_ya_tiene_comision_aprobada() throws Exception {
        McasPropuesta p = propuestaPendiente();
        McasPropuesta aprobada = mcas.aprobar(p.getId());
        Contrato cambio = contratos.findById(p.getIdContrato()).orElseThrow();
        cambio.setConsumoAnual(70_000d);
        contratos.save(cambio);
        McasPropuesta nueva = mcas.triggerDesdeContrato(cambio.getId());
        assertThat(nueva.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");

        assertThat(propuestaDelListado(nueva.getId()).path("admiteEdicionManual").asBoolean(true)).isFalse();
        assertThat(detalle(nueva.getId()).path("admiteEdicionManual").asBoolean(true)).isFalse();
        assertThatThrownBy(() -> mcas.abrirEdicion(nueva.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya tiene una comisión aprobada")
                .hasMessageContaining("servicio #" + aprobada.getIdServicioCreado());
        assertThat(propuestas.findById(nueva.getId()).orElseThrow().getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");

        // Si el servicio de aquella comisión se da de baja, ya se puede completar a mano.
        Servicio servicio = servicios.findById(aprobada.getIdServicioCreado()).orElseThrow();
        servicio.setFechaBaja(Date.valueOf("2026-06-01"));
        servicios.save(servicio);
        assertThat(propuestaDelListado(nueva.getId()).path("admiteEdicionManual").asBoolean(false)).isTrue();
        assertThat(mcas.abrirEdicion(nueva.getId()).getEstado()).isEqualTo("EN_EDICION");
    }

    @Test
    void no_se_crea_un_servicio_sin_fecha_de_inicio_ni_comercializadora() {
        Escenario e = escenario("COMERCIALIZADORA SIN FECHA");
        long serviciosAntes = servicios.count();
        for (Consumer<Contrato.ContratoBuilder> falta : List.<Consumer<Contrato.ContratoBuilder>>of(
                c -> c.fechaInicio(null), c -> c.idComercializadoraActual(null))) {
            McasPropuesta p = calcular(e, falta);
            assertThat(p.getEstado()).isEqualTo("REQUIERE_DATOS");
            mcas.abrirEdicion(p.getId());

            assertThatThrownBy(() -> mcas.finalizarEditado(p.getId(), new BigDecimal("80"), 1, null, "Importe manual"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Complete la fecha de inicio y la comercializadora del contrato antes de crear el servicio.");
            assertThat(propuestas.findById(p.getId()).orElseThrow().getEstado()).isEqualTo("EN_EDICION");
        }
        assertThat(servicios.count()).isEqualTo(serviciosAntes);
    }

    @Test
    void completar_a_mano_sin_importe_se_rechaza_con_mensaje_claro() {
        McasPropuesta p = propuestaEnRevisionManual();
        mcas.abrirEdicion(p.getId());

        assertThatThrownBy(() -> mcas.finalizarEditado(p.getId(), null, 1, null, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Indique el importe definitivo");
    }

    @Test
    void no_se_puede_editar_una_propuesta_finalizada_ni_pendiente() {
        McasPropuesta p = propuestaPendiente();

        assertThatThrownBy(() -> mcas.abrirEdicion(p.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rechazada o pendiente de revisión manual");

        mcas.aprobar(p.getId());
        assertThatThrownBy(() -> mcas.abrirEdicion(p.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void el_servicio_creado_indica_regla_y_conjunto() {
        McasPropuesta p = propuestaPendiente();

        McasPropuesta a = mcas.aprobar(p.getId());

        String conjunto = conjuntos.findById(p.getIdConjuntoReglas()).orElseThrow().getCodigo();
        // Sin nombre legible salen los códigos; con nombre, el nombre (ver McasArranqueVacioTest)
        assertThat(servicios.findById(a.getIdServicioCreado()).orElseThrow().getObservaciones())
                .startsWith("Comisión generada por MCAS. Propuesta #" + p.getId() + ".")
                .contains("Regla «IT_IMPORTE_FIJO» de la tabla de comisiones «" + conjunto + "» (versión 2)")
                .endsWith("Aprobada por: " + a.getAprobadoPor() + ".");
    }

    // ============================================================
    // T1.3 · DTO enriquecido y listado sin N+1
    // ============================================================

    @Test
    void el_listado_trae_cliente_comercializadora_fee_porcentaje_y_regla() throws Exception {
        Escenario e = escenario("COMERCIALIZADORA TEST");
        e.regla("IT_FEE_44", "FEE_PERCENTAGE", "{\"tipo_suministro\":\"LUZ\"}",
                "{\"porcentaje_aplicacion_fee\":0.44,\"fee_minimo_eur_mwh\":5}");
        TBGestoras gestora = gestoras.save(TBGestoras.builder().nombre("GESTORA TEST").build());
        McasProductoComercial producto = productosComerciales.save(McasProductoComercial.builder()
                .idComercializadora(e.idComercializadora).codigo("IT_PLAN").nombre("Plan pyme test")
                .tipoPrecio("FIJO").tipoSuministro("LUZ").activo(true).build());
        TBTarifasLuz tarifa = tarifasLuz.save(TBTarifasLuz.builder().nombre("3.0TD").build());
        McasPropuesta p = calcular(e, c -> c.mcasFeeEurMwh(new BigDecimal("7")).mcasFeeUnidad("EUR_MWH")
                .mcasIdGestora(gestora.getId()).mcasIdProductoComercial(producto.getId())
                .idTarifaLuz(tarifa.getId()).mcasSegmento("PYME").potencia1(40.0));
        assertThat(p.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        Contrato contrato = contratos.findById(p.getIdContrato()).orElseThrow();

        JsonNode dto = propuestaDelListado(p.getId());

        assertThat(dto.path("numeroContrato").asText()).isEqualTo(contrato.getContrato());
        assertThat(dto.path("idCliente").asInt()).isEqualTo(contrato.getCliente().getId());
        assertThat(dto.path("nombreCliente").asText()).isEqualTo("Cliente Test");
        assertThat(dto.path("idComercializadora").asInt()).isEqualTo(e.idComercializadora);
        assertThat(dto.path("nombreComercializadora").asText()).isEqualTo("COMERCIALIZADORA TEST");
        assertThat(dto.path("idGestora").asInt()).isEqualTo(gestora.getId());
        assertThat(dto.path("nombreGestora").asText()).isEqualTo("GESTORA TEST");
        assertThat(dto.path("tipoPrecio").asText()).isEqualTo("FIJO");
        assertThat(dto.path("tipoSuministro").asText()).isEqualTo("LUZ");
        assertThat(dto.path("segmento").asText()).isEqualTo("PYME");
        assertThat(dto.path("esRenovacion").asBoolean(true)).isFalse();
        assertThat(dto.path("tarifaAcceso").asText()).isEqualTo("3.0TD");
        assertThat(dto.path("productoComercial").asText()).isEqualTo("Plan pyme test");
        assertThat(dto.path("consumoAnualKwh").decimalValue()).isEqualByComparingTo("185475");
        assertThat(dto.path("potenciaCalculoKw").decimalValue()).isEqualByComparingTo("40");
        assertThat(dto.path("potenciaOrigen").asText()).isEqualTo("MAYOR_CONTRATADA");
        assertThat(dto.path("feeEurMwh").decimalValue()).isEqualByComparingTo("7");
        assertThat(dto.path("porcentajeAplicacionFee").decimalValue()).isEqualByComparingTo("0.44");
        assertThat(dto.path("codigoRegla").asText()).isEqualTo("IT_FEE_44");
        assertThat(dto.path("metodoCalculo").asText()).isEqualTo("FEE_PERCENTAGE");
        assertThat(dto.path("codigoConjunto").asText()).isEqualTo(e.conjunto.getCodigo());
        assertThat(dto.path("versionConjunto").asText()).isEqualTo("2");
        assertThat(dto.path("admiteEdicionManual").asBoolean(true)).isFalse();
        assertThat(dto.path("actualizadoPor").asText()).isNotBlank();
        assertThat(dto.path("actualizadoEn").isMissingNode()).isFalse();
        assertThat(dto.has("retrocesionPotencial")).isTrue();
    }

    @Test
    void el_listado_marca_las_propuestas_que_se_pueden_completar_a_mano() throws Exception {
        McasPropuesta manual = propuestaEnRevisionManual();

        JsonNode dto = propuestaDelListado(manual.getId());

        assertThat(dto.path("admiteEdicionManual").asBoolean(false)).isTrue();
        assertThat(dto.path("esRenovacion").asBoolean(false)).isTrue();
        assertThat(dto.path("codigoRegla").asText()).isEqualTo("IT_RENOVACION_MANUAL");
        assertThat(dto.path("porcentajeAplicacionFee").isNull()).isTrue();

        // El detalle usa el mismo mapeo.
        JsonNode detalle = mapper.readTree(mockMvc().perform(get("/api/mcas/propuestas/" + manual.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detalle.path("nombreCliente").asText()).isEqualTo("Cliente Test");
        assertThat(detalle.path("admiteEdicionManual").asBoolean(false)).isTrue();
    }

    @Test
    void el_listado_busca_los_nombres_por_lotes_y_tolera_un_json_ilegible() {
        McasProposalService servicio = mock(McasProposalService.class);
        ClienteRepository clientesMock = mock(ClienteRepository.class);
        TBComercializadorasRepository comercializadorasMock = mock(TBComercializadorasRepository.class);
        TBGestorasRepository gestorasMock = mock(TBGestorasRepository.class);
        McasProductoComercialRepository productosMock = mock(McasProductoComercialRepository.class);
        McasRetrocesionPolicyService retrocesiones = mock(McasRetrocesionPolicyService.class);
        McasReglaComisionRepository reglasMock = mock(McasReglaComisionRepository.class);
        McasConjuntoReglasRepository conjuntosMock = mock(McasConjuntoReglasRepository.class);
        McasPropuestasController controlador = new McasPropuestasController(servicio, clientesMock,
                comercializadorasMock, gestorasMock, productosMock, retrocesiones, reglasMock, conjuntosMock,
                mock(McasResumenContratoService.class), mock(McasLoteService.class),
                mock(McasContratoLocator.class), mapper);
        when(servicio.listarTodas()).thenReturn(List.of(
                McasPropuesta.builder().id(1L).estado("PENDIENTE_VALIDACION").idPoliticaRetrocesion(5)
                        .instantaneaJson("{\"cliente_id\":1,\"comercializadora_id\":10,\"fee\":7}").build(),
                McasPropuesta.builder().id(2L).estado("ERROR_CALCULO")
                        .instantaneaJson("{\"cliente_id\":2,\"comercializadora_id\":10}").build(),
                McasPropuesta.builder().id(3L).estado("RECHAZADO")
                        .instantaneaJson("{esto no es json").trazaCalculoJson("[roto").build()));
        when(clientesMock.findAllById(any())).thenReturn(List.of(
                Cliente.builder().id(1).nombre("Uno").build(), Cliente.builder().id(2).nombre("Dos").build()));
        when(comercializadorasMock.findAllById(any())).thenReturn(List.of(
                TBComercializadoras.builder().id(10).nombre("DIEZ").build()));
        when(retrocesiones.describirTodas(any())).thenReturn(Map.of(5, "Desde la activación: 0–3 meses: 100 %."));

        List<McasPropuestaDTO> listado = controlador.listar().getBody();

        assertThat(listado).extracting(McasPropuestaDTO::getNombreCliente).containsExactly("Uno", "Dos", null);
        assertThat(listado).extracting(McasPropuestaDTO::getNombreComercializadora).containsExactly("DIEZ", "DIEZ", null);
        assertThat(listado).extracting(McasPropuestaDTO::isAdmiteEdicionManual).containsExactly(false, true, true);
        assertThat(listado.get(0).getRetrocesionPotencial()).contains("100 %");
        assertThat(listado.get(0).getFeeEurMwh()).isEqualByComparingTo("7");
        assertThat(listado.get(2).getCodigoRegla()).isNull();
        verify(clientesMock, times(1)).findAllById(any());
        verify(comercializadorasMock, times(1)).findAllById(any());
        verify(retrocesiones, times(1)).describirTodas(any());
        verify(servicio, times(1)).contratosConComisionVigente(any());
        verify(servicio, never()).listarPorContrato(any());
        verifyNoInteractions(gestorasMock, productosMock, reglasMock, conjuntosMock);
    }

    // ============================================================
    // T1.4 · Retrocesión informativa
    // ============================================================

    @Test
    void la_politica_se_asocia_aunque_el_indicador_de_retrocesion_este_apagado() throws Exception {
        modulos.desactivar(McasProposalService.FLAG_RETROCESION);
        Escenario e = escenario("COMERCIALIZADORA RETROCESION");
        e.regla("IT_IMPORTE_FIJO", "FIXED_AMOUNT", "{\"tipo_suministro\":\"LUZ\"}", "{\"importe\":120}");
        McasPoliticaRetrocesion politica = politicaPublicada(e);

        McasPropuesta p = calcular(e, c -> c.mcasNumeroPagos(1));

        assertThat(p.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        assertThat(p.getIdPoliticaRetrocesion()).isEqualTo(politica.getId());
        assertThat(p.getImporteCalculado()).isEqualByComparingTo("120.00");   // nunca descuenta
        assertThat(propuestaDelListado(p.getId()).path("retrocesionPotencial").asText())
                .contains("0–12 meses: 100 %").contains("Nunca supera lo cobrado");
    }

    @Test
    void las_propuestas_que_no_pasan_por_el_motor_tambien_muestran_la_retrocesion() {
        Escenario e = escenario("COMERCIALIZADORA RETRO SIN CALCULO");
        McasPoliticaRetrocesion politica = politicaPublicada(e);

        // Falta el tipo de suministro: REQUIERE_DATOS sin pasar por el motor.
        McasPropuesta sinDatos = calcular(e, c -> c.mcasTipoSuministro(null));
        assertThat(sinDatos.getEstado()).isEqualTo("REQUIERE_DATOS");
        assertThat(sinDatos.getIdPoliticaRetrocesion()).isEqualTo(politica.getId());

        // Sin comercializadora no hay política que buscar, y no es un error.
        McasPropuesta sinComercializadora = calcular(e, c -> c.idComercializadoraActual(null));
        assertThat(sinComercializadora.getEstado()).isEqualTo("REQUIERE_DATOS");
        assertThat(sinComercializadora.getIdPoliticaRetrocesion()).isNull();
    }

    // ============================================================
    // Ayudas
    // ============================================================

    /** Política PUBLICADA para toda la comercializadora: 100 % los 12 primeros meses. */
    private McasPoliticaRetrocesion politicaPublicada(Escenario e) {
        McasPoliticaRetrocesion politica = politicas.save(McasPoliticaRetrocesion.builder()
                .codigo("RET-CONF-" + e.idComercializadora).idComercializadora(e.idComercializadora)
                .version("1").vigenciaDesde(LocalDate.of(2026, 1, 1)).metodo("PORCENTAJE")
                .limiteImportePagado(true).estado("PUBLICADO").build());
        tramos.save(McasTramoRetrocesion.builder().idPolitica(politica.getId()).unidad("MESES")
                .desdeValor(0).hastaValor(12).desdeInclusivo(true).hastaInclusivo(false)
                .valor(BigDecimal.ONE).tipoValor("PORCENTAJE").prioridad(100).build());
        return politica;
    }

    private JsonNode detalle(Long id) throws Exception {
        return mapper.readTree(mockMvc().perform(get("/api/mcas/propuestas/" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Listado real (controlador + JSON de la aplicación) sin el interceptor de acceso. */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(propuestasController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }

    private JsonNode propuestaDelListado(Long id) throws Exception {
        String json = mockMvc().perform(get("/api/mcas/propuestas")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode nodo : mapper.readTree(json))
            if (nodo.path("id").asLong() == id) return nodo;
        throw new AssertionError("La propuesta " + id + " no está en el listado");
    }

    /** PENDIENTE_VALIDACION por 120 € (FIXED_AMOUNT). */
    private McasPropuesta propuestaPendiente() {
        Escenario e = escenario("COMERCIALIZADORA IMPORTE FIJO");
        e.regla("IT_IMPORTE_FIJO", "FIXED_AMOUNT", "{\"tipo_suministro\":\"LUZ\"}", "{\"importe\":120}");
        McasPropuesta p = calcular(e, c -> c.mcasNumeroPagos(1));
        assertThat(p.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        return p;
    }

    /** REQUIERE_REVISION_MANUAL por una regla MANUAL de renovación. */
    private McasPropuesta propuestaEnRevisionManual() {
        Escenario e = escenario("COMERCIALIZADORA RENOVACION MANUAL");
        e.reglaManual("IT_RENOVACION_MANUAL", "{\"es_renovacion\":true}", "Renovación: importe manual");
        McasPropuesta p = calcular(e, c -> c.mcasEsRenovacion(true).mcasTipoRenovacion("FIRMADA"));
        assertThat(p.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        return p;
    }

    /** Auditoría de la propuesta en orden de inserción. */
    private List<McasAuditoria> auditoria(Long idPropuesta) {
        return auditorias.findByIdPropuestaOrderByFechaEventoAsc(idPropuesta).stream()
                .sorted(Comparator.comparing(McasAuditoria::getId)).toList();
    }

    /** Comercializadora propia con un conjunto PUBLICADO (versión «2») sin reglas. */
    private Escenario escenario(String nombreComercializadora) {
        TBComercializadoras com = comercializadoras.save(
                TBComercializadoras.builder().nombre(nombreComercializadora).build());
        McasConjuntoReglas conjunto = conjuntos.save(McasConjuntoReglas.builder()
                .codigo("CONF-FIJO-" + com.getId()).version("2").idComercializadora(com.getId())
                .fasePrecio("FIJO").vigenciaDesde(LocalDate.of(2026, 1, 1))
                .estado("PUBLICADO").creadoPor("test").build());
        return new Escenario(com.getId(), conjunto);
    }

    private final class Escenario {
        final Integer idComercializadora;
        final McasConjuntoReglas conjunto;

        Escenario(Integer idComercializadora, McasConjuntoReglas conjunto) {
            this.idComercializadora = idComercializadora;
            this.conjunto = conjunto;
        }

        McasReglaComision regla(String codigo, String metodo, String condiciones, String calculo) {
            return reglas.save(McasReglaComision.builder().idConjuntoReglas(conjunto.getId())
                    .codigo(codigo).prioridad(10).metodoCalculo(metodo)
                    .condicionesJson(condiciones).calculoJson(calculo).activo(true).build());
        }

        McasReglaComision reglaManual(String codigo, String condiciones, String motivo) {
            return reglas.save(McasReglaComision.builder().idConjuntoReglas(conjunto.getId())
                    .codigo(codigo).prioridad(5).metodoCalculo("MANUAL")
                    .condicionesJson(condiciones).motivoManual(motivo).activo(true).build());
        }
    }

    private void rango(McasReglaComision regla, int orden, String dimension,
                       String min, boolean minInclusivo, String max, boolean maxInclusivo, String resultado) {
        rangos.save(McasValorRango.builder().idRegla(regla.getId()).dimension(dimension)
                .valorMin(new BigDecimal(min)).minInclusivo(minInclusivo)
                .valorMax(max != null ? new BigDecimal(max) : null).maxInclusivo(maxInclusivo)
                .valorResultado(new BigDecimal(resultado)).orden(orden).build());
    }

    /** Contrato de luz a precio fijo de 185.475 kWh con los ajustes indicados, y su propuesta. */
    private McasPropuesta calcular(Escenario e, Consumer<Contrato.ContratoBuilder> ajustes) {
        return mcas.iniciarCalculo(nuevoContrato(e, ajustes).getId());
    }

    private Contrato nuevoContrato(Escenario e, Consumer<Contrato.ContratoBuilder> ajustes) {
        String numero = "CONF-" + System.nanoTime();
        Cliente cliente = clientes.save(Cliente.builder().nombre("Cliente Test").cif(numero).build());
        Contrato.ContratoBuilder datos = Contrato.builder()
                .cliente(cliente).contrato(numero)
                .fechaInicio(Date.valueOf("2026-03-16")).fechaFin(Date.valueOf("2027-03-15"))
                .idComercializadoraActual(e.idComercializadora).consumoAnual(185_475d)
                .mcasTipoPrecio("FIJO").mcasTipoSuministro("LUZ");
        ajustes.accept(datos);
        return contratos.save(datos.build());
    }
}
