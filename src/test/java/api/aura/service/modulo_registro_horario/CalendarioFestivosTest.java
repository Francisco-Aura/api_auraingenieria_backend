package api.aura.service.modulo_registro_horario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paridad con el Registro Horario anterior: los valores de oro de src/test/resources/registro-horario-oro los generó el
 * calendario.py ORIGINAL (años 2000 a 2100, con y sin festivos extra, incluidos extras mal escritos).
 */
class CalendarioFestivosTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static JsonNode oro(String nombre) throws IOException {
        try (InputStream in = CalendarioFestivosTest.class.getResourceAsStream("/registro-horario-oro/" + nombre)) {
            return MAPPER.readTree(in);
        }
    }

    private static List<String> aTextos(Set<LocalDate> fechas) {
        return fechas.stream().map(LocalDate::toString).sorted().toList();
    }

    private static List<String> aTextos(JsonNode lista) {
        List<String> r = new ArrayList<>();
        lista.forEach(x -> r.add(x.asText()));
        return r;
    }

    @Test
    void pascuaYFestivosNacionalesCoincidenConElOriginalDe2000A2100() throws IOException {
        JsonNode anios = oro("calendario.json");
        assertEquals(101, anios.size());
        anios.fields().forEachRemaining(e -> {
            int anio = Integer.parseInt(e.getKey());
            assertEquals(e.getValue().get("pascua").asText(), CalendarioFestivos.pascua(anio).toString(), "Pascua " + anio);
            assertEquals(aTextos(e.getValue().get("nacionales")), aTextos(CalendarioFestivos.festivosDelAnio(anio)),
                    "festivos nacionales " + anio);
        });
    }

    @Test
    void festivosExtraValidosEInvalidosCoincidenConElOriginal() throws IOException {
        List<String> extras = aTextos(oro("extras-calendario.json"));
        oro("calendario.json").fields().forEachRemaining(e -> {
            int anio = Integer.parseInt(e.getKey());
            assertEquals(aTextos(e.getValue().get("con_extras")),
                    aTextos(CalendarioFestivos.festivosDelAnio(anio, extras)), "festivos con extras " + anio);
        });
    }

    @Test
    void viernesSantoEsDosDiasAntesDePascua() {
        assertEquals(LocalDate.of(2026, 4, 5), CalendarioFestivos.pascua(2026));
        assertTrue(CalendarioFestivos.festivosDelAnio(2026).contains(LocalDate.of(2026, 4, 3)));
    }

    @Test
    void elDiaExtraDelUnoDeMarzoEnUnAnioNoBisiestoSeIgnora() {
        assertTrue(CalendarioFestivos.festivosDelAnio(2027, List.of("02-29")).stream()
                .noneMatch(f -> f.getMonthValue() == 2));
        assertTrue(CalendarioFestivos.festivosDelAnio(2028, List.of("02-29")).contains(LocalDate.of(2028, 2, 29)));
    }
}
