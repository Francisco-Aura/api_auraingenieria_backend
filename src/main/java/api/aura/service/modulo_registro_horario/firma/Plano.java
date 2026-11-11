package api.aura.service.modulo_registro_horario.firma;

import java.util.Arrays;

/**
 * Imagen de un solo canal de 8 bits (como el modo «L» de Pillow) con las operaciones que necesita el extractor de firma.
 * Cada operación reproduce el resultado de la de Pillow que usaba el Registro Horario anterior (redimensionado con sus
 * filtros y aritmética entera, filtros de máximo y mínimo, desenfoque gaussiano), para que la firma salga igual.
 */
public final class Plano {

    /** Filtros de redimensionado de Pillow: nombre y radio de soporte. */
    public enum Filtro {
        BOX(0.5), BILINEAR(1.0), BICUBIC(2.0), LANCZOS(3.0);

        final double soporte;

        Filtro(double soporte) {
            this.soporte = soporte;
        }

        double peso(double x) {
            switch (this) {
                case BOX:
                    return x > -0.5 && x <= 0.5 ? 1.0 : 0.0;
                case BILINEAR:
                    x = Math.abs(x);
                    return x < 1.0 ? 1.0 - x : 0.0;
                case BICUBIC: {
                    final double a = -0.5;
                    x = Math.abs(x);
                    if (x < 1.0) {
                        return ((a + 2.0) * x - (a + 3.0)) * x * x + 1;
                    }
                    return x < 2.0 ? (((x - 5) * x + 8) * x - 4) * a : 0.0;
                }
                default: { // LANCZOS
                    if (x <= -3.0 || x >= 3.0) {
                        return 0.0;
                    }
                    return seno(x) * seno(x / 3);
                }
            }
        }

        private static double seno(double x) {
            if (x == 0.0) {
                return 1.0;
            }
            x *= Math.PI;
            return Math.sin(x) / x;
        }
    }

    private static final int PRECISION = 22; // bits de la aritmética entera del redimensionado (como Pillow)

    public final int ancho;
    public final int alto;
    final byte[] datos;

    public Plano(int ancho, int alto) {
        this(ancho, alto, new byte[ancho * alto]);
    }

    public Plano(int ancho, int alto, byte[] datos) {
        if (datos.length != ancho * alto) {
            throw new IllegalArgumentException("El tamaño no cuadra con los datos");
        }
        this.ancho = ancho;
        this.alto = alto;
        this.datos = datos;
    }

    public int get(int x, int y) {
        return datos[y * ancho + x] & 0xFF;
    }

    public void set(int x, int y, int v) {
        datos[y * ancho + x] = (byte) v;
    }

    public byte[] bytes() {
        return datos;
    }

    public Plano copia() {
        return new Plano(ancho, alto, datos.clone());
    }

    // ---------------------------------------------------------------- operaciones de color

    /** Como {@code Image.point(lut)}. */
    public Plano punto(int[] tabla) {
        Plano r = new Plano(ancho, alto);
        for (int i = 0; i < datos.length; i++) {
            r.datos[i] = (byte) tabla[datos[i] & 0xFF];
        }
        return r;
    }

    /** Tabla de 256 valores a partir de una función (como {@code point(lambda v: ...)}). */
    public static int[] tabla(java.util.function.IntUnaryOperator f) {
        int[] t = new int[256];
        for (int v = 0; v < 256; v++) {
            t[v] = f.applyAsInt(v);
        }
        return t;
    }

    /** {@code ImageChops.multiply}: a·b/255 (división entera), como Pillow. */
    public Plano multiplicar(Plano otra) {
        Plano r = new Plano(ancho, alto);
        for (int i = 0; i < datos.length; i++) {
            r.datos[i] = (byte) ((datos[i] & 0xFF) * (otra.datos[i] & 0xFF) / 255);
        }
        return r;
    }

    /** {@code ImageChops.subtract}: a - b sin bajar de 0. */
    public Plano restar(Plano otra) {
        Plano r = new Plano(ancho, alto);
        for (int i = 0; i < datos.length; i++) {
            r.datos[i] = (byte) Math.max(0, (datos[i] & 0xFF) - (otra.datos[i] & 0xFF));
        }
        return r;
    }

    /** {@code ImageOps.expand(imagen, border=n, fill=valor)}: añade un marco de n píxeles. */
    public Plano expandir(int borde, int valor) {
        Plano r = new Plano(ancho + 2 * borde, alto + 2 * borde);
        if (valor != 0) {
            Arrays.fill(r.datos, (byte) valor);
        }
        r.pegar(this, borde, borde);
        return r;
    }

    /** {@code ImageChops.darker}: el menor de cada pareja de píxeles. */
    public Plano menor(Plano otra) {
        Plano r = new Plano(ancho, alto);
        for (int i = 0; i < datos.length; i++) {
            r.datos[i] = (byte) Math.min(datos[i] & 0xFF, otra.datos[i] & 0xFF);
        }
        return r;
    }

