package api.aura.mcas;

import api.aura.controller.mcas.McasPropuestasController;
import api.aura.dto.mcas.McasResumenContratoDTO;
import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.entity.tablas_maestras.TBProductos;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasPropuestaRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import api.aura.repository.tablas_maestras.TBProductosRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.McasProposalService;
import api.aura.service.mcas.McasResumenContratoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resumen de la comisión de un contrato para quien lo graba (A1): estado,
 * importe, pagos, qué falta y una frase en usted; sin traza, códigos ni JSON.
 */
@SpringBootTest
class McasResumenContratoTest {

    @Autowired private McasResumenContratoService resumenes;
    @Autowired private McasProposalService mcas;
    @Autowired private McasPropuestasController controlador;
    @Autowired private ModuloConfiguracionService modulos;
    @Autowired private ClienteRepository clientes;
    @Autowired private ContratoRepository contratos;
    @Autowired private TBComercializadorasRepository comercializadoras;
    @Autowired private McasConjuntoReglasRepository conjuntos;
    @Autowired private McasReglaComisionRepository reglas;
    @Autowired private McasPropuestaRepository propuestas;
    @Autowired private TBProductosRepository productosServicio;
    @Autowired private TBPagoPlazosRepository pagoPlazos;
    @Autowired private ObjectMapper mapper;

    @BeforeEach
    void datosBase() {
        modulos.activar(McasProposalService.FLAG_ENABLED);
        modulos.activar(McasProposalService.FLAG_FIJO);
        if (productosServicio.findFirstByNombreContainingIgnoreCaseOrderByIdAsc("switching").isEmpty())
            productosServicio.save(TBProductos.builder().nombre("SWITCHING").build());
        if (pagoPlazos.findFirstByNumeroCuotas(1).isEmpty())
            pagoPlazos.save(TBPagoPlazos.builder().nombre("1 PLAZO").numeroCuotas(1).build());
    }

    @AfterEach
    void moduloActivo() {
        modulos.activar(McasProposalService.FLAG_ENABLED);
    }

