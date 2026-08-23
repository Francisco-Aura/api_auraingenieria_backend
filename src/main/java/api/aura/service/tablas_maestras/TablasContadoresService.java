package api.aura.service.tablas_maestras;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Obtiene los conteos de todas las tablas maestras en una única consulta SQL.
 * La base de datos es remota, por lo que cada COUNT() individual implica un
 * viaje de red; agruparlos en un solo UNION ALL reduce la latencia de ~20
 * ida y vuelta a 1.
 */
@Service
public class TablasContadoresService {

    private final JdbcTemplate jdbcTemplate;

    public TablasContadoresService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Long> obtenerContadores() {
        String sql = """
                SELECT 'Comercializadoras' AS tabla, COUNT(*) AS total FROM tbcomercializadoras
                UNION ALL SELECT 'Gestoras', COUNT(*) FROM tbgestoras
                UNION ALL SELECT 'Métodos de Pago', COUNT(*) FROM tbmetodo_pago
                UNION ALL SELECT 'Niveles de Acceso', COUNT(*) FROM tbnivel_acceso
                UNION ALL SELECT 'Pago Plazos', COUNT(*) FROM tbpago_plazos
                UNION ALL SELECT 'Productos', COUNT(*) FROM tbproductos
                UNION ALL SELECT 'Sectores', COUNT(*) FROM tbsectores
                UNION ALL SELECT 'Subtipologías', COUNT(*) FROM tbsubtipologia
                UNION ALL SELECT 'Tarifas Gas', COUNT(*) FROM tbtarifas_gas
                UNION ALL SELECT 'Tarifas Luz', COUNT(*) FROM tbtarifas_luz
                UNION ALL SELECT 'Usuarios', COUNT(*) FROM usuarios
                UNION ALL SELECT 'Distribuidoras', COUNT(*) FROM tb_distribuidoras
                UNION ALL SELECT 'Tipos de Contador', COUNT(*) FROM tb_tipos_contador
                UNION ALL SELECT 'Tipos de Comunicación', COUNT(*) FROM tb_tipos_comunicacion
                UNION ALL SELECT 'Propiedades', COUNT(*) FROM tb_propiedades
                UNION ALL SELECT 'APP Monitorización', COUNT(*) FROM tbmonitorizacion
                UNION ALL SELECT 'Integración Sistemas FV', COUNT(*) FROM tbintegracion_sistemasfv
                UNION ALL SELECT 'Tareas - Categorías', COUNT(*) FROM modulotareas_categorias
                UNION ALL SELECT 'Tareas - Subcategorías', COUNT(*) FROM modulotareas_subcategorias
                UNION ALL SELECT 'Cargos de Contacto', COUNT(*) FROM tbcargo_contacto
                """;

        Map<String, Long> contadores = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            contadores.put(rs.getString("tabla"), rs.getLong("total"));
        });
        return contadores;
    }
}
