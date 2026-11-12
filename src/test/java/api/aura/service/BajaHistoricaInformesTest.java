package api.aura.service;

import api.aura.repository.InformesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Sin baja» significa lo mismo en toda la aplicación: fecha nula, año 1 o año
 * 1900 (fechas históricas heredadas). Los repositorios JPA y los servicios ya lo
 * tratan así; los informes, que son SQL nativo, solo miraban `0001-01-01`, de modo
 * que un cliente, punto, contrato o servicio con la fecha 1900 salía como activo
 * en las pantallas y desaparecía de todos los informes.
 *
 * <p>La consulta es SQL de MariaDB (DATE_SUB, CURDATE...) que H2 no interpreta;
 * por eso se comprueba el texto de la consulta.</p>
 */
class BajaHistoricaInformesTest {

    private static final Pattern COMPARACION_SUELTA =
            Pattern.compile("[\\w.]+\\.fecha_baja\\s*=\\s*'0001-01-01'");

    private static List<Method> consultasNativas() {
        List<Method> metodos = new ArrayList<>();
        for (Method m : InformesRepository.class.getMethods()) {
            if (m.getAnnotation(Query.class) != null) metodos.add(m);
        }
        assertThat(metodos).isNotEmpty();
        return metodos;
    }

    @Test
    void ningun_informe_da_por_baja_una_fecha_historica_de_1900() {
        List<String> sueltas = new ArrayList<>();
        for (Method m : consultasNativas()) {
            String sql = m.getAnnotation(Query.class).value().replaceAll("\\s+", " ");
            Matcher matcher = COMPARACION_SUELTA.matcher(sql);
            while (matcher.find()) sueltas.add(m.getName() + ": " + matcher.group());
        }
        assertThat(sueltas)
                .as("comparaciones que ignoran el año 1900")
                .isEmpty();
    }

    @Test
    void los_informes_usan_el_mismo_criterio_que_el_resto_de_la_aplicacion() {
        long conCriterio = consultasNativas().stream()
                .map(m -> m.getAnnotation(Query.class).value())
                .filter(sql -> sql.contains("fecha_baja"))
                .filter(sql -> sql.replaceAll("\\s+", " ").contains("YEAR(") && sql.contains("1900"))
                .count();
        assertThat(conCriterio).as("consultas con el criterio completo").isGreaterThanOrEqualTo(5);
    }
}
