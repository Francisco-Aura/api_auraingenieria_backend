package api.aura.service.modulo_registro_horario.firma;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Firma a partir de la imagen que sube la persona: valida, y separa la tinta del papel si es una foto o un escaneo.
 * Mismo criterio que el servicio anterior. Todo ocurre en memoria: no se escribe nada en disco ni en el log.
 */
@Service
public class FirmaService {

    public static final int MAX_BYTES = 8 * 1024 * 1024;        // peso máximo de la subida
    public static final long MAX_PIXELES = 25_000_000L;         // una foto de móvil actual ronda los 12-50 MP; por encima no se decodifica
    public static final int LADO_VISTA = 640, LADO_FINAL = 1400; // vista previa ligera y resultado a buena resolución

    static final String NO_SE_PUEDE_LEER = "No se pudo leer la imagen. Usa un PNG o un JPG.";

    private final TrabajosPesados trabajos;

    public FirmaService(TrabajosPesados trabajos) {
        this.trabajos = trabajos;
    }

    /** PNG con solo la firma de la foto subida (fondo transparente, recortada). Espera turno si ya hay dos trabajos pesados. */
    public byte[] extraerPng(byte[] datos, double sensibilidad, boolean vista) {
        return trabajos.ejecutar(() -> extraer(datos, sensibilidad, vista));
    }

    /**
     * PNG con solo la firma (fondo transparente, recortada). Si la imagen ya trae el fondo transparente solo se recorta.
     * Si es una foto, se separa la tinta del papel; {@code vista} usa la versión ligera (640 px) para la vista previa.
     */
    byte[] extraer(byte[] datos, double sensibilidad, boolean vista) {
        Imagen img = abrir(datos);
        int[] argb = img.argb;
        long transparentes = 0;
        for (int p : argb) {
            if ((p >>> 24) < 128) {
                transparentes++;
            }
        }
        if (transparentes >= 0.02 * img.ancho * img.alto) {
            int[] caja = img.canal(3).punto(Plano.tabla(v -> v > 10 ? 255 : 0)).caja();
            return caja == null ? png(img.ancho, img.alto, argb) : png(recortar(img, caja));
        }
        ExtractorFirma.Resultado r = new ExtractorFirma(img, vista ? LADO_VISTA : LADO_FINAL).resultado(sensibilidad);
        if (r == null) {
            throw new FirmaError("No se detecta ninguna firma. Sube la sensibilidad o repite la foto con más luz.");
        }
        return png(r.ancho(), r.alto(), r.argb());
    }

    /** La firma que llega al generar el documento: debe ser una imagen legible y de tamaño razonable. */
    public Imagen comprobarPng(byte[] datos) {
        Imagen img = abrir(datos);
        // como Pillow: con transparencia manda el canal alfa (todo transparente = vacía); sin ella, que haya algún píxel no negro
        boolean vacia = tieneAlfa(img) ? img.canal(3).caja() == null : !hayColor(img);
        if (vacia) {
            throw new FirmaError("La imagen de la firma está vacía.");
        }
        return img;
    }

    private static boolean tieneAlfa(Imagen img) {
        for (int p : img.argb) {
            if ((p >>> 24) != 255) {
                return true;
            }
        }
        return false;
    }

    private static boolean hayColor(Imagen img) {
        for (int p : img.argb) {
            if ((p & 0xFFFFFF) != 0) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- abrir

    /** Imagen decodificada y con la orientación corregida. Rechaza lo que pese o mida demasiado. */
    public Imagen abrir(byte[] datos) {
        return OrientacionExif.corregir(decodificar(datos), OrientacionExif.leer(datos));
    }

    /** Imagen decodificada tal cual (sin corregir la orientación). Rechaza lo que pese o mida demasiado. */
    public Imagen decodificar(byte[] datos) {
        if (datos.length > MAX_BYTES) {
            throw new FirmaError("La imagen pesa demasiado (máximo " + MAX_BYTES / (1024 * 1024) + " MB).");
        }
        BufferedImage leida;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            Iterator<ImageReader> lectores = in == null ? null : ImageIO.getImageReaders(in);
            if (lectores == null || !lectores.hasNext()) {
                throw new FirmaError(NO_SE_PUEDE_LEER);
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(in, true, true);
                if ((long) lector.getWidth(0) * lector.getHeight(0) > MAX_PIXELES) {
                    throw new FirmaError("La imagen es demasiado grande. Reduce su tamaño y vuelve a intentarlo.");
                }
                leida = lector.read(0);
            } finally {
                lector.dispose();
            }
        } catch (FirmaError e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new FirmaError(NO_SE_PUEDE_LEER);
        }
        if (leida == null) {
            throw new FirmaError(NO_SE_PUEDE_LEER);
        }
        return aImagen(leida);
    }

    /** ARGB sin premultiplicar; los grises se copian tal cual (sin la conversión de gamma de Java). */
    private static Imagen aImagen(BufferedImage b) {
        int w = b.getWidth(), h = b.getHeight();
        ColorModel cm = b.getColorModel();
        int[] argb = new int[w * h];
        if (cm.getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY) {
            Raster r = b.getRaster();
            int bandas = r.getNumBands();
            int bits = cm.getComponentSize(0);
            int[] muestra = new int[bandas];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    r.getPixel(x, y, muestra);
                    int g = bits > 8 ? muestra[0] >> (bits - 8) : muestra[0];
                    int a = bandas > 1 ? (bits > 8 ? muestra[1] >> (bits - 8) : muestra[1]) : 255;
                    argb[y * w + x] = (a << 24) | (g << 16) | (g << 8) | g;
                }
            }
        } else {
            b.getRGB(0, 0, w, h, argb, 0, w);
        }
        return new Imagen(w, h, argb);
    }

    // ---------------------------------------------------------------- salida

    private static Imagen recortar(Imagen img, int[] caja) {
        int w = caja[2] - caja[0], h = caja[3] - caja[1];
        int[] r = new int[w * h];
        for (int y = 0; y < h; y++) {
            System.arraycopy(img.argb, (y + caja[1]) * img.ancho + caja[0], r, y * w, w);
        }
        return new Imagen(w, h, r);
    }

    public static byte[] png(Imagen img) {
        return png(img.ancho, img.alto, img.argb);
    }

    public static byte[] png(int ancho, int alto, int[] argb) {
        BufferedImage b = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        b.setRGB(0, 0, ancho, alto, argb, 0, ancho);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            ImageIO.write(b, "png", salida);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo codificar el PNG", e);
        }
        return salida.toByteArray();
    }
}
