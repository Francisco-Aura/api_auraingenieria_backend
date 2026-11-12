package api.aura.service.modulo_registro_horario.documento;

import api.aura.service.modulo_registro_horario.firma.FirmaService;
import api.aura.service.modulo_registro_horario.firma.Imagen;
import api.aura.service.modulo_registro_horario.firma.Plano;
import api.aura.service.modulo_registro_horario.firma.Plano.Filtro;

/**
 * Prepara la imagen de la firma para ponerla en el documento: recortada y, si no tiene transparencia, con el blanco
 * convertido en transparente; a su tamaño final (400 ppp) y con el trazo reforzado para que se lea aunque salga pequeña.
 * Mismo procesado que {@code preparar_firma} y {@code _reforzar_trazo} del Registro Horario anterior.
 */
final class FirmaParaDocumento {

    /** PNG listo para insertar y su proporción ancho/alto. */
    record Preparada(byte[] png, int ancho, int alto) {
        double aspecto() {
            return (double) ancho / alto;
        }
    }

    /**
     * Tope del objetivo al engrosar una firma, en mm. El trazo final sale algo más grueso (el radio se redondea hacia
     * arriba y luego se refuerza la opacidad): con 0,28 queda en torno a 0,35 mm, como un bolígrafo normal.
     */
    private static final double GROSOR_MAX_MM = 0.28;

    private FirmaParaDocumento() {
    }

    /** Con {@code altoCm} se deja a su tamaño final y con el trazo reforzado (para las firmas de cada día y la del pie). */
    static Preparada preparar(Imagen original, boolean quitarFondo, Double altoCm) {
        Plano r = original.canal(0), g = original.canal(1), b = original.canal(2), a = original.canal(3);
        int minimoAlfa = 255;
        for (byte v : a.bytes()) {
            minimoAlfa = Math.min(minimoAlfa, v & 0xFF);
        }
        if (quitarFondo && minimoAlfa == 255) {
            Plano gris = Imagen.aGris(r, g, b);
            // 255 (papel) -> transparente; tinta oscura -> opaca; degradado para suavizar bordes
            a = gris.punto(Plano.tabla(v -> Math.max(0, Math.min(255, Math.floorDiv((245 - v) * 255, 90)))));
        }
        int[] caja = a.caja();
        if (caja != null) {
            r = r.recortar(caja[0], caja[1], caja[2], caja[3]);
            g = g.recortar(caja[0], caja[1], caja[2], caja[3]);
            b = b.recortar(caja[0], caja[1], caja[2], caja[3]);
            a = a.recortar(caja[0], caja[1], caja[2], caja[3]);
        }
        if (altoCm != null) {
            return reforzarTrazo(r, g, b, a, altoCm);
        }
        return codificar(r, g, b, a);
    }

    private static Preparada codificar(Plano r, Plano g, Plano b, Plano a) {
        int[] argb = new int[r.ancho * r.alto];
        for (int i = 0; i < argb.length; i++) {
            argb[i] = ((a.bytes()[i] & 0xFF) << 24) | ((r.bytes()[i] & 0xFF) << 16) | ((g.bytes()[i] & 0xFF) << 8)
                    | (b.bytes()[i] & 0xFF);
        }
        return new Preparada(FirmaService.png(r.ancho, r.alto, argb), r.ancho, r.alto);
    }

