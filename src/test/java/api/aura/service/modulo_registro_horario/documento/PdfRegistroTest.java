package api.aura.service.modulo_registro_horario.documento;

import api.aura.service.modulo_registro_horario.firma.FirmaService;
import api.aura.service.modulo_registro_horario.firma.TrabajosPesados;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El PDF dibujado en Java es el del Registro Horario anterior (ReportLab) con los mismos datos inventados: una página A4,
 * el mismo texto en las mismas posiciones y el mismo aspecto (se pintan los dos con el mismo programa y se comparan).
 */
class PdfRegistroTest {

    private final MotorWord motor = new MotorWord(new FirmaService(new TrabajosPesados()));

    /** Un carácter con su posición (x, y de la línea base) y tamaño. */
    private record Glifo(String c, float x, float y, float tam) {
    }

    private static List<Glifo> glifos(PDDocument pdf) throws IOException {
        List<Glifo> r = new ArrayList<>();
        PDFTextStripper t = new PDFTextStripper() {
            @Override
            protected void writeString(String texto, List<TextPosition> posiciones) {
                for (TextPosition p : posiciones) {
                    r.add(new Glifo(p.getUnicode(), p.getXDirAdj(), p.getYDirAdj(), p.getFontSizeInPt()));
                }
            }
        };
        t.setSortByPosition(false);
        t.getText(pdf);
        return r;
    }

    private static boolean hayTintaCerca(BufferedImage im, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && yy >= 0 && xx < im.getWidth() && yy < im.getHeight() && (im.getRGB(xx, yy) & 0xFF) < 160) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Fracción de píxeles oscuros de una página que no tienen ninguno cerca (a 1 píxel) en la otra, pintadas a 100 ppp.
     * Así un trazo que cae medio píxel más arriba o abajo no cuenta como diferencia, pero sí un texto o una firma que falten.
     */
    private static double fraccionDistinta(PDDocument a, PDDocument b) throws IOException {
        BufferedImage ia = new PDFRenderer(a).renderImageWithDPI(0, 100), ib = new PDFRenderer(b).renderImageWithDPI(0, 100);
        assertEquals(ia.getWidth(), ib.getWidth());
        assertEquals(ia.getHeight(), ib.getHeight());
        long distintos = 0, tinta = 0;
        for (int y = 0; y < ia.getHeight(); y++) {
            for (int x = 0; x < ia.getWidth(); x++) {
                boolean oa = (ia.getRGB(x, y) & 0xFF) < 160, ob = (ib.getRGB(x, y) & 0xFF) < 160;
                if (oa || ob) {
                    tinta++;
                }
                if ((oa && !hayTintaCerca(ib, x, y)) || (ob && !hayTintaCerca(ia, x, y))) {
                    distintos++;
                }
            }
        }
        return tinta == 0 ? 0 : (double) distintos / tinta;
    }

    @Test
    void cadaPdfEsElDelOriginal() throws IOException {
        JsonNode todo = MotorWordTest.casos();
        int comparados = 0;
        for (JsonNode caso : todo.get("casos")) {
            if (caso.has("error")) {
                continue;
            }
            String nombre = caso.get("nombre").asText();
            JsonNode p = caso.get("peticion");
            MotorWord.Resultado r = motor.construir(MotorWordTest.datos(p, todo.get("empresas")), p.get("anio").asInt(),
                    p.get("mes").asInt(), Base64.getDecoder().decode(p.get("firma_png_base64").asText()));
            byte[] obtenido = PdfRegistro.generar(r.documento());
            try (PDDocument pdf = Loader.loadPDF(obtenido);
                 PDDocument esperado = Loader.loadPDF(FirmaParaDocumentoTest.recurso("../pdf/" + nombre + ".pdf"))) {
                assertEquals(1, pdf.getNumberOfPages(), nombre + ": páginas");
                assertEquals(esperado.getPage(0).getMediaBox().getWidth(), pdf.getPage(0).getMediaBox().getWidth(), 0.01, nombre);
                assertEquals(esperado.getPage(0).getMediaBox().getHeight(), pdf.getPage(0).getMediaBox().getHeight(), 0.01, nombre);
                assertEquals("Registro de jornada", pdf.getDocumentInformation().getTitle());

                List<Glifo> ge = glifos(esperado), go = glifos(pdf);
                assertEquals(ge.size(), go.size(), nombre + ": nº de caracteres");
                for (int i = 0; i < ge.size(); i++) {
                    Glifo e = ge.get(i), o = go.get(i);
                    assertEquals(e.c(), o.c(), nombre + ": carácter " + i);
                    assertEquals(e.x(), o.x(), 0.05, nombre + ": x de «" + e.c() + "» (" + i + ")");
                    assertEquals(e.y(), o.y(), 0.05, nombre + ": y de «" + e.c() + "» (" + i + ")");
                    assertEquals(e.tam(), o.tam(), 0.01, nombre + ": tamaño de «" + e.c() + "»");
                }
                double distinta = fraccionDistinta(esperado, pdf);
                assertTrue(distinta < 0.002, nombre + ": " + distinta * 100 + " % del dibujo es distinto");
            }
            comparados++;
        }
        assertEquals(9, comparados);
    }

    @Test
    void siElContenidoNoCabeEnUnaPaginaSeAvisa() throws IOException {
        // la hoja pierde altura si la plantilla crece: se simula con un margen inferior enorme
        JsonNode todo = MotorWordTest.casos();
        JsonNode caso = todo.get("casos").get(0);
        JsonNode p = caso.get("peticion");
        MotorWord.Resultado r = motor.construir(MotorWordTest.datos(p, todo.get("empresas")), p.get("anio").asInt(),
                p.get("mes").asInt(), Base64.getDecoder().decode(p.get("firma_png_base64").asText()));
        var sp = XmlDocx.descendientesW(r.documento().cuerpo(), "sectPr").get(0);
        XmlDocx.setW(XmlDocx.hijoW(sp, "pgMar"), "bottom", "6000");
        DatosNoValidosException e = assertThrows(DatosNoValidosException.class, () -> PdfRegistro.generar(r.documento()));
        assertEquals("el contenido no cabe en una sola página", e.getMessage());
    }

    @Test
    void losCaracteresQueHelveticaNoTieneSeSustituyenPorInterrogante() throws IOException {
        JsonNode todo = MotorWordTest.casos();
        JsonNode p = todo.get("casos").get(0).get("peticion");
        var datos = MotorWordTest.datos(p, todo.get("empresas"));
        var chino = new api.aura.service.modulo_registro_horario.documento.DatosRegistro("李 小龙 Ñandú", datos.nif(),
                datos.numAfiliacion(), datos.empresa(), datos.cif(), datos.ccc(), datos.centroTrabajo(), datos.localidad(),
                datos.horarioManana(), datos.horarioTarde(), datos.estadoDias(), datos.horasDia(), datos.fechaFirma());
        MotorWord.Resultado r = motor.construir(chino, p.get("anio").asInt(), p.get("mes").asInt(),
                Base64.getDecoder().decode(p.get("firma_png_base64").asText()));
        try (PDDocument pdf = Loader.loadPDF(PdfRegistro.generar(r.documento()))) {
            String texto = new PDFTextStripper().getText(pdf);
            assertTrue(texto.contains("? ?? Ñandú"), texto);
        }
    }
}
