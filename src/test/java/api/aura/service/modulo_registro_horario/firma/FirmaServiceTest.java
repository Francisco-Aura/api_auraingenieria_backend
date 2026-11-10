package api.aura.service.modulo_registro_horario.firma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La firma sacada de una foto se parece a la que saca el Registro Horario anterior (Pillow) con las mismas fotos
 * inventadas y la misma sensibilidad. Los PNG de referencia los generó el firma.py original.
 *
 * <p>Puede no ser idéntica píxel a píxel (otro decodificador de JPEG, otro orden de pasadas en algunos redimensionados),
 * así que el recorte debe medir lo mismo y la transparencia y el color de la tinta quedar casi iguales. Con las 40 fotos de
 * prueba el resultado coincidió píxel a píxel; la tolerancia es por si otro JDK decodifica el JPEG con otro redondeo.
 */
class FirmaServiceTest {

    private static final String RAIZ = "/registro-horario-oro/firma/";
    private final FirmaService servicio = new FirmaService(new TrabajosPesados());

    private static byte[] recurso(String nombre) throws IOException {
        try (InputStream in = FirmaServiceTest.class.getResourceAsStream(RAIZ + nombre)) {
            assertTrue(in != null, "Falta el recurso " + nombre);
            return in.readAllBytes();
        }
    }

