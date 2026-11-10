package api.aura.service.modulo_registro_horario.firma;

/**
 * Orientación de una foto de móvil: los móviles guardan las fotos «de lado» con una etiqueta EXIF (en los JPEG). Pillow la
 * aplica con {@code exif_transpose}; aquí se lee del segmento APP1 y se gira la imagen.
 */
final class OrientacionExif {

    private OrientacionExif() {
    }

    /** Valor de la etiqueta Orientation (1 a 8); 1 si no hay o no se entiende. */
    static int leer(byte[] d) {
        try {
            if (d.length < 4 || (d[0] & 0xFF) != 0xFF || (d[1] & 0xFF) != 0xD8) {
                return 1; // no es un JPEG
            }
            int pos = 2;
            while (pos + 4 <= d.length && (d[pos] & 0xFF) == 0xFF) {
                int marca = d[pos + 1] & 0xFF;
                if (marca == 0xD8 || marca == 0x01 || (marca >= 0xD0 && marca <= 0xD7)) {
                    pos += 2;
                    continue;
                }
                if (marca == 0xDA || marca == 0xD9) {
                    return 1; // empieza la imagen: ya no hay más cabeceras
                }
                int largo = ((d[pos + 2] & 0xFF) << 8) | (d[pos + 3] & 0xFF);
                if (marca == 0xE1 && largo >= 16 && d[pos + 4] == 'E' && d[pos + 5] == 'x' && d[pos + 6] == 'i'
                        && d[pos + 7] == 'f' && d[pos + 8] == 0 && d[pos + 9] == 0) {
                    return orientacionTiff(d, pos + 10, pos + 2 + largo);
                }
                pos += 2 + largo;
            }
        } catch (RuntimeException e) {
            // etiquetas rotas: se ignoran, como hace Pillow
        }
        return 1;
    }

    private static int orientacionTiff(byte[] d, int tiff, int fin) {
        boolean pequenoPrimero = d[tiff] == 'I' && d[tiff + 1] == 'I';
        if (!pequenoPrimero && !(d[tiff] == 'M' && d[tiff + 1] == 'M')) {
            return 1;
        }
        int ifd = tiff + (int) entero(d, tiff + 4, 4, pequenoPrimero);
        int entradas = (int) entero(d, ifd, 2, pequenoPrimero);
        for (int i = 0; i < entradas; i++) {
            int e = ifd + 2 + i * 12;
            if (e + 12 > fin) {
                break;
            }
            if (entero(d, e, 2, pequenoPrimero) == 0x0112) { // Orientation
                int v = (int) entero(d, e + 8, 2, pequenoPrimero);
                return v >= 1 && v <= 8 ? v : 1;
            }
        }
        return 1;
    }

    private static long entero(byte[] d, int pos, int bytes, boolean pequenoPrimero) {
        long v = 0;
        for (int i = 0; i < bytes; i++) {
            int b = d[pos + (pequenoPrimero ? bytes - 1 - i : i)] & 0xFF;
            v = (v << 8) | b;
        }
        return v;
    }

    /** Gira o voltea la imagen para que se vea derecha (las 8 orientaciones de EXIF). */
    static Imagen corregir(Imagen img, int orientacion) {
        if (orientacion == 1) {
            return img;
        }
        int w = img.ancho, h = img.alto;
        boolean intercambia = orientacion >= 5;
        int nw = intercambia ? h : w, nh = intercambia ? w : h;
        int[] r = new int[nw * nh];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int nx, ny;
                switch (orientacion) {
                    case 2 -> { nx = w - 1 - x; ny = y; }          // volteo horizontal
                    case 3 -> { nx = w - 1 - x; ny = h - 1 - y; }  // 180 grados
                    case 4 -> { nx = x; ny = h - 1 - y; }          // volteo vertical
                    case 5 -> { nx = y; ny = x; }                  // transpuesta
                    case 6 -> { nx = h - 1 - y; ny = x; }          // 90 grados a la derecha
                    case 7 -> { nx = h - 1 - y; ny = w - 1 - x; }  // transversal
                    default -> { nx = y; ny = w - 1 - x; }         // 8: 90 grados a la izquierda
                }
                r[ny * nw + nx] = img.argb[y * w + x];
            }
        }
        return new Imagen(nw, nh, r);
    }
}
