package api.aura.service.modulo_registro_horario.firma;

import api.aura.service.modulo_registro_horario.firma.Plano.Filtro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Extrae la firma de la foto de un manuscrito: solo la tinta, con fondo transparente y recortada a su tamaño.
 *
 * <p>Una foto no tiene el papel blanco puro: hay sombras, luz desigual y grano. Por eso no basta con quitar el blanco.
 * El método es el del Registro Horario anterior: estimar cómo ilumina la luz el papel, restárselo a la foto (queda solo
 * lo que es más oscuro que el papel de alrededor, es decir, la tinta), quedarse con la tinta, limpiar motas y bordes, y
 * recortar. Se prepara la foto una sola vez y se puede sacar la firma con distinta sensibilidad (0 a 1).
 */
public final class ExtractorFirma {

    /** Firma ya extraída: tinta (RGB), transparencia y recorte. */
    public record Resultado(int ancho, int alto, int[] argb) {
    }

    private final Plano rojo, verde, azul;
    private final Plano gris;
    private final int ancho, alto;
    private Plano zona;
    private final Plano diff;
    private final int piso, techo;
    private final boolean hayFirma;

    /** {@code lado}: la foto se reduce hasta que su lado mayor mida eso (si es más grande). */
    public ExtractorFirma(Imagen imagen, int lado) {
        Plano r = imagen.canal(0), g = imagen.canal(1), b = imagen.canal(2);
        if (Math.max(imagen.ancho, imagen.alto) > lado) {
            double escala = (double) lado / Math.max(imagen.ancho, imagen.alto);
            int nw = Math.max(1, (int) Math.rint(imagen.ancho * escala));
            int nh = Math.max(1, (int) Math.rint(imagen.alto * escala));
            r = r.redimensionar(nw, nh, Filtro.LANCZOS);
            g = g.redimensionar(nw, nh, Filtro.LANCZOS);
            b = b.redimensionar(nw, nh, Filtro.LANCZOS);
        }
        this.rojo = r;
        this.verde = g;
        this.azul = b;
        this.gris = Imagen.aGris(r, g, b);
        this.ancho = gris.ancho;
        this.alto = gris.alto;
        int w = ancho, h = alto;

        // La luz sobre el papel: se reduce la imagen, se «borra» la tinta (que es fina) con un filtro de máximos y se
        // suaviza. Lo que queda es el brillo del papel en cada zona.
        Plano pequena = gris.redimensionar(Math.max(1, w / 8), Math.max(1, h / 8), Filtro.BOX);
        Plano fondoP = pequena.maximos(9).gaussiano(2);
        Plano fondo = fondoP.redimensionar(w, h, Filtro.BICUBIC);

        // Zona de papel: donde el «fondo» (la luz sin tinta) es claro. Se descarta lo que hay fuera (mesa, sombras
        // fuertes) y se reduce un poco hacia dentro para dejar fuera también el borde del papel. El filtro de máximos
        // corto (5) «contagia» ~16 px hacia fuera pero es más grande que cualquier mancha de tinta, así que una firma
        // densa no se confunde con la mesa; después la zona se reduce ~40 px.
        Plano zonaP = pequena.maximos(5);
        int claro = percentil(zonaP.histograma(null), 0.98);
        final double umbralPapel = 0.62 * claro;
        Plano papel = rellenarHuecos(zonaP.punto(Plano.tabla(v -> v > umbralPapel ? 255 : 0)), 0.12).minimos(11);
        this.zona = papel.redimensionar(w, h, Filtro.BILINEAR).punto(Plano.tabla(v -> v > 128 ? 255 : 0));

        // Oscurecimiento RELATIVO a la luz de cada zona (1 - brillo/papel): la tinta debe contar igual en una zona con
        // sombra que en una bien iluminada.
        final int fmin = Math.max(30, percentil(fondo.histograma(zona), 0.03));
        Plano ganancia = fondo.punto(Plano.tabla(v -> (int) (255.0 * fmin / Math.max(v, fmin))));
        Plano normal = gris.multiplicar(ganancia);
        this.diff = normal.punto(Plano.tabla(v -> Math.max(0, Math.min(255, Math.floorDiv((fmin - v) * 255, fmin)))));
        quitarMargenesOscuros();
        long[] hist = diff.histograma(zona); // el contraste se mide solo dentro del papel
        this.piso = percentil(hist, 0.80);     // nivel del ruido del papel
        this.techo = percentil(hist, 0.993);   // nivel de la tinta más oscura
        // Sin contraste suficiente no hay nada que extraer.
        this.hayFirma = (techo - piso) >= 45;
    }

