package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.service.modulo_planificador.estudio.Pvgis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente de PVGIS (Comisión Europea, gratuito y sin clave) y del buscador de lugares de Open-Meteo. Solo se envían coordenadas y el
 * nombre de un lugar: nunca datos del cliente.
 */
@Component
public class PvgisCliente {

    private final String urlPvgis;
    private final String urlLugares;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper json = new ObjectMapper();

    public PvgisCliente(@Value("${planificador.pvgis.url:https://re.jrc.ec.europa.eu/api/v5_3/seriescalc}") String urlPvgis,
                        @Value("${planificador.pvgis.url-lugares:https://geocoding-api.open-meteo.com/v1/search}") String urlLugares) {
        this.urlPvgis = urlPvgis;
        this.urlLugares = urlLugares;
    }

    /** Producción horaria de 1 kWp (kWh) del año tipo, con la inclinación y el azimut dados (azimut 0 = sur, -90 = este, 90 = oeste). */
    public double[] serie(double lat, double lon, int angulo, int azimut) {
        String url = urlPvgis + String.format(Locale.ROOT, "?lat=%.4f&lon=%.4f&peakpower=1&loss=14&angle=%d&aspect=%d&pvcalculation=1"
                + "&startyear=2015&endyear=2019&outputformat=json&browser=0", lat, lon, angulo, azimut);
        try {
            return Pvgis.anoTipo(json.readTree(pedir(url, Duration.ofSeconds(90))));
        } catch (IOException | IllegalArgumentException | java.time.format.DateTimeParseException e) {
            throw new ErrorApi(HttpStatus.BAD_GATEWAY, "No se pudo leer la respuesta de PVGIS.");
        }
    }

    /** Lugares que coinciden con el texto (nombre, región y coordenadas). */
    public List<Map<String, Object>> buscarLugar(String texto) {
        String url = urlLugares + "?count=10&language=es&format=json&name=" + URLEncoder.encode(texto, StandardCharsets.UTF_8);
        try {
            JsonNode r = json.readTree(pedir(url, Duration.ofSeconds(15))).path("results");
            List<Map<String, Object>> lugares = new ArrayList<>();
            for (JsonNode l : r) {
                Map<String, Object> m = new LinkedHashMap<>();
                String region = l.path("admin1").asText("");
                m.put("nombre", l.path("name").asText("") + (region.isEmpty() ? "" : " (" + region + ")") + " · " + l.path("country").asText(""));
                m.put("latitud", l.path("latitude").asDouble());
                m.put("longitud", l.path("longitude").asDouble());
                lugares.add(m);
            }
            // Los de España primero: es donde se hacen casi todos los estudios
            lugares.sort((x, y) -> Boolean.compare(String.valueOf(y.get("nombre")).endsWith("España"), String.valueOf(x.get("nombre")).endsWith("España")));
            return lugares;
        } catch (IOException e) {
            throw new ErrorApi(HttpStatus.BAD_GATEWAY, "No se pudo leer la respuesta del buscador de lugares.");
        }
    }

    private byte[] pedir(String url, Duration espera) {
        try {
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(espera).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) {
                throw new ErrorApi(HttpStatus.BAD_GATEWAY, "El servicio externo respondió con el error " + r.statusCode() + ".");
            }
            return r.body();
        } catch (IOException e) {
            throw new ErrorApi(HttpStatus.BAD_GATEWAY, "No se pudo conectar con el servicio externo (PVGIS).");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorApi(HttpStatus.BAD_GATEWAY, "Se interrumpió la consulta a PVGIS.");
        }
    }
}
