package api.aura.service.modulo_planificador.contrato;

import api.aura.service.modulo_planificador.ContratoProyectoService;
import api.aura.service.modulo_planificador.PresupuestoProyectoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El contrato en PDF: el Word interno sale idéntico al que genera el Planificador actual (se compara con ejemplos guardados, generados
 * con el código Python original) y el PDF se dibuja con el texto esperado.
 */
class ContratoPdfTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ContratoRelleno relleno = new ContratoRelleno(new ContratoProyectoService(new PresupuestoProyectoService()));
    private final ContratoPdf pdf = new ContratoPdf();
    private static final LocalDate HOY = LocalDate.of(2026, 11, 5);

    private String recurso(String nombre) throws IOException {
        try (var in = getClass().getResourceAsStream("/contrato-dorado/" + nombre)) {
            assertNotNull(in, nombre);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, Object> datos(String caso) throws IOException {
        return json.readValue(recurso(caso + ".json"), new TypeReference<>() {
        });
    }

    private static String documentoXml(byte[] docx) throws IOException {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(docx))) {
            var e = z.getNextEntry();
            while (e != null) {
                if (e.getName().equals("word/document.xml")) {
                    return new String(z.readAllBytes(), StandardCharsets.UTF_8);
                }
                e = z.getNextEntry();
            }
        }
        throw new AssertionError("sin document.xml");
    }

    private static String textoDe(byte[] bytes) throws IOException {
        PdfReader lector = new PdfReader(bytes);
        PdfTextExtractor extractor = new PdfTextExtractor(lector);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lector.getNumberOfPages(); i++) {
            sb.append(extractor.getTextFromPage(i)).append("\n--página ").append(i).append("--\n");
        }
        return sb.toString();
    }

    private void guardar(String nombre, byte[] bytes) throws IOException {
        Path carpeta = Path.of("target", "contrato-pruebas");
        Files.createDirectories(carpeta);
        Files.write(carpeta.resolve(nombre + ".pdf"), bytes);
    }

    // ---------------------------------------------------------------------------- Word interno

    @Test
    void elWordRellenoEsIgualAlDelPlanificadorActualConLaFormaDePagoHabitual() throws Exception {
        assertEquals(recurso("habitual.xml"), documentoXml(relleno.generar(datos("habitual"), HOY)));
    }

    @Test
    void elWordRellenoEsIgualAlDelPlanificadorActualConPagosPersonalizados() throws Exception {
        assertEquals(recurso("personalizada.xml"), documentoXml(relleno.generar(datos("personalizada"), HOY)));
    }

    @Test
    void elWordRellenoEsIgualAlDelPlanificadorActualConUnSoloPago() throws Exception {
        assertEquals(recurso("unico.xml"), documentoXml(relleno.generar(datos("unico"), HOY)));
    }

    @Test
    void sinDiaNiMesLaFirmaLlevaLaFechaDeHoyYElLugarPorDefecto() throws Exception {
        Map<String, Object> d = new java.util.HashMap<>(datos("habitual"));
        d.remove("dia");
        d.remove("mes");
        d.remove("lugar_firma");
        d.remove("anio");
        String xml = documentoXml(relleno.generar(d, LocalDate.of(2031, 3, 9)));
        assertTrue(xml.contains("Lorca"));
        assertTrue(xml.contains("marzo"));
        assertTrue(xml.contains(" 2031"));
    }

    // -------------------------------------------------------------------------------------- números

    @Test
    void losImportesSeEscribenEnLetrasYEnCifrasComoEnElModelo() {
        assertEquals("Treinta y ocho mil setecientos Euros", ContratoRelleno.eurosEnLetras(38700));
        assertEquals("Veintiún mil Euros", ContratoRelleno.eurosEnLetras(21000));
        assertEquals("Treinta y un mil quinientos Euros con cincuenta céntimos", ContratoRelleno.eurosEnLetras(31500.5));
        assertEquals("Un millón doscientos mil Euros", ContratoRelleno.eurosEnLetras(1_200_000));
        assertEquals("Cien Euros", ContratoRelleno.eurosEnLetras(100));
        assertEquals("Ciento uno Euros", ContratoRelleno.eurosEnLetras(101));
        assertEquals("Mil Euros", ContratoRelleno.eurosEnLetras(1000));
        assertEquals("cero", ContratoRelleno.numeroEnLetras(0));
        assertEquals("1.234.567,89", ContratoRelleno.formatoEs(1234567.891));
        assertEquals("11.704,00", ContratoRelleno.formatoEs(11704));
        assertEquals("0,50", ContratoRelleno.formatoEs(0.5));
    }

    @Test
    void sinTodosLosDatosObligatoriosNoSeGeneraYSeDiceCualesFaltan() throws Exception {
        assertEquals(15, ContratoRelleno.camposQueFaltan(Map.of()).size());
        assertEquals(List.of(), ContratoRelleno.camposQueFaltan(datos("habitual")).stream().filter(x -> !x.isEmpty()).toList());
        Map<String, Object> d = new java.util.HashMap<>(datos("habitual"));
        d.put("cif", "  ");
        d.remove("estructura");
        assertEquals(List.of("CIF", "Estructura"), ContratoRelleno.camposQueFaltan(d));
    }

    // ------------------------------------------------------------------------------------------ PDF

    @Test
    void elPdfDelContratoTieneElTextoLasFirmasYElAnexo() throws Exception {
        byte[] bytes = pdf.convertir(relleno.generar(datos("habitual"), HOY));
        guardar("habitual", bytes);
        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.ISO_8859_1));
        String texto = textoDe(bytes);
        assertTrue(texto.contains("BONAQUIA, S.L"), "el promotor");
        assertTrue(texto.contains("38.700,00"), "el importe");
        assertTrue(texto.contains("Treinta y ocho mil setecientos Euros"), "el importe en letras");
        assertTrue(texto.contains("11.610,00"), "el 30 % de la firma");
        assertTrue(texto.contains("ANEXO 1"), "el anexo");
        assertTrue(texto.contains("JA Solar 550"), "los equipos del anexo");
        assertTrue(texto.contains("Energía de futuro"), "el pie de página");
        assertTrue(texto.contains("Juan Perez"), "la firma del promotor");
        assertTrue(new PdfReader(bytes).getNumberOfPages() >= 4, "un contrato de varias páginas");
    }

    @Test
    void elPdfConPagosPersonalizadosLlevaUnoPorCadaPago() throws Exception {
        byte[] bytes = pdf.convertir(relleno.generar(datos("personalizada"), HOY));
        guardar("personalizada", bytes);
        String texto = textoDe(bytes);
        assertTrue(texto.contains("Primer Pago") && texto.contains("Segundo Pago") && texto.contains("Tercer Pago"));
        assertFalse(texto.contains("Cuarto Pago"));
        assertTrue(texto.contains("10.500,25"), "el 50 % de 21.000,50");
        assertTrue(texto.contains("en concepto de avance de obra"), "el concepto escrito");
        assertTrue(texto.contains("BYD 10kWh"), "la batería en el anexo");
    }

    @Test
    void elPdfConUnSoloPagoLoDiceEnSingular() throws Exception {
        byte[] bytes = pdf.convertir(relleno.generar(datos("unico"), HOY));
        guardar("unico", bytes);
        String texto = textoDe(bytes);
        assertTrue(texto.contains("Pago único"));
        assertTrue(texto.contains("Se entrega factura del pago."));
        assertFalse(texto.contains("Segundo Pago"));
    }

    @Test
    void unDocumentoQueNoEsUnWordSeRechazaConUnMensajeClaro() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> pdf.convertir("no es un docx".getBytes()));
        assertTrue(e.getMessage().startsWith("No se pudo generar el PDF del contrato"));
    }
}
