package api.aura.service.modulo_planificador;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Los scripts SQL del Planificador se aplican una sola vez por base de datos, en orden, y un fallo no deja pasar a los siguientes. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class, properties = {
        "planificador.scripts-bbdd=true", "planificador.scripts-bbdd.patron=classpath*:scripts-prueba/*.sql",
        "spring.datasource.url=jdbc:h2:mem:scripts;MODE=MariaDB;DB_CLOSE_DELAY=-1"})
@org.springframework.test.context.ActiveProfiles("planificador")
class ScriptsBBDDTest {

    @Autowired
    private ScriptsBBDD ejecutor;
    @Autowired
    private DataSource dataSource;

    @Test
    void cadaScriptSeEjecutaUnaSolaVezAunqueSeRepitaElArranque() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        // El arranque del contexto ya los ha aplicado
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM prueba_scripts", Integer.class));
        assertEquals("uno; con punto y coma", jdbc.queryForObject("SELECT texto FROM prueba_scripts WHERE n = 1", String.class));
        assertEquals(List.of("V1_Planificador_a.sql", "V2_Planificador_b.sql"),
                jdbc.queryForList("SELECT nombre FROM pl_scripts ORDER BY nombre", String.class));
        // Otro arranque: no repite nada (si repitiera, habría 4 filas)
        ejecutor.run(new org.springframework.boot.DefaultApplicationArguments());
        ejecutor.run(new org.springframework.boot.DefaultApplicationArguments());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM prueba_scripts", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM pl_scripts", Integer.class));
    }

    @Test
    void siUnScriptFallaNoSeEjecutanLosSiguientesNiSeAnotaElFallido() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        new ScriptsBBDD(dataSource, "classpath*:scripts-prueba-fallo/*.sql").run(new org.springframework.boot.DefaultApplicationArguments());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pl_scripts WHERE nombre LIKE '%_x.sql' OR nombre LIKE '%_y.sql'", Integer.class),
                "ni el que falló ni el siguiente quedan anotados");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = 'segundo_no_debe_existir'", Integer.class),
                "el siguiente script no se ejecutó");
    }

    /** Los scripts reales son de MariaDB y no corren en H2, pero se comprueba que se trocean bien (hay «;» dentro de textos y comentarios). */
    @Test
    void losScriptsRealesDelInventarioSeTroceanEnLasSentenciasEsperadas() throws Exception {
        assertEquals(4, sentencias("BBDD/V20261111_1_Insert_Planificador_AIKO_MDE72Dw.sql").size(), "SET NAMES, SET @pos, INSERT y SELECT");
        var insert = sentencias("BBDD/V20261111_1_Insert_Planificador_AIKO_MDE72Dw.sql").get(2);
        assertTrue(insert.startsWith("INSERT IGNORE INTO pl_equipos"));
        assertEquals(7, insert.split("\\('mod-aiko-", -1).length - 1, "una fila por potencia");
        assertEquals(3, sentencias("BBDD/V20261111_2_Update_Planificador_AIKO_ficha.sql").size(), "SET NAMES, UPDATE y SELECT");
    }

    /** Ejecuta el script contra una conexión simulada que solo anota las sentencias que recibe (así se ve cómo las trocea Spring). */
    private static List<String> sentencias(String ruta) throws Exception {
        List<String> salida = new ArrayList<>();
        java.sql.Statement st = (java.sql.Statement) java.lang.reflect.Proxy.newProxyInstance(
                ScriptsBBDDTest.class.getClassLoader(), new Class<?>[]{java.sql.Statement.class}, (p, m, a) -> {
                    if (m.getName().equals("execute")) {
                        salida.add(((String) a[0]).trim());
                        return false;
                    }
                    if (m.getName().equals("getUpdateCount")) {
                        return -1; // «no hay más resultados»: con otro valor Spring sigue pidiendo resultados sin parar
                    }
                    Class<?> t = m.getReturnType();
                    return t == boolean.class ? (Object) false : t == int.class ? (Object) 0 : t == long.class ? (Object) 0L : null;
                });
        java.sql.Connection c = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                ScriptsBBDDTest.class.getClassLoader(), new Class<?>[]{java.sql.Connection.class}, (p, m, a) -> {
                    if (m.getName().equals("createStatement")) {
                        return st;
                    }
                    Class<?> t = m.getReturnType();
                    return t == boolean.class ? (Object) false : t == int.class ? (Object) 0 : null;
                });
        ScriptUtils.executeSqlScript(c, new org.springframework.core.io.support.EncodedResource(new ClassPathResource(ruta), StandardCharsets.UTF_8));
        return salida;
    }
}
