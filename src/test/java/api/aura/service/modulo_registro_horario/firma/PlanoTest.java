package api.aura.service.modulo_registro_horario.firma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cada operación de imagen reproduce a Pillow: los casos de primitivas.json los generó Pillow (el del Registro Horario
 * anterior) con imágenes inventadas, y aquí el resultado debe ser idéntico byte a byte. Única excepción conocida: en el
 * redimensionado, Pillow actual a veces hace primero la pasada vertical y el resultado puede diferir en 1 nivel de gris
 * (se admite hasta 1 y que casi todos los casos sean exactos).
 */
class PlanoTest {

    private static final Map<String, Plano.Filtro> FILTROS = Map.of(
            "LANCZOS", Plano.Filtro.LANCZOS, "BOX", Plano.Filtro.BOX, "BICUBIC", Plano.Filtro.BICUBIC,
            "BILINEAR", Plano.Filtro.BILINEAR);

    private static Plano plano(String hex, int w, int h) {
        return new Plano(w, h, HexFormat.of().parseHex(hex));
    }

    @Test
    void cadaOperacionDaLoMismoQuePillow() throws IOException {
        JsonNode casos;
        try (InputStream in = PlanoTest.class.getResourceAsStream("/registro-horario-oro/firma/primitivas.json")) {
            casos = new ObjectMapper().readTree(in);
        }
        assertTrue(casos.size() > 150);
        int[] porOperacion = new int[6];
        int resizeExactos = 0, resizeConDiferencia = 0;
        StringBuilder fallos = new StringBuilder();
        for (JsonNode c : casos) {
            String op = c.get("op").asText();
            int w = c.get("w").asInt(), h = c.get("h").asInt();
            byte[] esperado = HexFormat.of().parseHex(c.get("salida").asText());
            byte[] obtenido;
            String nombre;
            switch (op) {
                case "max" -> {
                    nombre = "max" + c.get("n") + " " + w + "x" + h;
                    obtenido = plano(c.get("entrada").asText(), w, h).maximos(c.get("n").asInt()).bytes();
                    porOperacion[0]++;
                }
                case "min" -> {
                    nombre = "min" + c.get("n") + " " + w + "x" + h;
                    obtenido = plano(c.get("entrada").asText(), w, h).minimos(c.get("n").asInt()).bytes();
                    porOperacion[1]++;
                }
                case "gauss" -> {
                    nombre = "gauss " + w + "x" + h;
                    obtenido = plano(c.get("entrada").asText(), w, h).gaussiano(c.get("radio").asDouble()).bytes();
                    porOperacion[2]++;
                }
                case "resize" -> {
                    int ow = c.get("ow").asInt(), oh = c.get("oh").asInt();
                    nombre = "resize " + c.get("filtro").asText() + " " + w + "x" + h + "->" + ow + "x" + oh;
                    obtenido = plano(c.get("entrada").asText(), w, h)
                            .redimensionar(ow, oh, FILTROS.get(c.get("filtro").asText())).bytes();
                    porOperacion[3]++;
                }
                case "multiplicar" -> {
                    nombre = "multiplicar";
                    obtenido = plano(c.get("entrada").asText(), w, h).multiplicar(plano(c.get("entrada2").asText(), w, h)).bytes();
                    porOperacion[4]++;
                }
                case "gris" -> {
                    nombre = "gris";
                    obtenido = Imagen.aGris(HexFormat.of().parseHex(c.get("entrada").asText()), w, h).bytes();
                    porOperacion[5]++;
                }
                default -> throw new IllegalStateException(op);
            }
            if (java.util.Arrays.equals(esperado, obtenido)) {
                if (op.equals("resize")) {
                    resizeExactos++;
                }
            } else {
                int distintos = 0, maxDif = 0;
                for (int i = 0; i < esperado.length; i++) {
                    int d = Math.abs((esperado[i] & 0xFF) - (obtenido[i] & 0xFF));
                    if (d != 0) {
                        distintos++;
                        maxDif = Math.max(maxDif, d);
                    }
                }
                if (op.equals("resize") && maxDif <= 1) {
                    resizeConDiferencia++;
                    continue;
                }
                fallos.append(nombre).append(": ").append(distintos).append("/").append(esperado.length)
                        .append(" distintos, dif. máx ").append(maxDif).append('\n');
            }
        }
        assertEquals("", fallos.toString(), "Operaciones que no coinciden con Pillow");
        for (int n : porOperacion) {
            assertTrue(n > 0);
        }
        assertTrue(resizeConDiferencia <= 0.1 * (resizeExactos + resizeConDiferencia),
                "Redimensionados con diferencia de 1 nivel: " + resizeConDiferencia + " de " + (resizeExactos + resizeConDiferencia));
    }
}