    // ---------------------------------------------------------------- pasos

    static int percentil(long[] histograma, double p) {
        long total = 0;
        for (long n : histograma) {
            total += n;
        }
        long acumulado = 0;
        for (int valor = 0; valor < histograma.length; valor++) {
            acumulado += histograma[valor];
            if (acumulado >= p * total) {
                return valor;
            }
        }
        return 255;
    }

    /**
     * Un hueco de la zona de papel que queda rodeado de papel es tinta (el cruce de varios trazos gruesos forma una
     * mancha oscura y compacta), no mesa: se rellena. Los oscuros que tocan el borde de la foto (la mesa) no se tocan,
     * ni los que ocupan más de {@code maximo} de la imagen (un objeto grande sobre el papel).
     */
    static Plano rellenarHuecos(Plano mascara, double maximo) {
        int w = mascara.ancho, h = mascara.alto;
        byte[] datos = mascara.copia().bytes();
        boolean[] visto = new boolean[w * h];
        double limite = maximo * w * h;
        int[] pila = new int[w * h];
        int[] comp = new int[w * h];
        for (int inicio = 0; inicio < w * h; inicio++) {
            if (datos[inicio] != 0 || visto[inicio]) {
                continue;
            }
            visto[inicio] = true;
            int tope = 0, n = 0;
            boolean tocaBorde = false;
            pila[tope++] = inicio;
            while (tope > 0) {
                int p = pila[--tope];
                comp[n++] = p;
                int y = p / w, x = p % w;
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    tocaBorde = true;
                }
                if (x > 0 && datos[p - 1] == 0 && !visto[p - 1]) {
                    visto[p - 1] = true;
                    pila[tope++] = p - 1;
                }
                if (x < w - 1 && datos[p + 1] == 0 && !visto[p + 1]) {
                    visto[p + 1] = true;
                    pila[tope++] = p + 1;
                }
                if (y > 0 && datos[p - w] == 0 && !visto[p - w]) {
                    visto[p - w] = true;
                    pila[tope++] = p - w;
                }
                if (y < h - 1 && datos[p + w] == 0 && !visto[p + w]) {
                    visto[p + w] = true;
                    pila[tope++] = p + w;
                }
            }
            if (!tocaBorde && n <= limite) {
                for (int i = 0; i < n; i++) {
                    datos[comp[i]] = (byte) 255;
                }
            }
        }
        return new Plano(w, h, datos);
    }

    /**
     * Si un tramo de la franja junto a un borde de la foto es oscuro (la mesa, el borde del papel girado...), no es
     * papel: se saca de la zona. Se mira por tramos para que valga también con fotos giradas.
     */
    private void quitarMargenesOscuros() {
        int w = ancho, h = alto;
        int claro = percentil(gris.histograma(zona), 0.98);
        int m = Math.max(6, (int) (0.04 * Math.min(w, h))); // grosor de la franja
        int n = 10;                                          // tramos por lado
        List<int[]> tramos = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            int a = k * w / n, b = (k + 1) * w / n;
            tramos.add(new int[]{a, 0, b, m});
            tramos.add(new int[]{a, h - m, b, h});
            a = k * h / n;
            b = (k + 1) * h / n;
            tramos.add(new int[]{0, a, m, b});
            tramos.add(new int[]{w - m, a, w, b});
        }
        for (int[] caja : tramos) {
            if (percentil(gris.recortar(caja[0], caja[1], caja[2], caja[3]).histograma(null), 0.5) < 0.62 * claro) {
                zona.rellenar(caja[0], caja[1], caja[2], caja[3], 0);
            }
        }
        // El hueco que queda también se agranda un poco, para no dejar un filo de transición
        zona = zona.minimos(5);
    }

    private Plano alfa(double sens) {
        double k = 0.55 - 0.42 * sens; // sensibilidad alta = umbral bajo = entran más trazos finos (y algo más de ruido)
        double umbral = piso + (techo - piso) * k;
        double anchoRampa = Math.max(12.0, 0.30 * (techo - piso));
        int[] lut = Plano.tabla(v -> Math.max(0, Math.min(255, (int) ((v - umbral) * 255 / anchoRampa))));
        // sin filtro de mediana: rompería los trazos finos; las motas se quitan después por tamaño
        return diff.punto(lut).menor(zona);
    }

    /**
     * Quita las rayas largas pegadas a un borde de la foto (el filo del papel o de la mesa). Si la tinta forma una raya
     * continua de más del 12 % del lado, es un borde y no una firma; el extremo de un trazo que solo cruza el borde es corto.
     */
    private static Plano quitarRayasDeBorde(Plano alfa) {
        int w = alfa.ancho, h = alfa.alto;
        int grosor = Math.max(4, (int) (0.03 * Math.min(w, h)));
        Plano tinta = alfa.punto(Plano.tabla(v -> v > 70 ? 255 : 0));
        Plano resultado = alfa.copia();
        for (int lado = 0; lado < 4; lado++) {
            boolean vertical = lado < 2; // bordes izquierdo y derecho: se recorre en vertical
            int largo = vertical ? h : w;
            boolean[] perfil = new boolean[largo];
            for (int p = 0; p < largo; p++) {
                for (int q = 0; q < grosor; q++) {
                    int x, y;
                    if (lado == 0) {
                        x = q;
                        y = p;
                    } else if (lado == 1) {
                        x = w - grosor + q;
                        y = p;
                    } else if (lado == 2) {
                        x = p;
                        y = q;
                    } else {
                        x = p;
                        y = h - grosor + q;
                    }
                    if (x >= 0 && x < w && y >= 0 && y < h && tinta.get(x, y) != 0) {
                        perfil[p] = true;
                        break;
                    }
                }
            }
            int inicio = -1, ultimo = -1;
            for (int p = 0; p < largo + 4; p++) {
                if (p < largo && perfil[p]) {
                    if (inicio < 0) {
                        inicio = p;
                    }
                    ultimo = p;
                } else if (inicio >= 0 && p - ultimo > 3) { // una raya admite huecos de hasta 3 puntos
                    if (ultimo - inicio + 1 >= 0.12 * largo) {
                        switch (lado) {
                            case 0 -> resultado.rellenar(0, inicio, grosor, ultimo + 1, 0);
                            case 1 -> resultado.rellenar(w - grosor, inicio, w, ultimo + 1, 0);
                            case 2 -> resultado.rellenar(inicio, 0, ultimo + 1, grosor, 0);
                            default -> resultado.rellenar(inicio, h - grosor, ultimo + 1, h, 0);
                        }
                    }
                    inicio = -1;
                }
            }
        }
        return resultado;
    }

    /** Quita las motas sueltas y lo que toca el borde de la foto (marcas del papel, mesa, sombras); {@code null} si no queda firma. */
    private static Plano limpiar(Plano alfaEntrada) {
        int w = alfaEntrada.ancho, h = alfaEntrada.alto;
        int m = Math.max(4, (int) (0.025 * Math.min(w, h))); // franja junto al borde de la foto
        Plano alfa = quitarRayasDeBorde(alfaEntrada);
        byte[] datos = alfa.punto(Plano.tabla(v -> v > 70 ? 255 : 0)).bytes();
        boolean[] visto = new boolean[w * h];
        List<int[]> comps = new ArrayList<>();
        List<Boolean> pegados = new ArrayList<>();
        ArrayDeque<Integer> pila = new ArrayDeque<>();
        for (int inicio = 0; inicio < w * h; inicio++) {
            if (datos[inicio] == 0 || visto[inicio]) {
                continue;
            }
            visto[inicio] = true;
            pila.push(inicio);
            List<Integer> comp = new ArrayList<>();
            int enFranja = 0;
            while (!pila.isEmpty()) {
                int i = pila.pop();
                comp.add(i);
                int y = i / w, x = i % w;
                if (x < m || y < m || x >= w - m || y >= h - m) {
                    enFranja++;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) {
                        continue;
                    }
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx >= 0 && xx < w) {
                            int j = yy * w + xx;
                            if (datos[j] != 0 && !visto[j]) {
                                visto[j] = true;
                                pila.push(j);
                            }
                        }
                    }
                }
            }
            // «pegado al borde»: casi todo el fragmento está en la franja (resto del borde del papel, de la mesa...).
            // Una firma que solo cruza el borde de la foto no cumple esto y se conserva.
            comps.add(comp.stream().mapToInt(Integer::intValue).toArray());
            pegados.add(enFranja >= 0.5 * comp.size());
        }
        if (comps.isEmpty()) {
            return null;
        }
        long total = 0;
        boolean hayLibres = false;
        for (int c = 0; c < comps.size(); c++) {
            total += comps.get(c).length;
            hayLibres |= !pegados.get(c);
        }
        double minimo = Math.max(6, 0.004 * total);
        Plano mantener = new Plano(w, h);
        int maxArea = 0;
        long sumaAreas = 0;
        boolean alguna = false;
        for (int c = 0; c < comps.size(); c++) {
            int[] comp = comps.get(c);
            if (comp.length < minimo || (pegados.get(c) && hayLibres)) {
                continue;
            }
            alguna = true;
            maxArea = Math.max(maxArea, comp.length);
            sumaAreas += comp.length;
            for (int i : comp) {
                mantener.bytes()[i] = (byte) 255;
            }
        }
        // Una firma tiene algún trazo largo y conectado, o al menos bastante tinta en total; unas motas sueltas
        // repartidas por el papel, no. Así un papel en blanco no se toma por una firma.
        if (!alguna || (maxArea < 0.0003 * w * h && sumaAreas < 0.0025 * w * h)) {
            return null;
        }
        // el trazo conserva sus bordes suavizados: se ensancha un poco la zona permitida
        return alfa.menor(mantener.maximos(5));
    }

    /**
     * Color de la tinta: el de sus puntos más oscuros (los bordes difuminados del trazo son más claros y lavarían el
     * color); solo se oscurece si aun así sale demasiado claro para firmar.
     */
    private int[] colorTinta(Plano mascara) {
        int corte = percentil(gris.histograma(mascara), 0.40); // el 40 % más oscuro de la tinta
        long n = 0, sr = 0, sg = 0, sb = 0;
        for (int i = 0; i < gris.datos.length; i++) {
            if (mascara.datos[i] != 0 && (gris.datos[i] & 0xFF) <= corte) {
                n++;
                sr += rojo.datos[i] & 0xFF;
                sg += verde.datos[i] & 0xFF;
                sb += azul.datos[i] & 0xFF;
            }
        }
        if (n == 0) {
            return new int[]{20, 20, 40};
        }
        double r = (double) sr / n, g = (double) sg / n, b = (double) sb / n;
        double brillo = 0.299 * r + 0.587 * g + 0.114 * b;
        double factor = brillo != 0 ? Math.min(1.0, 80 / brillo) : 1.0;
        return new int[]{(int) (r * factor), (int) (g * factor), (int) (b * factor)};
    }

    /** La firma recortada (tinta con fondo transparente), o {@code null} si no se detecta ninguna. */
    public Resultado resultado(double sens) {
        if (!hayFirma) {
            return null;
        }
        Plano alfa = limpiar(alfa(sens));
        if (alfa == null) {
            return null;
        }
        int[] caja = alfa.punto(Plano.tabla(v -> v > 25 ? 255 : 0)).caja();
        if (caja == null) {
            return null;
        }
        Plano mascara = alfa.punto(Plano.tabla(v -> v > 200 ? 255 : 0));
        int[] tinta = colorTinta(mascara);
        int margen = Math.max(2, (int) (0.03 * Math.max(caja[2] - caja[0], caja[3] - caja[1])));
        int x0 = Math.max(0, caja[0] - margen), y0 = Math.max(0, caja[1] - margen);
        int x1 = Math.min(ancho, caja[2] + margen), y1 = Math.min(alto, caja[3] + margen);
        int rgb = (tinta[0] << 16) | (tinta[1] << 8) | tinta[2];
        int[] argb = new int[(x1 - x0) * (y1 - y0)];
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                argb[(y - y0) * (x1 - x0) + (x - x0)] = (alfa.get(x, y) << 24) | rgb;
            }
        }
        return new Resultado(x1 - x0, y1 - y0, argb);
    }
}
