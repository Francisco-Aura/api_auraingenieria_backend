package api.aura.mcas;

import api.aura.entity.Contrato;
import api.aura.entity.Servicio;
import api.aura.entity.mcas.McasCalendarioPago;
import api.aura.entity.mcas.McasProductoComercial;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.repository.ContratoRepository;
import api.aura.repository.ServicioRepository;
import api.aura.repository.ServiciosPagosRepository;
import api.aura.repository.mcas.*;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import api.aura.repository.tablas_maestras.TBProductosRepository;
import api.aura.repository.mcas.McasCondicionCobroRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBGestorasRepository;
import api.aura.service.mcas.McasCondicionesCobroService;
import api.aura.repository.tablas_maestras.TBTarifasGasRepository;
import api.aura.repository.tablas_maestras.TBTarifasLuzRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.McasAuditoriaService;
import api.aura.service.mcas.McasContratoSnapshotService;
import api.aura.service.mcas.McasProposalService;
import api.aura.service.mcas.McasRetrocesionPolicyService;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.service.mcas.engine.ContextoCalculo;
import api.aura.service.mcas.engine.EstrategiaFeePorentaje;
import api.aura.service.mcas.engine.EstrategiaImporteFijo;
import api.aura.service.mcas.engine.EstrategiaTasaPorMwh;
import api.aura.service.mcas.engine.McasCalculoException;
import api.aura.service.mcas.engine.MotorMcas;
import api.aura.service.mcas.engine.ResultadoCalculo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Auditoria integral del 16/09/2026: MCAS nunca debe fallar en silencio. */
class McasAuditoriaIntegralTest {

    private ContratoRepository contratoRepo;
    private McasPropuestaRepository propuestaRepo;
    private McasProductoComercialRepository productoRepo;
    private MotorMcas motor;
    private ModuloConfiguracionService flags;
    private ServicioRepository servicioRepo;
    private ServiciosPagosRepository pagosRepo;
    private TBPagoPlazosRepository plazosRepo;
    private McasCalendarioPagoRepository calendarioRepo;
    private McasRetrocesionPolicyService retrocesionPolicies;
    private McasContratoSnapshotService snapshots;
    private McasProposalService service;

    @BeforeEach
    void setUp() {
        contratoRepo = mock(ContratoRepository.class);
        propuestaRepo = mock(McasPropuestaRepository.class);
        productoRepo = mock(McasProductoComercialRepository.class);
        motor = mock(MotorMcas.class);
        flags = mock(ModuloConfiguracionService.class);
        servicioRepo = mock(ServicioRepository.class);
        pagosRepo = mock(ServiciosPagosRepository.class);
        plazosRepo = mock(TBPagoPlazosRepository.class);
        calendarioRepo = mock(McasCalendarioPagoRepository.class);
        retrocesionPolicies = mock(McasRetrocesionPolicyService.class);
        ObjectMapper mapper = new ObjectMapper();
        snapshots = new McasContratoSnapshotService(mapper);
        service = new McasProposalService(contratoRepo, propuestaRepo,
                mock(McasLineaPropuestaRepository.class), calendarioRepo,
                mock(McasAuditoriaRepository.class), productoRepo, motor,
                mock(McasAuditoriaService.class), flags, mapper, snapshots, retrocesionPolicies,
                servicioRepo, pagosRepo, plazosRepo, mock(TBTarifasLuzRepository.class),
                mock(TBTarifasGasRepository.class), mock(TBProductosRepository.class),
                new McasCondicionesCobroService(mock(McasCondicionCobroRepository.class),
                mock(TBComercializadorasRepository.class), mock(TBGestorasRepository.class),
                mock(TBPagoPlazosRepository.class)),
                new api.aura.service.mcas.McasZonasService(mock(api.aura.repository.mcas.McasZonaComisionRepository.class),
                mock(TBComercializadorasRepository.class), mock(api.aura.repository.PuntoSuministroRepository.class)));
    }

