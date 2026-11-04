package api.aura.mcas;

import api.aura.controller.mcas.McasVistaPreviaController;
import api.aura.dto.mcas.McasCampo;
import api.aura.dto.mcas.McasVistaPreviaDTO;
import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.entity.tablas_maestras.TBProductos;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.mcas.McasAuditoriaRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasPropuestaRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import api.aura.repository.tablas_maestras.TBProductosRepository;
import api.aura.service.ContratoService;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.McasProposalService;
import api.aura.service.mcas.McasVistaPreviaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vista previa de la comisión desde el formulario del contrato (B1/B2): mismo
 * cálculo que al guardar, sin escribir nada, y con los datos que piden las reglas
 * de la comercializadora.
 */
@SpringBootTest
class McasVistaPreviaTest {

    @Autowired private McasVistaPreviaService vistaPrevia;
    @Autowired private McasVistaPreviaController controlador;
    @Autowired private McasProposalService mcas;
    @Autowired private ContratoService contratoService;
    @Autowired private ModuloConfiguracionService modulos;
    @Autowired private ClienteRepository clientes;
    @Autowired private ContratoRepository contratos;
    @Autowired private TBComercializadorasRepository comercializadoras;
    @Autowired private McasConjuntoReglasRepository conjuntos;
    @Autowired private McasReglaComisionRepository reglas;
    @Autowired private McasValorRangoRepository rangos;
    @Autowired private McasPropuestaRepository propuestas;
    @Autowired private McasAuditoriaRepository auditorias;
    @Autowired private TBProductosRepository productosServicio;
    @Autowired private TBPagoPlazosRepository pagoPlazos;
    @Autowired private ObjectMapper mapper;

    @BeforeEach
    void datosBase() {
        activarTodo();
        if (productosServicio.findFirstByNombreContainingIgnoreCaseOrderByIdAsc("switching").isEmpty())
            productosServicio.save(TBProductos.builder().nombre("SWITCHING").build());
        for (int n = 1; n <= 2; n++) {
            int cuotas = n;
            if (pagoPlazos.findFirstByNumeroCuotas(cuotas).isEmpty())
                pagoPlazos.save(TBPagoPlazos.builder().nombre(cuotas + " PLAZOS").numeroCuotas(cuotas).build());
        }
    }

    @AfterEach
    void activarTodo() {
        modulos.activar(McasProposalService.FLAG_ENABLED);
        modulos.activar(McasProposalService.FLAG_FIJO);
    }

    @Test
    void calcula_la_comision_estimada_con_regla_legible_y_calendario() {
        Integer iris = comercializadoraIris();

        McasVistaPreviaDTO r = vistaPrevia.calcular(pyme(iris).put("mcasNumeroPagos", 2));

        assertThat(r.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.CALCULADO);
        assertThat(r.getImporte()).isEqualByComparingTo("571.26");
        assertThat(r.getNumeroPagos()).isEqualTo(2);
        assertThat(r.getMensaje()).isEqualTo("Comisión estimada: 571,26 € en 2 pagos.");
        assertThat(r.getRegla()).isEqualTo("Iris · pyme luz · 44 % del FEE");
        assertThat(r.getCalendario()).extracting(McasVistaPreviaDTO.Cuota::getFecha)
                .containsExactly(LocalDate.of(2026, 3, 16), LocalDate.of(2026, 4, 16));
        assertThat(r.getCalendario()).extracting(c -> c.getImporte().toPlainString()).containsExactly("285.63", "285.63");
        assertThat(r.getAvisos()).isEmpty();

        assertThat(r.getCampos()).extracting(McasVistaPreviaDTO.Campo::getClave).containsExactly(
                McasCampo.TIPO_SUMINISTRO, McasCampo.SEGMENTO, McasCampo.CONSUMO, McasCampo.FEE);
        assertThat(r.getCampos()).noneMatch(McasVistaPreviaDTO.Campo::isFalta);
        McasVistaPreviaDTO.Campo segmento = campo(r, McasCampo.SEGMENTO).orElseThrow();
        assertThat(segmento.getEtiqueta()).isEqualTo("Segmento");
        assertThat(segmento.getValores()).containsExactly("PYME", "RESIDENCIAL");
        // Una regla sin nombre no enseña su código a quien graba el contrato.
        assertThat(segmento.getUsadoPor()).containsExactly("Iris · pyme luz · 44 % del FEE", "Regla de la tabla de VP IRIS");
        assertThat(campo(r, McasCampo.FEE).orElseThrow().getUsadoPor()).containsExactly("Iris · pyme luz · 44 % del FEE");
    }

