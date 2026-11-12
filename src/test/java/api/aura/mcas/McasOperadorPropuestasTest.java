package api.aura.mcas;

import api.aura.controller.mcas.McasPropuestasController;
import api.aura.dto.mcas.McasContratoEncontradoDTO;
import api.aura.dto.mcas.McasRecuentoDTO;
import api.aura.dto.mcas.McasResultadoLoteDTO;
import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.entity.PuntoSuministro;
import api.aura.entity.mcas.McasAuditoria;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.entity.tablas_maestras.TBProductos;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.ServicioRepository;
import api.aura.repository.mcas.McasAuditoriaRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasPropuestaRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import api.aura.repository.tablas_maestras.TBProductosRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.McasContratoLocator;
import api.aura.service.mcas.McasLoteService;
import api.aura.service.mcas.McasProposalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Operaciones del validador de la ronda de usabilidad (30/09/2026): «No procede»,
 * recalcular una descartada, contador de pendientes, aprobación en lote, buscar
 * contrato por número y advertencias en texto humano en el listado.
 *
 * <p>H2 y transacciones reales, sin mocks: la aprobación en lote abre una
 * transacción por propuesta y el test comprueba que una fallida no deshace las
 * demás. Cada prueba usa su propia comercializadora.</p>
 */
@SpringBootTest
class McasOperadorPropuestasTest {

    @Autowired private McasProposalService mcas;
    @Autowired private McasLoteService lotes;
    @Autowired private McasContratoLocator localizador;
    @Autowired private McasPropuestasController controlador;
    @Autowired private ModuloConfiguracionService modulos;
    @Autowired private ClienteRepository clientes;
    @Autowired private ContratoRepository contratos;
    @Autowired private PuntoSuministroRepository puntos;
    @Autowired private TBComercializadorasRepository comercializadoras;
    @Autowired private McasConjuntoReglasRepository conjuntos;
    @Autowired private McasReglaComisionRepository reglas;
    @Autowired private McasPropuestaRepository propuestas;
    @Autowired private McasAuditoriaRepository auditorias;
    @Autowired private ServicioRepository servicios;
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

    // ============================================================
    // A6 · No procede
    // ============================================================

    @Test
    void descartar_cierra_sin_comision_con_motivo_y_auditoria_sin_liberar_la_clave() {
        Escenario e = escenario("DESCARTAR");
        McasPropuesta p = calcular(e, c -> {});
        String clave = p.getClaveIdempotencia();

        McasPropuesta descartada = mcas.descartar(p.getId(), "Estuvo con Endesa hace 6 meses");

        assertThat(descartada.getEstado()).isEqualTo("FINALIZADO_NO_COMISIONABLE");
        assertThat(descartada.getMotivoRechazo()).isEqualTo("Estuvo con Endesa hace 6 meses");
        assertThat(descartada.getIdServicioCreado()).isNull();
        assertThat(descartada.getClaveIdempotencia()).isEqualTo(clave);
        McasAuditoria evento = auditorias.findByIdPropuestaOrderByFechaEventoAsc(p.getId()).stream()
                .max(Comparator.comparing(McasAuditoria::getId)).orElseThrow();
        assertThat(evento.getTipoEvento()).isEqualTo("PROPUESTA_DESCARTADA");
        assertThat(evento.getEstadoAnterior()).isEqualTo("PENDIENTE_VALIDACION");
        assertThat(evento.getDatosJson()).contains("Estuvo con Endesa hace 6 meses");

        // Volver a guardar el contrato sin cambios no la resucita.
        McasPropuesta otraVez = mcas.triggerDesdeContrato(p.getIdContrato());
        assertThat(otraVez.getId()).isEqualTo(p.getId());
        assertThat(otraVez.getEstado()).isEqualTo("FINALIZADO_NO_COMISIONABLE");
        assertThat(propuestas.findByIdContratoOrderByCreadoEnDesc(p.getIdContrato())).hasSize(1);
    }

