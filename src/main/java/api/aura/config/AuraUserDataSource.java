package api.aura.config;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * DataSource decorator that executes SET @aura_usuario_nombre = ?
 * on every connection obtained from the pool, so that MariaDB triggers
 * can read the currently logged-in user name via COALESCE(@aura_usuario_nombre, 'Sistema').
 */
public class AuraUserDataSource extends DelegatingDataSource {

    public AuraUserDataSource(DataSource delegate) {
        super(delegate);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return conUsuario(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return conUsuario(super.getConnection(username, password));
    }

    /** Si no se puede fijar el usuario, la conexión se devuelve al pool en vez de quedar retenida. */
    private Connection conUsuario(Connection conn) throws SQLException {
        try {
            applyUser(conn);
            return conn;
        } catch (SQLException | RuntimeException e) {
            try {
                conn.close();
            } catch (SQLException cierre) {
                e.addSuppressed(cierre);
            }
            throw e;
        }
    }

    private void applyUser(Connection conn) throws SQLException {
        // Siempre se ejecuta SET, aunque no haya usuario, para limpiar el valor
        // que pueda haber quedado de una petición anterior en la misma conexión del pool.
        String nombre = AuraUserHolder.get();
        try (PreparedStatement ps = conn.prepareStatement("SET @aura_usuario_nombre = ?")) {
            if (nombre != null) {
                ps.setString(1, nombre);
            } else {
                ps.setNull(1, Types.VARCHAR);
            }
            ps.execute();
        }
    }
}
