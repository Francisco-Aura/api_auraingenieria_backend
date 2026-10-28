package api.aura.mcas;

import api.aura.dto.mcas.McasPoliticaRetrocesionDTO;
import api.aura.dto.mcas.McasTramoRetrocesionDTO;
import api.aura.entity.mcas.McasPoliticaRetrocesion;
import api.aura.entity.mcas.McasTramoRetrocesion;
import api.aura.repository.mcas.McasPoliticaRetrocesionRepository;
import api.aura.repository.mcas.McasTramoRetrocesionRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBGestorasRepository;
import api.aura.service.mcas.McasRetrocesionPolicyService;
import api.aura.service.mcas.engine.ContextoCalculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class McasRetrocesionPolicyServiceTest {
    private final McasPoliticaRetrocesionRepository repo = mock(McasPoliticaRetrocesionRepository.class);
    private final McasTramoRetrocesionRepository tramoRepo = mock(McasTramoRetrocesionRepository.class);
    private final TBComercializadorasRepository comercializadoras = mock(TBComercializadorasRepository.class);
    private final McasRetrocesionPolicyService service = new McasRetrocesionPolicyService(repo, tramoRepo,
            comercializadoras, mock(TBGestorasRepository.class));

    /** Políticas y tramos «guardados» en memoria. */
    private final Map<Integer, McasPoliticaRetrocesion> politicas = new HashMap<>();
    private final Map<Integer, List<McasTramoRetrocesion>> tramosPorPolitica = new HashMap<>();

    @BeforeEach
    void memoria() {
        lenient().when(repo.findById(anyInt())).thenAnswer(inv -> Optional.ofNullable(politicas.get(inv.<Integer>getArgument(0))));
        lenient().when(repo.save(any())).thenAnswer(inv -> {
            McasPoliticaRetrocesion p = inv.getArgument(0);
            if (p.getId() == null) p.setId(politicas.size() + 100);
            politicas.put(p.getId(), p);
            return p;
        });
        lenient().when(repo.findAllById(any())).thenAnswer(inv -> {
            List<McasPoliticaRetrocesion> lista = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0))
                if (politicas.containsKey(id)) lista.add(politicas.get(id));
            return lista;
        });
        lenient().when(tramoRepo.findByIdPoliticaOrderByUnidadAscDesdeValorAscPrioridadAsc(anyInt()))
                .thenAnswer(inv -> tramosPorPolitica.getOrDefault(inv.<Integer>getArgument(0), List.of()));
        lenient().when(tramoRepo.findByIdPoliticaInOrderByUnidadAscDesdeValorAscPrioridadAsc(anyCollection()))
                .thenAnswer(inv -> {
                    List<McasTramoRetrocesion> lista = new ArrayList<>();
                    for (Object id : (Iterable<?>) inv.getArgument(0))
                        lista.addAll(tramosPorPolitica.getOrDefault(id, List.of()));
                    return lista;
                });
    }

    // ---- Selección (informativa) ----

    @Test void selecciona_la_politica_publicada_mas_especifica_sin_aplicarla() {
        ContextoCalculo ctx = contexto();
        var generica = politica(1, null, null, null);
        var especifica = politica(2, 3, "LUZ", "PYME");
        when(repo.findPublicadasVigentes(7, LocalDate.of(2026, 3, 16)))
                .thenReturn(List.of(generica, especifica));

        assertThat(service.seleccionar(ctx)).contains(2);
    }

    @Test void rechaza_dos_politicas_con_igual_especificidad() {
        ContextoCalculo ctx = contexto();
        when(repo.findPublicadasVigentes(7, LocalDate.of(2026, 3, 16)))
                .thenReturn(List.of(politica(1, 3, "LUZ", "PYME"), politica(2, 3, "LUZ", "PYME")));

        assertThatThrownBy(() -> service.seleccionar(ctx))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("2 politicas");
    }

    @Test void la_tarifa_de_la_politica_se_compara_normalizada() {
        var porTarifa = politica(4, null, null, null);
        porTarifa.setTarifaAcceso("3.0 TD");
        when(repo.findPublicadasVigentes(7, LocalDate.of(2026, 3, 16))).thenReturn(List.of(porTarifa));

        assertThat(service.seleccionar(contexto())).contains(4);     // el contrato trae «3.0TD»
    }

    // ---- Resumen en lenguaje natural ----

    @Test void describir_resume_los_tramos_en_lenguaje_natural() {
        Integer id = guardada("PUBLICADO", "PYME", "PORCENTAJE",
                List.of(tramo("MESES", 0, 3, "1"), tramo("MESES", 3, 6, "0.6")));

        assertThat(service.describir(id).orElseThrow())
                .contains("0–3 meses: 100 %; 3–6 meses: 60 %")
                .isEqualTo("PYME — desde la activación: 0–3 meses: 100 %; 3–6 meses: 60 %. Nunca supera lo cobrado.");
    }

    @Test void describir_prorrateo_no_aplica_y_unidades_sin_confirmar() {
        Integer prorrateo = guardada("PUBLICADO", null, "PRORRATEO", List.of());
        Integer noAplica = guardada("PUBLICADO", null, "NO_APLICA", List.of());
        politicas.get(noAplica).setLimiteImportePagado(false);
        McasTramoRetrocesion sinConfirmar = tramo("DIAS", 90, 180, "20");
        sinConfirmar.setTipoValor("SIN_CONFIRMAR");
        sinConfirmar.setDesdeInclusivo(false);
        sinConfirmar.setHastaInclusivo(true);
        Integer iberdrola = guardada("BORRADOR", null, "PORCENTAJE", List.of(tramo("DIAS", 0, 90, "1"), sinConfirmar));
        politicas.get(iberdrola).setTarifaAcceso("2.0TD");
        politicas.get(iberdrola).setLimiteImportePagado(false);

        assertThat(service.describir(prorrateo)).contains("Prorrateo a 365 días desde la activación. Nunca supera lo cobrado.");
        assertThat(service.describir(noAplica)).contains("No aplica retrocesión");
        assertThat(service.describir(iberdrola).orElseThrow()).isEqualTo(
                "2.0TD — desde la activación: 0–90 días: 100 %; 90–180 días: 20 (tipo de valor sin elegir).");
        assertThat(service.describir(999)).isEmpty();
        assertThat(service.describir(null)).isEmpty();
    }

    @Test void describir_todas_hace_una_sola_consulta_para_todo_el_listado() {
        Integer a = guardada("PUBLICADO", "PYME", "PORCENTAJE", List.of(tramo("MESES", 0, 3, "1")));
        Integer b = guardada("PUBLICADO", "RESIDENCIAL", "PORCENTAJE", List.of(tramo("MESES", 0, 12, "0.5")));

        Map<Integer, String> resumenes = service.describirTodas(List.of(a, b, a));

        assertThat(resumenes).containsOnlyKeys(a, b);
        assertThat(resumenes.get(b)).contains("0–12 meses: 50 %");
        verify(repo, times(1)).findAllById(any());
        verify(tramoRepo, times(1)).findByIdPoliticaInOrderByUnidadAscDesdeValorAscPrioridadAsc(anyCollection());
        verify(tramoRepo, never()).findByIdPoliticaOrderByUnidadAscDesdeValorAscPrioridadAsc(anyInt());
    }

    // ---- Publicación ----

    @Test void no_se_publica_una_politica_con_tramos_sin_confirmar_ni_solapados() {
        McasTramoRetrocesion sinConfirmar = tramo("DIAS", 0, 90, "20");
        sinConfirmar.setTipoValor("SIN_CONFIRMAR");
        Integer a = guardada("BORRADOR", null, "PORCENTAJE", List.of(sinConfirmar));
        assertThatThrownBy(() -> service.publicar(a)).hasMessage("El tramo 0–90 días no tiene elegido el tipo de valor: "
                + "elija porcentaje, factor o importe fijo y guarde la política antes de publicarla.");

        Integer b = guardada("BORRADOR", null, "PORCENTAJE", List.of(tramo("MESES", 0, 6, "1"), tramo("MESES", 3, 9, "0.5")));
        assertThatThrownBy(() -> service.publicar(b)).hasMessageContaining("se solapan");

        assertThat(politicas.get(a).getEstado()).isEqualTo("BORRADOR");
        assertThat(politicas.get(b).getEstado()).isEqualTo("BORRADOR");
    }

    @Test void no_se_publica_sin_tramos_ni_con_porcentajes_fuera_de_rango() {
        Integer sinTramos = guardada("BORRADOR", null, "PORCENTAJE", List.of());
        assertThatThrownBy(() -> service.publicar(sinTramos)).hasMessageContaining("al menos un tramo");

        Integer porcentaje = guardada("BORRADOR", null, "PORCENTAJE", List.of(tramo("MESES", 0, 3, "60")));
        assertThatThrownBy(() -> service.publicar(porcentaje)).hasMessageContaining("entre 0 y 1");

        Integer negativo = guardada("BORRADOR", null, "FIJO", List.of(importeFijo("-5")));
        assertThatThrownBy(() -> service.publicar(negativo)).hasMessageContaining("negativo");
    }

    @Test void se_publican_los_tramos_contiguos_y_el_prorrateo_sin_tramos() {
        McasTramoRetrocesion abierto = tramo("MESES", 4, null, "0");
        abierto.setDesdeInclusivo(false);
        McasTramoRetrocesion cerrado = tramo("MESES", 3, 4, "0.5");
        cerrado.setHastaInclusivo(true);
        Integer naturgy = guardada("BORRADOR", "RESIDENCIAL", "PORCENTAJE",
                List.of(tramo("MESES", 0, 2, "1"), cerrado, abierto));
        Integer iris = guardada("BORRADOR", null, "PRORRATEO", List.of());

        assertThat(service.publicar(naturgy).getEstado()).isEqualTo("PUBLICADO");
        assertThat(service.publicar(iris).getEstado()).isEqualTo("PUBLICADO");
    }

    @Test void no_se_publica_otra_politica_para_el_mismo_ambito_y_vigencia() {
        Integer vigente = guardada("PUBLICADO", "PYME", "PORCENTAJE", List.of(tramo("MESES", 0, 3, "1")));
        Integer nueva = guardada("BORRADOR", "PYME", "PORCENTAJE", List.of(tramo("MESES", 0, 6, "1")));
        when(repo.findByIdComercializadoraAndEstado(7, "PUBLICADO")).thenReturn(List.of(politicas.get(vigente)));

        assertThatThrownBy(() -> service.publicar(nueva)).hasMessageContaining("Ya hay una política publicada");
    }

    @Test void solo_se_publican_borradores() {
        Integer publicada = guardada("PUBLICADO", null, "PRORRATEO", List.of());
        assertThatThrownBy(() -> service.publicar(publicada)).isInstanceOf(IllegalStateException.class);
    }

    // ---- Gestión ----

    @Test void actualizar_solo_en_borrador_y_sustituye_los_tramos() {
        Integer borrador = guardada("BORRADOR", "PYME", "PORCENTAJE", List.of(tramo("MESES", 0, 3, "1")));
        McasPoliticaRetrocesionDTO datos = dto("PYME", tramoDto("MESES", 0, 6, "0.8"));

        McasPoliticaRetrocesionDTO actualizada = service.actualizar(borrador, datos);

        assertThat(actualizada.getEstado()).isEqualTo("BORRADOR");
        verify(tramoRepo).deleteByIdPolitica(borrador);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<McasTramoRetrocesion>> nuevos = ArgumentCaptor.forClass(List.class);
        verify(tramoRepo).saveAll(nuevos.capture());
        assertThat(nuevos.getValue()).singleElement().satisfies(t -> {
            assertThat(t.getIdPolitica()).isEqualTo(borrador);
            assertThat(t.getHastaValor()).isEqualTo(6);
            assertThat(t.getId()).isNull();
            assertThat(t.getDesdeInclusivo()).isTrue();
            assertThat(t.getHastaInclusivo()).isFalse();
        });

        Integer publicada = guardada("PUBLICADO", "PYME", "PORCENTAJE", List.of(tramo("MESES", 0, 3, "1")));
        assertThatThrownBy(() -> service.actualizar(publicada, datos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Solo se puede editar una política en borrador (esta está publicada).");
    }

    @Test void crear_valida_la_estructura_de_los_tramos() {
        McasPoliticaRetrocesionDTO alReves = dto("PYME", tramoDto("MESES", 6, 3, "1"));
        assertThatThrownBy(() -> service.crear(alReves)).hasMessageContaining("mayor que");

        McasPoliticaRetrocesionDTO unidad = dto("PYME", tramoDto("AÑOS", 0, 3, "1"));
        assertThatThrownBy(() -> service.crear(unidad)).hasMessage("La unidad del tramo debe ser días o meses.");

        McasTramoRetrocesionDTO otroTipo = tramoDto("MESES", 0, 3, "1");
        otroTipo.setTipoValor("PUNTOS");
        assertThatThrownBy(() -> service.crear(dto("PYME", otroTipo)))
                .hasMessage("Elija el tipo de valor del tramo: porcentaje, factor o importe fijo.");

        McasPoliticaRetrocesionDTO sinComercializadora = dto("PYME");
        sinComercializadora.setIdComercializadora(null);
        assertThatThrownBy(() -> service.crear(sinComercializadora)).hasMessageContaining("comercializadora");

        McasPoliticaRetrocesionDTO valida = dto("PYME", tramoDto("MESES", 0, 3, "1"));
        valida.setEstado("PUBLICADO");                                   // se ignora: nace en BORRADOR
        assertThat(service.crear(valida).getEstado()).isEqualTo("BORRADOR");
    }

    @Test void sin_codigo_se_genera_uno_con_la_comercializadora_el_segmento_el_suministro_y_la_fecha() {
        when(comercializadoras.findById(7)).thenReturn(Optional.of(api.aura.entity.tablas_maestras.TBComercializadoras
                .builder().id(7).nombre("IBERDROLA CLIENTES S.A.U.").build()));
        McasPoliticaRetrocesionDTO sinCodigo = dto("PYME", tramoDto("DIAS", 0, 90, "1"));
        sinCodigo.setTipoSuministro("luz");
        sinCodigo.setCodigo("  ");
        when(repo.findByCodigo("RET-IBERDROLA-PYME-LUZ-20260101")).thenReturn(Optional.of(
                McasPoliticaRetrocesion.builder().id(1).codigo("RET-IBERDROLA-PYME-LUZ-20260101").build()));

        assertThat(service.crear(sinCodigo).getCodigo()).isEqualTo("RET-IBERDROLA-PYME-LUZ-20260101-2");

        McasPoliticaRetrocesionDTO generica = dto(null, tramoDto("DIAS", 0, 90, "1"));
        assertThat(service.crear(generica).getCodigo()).isEqualTo("RET-IBERDROLA-20260101");

        // Al editar, un código vacío conserva el que tenía
        Integer id = politicas.values().stream().filter(p -> "RET-IBERDROLA-20260101".equals(p.getCodigo()))
                .findFirst().orElseThrow().getId();
        assertThat(service.actualizar(id, dto(null, tramoDto("DIAS", 0, 90, "1"))).getCodigo())
                .isEqualTo("RET-IBERDROLA-20260101");
    }

    @Test void eliminar_solo_borradores_sin_propuestas() {
        Integer publicada = guardada("PUBLICADO", null, "PRORRATEO", List.of());
        assertThatThrownBy(() -> service.eliminar(publicada))
                .hasMessage("Solo se puede eliminar una política en borrador (esta está publicada).");

        Integer usada = guardada("BORRADOR", null, "PRORRATEO", List.of());
        when(repo.contarPropuestas(usada)).thenReturn(2L);
        assertThatThrownBy(() -> service.eliminar(usada)).hasMessageContaining("2 propuestas");

        Integer libre = guardada("BORRADOR", null, "PRORRATEO", List.of());
        service.eliminar(libre);
        verify(tramoRepo).deleteByIdPolitica(libre);
        verify(repo).deleteById(libre);
    }

    @Test void archivar_publicadas_o_borradores() {
        Integer publicada = guardada("PUBLICADO", null, "PRORRATEO", List.of());
        assertThat(service.archivar(publicada).getEstado()).isEqualTo("ARCHIVADO");
        assertThatThrownBy(() -> service.archivar(publicada)).isInstanceOf(IllegalStateException.class);
    }

    // ---- ayudas ----

    private ContextoCalculo contexto() {
        return ContextoCalculo.builder().idComercializadora(7).idGestora(3)
                .tipoSuministro("LUZ").segmento("PYME").codigoTarifaAcceso("3.0TD")
                .fechaEfectiva(LocalDate.of(2026, 3, 16)).build();
    }

    private McasPoliticaRetrocesion politica(int id, Integer gestora, String suministro, String segmento) {
        return McasPoliticaRetrocesion.builder().id(id).idComercializadora(7).idGestora(gestora)
                .tipoSuministro(suministro).segmento(segmento).estado("PUBLICADO")
                .vigenciaDesde(LocalDate.of(2026, 1, 1)).metodo("PORCENTAJE").build();
    }

    private Integer guardada(String estado, String segmento, String metodo, List<McasTramoRetrocesion> tramos) {
        int id = politicas.size() + 1;
        politicas.put(id, McasPoliticaRetrocesion.builder().id(id).idComercializadora(7).version("1")
                .segmento(segmento).metodo(metodo).estado(estado).limiteImportePagado(true)
                .vigenciaDesde(LocalDate.of(2026, 1, 1)).build());
        tramos.forEach(t -> t.setIdPolitica(id));
        tramosPorPolitica.put(id, tramos);
        return id;
    }

    private McasTramoRetrocesion tramo(String unidad, int desde, Integer hasta, String valor) {
        return McasTramoRetrocesion.builder().unidad(unidad).desdeValor(desde).hastaValor(hasta)
                .valor(new BigDecimal(valor)).tipoValor("PORCENTAJE").build();
    }

    private McasTramoRetrocesion importeFijo(String valor) {
        McasTramoRetrocesion t = tramo("MESES", 0, 12, valor);
        t.setTipoValor("IMPORTE_FIJO");
        return t;
    }

    private McasPoliticaRetrocesionDTO dto(String segmento, McasTramoRetrocesionDTO... tramos) {
        McasPoliticaRetrocesionDTO d = new McasPoliticaRetrocesionDTO();
        d.setIdComercializadora(7);
        d.setVersion("1");
        d.setVigenciaDesde(LocalDate.of(2026, 1, 1));
        d.setSegmento(segmento);
        d.setMetodo("PORCENTAJE");
        d.setTramos(new ArrayList<>(List.of(tramos)));
        return d;
    }

    private McasTramoRetrocesionDTO tramoDto(String unidad, int desde, Integer hasta, String valor) {
        McasTramoRetrocesionDTO t = new McasTramoRetrocesionDTO();
        t.setId(55);                                                     // el id del cliente no se reutiliza
        t.setUnidad(unidad);
        t.setDesdeValor(desde);
        t.setHastaValor(hasta);
        t.setValor(new BigDecimal(valor));
        t.setTipoValor("PORCENTAJE");
        return t;
    }
}