    private static BufferedImage png(byte[] datos) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(datos));
    }

    /** Diferencia entre dos firmas del mismo tamaño: (nº de píxeles con alfa distinto en más de 16, mayor diferencia de alfa, diferencia de color de la tinta). */
    private static int[] diferencia(BufferedImage a, BufferedImage b) {
        int muchos = 0, maxAlfa = 0, maxColor = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int pa = a.getRGB(x, y), pb = b.getRGB(x, y);
                int da = Math.abs((pa >>> 24) - (pb >>> 24));
                maxAlfa = Math.max(maxAlfa, da);
                if (da > 16) {
                    muchos++;
                }
                if ((pa >>> 24) > 200 && (pb >>> 24) > 200) {
                    for (int s = 0; s <= 16; s += 8) {
                        maxColor = Math.max(maxColor, Math.abs(((pa >> s) & 0xFF) - ((pb >> s) & 0xFF)));
                    }
                }
            }
        }
        return new int[]{muchos, maxAlfa, maxColor};
    }

    @Test
    void lasFotosDanUnaFirmaParecidaALaDelOriginal() throws IOException {
        JsonNode resumen = new ObjectMapper().readTree(recurso("resumen-fotos.json"));
        List<String> informe = new ArrayList<>();
        int peorMuchos = 0, peorAlfa = 0, peorColor = 0;
        int comparadas = 0;
        for (var it = resumen.fields(); it.hasNext(); ) {
            var e = it.next();
            String clave = e.getKey();
            String foto = clave.substring(0, clave.lastIndexOf("_s"));
            String resto = clave.substring(clave.lastIndexOf("_s") + 2); // 50_v
            double sens = Integer.parseInt(resto.substring(0, resto.indexOf('_'))) / 100.0;
            boolean vista = resto.endsWith("_v");
            byte[] jpg = recurso(foto + ".jpg");
            if (!e.getValue().get("ok").asBoolean()) {
                FirmaError error = assertThrows(FirmaError.class, () -> servicio.extraer(jpg, sens, vista), clave);
                assertEquals(e.getValue().get("error").asText(), error.getMessage(), clave);
                continue;
            }
            BufferedImage esperada = png(recurso(clave + ".png"));
            BufferedImage obtenida = png(servicio.extraer(jpg, sens, vista));
            assertEquals(esperada.getWidth(), obtenida.getWidth(), clave + ": ancho del recorte");
            assertEquals(esperada.getHeight(), obtenida.getHeight(), clave + ": alto del recorte");
            int[] d = diferencia(esperada, obtenida);
            peorMuchos = Math.max(peorMuchos, d[0]);
            peorAlfa = Math.max(peorAlfa, d[1]);
            peorColor = Math.max(peorColor, d[2]);
            comparadas++;
            informe.add(String.format("%s %dx%d: píxeles con alfa distinto >16: %d, dif. máx. alfa %d, dif. máx. color %d",
                    clave, esperada.getWidth(), esperada.getHeight(), d[0], d[1], d[2]));
        }
        assertEquals(40, comparadas);
        assertTrue(peorMuchos <= 50, "Píxeles con alfa muy distinto (peor foto): " + peorMuchos);
        assertTrue(peorAlfa <= 64, "Mayor diferencia de alfa: " + peorAlfa);
        assertTrue(peorColor <= 8, "Mayor diferencia de color: " + peorColor);
    }

    @Test
    void unPngYaTransparenteSoloSeRecorta() throws IOException {
        BufferedImage esperada = png(recurso("transparente_salida.png"));
        BufferedImage obtenida = png(servicio.extraer(recurso("transparente.png"), 0.5, false));
        assertEquals(esperada.getWidth(), obtenida.getWidth());
        assertEquals(esperada.getHeight(), obtenida.getHeight());
        for (int y = 0; y < esperada.getHeight(); y++) {
            for (int x = 0; x < esperada.getWidth(); x++) {
                assertEquals(esperada.getRGB(x, y), obtenida.getRGB(x, y), "píxel " + x + "," + y);
            }
        }
    }

    @Test
    void archivoInvalidoOEnormeSeRechazaConElMensajeDelOriginal() {
        FirmaError e = assertThrows(FirmaError.class, () -> servicio.extraer("basura".getBytes(), 0.5, false));
        assertEquals("No se pudo leer la imagen. Usa un PNG o un JPG.", e.getMessage());
        e = assertThrows(FirmaError.class, () -> servicio.extraer(new byte[9 * 1024 * 1024], 0.5, false));
        assertEquals("La imagen pesa demasiado (máximo 8 MB).", e.getMessage());
        assertEquals(422, e.getEstado().value());
    }

    @Test
    void papelEnBlancoNoEsUnaFirma() throws IOException {
        BufferedImage papel = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 400; y++) {
            for (int x = 0; x < 600; x++) {
                papel.setRGB(x, y, 0xEBEBEB);
            }
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(papel, "png", salida);
        FirmaError e = assertThrows(FirmaError.class, () -> servicio.extraer(salida.toByteArray(), 0.5, false));
        assertEquals("No se detecta ninguna firma. Sube la sensibilidad o repite la foto con más luz.", e.getMessage());
    }

    @Test
    void comprobarPngRechazaLaFirmaVaciaYAceptaLaBuena() throws IOException {
        BufferedImage vacia = new BufferedImage(50, 20, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(vacia, "png", salida);
        FirmaError e = assertThrows(FirmaError.class, () -> servicio.comprobarPng(salida.toByteArray()));
        assertEquals("La imagen de la firma está vacía.", e.getMessage());
        assertEquals(300, servicio.comprobarPng(recurso("transparente.png")).ancho);
    }

    @Test
    void unaFotoDeMovilGuardadaDeLadoSeEnderezaComoEnElOriginal() throws IOException {
        byte[] jpg = recurso("foto5_exif6.jpg");
        assertEquals(6, OrientacionExif.leer(jpg));
        BufferedImage esperada = png(recurso("foto5_exif6_s50_f.png"));
        BufferedImage obtenida = png(servicio.extraer(jpg, 0.5, false));
        assertEquals(esperada.getWidth(), obtenida.getWidth());
        assertEquals(esperada.getHeight(), obtenida.getHeight());
        assertTrue(diferencia(esperada, obtenida)[0] <= 50);
    }

    @Test
    void laOrientacionDeLaFotoDelMovilSeCorrige() {
        Imagen original = new Imagen(3, 2, new int[]{1, 2, 3, 4, 5, 6}); // 3 de ancho, 2 de alto
        assertEquals(List.of(4, 1, 5, 2, 6, 3), aLista(OrientacionExif.corregir(original, 6).argb)); // 90° a la derecha
        assertEquals(2, OrientacionExif.corregir(original, 6).ancho);
        assertEquals(List.of(3, 6, 2, 5, 1, 4), aLista(OrientacionExif.corregir(original, 8).argb)); // 90° a la izquierda
        assertEquals(List.of(6, 5, 4, 3, 2, 1), aLista(OrientacionExif.corregir(original, 3).argb));
        assertEquals(List.of(3, 2, 1, 6, 5, 4), aLista(OrientacionExif.corregir(original, 2).argb));
    }

    private static List<Integer> aLista(int[] v) {
        List<Integer> l = new ArrayList<>();
        for (int i : v) {
            l.add(i);
        }
        return l;
    }
}
