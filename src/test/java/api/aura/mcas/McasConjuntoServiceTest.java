package api.aura.mcas;

import api.aura.dto.mcas.McasIncidenciaDTO;
import api.aura.dto.mcas.McasResultadoLoteDTO;
import api.aura.entity.Contrato;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.repository.ContratoRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasPropuestaRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.McasConjuntoService;
import api.aura.service.mcas.McasProposalService;
import api.aura.service.mcas.McasRuleSetValidator;
import api.aura.service.mcas.McasValoresRangoService;
import api.aura.service.mcas.engine.ContextoCalculo;
import api.aura.service.mcas.engine.MotorMcas;
import api.aura.service.mcas.engine.ResultadoCalculo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Configurador no técnico (plan de usabilidad 30/09/2026, A7, A8, C1, C3, C4 y C5)
 * contra H2 y el motor real.
 *
 * <p>No es {@code @Transactional} a propósito: «Activar en lote» y el guardado de la
 * tabla abren sus propias transacciones y lo que se comprueba es justo qué queda
 * confirmado cuando una falla. Cada prueba usa una comercializadora nueva, así que
 * no interfiere con los datos de otras clases.</p>
 */
@SpringBootTest
class McasConjuntoServiceTest {

    private static final LocalDate MARZO = LocalDate.of(2026, 3, 1);
    private static final LocalDate OCTUBRE = LocalDate.of(2026, 10, 1);

    @Autowired private McasConjuntoService servicio;
    @Autowired private McasValoresRangoService tablas;
    @Autowired private MotorMcas motor;
    @Autowired private ModuloConfiguracionService modulos;
    @Autowired private McasConjuntoReglasRepository conjuntos;
    @Autowired private McasReglaComisionRepository reglas;
    @Autowired private McasValorRangoRepository rangos;
    @Autowired private McasPropuestaRepository propuestas;
    @Autowired private ContratoRepository contratos;
    @Autowired private TBComercializadorasRepository comercializadoras;
    @Autowired private PlatformTransactionManager transacciones;

    @AfterEach
    void apagarIndexado() {
        modulos.desactivar(McasProposalService.FLAG_INDEXADO);
    }

    // =====================================================================
    // A7 · Volver a borrador
    // =====================================================================

    @Test
    void una_tabla_validada_sin_propuestas_vuelve_a_borrador() {
        McasConjuntoReglas validado = conjunto(comercializadora("Volver"), "FIJO", MARZO, "VALIDADO", "Volver 2026-03");

        McasConjuntoReglas resultado = servicio.volverABorrador(validado.getId());

        assertThat(resultado.getEstado()).isEqualTo("BORRADOR");
        assertThat(conjuntos.findById(validado.getId()).orElseThrow().getEstado()).isEqualTo("BORRADOR");
    }