    @Test
    void pendiente_de_aprobar_con_importe_y_pagos() {
        Integer id = comercializadoraConFee();
        McasPropuesta p = calcular(id, c -> c.mcasFeeEurMwh(new BigDecimal("7")).mcasFeeUnidad("EUR_MWH"));

        McasResumenContratoDTO r = resumenes.resumen(p.getIdContrato());

        assertThat(r.isModuloActivo()).isTrue();
        assertThat(r.isHayPropuesta()).isTrue();
        assertThat(r.getIdPropuesta()).isEqualTo(p.getId());
        assertThat(r.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        assertThat(r.getImporte()).isEqualByComparingTo("571.26");
        assertThat(r.getNumeroPagos()).isEqualTo(1);
        assertThat(r.getQueFalta()).isEmpty();
        assertThat(r.getMensaje()).isEqualTo("Comisión pendiente de aprobar: 571,26 € en 1 pago.");
        assertThat(r.isServicioCreado()).isFalse();
        assertThat(r.getActualizadoEn()).isNotNull();
    }

    @Test
    void requiere_datos_dice_que_falta() {
        Integer id = comercializadoraConFee();
        McasPropuesta p = calcular(id, c -> {});

        McasResumenContratoDTO r = resumenes.resumen(p.getIdContrato());

        assertThat(r.getEstado()).isEqualTo("REQUIERE_DATOS");
        assertThat(r.getQueFalta()).containsExactly("FEE del contrato");
        assertThat(r.getMensaje()).isEqualTo("Falta un dato para calcular la comisión: FEE del contrato. "
                + "Complételo y guarde el contrato.");
        assertThat(r.getImporte()).isNull();
    }

    @Test
    void faltan_datos_basicos_del_contrato() {
        Integer id = comercializadoraConFee();
        McasPropuesta p = calcular(id, c -> c.mcasTipoSuministro(null));

        McasResumenContratoDTO r = resumenes.resumen(p.getIdContrato());

        assertThat(r.getEstado()).isEqualTo("REQUIERE_DATOS");
        assertThat(r.getQueFalta()).containsExactly("Tipo de suministro");
    }

    @Test
    void aprobada_muestra_la_comision_aunque_despues_cambie_el_contrato() {
        Integer id = comercializadoraConFee();
        McasPropuesta p = calcular(id, c -> c.mcasFeeEurMwh(new BigDecimal("7")).mcasFeeUnidad("EUR_MWH"));
        McasPropuesta aprobada = mcas.aprobar(p.getId());
        Contrato c = contratos.findById(p.getIdContrato()).orElseThrow();
        c.setConsumoAnual(200_000d);
        contratos.save(c);
        McasPropuesta posterior = mcas.triggerDesdeContrato(c.getId());
        assertThat(posterior.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");

        McasResumenContratoDTO r = resumenes.resumen(c.getId());

        assertThat(r.getIdPropuesta()).isEqualTo(aprobada.getId());
        assertThat(r.getEstado()).isEqualTo("FINALIZADO_APROBADO");
        assertThat(r.isServicioCreado()).isTrue();
        assertThat(r.getMensaje()).isEqualTo("Comisión aprobada: 571,26 € en 1 pago. Se ha creado el servicio de comisión.");
    }

    @Test
    void revision_manual_y_no_procede_con_su_motivo() {
        Integer id = comercializadoraConFee();
        McasPropuesta manual = calcular(id, c -> c.mcasTipoSuministro("GAS"));
        assertThat(resumenes.resumen(manual.getIdContrato()).getMensaje()).isEqualTo(
                "Comisión pendiente de revisión manual. Ninguna regla de la tabla de comisiones se aplica a este "
                        + "contrato; la comisión se revisará manualmente.");

        McasPropuesta pendiente = calcular(id, c -> c.mcasFeeEurMwh(new BigDecimal("7")).mcasFeeUnidad("EUR_MWH"));
        mcas.descartar(pendiente.getId(), "Cliente de vuelta en menos de un año");
        McasResumenContratoDTO r = resumenes.resumen(pendiente.getIdContrato());
        assertThat(r.getEstado()).isEqualTo("FINALIZADO_NO_COMISIONABLE");
        // El motivo libre del validador no se enseña a quien graba el contrato.
        assertThat(r.getMensaje()).isEqualTo("Cerrada sin comisión (no procede).");
        assertThat(r.getMensaje()).doesNotContain("Cliente de vuelta");

        // Tampoco cuando el contrato cambia y la propuesta nueva va a revisión por ese «No procede».
        Contrato c = contratos.findById(pendiente.getIdContrato()).orElseThrow();
        c.setConsumoAnual(190_000d);
        contratos.save(c);
        assertThat(mcas.triggerDesdeContrato(c.getId()).getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        McasResumenContratoDTO cambiado = resumenes.resumen(c.getId());
        assertThat(cambiado.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(cambiado.getMensaje()).isEqualTo("Comisión pendiente de revisión manual: estaba cerrada sin comisión "
                + "(no procede) y el contrato ha cambiado.");
    }

    @Test
    void sin_propuesta_explica_por_que() {
        Integer id = comercializadoraConFee();
        Contrato sinTipo = nuevoContrato(id, c -> c.mcasTipoPrecio(null));

        McasResumenContratoDTO r = resumenes.resumen(sinTipo.getId());
        assertThat(r.isHayPropuesta()).isFalse();
        assertThat(r.getIdPropuesta()).isNull();
        assertThat(r.getQueFalta()).containsExactly("Tipo de precio");
        assertThat(r.getMensaje()).isEqualTo("Este contrato aún no tiene comisión calculada. "
                + "Complete los datos que faltan y guarde el contrato.");

        modulos.desactivar(McasProposalService.FLAG_ENABLED);
        McasResumenContratoDTO apagado = resumenes.resumen(sinTipo.getId());
        assertThat(apagado.isModuloActivo()).isFalse();
        assertThat(apagado.getMensaje()).isEqualTo(
                "El cálculo automático de comisiones está desactivado: la comisión se gestiona manualmente.");
        assertThat(apagado.getQueFalta()).isEmpty();

        assertThatThrownBy(() -> resumenes.resumen(999_999_999)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propuestas_antiguas_con_causa_tecnica_se_explican_igual() {
        Integer id = comercializadoraConFee();
        Contrato c = nuevoContrato(id, x -> {});
        propuestas.save(McasPropuesta.builder().idContrato(c.getId()).tipoEvento("NUEVO_CONTRATO")
                .claveIdempotencia("VIEJA-" + System.nanoTime()).estado("REQUIERE_DATOS")
                .advertenciasJson("[\"Faltan datos requeridos por las reglas: consumo_kwh, segmento\"]").build());

        McasResumenContratoDTO r = resumenes.resumen(c.getId());

        assertThat(r.getQueFalta()).containsExactly("Consumo anual", "Segmento");
        assertThat(r.getMensaje()).startsWith("Faltan datos para calcular la comisión: consumo anual y segmento.");
    }

    /**
     * creado_en es DATETIME (al segundo): guardar el contrato dos veces seguidas deja
     * la propuesta sustituida y la nueva con la misma fecha. «La última» es la de
     * mayor id, como en el listado y el recuento, no la que devuelva la BD primero.
     */
    @Test
    void con_dos_propuestas_del_mismo_segundo_manda_la_ultima_creada() {
        Integer id = comercializadoraConFee();
        Contrato c = nuevoContrato(id, x -> {});
        java.time.LocalDateTime mismoSegundo = java.time.LocalDateTime.of(2026, 10, 6, 12, 0, 0);
        McasPropuesta sustituida = propuestas.save(McasPropuesta.builder().idContrato(c.getId())
                .tipoEvento("NUEVO_CONTRATO").claveIdempotencia("SUSTITUIDA-" + System.nanoTime())
                .estado("DESACTUALIZADO").creadoEn(mismoSegundo).build());
        McasPropuesta nueva = propuestas.save(McasPropuesta.builder().idContrato(c.getId())
                .tipoEvento("NUEVO_CONTRATO").claveIdempotencia("NUEVA-" + System.nanoTime())
                .estado("REQUIERE_DATOS").creadoEn(mismoSegundo).build());
        assertThat(nueva.getId()).isGreaterThan(sustituida.getId());

        assertThat(propuestas.findByIdContratoOrderByCreadoEnDesc(c.getId()))
                .extracting(McasPropuesta::getId).containsExactly(nueva.getId(), sustituida.getId());
        McasResumenContratoDTO r = resumenes.resumen(c.getId());
        assertThat(r.getIdPropuesta()).isEqualTo(nueva.getId());
        assertThat(r.getEstado()).isEqualTo("REQUIERE_DATOS");
    }

    @Test
    void la_api_devuelve_solo_los_campos_del_contrato_de_api() throws Exception {
        Integer id = comercializadoraConFee();
        McasPropuesta p = calcular(id, c -> c.mcasFeeEurMwh(new BigDecimal("7")).mcasFeeUnidad("EUR_MWH"));

        String json = MockMvcBuilders.standaloneSetup(controlador)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build()
                .perform(get("/api/mcas/propuestas/contrato/" + p.getIdContrato() + "/resumen"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode nodo = mapper.readTree(json);
        List<String> campos = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("moduloActivo", "hayPropuesta", "idPropuesta", "estado",
                "importe", "numeroPagos", "queFalta", "mensaje", "actualizadoEn", "servicioCreado");
        assertThat(json).doesNotContain("RES_FEE").doesNotContain("traza");
    }

    // ============================================================
    // Ayudas
    // ============================================================

    /** Comercializadora con una regla «44 % del FEE» para luz. */
    private Integer comercializadoraConFee() {
        TBComercializadoras com = comercializadoras.save(
                TBComercializadoras.builder().nombre("COMERCIALIZADORA RESUMEN").build());
        McasConjuntoReglas conjunto = conjuntos.save(McasConjuntoReglas.builder()
                .codigo("RES-" + com.getId()).version("1").idComercializadora(com.getId())
                .fasePrecio("FIJO").vigenciaDesde(LocalDate.of(2026, 1, 1)).estado("PUBLICADO").build());
        reglas.save(McasReglaComision.builder().idConjuntoReglas(conjunto.getId()).codigo("RES_FEE")
                .prioridad(10).metodoCalculo("FEE_PERCENTAGE").condicionesJson("{\"tipo_suministro\":\"LUZ\"}")
                .calculoJson("{\"porcentaje_aplicacion_fee\":0.44}").activo(true).build());
        return com.getId();
    }

    private McasPropuesta calcular(Integer idComercializadora, Consumer<Contrato.ContratoBuilder> ajustes) {
        return mcas.iniciarCalculo(nuevoContrato(idComercializadora, ajustes).getId());
    }

    private Contrato nuevoContrato(Integer idComercializadora, Consumer<Contrato.ContratoBuilder> ajustes) {
        String numero = "RES-" + System.nanoTime();
        Cliente cliente = clientes.save(Cliente.builder().nombre("Cliente " + numero).cif(numero).build());
        Contrato.ContratoBuilder datos = Contrato.builder().cliente(cliente).contrato(numero)
                .fechaInicio(Date.valueOf("2026-03-16")).idComercializadoraActual(idComercializadora)
                .consumoAnual(185_475d).mcasTipoPrecio("FIJO").mcasTipoSuministro("LUZ").mcasNumeroPagos(1);
        ajustes.accept(datos);
        return contratos.save(datos.build());
    }
}
