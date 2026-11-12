package api.aura.mcas;

import api.aura.controller.mcas.McasPropuestasController;
import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.entity.Servicio;
import api.aura.entity.ServiciosPagos;
import api.aura.entity.mcas.McasAuditoria;
import api.aura.entity.mcas.McasCalendarioPago;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasPoliticaRetrocesion;
import api.aura.entity.mcas.McasProductoComercial;
import api.aura.entity.mcas.McasCondicionCobro;
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
    @Autowired private api.aura.repository.mcas.McasCondicionCobroRepository condicionesCobro;
    @Autowired private api.aura.service.ContratoService contratoService;
    @Autowired private api.aura.service.mcas.McasCondicionesCobroService cobros;
    @Autowired private api.aura.repository.mcas.McasZonaComisionRepository zonasRepo;
    @Autowired private api.aura.repository.PuntoSuministroRepository puntos;
    @Autowired private api.aura.service.mcas.McasVistaPreviaService vistaPrevia;
    @Autowired private TBGestorasRepository gestoras;
    @Autowired private McasProductoComercialRepository productosComerciales;
    @Autowired private McasPoliticaRetrocesionRepository politicas;
    @Autowired private McasTramoRetrocesionRepository tramos;
    @Autowired private McasPropuestasController propuestasController;
    @Autowired private ObjectMapper mapper;
    @Autowired private api.aura.service.mcas.McasRetrocesionService retrocesiones;
    @Autowired private api.aura.repository.mcas.McasRetrocesionRepository retrocesionesRepo;

    @BeforeEach
    void datosBase() {
        modulos.activar(McasProposalService.FLAG_ENABLED);
        modulos.activar(McasProposalService.FLAG_FIJO);
        modulos.desactivar(McasProposalService.FLAG_RETROCESION);
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
    void una_propuesta_sin_calculo_usa_la_fecha_y_los_pagos_de_la_comercializadora() {
        // Sin tipo de suministro la propuesta se crea en REQUIERE_DATOS sin pasar por
        // el motor: no tiene fecha de primera aplicación; los pagos, los de la comercializadora.
        Escenario e = escenario("COMERCIALIZADORA SIN CALCULO");
        condicionesCobro.save(McasCondicionCobro.builder().idComercializadora(e.idComercializadora).numeroPagos(2).build());
        McasPropuesta p = calcular(e, c -> c.mcasTipoSuministro(null).mcasNumeroPagos(9));
        assertThat(p.getEstado()).isEqualTo("REQUIERE_DATOS");
        assertThat(p.getFechaPrimeraAplicacion()).isNull();
        assertThat(p.getNumeroPagos()).isEqualTo(2);        // «Completar manualmente» precarga los de la comercializadora

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

        // Si el servicio de aquella comisión se da de baja (retrocesión), la comisión sigue
        // contando: no se completa otra (respuesta del cliente a la duda 17)
        Servicio servicio = servicios.findById(aprobada.getIdServicioCreado()).orElseThrow();
        servicio.setFechaBaja(Date.valueOf("2026-06-01"));
        servicios.save(servicio);
        assertThat(propuestaDelListado(nueva.getId()).path("admiteEdicionManual").asBoolean(true)).isFalse();

        // Si se elimina, ya no cuenta y se puede completar a mano
        serviciosPagos.findByServicioId(servicio.getId()).ifPresent(serviciosPagos::delete);
        servicios.delete(servicio);
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
    // ============================================================
    // Respuestas del cliente a las dudas (05/11/2026)
    // ============================================================

    @Test
    void la_comision_se_cobra_con_las_condiciones_de_su_comercializadora() {
        Escenario e = escenario("COMERCIALIZADORA COBRO A FINAL DE MES");
        e.regla("IT_FIJA", "FIXED_AMOUNT", "{\"tipo_suministro\":\"LUZ\"}", "{\"importe\":90}");
        condicionesCobro.save(McasCondicionCobro.builder().idComercializadora(e.idComercializadora).numeroPagos(2)
                .mesesHastaPrimerCobro(1).diaPrimerCobro(McasCondicionCobro.DiaCobro.ULTIMO_DIA_MES).build());
        McasPropuesta p = calcular(e, c -> c.mcasNumeroPagos(5));   // el del contrato ya no cuenta

        assertThat(p.getNumeroPagos()).isEqualTo(2);
        assertThat(p.getFechaPrimeraAplicacion()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(mcas.obtenerCalendario(p.getId())).extracting(McasCalendarioPago::getFechaPago)
                .containsExactly(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31));

        McasPropuesta aprobada = mcas.aprobar(p.getId());
        Servicio s = servicios.findById(aprobada.getIdServicioCreado()).orElseThrow();
        assertThat(s.getFechaInicioServicio().toString()).isEqualTo("2026-03-16");   // dura lo que el contrato
        ServiciosPagos plan = serviciosPagos.findByServicioId(s.getId()).orElseThrow();
        assertThat(plan.getPrimerPagoFecha().toString()).isEqualTo("2026-04-30");
        assertThat(plan.getSegundoPagoFecha().toString()).isEqualTo("2026-05-31");
    }

    @Test
    void si_solo_cambia_la_fecha_de_inicio_de_un_contrato_comisionado_se_mueven_las_cuotas_pendientes() {
        Escenario e = escenario("COMERCIALIZADORA CAMBIO DE FECHA");
        e.regla("IT_FIJA", "FIXED_AMOUNT", "{\"tipo_suministro\":\"LUZ\"}", "{\"importe\":100}");
        condicionesCobro.save(McasCondicionCobro.builder().idComercializadora(e.idComercializadora).numeroPagos(2).build());
        McasPropuesta aprobada = mcas.aprobar(calcular(e, c -> { }).getId());
        ServiciosPagos plan = serviciosPagos.findByServicioId(aprobada.getIdServicioCreado()).orElseThrow();
        plan.setPrimerPagoPagado(true);   // la primera ya se cobró
        serviciosPagos.save(plan);
        int propuestasAntes = propuestas.findByIdContratoOrderByCreadoEnDesc(aprobada.getIdContrato()).size();

        Contrato contrato = contratos.findById(aprobada.getIdContrato()).orElseThrow();
        contrato.setFechaInicio(Date.valueOf("2026-03-18"));
        contratos.save(contrato);
        McasPropuesta despues = mcas.triggerDesdeContrato(contrato.getId());

        // Sin propuesta nueva ni avisos: la misma comisión, al día con el contrato
        assertThat(despues.getId()).isEqualTo(aprobada.getId());
        assertThat(propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId())).hasSize(propuestasAntes);
        ServiciosPagos movido = serviciosPagos.findByServicioId(aprobada.getIdServicioCreado()).orElseThrow();
        assertThat(movido.getPrimerPagoFecha().toString()).isEqualTo("2026-03-16");   // cobrada: no se toca
        assertThat(movido.getSegundoPagoFecha().toString()).isEqualTo("2026-04-18");
        assertThat(servicios.findById(aprobada.getIdServicioCreado()).orElseThrow()
                .getFechaInicioServicio().toString()).isEqualTo("2026-03-18");
        assertThat(auditoria(aprobada.getId())).extracting(McasAuditoria::getTipoEvento)
                .contains("COBROS_MOVIDOS_POR_FECHA_INICIO");
        // Volver a guardar sin cambios no mueve nada más ni crea propuestas
        assertThat(mcas.triggerDesdeContrato(contrato.getId()).getId()).isEqualTo(aprobada.getId());
        assertThat(propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId())).hasSize(propuestasAntes);

        // Y si la fecha vuelve a la de antes, las cuotas pendientes también vuelven
        contrato = contratos.findById(contrato.getId()).orElseThrow();
        contrato.setFechaInicio(Date.valueOf("2026-03-16"));
        contratos.save(contrato);
        assertThat(mcas.triggerDesdeContrato(contrato.getId()).getId()).isEqualTo(aprobada.getId());
        assertThat(serviciosPagos.findByServicioId(aprobada.getIdServicioCreado()).orElseThrow()
                .getSegundoPagoFecha().toString()).isEqualTo("2026-04-16");
        assertThat(propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId())).hasSize(propuestasAntes);
    }

    @Test
    void cambiar_las_condiciones_de_cobro_avisa_de_las_comisiones_pendientes_calculadas_antes() {
        Escenario e = escenario("COMERCIALIZADORA CAMBIA SUS COBROS");
        e.regla("IT_FIJA", "FIXED_AMOUNT", "{\"tipo_suministro\":\"LUZ\"}", "{\"importe\":100}");
        McasPropuesta pendiente = calcular(e, c -> { });
        assertThat(pendiente.getEstado()).isEqualTo("PENDIENTE_VALIDACION");

        assertThat(cobros.comisionesPendientesAfectadas(e.idComercializadora, null)).isEqualTo(1);
        assertThat(cobros.comisionesPendientesAfectadas(e.idComercializadora, 999)).isZero();
        mcas.aprobar(pendiente.getId());
        assertThat(cobros.comisionesPendientesAfectadas(e.idComercializadora, null)).isZero();
    }

    @Test
    void al_mover_las_cuotas_no_se_pisan_las_que_se_cambiaron_a_mano() {
        Escenario e = escenario("COMERCIALIZADORA CUOTAS A MANO");
        e.regla("IT_FIJA", "FIXED_AMOUNT", "{\"tipo_suministro\":\"LUZ\"}", "{\"importe\":90}");
        condicionesCobro.save(McasCondicionCobro.builder().idComercializadora(e.idComercializadora).numeroPagos(2).build());
        McasPropuesta aprobada = mcas.aprobar(calcular(e, c -> { }).getId());
        // Duda 13: la segunda cuota se cambia a mano en el servicio
        ServiciosPagos plan = serviciosPagos.findByServicioId(aprobada.getIdServicioCreado()).orElseThrow();
        plan.setSegundoPagoFecha(Date.valueOf("2026-06-30"));
        serviciosPagos.save(plan);

        Contrato contrato = contratos.findById(aprobada.getIdContrato()).orElseThrow();
        contrato.setFechaInicio(Date.valueOf("2026-03-20"));
        contratos.save(contrato);
        mcas.triggerDesdeContrato(contrato.getId());

        ServiciosPagos despues = serviciosPagos.findByServicioId(aprobada.getIdServicioCreado()).orElseThrow();
        assertThat(despues.getPrimerPagoFecha().toString()).isEqualTo("2026-03-20");   // automática: se mueve
        assertThat(despues.getSegundoPagoFecha().toString()).isEqualTo("2026-06-30");  // a mano: se respeta
    }

    @Test
    void cambiar_la_fecha_fin_de_un_contrato_comisionado_no_abre_otra_propuesta() {
        McasPropuesta aprobada = mcas.aprobar(propuestaPendiente().getId());
        Contrato contrato = contratos.findById(aprobada.getIdContrato()).orElseThrow();
        contrato.setFechaFin(Date.valueOf("2026-09-30"));
        contratos.save(contrato);

        assertThat(mcas.triggerDesdeContrato(contrato.getId()).getId()).isEqualTo(aprobada.getId());
        assertThat(propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId())).hasSize(1);
    }

    @Test
    void borrar_un_contrato_borra_su_comision_y_su_servicio_pero_no_la_auditoria() {
        McasPropuesta aprobada = mcas.aprobar(propuestaPendiente().getId());
        Integer idServicio = aprobada.getIdServicioCreado();

        contratoService.eliminarPorId(aprobada.getIdContrato());

        assertThat(servicios.findById(idServicio)).isEmpty();
        assertThat(serviciosPagos.findByServicioId(idServicio)).isEmpty();
        assertThat(propuestas.findByIdContratoOrderByCreadoEnDesc(aprobada.getIdContrato())).isEmpty();
        assertThat(auditorias.findByIdContratoOrderByFechaEventoAsc(aprobada.getIdContrato()))
                .extracting(McasAuditoria::getTipoEvento).contains("PROPUESTA_APROBADA", "CONTRATO_ELIMINADO");
    }

    // ============================================================
    // Retrocesión automática (duda 20): baja o cambio de comercializadora antes de 365 días
    // ============================================================

    /** Contrato de 100 € aprobado en un punto propio, con la política del 100 % de 0 a 12 meses. */
    private McasPropuesta comisionAprobadaEnPunto(Escenario e, api.aura.entity.PuntoSuministro ps) {
        return mcas.aprobar(calcular(e, c -> c.puntoSuministro(ps)).getId());
    }

    private Escenario escenarioRetrocesion(String nombre, int pagos) {
        modulos.activar(McasProposalService.FLAG_RETROCESION);
        Escenario e = escenario(nombre);
        e.regla("IT_FIJA", "FIXED_AMOUNT", "{\"tipo_suministro\":\"LUZ\"}", "{\"importe\":100}");
        if (pagos > 1)
            condicionesCobro.save(McasCondicionCobro.builder().idComercializadora(e.idComercializadora).numeroPagos(pagos).build());
        politicaPublicada(e);
        return e;
    }

    @Test
    void la_baja_del_punto_antes_del_anyo_deja_una_retrocesion_pendiente_y_al_aprobarla_crea_el_servicio_en_negativo() {
        Escenario e = escenarioRetrocesion("COMERCIALIZADORA RETRO BAJA", 1);
        api.aura.entity.PuntoSuministro ps = punto("Murcia", "ES0031");
        McasPropuesta comision = comisionAprobadaEnPunto(e, ps);
        assertThat(retrocesiones.revisarPunto(ps.getId())).isEmpty();   // sigue activo: nada

        ps.setFechaBaja(Date.valueOf("2026-06-20"));
        puntos.save(ps);
        List<api.aura.entity.mcas.McasRetrocesion> detectadas = retrocesiones.revisarPunto(ps.getId());

        assertThat(detectadas).hasSize(1);
        api.aura.entity.mcas.McasRetrocesion r = detectadas.get(0);
        assertThat(r.getEstado()).isEqualTo("PENDIENTE");
        assertThat(r.getMotivo()).isEqualTo("BAJA_PUNTO");
        assertThat(r.getDiasActivo()).isEqualTo(96);
        assertThat(r.getImporteCalculado()).isEqualByComparingTo("100.00");
        assertThat(retrocesiones.pendientes()).isGreaterThanOrEqualTo(1);
        // Revisar otra vez no la duplica
        assertThat(retrocesiones.revisarPunto(ps.getId())).extracting(api.aura.entity.mcas.McasRetrocesion::getId)
                .containsExactly(r.getId());

        api.aura.entity.mcas.McasRetrocesion aprobada = retrocesiones.aprobar(r.getId(), null);

        assertThat(aprobada.getEstado()).isEqualTo("APROBADA");
        Servicio negativo = servicios.findById(aprobada.getIdServicioCreado()).orElseThrow();
        assertThat(negativo.getImporteFinal()).isEqualTo(-100.0);
        assertThat(negativo.getDescripcion()).contains("Retrocesión");
        assertThat(serviciosPagos.findByServicioId(negativo.getId()).orElseThrow().getPrimerPagoImporte()).isEqualTo(-100.0);
        // La comisión cobrada se queda como estaba
        assertThat(servicios.findById(comision.getIdServicioCreado()).orElseThrow().getImporteFinal()).isEqualTo(100.0);
        assertThat(auditoria(comision.getId())).extracting(McasAuditoria::getTipoEvento)
                .contains("RETROCESION_DETECTADA", "RETROCESION_APROBADA");
        // Ya resuelta: ni se vuelve a aprobar ni se vuelve a detectar
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> retrocesiones.aprobar(r.getId(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(retrocesiones.revisarPunto(ps.getId())).isEmpty();

        // Si el contrato se borra (no llegó a hacerse), se van la retrocesión y su servicio en negativo
        contratoService.eliminarPorId(comision.getIdContrato());
        retrocesiones.revisarPunto(ps.getId());
        assertThat(retrocesionesRepo.findById(r.getId())).isEmpty();
        assertThat(servicios.findById(negativo.getId())).isEmpty();
    }

    @Test
    void al_aprobar_la_retrocesion_se_anulan_las_cuotas_sin_cobrar_posteriores_a_la_baja() {
        Escenario e = escenarioRetrocesion("COMERCIALIZADORA RETRO CUOTAS", 2);
        api.aura.entity.PuntoSuministro ps = punto("Murcia", "ES0031");
        McasPropuesta comision = comisionAprobadaEnPunto(e, ps);
        Contrato contrato = contratos.findById(comision.getIdContrato()).orElseThrow();
        contrato.setFechaBaja(Date.valueOf("2026-04-01"));   // cuotas: 16/03 y 16/04
        contratos.save(contrato);

        api.aura.entity.mcas.McasRetrocesion r = retrocesiones.revisarPunto(ps.getId()).get(0);

        assertThat(r.getMotivo()).isEqualTo("BAJA_CONTRATO");
        assertThat(r.getCuotasAnuladas()).isEqualTo("2");
        assertThat(r.getImporteCuotasAnuladas()).isEqualByComparingTo("50.00");
        assertThat(r.getImporteCalculado()).isEqualByComparingTo("50.00");   // 100 % de lo cobrado

        retrocesiones.aprobar(r.getId(), new BigDecimal("30"));   // el administrador puede corregir el importe

        ServiciosPagos plan = serviciosPagos.findByServicioId(comision.getIdServicioCreado()).orElseThrow();
        assertThat(plan.getPrimerPagoImporte()).isEqualTo(50.0);
        assertThat(plan.getSegundoPagoImporte()).isEqualTo(0.0);
        Servicio servicio = servicios.findById(comision.getIdServicioCreado()).orElseThrow();
        assertThat(servicio.getImporteFinal()).isEqualTo(50.0);
        assertThat(servicio.getObservaciones()).contains("anuladas");
        api.aura.entity.mcas.McasRetrocesion aprobada = retrocesionesRepo.findById(r.getId()).orElseThrow();
        assertThat(servicios.findById(aprobada.getIdServicioCreado()).orElseThrow().getImporteFinal()).isEqualTo(-30.0);
    }

    @Test
    void renovar_con_la_misma_comercializadora_no_retrocede_y_cambiar_a_otra_antes_del_anyo_si() {
        Escenario e = escenarioRetrocesion("COMERCIALIZADORA RETRO CAMBIO", 1);
        api.aura.entity.PuntoSuministro ps = punto("Murcia", "ES0031");
        McasPropuesta comision = comisionAprobadaEnPunto(e, ps);
        Contrato original = contratos.findById(comision.getIdContrato()).orElseThrow();
        original.setFechaBaja(Date.valueOf("2026-09-01"));   // lo que hace la renovación
        contratos.save(original);
        Contrato renovado = nuevoContrato(e, c -> c.puntoSuministro(ps).fechaInicio(Date.valueOf("2026-09-01")));

        assertThat(retrocesiones.revisarPunto(ps.getId())).isEmpty();

        // El siguiente contrato pasa a ser de otra comercializadora
        renovado.setIdComercializadoraActual(escenario("COMERCIALIZADORA RETRO NUEVA").idComercializadora);
        contratos.save(renovado);
        api.aura.entity.mcas.McasRetrocesion r = retrocesiones.revisarPunto(ps.getId()).get(0);

        assertThat(r.getMotivo()).isEqualTo("CAMBIO_COMERCIALIZADORA");
        assertThat(r.getIdContratoNuevo()).isEqualTo(renovado.getId());
        assertThat(r.getDiasActivo()).isEqualTo(169);

        // Y si el cambio llega cumplido el año, la pendiente se descarta sola
        renovado.setFechaInicio(Date.valueOf("2027-03-16"));
        contratos.save(renovado);
        assertThat(retrocesiones.revisarPunto(ps.getId())).extracting(api.aura.entity.mcas.McasRetrocesion::getEstado)
                .containsExactly("DESCARTADA");
    }

    @Test
    void con_el_interruptor_apagado_no_se_genera_retrocesion_y_descartar_pide_motivo() {
        Escenario e = escenarioRetrocesion("COMERCIALIZADORA RETRO APAGADA", 1);
        api.aura.entity.PuntoSuministro ps = punto("Murcia", "ES0031");
        comisionAprobadaEnPunto(e, ps);
        ps.setFechaBaja(Date.valueOf("2026-05-01"));
        puntos.save(ps);

        modulos.desactivar(McasProposalService.FLAG_RETROCESION);
        assertThat(retrocesiones.revisarPunto(ps.getId())).isEmpty();

        modulos.activar(McasProposalService.FLAG_RETROCESION);
        api.aura.entity.mcas.McasRetrocesion r = retrocesiones.revisarPunto(ps.getId()).get(0);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> retrocesiones.descartar(r.getId(), " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(retrocesiones.descartar(r.getId(), "Acordado con la comercializadora").getEstado()).isEqualTo("DESCARTADA");
        assertThat(retrocesiones.listar()).anySatisfy(v -> {
            assertThat(v.retrocesion().getId()).isEqualTo(r.getId());
            assertThat(v.comercializadora()).isEqualTo("COMERCIALIZADORA RETRO APAGADA");
            assertThat(v.politica()).contains("100 %");
        });
    }

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
    // ------------------------------------------------------------------ duda 5: zona por provincia

    @Test
    void la_zona_sale_de_la_provincia_del_punto_y_no_de_lo_escrito_en_el_contrato() {
        Escenario e = escenario("COMERCIALIZADORA CON ZONAS");
        e.regla("ZONA_ZAR", "FIXED_AMOUNT", "{\"zona_comision\":\"ZAR\"}", "{\"importe\":100}");
        e.regla("ZONA_ZBR", "FIXED_AMOUNT", "{\"zona_comision\":\"ZBR\"}", "{\"importe\":60}");
        zonasRepo.save(api.aura.entity.mcas.McasZonaComision.builder().idComercializadora(e.idComercializadora).zona("ZAR")
                .provincias(new java.util.ArrayList<>(List.of("03", "30"))).build());
        zonasRepo.save(api.aura.entity.mcas.McasZonaComision.builder().idComercializadora(e.idComercializadora).zona("ZBR")
                .esResto(true).build());

        // Murcia escrita a mano en mayúsculas, con un CUPS que por la regla del CUPS sería ZBR
        // y con «ZBR» escrito en el contrato: manda la provincia
        McasPropuesta murcia = calcular(e, c -> c.puntoSuministro(punto("MURCIA", "ES0031")).mcasZonaComision("ZBR"));
        assertThat(murcia.getImporteCalculado()).isEqualByComparingTo("100.00");
        assertThat(contratos.findById(murcia.getIdContrato()).orElseThrow().getMcasZonaComision()).isEqualTo("ZAR");

        // Almería no está en la ZAR: va a la zona del resto aunque su CUPS empiece por ES0021
        McasPropuesta almeria = calcular(e, c -> c.puntoSuministro(punto("Almería", "ES0021")));
        assertThat(almeria.getImporteCalculado()).isEqualByComparingTo("60.00");

        // Una provincia que no se reconoce no se manda al resto: faltan datos y se dice qué corregir
        McasPropuesta rara = calcular(e, c -> c.puntoSuministro(punto("Mursia", "ES0021")));
        assertThat(rara.getEstado()).isEqualTo("REQUIERE_DATOS");
        assertThat(rara.getAdvertenciasJson()).contains("No se reconoce la provincia del punto de suministro (Mursia)");
    }

    @Test
    void sin_zonas_configuradas_la_zona_sigue_saliendo_del_cups_de_luz() {
        Escenario e = escenario("COMERCIALIZADORA SIN ZONAS");
        e.regla("ZONA_ZAR", "FIXED_AMOUNT", "{\"zona_comision\":\"ZAR\"}", "{\"importe\":100}");
        e.regla("ZONA_ZBR", "FIXED_AMOUNT", "{\"zona_comision\":\"ZBR\"}", "{\"importe\":60}");

        assertThat(calcular(e, c -> c.puntoSuministro(punto("Murcia", "ES0021"))).getImporteCalculado())
                .isEqualByComparingTo("100.00");
        assertThat(calcular(e, c -> c.puntoSuministro(punto("Murcia", "ES0031"))).getImporteCalculado())
                .isEqualByComparingTo("60.00");
    }

    // ------------------------------------------------------------------ duda 22: la vista previa también

    @Test
    void la_vista_previa_calcula_la_ultima_relacion_igual_que_al_guardar() {
        Escenario e = escenario("COMERCIALIZADORA QUE NO PAGA SI VUELVE");
        e.regla("GENERAL", "FIXED_AMOUNT", "{\"tipo_suministro\":\"LUZ\"}", "{\"importe\":120}");
        reglas.save(McasReglaComision.builder().idConjuntoReglas(e.conjunto.getId()).codigo("VUELVE_PRONTO").prioridad(20)
                .metodoCalculo("NOT_COMMISSIONABLE")
                .condicionesJson("{\"tipo_suministro\":\"LUZ\",\"meses_ultima_relacion_max\":12}")
                .motivoManual("Volvió antes de un año").activo(true).build());
        api.aura.entity.PuntoSuministro punto = punto("Murcia", "ES0031");
        // El ejemplo del cliente: con esta comercializadora hasta el 15/05/2025
        contratos.save(Contrato.builder().cliente(punto.getCliente()).puntoSuministro(punto)
                .contrato("ANT-" + System.nanoTime())
                .fechaInicio(Date.valueOf("2024-05-15")).fechaFin(Date.valueOf("2025-05-15"))
                .idComercializadoraActual(e.idComercializadora).mcasTipoPrecio("FIJO").mcasTipoSuministro("LUZ").build());

        com.fasterxml.jackson.databind.node.ObjectNode cuerpo = mapper.createObjectNode()
                .put("contrato", "NUEVO-" + System.nanoTime()).put("fechaInicio", "2026-02-13")
                .put("idComercializadoraActual", e.idComercializadora)
                .put("mcasTipoPrecio", "FIJO").put("mcasTipoSuministro", "LUZ").put("consumoAnual", 5000);
        cuerpo.putObject("puntoSuministro").put("id", punto.getId());

        // Vuelve a los 8 meses: no comisiona, y la estimación ya lo dice antes de guardar
        assertThat(vistaPrevia.calcular(cuerpo).getEstado())
                .isEqualTo(api.aura.dto.mcas.McasVistaPreviaDTO.Estado.NO_COMISIONABLE);
        // Pasado el año, la regla general
        cuerpo.put("fechaInicio", "2026-06-01");
        assertThat(vistaPrevia.calcular(cuerpo).getImporte()).isEqualByComparingTo("120.00");
    }

    /** Punto de suministro en esa provincia con un CUPS de luz que empieza así. */
    private api.aura.entity.PuntoSuministro punto(String provincia, String prefijoCups) {
        Cliente titular = clientes.save(Cliente.builder().nombre("Titular zona").cif("Z" + System.nanoTime()).build());
        return puntos.save(api.aura.entity.PuntoSuministro.builder().cliente(titular).direccion("Calle Mayor 1")
                .codigoPostal("30001").poblacion("Murcia").provincia(provincia)
                .CUPSluz(prefijoCups + String.format("%014d", System.nanoTime() % 100_000_000_000_000L) + "AA").build());
    }

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