    @Test
    void no_vuelve_a_borrador_si_no_esta_validada_o_ya_tiene_propuestas() {
        Integer com = comercializadora("NoVuelve");
        McasConjuntoReglas publicado = conjunto(com, "FIJO", MARZO, "PUBLICADO", "Publicada");
        McasConjuntoReglas conPropuesta = conjunto(com, "FIJO", OCTUBRE, "VALIDADO", "Con propuesta");
        propuesta(conPropuesta.getId(), contrato(OCTUBRE), "PENDIENTE_VALIDACION");

        assertThatThrownBy(() -> servicio.volverABorrador(publicado.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("validada");
        assertThatThrownBy(() -> servicio.volverABorrador(conPropuesta.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("comisiones calculadas");
        assertThatThrownBy(() -> servicio.volverABorrador(-1)).isInstanceOf(NoSuchElementException.class);
        assertThat(conjuntos.findById(conPropuesta.getId()).orElseThrow().getEstado()).isEqualTo("VALIDADO");
    }

    // =====================================================================
    // A8 · Fase indexada
    // =====================================================================

    @Test
    void la_fase_indexada_se_bloquea_con_el_interruptor_apagado_y_se_permite_encendido() {
        McasConjuntoReglas indexado = conjunto(comercializadora("Indexado"), "INDEXADO", MARZO, "BORRADOR", "Indexado");
        regla(indexado, "INDEX", "Indexado · pyme", 10, "{\"segmento\":\"PYME\"}", "FEE_PERCENTAGE",
                "{\"porcentaje_aplicacion_fee\":0.52}", true);
        modulos.desactivar(McasProposalService.FLAG_INDEXADO);

        assertThat(servicio.motivoFaseBloqueada(indexado)).isEqualTo(McasConjuntoService.FASE_INDEXADA_DESACTIVADA)
                .isEqualTo("Las tablas de precio indexado no se pueden activar con el interruptor «Indexado» apagado: "
                        + "enciéndalo en Configuración MCAS, en «Interruptores del módulo».");
        var comprobacion = servicio.comprobacion(indexado.getId(), true);
        assertThat(comprobacion.puedeActivar()).isFalse();
        assertThat(comprobacion.incidencias()).extracting(McasIncidenciaDTO::getMensaje)
                .contains(McasConjuntoService.FASE_INDEXADA_DESACTIVADA);
        assertThatThrownBy(() -> servicio.activar(indexado.getId(), true))
                .isInstanceOf(IllegalStateException.class).hasMessage(McasConjuntoService.FASE_INDEXADA_DESACTIVADA);
        assertThat(estado(indexado)).isEqualTo("BORRADOR");

        modulos.activar(McasProposalService.FLAG_INDEXADO);

        assertThat(servicio.motivoFaseBloqueada(indexado)).isNull();
        assertThat(servicio.comprobacion(indexado.getId(), true).puedeActivar()).isTrue();
        assertThat(servicio.activar(indexado.getId(), true).getEstado()).isEqualTo("PUBLICADO");
        assertThat(servicio.motivoFaseBloqueada(McasConjuntoReglas.builder().fasePrecio("FIJO").build())).isNull();
    }

    // =====================================================================
    // C4 · Nueva versión
    // =====================================================================

    @Test
    void nueva_version_copia_en_borrador_las_reglas_activas_e_inactivas_y_sus_valores() {
        Integer com = comercializadora("Copia");
        McasConjuntoReglas origen = conjunto(com, "FIJO", MARZO, "PUBLICADO", "Endesa 2026-03");
        origen.setDescripcion("Tabla de marzo");
        conjuntos.save(origen);
        McasReglaComision fija = regla(origen, "FIJA", "Copia · residencial", 10,
                "{\"segmento\":\"RESIDENCIAL\"}", "FIXED_AMOUNT", "{\"importe\":110}", true);
        McasReglaComision inactiva = regla(origen, "INACTIVA", "Copia · antigua", 20,
                "{\"segmento\":\"OTRO\"}", "FIXED_AMOUNT", "{\"importe\":5}", false);
        McasReglaComision matriz = regla(origen, "MATRIZ", "Copia · pyme luz 3.0TD", 30,
                "{\"segmento\":\"PYME\"}", "MATRIX_LOOKUP", "{}", true);
        guardarMatriz(matriz, "243.36", "411.84");

        McasConjuntoReglas copia = servicio.nuevaVersion(origen.getId(), OCTUBRE, null);

        assertThat(copia.getId()).isNotEqualTo(origen.getId());
        assertThat(copia.getEstado()).isEqualTo("BORRADOR");
        assertThat(copia.getIdConjuntoOrigen()).isEqualTo(origen.getId());
        assertThat(copia.getVigenciaDesde()).isEqualTo(OCTUBRE);
        assertThat(copia.getVigenciaHasta()).isNull();
        assertThat(copia.getNombre()).isEqualTo("Endesa 2026-10");
        assertThat(copia.getDescripcion()).isEqualTo("Tabla de marzo");
        assertThat(copia.getCodigo()).isEqualTo("COPIA-FIJO-20261001");
        assertThat(copia.getAprobadoPor()).isNull();

        List<McasReglaComision> copiadas = reglas.findByIdConjuntoReglasOrderByPrioridadAsc(copia.getId());
        assertThat(copiadas).extracting(McasReglaComision::getCodigo).containsExactly("FIJA", "INACTIVA", "MATRIZ");
        assertThat(copiadas).extracting(McasReglaComision::getNombre)
                .containsExactly("Copia · residencial", "Copia · antigua", "Copia · pyme luz 3.0TD");
        assertThat(copiadas).extracting(McasReglaComision::getActivo).containsExactly(true, false, true);
        assertThat(copiadas.get(0).getCalculoJson()).isEqualTo("{\"importe\":110}");
        assertThat(copiadas).extracting(McasReglaComision::getId)
                .doesNotContain(fija.getId(), inactiva.getId(), matriz.getId());

        List<McasValorRango> valoresCopia = rangos.findByIdReglaOrderByOrdenAsc(copiadas.get(2).getId());
        List<McasValorRango> valoresOrigen = rangos.findByIdReglaOrderByOrdenAsc(matriz.getId());
        assertThat(valoresCopia).hasSize(4);
        assertThat(McasValoresRangoService.firma(valoresCopia)).isEqualTo(McasValoresRangoService.firma(valoresOrigen));
        assertThat(valoresOrigen).hasSize(4);
        assertThat(estado(origen)).isEqualTo("PUBLICADO");

        McasConjuntoReglas otra = servicio.nuevaVersion(origen.getId(), OCTUBRE.plusMonths(1), "  Endesa noviembre ");
        assertThat(otra.getNombre()).isEqualTo("Endesa noviembre");
    }

    @Test
    void la_nueva_version_debe_empezar_despues_que_la_original() {
        McasConjuntoReglas origen = conjunto(comercializadora("Fecha"), "FIJO", OCTUBRE, "PUBLICADO", "Fecha");

        assertThatThrownBy(() -> servicio.nuevaVersion(origen.getId(), OCTUBRE, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("después del 01/10/2026");
        assertThatThrownBy(() -> servicio.nuevaVersion(origen.getId(), null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Indique la fecha de inicio de la nueva versión.");
    }

    // =====================================================================
    // C5 · Comprobar y activar
    // =====================================================================

    @Test
    void activar_cierra_la_vigencia_anterior_y_el_motor_elige_la_version_por_fecha() {
        Integer com = comercializadora("Activar");
        McasConjuntoReglas anterior = conjunto(com, "FIJO", MARZO, "PUBLICADO", "Activar 2026-03");
        regla(anterior, "LUZ", "Activar · luz", 10, "{\"tipo_suministro\":\"LUZ\"}", "FIXED_AMOUNT",
                "{\"importe\":100}", true);
        McasConjuntoReglas nueva = servicio.nuevaVersion(anterior.getId(), OCTUBRE, null);
        McasReglaComision copiada = reglas.findByIdConjuntoReglas(nueva.getId()).get(0);
        copiada.setCalculoJson("{\"importe\":200}");
        reglas.save(copiada);

        var comprobacion = servicio.comprobacion(nueva.getId(), true);
        assertThat(comprobacion.incidencias()).isEmpty();
        assertThat(comprobacion.puedeActivar()).isTrue();
        assertThat(comprobacion.cambios()).contains("Se cerrará «Activar 2026-03» el 30/09/2026");
        assertThat(estado(nueva)).as("comprobar no cambia nada").isEqualTo("BORRADOR");
        assertThat(conjuntos.findById(anterior.getId()).orElseThrow().getVigenciaHasta()).isNull();

        McasConjuntoReglas activada = servicio.activar(nueva.getId(), true);

        assertThat(activada.getEstado()).isEqualTo("PUBLICADO");
        assertThat(activada.getFechaAprobacion()).isNotNull();
        McasConjuntoReglas cerrada = conjuntos.findById(anterior.getId()).orElseThrow();
        assertThat(cerrada.getEstado()).isEqualTo("PUBLICADO");
        assertThat(cerrada.getVigenciaHasta()).isEqualTo(LocalDate.of(2026, 9, 30));

        assertThat(calcular(com, LocalDate.of(2026, 9, 30)).getImporteTotal()).isEqualByComparingTo("100");
        assertThat(calcular(com, OCTUBRE).getImporteTotal()).isEqualByComparingTo("200");
        assertThat(calcular(com, OCTUBRE).getIdConjuntoReglas()).isEqualTo(nueva.getId());
    }

    @Test
    void activar_sin_cerrar_la_anterior_no_se_permite_si_coinciden_en_fechas() {
        Integer com = comercializadora("SinCerrar");
        McasConjuntoReglas anterior = conjunto(com, "FIJO", MARZO, "PUBLICADO", "SinCerrar 2026-03");
        regla(anterior, "LUZ", "SinCerrar · luz", 10, "{\"tipo_suministro\":\"LUZ\"}", "FIXED_AMOUNT",
                "{\"importe\":100}", true);
        McasConjuntoReglas nueva = servicio.nuevaVersion(anterior.getId(), OCTUBRE, null);

        var comprobacion = servicio.comprobacion(nueva.getId(), false);

        assertThat(comprobacion.puedeActivar()).isFalse();
        assertThat(comprobacion.cambios()).noneMatch(c -> c.startsWith("Se cerrará"));
        assertThatThrownBy(() -> servicio.activar(nueva.getId(), false))
                .isInstanceOf(McasRuleSetValidator.IncidenciasException.class);
        assertThat(conjuntos.findById(anterior.getId()).orElseThrow().getVigenciaHasta()).isNull();
    }

    @Test
    void activar_con_errores_no_cambia_nada_e_informa_por_regla() {
        Integer com = comercializadora("Errores");
        McasConjuntoReglas anterior = conjunto(com, "FIJO", MARZO, "PUBLICADO", "Errores 2026-03");
        McasConjuntoReglas nueva = conjunto(com, "FIJO", OCTUBRE, "BORRADOR", "Errores 2026-10");
        regla(nueva, "SIN_IMPORTE", "Errores · sin importe", 10, "{\"segmento\":\"PYME\"}", "FIXED_AMOUNT", "{}", true);

        var error = catchThrowableOfType(() -> servicio.activar(nueva.getId(), true),
                McasRuleSetValidator.IncidenciasException.class);

        assertThat(error.getMessage()).contains("«Errores 2026-10»").contains("1 problema");
        assertThat(error.getIncidencias()).extracting(McasIncidenciaDTO::getRegla).containsExactly("Errores · sin importe");
        assertThat(error.getDetalles()).containsExactly(
                "Errores · sin importe: indique el importe fijo de la comisión (un número).");
        assertThat(estado(nueva)).isEqualTo("BORRADOR");
        assertThat(conjuntos.findById(anterior.getId()).orElseThrow().getVigenciaHasta())
                .as("la vigencia anterior no se cierra si la activación falla").isNull();
    }

    @Test
    void no_se_cierra_una_tabla_activa_que_empieza_el_mismo_dia_o_despues() {
        Integer com = comercializadora("MismoDia");
        McasConjuntoReglas activa = conjunto(com, "FIJO", OCTUBRE, "PUBLICADO", "MismoDia activa");
        McasConjuntoReglas nueva = conjunto(com, "FIJO", OCTUBRE, "BORRADOR", "MismoDia nueva");
        regla(nueva, "LUZ", "MismoDia · luz", 10, "{\"tipo_suministro\":\"LUZ\"}", "FIXED_AMOUNT",
                "{\"importe\":100}", true);

        var comprobacion = servicio.comprobacion(nueva.getId(), true);

        assertThat(comprobacion.puedeActivar()).isFalse();
        assertThat(comprobacion.incidencias()).extracting(McasIncidenciaDTO::getMensaje).containsExactly(
                "La tabla activa «MismoDia activa» empieza el 01/10/2026, el mismo día o después que esta, "
                        + "y no se puede cerrar: cambie la fecha de inicio de esta tabla o archive «MismoDia activa».");
        assertThatThrownBy(() -> servicio.activar(nueva.getId(), true))
                .isInstanceOf(McasRuleSetValidator.IncidenciasException.class);
        assertThat(conjuntos.findById(activa.getId()).orElseThrow().getVigenciaHasta()).isNull();
    }

    @Test
    void la_comprobacion_avisa_de_las_propuestas_pendientes_calculadas_con_la_version_anterior() {
        Integer com = comercializadora("Avisos");
        McasConjuntoReglas anterior = conjunto(com, "FIJO", MARZO, "PUBLICADO", "Avisos 2026-03");
        regla(anterior, "LUZ", "Avisos · luz", 10, "{\"tipo_suministro\":\"LUZ\"}", "FIXED_AMOUNT",
                "{\"importe\":100}", true);
        McasConjuntoReglas nueva = servicio.nuevaVersion(anterior.getId(), OCTUBRE, null);
        propuesta(anterior.getId(), contrato(OCTUBRE.plusDays(4)), "PENDIENTE_VALIDACION");
        propuesta(anterior.getId(), contrato(OCTUBRE), "REQUIERE_REVISION_MANUAL");
        propuesta(anterior.getId(), contrato(LocalDate.of(2026, 9, 15)), "PENDIENTE_VALIDACION");  // sigue con la anterior
        propuesta(anterior.getId(), contrato(OCTUBRE.plusDays(4)), "FINALIZADO_APROBADO");          // ya cerrada

        var comprobacion = servicio.comprobacion(nueva.getId(), true);

        assertThat(comprobacion.puedeActivar()).isTrue();
        assertThat(comprobacion.avisos()).containsExactly(
                "Hay 2 propuestas pendientes calculadas con la versión anterior para contratos que empiezan "
                        + "desde el 01/10/2026; no se recalculan solas: recalcúlelas en Comisiones MCAS.");
    }

    @Test
    void activar_en_lote_activa_cada_tabla_por_separado_y_devuelve_el_resultado_de_cada_una() {
        Integer com = comercializadora("Lote");
        McasConjuntoReglas buena = conjunto(com, "FIJO", MARZO, "BORRADOR", "Lote buena");
        regla(buena, "LUZ", "Lote · luz", 10, "{\"tipo_suministro\":\"LUZ\"}", "FIXED_AMOUNT",
                "{\"importe\":100}", true);
        McasConjuntoReglas mala = conjunto(comercializadora("LoteMala"), "FIJO", MARZO, "BORRADOR", "Lote mala");

        List<McasResultadoLoteDTO> resultados = servicio.activarLote(
                List.of(buena.getId(), mala.getId(), -5), true);

        assertThat(resultados).extracting(McasResultadoLoteDTO::getId)
                .containsExactly(buena.getId().longValue(), mala.getId().longValue(), -5L);
        assertThat(resultados).extracting(McasResultadoLoteDTO::isOk).containsExactly(true, false, false);
        assertThat(resultados).extracting(McasResultadoLoteDTO::getEstado).containsExactly("PUBLICADO", "BORRADOR", null);
        assertThat(resultados.get(0).getMensaje()).isEqualTo("«Lote buena» activada desde el 01/03/2026.");
        assertThat(resultados.get(1).getMensaje()).contains("«Lote mala»").contains("ninguna regla activa");
        assertThat(estado(buena)).isEqualTo("PUBLICADO");
        assertThat(estado(mala)).isEqualTo("BORRADOR");
        assertThatThrownBy(() -> servicio.activarLote(List.of(), true)).isInstanceOf(IllegalArgumentException.class);
    }

    // =====================================================================
    // C1 · Nombres legibles y códigos automáticos
    // =====================================================================

    @Test
    void sin_codigo_se_genera_uno_unico_para_la_tabla_y_para_la_regla() {
        // Sin acentos y solo la primera palabra.
        Integer com = comercializadoras.save(TBComercializadoras.builder()
                .nombre("Eléctrica Ñandú, S.L. " + System.nanoTime()).build()).getId();
        McasConjuntoReglas datos = McasConjuntoReglas.builder().idComercializadora(com).fasePrecio("FIJO")
                .vigenciaDesde(OCTUBRE).nombre(" Eléctrica 2026-10 ").descripcion("Tabla nueva").build();
        String codigoEsperado = "ELECTRICA-FIJO-20261001";
        conjuntos.findByCodigo(codigoEsperado).ifPresent(c -> {
            c.setCodigo("ELECTRICA-ANTIGUA-" + System.nanoTime());
            conjuntos.save(c);
        });

        McasConjuntoReglas primera = servicio.crear(datos);
        McasConjuntoReglas segunda = servicio.crear(McasConjuntoReglas.builder().codigo("  ").idComercializadora(com)
                .fasePrecio("FIJO").vigenciaDesde(OCTUBRE).build());

        assertThat(primera.getCodigo()).isEqualTo(codigoEsperado);
        assertThat(primera.getNombre()).isEqualTo("Eléctrica 2026-10");
        assertThat(primera.getDescripcion()).isEqualTo("Tabla nueva");
        assertThat(primera.getEstado()).isEqualTo("BORRADOR");
        assertThat(segunda.getCodigo()).isEqualTo(codigoEsperado + "-2");

        McasReglaComision r1 = servicio.crearRegla(primera.getId(), McasReglaComision.builder()
                .nombre("Iris · pyme luz · 44 % del FEE").metodoCalculo("FEE_PERCENTAGE").build());
        McasReglaComision r2 = servicio.crearRegla(primera.getId(), McasReglaComision.builder()
                .nombre("Iris · pyme luz · 44 % del FEE").metodoCalculo("FEE_PERCENTAGE").build());
        McasReglaComision conCodigo = servicio.crearRegla(primera.getId(), McasReglaComision.builder()
                .codigo("PROPIO").nombre("Algo").metodoCalculo("MANUAL").build());

        assertThat(r1.getCodigo()).isEqualTo("IRIS_PYME_LUZ_44_DEL_FEE");
        assertThat(r2.getCodigo()).isEqualTo("IRIS_PYME_LUZ_44_DEL_FEE-2");
        assertThat(conCodigo.getCodigo()).isEqualTo("PROPIO");
        assertThat(r1.getActivo()).isTrue();
        assertThat(r1.getIdConjuntoReglas()).isEqualTo(primera.getId());
    }

    @Test
    void crear_exige_comercializadora_y_fecha_y_las_reglas_solo_en_borrador() {
        assertThatThrownBy(() -> servicio.crear(McasConjuntoReglas.builder().vigenciaDesde(OCTUBRE).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Elija la comercializadora de la tabla.");
        assertThatThrownBy(() -> servicio.crear(McasConjuntoReglas.builder().idComercializadora(1).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Indique desde qué fecha se aplica la tabla.");
        McasConjuntoReglas publicada = conjunto(comercializadora("Regla"), "FIJO", MARZO, "PUBLICADO", "Regla");
        assertThatThrownBy(() -> servicio.crearRegla(publicada.getId(), McasReglaComision.builder()
                .nombre("Nueva").metodoCalculo("MANUAL").build()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("borrador");
    }

    // =====================================================================
    // C3 · Tabla completa con firma
    // =====================================================================

    @Test
    void reemplazar_la_tabla_renumera_las_celdas_y_devuelve_la_misma_firma_que_una_lectura() {
        McasConjuntoReglas borrador = conjunto(comercializadora("Tabla"), "FIJO", MARZO, "BORRADOR", "Tabla");
        McasReglaComision matriz = regla(borrador, "MATRIZ", "Tabla · matriz", 10, "{}", "MATRIX_LOOKUP", "{}", true);
        guardarMatriz(matriz, "1", "2");
        var leida = tablas.leer(matriz.getId());
        assertThat(leida.metodo()).isEqualTo("MATRIX_LOOKUP");
        assertThat(leida.valores()).hasSize(4);

        var guardada = tablas.reemplazar(matriz.getId(), leida.firma(), List.of(
                celda(7, "consumo_kwh", "0", "50000", "243.36"),
                celda(7, "potencia_kw", "15", "30", "243.36"),
                celda(3, "consumo_kwh", "50000", "100000", "411.84"),
                celda(3, "potencia_kw", "15", "30", "411.84"),
                celda(9, "consumo_kwh", "100000", "200000", "500"),
                celda(9, "potencia_kw", "15", "30", "500")));

        List<McasValorRango> enBbdd = rangos.findByIdReglaOrderByOrdenAsc(matriz.getId());
        assertThat(enBbdd).extracting(McasValorRango::getOrden).containsExactly(1, 1, 2, 2, 3, 3);
        assertThat(enBbdd).filteredOn(v -> v.getOrden() == 1).extracting(McasValorRango::getValorResultado)
                .allSatisfy(v -> assertThat(v).isEqualByComparingTo("411.84"));
        assertThat(enBbdd).allMatch(v -> matriz.getId().equals(v.getIdRegla()) && "EUR".equals(v.getUnidadResultado()));
        assertThat(guardada.valores()).extracting(McasValorRango::getOrden).containsExactly(1, 1, 2, 2, 3, 3);
        assertThat(guardada.firma()).isNotEqualTo(leida.firma()).isEqualTo(tablas.leer(matriz.getId()).firma());
    }

    @Test
    void con_una_firma_obsoleta_no_se_guarda_la_tabla() {
        McasConjuntoReglas borrador = conjunto(comercializadora("Firma"), "FIJO", MARZO, "BORRADOR", "Firma");
        McasReglaComision matriz = regla(borrador, "MATRIZ", "Firma · matriz", 10, "{}", "MATRIX_LOOKUP", "{}", true);
        guardarMatriz(matriz, "1", "2");
        String firma = tablas.leer(matriz.getId()).firma();
        McasValorRango otraPersona = rangos.findByIdReglaOrderByOrdenAsc(matriz.getId()).get(0);
        otraPersona.setValorResultado(new BigDecimal("9"));
        rangos.save(otraPersona);

        assertThatThrownBy(() -> tablas.reemplazar(matriz.getId(), firma, List.of(
                celda(1, "consumo_kwh", "0", "10", "5"), celda(1, "potencia_kw", "0", "10", "5"))))
                .isInstanceOf(McasValoresRangoService.FirmaObsoletaException.class)
                .hasMessageContaining("Vuelva a cargarla");
        assertThat(rangos.findByIdReglaOrderByOrdenAsc(matriz.getId())).hasSize(4)
                .anySatisfy(v -> assertThat(v.getValorResultado()).isEqualByComparingTo("9"));
        assertThatThrownBy(() -> tablas.reemplazar(matriz.getId(), null, List.of()))
                .isInstanceOf(McasValoresRangoService.FirmaObsoletaException.class);
    }

    @Test
    void la_tabla_solo_se_cambia_en_borrador_y_en_reglas_de_tabla() {
        Integer com = comercializadora("SoloBorrador");
        McasConjuntoReglas publicado = conjunto(com, "FIJO", MARZO, "PUBLICADO", "Publicada");
        McasReglaComision matriz = regla(publicado, "MATRIZ", "Publicada · matriz", 10, "{}", "MATRIX_LOOKUP", "{}", true);
        guardarMatriz(matriz, "1", "2");
        String firma = tablas.leer(matriz.getId()).firma();
        McasConjuntoReglas borrador = conjunto(com, "FIJO", OCTUBRE, "BORRADOR", "Borrador");
        McasReglaComision fija = regla(borrador, "FIJA", "Borrador · fija", 10, "{}", "FIXED_AMOUNT", "{\"importe\":1}", true);

        assertThatThrownBy(() -> tablas.reemplazar(matriz.getId(), firma, List.of(
                celda(1, "consumo_kwh", "0", "10", "5"), celda(1, "potencia_kw", "0", "10", "5"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("borrador");
        assertThat(McasValoresRangoService.firma(rangos.findByIdReglaOrderByOrdenAsc(matriz.getId()))).isEqualTo(firma);
        assertThatThrownBy(() -> tablas.reemplazar(fija.getId(), tablas.leer(fija.getId()).firma(), List.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no usa una tabla");
        assertThatThrownBy(() -> tablas.leer(-1)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void una_tabla_con_errores_no_se_guarda_y_las_incidencias_llevan_el_orden() {
        McasConjuntoReglas borrador = conjunto(comercializadora("TablaMal"), "FIJO", MARZO, "BORRADOR", "TablaMal");
        McasReglaComision matriz = regla(borrador, "MATRIZ", "TablaMal · matriz", 10, "{}", "MATRIX_LOOKUP", "{}", true);
        guardarMatriz(matriz, "1", "2");
        String firma = tablas.leer(matriz.getId()).firma();

        // La rejilla numera sus celdas y puede dejar huecos (celdas vacías): las incidencias
        // llevan el orden recibido para que la pantalla marque la celda correcta.
        var error = catchThrowableOfType(() -> tablas.reemplazar(matriz.getId(), firma, List.of(
                celda(1, "consumo_kwh", "0", "100", "5"), celda(1, "potencia_kw", "0", "10", "5"),
                celda(4, "consumo_kwh", "50", "200", null), celda(4, "potencia_kw", "0", "10", null))),
                McasRuleSetValidator.IncidenciasException.class);

        assertThat(error.getIncidencias()).extracting(McasIncidenciaDTO::getOrden).containsOnly(4);
        assertThat(error.getIncidencias()).extracting(McasIncidenciaDTO::getMensaje)
                .contains("La celda 4 no tiene importe.", "Las celdas 1 y 4 se solapan: un mismo contrato encajaría en las dos.");
        assertThat(McasValoresRangoService.firma(rangos.findByIdReglaOrderByOrdenAsc(matriz.getId()))).isEqualTo(firma);
    }

    @Test
    void reemplazar_la_tabla_es_atomico_si_falla_al_guardar() {
        McasConjuntoReglas borrador = conjunto(comercializadora("Atomico"), "FIJO", MARZO, "BORRADOR", "Atomico");
        McasReglaComision matriz = regla(borrador, "MATRIZ", "Atomico · matriz", 10, "{}", "MATRIX_LOOKUP", "{}", true);
        guardarMatriz(matriz, "1", "2");
        String firma = tablas.leer(matriz.getId()).firma();
        McasValorRango demasiadoLarga = celda(2, "potencia_kw", "0", "10", "5");
        demasiadoLarga.setUnidadResultado("U".repeat(40));   // la columna admite 20: falla al insertar, tras borrar

        assertThatThrownBy(() -> tablas.reemplazar(matriz.getId(), firma, List.of(
                celda(1, "consumo_kwh", "0", "10", "5"), celda(1, "potencia_kw", "0", "10", "5"),
                celda(2, "consumo_kwh", "10", "20", "5"), demasiadoLarga)))
                .isInstanceOf(RuntimeException.class);

        assertThat(McasValoresRangoService.firma(rangos.findByIdReglaOrderByOrdenAsc(matriz.getId())))
                .as("la tabla anterior sigue intacta").isEqualTo(firma);
    }

    // =====================================================================
    // Correcciones de la revisión de código
    // =====================================================================

    @Test
    void activar_cierra_tambien_una_tabla_activa_de_fase_ambos_y_avisa_de_la_otra_fase() {
        // H13: el validador da por solapadas una tabla «ambos» y una «fijo»; «Activar» tiene
        // que proponer el cierre, no solo avisar de que coinciden en fechas.
        Integer com = comercializadora("Ambos");
        McasConjuntoReglas ambos = conjunto(com, "AMBOS", MARZO, "PUBLICADO", "Ambos 2026-03");
        McasConjuntoReglas fijo = conjunto(com, "FIJO", OCTUBRE, "BORRADOR", "Fijo 2026-10");
        regla(fijo, "LUZ", "Fijo · luz", 10, "{\"tipo_suministro\":\"LUZ\"}", "FIXED_AMOUNT", "{\"importe\":100}", true);

        var comprobacion = servicio.comprobacion(fijo.getId(), true);

        assertThat(comprobacion.incidencias()).isEmpty();
        assertThat(comprobacion.puedeActivar()).isTrue();
        assertThat(comprobacion.cambios()).contains("Se cerrará «Ambos 2026-03» el 30/09/2026");
        assertThat(comprobacion.avisos()).containsExactly("«Ambos 2026-03» también se aplicaba a los contratos "
                + "indexados: desde el 01/10/2026 no tendrán tabla activa hasta que active una.");

        servicio.activar(fijo.getId(), true);

        assertThat(conjuntos.findById(ambos.getId()).orElseThrow().getVigenciaHasta()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(estado(fijo)).isEqualTo("PUBLICADO");
    }

    @Test
    void la_comprobacion_avisa_de_las_comisiones_de_la_comercializadora_que_esperan_una_tabla() {
        // H14: propuestas sin tabla (idConjuntoReglas nulo) de esa comercializadora y desde esa fecha.
        Integer com = comercializadora("Espera");
        String nombre = comercializadoras.findById(com).orElseThrow().getNombre();
        Integer otra = comercializadora("EsperaOtra");
        McasConjuntoReglas primera = conjunto(com, "FIJO", OCTUBRE, "BORRADOR", "Espera 2026-10");
        regla(primera, "LUZ", "Espera · luz", 10, "{\"tipo_suministro\":\"LUZ\"}", "FIXED_AMOUNT",
                "{\"importe\":100}", true);
        propuesta(null, contrato(com, OCTUBRE.plusDays(3), "FIJO"), "REQUIERE_REVISION_MANUAL");
        propuesta(null, contrato(com, OCTUBRE, "FIJO"), "REQUIERE_DATOS");
        propuesta(null, contrato(com, OCTUBRE.plusDays(3), null), "PENDIENTE_VALIDACION");
        propuesta(null, contrato(com, LocalDate.of(2026, 9, 30), "FIJO"), "REQUIERE_REVISION_MANUAL");  // antes
        propuesta(null, contrato(otra, OCTUBRE.plusDays(3), "FIJO"), "REQUIERE_REVISION_MANUAL");      // otra
        propuesta(null, contrato(com, OCTUBRE.plusDays(3), "FIJO"), "FINALIZADO_APROBADO");            // cerrada
        propuesta(null, contrato(com, OCTUBRE.plusDays(3), "INDEXADO"), "REQUIERE_REVISION_MANUAL");   // otra fase

        var comprobacion = servicio.comprobacion(primera.getId(), true);

        assertThat(comprobacion.puedeActivar()).isTrue();
        assertThat(comprobacion.avisos()).containsExactly("Hay 3 comisiones de " + nombre
                + " esperando una tabla: recalcúlelas en Comisiones MCAS tras activar.");
    }

    @Test
    void dos_guardados_a_la_vez_de_la_misma_tabla_no_se_pisan_y_el_segundo_recibe_firma_obsoleta() throws Exception {
        // H7: el segundo guardado espera al bloqueo de la regla y compara la firma con lo ya
        // confirmado por el primero; sin bloqueo, leía la tabla vieja y mezclaba las filas.
        McasConjuntoReglas borrador = conjunto(comercializadora("Bloqueo"), "FIJO", MARZO, "BORRADOR", "Bloqueo");
        McasReglaComision matriz = regla(borrador, "MATRIZ", "Bloqueo · matriz", 10, "{}", "MATRIX_LOOKUP", "{}", true);
        guardarMatriz(matriz, "1", "2");
        String firma = tablas.leer(matriz.getId()).firma();
        TransactionTemplate transaccion = new TransactionTemplate(transacciones);
        CountDownLatch primeroGuardado = new CountDownLatch(1);
        ExecutorService hilo = Executors.newSingleThreadExecutor();
        try {
            Future<?> primero = hilo.submit(() -> transaccion.executeWithoutResult(estado -> {
                tablas.reemplazar(matriz.getId(), firma, List.of(
                        celda(1, "consumo_kwh", "0", "10", "7"), celda(1, "potencia_kw", "0", "10", "7")));
                primeroGuardado.countDown();
                dormir(300);   // conserva el bloqueo sin confirmar mientras llega el segundo
            }));
            assertThat(primeroGuardado.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> tablas.reemplazar(matriz.getId(), firma, List.of(
                    celda(1, "consumo_kwh", "0", "10", "8"), celda(1, "potencia_kw", "0", "10", "8"))))
                    .isInstanceOf(McasValoresRangoService.FirmaObsoletaException.class);
            primero.get(10, TimeUnit.SECONDS);
        } finally {
            hilo.shutdownNow();
        }

        assertThat(rangos.findByIdReglaOrderByOrdenAsc(matriz.getId())).hasSize(2)
                .allSatisfy(v -> assertThat(v.getValorResultado()).isEqualByComparingTo("7"));
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Integer contrato(Integer comercializadora, LocalDate inicio, String tipoPrecio) {
        return contratos.save(Contrato.builder().contrato("CS-" + System.nanoTime())
                .idComercializadoraActual(comercializadora).mcasTipoPrecio(tipoPrecio)
                .fechaInicio(Date.valueOf(inicio)).build()).getId();
    }

    private Integer comercializadora(String prefijo) {
        return comercializadoras.save(TBComercializadoras.builder()
                .nombre(prefijo + " " + System.nanoTime()).build()).getId();
    }

    private McasConjuntoReglas conjunto(Integer comercializadora, String fase, LocalDate desde, String estado, String nombre) {
        return conjuntos.save(McasConjuntoReglas.builder().codigo("CS-" + System.nanoTime())
                .nombre(nombre).idComercializadora(comercializadora).fasePrecio(fase).vigenciaDesde(desde)
                .estado(estado).build());
    }

    private McasReglaComision regla(McasConjuntoReglas conjunto, String codigo, String nombre, int prioridad,
                                    String condiciones, String metodo, String calculo, boolean activa) {
        return reglas.save(McasReglaComision.builder().idConjuntoReglas(conjunto.getId()).codigo(codigo)
                .nombre(nombre).prioridad(prioridad).condicionesJson(condiciones).metodoCalculo(metodo)
                .calculoJson(calculo).activo(activa).build());
    }

    /** Dos celdas consumo × potencia con los importes dados. */
    private void guardarMatriz(McasReglaComision regla, String importe1, String importe2) {
        List<McasValorRango> valores = new ArrayList<>(List.of(
                celda(1, "consumo_kwh", "0", "50000", importe1), celda(1, "potencia_kw", "15", "30", importe1),
                celda(2, "consumo_kwh", "50000", "100000", importe2), celda(2, "potencia_kw", "15", "30", importe2)));
        valores.forEach(v -> v.setIdRegla(regla.getId()));
        rangos.saveAll(valores);
    }

    private McasValorRango celda(int orden, String dimension, String min, String max, String importe) {
        return McasValorRango.builder().dimension(dimension)
                .valorMin(new BigDecimal(min)).valorMax(new BigDecimal(max)).minInclusivo(false).maxInclusivo(true)
                .valorResultado(importe == null ? null : new BigDecimal(importe)).unidadResultado("EUR").orden(orden).build();
    }

    private Integer contrato(LocalDate inicio) {
        return contratos.save(Contrato.builder().contrato("CS-" + System.nanoTime())
                .fechaInicio(Date.valueOf(inicio)).build()).getId();
    }

    private void propuesta(Integer idConjunto, Integer idContrato, String estado) {
        propuestas.save(McasPropuesta.builder().idContrato(idContrato).tipoEvento("NUEVO")
                .claveIdempotencia("CS-" + System.nanoTime()).estado(estado).idConjuntoReglas(idConjunto).build());
    }

    private String estado(McasConjuntoReglas conjunto) {
        return conjuntos.findById(conjunto.getId()).orElseThrow().getEstado();
    }

    private ResultadoCalculo calcular(Integer comercializadora, LocalDate fecha) {
        ResultadoCalculo resultado = motor.evaluar(ContextoCalculo.builder().idComercializadora(comercializadora)
                .tipoPrecio("FIJO").tipoSuministro("LUZ").segmento("PYME").esRenovacion(false)
                .fechaEfectiva(fecha).build());
        assertThat(resultado.getResultado()).as("%s", resultado.getCausaError())
                .isEqualTo(ResultadoCalculo.Resultado.CALCULADO);
        return resultado;
    }
}