    /**
     * Reduce la firma a su tamaño final (400 ppp) y engrosa y oscurece el trazo. Una firma grande reducida a 0,5 cm
     * pierde los trazos finos y sale pálida; así queda legible.
     */
    private static Preparada reforzarTrazo(Plano r, Plano g, Plano b, Plano a, double altoCm) {
        int w = r.ancho, h = r.alto;
        int px = Math.max(48, (int) Math.rint(altoCm / 2.54 * 400));
        int nuevoAncho = Math.max(1, (int) Math.rint((double) (w * px) / h));
        if (nuevoAncho != w || px != h) {
            // Pillow reduce las imágenes con transparencia sobre colores premultiplicados por el alfa
            Plano[] pre = premultiplicar(r, g, b, a);
            r = pre[0].redimensionar(nuevoAncho, px, Filtro.LANCZOS);
            g = pre[1].redimensionar(nuevoAncho, px, Filtro.LANCZOS);
            b = pre[2].redimensionar(nuevoAncho, px, Filtro.LANCZOS);
            a = a.redimensionar(nuevoAncho, px, Filtro.LANCZOS);
            Plano[] normal = desmultiplicar(r, g, b, a);
            r = normal[0];
            g = normal[1];
            b = normal[2];
        }
        Plano mascara = a.punto(Plano.tabla(v -> v > 128 ? 255 : 0)); // color medio de la tinta
        long n = 0, sr = 0, sg = 0, sb = 0;
        for (int i = 0; i < mascara.bytes().length; i++) {
            if (mascara.bytes()[i] != 0) {
                n++;
                sr += r.bytes()[i] & 0xFF;
                sg += g.bytes()[i] & 0xFF;
                sb += b.bytes()[i] & 0xFF;
            }
        }
        double mr = n == 0 ? 0 : (double) sr / n, mg = n == 0 ? 0 : (double) sg / n, mb = n == 0 ? 0 : (double) sb / n;
        double brillo = 0.299 * mr + 0.587 * mg + 0.114 * mb;
        double factor = brillo != 0 ? Math.min(1.0, 90 / brillo) : 1.0; // solo se oscurece si sale demasiado claro para firmar
        int tr = (int) (mr * factor), tg = (int) (mg * factor), tb = (int) (mb * factor);

        // Grosor real del trazo (≈ 2·área / perímetro). Solo se engrosa si es más fino que ~2,5 % de la altura: una firma
        // ya gruesa se estropearía (los detalles se vuelven manchas), una de líneas finas se perdería.
        Plano solida = a.punto(Plano.tabla(v -> v > 128 ? 255 : 0));
        long area = solida.histograma(null)[255];
        long perimetro = solida.restar(solida.minimos(3)).histograma(null)[255];
        double grosor = perimetro != 0 ? 2.0 * area / perimetro : 0;
        // En una firma pequeña (la de cada día, ~0,5 cm) hace falta engrosar para que se lea; en la grande del pie (2 cm)
        // el mismo porcentaje daría un trazo de rotulador, así que el objetivo no pasa de GROSOR_MAX_MM.
        double objetivo = Math.min(0.025 * px, GROSOR_MAX_MM / 25.4 * 400);
        int radio = grosor >= objetivo ? 0
                : Math.min(Math.max(1, (int) Math.rint(px / 55.0)), Math.max(1, (int) Math.ceil((objetivo - grosor) / 2)));
        if (radio > 0) {
            a = a.expandir(radio, 0);         // sitio para el trazo engrosado
            a = a.maximos(2 * radio + 1);     // engrosar
        }
        a = a.punto(Plano.tabla(v -> Math.min(255, (int) (v * 1.5)))); // menos transparente
        Plano rr = new Plano(a.ancho, a.alto), gg = new Plano(a.ancho, a.alto), bb = new Plano(a.ancho, a.alto);
        java.util.Arrays.fill(rr.bytes(), (byte) tr);
        java.util.Arrays.fill(gg.bytes(), (byte) tg);
        java.util.Arrays.fill(bb.bytes(), (byte) tb);
        return codificar(rr, gg, bb, a);
    }

    /** MULDIV255 de Pillow: a·b/255 con redondeo. */
    private static int mul255(int a, int b) {
        int t = a * b + 128;
        return ((t >> 8) + t) >> 8;
    }

    private static Plano[] premultiplicar(Plano r, Plano g, Plano b, Plano a) {
        Plano[] s = {new Plano(r.ancho, r.alto), new Plano(r.ancho, r.alto), new Plano(r.ancho, r.alto)};
        Plano[] e = {r, g, b};
        for (int i = 0; i < a.bytes().length; i++) {
            int alfa = a.bytes()[i] & 0xFF;
            for (int c = 0; c < 3; c++) {
                s[c].bytes()[i] = (byte) mul255(e[c].bytes()[i] & 0xFF, alfa);
            }
        }
        return s;
    }

    private static Plano[] desmultiplicar(Plano r, Plano g, Plano b, Plano a) {
        Plano[] s = {new Plano(r.ancho, r.alto), new Plano(r.ancho, r.alto), new Plano(r.ancho, r.alto)};
        Plano[] e = {r, g, b};
        for (int i = 0; i < a.bytes().length; i++) {
            int alfa = a.bytes()[i] & 0xFF;
            for (int c = 0; c < 3; c++) {
                int v = e[c].bytes()[i] & 0xFF;
                s[c].bytes()[i] = (byte) (alfa == 255 || alfa == 0 ? v : Math.min(255, (255 * v) / alfa));
            }
        }
        return s;
    }
}
