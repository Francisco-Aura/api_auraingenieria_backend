package api.aura.mcas;

import api.aura.controller.mcas.McasConjuntoReglasController;
import api.aura.controller.mcas.McasReglasController;
import api.aura.controller.mcas.McasProductosController;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.repository.mcas.McasProductoComercialRepository;
import api.aura.entity.mcas.McasProductoComercial;
import api.aura.service.mcas.McasConjuntoService;
import api.aura.service.mcas.McasRuleSetValidator;
import api.aura.service.mcas.McasValoresRangoService;
import api.aura.dto.mcas.McasIncidenciaDTO;
import api.aura.dto.mcas.McasResultadoLoteDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McasConfigurationControllerTest {

    @Test
    void producto_invalido_no_se_guarda() {
        var productos = mock(McasProductoComercialRepository.class);
        var controller = new McasProductosController(productos);
        var invalido = McasProductoComercial.builder().idComercializadora(7).codigo("PLAN")
                .nombre("Plan").tipoPrecio("OTRO").tipoSuministro("LUZ").build();

        var respuesta = controller.crear(invalido);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        verify(productos, never()).save(any());
    }

    @Test
    void listado_de_productos_sin_filtros_incluye_los_dados_de_baja() {
        var productos = mock(McasProductoComercialRepository.class);
        var controller = new McasProductosController(productos);
        var baja = McasProductoComercial.builder().id(8).codigo("BAJA").activo(false).build();
        when(productos.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(java.util.List.of(baja));

        var respuesta = controller.listar(null, null, null);

        assertThat(respuesta.getBody()).containsExactly(baja);
        verify(productos, never()).findByActivoTrue();
    }

    @Test
    void listado_de_reglas_incluye_las_inactivas_para_poder_reactivarlas() {
        var conjuntos = mock(McasConjuntoReglasRepository.class);
        var reglas = mock(McasReglaComisionRepository.class);
        var controller = new McasConjuntoReglasController(conjuntos, reglas,
                mock(McasValorRangoRepository.class), mock(McasRuleSetValidator.class), mock(McasConjuntoService.class));
        var activa = McasReglaComision.builder().id(1).idConjuntoReglas(3).prioridad(10).activo(true).build();
        var inactiva = McasReglaComision.builder().id(2).idConjuntoReglas(3).prioridad(20).activo(false).build();
        when(reglas.findByIdConjuntoReglasOrderByPrioridadAsc(3)).thenReturn(java.util.List.of(activa, inactiva));

        var respuesta = controller.reglas(3);

        assertThat(respuesta.getBody()).containsExactly(activa, inactiva);
        verify(reglas, never()).findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(anyInt());
    }

    @Test
    void conjunto_archivado_es_inmutable() {
        var conjuntos = mock(McasConjuntoReglasRepository.class);
        var reglas = mock(McasReglaComisionRepository.class);
        var validator = mock(McasRuleSetValidator.class);
        var rangos = mock(McasValorRangoRepository.class);
        var controller = new McasConjuntoReglasController(conjuntos, reglas, rangos, validator, mock(McasConjuntoService.class));
        var archivado = McasConjuntoReglas.builder().id(1).estado("ARCHIVADO").build();
        when(conjuntos.findById(1)).thenReturn(Optional.of(archivado));

        var respuesta = controller.actualizar(1, McasConjuntoReglas.builder().codigo("CAMBIO").build());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        verify(conjuntos, never()).save(any());
    }

    @Test
    void reglas_de_conjunto_validado_son_inmutables() {
        var reglas = mock(McasReglaComisionRepository.class);
        var rangos = mock(McasValorRangoRepository.class);
        var conjuntos = mock(McasConjuntoReglasRepository.class);
        var controller = new McasReglasController(reglas, rangos, conjuntos, mock(McasValoresRangoService.class));
        var regla = McasReglaComision.builder().id(2).idConjuntoReglas(3).build();
        when(reglas.findById(2)).thenReturn(Optional.of(regla));
        when(conjuntos.findById(3)).thenReturn(Optional.of(
                McasConjuntoReglas.builder().id(3).estado("VALIDADO").build()));

        var respuesta = controller.actualizar(2, McasReglaComision.builder().codigo("CAMBIO").build());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        verify(reglas, never()).save(any());
    }

    @Test
    void solo_un_conjunto_publicado_puede_archivarse() {
        var conjuntos = mock(McasConjuntoReglasRepository.class);
        var reglas = mock(McasReglaComisionRepository.class);
        var validator = mock(McasRuleSetValidator.class);
        var rangos = mock(McasValorRangoRepository.class);
        var controller = new McasConjuntoReglasController(conjuntos, reglas, rangos, validator, mock(McasConjuntoService.class));
        var borrador = McasConjuntoReglas.builder().id(4).estado("BORRADOR").build();
        when(conjuntos.findById(4)).thenReturn(Optional.of(borrador));

        var respuesta = controller.archivar(4);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        verify(conjuntos, never()).save(any());
    }

    @Test
    void cerrar_vigencia_de_un_conjunto_publicado_solo_la_adelanta() {
        var conjuntos = mock(McasConjuntoReglasRepository.class);
        var controller = new McasConjuntoReglasController(conjuntos, mock(McasReglaComisionRepository.class),
                mock(McasValorRangoRepository.class), mock(McasRuleSetValidator.class), mock(McasConjuntoService.class));
        var publicado = McasConjuntoReglas.builder().id(7).estado("PUBLICADO")
                .vigenciaDesde(java.time.LocalDate.of(2026, 3, 4)).build();
        when(conjuntos.findById(7)).thenReturn(Optional.of(publicado));
        when(conjuntos.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(controller.cerrarVigencia(7, java.time.LocalDate.of(2026, 3, 1)).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.cerrarVigencia(7, java.time.LocalDate.of(2026, 12, 31)).getStatusCode().value()).isEqualTo(200);
        assertThat(publicado.getVigenciaHasta()).isEqualTo(java.time.LocalDate.of(2026, 12, 31));
        assertThat(controller.cerrarVigencia(7, java.time.LocalDate.of(2027, 6, 30)).getStatusCode().value()).isEqualTo(400);
        assertThat(publicado.getVigenciaHasta()).isEqualTo(java.time.LocalDate.of(2026, 12, 31));
    }

    @Test
    void cerrar_vigencia_no_aplica_a_borradores() {
        var conjuntos = mock(McasConjuntoReglasRepository.class);
        var controller = new McasConjuntoReglasController(conjuntos, mock(McasReglaComisionRepository.class),
                mock(McasValorRangoRepository.class), mock(McasRuleSetValidator.class), mock(McasConjuntoService.class));
        when(conjuntos.findById(8)).thenReturn(Optional.of(McasConjuntoReglas.builder().id(8).estado("BORRADOR").build()));

        assertThat(controller.cerrarVigencia(8, java.time.LocalDate.of(2026, 12, 31)).getStatusCode().value()).isEqualTo(400);
        verify(conjuntos, never()).save(any());
    }

    @Test
    void eliminar_borrador_limpia_sus_rangos_y_reglas_en_orden() {
        var conjuntos = mock(McasConjuntoReglasRepository.class);
        var reglas = mock(McasReglaComisionRepository.class);
        var rangos = mock(McasValorRangoRepository.class);
        var validator = mock(McasRuleSetValidator.class);
        var controller = new McasConjuntoReglasController(conjuntos, reglas, rangos, validator, mock(McasConjuntoService.class));
        var borrador = McasConjuntoReglas.builder().id(5).estado("BORRADOR").build();
        var regla = McasReglaComision.builder().id(6).idConjuntoReglas(5).build();
        when(conjuntos.findById(5)).thenReturn(Optional.of(borrador));
        when(reglas.findByIdConjuntoReglas(5)).thenReturn(java.util.List.of(regla));

        var respuesta = controller.eliminar(5);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        var orden = inOrder(rangos, reglas, conjuntos);
        orden.verify(rangos).deleteByIdRegla(6);
        orden.verify(reglas).deleteAll(java.util.List.of(regla));
        orden.verify(conjuntos).deleteById(5);
    }

    // =====================================================================
    // Configurador no técnico (usabilidad 30/09/2026)
    // =====================================================================

    @Test
    void validar_y_publicar_una_tabla_indexada_con_el_interruptor_apagado_responden_400_con_el_motivo() {
        var conjuntos = mock(McasConjuntoReglasRepository.class);
        var validator = mock(McasRuleSetValidator.class);
        var servicio = mock(McasConjuntoService.class);
        var controller = new McasConjuntoReglasController(conjuntos, mock(McasReglaComisionRepository.class),
                mock(McasValorRangoRepository.class), validator, servicio);
        var borrador = McasConjuntoReglas.builder().id(1).estado("BORRADOR").fasePrecio("INDEXADO").build();
        var validado = McasConjuntoReglas.builder().id(2).estado("VALIDADO").fasePrecio("INDEXADO").build();
        when(conjuntos.findById(1)).thenReturn(Optional.of(borrador));
        when(conjuntos.findById(2)).thenReturn(Optional.of(validado));
        when(servicio.motivoFaseBloqueada(any())).thenReturn(McasConjuntoService.FASE_INDEXADA_DESACTIVADA);

        var validar = controller.validar(1);
        var publicar = controller.publicar(2);

        assertThat(validar.getStatusCode().value()).isEqualTo(400);
        assertThat(publicar.getStatusCode().value()).isEqualTo(400);
        assertThat(validar.getBody()).isEqualTo(Map.of("error", McasConjuntoService.FASE_INDEXADA_DESACTIVADA));
        assertThat(publicar.getBody()).isEqualTo(Map.of("error", McasConjuntoService.FASE_INDEXADA_DESACTIVADA));
        verifyNoInteractions(validator);
        verify(conjuntos, never()).save(any());
    }

    @Test
    void validar_con_errores_devuelve_detalles_e_incidencias() {
        var conjuntos = mock(McasConjuntoReglasRepository.class);
        var validator = mock(McasRuleSetValidator.class);
        var controller = new McasConjuntoReglasController(conjuntos, mock(McasReglaComisionRepository.class),
                mock(McasValorRangoRepository.class), validator, mock(McasConjuntoService.class));
        var borrador = McasConjuntoReglas.builder().id(1).estado("BORRADOR").fasePrecio("FIJO").build();
        var incidencia = new McasIncidenciaDTO(7, "Endesa · pyme luz 3.0TD", 12, "La celda 12 no tiene importe.");
        when(conjuntos.findById(1)).thenReturn(Optional.of(borrador));
        when(validator.validar(borrador)).thenReturn(new McasRuleSetValidator.Resultado(false,
                List.of("Endesa · pyme luz 3.0TD: la celda 12 no tiene importe."), List.of(incidencia)));

        var respuesta = controller.validar(1);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(cuerpo(respuesta)).containsEntry("incidencias", List.of(incidencia))
                .containsEntry("detalles", List.of("Endesa · pyme luz 3.0TD: la celda 12 no tiene importe."))
                .containsKey("error");
        assertThat(borrador.getEstado()).isEqualTo("BORRADOR");
    }

    @Test
    void activar_con_incidencias_responde_400_con_la_lista_y_sin_tabla_404() {
        var servicio = mock(McasConjuntoService.class);
        var controller = new McasConjuntoReglasController(mock(McasConjuntoReglasRepository.class),
                mock(McasReglaComisionRepository.class), mock(McasValorRangoRepository.class),
                mock(McasRuleSetValidator.class), servicio);
        var incidencia = new McasIncidenciaDTO(7, "Endesa · pyme luz 3.0TD", 12, "La celda 12 no tiene importe.");
        when(servicio.activar(3, true)).thenThrow(new McasRuleSetValidator.IncidenciasException(
                "No se puede activar «Endesa 2026-10»: hay 1 problema por resolver.", List.of(incidencia)));
        when(servicio.activar(4, false)).thenThrow(new NoSuchElementException("No existe la tabla de comisiones 4."));
        when(servicio.volverABorrador(5)).thenThrow(new IllegalStateException("Solo una tabla validada puede volver a borrador."));

        var conErrores = controller.activar(3, null);
        var noExiste = controller.activar(4, new McasConjuntoReglasController.Activacion(false));
        var noValidada = controller.volverABorrador(5);

        assertThat(conErrores.getStatusCode().value()).isEqualTo(400);
        assertThat(cuerpo(conErrores))
                .containsEntry("error", "No se puede activar «Endesa 2026-10»: hay 1 problema por resolver.")
                .containsEntry("incidencias", List.of(incidencia))
                .containsEntry("detalles", List.of("Endesa · pyme luz 3.0TD: la celda 12 no tiene importe."));
        assertThat(noExiste.getStatusCode().value()).isEqualTo(404);
        assertThat(noValidada.getStatusCode().value()).isEqualTo(400);
        assertThat(noValidada.getBody()).isEqualTo(Map.of("error", "Solo una tabla validada puede volver a borrador."));
    }

    @Test
    void activar_en_lote_devuelve_los_resultados_y_cierra_la_vigencia_por_defecto() {
        var servicio = mock(McasConjuntoService.class);
        var controller = new McasConjuntoReglasController(mock(McasConjuntoReglasRepository.class),
                mock(McasReglaComisionRepository.class), mock(McasValorRangoRepository.class),
                mock(McasRuleSetValidator.class), servicio);
        var resultados = List.of(McasResultadoLoteDTO.ok(1L, "PUBLICADO", "«A» activada desde el 01/10/2026."),
                McasResultadoLoteDTO.error(2L, "BORRADOR", "No se puede activar «B»"));
        when(servicio.activarLote(List.of(1, 2), true)).thenReturn(resultados);

        var respuesta = controller.activarLote(new McasConjuntoReglasController.Lote(List.of(1, 2), null));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isEqualTo(resultados);
    }

    @Test
    void la_tabla_con_firma_obsoleta_responde_409_y_con_errores_400() {
        var tablas = mock(McasValoresRangoService.class);
        var controller = new McasReglasController(mock(McasReglaComisionRepository.class),
                mock(McasValorRangoRepository.class), mock(McasConjuntoReglasRepository.class), tablas);
        var incidencia = new McasIncidenciaDTO(7, "Endesa · pyme luz 3.0TD", 2, "La celda 2 no tiene importe.");
        when(tablas.reemplazar(7, "vieja", List.of())).thenThrow(new McasValoresRangoService.FirmaObsoletaException());
        when(tablas.reemplazar(7, "buena", List.of())).thenThrow(
                new McasRuleSetValidator.IncidenciasException("La tabla tiene 1 problema; corríjalos antes de guardar.",
                        List.of(incidencia)));
        when(tablas.leer(8)).thenReturn(new McasValoresRangoService.Tabla("abc", "MATRIX_LOOKUP", List.of()));

        var obsoleta = controller.guardarTabla(7, new McasReglasController.CambioTabla("vieja", List.of()));
        var conErrores = controller.guardarTabla(7, new McasReglasController.CambioTabla("buena", List.of()));
        var leida = controller.tabla(8);

        assertThat(obsoleta.getStatusCode().value()).isEqualTo(409);
        assertThat(cuerpo(obsoleta)).containsKey("error");
        assertThat(conErrores.getStatusCode().value()).isEqualTo(400);
        assertThat(cuerpo(conErrores)).containsEntry("incidencias", List.of(incidencia));
        assertThat(leida.getBody()).isEqualTo(new McasValoresRangoService.Tabla("abc", "MATRIX_LOOKUP", List.of()));
    }

    @Test
    void editar_una_regla_guarda_nombre_y_descripcion_y_un_codigo_vacio_conserva_el_anterior() {
        var reglas = mock(McasReglaComisionRepository.class);
        var conjuntos = mock(McasConjuntoReglasRepository.class);
        var controller = new McasReglasController(reglas, mock(McasValorRangoRepository.class), conjuntos,
                mock(McasValoresRangoService.class));
        var regla = McasReglaComision.builder().id(2).idConjuntoReglas(3).codigo("IRIS_PYME_LUZ").build();
        when(reglas.findById(2)).thenReturn(Optional.of(regla));
        when(conjuntos.findById(3)).thenReturn(Optional.of(McasConjuntoReglas.builder().id(3).estado("BORRADOR").build()));
        when(reglas.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = controller.actualizar(2, McasReglaComision.builder().codigo(" ").prioridad(10)
                .nombre(" Iris · pyme luz · 44 % del FEE ").descripcion("Consumo × FEE × 44 %").metodoCalculo("FEE_PERCENTAGE").build());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(regla.getCodigo()).isEqualTo("IRIS_PYME_LUZ");
        assertThat(regla.getNombre()).isEqualTo("Iris · pyme luz · 44 % del FEE");
        assertThat(regla.getDescripcion()).isEqualTo("Consumo × FEE × 44 %");
    }

    @Test
    void editar_una_tabla_guarda_nombre_y_descripcion_y_un_codigo_vacio_conserva_el_anterior() {
        var conjuntos = mock(McasConjuntoReglasRepository.class);
        var controller = new McasConjuntoReglasController(conjuntos, mock(McasReglaComisionRepository.class),
                mock(McasValorRangoRepository.class), mock(McasRuleSetValidator.class), mock(McasConjuntoService.class));
        var borrador = McasConjuntoReglas.builder().id(1).codigo("ENDESA-FIJO-20261001").estado("BORRADOR").build();
        when(conjuntos.findById(1)).thenReturn(Optional.of(borrador));
        when(conjuntos.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = controller.actualizar(1, McasConjuntoReglas.builder().codigo(null).nombre("Endesa 2026-10")
                .descripcion("Tabla de octubre").idComercializadora(7).fasePrecio("FIJO").build());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(borrador.getCodigo()).isEqualTo("ENDESA-FIJO-20261001");
        assertThat(borrador.getNombre()).isEqualTo("Endesa 2026-10");
        assertThat(borrador.getDescripcion()).isEqualTo("Tabla de octubre");
    }

    @Test
    void las_rutas_nuevas_leen_y_escriben_el_json_del_contrato_de_api() throws Exception {
        var servicio = mock(McasConjuntoService.class);
        var tablas = mock(McasValoresRangoService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new McasConjuntoReglasController(mock(McasConjuntoReglasRepository.class),
                        mock(McasReglaComisionRepository.class), mock(McasValorRangoRepository.class),
                        mock(McasRuleSetValidator.class), servicio),
                new McasReglasController(mock(McasReglaComisionRepository.class), mock(McasValorRangoRepository.class),
                        mock(McasConjuntoReglasRepository.class), tablas)).build();
        var incidencia = new McasIncidenciaDTO(7, "Endesa · pyme luz 3.0TD", 12, "La celda 12 no tiene importe.");
        when(servicio.comprobacion(3, true)).thenReturn(new McasConjuntoService.Comprobacion(false, List.of(incidencia),
                List.of("Se cerrará «Endesa 2026-03» el 30/09/2026"), List.of()));
        when(servicio.nuevaVersion(3, java.time.LocalDate.of(2026, 10, 1), "Endesa 2026-10"))
                .thenReturn(McasConjuntoReglas.builder().id(4).estado("BORRADOR").idConjuntoOrigen(3).build());
        when(servicio.activar(3, true)).thenReturn(McasConjuntoReglas.builder().id(3).estado("PUBLICADO").build());
        when(servicio.activarLote(List.of(1, 2), true)).thenReturn(List.of(McasResultadoLoteDTO.ok(1L, "PUBLICADO", "ok")));
        when(tablas.leer(8)).thenReturn(new McasValoresRangoService.Tabla("abc", "MATRIX_LOOKUP", List.of(
                api.aura.entity.mcas.McasValorRango.builder().id(1).idRegla(8).dimension("consumo_kwh")
                        .valorResultado(java.math.BigDecimal.TEN).orden(1).build())));
        when(tablas.reemplazar(eq(8), eq("vieja"), anyList())).thenThrow(new McasValoresRangoService.FirmaObsoletaException());

        mvc.perform(get("/api/mcas/conjuntos-reglas/3/comprobacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeActivar").value(false))
                .andExpect(jsonPath("$.incidencias[0].idRegla").value(7))
                .andExpect(jsonPath("$.incidencias[0].regla").value("Endesa · pyme luz 3.0TD"))
                .andExpect(jsonPath("$.incidencias[0].orden").value(12))
                .andExpect(jsonPath("$.cambios[0]").value("Se cerrará «Endesa 2026-03» el 30/09/2026"))
                .andExpect(jsonPath("$.avisos").isArray());
        mvc.perform(post("/api/mcas/conjuntos-reglas/3/nueva-version").contentType("application/json")
                        .content("{\"vigenciaDesde\":\"2026-10-01\",\"nombre\":\"Endesa 2026-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idConjuntoOrigen").value(3));
        mvc.perform(post("/api/mcas/conjuntos-reglas/3/activar"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PUBLICADO"));
        mvc.perform(post("/api/mcas/conjuntos-reglas/activar-lote").contentType("application/json")
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ok").value(true)).andExpect(jsonPath("$[0].id").value(1));
        mvc.perform(get("/api/mcas/reglas/8/tabla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firma").value("abc"))
                .andExpect(jsonPath("$.metodo").value("MATRIX_LOOKUP"))
                .andExpect(jsonPath("$.valores[0].dimension").value("consumo_kwh"));
        mvc.perform(put("/api/mcas/reglas/8/tabla").contentType("application/json")
                        .content("{\"firma\":\"vieja\",\"valores\":[{\"dimension\":\"consumo_kwh\",\"valorMin\":0,"
                                + "\"valorMax\":100,\"minInclusivo\":true,\"maxInclusivo\":true,\"valorResultado\":10,"
                                + "\"orden\":1}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
        verify(servicio).activar(3, true);
    }

    @Test
    void nueva_version_sin_cuerpo_responde_400_pidiendo_la_fecha() throws Exception {
        // H17: sin cuerpo, el servicio recibe la fecha nula y responde con un mensaje claro.
        var servicio = mock(McasConjuntoService.class);
        when(servicio.nuevaVersion(3, null, null)).thenThrow(
                new IllegalArgumentException("Indique la fecha de inicio de la nueva versión."));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new McasConjuntoReglasController(mock(McasConjuntoReglasRepository.class),
                        mock(McasReglaComisionRepository.class), mock(McasValorRangoRepository.class),
                        mock(McasRuleSetValidator.class), servicio))
                .setControllerAdvice(new api.aura.config.GlobalExceptionHandler()).build();

        mvc.perform(post("/api/mcas/conjuntos-reglas/3/nueva-version"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Indique la fecha de inicio de la nueva versión."));
        mvc.perform(post("/api/mcas/conjuntos-reglas/3/nueva-version").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Indique la fecha de inicio de la nueva versión."));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cuerpo(org.springframework.http.ResponseEntity<?> respuesta) {
        return (Map<String, Object>) respuesta.getBody();
    }
}
