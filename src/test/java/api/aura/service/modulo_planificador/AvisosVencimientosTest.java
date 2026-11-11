package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Actividad;
import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.entity.modulo_planificador.pl_Proyecto;
import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import api.aura.repository.modulo_planificador.pl_ActividadRepository;
import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import api.aura.repository.modulo_planificador.pl_MetaRepository;
import api.aura.repository.modulo_planificador.pl_ProyectoRepository;
import api.aura.repository.modulo_planificador.pl_TareaProyectoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Avisos y vencimientos: con los mismos casos que el Planificador actual (generados con su código Python, en
 * {@code avisos-dorado/escenarios.json}) salen los mismos avisos, mensajes, marcas y entradas del registro.
 */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
@Import(AvisosVencimientosTest.Config.class)
class AvisosVencimientosTest {

    /** La intranet de mentira: guarda lo que recibe y falla para los grupos que se le indiquen. */
    public static class AvisosDePrueba implements AvisosPort {
        final List<Map<String, Object>> recibidos = new ArrayList<>();
        Set<String> fallan = Set.of();
        boolean explota;

        @Override
        public boolean activo() {
            return true;
        }

        @Override
        public void enviar(Map<String, Object> aviso) {
            if (explota) {
                throw new IllegalStateException("fallo inesperado");
            }
            if (fallan.contains(String.valueOf(aviso.get("group")))) {
                throw new AvisoError("La intranet respondió 500");
            }
            recibidos.add(aviso);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        AvisosDePrueba avisosDePrueba() {
            return new AvisosDePrueba();
        }
    }

    @Autowired
    private AvisosDePrueba intranet;
    @Autowired
    private AvisosService avisos;
    @Autowired
    private VencimientosService vencimientos;
    @Autowired
    private pl_TareaProyectoRepository tareas;
    @Autowired
    private pl_ProyectoRepository proyectos;
    @Autowired
    private pl_EquipoRepository equipos;
    @Autowired
    private pl_ActividadRepository actividades;
    @Autowired
    private pl_MetaRepository meta;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApplicationContext contexto;

    private JsonNode dorado;

    @BeforeEach
    void preparar() throws IOException {
        for (String t : new String[]{"pl_actividad", "pl_tareas_proyecto", "pl_proyectos", "pl_equipos", "pl_meta"}) {
            jdbc.execute("DELETE FROM " + t);
        }
        intranet.recibidos.clear();
        intranet.fallan = Set.of();
        intranet.explota = false;
        try (var in = getClass().getResourceAsStream("/avisos-dorado/escenarios.json")) {
            dorado = json.readTree(in);
        }
    }

    // ------------------------------------------------------------------ escenarios del código original

    private static List<String> lista(JsonNode n) {
        List<String> r = new ArrayList<>();
        n.forEach(x -> r.add(x.asText()));
        return r;
    }

    private void cargar(JsonNode entrada) {
        for (JsonNode p : entrada.get("proyectos")) {
            pl_Proyecto proyecto = new pl_Proyecto();
            proyecto.setId(p.get("id").asText());
            proyecto.setNombre(p.get("nombre").asText());
            proyecto.setCreadoEl(LocalDateTime.now());
            if (p.get("eliminado").asBoolean()) {
                proyecto.setEliminadoEl(LocalDateTime.of(2026, 1, 1, 0, 0));
            }
            proyectos.save(proyecto);
        }
        for (JsonNode x : entrada.get("tareas")) {
            pl_TareaProyecto t = new pl_TareaProyecto();
            t.setId(x.get("id").asText());
            t.setProyectoId(x.get("proyecto").asText());
            t.setNombre(x.get("nombre").asText());
            t.setDepartamento(x.get("departamento").asText());
            t.setPosicion(x.get("posicion").asInt());
            t.setInicio(LocalDate.parse(x.get("inicio").asText()));
            t.setFin(LocalDate.parse(x.get("fin").asText()));
            t.setEstado(x.get("estado").asText());
            t.setDependeDe(lista(x.get("dependeDe")));
            t.setAvisadas(lista(x.get("avisadas")));
            t.setMarcasVencimiento(lista(x.get("marcas")));
            t.setCreadoEl(LocalDateTime.now());
            tareas.save(t);
        }
        if (entrada.has("equipos")) {
            for (JsonNode q : entrada.get("equipos")) {
                pl_Equipo e = new pl_Equipo();
                e.setId(q.get("id").asText());
                e.setCategoria("c");
                e.setPartida("g");
                if (!q.get("revisado").isNull()) {
                    e.setPrecioRevisadoEl(LocalDate.parse(q.get("revisado").asText()));
                }
                equipos.save(e);
            }
        }
    }

