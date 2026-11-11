package api.aura.service.modulo_registro_horario.firma;

/**
 * Imagen en color con transparencia, decodificada y con la orientación ya corregida: un entero ARGB por píxel (sin
 * premultiplicar). Es lo que Pillow llamaba «RGBA».
 */
public final class Imagen {

    public final int ancho;
    public final int alto;
    public final int[] argb;

    public Imagen(int ancho, int alto, int[] argb) {
        this.ancho = ancho;
        this.alto = alto;
        this.argb = argb;
    }

    /** Un canal (0 = rojo, 1 = verde, 2 = azul, 3 = alfa). */
    public Plano canal(int canal) {
        int desplazamiento = switch (canal) {
            case 0 -> 16;
            case 1 -> 8;
            case 2 -> 0;
            default -> 24;
        };
        Plano p = new Plano(ancho, alto);
        for (int i = 0; i < argb.length; i++) {
            p.datos[i] = (byte) (argb[i] >> desplazamiento);
        }
        return p;
    }

    /** Gris como Pillow: L = (R·19595 + G·38470 + B·7471 + 0x8000) >> 16. */
    public static Plano aGris(Plano r, Plano g, Plano b) {
        Plano p = new Plano(r.ancho, r.alto);
        for (int i = 0; i < p.datos.length; i++) {
            p.datos[i] = (byte) (((r.datos[i] & 0xFF) * 19595 + (g.datos[i] & 0xFF) * 38470 + (b.datos[i] & 0xFF) * 7471
                    + 0x8000) >> 16);
        }
        return p;
    }

    /** Gris de bytes RGB intercalados. */
    public static Plano aGris(byte[] rgb, int ancho, int alto) {
        Plano p = new Plano(ancho, alto);
        for (int i = 0; i < ancho * alto; i++) {
            p.datos[i] = (byte) (((rgb[i * 3] & 0xFF) * 19595 + (rgb[i * 3 + 1] & 0xFF) * 38470
                    + (rgb[i * 3 + 2] & 0xFF) * 7471 + 0x8000) >> 16);
        }
        return p;
    }
}
