package api.aura.service.modulo_planificador;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Aplica al arrancar los scripts SQL del Planificador ({@code BBDD/*_Planificador_*.sql}) que esa base de datos todavía no ha ejecutado.
 * <p>
 * Cada base anota en {@code pl_scripts} el nombre y la huella de los que ya ha aplicado, así que cada entorno (DES, PRE, PRO) recibe cada
 * script una sola vez, en el orden de su nombre, aunque el código llegue a cada uno en un momento distinto. Los archivos se quedan en el
 * repositorio; cuando ya han corrido en todos los entornos se pueden borrar sin consecuencias (ver docs del Planificador).
 * <p>
 * Reglas: un script ya anotado no se repite aunque se edite (se avisa en el registro; para cambiar algo, un script nuevo). Si un script
 * falla se anota en el registro, no se ejecutan los siguientes (el orden importa) y se reintenta en el siguiente arranque, sin impedir que
 * arranque la intranet. Como en MariaDB las sentencias que crean tablas no se deshacen, los scripts deben poder repetirse sin daño
 * ({@code IF NOT EXISTS}, {@code INSERT IGNORE}, {@code UPDATE} con condición).
 * <p>
 * Se apaga con {@code planificador.scripts-bbdd=false} (variable PLANIFICADOR_SCRIPTS_BBDD).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "planificador.scripts-bbdd", havingValue = "true")
public class ScriptsBBDD implements ApplicationRunner {

    static final String PATRON_POR_DEFECTO = "classpath*:BBDD/*_Planificador_*.sql";
    private static final Logger LOG = LoggerFactory.getLogger("planificador");

    private final DataSource dataSource;
    private final String patron;

    public ScriptsBBDD(DataSource dataSource, @Value("${planificador.scripts-bbdd.patron:" + PATRON_POR_DEFECTO + "}") String patron) {
        this.dataSource = dataSource;
        this.patron = patron;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            aplicar();
        } catch (Exception e) {
            LOG.error("No se pudieron aplicar los scripts de base de datos del Planificador: {}", e.toString());
        }
    }

    void aplicar() throws Exception {
        Resource[] scripts = new PathMatchingResourcePatternResolver().getResources(patron);
        Arrays.sort(scripts, Comparator.comparing(r -> String.valueOf(r.getFilename())));
        try (Connection c = dataSource.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS pl_scripts (nombre VARCHAR(200) NOT NULL PRIMARY KEY, "
                        + "huella VARCHAR(64) NOT NULL, ejecutado_el TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            }
            Map<String, String> hechos = new HashMap<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT nombre, huella FROM pl_scripts")) {
                while (rs.next()) {
                    hechos.put(rs.getString(1), rs.getString(2));
                }
            }
            for (Resource script : scripts) {
                String nombre = script.getFilename();
                String huella = huella(script);
                if (hechos.containsKey(nombre)) {
                    if (!hechos.get(nombre).equals(huella)) {
                        LOG.warn("El script {} ya se ejecutó y después se ha modificado: no se repite (usa un script nuevo para el cambio).", nombre);
                    }
                    continue;
                }
                LOG.info("Ejecutando el script de base de datos {}", nombre);
                try {
                    ScriptUtils.executeSqlScript(c, new EncodedResource(script, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    LOG.error("Falló el script {}: {}. No se ejecutan los siguientes; se reintentará en el próximo arranque.", nombre, e.toString());
                    return;
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO pl_scripts (nombre, huella) VALUES (?, ?)")) {
                    ps.setString(1, nombre);
                    ps.setString(2, huella);
                    ps.executeUpdate();
                }
            }
        }
    }

    private static String huella(Resource script) throws Exception {
        try (var in = script.getInputStream()) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }
}