    private JsonNode estadoTareas() {
        ObjectNode r = json.createObjectNode();
        for (pl_TareaProyecto t : tareas.findAll()) {
            ObjectNode o = r.putObject(t.getId());
            o.set("avisadas", json.valueToTree(t.getAvisadas()));
            o.set("marcas", json.valueToTree(t.getMarcasVencimiento()));
        }
        return r;
    }

    private JsonNode actividadDesde(int vistas) {
        ArrayNode r = json.createArrayNode();
        List<pl_Actividad> todas = actividades.findAll(Sort.by("fecha"));
        for (pl_Actividad a : todas.subList(vistas, todas.size())) {
            ObjectNode o = r.addObject();
            o.put("tipo", a.getTipo());
            o.put("resumen", a.getResumen());
            o.put("tarea", a.getNombreTarea());
            o.put("detalle", a.getDetalle());
            o.put("actor", a.getActor());
            o.put("proyecto", a.getProyectoId());
        }
        return r;
    }

    private void repetir(String nombre) {
        JsonNode escenario = dorado.get(nombre);
        cargar(escenario.get("entrada"));
        int vistas = 0;
        int n = 0;
        for (JsonNode paso : escenario.get("entrada").get("pasos")) {
            JsonNode esperado = escenario.get("salida").get(n++);
            String donde = nombre + ", paso " + n;
            intranet.recibidos.clear();
            Set<String> fallan = new HashSet<>();
            if (paso.has("fallan")) {
                fallan.addAll(lista(paso.get("fallan")));
            }
            intranet.fallan = fallan;
            Map<String, Object> resumen = null;
            if ("terminada".equals(paso.get("accion").asText())) {
                Map<String, pl_TareaProyecto> delProyecto = new LinkedHashMap<>();
                tareas.findByProyectoId("p1").forEach(t -> delProyecto.put(t.getId(), t));
                avisos.tareaTerminada(delProyecto.get(paso.get("tarea").asText()), delProyecto, proyectos.findById("p1").orElseThrow(),
                        paso.get("actor").asText(), paso.get("actorId").isNull() ? null : paso.get("actorId").asInt());
            } else {
                resumen = vencimientos.ejecutar(LocalDate.parse(paso.get("hoy").asText()), paso.get("soloMarcar").asBoolean());
            }
            assertEquals(esperado.get("avisos"), json.valueToTree(intranet.recibidos), donde + ": avisos enviados");
            if (resumen != null) {
                assertEquals(esperado.get("resumen"), json.valueToTree(resumen), donde + ": resumen");
            }
            assertEquals(esperado.get("tareas"), estadoTareas(), donde + ": marcas de las tareas");
            JsonNode registro = actividadDesde(vistas);
            assertEquals(esperado.get("actividad"), registro, donde + ": registro de actividad");
            vistas += registro.size();
            JsonNode precio = esperado.get("precioAvisado");
            assertEquals(precio.isNull() ? null : precio.asText(), meta.findById(RevisionPreciosService.META_AVISADO).map(m -> m.getValor()).orElse(null),
                    donde + ": aviso de precios anotado");
        }
    }

    @Test
    void alTerminarUnaTareaSeAvisaUnaVezPorGrupoComoEnElOriginal() {
        repetir("terminada");
        // dos grupos para la misma tarea, uno para cada otra, y nadie para la que no tiene departamento
        assertEquals(List.of("administracion", "coordinacion", "ingenieria"),
                dorado.get("terminada").get("salida").get(0).get("avisos").findValuesAsText("group").stream().sorted().toList());
    }

    @Test
    void quienTerminaSinIdentificarseSeAvisaComoAlguien() {
        repetir("terminada_anonima");
        assertEquals("Alguien ha terminado «Redactar contrato» (Instalación Solar Vega Lorca). Ya puede empezar «Pedir material».",
                dorado.get("terminada_anonima").get("salida").get(0).get("avisos").get(0).get("message").asText());
    }

    @Test
    void siFallaUnGrupoLaTareaNoSeDaPorAvisadaYSeReintenta() {
        repetir("terminada_fallo");
        assertEquals(List.of("t2"), lista(dorado.get("terminada_fallo").get("salida").get(0).get("tareas").get("t1").get("avisadas")));
    }