    @Test
    void una_retrocesion_mal_configurada_no_hace_desaparecer_la_propuesta() {
        Contrato contrato = contratoBase();
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(flags.estaActivo(anyString())).thenReturn(true);
        when(motor.evaluar(any())).thenReturn(ResultadoCalculo.builder()
                .resultado(ResultadoCalculo.Resultado.CALCULADO)
                .importeTotal(new BigDecimal("180.00")).numeroPagos(1)
                .lineas(List.of()).advertencias(List.of()).build());
        when(motor.buildPagos(any(), anyInt())).thenReturn(List.of(new BigDecimal("180.00")));
        when(retrocesionPolicies.seleccionar(any()))
                .thenThrow(new IllegalStateException("2 politicas de retrocesion PUBLICADAS coinciden"));
        when(propuestaRepo.save(any())).thenAnswer(inv -> {
            McasPropuesta p = inv.getArgument(0);
            if (p.getId() == null) p.setId(99L);
            return p;
        });

        McasPropuesta propuesta = service.iniciarCalculo(42);

        assertThat(propuesta.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        assertThat(propuesta.getImporteCalculado()).isEqualByComparingTo("180.00");
        assertThat(propuesta.getIdPoliticaRetrocesion()).isNull();
        assertThat(propuesta.getAdvertenciasJson()).contains("No se ha podido mostrar la retrocesión potencial: hay varias "
                + "políticas de retrocesión publicadas").doesNotContain("PUBLICADAS");
    }

    @Test
    void un_producto_comercial_sin_tipos_se_reporta_como_incompatible_y_no_rompe_el_calculo() {
        Contrato contrato = contratoBase();
        contrato.setMcasIdProductoComercial(900);
        when(productoRepo.findById(900)).thenReturn(Optional.of(McasProductoComercial.builder()
                .id(900).idComercializadora(7).codigo("P900").nombre("Producto sin tipos")
                .activo(true).build()));

        assertThat(service.camposFaltantes(contrato))
                .containsExactly("producto_comercial_activo_y_compatible");
    }

    @Test
    void aprobar_sin_modalidad_de_pago_avisa_en_lugar_de_crear_una_comision_invisible() {
        // El informe «Servicios pendientes de pago» cruza servicios_pagos con
        // tbpago_plazos: sin modalidad, el servicio creado no saldria nunca.
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        McasPropuesta propuesta = McasPropuesta.builder().id(30L).idContrato(42)
                .hashContrato(hash).estado("PENDIENTE_VALIDACION")
                .claveIdempotencia("actual").tipoEvento("NUEVO_CONTRATO")
                .importeDefinitivo(new BigDecimal("150")).numeroPagos(2).build();
        when(propuestaRepo.findById(30L)).thenReturn(Optional.of(propuesta));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(calendarioRepo.findByIdPropuestaOrderByNumeroPagoAsc(30L)).thenReturn(List.of(
                McasCalendarioPago.builder().idPropuesta(30L).numeroPago(1).importe(new BigDecimal("75")).build(),
                McasCalendarioPago.builder().idPropuesta(30L).numeroPago(2).importe(new BigDecimal("75")).build()));
        when(plazosRepo.findFirstByNumeroCuotas(2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aprobar(30L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("modalidad de pago")
                .hasMessageContaining("2");
        verify(servicioRepo, never()).save(any(Servicio.class));
        verifyNoInteractions(pagosRepo);
        assertThat(propuesta.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
    }

    @Test
    void un_calculo_mal_configurado_dice_que_dato_le_falta_y_no_un_escueto_null() {
        ObjectMapper mapper = new ObjectMapper();
        McasValorRangoRepository rangos = mock(McasValorRangoRepository.class);

        assertThatThrownBy(() -> new EstrategiaImporteFijo(mapper).calcular(
                contexto(), regla("FIXED_AMOUNT", "{}"), rangos))
                .isInstanceOf(McasCalculoException.class)
                .hasMessageContaining("importe")
                .hasMessageNotContaining("null");

        assertThatThrownBy(() -> new EstrategiaTasaPorMwh(mapper).calcular(
                contexto(), regla("RATE_PER_MWH", "{}"), rangos))
                .isInstanceOf(McasCalculoException.class)
                .hasMessageContaining("tasa_eur_mwh")
                .hasMessageNotContaining("null");

        assertThatThrownBy(() -> new EstrategiaFeePorentaje(mapper).calcular(
                contexto(), regla("FEE_PERCENTAGE", "{}"), rangos))
                .isInstanceOf(McasCalculoException.class)
                .hasMessageContaining("porcentaje_aplicacion_fee")
                .hasMessageNotContaining("null");
    }

    @Test
    void un_porcentaje_fuera_de_rango_se_rechaza_igual_que_en_las_sumas_compuestas() {
        assertThatThrownBy(() -> new EstrategiaFeePorentaje(new ObjectMapper()).calcular(
                contexto(), regla("FEE_PERCENTAGE", "{\"porcentaje_aplicacion_fee\":44}"),
                mock(McasValorRangoRepository.class)))
                .isInstanceOf(McasCalculoException.class)
                .hasMessageContaining("entre 0 y 1");
    }

    private ContextoCalculo contexto() {
        return ContextoCalculo.builder().idContrato(42)
                .consumoAnualKwh(new BigDecimal("185475"))
                .consumoAnualMwh(new BigDecimal("185.475"))
                .feeEurMwh(new BigDecimal("7")).build();
    }

    private McasReglaComision regla(String metodo, String calculoJson) {
        return McasReglaComision.builder().id(1).metodoCalculo(metodo).calculoJson(calculoJson).build();
    }

    private Contrato contratoBase() {
        return Contrato.builder().id(42).contrato("CTR-42")
                .fechaInicio(Date.valueOf("2026-03-16"))
                .idComercializadoraActual(7).consumoAnual(58_500d)
                .mcasTipoPrecio("FIJO").mcasTipoSuministro("LUZ")
                .mcasFeeEurMwh(new BigDecimal("7"))
                .mcasFeeUnidad("EUR_MWH").mcasNumeroPagos(1).build();
    }
}
