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
import static org.mockito.ArgumentMatchers.eq;
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
        when(propuestaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

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
        when(propuestaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

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
        when(propuestaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.aprobar(25L).getIdServicioCreado()).isEqualTo(73);
    }

    @Test
    void si_el_servicio_de_la_comision_previa_se_borro_se_puede_aprobar_otra() {
        // Al borrar el servicio la BD deja id_servicio_creado vacío (ON DELETE SET NULL)
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        McasPropuesta previa = McasPropuesta.builder().id(26L).idContrato(42)
                .estado("FINALIZADO_APROBADO").idServicioCreado(null)
                .claveIdempotencia("previa-borrada").tipoEvento("NUEVO_CONTRATO").build();
        McasPropuesta nueva = McasPropuesta.builder().id(27L).idContrato(42)
                .hashContrato(hash).estado("PENDIENTE_VALIDACION")
                .claveIdempotencia("nueva-tras-borrar").tipoEvento("NUEVO_CONTRATO")
                .importeDefinitivo(new BigDecimal("100")).build();
        when(propuestaRepo.findById(27L)).thenReturn(Optional.of(nueva));
        when(propuestaRepo.findByIdContratoOrderByCreadoEnDesc(42)).thenReturn(List.of(nueva, previa));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(servicioRepo.save(any())).thenAnswer(inv -> {
            Servicio s = inv.getArgument(0); s.setId(74); return s;
        });
        when(propuestaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(propuestaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.aprobar(27L).getIdServicioCreado()).isEqualTo(74);
        assertThat(service.contratosConComisionVigente(List.of(previa))).as("ya no cuenta como comisión vigente").isEmpty();
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
        // Su servicio se borró (la BD deja id_servicio_creado vacío): ya no cuenta
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
                McasProposalService.claveComision(1, "NUEVO_CONTRATO"));
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

    @Test
    void un_contrato_de_gas_se_evalua_con_su_tarifa_de_gas_y_no_con_sin_tarifa_de_luz() {
        when(tarifasLuzRepo.findById(9)).thenReturn(Optional.of(
                api.aura.entity.tablas_maestras.TBTarifasLuz.builder().id(9).nombre("Sin Tarifa").build()));
        when(tarifasGasRepo.findById(3)).thenReturn(Optional.of(
                api.aura.entity.tablas_maestras.TBTarifasGas.builder().id(3).nombre("RL.2").build()));

        Contrato gas = new Contrato();
        gas.setMcasTipoSuministro("GAS");
        gas.setIdTarifaLuz(9);
        gas.setIdTarifaGas(3);
        assertThat(service.resolverCodigoTarifa(gas)).isEqualTo("RL.2");

        Contrato luz = new Contrato();
        luz.setMcasTipoSuministro("LUZ");
        luz.setIdTarifaLuz(9);
        luz.setIdTarifaGas(3);
        assertThat(service.resolverCodigoTarifa(luz)).as("luz: como siempre").isEqualTo("Sin Tarifa");
    }

    @Test
    void una_comision_aprobada_cuyo_servicio_se_borro_deja_volver_a_calcular_el_contrato_sin_cambios() {
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        // Su servicio se borró (la BD deja id_servicio_creado vacío) y el contrato no ha cambiado
        McasPropuesta aprobada = McasPropuesta.builder().id(60L).idContrato(42).hashContrato(hash)
                .estado("FINALIZADO_APROBADO").claveIdempotencia("42|NUEVO_CONTRATO|" + hash)
                .tipoEvento("NUEVO_CONTRATO").build();
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(propuestaRepo.findActivasPorContrato(42)).thenReturn(List.of(aprobada));
        when(propuestaRepo.findByIdContratoOrderByCreadoEnDesc(42)).thenReturn(List.of(aprobada));
        when(propuestaRepo.findByClaveIdempotencia(anyString())).thenReturn(Optional.of(aprobada));
        when(propuestaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(propuestaRepo.save(any())).thenAnswer(inv -> {
            McasPropuesta p = inv.getArgument(0); if (p.getId() == null) p.setId(61L); return p;
        });

        McasPropuesta nueva = service.triggerDesdeContrato(42);

        assertThat(nueva.getId()).as("se calcula otra (antes devolvía la aprobada sin servicio)").isEqualTo(61L);
        assertThat(aprobada.getEstado()).isEqualTo("FINALIZADO_APROBADO");
        assertThat(aprobada.getClaveIdempotencia()).startsWith("LIBERADA|60|sin-servicio|");
        verify(propuestaRepo).saveAndFlush(aprobada);
        verify(auditoria).registrar(eq(60L), eq(42), eq("COMISION_SIN_SERVICIO"), any(), any(), any(), any(), any());
    }

    @Test
    void una_comision_aprobada_con_su_servicio_sigue_siendo_la_del_contrato() {
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        McasPropuesta aprobada = McasPropuesta.builder().id(62L).idContrato(42).hashContrato(hash)
                .estado("FINALIZADO_APROBADO").idServicioCreado(85).claveIdempotencia("42|NUEVO_CONTRATO|" + hash)
                .tipoEvento("NUEVO_CONTRATO").build();
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(propuestaRepo.findByClaveIdempotencia(anyString())).thenReturn(Optional.of(aprobada));
        when(servicioRepo.findById(85)).thenReturn(Optional.of(Servicio.builder().id(85).build()));

        assertThat(service.triggerDesdeContrato(42)).isSameAs(aprobada);
        assertThat(aprobada.getClaveIdempotencia()).isEqualTo("42|NUEVO_CONTRATO|" + hash);
        verify(propuestaRepo, never()).saveAndFlush(any());
    }

    @Test
    void con_el_suministro_indicado_solo_vale_su_propia_tarifa() {
        when(tarifasGasRepo.findById(3)).thenReturn(Optional.of(
                api.aura.entity.tablas_maestras.TBTarifasGas.builder().id(3).nombre("Sin Tarifa").build()));
        when(tarifasLuzRepo.findById(5)).thenReturn(Optional.of(
                api.aura.entity.tablas_maestras.TBTarifasLuz.builder().id(5).nombre("3.0TD").build()));

        Contrato luzSinTarifa = new Contrato();
        luzSinTarifa.setMcasTipoSuministro("LUZ");
        luzSinTarifa.setIdTarifaLuz(0);
        luzSinTarifa.setIdTarifaGas(3);
        assertThat(service.resolverCodigoTarifa(luzSinTarifa))
                .as("falta la tarifa de luz: se pide el dato, no se usa la «Sin Tarifa» de gas").isNull();

        Contrato sinTipo = new Contrato();
        sinTipo.setIdTarifaLuz(0);
        sinTipo.setIdTarifaGas(3);
        assertThat(service.resolverCodigoTarifa(sinTipo)).as("sin tipo de suministro, la que haya").isEqualTo("Sin Tarifa");

        Contrato luz = new Contrato();
        luz.setMcasTipoSuministro(" luz ");
        luz.setIdTarifaLuz(5);
        luz.setIdTarifaGas(3);
        assertThat(service.resolverCodigoTarifa(luz)).isEqualTo("3.0TD");
    }

    @Test
    void recalcular_deja_constancia_de_quien_sustituyo_la_propuesta() {
        Contrato contrato = contratoBase();
        McasPropuesta enEdicion = McasPropuesta.builder().id(70L).idContrato(42).hashContrato("h")
                .estado("EN_EDICION").claveIdempotencia("42|NUEVO_CONTRATO|h").tipoEvento("NUEVO_CONTRATO").build();
        when(propuestaRepo.findById(70L)).thenReturn(Optional.of(enEdicion));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(propuestaRepo.save(any())).thenAnswer(inv -> {
            McasPropuesta p = inv.getArgument(0); if (p.getId() == null) p.setId(71L); return p;
        });

        service.recalcular(70L);

        assertThat(enEdicion.getEstado()).isEqualTo("SUPERADO");
        assertThat(enEdicion.getActualizadoEn()).isNotNull();
        verify(auditoria).registrar(eq(70L), eq(42), eq("PROPUESTA_SUPERADA"), eq("EN_EDICION"), eq("SUPERADO"), any(), any(), any());
    }

    @Test
    void un_importe_manual_con_mas_de_dos_decimales_se_redondea_al_centimo() {
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        McasPropuesta propuesta = McasPropuesta.builder().id(80L).idContrato(42).hashContrato(hash)
                .estado("EN_EDICION").claveIdempotencia("42|NUEVO_CONTRATO|" + hash).tipoEvento("NUEVO_CONTRATO")
                .numeroPagos(1).build();
        when(propuestaRepo.findById(80L)).thenReturn(Optional.of(propuesta));
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(servicioRepo.save(any())).thenAnswer(inv -> { Servicio s = inv.getArgument(0); s.setId(90); return s; });
        when(propuestaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(propuestaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(calendarioRepo.findByIdPropuestaOrderByNumeroPagoAsc(80L)).thenReturn(List.of(
                McasCalendarioPago.builder().idPropuesta(80L).numeroPago(1).importe(new BigDecimal("100.56")).build()));

        McasPropuesta finalizada = service.finalizarEditado(80L, new BigDecimal("100.555"), 1, null, "Ajuste");

        assertThat(finalizada.getImporteDefinitivo()).isEqualByComparingTo("100.56");
        assertThat(finalizada.getImporteDefinitivo().scale()).isEqualTo(2);
        ArgumentCaptor<Servicio> servicio = ArgumentCaptor.forClass(Servicio.class);
        verify(servicioRepo).save(servicio.capture());
        assertThat(servicio.getValue().getImporte()).isEqualTo(100.56);
    }

    @Test
    void los_mensajes_para_el_usuario_van_con_tildes_en_usted_y_sin_codigos_de_estado() {
        Contrato contrato = contratoBase();
        String hash = snapshots.capturar(contrato).hash();
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        McasPropuesta enEdicion = McasPropuesta.builder().id(81L).idContrato(42).hashContrato(hash)
                .estado("EN_EDICION").claveIdempotencia("e").tipoEvento("NUEVO_CONTRATO").numeroPagos(1).build();
        when(propuestaRepo.findById(81L)).thenReturn(Optional.of(enEdicion));

        assertThatThrownBy(() -> service.finalizarEditado(81L, BigDecimal.TEN, 13, null, "Ajuste"))
                .hasMessage("El número de pagos debe estar entre 1 y 12.");
        assertThatThrownBy(() -> service.finalizarEditado(81L, BigDecimal.TEN, 1, null, " "))
                .hasMessage("Indique el motivo de la corrección.");
        assertThatThrownBy(() -> service.rechazar(81L, " ")).hasMessage("Indique el motivo del rechazo.");
        assertThatThrownBy(() -> service.aprobar(81L))
                .hasMessage("Solo se puede aprobar una propuesta pendiente de validación (estado actual: «En edición»).");
        assertThatThrownBy(() -> service.rechazar(81L, "No cuadra"))
                .hasMessage("Solo se puede rechazar una propuesta pendiente de validación (estado actual: «En edición»).");
        assertThatThrownBy(() -> service.abrirEdicion(81L)).hasMessage("Solo se puede completar a mano una propuesta "
                + "rechazada o pendiente de revisión manual (estado actual: «En edición»).");

        McasPropuesta pendiente = McasPropuesta.builder().id(82L).idContrato(42).hashContrato(hash)
                .estado("PENDIENTE_VALIDACION").claveIdempotencia("p").tipoEvento("NUEVO_CONTRATO")
                .importeCalculado(BigDecimal.TEN).numeroPagos(3).build();
        when(propuestaRepo.findById(82L)).thenReturn(Optional.of(pendiente));
        assertThatThrownBy(() -> service.finalizarEditado(82L, BigDecimal.TEN, 1, null, "Ajuste"))
                .hasMessage("Solo se puede terminar la corrección de una propuesta en edición "
                        + "(estado actual: «Pendiente de validación»).");
        when(calendarioRepo.findByIdPropuestaOrderByNumeroPagoAsc(82L)).thenReturn(List.of(
                McasCalendarioPago.builder().numeroPago(1).importe(BigDecimal.ONE).build(),
                McasCalendarioPago.builder().numeroPago(2).importe(BigDecimal.ONE).build(),
                McasCalendarioPago.builder().numeroPago(3).importe(BigDecimal.ONE).build()));
        when(plazosRepo.findFirstByNumeroCuotas(3)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.aprobar(82L)).hasMessage("No existe una modalidad de pago de 3 cuotas: "
                + "créela en Administración › Pago Plazos antes de aprobar la comisión.");

        pendiente.setHashContrato("otro");
        assertThatThrownBy(() -> service.aprobar(82L))
                .hasMessage("El contrato ha cambiado desde el cálculo: recalcule la propuesta antes de aprobarla.");
    }

    @Test
    void una_retrocesion_mal_configurada_se_avisa_en_texto_humano_y_lo_tecnico_queda_en_el_log() throws Exception {
        Contrato contrato = contratoBase();
        contrato.setMcasTipoPrecio("FIJO");
        contrato.setMcasTipoSuministro("LUZ");
        when(contratoRepo.findByIdForUpdate(42)).thenReturn(Optional.of(contrato));
        when(flags.estaActivo(anyString())).thenReturn(false);       // módulo apagado: no pasa por el motor
        when(propuestaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(retrocesionPolicies.seleccionar(any())).thenThrow(new IllegalStateException(
                "2 politicas de retrocesion PUBLICADAS coinciden con igual especificidad"));

        McasPropuesta p = service.iniciarCalculo(42);

        List<String> advertencias = new ObjectMapper().readValue(p.getAdvertenciasJson(),
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        assertThat(advertencias).contains("No se ha podido mostrar la retrocesión potencial: hay varias políticas "
                + "de retrocesión publicadas que encajan con este contrato. Avise al administrador para que lo revise.");
        assertThat(p.getAdvertenciasJson()).doesNotContain("politicas de retrocesion PUBLICADAS");
    }
}