    @Test
    void no_crea_propuestas_ni_auditoria_ni_modifica_el_contrato_aunque_llegue_su_id() {
        Integer iris = comercializadoraIris();
        Contrato guardado = contratos.save(Contrato.builder().contrato("VP-" + System.nanoTime())
                .fechaInicio(Date.valueOf("2026-03-16")).idComercializadoraActual(iris).consumoAnual(100_000d)
                .mcasTipoPrecio("FIJO").mcasTipoSuministro("LUZ").mcasSegmento("RESIDENCIAL").build());
        long propuestasAntes = propuestas.count();
        long auditoriaAntes = auditorias.count();

        McasVistaPreviaDTO r = vistaPrevia.calcular(pyme(iris).put("id", guardado.getId())
                .put("contrato", guardado.getContrato()));

        assertThat(r.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.CALCULADO);
        Contrato leido = contratos.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getConsumoAnual()).isEqualTo(100_000d);
        assertThat(leido.getMcasFeeEurMwh()).isNull();
        assertThat(leido.getMcasSegmento()).isEqualTo("RESIDENCIAL");
        assertThat(propuestas.count()).isEqualTo(propuestasAntes);
        assertThat(auditorias.count()).isEqualTo(auditoriaAntes);
    }

    @Test
    void requiere_datos_marca_los_campos_que_faltan() {
        Integer iris = comercializadoraIris();
        ObjectNode sinFee = pyme(iris);
        sinFee.remove("mcasFeeEurMwh");

        McasVistaPreviaDTO r = vistaPrevia.calcular(sinFee);

        assertThat(r.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.REQUIERE_DATOS);
        assertThat(r.getMensaje()).isEqualTo("Falta un dato para calcular la comisión: FEE del contrato.");
        assertThat(r.getImporte()).isNull();
        assertThat(r.getCalendario()).isEmpty();
        assertThat(r.getRegla()).isEqualTo("Iris · pyme luz · 44 % del FEE");
        assertThat(r.getCampos()).filteredOn(McasVistaPreviaDTO.Campo::isFalta)
                .extracting(McasVistaPreviaDTO.Campo::getClave).containsExactly(McasCampo.FEE);
    }

    @Test
    void solo_pide_los_campos_que_usan_las_reglas_con_los_valores_de_las_tablas_y_la_gestora() {
        TBComercializadoras com = comercializadoras.save(TBComercializadoras.builder().nombre("VP IBERDROLA").build());
        McasConjuntoReglas generico = conjunto(com.getId(), null, "Iberdrola 2026");
        McasReglaComision matriz = reglas.save(McasReglaComision.builder().idConjuntoReglas(generico.getId())
                .codigo("IBE_MATRIZ").nombre("Iberdrola · fuera de ámbito").prioridad(10).metodoCalculo("MATRIX_LOOKUP")
                .condicionesJson("{\"ambito\":\"FUERA\"}").activo(true).build());
        celda(matriz, 1, "ZBR", "74.40");
        celda(matriz, 2, "ZAR", "57.60");
        conjunto(com.getId(), 77, "Iberdrola gestora Nexo");

        McasVistaPreviaDTO r = vistaPrevia.calcular(base(com.getId()).put("mcasAmbito", "FUERA")
                .put("potencia1", 10).put("mcasZonaComision", "ZAR"));

        assertThat(r.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.CALCULADO);
        assertThat(r.getImporte()).isEqualByComparingTo("57.60");
        assertThat(r.getCampos()).extracting(McasVistaPreviaDTO.Campo::getClave).containsExactly(
                McasCampo.AMBITO, McasCampo.ZONA_COMISION, McasCampo.POTENCIA, McasCampo.GESTORA);
        assertThat(campo(r, McasCampo.ZONA_COMISION).orElseThrow().getValores()).containsExactly("ZAR", "ZBR");
        assertThat(campo(r, McasCampo.AMBITO).orElseThrow().getValores()).containsExactly("FUERA");
        assertThat(campo(r, McasCampo.GESTORA).orElseThrow().getUsadoPor()).containsExactly("Iberdrola gestora Nexo");
    }

    @Test
    void estados_sin_calculo_automatico() {
        Integer iris = comercializadoraIris();

        ObjectNode sinTipo = pyme(iris);
        sinTipo.remove("mcasTipoPrecio");
        McasVistaPreviaDTO r = vistaPrevia.calcular(sinTipo);
        assertThat(r.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.SIN_TIPO_PRECIO);
        assertThat(r.getMensaje()).isEqualTo("Indique el tipo de precio (fijo o indexado) para estimar la comisión.");
        assertThat(campo(r, McasCampo.TIPO_PRECIO).orElseThrow().isFalta()).isTrue();

        ObjectNode sinComercializadora = pyme(iris);
        sinComercializadora.remove("idComercializadoraActual");
        McasVistaPreviaDTO sinCom = vistaPrevia.calcular(sinComercializadora);
        assertThat(sinCom.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.REQUIERE_DATOS);
        assertThat(sinCom.getMensaje()).isEqualTo("Complete o corrija estos datos del contrato: comercializadora.");

        TBComercializadoras sinTabla = comercializadoras.save(TBComercializadoras.builder().nombre("VP SIN TABLA").build());
        McasVistaPreviaDTO sinReglas = vistaPrevia.calcular(pyme(sinTabla.getId()));
        assertThat(sinReglas.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.SIN_REGLAS);
        assertThat(sinReglas.getMensaje()).isEqualTo("Esta comercializadora no tiene una tabla de comisiones activa "
                + "en la fecha del contrato (16/03/2026); la comisión se revisará manualmente.");

        modulos.desactivar(McasProposalService.FLAG_FIJO);
        McasVistaPreviaDTO fijoApagado = vistaPrevia.calcular(pyme(iris));
        assertThat(fijoApagado.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.DESACTIVADO);
        assertThat(fijoApagado.getMensaje()).startsWith("El cálculo automático de los contratos a precio fijo está desactivado");

        // Con el módulo apagado no se crea ninguna propuesta: no se promete una revisión.
        modulos.desactivar(McasProposalService.FLAG_ENABLED);
        McasVistaPreviaDTO apagado = vistaPrevia.calcular(pyme(iris));
        assertThat(apagado.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.DESACTIVADO);
        assertThat(apagado.getMensaje()).isEqualTo(
                "El cálculo automático de comisiones está desactivado: la comisión se gestiona manualmente.");
    }

    @Test
    void una_regla_sin_nombre_se_muestra_con_el_de_la_comercializadora_y_nunca_con_su_codigo() throws Exception {
        Integer iris = comercializadoraIris();
        ObjectNode residencial = base(iris).put("mcasSegmento", "RESIDENCIAL").put("consumoAnual", 3_000);

        McasVistaPreviaDTO r = vistaPrevia.calcular(residencial);

        assertThat(r.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.CALCULADO);
        assertThat(r.getImporte()).isEqualByComparingTo("40");
        assertThat(r.getRegla()).isEqualTo("Regla de la tabla de VP IRIS");
        assertThat(mapper.writeValueAsString(r)).doesNotContain("IRIS_RES_FIJO").doesNotContain("IRIS_PYME");
    }

    @Test
    void contrato_con_comision_aprobada_avisa_de_que_no_se_generara_otra() {
        Integer iris = comercializadoraIris();
        Contrato contrato = guardarContrato(pyme(iris));
        McasPropuesta p = propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId()).get(0);
        mcas.aprobar(p.getId());

        McasVistaPreviaDTO r = vistaPrevia.calcular(pyme(iris).put("id", contrato.getId())
                .put("consumoAnual", 300_000));

        assertThat(r.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.YA_TIENE_COMISION);
        assertThat(r.getImporte()).isEqualByComparingTo("571.26");
        assertThat(r.getMensaje()).isEqualTo("Este contrato ya tiene una comisión aprobada: 571,26 € en 1 pago. "
                + "Los cambios del contrato no generan otra comisión.");
    }

    @Test
    void un_contrato_marcado_no_procede_no_promete_comision_y_no_muestra_el_motivo() {
        Integer iris = comercializadoraIris();
        Contrato contrato = guardarContrato(pyme(iris));
        McasPropuesta p = propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId()).get(0);
        mcas.descartar(p.getId(), "Motivo interno del validador");

        McasVistaPreviaDTO r = vistaPrevia.calcular(pyme(iris).put("id", contrato.getId()).put("consumoAnual", 250_000));

        assertThat(r.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.REQUIERE_REVISION_MANUAL);
        assertThat(r.getImporte()).isNull();
        assertThat(r.getMensaje()).isEqualTo("Cerrada sin comisión (no procede): el validador revisará si ahora procede.");
    }

    @Test
    void coincide_con_la_propuesta_que_se_genera_al_guardar() {
        Integer iris = comercializadoraIris();
        ObjectNode cuerpo = pyme(iris).put("consumoAnual", 98_765).put("mcasFeeEurMwh", "6.5");

        McasVistaPreviaDTO estimada = vistaPrevia.calcular(cuerpo);
        Contrato contrato = guardarContrato(cuerpo);
        McasPropuesta real = propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId()).get(0);

        assertThat(estimada.getEstado()).isEqualTo(McasVistaPreviaDTO.Estado.CALCULADO);
        assertThat(real.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        assertThat(estimada.getImporte()).isEqualByComparingTo(real.getImporteCalculado());
        assertThat(estimada.getNumeroPagos()).isEqualTo(real.getNumeroPagos());
        assertThat(real.getTrazaCalculoJson()).contains("\"regla_nombre\":\"" + estimada.getRegla() + "\"");
    }

    @Test
    void la_api_responde_con_los_campos_del_contrato_y_400_si_el_cuerpo_no_es_valido() throws Exception {
        Integer iris = comercializadoraIris();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();

        String json = mvc.perform(post("/api/mcas/vista-previa").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(pyme(iris))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode nodo = mapper.readTree(json);
        List<String> campos = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("estado", "mensaje", "importe", "numeroPagos", "calendario",
                "regla", "avisos", "campos");
        assertThat(json).doesNotContain("traza").doesNotContain("IRIS_PYME");

        mvc.perform(post("/api/mcas/vista-previa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consumoAnual\":\"mucho\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
    }

    // ============================================================
    // Ayudas
    // ============================================================

    /** Iris: 44 % del FEE para pyme de luz y 40 € fijos para residencial. */
    private Integer comercializadoraIris() {
        TBComercializadoras com = comercializadoras.save(TBComercializadoras.builder().nombre("VP IRIS").build());
        McasConjuntoReglas c = conjunto(com.getId(), null, "Iris 2026");
        reglas.save(McasReglaComision.builder().idConjuntoReglas(c.getId()).codigo("IRIS_PYME")
                .nombre("Iris · pyme luz · 44 % del FEE").prioridad(10).metodoCalculo("FEE_PERCENTAGE")
                .condicionesJson("{\"tipo_suministro\":\"LUZ\",\"segmento\":\"PYME\"}")
                .calculoJson("{\"porcentaje_aplicacion_fee\":0.44}").activo(true).build());
        reglas.save(McasReglaComision.builder().idConjuntoReglas(c.getId()).codigo("IRIS_RES_FIJO")
                .prioridad(20).metodoCalculo("FIXED_AMOUNT")
                .condicionesJson("{\"tipo_suministro\":\"LUZ\",\"segmento\":\"RESIDENCIAL\"}")
                .calculoJson("{\"importe\":40}").activo(true).build());
        return com.getId();
    }

    private McasConjuntoReglas conjunto(Integer idComercializadora, Integer idGestora, String nombre) {
        return conjuntos.save(McasConjuntoReglas.builder().codigo("VP-" + System.nanoTime()).nombre(nombre)
                .version("1").idComercializadora(idComercializadora).idGestora(idGestora).fasePrecio("FIJO")
                .vigenciaDesde(LocalDate.of(2026, 1, 1)).estado("PUBLICADO").build());
    }

    private void celda(McasReglaComision regla, int orden, String zona, String importe) {
        rangos.save(McasValorRango.builder().idRegla(regla.getId()).dimension("potencia_kw")
                .valorMin(BigDecimal.ZERO).valorMax(new BigDecimal("15")).valorResultado(new BigDecimal(importe))
                .orden(orden).build());
        rangos.save(McasValorRango.builder().idRegla(regla.getId()).dimension("zona_comision")
                .valorCategoria(zona).valorResultado(new BigDecimal(importe)).orden(orden).build());
    }

    /** JSON del formulario de contratos con lo mínimo para calcular. */
    private ObjectNode base(Integer idComercializadora) {
        return mapper.valueToTree(Map.of("contrato", "VP-" + System.nanoTime(), "fechaInicio", "2026-03-16",
                "idComercializadoraActual", idComercializadora, "mcasTipoPrecio", "FIJO",
                "mcasTipoSuministro", "LUZ", "mcasNumeroPagos", 1));
    }

    private ObjectNode pyme(Integer idComercializadora) {
        return base(idComercializadora).put("mcasSegmento", "PYME").put("consumoAnual", 185_475)
                .put("mcasFeeEurMwh", 7).put("mcasFeeUnidad", "EUR_MWH");
    }

    private Optional<McasVistaPreviaDTO.Campo> campo(McasVistaPreviaDTO r, McasCampo clave) {
        return r.getCampos().stream().filter(c -> c.getClave() == clave).findFirst();
    }

    /** Guarda el contrato como el controlador de contratos: la propuesta se genera tras el commit. */
    private Contrato guardarContrato(ObjectNode cuerpo) {
        try {
            Contrato c = mapper.treeToValue(cuerpo, Contrato.class);
            c.setCliente(clientes.save(Cliente.builder().nombre("Cliente VP").cif("VP-" + System.nanoTime()).build()));
            return contratoService.guardarContrato(c);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
