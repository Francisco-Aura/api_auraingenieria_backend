package api.aura.service.modulo_registro_horario.documento;

import api.aura.service.modulo_registro_horario.firma.FirmaService;
import api.aura.service.modulo_registro_horario.firma.Imagen;
import api.aura.service.modulo_registro_horario.firma.TrabajosPesados;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La firma preparada para el documento (recorte, fondo blanco a transparente, tamaño final y trazo reforzado) sale como
 * la de {@code preparar_firma} del original, con firmas inventadas (una transparente y una con fondo blanco).
 */
class FirmaParaDocumentoTest {

    private static final String RAIZ = "/registro-horario-oro/word/";
    private final FirmaService firmas = new FirmaService(new TrabajosPesados());

    static byte[] recurso(String nombre) throws IOException {
        try (InputStream in = FirmaParaDocumentoTest.class.getResourceAsStream(RAIZ + nombre)) {
            assertTrue(in != null, "Falta " + nombre);
            return in.readAllBytes();
        }
    }

    @Test
    void cadaFirmaPreparadaCoincideConLaDelOriginal() throws IOException {
        JsonNode indice = new ObjectMapper().readTree(recurso("preparar/indice.json"));
        assertEquals(12, indice.size());
        StringBuilder fallos = new StringBuilder();
        for (JsonNode c : indice) {
            String clave = c.get("clave").asText();
            Double alto = c.get("alto").isNull() ? null : c.get("alto").asDouble();
            Imagen entrada = firmas.decodificar(recurso(c.get("firma").asText() + ".png"));
            FirmaParaDocumento.Preparada obtenida = FirmaParaDocumento.preparar(entrada, true, alto);
            Imagen esperada = firmas.decodificar(recurso("preparar/" + clave + ".png"));
            Imagen recibida = firmas.decodificar(obtenida.png());
            if (esperada.ancho != recibida.ancho || esperada.alto != recibida.alto) {
                fallos.append(clave).append(": tamaño ").append(recibida.ancho).append('x').append(recibida.alto)
                        .append(" en vez de ").append(esperada.ancho).append('x').append(esperada.alto).append('\n');
                continue;
            }
            assertEquals(c.get("aspecto").asDouble(), obtenida.aspecto(), 1e-12, clave + ": proporción");
            int distintos = 0, maxDif = 0;
            for (int i = 0; i < esperada.argb.length; i++) {
                for (int s = 0; s <= 24; s += 8) {
                    int d = Math.abs(((esperada.argb[i] >>> s) & 0xFF) - ((recibida.argb[i] >>> s) & 0xFF));
                    if (d != 0 && !(s < 24 && (esperada.argb[i] >>> 24) == 0 && (recibida.argb[i] >>> 24) == 0)) {
                        distintos++;
                        maxDif = Math.max(maxDif, d);
                    }
                }
            }
            if (maxDif > 1 || distintos > 0.02 * esperada.argb.length) {
                fallos.append(clave).append(": ").append(distintos).append(" valores distintos, dif. máx ").append(maxDif).append('\n');
            }
        }
        assertEquals("", fallos.toString());
    }
}