    @Test
    void conMuchasTareasElMensajeSeRecortaSinPasarseDeLargo() {
        repetir("terminada_larga");
        dorado.get("terminada_larga").get("salida").get(0).get("avisos")
                .forEach(a -> assertTrue(a.get("message").asText().codePointCount(0, a.get("message").asText().length()) <= 240));
    }

    @Test
    void losVencimientosSalenComoEnElOriginalDiaTrasDia() {
        repetir("vencimientos");
    }

    @Test
    void siFallaLaIntranetNoSeAnotaYSeReintentaAlDiaSiguiente() {
        repetir("vencimientos_fallo");
    }

    @Test
    void soloMarcarDaPorAvisadoLoQueYaVenceSinEnviarNada() {
        repetir("vencimientos_solo_marcar");
    }

    @Test
    void laRevisionDePreciosAvisaAIngenieriaALos15DiasDeLaUltimaRevision() {
        repetir("precios");
        repetir("precios_revisados");
    }

    @Test
    void siFallaElAvisoDePreciosNoSeAnotaYSeReintenta() {
        repetir("precios_fallo");
    }

    @Test
    void laRevisionDePreciosTambienSePuedeDarPorAvisadaSinEnviar() {
        repetir("precios_solo_marcar");
    }

    // ------------------------------------------------------------------ funciones puras

    @Test
    void losMensajesSonIgualesQueLosDelOriginalConTodosLosTamanos() {
        int n = 0;
        for (JsonNode m : dorado.get("_mensajes")) {
            String esperado = m.get("esperado").asText();
            String obtenido = switch (m.get("tipo").asText()) {
                case "terminada" -> AvisosService.mensaje(m.get("quien").asText(), m.get("terminada").asText(), m.get("proyecto").asText(), lista(m.get("nombres")));
                default -> VencimientosService.mensaje(m.get("proyecto").asText(), m.get("tipo").asText(), lista(m.get("textos")));
            };
            assertEquals(esperado, obtenido, "mensaje " + n++);
        }
        assertTrue(n > 50);
    }

    @Test
    void losGruposSeSacanDelDepartamentoSinTildesNiMayusculas() {
        dorado.get("_grupos").fields().forEachRemaining(e -> assertEquals(lista(e.getValue()), AvisosService.gruposDe(e.getKey()), e.getKey()));
        dorado.get("_nombres").fields().forEachRemaining(e -> assertEquals(e.getValue().asText(), AvisosService.nombreGrupo(e.getKey()), e.getKey()));
    }

    // ------------------------------------------------------------------ de punta a punta

    @Test
    void alMarcarUnaTareaComoHechaEnLaApiSeAvisaALasSiguientesYUnFalloNoLaImpide() throws Exception {
        cargar(dorado.get("terminada_anonima").get("entrada"));
        // t1 está «done» en los datos de prueba: se vuelve a poner en curso para terminarla por la API
        pl_TareaProyecto t1 = tareas.findById("t1").orElseThrow();
        t1.setEstado("doing");
        tareas.save(t1);

        mvc.perform(patch("/api/planificador/tasks/t1").header("X-Aura-Usuario-Nombre", "Ana").header("X-Usuario-Id", 7).header("X-Usuario-Nivel", 3)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"done\"}")).andExpect(status().isOk());
        assertEquals(1, intranet.recibidos.size());
        assertEquals("ingenieria", intranet.recibidos.get(0).get("group"));
        assertEquals(7, intranet.recibidos.get(0).get("excludeUserId"));
        assertEquals("Ana", intranet.recibidos.get(0).get("excludeUser"));
        assertEquals("proyecto=p1&tarea=t2", intranet.recibidos.get(0).get("link"));

        // Reabierta y terminada otra vez con la intranet rota: la tarea se termina igualmente
        pl_TareaProyecto t3 = tareas.findById("t1").orElseThrow();
        t3.setEstado("doing");
        t3.setAvisadas(new ArrayList<>());
        tareas.save(t3);
        intranet.explota = true;
        mvc.perform(patch("/api/planificador/tasks/t1").header("X-Aura-Usuario-Nombre", "Ana")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"done\"}")).andExpect(status().isOk());
        assertEquals("done", tareas.findById("t1").orElseThrow().getEstado());
    }

    @Test
    void elTemporizadorDiarioEstaApagadoSalvoQueSeActive() {
        assertEquals(0, contexto.getBeanNamesForType(VencimientosProgramador.class).length);
    }
}
