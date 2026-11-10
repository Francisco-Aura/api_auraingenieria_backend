package api.aura.controller.modulo_planificador;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de contrato del inventario: reproducen el comportamiento del Planificador actual
 * (rutas, JSON, códigos de error y reglas de negocio de marcas, proveedores, equipos y precios).
 */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class InventarioContratoTest {

    private static final String BASE = "/api/planificador";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("DELETE FROM pl_historial_precios");
        jdbc.execute("DELETE FROM pl_equipos");
        jdbc.execute("DELETE FROM pl_marcas");
        jdbc.execute("DELETE FROM pl_proveedores");
        jdbc.execute("DELETE FROM pl_meta");
    }

    // ------------------------------------------------------------------ ayudas

    private ResultActions peticionPost(String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(BASE + ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header("X-Aura-Usuario-Nombre", "Ana"));
    }

    private ResultActions peticionPatch(String ruta, String cuerpo) throws Exception {
        return mvc.perform(patch(BASE + ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header("X-Usuario", "Luis%20P%C3%A9rez"));
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private void marca(String n) throws Exception {
        peticionPost("/brands", "{\"name\":\"" + n + "\"}").andExpect(status().isCreated());
    }

    private void proveedor(String n) throws Exception {
        peticionPost("/suppliers", "{\"name\":\"" + n + "\"}").andExpect(status().isCreated());
    }

    private String equipo(String cuerpo) throws Exception {
        return leer(peticionPost("/equipment", cuerpo).andExpect(status().isCreated())).get("id").asText();
    }

    // ------------------------------------------------------------------ marcas

    @Test
    void marcaSeCreaEnMayusculasSinEspaciosDeMasYNoSePuedeRepetir() throws Exception {
        peticionPost("/brands", "{\"name\":\"  ja   solar \"}")
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"name\":\"JA SOLAR\",\"products\":0}", true));
        peticionPost("/brands", "{\"name\":\"Ja Solar\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La marca JA SOLAR ya existe"));
    }

    @Test
    void marcaConNombreVacioOConBarraEsInvalida() throws Exception {
        peticionPost("/brands", "{\"name\":\"  \"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Escribe el nombre de la marca"));
        peticionPost("/brands", "{\"name\":\"A/B\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El nombre de la marca no es válido"));
    }

    @Test
    void listaDeMarcasLlevaProductosYSiTieneLogo() throws Exception {
        marca("ZETA");
        marca("ALFA");
        equipo("{\"category\":\"contadores\",\"group\":\"electricidad\",\"brand\":\"alfa\",\"model\":\"C1\"}");
        mvc.perform(get(BASE + "/brands")).andExpect(status().isOk())
                .andExpect(content().json("[{\"name\":\"ALFA\",\"products\":1,\"logo\":false},"
                        + "{\"name\":\"ZETA\",\"products\":0,\"logo\":false}]", true));
    }

    @Test
    void marcaEnUsoNoSePuedeEliminar() throws Exception {
        marca("ALFA");
        equipo("{\"category\":\"contadores\",\"group\":\"electricidad\",\"brand\":\"ALFA\"}");
        mvc.perform(delete(BASE + "/brands/ALFA")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "La marca ALFA está en 1 producto(s): cámbiales la marca antes de eliminarla."));
        marca("BETA");
        mvc.perform(delete(BASE + "/brands/BETA")).andExpect(status().isNoContent());
        mvc.perform(delete(BASE + "/brands/BETA")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La marca no existe"));
    }

    @Test
    void logoDeMarcaSeGuardaSeLeeYSeBorra() throws Exception {
        marca("ALFA");
        String png = "data:image/png;base64,iVBORw0KGgo=";
        mvc.perform(put(BASE + "/brands/ALFA/logo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logo\":\"" + png + "\"}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"name\":\"ALFA\",\"logo\":true}", true));
        mvc.perform(get(BASE + "/brands/ALFA/logo")).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'"));
        mvc.perform(put(BASE + "/brands/ALFA/logo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logo\":\"data:text/html;base64,AAAA\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "El logo tiene que ser una imagen PNG, JPG, WebP o SVG de menos de 400 KB"));
        mvc.perform(delete(BASE + "/brands/ALFA/logo")).andExpect(status().isOk())
                .andExpect(content().json("{\"name\":\"ALFA\",\"logo\":false}", true));
        mvc.perform(get(BASE + "/brands/ALFA/logo")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Esta marca no tiene logo"));
    }

    // -------------------------------------------------------------- proveedores

    @Test
    void proveedorSeCreaEnMayusculasYNoSePuedeRepetirNiEliminarEnUso() throws Exception {
        peticionPost("/suppliers", "{\"name\":\"sumeba\"}").andExpect(status().isCreated())
                .andExpect(content().json("{\"name\":\"SUMEBA\",\"products\":0}", true));
        peticionPost("/suppliers", "{\"name\":\"Sumeba\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El proveedor SUMEBA ya existe"));
        equipo("{\"category\":\"contadores\",\"group\":\"electricidad\",\"supplier\":\"sumeba\",\"unit_price\":10}");
        mvc.perform(delete(BASE + "/suppliers/SUMEBA")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "El proveedor SUMEBA está en 1 producto(s): cámbiales el proveedor antes de eliminarlo."));
        mvc.perform(get(BASE + "/suppliers")).andExpect(status().isOk())
                .andExpect(content().json("[{\"name\":\"SUMEBA\",\"products\":1}]", true));
    }

    // ------------------------------------------------------------------ equipos

    @Test
    void catalogoTraePartidasCategoriasConListasCerradasYEquiposOrdenados() throws Exception {
        equipo("{\"category\":\"contadores\",\"group\":\"electricidad\",\"model\":\"A\"}");
        equipo("{\"category\":\"contadores\",\"group\":\"electricidad\",\"model\":\"B\"}");
        JsonNode c = leer(mvc.perform(get(BASE + "/equipment")).andExpect(status().isOk()));
        assertEquals(4, c.get("partidas").size());
        assertEquals("Módulos y montaje", c.get("partidas").get(1).get("label").asText());
        assertEquals(14, c.get("categories").size());
        JsonNode protecciones = null;
        for (JsonNode cat : c.get("categories")) {
            if (cat.get("key").asText().equals("protecciones")) {
                protecciones = cat;
            }
        }
        assertNotNull(protecciones);
        assertEquals("CC", protecciones.get("filters").get(0).get("options").get(0).asText());
        assertEquals(1, c.get("items").get(0).get("position").asInt());
        assertEquals(2, c.get("items").get(1).get("position").asInt());
        assertEquals("A", c.get("items").get(0).get("model").asText());
    }

    @Test
    void equipoDevuelveTodasLasClavesDelContratoActual() throws Exception {
        JsonNode e = leer(peticionPost("/equipment", "{\"category\":\"contadores\",\"group\":\"electricidad\"}")
                .andExpect(status().isCreated()));
        for (String clave : new String[]{"id", "position", "category", "group", "brand", "model", "supplier", "unit",
                "unit_price", "per_module", "default_qty", "attrs", "notes", "sample", "stock", "stock_min",
                "datasheet", "offers", "price_reviewed_at"}) {
            assertTrue(e.has(clave), "falta la clave " + clave);
        }
        assertEquals("ud", e.get("unit").asText());
        assertEquals(1.0, e.get("default_qty").asDouble());
        assertTrue(e.get("price_reviewed_at").isNull(), "sin precio no se da por revisado");
    }

    @Test
    void categoriaYPartidaDeben_existirYIrJuntas() throws Exception {
        peticionPost("/equipment", "{\"category\":\"nada\",\"group\":\"electricidad\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Categoría desconocida: nada"));
        peticionPost("/equipment", "{\"category\":\"contadores\",\"group\":\"nada\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Partida desconocida: nada"));
        peticionPost("/equipment", "{\"category\":\"baterias\",\"group\":\"proyecto\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La categoría «baterias» no puede estar en la partida «proyecto»"));
    }

    @Test
    void precioNegativoOFaltarCategoriaEsError422() throws Exception {
        peticionPost("/equipment", "{\"category\":\"contadores\",\"group\":\"electricidad\",\"unit_price\":-1}")
                .andExpect(status().isUnprocessableEntity());
        peticionPost("/equipment", "{\"group\":\"electricidad\"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void marcaYProveedorTienenQueEstarEnLaListaYSeGuardanConSuNombreExacto() throws Exception {
        peticionPost("/equipment", "{\"category\":\"contadores\",\"group\":\"electricidad\",\"brand\":\"xx\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La marca «xx» no está en la lista: créala primero"));
        peticionPost("/equipment", "{\"category\":\"contadores\",\"group\":\"electricidad\",\"supplier\":\"yy\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El proveedor «yy» no está en la lista: créalo primero"));
        marca("ALFA");
        proveedor("SUMEBA");
        JsonNode e = leer(peticionPost("/equipment",
                "{\"category\":\"contadores\",\"group\":\"electricidad\",\"brand\":\"alfa\",\"supplier\":\"sumeba\"}")
                .andExpect(status().isCreated()));
        assertEquals("ALFA", e.get("brand").asText());
        assertEquals("SUMEBA", e.get("supplier").asText());
    }

    @Test
    void altaConPrecioCreaLaOfertaDelPrincipalDaPorRevisadoYAnotaElHistorial() throws Exception {
        proveedor("SUMEBA");
        String id = equipo("{\"category\":\"contadores\",\"group\":\"electricidad\",\"supplier\":\"SUMEBA\",\"unit_price\":12.5}");
        JsonNode e = leer(mvc.perform(get(BASE + "/equipment")).andExpect(status().isOk())).get("items").get(0);
        assertEquals(1, e.get("offers").size());
        assertEquals("SUMEBA", e.get("offers").get(0).get("supplier").asText());
        assertEquals(12.5, e.get("offers").get(0).get("price").asDouble());
        assertEquals(LocalDate.now().toString(), e.get("price_reviewed_at").asText());
        JsonNode h = leer(mvc.perform(get(BASE + "/equipment/" + id + "/price-history")).andExpect(status().isOk()));
        assertEquals(1, h.size());
        assertEquals(12.5, h.get(0).get("price").asDouble());
        assertEquals("Ana", h.get(0).get("by").asText());
        assertEquals("SUMEBA", h.get(0).get("supplier").asText());
        assertTrue(h.get(0).has("at") && h.get(0).has("offers"));
    }

    @Test
    void cambiarElPrecioActualizaLaOfertaDelPrincipalElHistorialYQuitaLaMarcaDePrueba() throws Exception {
        proveedor("SUMEBA");
        String id = equipo("{\"category\":\"contadores\",\"group\":\"electricidad\",\"supplier\":\"SUMEBA\",\"unit_price\":10}");
        JsonNode e = leer(peticionPatch("/equipment/" + id, "{\"unit_price\":15}").andExpect(status().isOk()));
        assertEquals(15.0, e.get("unit_price").asDouble());
        assertEquals(15.0, e.get("offers").get(0).get("price").asDouble());
        assertFalse(e.get("sample").asBoolean());
        JsonNode h = leer(mvc.perform(get(BASE + "/equipment/" + id + "/price-history")));
        assertEquals(2, h.size());
        assertEquals(15.0, h.get(1).get("price").asDouble());
        assertEquals("Luis Pérez", h.get(1).get("by").asText(), "el nombre de X-Usuario se descodifica");
    }

    @Test
    void sinCabecerasElHistorialSeFirmaSinIdentificar() throws Exception {
        String id = leer(mvc.perform(post(BASE + "/equipment").contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"contadores\",\"group\":\"electricidad\",\"unit_price\":3}"))
                .andExpect(status().isCreated())).get("id").asText();
        JsonNode h = leer(mvc.perform(get(BASE + "/equipment/" + id + "/price-history")));
        assertEquals("Sin identificar", h.get(0).get("by").asText());
    }

    @Test
    void cambiarSoloLasNotasNoTocaElHistorialNiLaRevision() throws Exception {
        String id = equipo("{\"category\":\"contadores\",\"group\":\"electricidad\"}");
        JsonNode e = leer(peticionPatch("/equipment/" + id, "{\"notes\":\"hola\"}").andExpect(status().isOk()));
        assertEquals("hola", e.get("notes").asText());
        assertTrue(e.get("price_reviewed_at").isNull());
        assertEquals(0, leer(mvc.perform(get(BASE + "/equipment/" + id + "/price-history"))).size());
    }

    @Test
    void ofertasDeterminanElPrecioYElPrincipalPasaALaPrimeraSiNoEstaEntreEllas() throws Exception {
        proveedor("AAA");
        proveedor("BBB");
        String id = equipo("{\"category\":\"contadores\",\"group\":\"electricidad\",\"supplier\":\"AAA\",\"unit_price\":10}");
        JsonNode e = leer(peticionPatch("/equipment/" + id,
                "{\"offers\":[{\"supplier\":\"bbb\",\"price\":7.123456,\"ref\":\" R1 \",\"note\":\"n\"}]}")
                .andExpect(status().isOk()));
        assertEquals("BBB", e.get("supplier").asText());
        assertEquals(7.1235, e.get("unit_price").asDouble());
        assertEquals("R1", e.get("offers").get(0).get("ref").asText());
        peticionPatch("/equipment/" + id, "{\"supplier\":\"AAA\"}").andExpect(status().isOk());
    }

    @Test
    void cambiarElProveedorPrincipalTomaElPrecioDeSuOfertaOCreaLaOferta() throws Exception {
        proveedor("AAA");
        proveedor("BBB");
        String id = equipo("{\"category\":\"contadores\",\"group\":\"electricidad\",\"supplier\":\"AAA\",\"unit_price\":10,"
                + "\"offers\":[{\"supplier\":\"AAA\",\"price\":10},{\"supplier\":\"BBB\",\"price\":8}]}");
        JsonNode e = leer(peticionPatch("/equipment/" + id, "{\"supplier\":\"BBB\"}").andExpect(status().isOk()));
        assertEquals(8.0, e.get("unit_price").asDouble());
        assertEquals(2, e.get("offers").size());
    }

    @Test
    void ofertasConProveedorRepetidoOSinProveedorOPrecioMaloSonError422() throws Exception {
        proveedor("AAA");
        String base = "{\"category\":\"contadores\",\"group\":\"electricidad\",\"offers\":";
        peticionPost("/equipment", base + "[{\"supplier\":\"AAA\",\"price\":1},{\"supplier\":\"aaa\",\"price\":2}]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El proveedor AAA está repetido en el producto"));
        peticionPost("/equipment", base + "[{\"price\":1}]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Cada oferta necesita un proveedor"));
        peticionPost("/equipment", base + "[{\"supplier\":\"AAA\",\"price\":\"abc\"}]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El precio de AAA no es un número"));
        peticionPost("/equipment", base + "[{\"supplier\":\"AAA\",\"price\":-2}]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El precio de AAA tiene que ser 0 o más"));
    }

    @Test
    void laListaCerradaDeProteccionesNormalizaElValorYRechazaLosDemas() throws Exception {
        JsonNode e = leer(peticionPost("/equipment", "{\"category\":\"protecciones\",\"group\":\"electricidad\","
                + "\"attrs\":{\"tipo\":\"cc\",\"elemento\":\"rele  DIFERENCIAL\"}}").andExpect(status().isCreated()));
        assertEquals("CC", e.get("attrs").get("tipo").asText());
        assertEquals("Relé diferencial", e.get("attrs").get("elemento").asText());
        peticionPost("/equipment", "{\"category\":\"protecciones\",\"group\":\"electricidad\",\"attrs\":{\"elemento\":\"Cosa\"}}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("«Cosa» no es un valor válido de «elemento»: elige uno de la lista ("
                        + "Conjunto, Fusible, Portafusibles, Fusible con portafusibles, Seccionador, DPS, Cuadro, "
                        + "Magnetotérmico, Diferencial, Relé diferencial, Transformador)"));
        // El valor antiguo «fusibles por string» se reconoce con su nombre nuevo
        JsonNode antiguo = leer(peticionPost("/equipment", "{\"category\":\"protecciones\",\"group\":\"electricidad\","
                + "\"attrs\":{\"elemento\":\"Fusibles por string\"}}").andExpect(status().isCreated()));
        assertEquals("Fusible con portafusibles", antiguo.get("attrs").get("elemento").asText());
    }

    @Test
    void editarUnEquipoValidaTiposYRangos() throws Exception {
        String id = equipo("{\"category\":\"contadores\",\"group\":\"electricidad\"}");
        peticionPatch("/equipment/" + id, "{\"unit_price\":-1}").andExpect(status().isUnprocessableEntity());
        peticionPatch("/equipment/" + id, "{\"stock\":\"mucho\"}").andExpect(status().isUnprocessableEntity());
        peticionPatch("/equipment/" + id, "{\"category\":\"logistica\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La categoría «logistica» no puede estar en la partida «electricidad»"));
    }

    @Test
    void editarOEliminarUnEquipoInexistenteEs404() throws Exception {
        peticionPatch("/equipment/nada", "{\"notes\":\"x\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Equipo no encontrado"));
        mvc.perform(delete(BASE + "/equipment/nada")).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/equipment/nada/price-history")).andExpect(status().isNotFound());
    }

    @Test
    void eliminarUnEquipoBorraTambienSuHistorial() throws Exception {
        String id = equipo("{\"category\":\"contadores\",\"group\":\"electricidad\",\"unit_price\":5}");
        mvc.perform(delete(BASE + "/equipment/" + id)).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pl_historial_precios", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pl_equipos", Integer.class));
    }

    // ------------------------------------------------------------------- fichas

    @Test
    void fichaTecnicaDebeSerUnPdfEnLaCarpetaDeSuMarca() throws Exception {
        marca("ALFA");
        marca("BETA");
        String base = "{\"category\":\"contadores\",\"group\":\"electricidad\",\"brand\":\"ALFA\",\"datasheet\":";
        peticionPost("/equipment", base + "\"ALFA/x.pdf\"}").andExpect(status().isCreated());
        peticionPost("/equipment", base + "\"alfa/x.PDF\"}").andExpect(status().isCreated());
        peticionPost("/equipment", base + "\"ALFA/sub/x.pdf\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La ficha tiene que ser un PDF de la carpeta de su marca (MARCA/archivo.pdf)"));
        peticionPost("/equipment", base + "\"../x.pdf\"}").andExpect(status().isUnprocessableEntity());
        peticionPost("/equipment", base + "\"ALFA/x.txt\"}").andExpect(status().isUnprocessableEntity());
        peticionPost("/equipment", base + "\"BETA/x.pdf\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La ficha tiene que estar en la carpeta de la marca del producto"));
    }

    @Test
    void quitarLaFichaFuncionaYSubirOVerDependenDeDropbox() throws Exception {
        marca("ALFA");
        String id = equipo("{\"category\":\"contadores\",\"group\":\"electricidad\",\"brand\":\"ALFA\",\"datasheet\":\"ALFA/x.pdf\"}");
        mvc.perform(get(BASE + "/equipment/" + id + "/datasheet")).andExpect(status().isServiceUnavailable());
        mvc.perform(get(BASE + "/equipment/" + id + "/datasheet-options")).andExpect(status().isConflict());
        mvc.perform(put(BASE + "/equipment/" + id + "/datasheet?name=a.pdf").content("%PDF-1"))
                .andExpect(status().isConflict());
        mvc.perform(delete(BASE + "/equipment/" + id + "/datasheet")).andExpect(status().isOk())
                .andExpect(jsonPath("$.datasheet").value(""));
        mvc.perform(get(BASE + "/equipment/" + id + "/datasheet")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Este producto no tiene ficha técnica adjunta"));
    }

    // -------------------------------------------------------- revisión de precios

    @Test
    void estadoDeRevisionSinNadaRevisadoCuentaDesdeHoy() throws Exception {
        String hoy = LocalDate.now().toString();
        mvc.perform(get(BASE + "/equipment/price-review-status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.last_review").value(hoy))
                .andExpect(jsonPath("$.due_date").value(LocalDate.now().plusDays(15).toString()))
                .andExpect(jsonPath("$.days_since").value(0))
                .andExpect(jsonPath("$.days_left").value(15))
                .andExpect(jsonPath("$.notified").value(false))
                .andExpect(jsonPath("$.reviewed_products").value(0))
                .andExpect(jsonPath("$.plazo").value(15));
    }

    @Test
    void marcarPreciosRevisadosPonePrecioRevisadoSinCambiarlosYSeVeEnElEstado() throws Exception {
        String id = equipo("{\"category\":\"contadores\",\"group\":\"electricidad\"}");
        peticionPost("/equipment/price-review", "{\"ids\":[\"" + id + "\",\"no-existe\"]}").andExpect(status().isOk())
                .andExpect(content().json("{\"reviewed\":1,\"date\":\"" + LocalDate.now() + "\"}", true));
        mvc.perform(get(BASE + "/equipment/price-review-status")).andExpect(jsonPath("$.reviewed_products").value(1));
        assertEquals(0, leer(mvc.perform(get(BASE + "/equipment/" + id + "/price-history"))).size());
    }
}
