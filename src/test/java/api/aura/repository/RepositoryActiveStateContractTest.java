package api.aura.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryActiveStateContractTest {

    @Test
    void activeServiceIdsRequireVigencyAndCompatibleNoBajaState() throws Exception {
        String query = queryOf(ServicioRepository.class,
                "findIdsPuntosSuministroConServicioActivo", List.class);

        assertActiveServicePredicate(query);
    }

    @Test
    void activeServiceCountUsesTheSameCompatibleNoBajaState() throws Exception {
        String query = queryOf(ServicioRepository.class, "countServiciosActivos");

        assertActiveServicePredicate(query);
    }

    @Test
    void cascadeRepositoriesExposeCompatibleActiveRows() throws Exception {
        assertCompatibleNoBajaPredicate(queryOf(PuntoSuministroRepository.class,
                "findActivosPorClienteId", Integer.class));
        assertCompatibleNoBajaPredicate(queryOf(ServicioRepository.class,
                "findActivosPorClienteId", Integer.class));
        assertCompatibleNoBajaPredicate(queryOf(ContratoRepository.class,
                "findActivosPorClienteId", Integer.class));
    }

    @Test
    void activeContractsAcceptBothHistoricalSentinelYears() throws Exception {
        assertCompatibleNoBajaPredicate(queryOf(ContratoRepository.class,
                "findContratosActivos"));
    }

    private static String queryOf(Class<?> repository, String methodName, Class<?>... parameterTypes)
            throws Exception {
        Method method = repository.getDeclaredMethod(methodName, parameterTypes);
        Query query = method.getAnnotation(Query.class);
        assertNotNull(query, () -> methodName + " debe declarar explicitamente la regla de vigencia");
        return query.value().replaceAll("\\s+", " ").toUpperCase();
    }

    private static void assertActiveServicePredicate(String query) {
        assertTrue(query.contains("FECHAINICIOSERVICIO <= CURRENT_DATE"));
        assertTrue(query.contains("FECHAFINSERVICIO > CURRENT_DATE"));
        assertCompatibleNoBajaPredicate(query);
    }

    private static void assertCompatibleNoBajaPredicate(String query) {
        assertTrue(query.contains("FECHABAJA IS NULL"));
        assertTrue(query.contains("YEAR(") && query.contains("= 1"));
        assertTrue(query.contains("YEAR(") && query.contains("= 1900"));
    }

    @Test
    void elAvisoDeVencimientoNoSeSaltaLasTareasConElFlagSinInformar() throws Exception {
        // «notificadoProximoVencimiento = false» deja fuera las filas con NULL: en SQL
        // una comparación con NULL nunca es cierta. Una tarea heredada con el campo sin
        // informar no recibiría nunca el aviso de «1 hora antes».
        String query = queryOf(api.aura.repository.modulo_tareas.mt_TareaRepository.class,
                "findTareasParaNotificarVencimiento", java.time.LocalDateTime.class, java.time.LocalDateTime.class);

        assertTrue(query.replaceAll("\s+", " ").toUpperCase().contains("NOTIFICADOPROXIMOVENCIMIENTO IS NULL"),
                "la consulta debe tratar el flag sin informar como «no notificada»: " + query);
    }

}
