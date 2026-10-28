package api.aura.rendimiento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los índices de V20261006_1 se crean sobre las tablas y columnas reales (las que
 * genera JPA con nombres en snake_case). V20260305_1 usaba nombres en camelCase
 * (fechaFinServicio, idPuntoSuministro...) que no existen y sus índices nunca se
 * crearon. Aquí cada CREATE INDEX del script se ejecuta contra el esquema de pruebas.
 */
@SpringBootTest
class ScriptIndicesTest {

    private static final String SCRIPT = "V20261006_1_Indices_Servicios_Contratos.sql";

    @Autowired private JdbcTemplate jdbc;

    @Test
    void cada_indice_del_script_se_crea_sobre_columnas_existentes() throws Exception {
        String sql = Files.readString(Path.of("src", "main", "resources", "BBDD", SCRIPT), StandardCharsets.UTF_8);
        List<String> indices = Arrays.stream(sql.replaceAll("(?m)^\\s*--.*$", "").split(";"))
                .map(String::trim)
                .filter(s -> s.toUpperCase().startsWith("CREATE INDEX"))
                .toList();

        assertThat(indices).hasSize(3);
        indices.forEach(jdbc::execute);
    }
}