    @Test
    void descartar_exige_motivo_y_una_propuesta_abierta() {
        Escenario e = escenario("DESCARTAR ESTADOS");
        McasPropuesta p = calcular(e, c -> {});

        assertThatThrownBy(() -> mcas.descartar(p.getId(), "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
        mcas.aprobar(p.getId());
        assertThatThrownBy(() -> mcas.descartar(p.getId(), "Ya no procede"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recalcular_reabre_una_propuesta_descartada_sin_servicio() {
        Escenario e = escenario("REABRIR");
        McasPropuesta p = calcular(e, c -> {});
        mcas.descartar(p.getId(), "Error del operador");

        McasPropuesta nueva = mcas.recalcular(p.getId());

        assertThat(nueva.getId()).isNotEqualTo(p.getId());
        assertThat(nueva.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        assertThat(propuestas.findById(p.getId()).orElseThrow().getEstado()).isEqualTo("SUPERADO");
    }

    @Test
    void no_procede_se_mantiene_aunque_cambie_el_contrato_hasta_reabrirla() {
        Escenario e = escenario("NO PROCEDE PERSISTE");
        McasPropuesta p = calcular(e, c -> {});
        McasPropuesta descartada = mcas.descartar(p.getId(), "Cliente de vuelta antes de un año.");
        String fecha = descartada.getActualizadoEn().toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        cambiarConsumo(p.getIdContrato(), 65_000d);
        McasPropuesta nueva = mcas.triggerDesdeContrato(p.getIdContrato());

        assertThat(nueva.getId()).isNotEqualTo(p.getId());
        assertThat(nueva.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(nueva.getAdvertenciasJson()).contains("Se marcó como No procede el " + fecha
                + ": Cliente de vuelta antes de un año. Revise si ahora procede.");
        assertThat(lotes.aprobarLote(List.of(nueva.getId())).get(0).isOk()).isFalse();
        assertThat(propuestas.findById(p.getId()).orElseThrow().getEstado()).isEqualTo("FINALIZADO_NO_COMISIONABLE");

        // Recalcular la descartada la reabre: sustituye la de revisión y calcula de nuevo.
        McasPropuesta reabierta = mcas.recalcular(p.getId());
        assertThat(reabierta.getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        assertThat(propuestas.findById(nueva.getId()).orElseThrow().getEstado()).isEqualTo("SUPERADO");
    }

    @Test
    void una_regla_no_comisionable_no_bloquea_el_calculo_cuando_cambia_el_contrato() {
        Escenario e = escenario("REGLA NO COMISIONABLE");
        reglas.save(McasReglaComision.builder().idConjuntoReglas(e.conjunto().getId()).codigo("OPE_GAS_NO")
                .prioridad(5).metodoCalculo("NOT_COMMISSIONABLE").condicionesJson("{\"tipo_suministro\":\"GAS\"}")
                .motivoManual("El gas no comisiona").activo(true).build());
        McasPropuesta gas = calcular(e, c -> c.mcasTipoSuministro("GAS"));
        assertThat(gas.getEstado()).isEqualTo("FINALIZADO_NO_COMISIONABLE");

        Contrato contrato = contratos.findById(gas.getIdContrato()).orElseThrow();
        contrato.setMcasTipoSuministro("LUZ");
        contratos.save(contrato);

        assertThat(mcas.triggerDesdeContrato(contrato.getId()).getEstado()).isEqualTo("PENDIENTE_VALIDACION");
    }

    @Test
    void descartar_por_la_api_devuelve_la_propuesta_y_sin_motivo_da_400() throws Exception {
        Escenario e = escenario("DESCARTAR API");
        McasPropuesta p = calcular(e, c -> {});

        mvc().perform(post("/api/mcas/propuestas/" + p.getId() + "/descartar")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        mvc().perform(post("/api/mcas/propuestas/" + p.getId() + "/descartar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"No procede\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO_NO_COMISIONABLE"))
                .andExpect(jsonPath("$.motivoRechazo").value("No procede"));
    }

    // ============================================================
    // B4 · Recuento
    // ============================================================

    @Test
    void recuento_por_grupos_sin_contar_las_desactualizadas_ya_sustituidas() {
        McasRecuentoDTO antes = lotes.recuento();
        Escenario e = escenario("RECUENTO");

        calcular(e, c -> {});                                                     // por validar
        calcular(e, c -> c.mcasTipoSuministro("GAS"));                            // revisión manual (ninguna regla)
        McasPropuesta rechazada = calcular(e, c -> {});
        mcas.rechazar(rechazada.getId(), "Importe mal");                          // en corrección
        McasPropuesta sustituida = calcular(e, c -> {});                           // cambia el contrato y se recalcula:
        cambiarConsumo(sustituida.getIdContrato(), 60_000d);                      // la antigua queda DESACTUALIZADO
        mcas.triggerDesdeContrato(sustituida.getIdContrato());                    // y la nueva, por validar
        McasPropuesta desfasada = calcular(e, c -> {});                            // cambia sin recalcular: al aprobar
        cambiarConsumo(desfasada.getIdContrato(), 61_000d);                       // queda DESACTUALIZADO y es la última
        assertThatThrownBy(() -> mcas.aprobar(desfasada.getId())).isInstanceOf(RuntimeException.class);

        McasRecuentoDTO despues = lotes.recuento();

        assertThat(despues.porValidar() - antes.porValidar()).isEqualTo(2);
        assertThat(despues.porRevisar() - antes.porRevisar()).isEqualTo(2);
        assertThat(despues.enCorreccion() - antes.enCorreccion()).isEqualTo(1);
        assertThat(despues.total()).isEqualTo(despues.porValidar() + despues.porRevisar() + despues.enCorreccion());
    }

    @Test
    void recuento_por_la_api() throws Exception {
        String json = mvc().perform(get("/api/mcas/propuestas/recuento")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> campos = new ArrayList<>();
        mapper.readTree(json).fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("porValidar", "porRevisar", "enCorreccion", "total");
    }

    // ============================================================
    // B5 · Aprobación en lote
    // ============================================================

    @Test
    void aprobar_en_lote_devuelve_resultados_parciales_y_conserva_lo_aprobado() {
        Escenario e = escenario("LOTE");
        McasPropuesta buena = calcular(e, c -> {});
        McasPropuesta manual = calcular(e, c -> c.mcasTipoSuministro("GAS"));
        McasPropuesta desfasada = calcular(e, c -> {});
        cambiarConsumo(desfasada.getIdContrato(), 70_000d);

        List<McasResultadoLoteDTO> r = lotes.aprobarLote(
                List.of(buena.getId(), manual.getId(), 999_999_999L, desfasada.getId(), buena.getId()));

        assertThat(r).extracting(McasResultadoLoteDTO::getId)
                .containsExactly(buena.getId(), manual.getId(), 999_999_999L, desfasada.getId());
        McasPropuesta aprobada = propuestas.findById(buena.getId()).orElseThrow();
        assertThat(aprobada.getEstado()).isEqualTo("FINALIZADO_APROBADO");
        assertThat(servicios.findById(aprobada.getIdServicioCreado())).isPresent();
        assertThat(r.get(0).isOk()).isTrue();
        assertThat(r.get(0).getEstado()).isEqualTo("FINALIZADO_APROBADO");
        assertThat(r.get(0).getMensaje()).isEqualTo("Servicio #" + aprobada.getIdServicioCreado() + " creado");

        assertThat(r.get(1).isOk()).isFalse();
        assertThat(r.get(1).getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(r.get(1).getMensaje()).isEqualTo("Esta propuesta no está pendiente de aprobar.");
        assertThat(r.get(2).isOk()).isFalse();
        assertThat(r.get(2).getEstado()).isNull();
        assertThat(r.get(2).getMensaje()).isEqualTo("La propuesta no existe.");
        assertThat(r.get(3).isOk()).isFalse();
        assertThat(r.get(3).getEstado()).isEqualTo("DESACTUALIZADO");
        assertThat(r.get(3).getMensaje())
                .isEqualTo("El contrato ha cambiado desde el cálculo: recalcule la propuesta antes de aprobarla.");
        assertThat(propuestas.findById(desfasada.getId()).orElseThrow().getEstado()).isEqualTo("DESACTUALIZADO");
    }

    @Test
    void aprobar_en_lote_admite_como_maximo_200_y_al_menos_una() throws Exception {
        List<Long> muchos = LongStream.rangeClosed(1, 201).boxed().toList();
        assertThatThrownBy(() -> lotes.aprobarLote(muchos))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("200");
        assertThatThrownBy(() -> lotes.aprobarLote(List.of())).isInstanceOf(IllegalArgumentException.class);

        mvc().perform(post("/api/mcas/propuestas/aprobar-lote").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("ids", muchos))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        mvc().perform(post("/api/mcas/propuestas/aprobar-lote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[999999998]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(999999998))
                .andExpect(jsonPath("$[0].ok").value(false));
    }

    // ============================================================
    // A5 · Buscar contrato por número
    // ============================================================

    @Test
    void buscar_contrato_por_numero_exacto_prefijo_o_id_con_datos_para_elegir() throws Exception {
        Escenario e = escenario("BUSCAR");
        String base = "BUS" + System.nanoTime();
        Contrato exacto = contrato(e, base, "ES0021000000000001AB");
        for (int i = 0; i < 12; i++) contrato(e, base + "-" + i, null);

        List<McasContratoEncontradoDTO> porNumero = localizador.buscar(base);
        assertThat(porNumero).hasSize(McasContratoLocator.MAX_RESULTADOS);
        assertThat(porNumero.get(0).getId()).isEqualTo(exacto.getId());
        assertThat(porNumero.get(0).getNumero()).isEqualTo(base);
        assertThat(porNumero.get(0).getCliente()).isEqualTo("Cliente " + base);
        assertThat(porNumero.get(0).getCups()).isEqualTo("ES0021000000000001AB");
        assertThat(porNumero.get(0).getComercializadora()).isEqualTo("COMERCIALIZADORA BUSCAR");
        assertThat(porNumero.get(0).getFechaInicio()).isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(porNumero.stream().map(McasContratoEncontradoDTO::getId).distinct()).hasSize(10);

        assertThat(localizador.buscar(String.valueOf(exacto.getId())))
                .extracting(McasContratoEncontradoDTO::getId).contains(exacto.getId());
        assertThat(localizador.buscar("NO-EXISTE-" + base)).isEmpty();
        assertThat(localizador.buscar("  ")).isEmpty();

        mvc().perform(get("/api/mcas/propuestas/buscar-contrato").param("ref", base))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].numero").value(base))
                .andExpect(jsonPath("$[0].fechaInicio").value("2026-03-16"));
    }

    @Test
    void buscarUno_conserva_el_comportamiento_de_la_simulacion() {
        Escenario e = escenario("BUSCAR UNO");
        String numero = "UNO" + System.nanoTime();
        Contrato c = contrato(e, numero, null);

        assertThat(localizador.buscarUno(numero).getId()).isEqualTo(c.getId());
        assertThat(localizador.buscarUno(String.valueOf(c.getId())).getId()).isEqualTo(c.getId());
        assertThatThrownBy(() -> localizador.buscarUno("NO-" + numero))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NO-" + numero);
        assertThatThrownBy(() -> localizador.buscarUno(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    // ============================================================
    // A2 · Advertencias humanas y nombres legibles en el listado
    // ============================================================

    @Test
    void el_listado_traduce_las_advertencias_antiguas_y_da_nombres_legibles() throws Exception {
        Escenario e = escenario("NOMBRES");
        e.conjunto.setNombre("Nombres 2026");
        conjuntos.save(e.conjunto);
        McasPropuesta calculada = calcular(e, c -> {});
        McasPropuesta antigua = propuestas.save(McasPropuesta.builder().idContrato(calculada.getIdContrato())
                .tipoEvento("NUEVO_CONTRATO").claveIdempotencia("ANTIGUA-" + System.nanoTime())
                .estado("REQUIERE_REVISION_MANUAL")
                .advertenciasJson("[\"MCAS_ENABLED=false\",\"Faltan o son invalidos: tipo_precio_FIJO_o_INDEXADO\"]")
                .build());

        JsonNode lista = mapper.readTree(mvc().perform(get("/api/mcas/propuestas/contrato/" + calculada.getIdContrato()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        JsonNode vieja = buscar(lista, antigua.getId());
        assertThat(vieja.path("advertencias")).extracting(JsonNode::asText).containsExactly(
                "El cálculo automático de comisiones está desactivado; la comisión se revisará manualmente.",
                "Complete o corrija estos datos del contrato: tipo de precio.");
        assertThat(vieja.path("nombreRegla").isNull()).isTrue();
        JsonNode nueva = buscar(lista, calculada.getId());
        assertThat(nueva.path("nombreRegla").asText()).isEqualTo("Importe fijo de prueba");
        assertThat(nueva.path("nombreConjunto").asText()).isEqualTo("Nombres 2026");
        assertThat(nueva.path("advertencias").isArray()).isTrue();
    }

    @Test
    void ultima_del_contrato_en_el_listado_por_contrato_y_detalle() throws Exception {
        Escenario e = escenario("ULTIMA");
        McasPropuesta vieja = calcular(e, c -> {});
        cambiarConsumo(vieja.getIdContrato(), 66_000d);
        McasPropuesta nueva = mcas.triggerDesdeContrato(vieja.getIdContrato());
        assertThat(propuestas.findById(vieja.getId()).orElseThrow().getEstado()).isEqualTo("DESACTUALIZADO");

        for (String ruta : List.of("/api/mcas/propuestas/contrato/" + vieja.getIdContrato(), "/api/mcas/propuestas")) {
            JsonNode lista = json(ruta);
            assertThat(buscar(lista, vieja.getId()).path("ultimaDelContrato").asBoolean(true)).as(ruta).isFalse();
            assertThat(buscar(lista, nueva.getId()).path("ultimaDelContrato").asBoolean(false)).as(ruta).isTrue();
        }
        assertThat(json("/api/mcas/propuestas/" + nueva.getId()).path("ultimaDelContrato").asBoolean(false)).isTrue();
        assertThat(json("/api/mcas/propuestas/" + vieja.getId()).path("ultimaDelContrato").asBoolean(true)).isFalse();
    }

    // ============================================================
    // Ayudas
    // ============================================================

    private JsonNode json(String ruta) throws Exception {
        return mapper.readTree(mvc().perform(get(ruta)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(controlador)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }

    private JsonNode buscar(JsonNode lista, Long id) {
        for (JsonNode n : lista) if (n.path("id").asLong() == id) return n;
        throw new AssertionError("Sin propuesta " + id);
    }

    private Escenario escenario(String nombre) {
        TBComercializadoras com = comercializadoras.save(
                TBComercializadoras.builder().nombre("COMERCIALIZADORA " + nombre).build());
        McasConjuntoReglas conjunto = conjuntos.save(McasConjuntoReglas.builder()
                .codigo("OPE-" + com.getId()).version("1").idComercializadora(com.getId())
                .fasePrecio("FIJO").vigenciaDesde(LocalDate.of(2026, 1, 1)).estado("PUBLICADO").build());
        reglas.save(McasReglaComision.builder().idConjuntoReglas(conjunto.getId()).codigo("OPE_FIJO")
                .nombre("Importe fijo de prueba").prioridad(10).metodoCalculo("FIXED_AMOUNT")
                .condicionesJson("{\"tipo_suministro\":\"LUZ\"}").calculoJson("{\"importe\":120}").activo(true).build());
        return new Escenario(com.getId(), conjunto);
    }

    private record Escenario(Integer idComercializadora, McasConjuntoReglas conjunto) {}

    private McasPropuesta calcular(Escenario e, Consumer<Contrato.ContratoBuilder> ajustes) {
        String numero = "OPE-" + System.nanoTime();
        Cliente cliente = clientes.save(Cliente.builder().nombre("Cliente " + numero).cif(numero).build());
        Contrato.ContratoBuilder datos = Contrato.builder().cliente(cliente).contrato(numero)
                .fechaInicio(Date.valueOf("2026-03-16")).idComercializadoraActual(e.idComercializadora())
                .consumoAnual(50_000d).mcasTipoPrecio("FIJO").mcasTipoSuministro("LUZ").mcasNumeroPagos(1);
        ajustes.accept(datos);
        return mcas.iniciarCalculo(contratos.save(datos.build()).getId());
    }

    private Contrato contrato(Escenario e, String numero, String cups) {
        Cliente cliente = clientes.save(Cliente.builder().nombre("Cliente " + numero).cif(numero).build());
        PuntoSuministro punto = cups == null ? null
                : puntos.save(PuntoSuministro.builder().cliente(cliente).CUPSluz(cups).build());
        return contratos.save(Contrato.builder().cliente(cliente).puntoSuministro(punto).contrato(numero)
                .fechaInicio(Date.valueOf("2026-03-16")).idComercializadoraActual(e.idComercializadora()).build());
    }

    private void cambiarConsumo(Integer idContrato, double consumo) {
        Contrato c = contratos.findById(idContrato).orElseThrow();
        c.setConsumoAnual(consumo);
        contratos.save(c);
    }
}
