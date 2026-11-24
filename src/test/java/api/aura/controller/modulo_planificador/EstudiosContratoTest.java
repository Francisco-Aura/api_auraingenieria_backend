package api.aura.controller.modulo_planificador;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Estudios fotovoltaicos: registro, SIPS, curva, cálculo e informe por HTTP. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class EstudiosContratoTest {

    private static final String BASE = "/api/planificador/studies";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("DELETE FROM pl_estudios");
        jdbc.execute("DELETE FROM pl_precios_regulados");
        jdbc.execute("DELETE FROM pl_boes");
    }

    /** Añade un BOE (un conjunto de peajes y cargos) y devuelve su id. */
    private int boe(String nombre, String desde) {
        jdbc.update("INSERT INTO pl_boes (nombre, vigente_desde) VALUES (?, ?)", nombre, java.sql.Date.valueOf(desde));
        return jdbc.queryForObject("SELECT MAX(id) FROM pl_boes", Integer.class);
    }

    /** Los precios de la 6.1TD de un BOE: los reales de 2026. */
    private void preciosBoe61(int boeId) {
        String sql = "INSERT INTO pl_precios_regulados (boe_id, tarifa, termino, concepto, p1, p2, p3, p4, p5, p6, referencia) VALUES (?, '6.1TD', ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbc.update(sql, boeId, "energia", "peaje", 0.026785, 0.012281, 0.005133, 0.002780, 0.000120, 0.000029, "BOE-A-2025-26348");
        jdbc.update(sql, boeId, "energia", "cargo", 0.019489, 0.014436, 0.007795, 0.003898, 0.002499, 0.001559, "BOE-A-2025-26705");
        jdbc.update(sql, boeId, "potencia", "peaje", 23.946498, 12.687713, 4.747747, 3.339695, 0.070979, 0.062703, "BOE-A-2025-26348");
        jdbc.update(sql, boeId, "potencia", "cargo", 5.648870, 2.826996, 2.054134, 2.054134, 2.054134, 0.941478, "BOE-A-2025-26705");
    }

    /** Los precios de la 6.1TD de un BOE con el mismo valor `v` en todo (peaje y cargo): sirve para saber cuál se ha usado. */
    private void preciosUniformes61(int boeId, double v) {
        String sql = "INSERT INTO pl_precios_regulados (boe_id, tarifa, termino, concepto, p1, p2, p3, p4, p5, p6, referencia) VALUES (?, '6.1TD', ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        for (String termino : new String[]{"energia", "potencia"})
            for (String concepto : new String[]{"peaje", "cargo"}) jdbc.update(sql, boeId, termino, concepto, v, v, v, v, v, v, "BOE-A-PRUEBA");
    }

    @Test
    void elEstudioUsaElBoeVigenteOElQueSeEligeAunqueHayaVariosElMismoAnioOUnoCadaDos() throws Exception {
        // Dos BOE en 2025 (uno cada medio año), uno en 2026 (el vigente hoy: el último que ha empezado) y otro que empezará en 2099
        int a = boe("Peajes y cargos 2025", "2025-01-01");
        int b = boe("Cargos julio 2025", "2025-07-01");
        int c = boe("Peajes y cargos 2026", "2026-01-01");
        int futuro = boe("Futuro", "2099-01-01");
        preciosUniformes61(a, 1);
        preciosUniformes61(b, 2);
        preciosBoe61(c);
        preciosUniformes61(futuro, 9);
        JsonNode e = crearConConsumo("Cliente BOE", sips(12), null);
        String id = e.get("id").asText();

        JsonNode vigente = e.get("resultado").get("precios").get("regulados");
        assertEquals("Peajes y cargos 2026", vigente.get("nombre").asText());
        assertEquals("2026-01-01", vigente.get("desde").asText());
        assertEquals(0, e.get("resultado").get("precios").get("avisos").size());

        // Uno elegido a mano, del mismo año que otro, o uno que aún no ha empezado: manda el elegido, sin avisos
        JsonNode junio = cambiar(id, "{\"datos\":{\"economia\":{\"boeId\":" + b + "}}}").get("resultado").get("precios");
        assertEquals("Cargos julio 2025", junio.get("regulados").get("nombre").asText());
        assertEquals(2 + 2, junio.get("regulados").get("energia").get(0).asDouble(), 1e-9, "peaje + cargo del BOE elegido");
        assertEquals(0, junio.get("avisos").size());
        JsonNode del2099 = cambiar(id, "{\"datos\":{\"economia\":{\"boeId\":" + futuro + "}}}").get("resultado").get("precios");
        assertEquals("Futuro", del2099.get("regulados").get("nombre").asText());
        assertEquals(0, del2099.get("avisos").size());

        // Uno que ya no existe cae al vigente, con aviso; y quitar la elección también vuelve al vigente
        JsonNode inexistente = cambiar(id, "{\"datos\":{\"economia\":{\"boeId\":99999}}}").get("resultado").get("precios");
        assertEquals("Peajes y cargos 2026", inexistente.get("regulados").get("nombre").asText());
        assertTrue(inexistente.get("avisos").get(0).asText().contains("ya no existe"));
        assertEquals("Peajes y cargos 2026", cambiar(id, "{\"datos\":{\"economia\":{\"boeId\":\"\"}}}").get("resultado").get("precios").get("regulados").get("nombre").asText());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"datos\":{\"economia\":{\"boeId\":0}}}")).andExpect(status().isUnprocessableEntity());

        // Si ninguno ha empezado todavía se usa el más antiguo, con aviso
        jdbc.execute("DELETE FROM pl_precios_regulados WHERE boe_id IN (" + a + ", " + b + ", " + c + ")");
        jdbc.execute("DELETE FROM pl_boes WHERE id IN (" + a + ", " + b + ", " + c + ")");
        JsonNode soloFuturo = cambiar(id, "{\"datos\":{\"economia\":{\"observacion\":1}}}").get("resultado").get("precios");
        assertEquals("Futuro", soloFuturo.get("regulados").get("nombre").asText());
        assertTrue(soloFuturo.get("avisos").get(0).asText().contains("Ningún BOE está vigente todavía"));
    }

    @Test
    void conLaTarifaDelSipsUsaLosPeajesYCargosDelBoePorPeriodo() throws Exception {
        preciosBoe61(boe("Peajes y cargos 2026", "2026-01-01"));
        JsonNode a = crearConConsumo("Cliente BOE", sips(12), null);
        String id = a.get("id").asText();
        JsonNode reg = a.get("resultado").get("precios").get("regulados");
        assertEquals("Peajes y cargos 2026", reg.get("nombre").asText());
        assertEquals(0.026785 + 0.019489, reg.get("energia").get(0).asDouble(), 1e-9);
        assertEquals(23.946498 + 5.648870, reg.get("potencia").get(0).asDouble(), 1e-9);
        assertEquals("medio", a.get("resultado").get("precios").get("modo").asText());

        JsonNode b = cambiar(id, "{\"datos\":{\"economia\":{\"modoPrecio\":\"periodos\",\"incluyePeajes\":false,"
                + "\"preciosPeriodo\":[0.09,0.085,0.08,0.075,0.07,0.065],\"reduccionPotencia\":[10,0,0,0,0,0]}}}");
        JsonNode p = b.get("resultado").get("precios");
        assertEquals("periodos", p.get("modo").asText());
        assertEquals(0, p.get("avisos").size(), p.toString());
        assertEquals(0.09 + 0.026785 + 0.019489, p.get("preciosPeriodo").get(0).asDouble(), 1e-9);
        assertEquals(10 * (23.946498 + 5.648870), p.get("ahorroPotencia").asDouble(), 1e-6);
        assertTrue(p.get("potenciaCalculada").asBoolean());
        String html = mvc.perform(get(BASE + "/" + id + "/report.html")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("peajes y cargos del BOE «Peajes y cargos 2026»"));
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"datos\":{\"economia\":{\"modoPrecio\":\"raro\"}}}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"datos\":{\"economia\":{\"preciosPeriodo\":[5,0,0,0,0,0]}}}")).andExpect(status().isUnprocessableEntity());
    }

    private JsonNode crear(String cliente) throws Exception {
        String r = mvc.perform(post(BASE).contentType("application/json").content("{\"cliente\":\"" + cliente + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(r);
    }

    private static byte[] sips(int lecturas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet h = wb.createSheet("sips");
            h.createRow(0).createCell(0).setCellValue("Datos suministro");
            Row cab = h.createRow(1);
            String[] nombres = {"", "Distribuidora", "Tarifa", "Localidad", "Provincia", "Nombre Completo Titular", "Potencia Contratada P1", "Potencia Contratada P2",
                    "Potencia Contratada P3", "Potencia Contratada P4", "Potencia Contratada P5", "Potencia Contratada P6", "kWh Anual"};
            for (int i = 0; i < nombres.length; i++) cab.createCell(i).setCellValue(nombres[i]);
            Row val = h.createRow(2);
            val.createCell(0).setCellValue("ES0021000000000000XX");
            val.createCell(1).setCellValue("i-DE");
            val.createCell(2).setCellValue("6.1TD");
            val.createCell(3).setCellValue("Lorca");
            val.createCell(4).setCellValue("Murcia");
            val.createCell(5).setCellValue("TITULAR PRIVADO");
            for (int p = 0; p < 6; p++) val.createCell(6 + p).setCellValue(150);
            val.createCell(12).setCellValue(96000);
            h.createRow(3).createCell(0).setCellValue("Datos lecturas");
            Row cl = h.createRow(4);
            cl.createCell(1).setCellValue("Fecha Lectura");
            cl.createCell(2).setCellValue("Fecha Lectura Anterior");
            for (int p = 0; p < 6; p++) cl.createCell(4 + p).setCellValue("Consumo Activa P" + (p + 1));
            LocalDate fin = LocalDate.of(2026, 9, 30);
            for (int m = 0; m < lecturas; m++) {
                LocalDate hasta = fin.minusMonths(m).withDayOfMonth(fin.minusMonths(m).lengthOfMonth());
                LocalDate desde = hasta.minusMonths(1).withDayOfMonth(hasta.minusMonths(1).lengthOfMonth());
                Row r = h.createRow(5 + m);
                r.createCell(0).setCellValue("ES0021000000000000XX");
                r.createCell(1).setCellValue(hasta.toEpochDay() + 25569);
                r.createCell(2).setCellValue(desde.toEpochDay() + 25569);
                for (int p = 0; p < 6; p++) r.createCell(4 + p).setCellValue(1500);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Un estudio guardado con su SIPS y/o su curva real, creado como lo hace la pantalla: los ficheros se leen sin guardar y se mandan enteros. */
    private JsonNode crearConConsumo(String cliente, byte[] sips, byte[] curva) throws Exception {
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.putObject("customer").put("kind", "potencial").put("name", cliente);
        if (sips != null) cuerpo.set("datos", sinGuardar("read-sips", "sips.xlsx", sips));
        if (curva != null) cuerpo.set("curva", sinGuardar("read-curve", "curva.csv", curva));
        return enviar("", json.writeValueAsString(cuerpo));
    }

    private JsonNode cambiar(String id, String cuerpo) throws Exception {
        return json.readTree(mvc.perform(put(BASE + "/" + id).contentType("application/json").content(cuerpo)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void creaUnEstudioConEstadoPorDefectoYSinCalcular() throws Exception {
        JsonNode e = crear("Curtidos Prueba");
        assertEquals("en_estudio", e.get("estado").asText());
        assertFalse(e.get("calculado").asBoolean());
        assertEquals(7, e.get("estados").size());
        assertEquals(0.10, e.get("datos").get("economia").get("precioEnergia").asDouble(), 1e-9);
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].cliente").value("Curtidos Prueba"));
        mvc.perform(post(BASE).contentType("application/json").content("{\"cliente\":\"  \"}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void elClienteSeEligeComoEnElPresupuestadorExistenteOPotencial() throws Exception {
        String existente = "{\"customer\":{\"kind\":\"existente\",\"id\":42,\"name\":\"Ingaser SL\",\"tradeName\":\"Ingaser\",\"cif\":\"B12345678\"},\"proyecto\":\"Nave 2, fotovoltaica\"}";
        JsonNode e = json.readTree(mvc.perform(post(BASE).contentType("application/json").content(existente)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("Ingaser SL", e.get("cliente").asText());
        assertEquals("Nave 2, fotovoltaica", e.get("proyecto").asText());
        assertEquals("Cubierta oficinas", cambiar(e.get("id").asText(), "{\"proyecto\":\"Cubierta oficinas\"}").get("proyecto").asText());
        assertEquals("existente", e.get("customer").get("kind").asText());
        assertEquals(42, e.get("customer").get("id").asInt());
        assertEquals(42, jdbc.queryForObject("SELECT cliente_id FROM pl_estudios WHERE id = ?", Integer.class, e.get("id").asText()));

        // Se cambia por un cliente potencial: sin id en la intranet
        JsonNode p = cambiar(e.get("id").asText(), "{\"customer\":{\"kind\":\"potencial\",\"name\":\"Pedro Ingaser\",\"cif\":\"\",\"phone\":\"600000000\",\"email\":\"\"}}");
        assertEquals("Pedro Ingaser", p.get("cliente").asText());
        assertEquals("potencial", p.get("customer").get("kind").asText());
        assertEquals("600000000", p.get("customer").get("phone").asText());
        assertEquals(null, jdbc.queryForObject("SELECT cliente_id FROM pl_estudios WHERE id = ?", Integer.class, e.get("id").asText()));

        // Un estudio creado solo con el nombre queda como cliente potencial
        assertEquals("potencial", crear("Solo nombre").get("customer").get("kind").asText());

        mvc.perform(post(BASE).contentType("application/json").content("{\"customer\":{\"kind\":\"existente\",\"name\":\"Sin id\"}}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(BASE).contentType("application/json").content("{\"customer\":{\"kind\":\"potencial\",\"id\":3,\"name\":\"Con id\"}}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(BASE).contentType("application/json").content("{\"customer\":{\"kind\":\"potencial\",\"name\":\" \"}}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void elSipsRellenaElSuministroYEstimaLaCurvaSinGuardarAlTitular() throws Exception {
        JsonNode r = crearConConsumo("Cliente SIPS", sips(12), null);
        assertEquals("6.1TD", r.get("datos").get("tarifa").asText());
        assertEquals("ES0021000000000000XX", r.get("cups").asText());
        assertEquals("Lorca", r.get("datos").get("localidad").asText());
        assertTrue(r.get("curva").get("estimada").asBoolean());
        assertTrue(r.get("calculado").asBoolean());
        assertTrue(r.get("datos").get("instalacion").get("kwp").asDouble() > 0, "propone una potencia");
        assertFalse(r.toString().contains("TITULAR PRIVADO"), "el titular no se guarda");
        assertEquals(6, r.get("datos").get("sips").get("potencias").size());
        assertEquals(6 * 1500 * 12, r.get("resultado").get("consumo").get("consumoAnual").asDouble(), 6 * 1500 * 12 * 0.01);
    }

    /** El SIPS como lo da la web de la distribuidora: un «.xls» que en realidad es una tabla HTML, con fechas ISO y decimales en coma. */
    private static byte[] sipsTablaWeb(int meses) {
        StringBuilder sb = new StringBuilder("<html xmlns:x='urn:schemas-microsoft-com:office:excel'><head><meta http-equiv=\"Content-type\" content=\"text/html;charset=utf-8\" /></head>"
                + "<body><table id='tabledata'><thead><tr><th>CUPS</th><th>Fecha Lectura Inicial</th><th>Fecha Lectura Final</th>");
        for (int p = 1; p <= 6; p++) sb.append("<th>P").append(p).append(" Activa</th>");
        for (int p = 1; p <= 6; p++) sb.append("<th>P").append(p).append(" Reactiva</th>");
        sb.append("</tr></thead><tbody><tr><td>ES0021000000000000XX</td>").append("<td></td>".repeat(14)).append("</tr>");
        LocalDate fin = LocalDate.of(2026, 7, 31);
        for (int m = meses - 1; m >= 0; m--) {
            sb.append("<tr><td></td><td>").append(fin.minusMonths(m + 1)).append("T00:00:00</td><td>").append(fin.minusMonths(m)).append("T00:00:00</td>");
            for (int p = 0; p < 6; p++) sb.append("<td>1500,000</td>");
            for (int p = 0; p < 6; p++) sb.append("<td>10,000</td>");
            sb.append("</tr>");
        }
        return sb.append("</tbody></table></body></html>").toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void elSipsComoTablaWebDeLaDistribuidoraSeLeeYEstimaLaCurva() throws Exception {
        JsonNode sips = sinGuardar("read-sips", "Contenido-tabla.xls", sipsTablaWeb(14));
        assertEquals("ES0021000000000000XX", sips.get("cups").asText());
        assertEquals("", sips.get("tarifa").asText(), "esta tabla no trae la tarifa");
        assertEquals(0, sips.get("potencias").size(), "ni las potencias contratadas");
        assertEquals(14, sips.get("sips").get("lecturas").size());
        assertEquals(6 * 1500 * 12, sips.get("sips").get("kwhAnual").asDouble(), 1e-6, "el consumo anual sale de las lecturas del último año");

        // Con esas lecturas se estima la curva y se calcula el estudio
        JsonNode e = crearConConsumo("Cliente tabla web", sipsTablaWeb(14), null);
        assertTrue(e.get("curva").get("estimada").asBoolean());
        assertTrue(e.get("calculado").asBoolean());
        assertEquals(6 * 1500 * 12, e.get("resultado").get("consumo").get("consumoAnual").asDouble(), 6 * 1500 * 12 * 0.01);

        // Una página web que no es un SIPS se rechaza con un mensaje que lo explica
        mvc.perform(multipart(BASE + "/read-sips").file(new MockMultipartFile("file", "x.xls", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                        "<html><body><table><tr><td>hola</td></tr></table></body></html>".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no parece un SIPS")));
    }

    @Test
    void conPocasLecturasElSipsSeGuardaYAvisaDeQueFaltaLaCurva() throws Exception {
        JsonNode r = crearConConsumo("Cliente corto", sips(3), null);
        assertTrue(r.get("aviso").asText().contains("faltan meses"));
        assertFalse(r.get("calculado").asBoolean());
        mvc.perform(get(BASE + "/" + r.get("id").asText() + "/report.html")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cambiarLaInstalacionRecalculaYUnaCurvaRealSustituyeALaEstimada() throws Exception {
        String id = crearConConsumo("Cliente real", sips(12), null).get("id").asText();
        JsonNode a = cambiar(id, "{\"datos\":{\"instalacion\":{\"kwp\":100,\"bateriaKwh\":0,\"orientacion\":\"sur\"}}}");
        JsonNode b = cambiar(id, "{\"datos\":{\"instalacion\":{\"kwp\":100,\"bateriaKwh\":120,\"orientacion\":\"sur\"}}}");
        assertTrue(b.get("resultado").get("simulacion").get("cobertura").asDouble() > a.get("resultado").get("simulacion").get("cobertura").asDouble());
        assertEquals(5, b.get("resultado").get("escenarios").size());
        // La curva real llega como parte del estudio (el navegador la leyó antes sin guardar nada)
        JsonNode c = cambiar(id, "{\"curva\":" + sinGuardar("read-curve", "curva.csv", curvaCsv()) + "}");
        assertFalse(c.get("curva").get("estimada").asBoolean());
        assertEquals("curva.csv", c.get("curva").get("origen").asText());
        assertEquals(365 * (10 * 20 + 14 * 3), c.get("resultado").get("consumo").get("consumoAnual").asDouble(), 100);
        // Un horario nuevo ya no toca una curva real
        JsonNode d = cambiar(id, "{\"datos\":{\"horario\":{\"ini\":6,\"fin\":20}}}");
        assertFalse(d.get("curva").get("estimada").asBoolean());
    }

    @Test
    void elCosteDeLaInstalacionSaleDelPresupuestoDeReferenciaYSeGuardaConElEstudio() throws Exception {
        String id = crearConConsumo("Cliente coste", sips(12), null).get("id").asText();
        String instalacion = "\"instalacion\":{\"kwp\":10,\"bateriaKwh\":0,\"orientacion\":\"sur\"}";
        // Sin presupuesto: el precio provisional (0,85 €/Wp)
        JsonNode a = cambiar(id, "{\"datos\":{" + instalacion + ",\"economia\":{\"precioWp\":0.85}}}");
        assertEquals(8500, a.get("resultado").get("finanzas").get("inversion").asDouble(), 0.5);
        // Con presupuesto de referencia: la inversión es la suya y el estudio lo recuerda
        JsonNode b = cambiar(id, "{\"datos\":{\"economia\":{\"presupuestoId\":\"p-1\",\"presupuestoTotal\":11500,\"presupuestoKwp\":10}}}");
        assertEquals(11500, b.get("resultado").get("finanzas").get("inversion").asDouble(), 0.5);
        assertEquals("p-1", b.get("datos").get("economia").get("presupuestoId").asText());
        // Se puede volver al precio provisional
        JsonNode c = cambiar(id, "{\"datos\":{\"economia\":{\"presupuestoId\":\"\",\"presupuestoTotal\":\"\",\"presupuestoKwp\":\"\"}}}");
        assertEquals(8500, c.get("resultado").get("finanzas").get("inversion").asDouble(), 0.5);
    }

    private JsonNode sinGuardar(String ruta, String nombre, byte[] bytes) throws Exception {
        return json.readTree(mvc.perform(multipart(BASE + "/" + ruta).file(new MockMultipartFile("file", nombre, MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode enviar(String ruta, String cuerpo) throws Exception {
        return json.readTree(mvc.perform(post(BASE + ruta).contentType("application/json").content(cuerpo)).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void unEstudioLocalSeCalculaSinGuardarYAlCompartirloSeGuardaConSuId() throws Exception {
        // El SIPS y la curva se leen sin guardar nada: el navegador se queda con sus datos
        JsonNode sips = sinGuardar("read-sips", "sips.xlsx", sips(12));
        assertEquals("ES0021000000000000XX", sips.get("cups").asText());
        assertEquals("6.1TD", sips.get("tarifa").asText());
        assertFalse(sips.has("nombre") || sips.get("sips").has("suministro"), "el titular no viaja");
        JsonNode curva = sinGuardar("read-curve", "curva.csv", curvaCsv());
        assertEquals("curva.csv", curva.get("origen").asText());
        assertEquals(8760, curva.get("texto").asText().split(",").length);
        Integer antes = jdbc.queryForObject("SELECT COUNT(*) FROM pl_estudios", Integer.class);
        assertEquals(0, antes);

        // Calcular un estudio entero tampoco guarda: con SIPS se estima la curva; con la curva real, manda ella
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.set("customer", json.readTree("{\"kind\":\"potencial\",\"name\":\"Local SA\"}"));
        cuerpo.set("datos", json.createObjectNode().setAll((ObjectNode) sips));
        JsonNode a = enviar("/calculate", json.writeValueAsString(cuerpo));
        assertTrue(a.get("curva").get("estimada").asBoolean());
        assertTrue(a.get("calculado").asBoolean());
        // Las recomendaciones se escriben con los números del estudio (sin guardar nada): con curva estimada sale la de confirmarla
        JsonNode ra = enviar("/recommendations", json.writeValueAsString(cuerpo)).get("recomendaciones");
        assertTrue(ra.size() >= 3);        java.util.Set<String> idsA = new java.util.HashSet<>();
        for (JsonNode r : ra) {
            idsA.add(r.get("id").asText());
            assertTrue(r.get("sugerida").asBoolean() && !r.get("titulo").asText().isBlank() && !r.get("texto").asText().contains("{"), r.toString());
        }
        assertTrue(idsA.containsAll(java.util.List.of("potencia", "orientacion", "rentabilidad", "curva")), idsA.toString());
        cuerpo.set("curva", curva);
        java.util.Set<String> idsB = new java.util.HashSet<>();
        for (JsonNode r : enviar("/recommendations", json.writeValueAsString(cuerpo)).get("recomendaciones")) idsB.add(r.get("id").asText());
        assertTrue(idsB.contains("potencia") && !idsB.contains("curva"), "con la curva real no hay que confirmarla: " + idsB);
        JsonNode b = enviar("/calculate", json.writeValueAsString(cuerpo));
        assertFalse(b.get("curva").get("estimada").asBoolean());
        assertEquals(365 * (10 * 20 + 14 * 3), b.get("resultado").get("consumo").get("consumoAnual").asDouble(), 100);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pl_estudios", Integer.class));

        // El informe de un estudio local también sale sin guardarlo
        mvc.perform(post(BASE + "/report.html").contentType("application/json").content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Local SA")));

        // «Guardar y compartir»: se guarda entero con el id del navegador, en la versión 1
        String id = "11111111-2222-3333-4444-555555555555";
        cuerpo.put("id", id);
        JsonNode c = enviar("", json.writeValueAsString(cuerpo));
        assertEquals(id, c.get("id").asText());
        assertEquals(1, c.get("version").asInt());
        assertFalse(c.get("curva").get("estimada").asBoolean());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pl_estudios", Integer.class));
        // Compartirlo otra vez no lo duplica
        mvc.perform(post(BASE).contentType("application/json").content(json.writeValueAsString(cuerpo))).andExpect(status().isConflict());
        mvc.perform(post(BASE).contentType("application/json").content("{\"id\":\"../x\",\"cliente\":\"X\"}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void guardarConUnaVersionVieja409YConForceSeGuardaEncima() throws Exception {
        String id = crear("Cliente versiones").get("id").asText();
        JsonNode v1 = json.readTree(mvc.perform(get(BASE + "/" + id)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(1, v1.get("version").asInt());
        JsonNode v2 = cambiar(id, "{\"version\":1,\"observaciones\":\"primera\"}");
        assertEquals(2, v2.get("version").asInt());

        // Quien abrió la versión 1 y guarda ahora recibe el aviso, y su cambio no se guarda
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"version\":1,\"observaciones\":\"segunda\"}")).andExpect(status().isConflict());
        assertEquals("primera", json.readTree(mvc.perform(get(BASE + "/" + id)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("observaciones").asText());

        JsonNode v3 = cambiar(id, "{\"version\":1,\"force\":true,\"observaciones\":\"segunda\"}");
        assertEquals(3, v3.get("version").asInt());
        assertEquals("segunda", v3.get("observaciones").asText());
    }

    private static byte[] curvaCsv() {
        StringBuilder sb = new StringBuilder("Fecha;Hora;Consumo_kWh\n");
        for (LocalDate d = LocalDate.of(2025, 10, 1); d.isBefore(LocalDate.of(2026, 10, 1)); d = d.plusDays(1)) {
            for (int h = 1; h <= 24; h++) sb.append(String.format("%02d/%02d/%d;%d;%s\n", d.getDayOfMonth(), d.getMonthValue(), d.getYear(), h, h >= 8 && h <= 17 ? "20" : "3"));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void quitarElSipsOLaCurvaEsGuardarElEstudioSinEllos() throws Exception {
        String id = crearConConsumo("Cliente quitar", sips(12), curvaCsv()).get("id").asText();
        JsonNode sips = sinGuardar("read-sips", "sips.xlsx", sips(12));
        JsonNode curva = sinGuardar("read-curve", "curva.csv", curvaCsv());

        // Sin la curva real (pero con el SIPS) se vuelve a la estimada con sus lecturas
        ObjectNode sinCurva = json.createObjectNode();
        sinCurva.set("datos", sips);
        sinCurva.putNull("curva");
        JsonNode a = cambiar(id, sinCurva.toString());
        assertTrue(a.get("curva").get("presente").asBoolean());
        assertTrue(a.get("curva").get("estimada").asBoolean());
        assertTrue(a.get("calculado").asBoolean());

        // Sin SIPS ni curva se va la curva estimada, el suministro y la tarifa: el estudio se queda sin consumo
        ObjectNode vacio = json.createObjectNode();
        ObjectNode datos = vacio.putObject("datos");
        for (String k : new String[]{"cups", "tarifa", "localidad", "provincia", "cnae"}) datos.put(k, "");
        datos.putNull("sips");
        datos.putArray("potencias");
        vacio.putNull("curva");
        JsonNode b = cambiar(id, vacio.toString());
        assertEquals("", b.get("cups").asText());
        assertEquals("", b.get("datos").get("tarifa").asText());
        assertFalse(b.get("datos").has("potencias"));
        assertFalse(b.get("curva").get("presente").asBoolean());
        assertFalse(b.get("calculado").asBoolean());

        // Con la curva real y sin SIPS, la curva se queda
        ObjectNode soloCurva = vacio.deepCopy();
        soloCurva.set("curva", curva);
        JsonNode c = cambiar(id, soloCurva.toString());
        assertFalse(c.get("curva").get("estimada").asBoolean());
        assertTrue(c.get("calculado").asBoolean());
    }

    @Test
    void validaLosDatosYGuardaEstadoYObservaciones() throws Exception {
        JsonNode e = crear("Cliente estado");
        String id = e.get("id").asText();
        JsonNode r = cambiar(id, "{\"estado\":\"pte_doc\",\"observaciones\":\"Falta la factura de julio\"}");
        assertEquals("Pendiente de documentación del cliente", r.get("estadoNombre").asText());
        assertEquals("Falta la factura de julio", r.get("observaciones").asText());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"estado\":\"inventado\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"datos\":{\"instalacion\":{\"kwp\":-5}}}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"datos\":{\"economia\":{\"precioEnergia\":\"mucho\"}}}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"datos\":{\"horario\":{\"ini\":18,\"fin\":9}}}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(BASE + "/no-existe")).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void elInformeHtmlTieneSietePaginasYLasCifrasDelEstudio() throws Exception {
        String id = crearConConsumo("Aceitunas <Prueba> & Cía", sips(12), null).get("id").asText();
        cambiar(id, "{\"datos\":{\"instalacion\":{\"kwp\":120,\"bateriaKwh\":100,\"orientacion\":\"sur\"}}}");
        var res = mvc.perform(get(BASE + "/" + id + "/report.html")).andExpect(status().isOk()).andReturn().getResponse();
        String html = res.getContentAsString(StandardCharsets.UTF_8);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "estudio-pruebas"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target", "estudio-pruebas", "estudio.html"), html);
        assertEquals(7, html.split("<section class=\"pag\">", -1).length - 1);
        assertTrue(html.contains("120 kWp y 100 kWh de batería cubren el"));
        assertTrue(html.contains("Aceitunas &lt;Prueba&gt; &amp; Cía"), "el nombre del cliente va escapado");
        assertFalse(html.contains("<Prueba>"));
        assertTrue(html.contains("Curva estimada"));
        assertTrue(html.contains("Escenarios de potencia") && html.contains("<svg"));
        assertTrue(html.contains("data:image/png;base64,"), "lleva el logo de AURA");
        assertFalse(html.replaceAll("data:image/png;base64,[A-Za-z0-9+/=]+", "").contains("NaN"), "sin valores NaN");
    }

    @Test
    void elInformeEnPdfTieneSietePaginasOAvisaDeQueNoHayNavegador() throws Exception {
        String id = crearConConsumo("Cliente PDF", sips(12), null).get("id").asText();
        var res = mvc.perform(get(BASE + "/" + id + "/report.pdf")).andReturn().getResponse();
        if (res.getStatus() == 501) return; // sin Chrome en esta máquina
        assertEquals(200, res.getStatus(), res.getContentAsString());
        try (org.apache.pdfbox.pdmodel.PDDocument d = org.apache.pdfbox.Loader.loadPDF(res.getContentAsByteArray())) {
            assertEquals(7, d.getNumberOfPages());
            String texto = new org.apache.pdfbox.text.PDFTextStripper().getText(d);
            assertTrue(texto.contains("La inversión") && texto.contains("Escenarios de potencia"));
        }
    }
}
