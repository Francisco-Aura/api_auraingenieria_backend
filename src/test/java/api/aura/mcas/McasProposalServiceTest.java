package api.aura.mcas;

import api.aura.entity.Contrato;
import api.aura.entity.Servicio;
import api.aura.entity.mcas.McasCalendarioPago;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.repository.ContratoRepository;
import api.aura.repository.ServicioRepository;
import api.aura.repository.ServiciosPagosRepository;
import api.aura.repository.mcas.*;
import api.aura.entity.ServiciosPagos;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.entity.tablas_maestras.TBProductos;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import api.aura.repository.tablas_maestras.TBProductosRepository;
import api.aura.repository.tablas_maestras.TBTarifasGasRepository;
import api.aura.repository.tablas_maestras.TBTarifasLuzRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.McasAuditoriaService;
import api.aura.service.mcas.McasContratoSnapshotService;
import api.aura.service.mcas.McasProposalService;
import api.aura.service.mcas.McasRetrocesionPolicyService;
import api.aura.service.mcas.engine.MotorMcas;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class McasProposalServiceTest {

    private ContratoRepository contratoRepo;
    private McasPropuestaRepository propuestaRepo;
    private McasLineaPropuestaRepository lineaRepo;
    private McasCalendarioPagoRepository calendarioRepo;
    private McasAuditoriaRepository auditoriaRepo;
    private McasProductoComercialRepository productoRepo;
    private MotorMcas motor;
    private McasAuditoriaService auditoria;
    private ModuloConfiguracionService flags;
    private ServicioRepository servicioRepo;
    private ServiciosPagosRepository pagosRepo;
    private TBPagoPlazosRepository plazosRepo;
    private TBTarifasLuzRepository tarifasLuzRepo;
    private TBTarifasGasRepository tarifasGasRepo;
    private TBProductosRepository productosServicioRepo;
    private McasContratoSnapshotService snapshots;
    private McasRetrocesionPolicyService retrocesionPolicies;
    private McasProposalService service;

    @BeforeEach
    void setUp() {
        contratoRepo = mock(ContratoRepository.class);
        propuestaRepo = mock(McasPropuestaRepository.class);
        lineaRepo = mock(McasLineaPropuestaRepository.class);
        calendarioRepo = mock(McasCalendarioPagoRepository.class);
        auditoriaRepo = mock(McasAuditoriaRepository.class);
        productoRepo = mock(McasProductoComercialRepository.class);
        motor = mock(MotorMcas.class);
        auditoria = mock(McasAuditoriaService.class);
        flags = mock(ModuloConfiguracionService.class);
        servicioRepo = mock(ServicioRepository.class);
        pagosRepo = mock(ServiciosPagosRepository.class);
        plazosRepo = mock(TBPagoPlazosRepository.class);
        tarifasLuzRepo = mock(TBTarifasLuzRepository.class);
        tarifasGasRepo = mock(TBTarifasGasRepository.class);
        productosServicioRepo = mock(TBProductosRepository.class);
        ObjectMapper mapper = new ObjectMapper();
        snapshots = new McasContratoSnapshotService(mapper);
        retrocesionPolicies = mock(McasRetrocesionPolicyService.class);
        service = new McasProposalService(contratoRepo, propuestaRepo, lineaRepo, calendarioRepo,
                auditoriaRepo, productoRepo, motor, auditoria, flags, mapper, snapshots,
                retrocesionPolicies, servicioRepo, pagosRepo, plazosRepo, tarifasLuzRepo, tarifasGasRepo,
                productosServicioRepo);
        // Catalogo minimo de modalidades de pago: sin el, aprobar avisa de que falta.
        lenient().when(plazosRepo.findFirstByNumeroCuotas(anyInt())).thenAnswer(inv ->
                Optional.of(TBPagoPlazos.builder().id(1).nombre("1 PLAZO")
                        .numeroCuotas(inv.getArgument(0)).build()));
    }

    @Test
    void trigger_repetido_devuelve_la_misma_propuesta_sin_superarla() {
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        McasPropuesta activa = McasPropuesta.builder().id(10L).idContrato(42)
                .hashContrato(hash).estado("PENDIENTE_VALIDACION")
                .claveIdempotencia("42|NUEVO_CONTRATO|" + hash).tipoEvento("NUEVO_CONTRATO").build();
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(propuestaRepo.findActivasPorContrato(42)).thenReturn(List.of(activa));

        assertThat(service.triggerDesdeContrato(42)).isSameAs(activa);
        assertThat(activa.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        verify(propuestaRepo, never()).save(any());
        verify(contratoRepo).findByIdForUpdate(42);
        verifyNoInteractions(motor);
    }

    @Test
    void aprobar_bloquea_propuesta_si_el_contrato_cambio() {
        Contrato contrato = contratoBase();
        McasPropuesta propuesta = McasPropuesta.builder().id(11L).idContrato(42)
                .hashContrato("hash-anterior").estado("PENDIENTE_VALIDACION")
                .claveIdempotencia("anterior").tipoEvento("NUEVO_CONTRATO")
                .importeDefinitivo(new BigDecimal("100")).build();
        when(propuestaRepo.findById(11L)).thenReturn(Optional.of(propuesta));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));

        assertThatThrownBy(() -> service.aprobar(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contrato ha cambiado");
        assertThat(propuesta.getEstado()).isEqualTo("DESACTUALIZADO");
        verify(propuestaRepo).save(propuesta);
        verifyNoInteractions(servicioRepo);
    }

    @Test
    void aprobar_propaga_error_de_pagos_para_que_la_transaccion_haga_rollback() {
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        McasPropuesta propuesta = McasPropuesta.builder().id(12L).idContrato(42)
                .hashContrato(hash).estado("PENDIENTE_VALIDACION")
                .claveIdempotencia("actual").tipoEvento("NUEVO_CONTRATO")
                .importeDefinitivo(new BigDecimal("120")).numeroPagos(1).build();
        when(propuestaRepo.findById(12L)).thenReturn(Optional.of(propuesta));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(servicioRepo.save(any())).thenAnswer(inv -> {
            Servicio s = inv.getArgument(0); s.setId(55); return s;
        });
        when(calendarioRepo.findByIdPropuestaOrderByNumeroPagoAsc(12L)).thenReturn(List.of(
                McasCalendarioPago.builder().idPropuesta(12L).numeroPago(1)
                        .importe(new BigDecimal("120")).build()));
        when(pagosRepo.save(any())).thenThrow(new IllegalStateException("fallo pagos"));

        assertThatThrownBy(() -> service.aprobar(12L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fallo pagos");
        verify(propuestaRepo, never()).save(propuesta);
    }

    @Test
    void finalizar_editado_desactualizado_no_persiste_edicion_ni_reconstruye_calendario() {
        Contrato contrato = contratoBase();
        McasPropuesta propuesta = McasPropuesta.builder().id(13L).idContrato(42)
                .hashContrato("hash-anterior").estado("EN_EDICION")
                .claveIdempotencia("anterior").tipoEvento("NUEVO_CONTRATO")
                .importeCalculado(new BigDecimal("100"))
                .importeDefinitivo(new BigDecimal("110"))
                .numeroPagos(2).datosEditadosJson("{\"origen\":\"anterior\"}").build();
        when(propuestaRepo.findById(13L)).thenReturn(Optional.of(propuesta));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));

        assertThatThrownBy(() -> service.finalizarEditado(
                13L, new BigDecimal("999"), 12, "{\"origen\":\"nuevo\"}", "ajuste"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contrato ha cambiado");

        assertThat(propuesta.getEstado()).isEqualTo("DESACTUALIZADO");
        assertThat(propuesta.getImporteDefinitivo()).isEqualByComparingTo("110");
        assertThat(propuesta.getNumeroPagos()).isEqualTo(2);
        assertThat(propuesta.getDatosEditadosJson()).isEqualTo("{\"origen\":\"anterior\"}");
        verify(calendarioRepo, never()).deleteByIdPropuesta(anyLong());
        verifyNoInteractions(servicioRepo);
    }

    @Test
    void aprobar_crea_servicio_con_producto_switching_y_modalidad_de_pago() {
        Contrato contrato = contratoBase();
        contrato.setMcasIdProductoComercial(900);   // producto comercial MCAS: no debe acabar en el servicio
        when(productoRepo.findById(900)).thenReturn(Optional.empty());
        String hash = snapshots.capturar(contrato).hash();
        McasPropuesta propuesta = McasPropuesta.builder().id(14L).idContrato(42)
                .hashContrato(hash).estado("PENDIENTE_VALIDACION")
                .claveIdempotencia("actual").tipoEvento("NUEVO_CONTRATO")
                .importeDefinitivo(new BigDecimal("150")).numeroPagos(2).build();
        when(propuestaRepo.findById(14L)).thenReturn(Optional.of(propuesta));
        when(propuestaRepo.findByIdContratoOrderByCreadoEnDesc(42)).thenReturn(List.of(propuesta));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(calendarioRepo.findByIdPropuestaOrderByNumeroPagoAsc(14L)).thenReturn(List.of(
                McasCalendarioPago.builder().idPropuesta(14L).numeroPago(1).importe(new BigDecimal("75")).build(),
                McasCalendarioPago.builder().idPropuesta(14L).numeroPago(2).importe(new BigDecimal("75")).build()));
        TBPagoPlazos dosPlazos = TBPagoPlazos.builder().id(3).nombre("2 PLAZOS").numeroCuotas(2).build();
        when(plazosRepo.findFirstByNumeroCuotas(2)).thenReturn(Optional.of(dosPlazos));
        when(productosServicioRepo.findFirstByNombreContainingIgnoreCaseOrderByIdAsc("SWITCHING"))
                .thenReturn(Optional.of(TBProductos.builder().id(7).nombre("SWITCHING ").build()));
        when(servicioRepo.save(any())).thenAnswer(inv -> {
            Servicio s = inv.getArgument(0); s.setId(56); return s;
        });
        when(propuestaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        McasPropuesta aprobada = service.aprobar(14L);

        ArgumentCaptor<Servicio> servicio = ArgumentCaptor.forClass(Servicio.class);
        verify(servicioRepo).save(servicio.capture());
        assertThat(servicio.getValue().getIdProducto()).isEqualTo(7);
        assertThat(servicio.getValue().getIdPagoPlazos()).isEqualTo(3);
        ArgumentCaptor<ServiciosPagos> pagos = ArgumentCaptor.forClass(ServiciosPagos.class);
        verify(pagosRepo).save(pagos.capture());
        assertThat(pagos.getValue().getPagoPlazos()).isSameAs(dosPlazos);
        assertThat(pagos.getValue().getSegundoPagoImporte()).isEqualTo(75.0);
        assertThat(aprobada.getEstado()).isEqualTo("FINALIZADO_APROBADO");
        assertThat(aprobada.getIdServicioCreado()).isEqualTo(56);
    }

    @Test
    void aprobar_sin_producto_switching_deja_el_producto_del_servicio_vacio() {
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        McasPropuesta propuesta = McasPropuesta.builder().id(15L).idContrato(42)
                .hashContrato(hash).estado("PENDIENTE_VALIDACION")
                .claveIdempotencia("actual").tipoEvento("NUEVO_CONTRATO")
                .importeDefinitivo(new BigDecimal("80")).build();
        when(propuestaRepo.findById(15L)).thenReturn(Optional.of(propuesta));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(servicioRepo.save(any())).thenAnswer(inv -> {
            Servicio s = inv.getArgument(0); s.setId(57); return s;
        });
        when(propuestaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.aprobar(15L);

        ArgumentCaptor<Servicio> servicio = ArgumentCaptor.forClass(Servicio.class);
        verify(servicioRepo).save(servicio.capture());
        assertThat(servicio.getValue().getIdProducto()).isNull();
    }

    @Test
    void aprobar_se_bloquea_si_el_contrato_ya_tiene_comision_aprobada() {
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        McasPropuesta previa = McasPropuesta.builder().id(20L).idContrato(42)
                .estado("FINALIZADO_APROBADO").idServicioCreado(70)
                .claveIdempotencia("previa").tipoEvento("NUEVO_CONTRATO").build();
        McasPropuesta nueva = McasPropuesta.builder().id(21L).idContrato(42)
                .hashContrato(hash).estado("PENDIENTE_VALIDACION")
                .claveIdempotencia("nueva").tipoEvento("NUEVO_CONTRATO")
                .importeDefinitivo(new BigDecimal("100")).build();
        when(propuestaRepo.findById(21L)).thenReturn(Optional.of(nueva));
        when(propuestaRepo.findByIdContratoOrderByCreadoEnDesc(42)).thenReturn(List.of(nueva, previa));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(servicioRepo.findById(70)).thenReturn(Optional.of(Servicio.builder().id(70).build()));

        assertThatThrownBy(() -> service.aprobar(21L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("propuesta #20")
                .hasMessageContaining("servicio #70");
        assertThat(nueva.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        verify(servicioRepo, never()).save(any());
        verifyNoInteractions(pagosRepo);
    }

    @Test
    void finalizar_editado_se_bloquea_si_el_contrato_ya_tiene_comision_aprobada() {
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        McasPropuesta previa = McasPropuesta.builder().id(22L).idContrato(42)
                .estado("FINALIZADO_EDITADO").idServicioCreado(71)
                .claveIdempotencia("previa").tipoEvento("NUEVO_CONTRATO").build();
        McasPropuesta enEdicion = McasPropuesta.builder().id(23L).idContrato(42)
                .hashContrato(hash).estado("EN_EDICION")
                .claveIdempotencia("nueva").tipoEvento("NUEVO_CONTRATO")
                .importeCalculado(new BigDecimal("100")).build();
        when(propuestaRepo.findById(23L)).thenReturn(Optional.of(enEdicion));
        when(propuestaRepo.findByIdContratoOrderByCreadoEnDesc(42)).thenReturn(List.of(enEdicion, previa));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(servicioRepo.findById(71)).thenReturn(Optional.of(Servicio.builder().id(71).build()));

        assertThatThrownBy(() -> service.finalizarEditado(23L, new BigDecimal("90"), 1, null, "ajuste"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("propuesta #22");
        verify(calendarioRepo, never()).deleteByIdPropuesta(anyLong());
        verify(servicioRepo, never()).save(any());
        verifyNoInteractions(pagosRepo);
    }

    @Test
    void cambio_del_contrato_tras_aprobar_genera_revision_manual_y_no_otra_comision() {
        Contrato contrato = contratoBase();
        McasPropuesta aprobada = McasPropuesta.builder().id(30L).idContrato(42)
                .hashContrato("hash-anterior").estado("FINALIZADO_APROBADO").idServicioCreado(80)
                .claveIdempotencia("42|NUEVO|hash-anterior").tipoEvento("NUEVO_CONTRATO").build();
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(propuestaRepo.findActivasPorContrato(42)).thenReturn(List.of(aprobada));
        when(propuestaRepo.findByIdContratoOrderByCreadoEnDesc(42)).thenReturn(List.of(aprobada));
        when(propuestaRepo.findByClaveIdempotencia(anyString())).thenReturn(Optional.empty());
        when(flags.estaActivo(McasProposalService.FLAG_ENABLED)).thenReturn(true);
        when(flags.estaActivo(McasProposalService.FLAG_FIJO)).thenReturn(true);
        when(servicioRepo.findById(80)).thenReturn(Optional.of(Servicio.builder().id(80).build()));
        when(propuestaRepo.save(any())).thenAnswer(inv -> {
            McasPropuesta p = inv.getArgument(0); if (p.getId() == null) p.setId(31L); return p;
        });

        McasPropuesta nueva = service.triggerDesdeContrato(42);

        assertThat(nueva.getId()).isEqualTo(31L);
        assertThat(nueva.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(nueva.getAdvertenciasJson()).contains("propuesta #30").contains("servicio #80");
        assertThat(aprobada.getEstado()).isEqualTo("FINALIZADO_APROBADO");
        verifyNoInteractions(motor);
        verify(servicioRepo, never()).save(any());
    }

    @Test
    void si_el_servicio_de_la_comision_previa_se_dio_de_baja_se_puede_aprobar_otra() {
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        McasPropuesta previa = McasPropuesta.builder().id(24L).idContrato(42)
                .estado("FINALIZADO_APROBADO").idServicioCreado(72)
                .claveIdempotencia("previa").tipoEvento("NUEVO_CONTRATO").build();
        McasPropuesta nueva = McasPropuesta.builder().id(25L).idContrato(42)
                .hashContrato(hash).estado("PENDIENTE_VALIDACION")
                .claveIdempotencia("nueva").tipoEvento("NUEVO_CONTRATO")
                .importeDefinitivo(new BigDecimal("100")).build();
        when(propuestaRepo.findById(25L)).thenReturn(Optional.of(nueva));
        when(propuestaRepo.findByIdContratoOrderByCreadoEnDesc(42)).thenReturn(List.of(nueva, previa));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(servicioRepo.findById(72)).thenReturn(Optional.of(
                Servicio.builder().id(72).fechaBaja(Date.valueOf("2026-05-01")).build()));
        when(servicioRepo.save(any())).thenAnswer(inv -> {
            Servicio s = inv.getArgument(0); s.setId(73); return s;
        });
        when(propuestaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.aprobar(25L).getIdServicioCreado()).isEqualTo(73);
    }

    @Test
    void la_clave_liberada_no_crece_con_cada_desactualizacion() {
        Contrato contrato = contratoBase();
        String claveLarga = "42|NUEVO|" + "a".repeat(64) + "|stale|" + java.util.UUID.randomUUID();
        McasPropuesta propuesta = McasPropuesta.builder().id(16L).idContrato(42)
                .hashContrato("hash-anterior").estado("PENDIENTE_VALIDACION")
                .claveIdempotencia(claveLarga).tipoEvento("NUEVO_CONTRATO").build();
        when(propuestaRepo.findById(16L)).thenReturn(Optional.of(propuesta));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));

        assertThatThrownBy(() -> service.aprobar(16L)).isInstanceOf(IllegalStateException.class);

        assertThat(propuesta.getClaveIdempotencia()).startsWith("LIBERADA|16|stale|");
        assertThat(propuesta.getClaveIdempotencia().length()).isLessThanOrEqualTo(190);
    }

    @Test
    void comision_vigente_por_contrato_y_evento_con_una_sola_consulta_de_servicios() {
        McasPropuesta vigente = McasPropuesta.builder().id(40L).idContrato(1).tipoEvento("NUEVO_CONTRATO")
                .estado("FINALIZADO_APROBADO").idServicioCreado(100).build();
        McasPropuesta deBaja = McasPropuesta.builder().id(41L).idContrato(2).tipoEvento("NUEVO_CONTRATO")
                .estado("FINALIZADO_APROBADO").idServicioCreado(101).build();
        McasPropuesta sinServicio = McasPropuesta.builder().id(42L).idContrato(3).tipoEvento("RENOVACION")
                .estado("FINALIZADO_EDITADO").build();
        McasPropuesta manual = McasPropuesta.builder().id(43L).idContrato(1).tipoEvento("NUEVO_CONTRATO")
                .estado("REQUIERE_REVISION_MANUAL").build();
        McasPropuesta otroEvento = McasPropuesta.builder().id(44L).idContrato(2).tipoEvento("RENOVACION")
                .estado("REQUIERE_REVISION_MANUAL").build();
        when(servicioRepo.findAllById(any())).thenReturn(List.of(
                Servicio.builder().id(100).build(),
                Servicio.builder().id(101).fechaBaja(Date.valueOf("2026-05-01")).build()));

        var claves = service.contratosConComisionVigente(List.of(vigente, deBaja, sinServicio, manual, otroEvento));

        assertThat(claves).containsExactlyInAnyOrder(
                McasProposalService.claveComision(1, "NUEVO_CONTRATO"),
                McasProposalService.claveComision(3, "RENOVACION"));
        verify(servicioRepo, times(1)).findAllById(any());
        verify(servicioRepo, never()).findById(any());
    }

    @Test
    void abrir_edicion_se_bloquea_si_el_contrato_ya_tiene_comision_aprobada() {
        McasPropuesta previa = McasPropuesta.builder().id(50L).idContrato(42)
                .estado("FINALIZADO_APROBADO").idServicioCreado(90)
                .claveIdempotencia("previa").tipoEvento("NUEVO_CONTRATO").build();
        McasPropuesta manual = McasPropuesta.builder().id(51L).idContrato(42)
                .estado("REQUIERE_REVISION_MANUAL").claveIdempotencia("manual").tipoEvento("NUEVO_CONTRATO").build();
        when(propuestaRepo.findById(51L)).thenReturn(Optional.of(manual));
        when(propuestaRepo.findByIdContratoOrderByCreadoEnDesc(42)).thenReturn(List.of(manual, previa));
        when(servicioRepo.findById(90)).thenReturn(Optional.of(Servicio.builder().id(90).build()));

        assertThatThrownBy(() -> service.abrirEdicion(51L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("propuesta #50");
        assertThat(manual.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        verify(propuestaRepo, never()).save(any());
        verifyNoInteractions(auditoria);
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