    /** Histograma de 256 valores; con máscara, solo cuentan los píxeles donde la máscara no es 0. */
    public long[] histograma(Plano mascara) {
        long[] h = new long[256];
        for (int i = 0; i < datos.length; i++) {
            if (mascara == null || mascara.datos[i] != 0) {
                h[datos[i] & 0xFF]++;
            }
        }
        return h;
    }

    /** Recuadro (x0, y0, x1, y1) de lo que no es 0, o {@code null} si todo es 0 ({@code getbbox}). */
    public int[] caja() {
        int x0 = ancho, y0 = alto, x1 = -1, y1 = -1;
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                if (datos[y * ancho + x] != 0) {
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                }
            }
        }
        return x1 < 0 ? null : new int[]{x0, y0, x1 + 1, y1 + 1};
    }

    // ---------------------------------------------------------------- recortar y pegar

    /** {@code Image.crop}: lo que cae fuera de la imagen se rellena con 0. */
    public Plano recortar(int x0, int y0, int x1, int y1) {
        Plano r = new Plano(Math.max(0, x1 - x0), Math.max(0, y1 - y0));
        for (int y = 0; y < r.alto; y++) {
            int sy = y + y0;
            if (sy < 0 || sy >= alto) {
                continue;
            }
            for (int x = 0; x < r.ancho; x++) {
                int sx = x + x0;
                if (sx >= 0 && sx < ancho) {
                    r.datos[y * r.ancho + x] = datos[sy * ancho + sx];
                }
            }
        }
        return r;
    }

    /** Rellena un recuadro (lo que sale de la imagen se ignora), como {@code paste(valor, caja)}. */
    public void rellenar(int x0, int y0, int x1, int y1, int valor) {
        for (int y = Math.max(0, y0); y < Math.min(alto, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(ancho, x1); x++) {
                datos[y * ancho + x] = (byte) valor;
            }
        }
    }

    /** Pega otra imagen con su esquina en (x0, y0), recortando lo que sobre. */
    public void pegar(Plano otra, int x0, int y0) {
        for (int y = 0; y < otra.alto; y++) {
            int dy = y + y0;
            if (dy < 0 || dy >= alto) {
                continue;
            }
            for (int x = 0; x < otra.ancho; x++) {
                int dx = x + x0;
                if (dx >= 0 && dx < ancho) {
                    datos[dy * ancho + dx] = otra.datos[y * otra.ancho + x];
                }
            }
        }
    }

    // ---------------------------------------------------------------- filtros de rango

    /** {@code MaxFilter(n)}: máximo de la ventana de n×n. */
    public Plano maximos(int n) {
        return rango(n, true);
    }

    /** {@code MinFilter(n)}: mínimo de la ventana de n×n. */
    public Plano minimos(int n) {
        return rango(n, false);
    }

    /**
     * Como el filtro de rango de Pillow actual: en el borde la ventana se recorta a lo que cabe en la imagen (para el
     * máximo y el mínimo da lo mismo que repetir el borde).
     */
    private Plano rango(int n, boolean maximo) {
        if (n % 2 == 0) {
            throw new IllegalArgumentException("El tamaño del filtro debe ser impar");
        }
        int m = n / 2;
        byte[] horizontal = new byte[datos.length];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int mejor = maximo ? 0 : 255;
                for (int k = Math.max(0, x - m); k <= Math.min(ancho - 1, x + m); k++) {
                    int v = datos[y * ancho + k] & 0xFF;
                    mejor = maximo ? Math.max(mejor, v) : Math.min(mejor, v);
                }
                horizontal[y * ancho + x] = (byte) mejor;
            }
        }
        Plano r = new Plano(ancho, alto);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int mejor = maximo ? 0 : 255;
                for (int k = Math.max(0, y - m); k <= Math.min(alto - 1, y + m); k++) {
                    int v = horizontal[k * ancho + x] & 0xFF;
                    mejor = maximo ? Math.max(mejor, v) : Math.min(mejor, v);
                }
                r.datos[y * ancho + x] = (byte) mejor;
            }
        }
        return r;
    }

    // ---------------------------------------------------------------- redimensionado

    /**
     * {@code Image.resize((ancho, alto), filtro)}: dos pasadas con coeficientes enteros, como Pillow. Aquí siempre va
     * primero la horizontal; Pillow actual a veces empieza por la vertical (según los tamaños), y en esos casos el
     * resultado puede diferir en 1 nivel de gris en algunos píxeles.
     */
    public Plano redimensionar(int nuevoAncho, int nuevoAlto, Filtro filtro) {
        Plano actual = this;
        if (nuevoAncho != ancho) {
            actual = actual.pasada(nuevoAncho, filtro, true);
        }
        if (nuevoAlto != alto) {
            actual = actual.pasada(nuevoAlto, filtro, false);
        }
        return actual == this ? copia() : actual;
    }

    private Plano pasada(int salida, Filtro filtro, boolean horizontal) {
        int entrada = horizontal ? ancho : alto;
        int[] limites = new int[salida * 2];
        int[][] coef = coeficientes(entrada, salida, filtro, limites);
        Plano r = horizontal ? new Plano(salida, alto) : new Plano(ancho, salida);
        int lineas = horizontal ? alto : ancho;
        for (int linea = 0; linea < lineas; linea++) {
            for (int i = 0; i < salida; i++) {
                int min = limites[i * 2];
                int cuantos = limites[i * 2 + 1];
                long suma = 1L << (PRECISION - 1);
                int[] k = coef[i];
                for (int j = 0; j < cuantos; j++) {
                    int v = horizontal ? datos[linea * ancho + min + j] & 0xFF : datos[(min + j) * ancho + linea] & 0xFF;
                    suma += (long) v * k[j];
                }
                int valor = (int) Math.max(0, Math.min(255, suma >> PRECISION));
                if (horizontal) {
                    r.datos[linea * r.ancho + i] = (byte) valor;
                } else {
                    r.datos[i * r.ancho + linea] = (byte) valor;
                }
            }
        }
        return r;
    }

    /** Coeficientes de cada píxel de salida (enteros de 22 bits) y su tramo de entrada [mínimo, cuántos]. */
    private static int[][] coeficientes(int entrada, int salida, Filtro filtro, int[] limites) {
        double escala = (double) entrada / salida;
        double escalaFiltro = Math.max(escala, 1.0);
        double soporte = filtro.soporte * escalaFiltro;
        int[][] resultado = new int[salida][];
        for (int xx = 0; xx < salida; xx++) {
            double centro = (xx + 0.5) * escala;
            double ss = 1.0 / escalaFiltro;
            int min = (int) (centro - soporte + 0.5);
            if (min < 0) {
                min = 0;
            }
            int max = (int) (centro + soporte + 0.5);
            if (max > entrada) {
                max = entrada;
            }
            max -= min;
            double[] k = new double[Math.max(max, 0)];
            double total = 0;
            for (int x = 0; x < max; x++) {
                double w = filtro.peso((x + min - centro + 0.5) * ss);
                k[x] = w;
                total += w;
            }
            int[] enteros = new int[k.length];
            for (int x = 0; x < k.length; x++) {
                double v = total != 0 ? k[x] / total : k[x];
                enteros[x] = (int) (v < 0 ? -0.5 + v * (1 << PRECISION) : 0.5 + v * (1 << PRECISION));
            }
            resultado[xx] = enteros;
            limites[xx * 2] = min;
            limites[xx * 2 + 1] = k.length;
        }
        return resultado;
    }

    // ---------------------------------------------------------------- desenfoque gaussiano

    /**
     * {@code GaussianBlur(radio)}: como Pillow, tres pasadas de desenfoque «de caja» por cada dirección (primero las
     * horizontales y luego, transpuesta la imagen, las verticales), con radio de caja fraccionario y borde repetido.
     */
    public Plano gaussiano(double radio) {
        final int pasadas = 3;
        float sigma2 = (float) radio * (float) radio / pasadas;
        float largo = (float) Math.sqrt(12.0 * sigma2 + 1.0);
        float l = (float) Math.floor((largo - 1.0) / 2.0);
        float a = (2 * l + 1) * (l * (l + 1) - 3 * sigma2);
        a /= 6 * (sigma2 - (l + 1) * (l + 1));
        float radioCaja = l + a;

        Plano actual = copia();
        for (int i = 0; i < pasadas; i++) {
            actual = actual.cajaHorizontal(radioCaja);
        }
        actual = actual.transpuesta();
        for (int i = 0; i < pasadas; i++) {
            actual = actual.cajaHorizontal(radioCaja);
        }
        return actual.transpuesta();
    }

    private Plano transpuesta() {
        Plano r = new Plano(alto, ancho);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                r.datos[x * r.ancho + y] = datos[y * ancho + x];
            }
        }
        return r;
    }

    /** Una pasada horizontal del desenfoque de caja de Pillow (pesos de 24 bits; píxeles fuera de la imagen = el del borde). */
    private Plano cajaHorizontal(float radioFlotante) {
        int radio = (int) radioFlotante;
        long ww = (long) ((float) (1 << 24) / (radioFlotante * 2 + 1)) & 0xFFFFFFFFL;
        long fw = (((1L << 24) - (long) (radio * 2 + 1) * ww) / 2) & 0xFFFFFFFFL;
        Plano r = new Plano(ancho, alto);
        int ultimo = ancho - 1;
        for (int y = 0; y < alto; y++) {
            int base = y * ancho;
            for (int x = 0; x <= ultimo; x++) {
                long acumulado = 0;
                for (int k = x - radio; k <= x + radio; k++) {
                    acumulado += datos[base + Math.max(0, Math.min(ultimo, k))] & 0xFF;
                }
                long lejos = (datos[base + Math.max(0, Math.min(ultimo, x - radio - 1))] & 0xFF)
                        + (datos[base + Math.max(0, Math.min(ultimo, x + radio + 1))] & 0xFF);
                long total = (acumulado * ww + lejos * fw + (1L << 23)) & 0xFFFFFFFFL;
                r.datos[base + x] = (byte) (total >> 24);
            }
        }
        return r;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Plano p && p.ancho == ancho && p.alto == alto && Arrays.equals(p.datos, datos);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * ancho + alto) + Arrays.hashCode(datos);
    }
}
