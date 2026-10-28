package api.aura.config;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

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

    static final int LONGITUD_NOMBRE_HISTORIAL = 100;

    private void applyUser(Connection conn) throws SQLException {
        // Siempre se ejecuta SET, aunque no haya usuario, para limpiar el valor
        // que pueda haber quedado de una petición anterior en la misma conexión del pool.
        String nombre = AuraUserHolder.get();
        // Los historiales lo guardan en VARCHAR(100): con un nombre más largo, todo lo que
        // dispara un historial (contratos, servicios, pagos) fallaba para ese usuario
        if (nombre != null && nombre.length() > LONGITUD_NOMBRE_HISTORIAL) {
            nombre = nombre.substring(0, LONGITUD_NOMBRE_HISTORIAL);
        }
        // La variable vive en la conexión física, que el pool reutiliza: si ya tiene este
        // mismo valor no se vuelve a fijar (cada SET era un viaje más a la BD remota en
        // cada transacción). Una conexión nueva no está en el mapa y siempre se fija.
        Connection fisica = conexionFisica(conn);
        Object valor = nombre != null ? nombre : SIN_USUARIO;
        if (fisica != null && valor.equals(ultimoValor.get(fisica))) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement("SET @aura_usuario_nombre = ?")) {
            if (nombre != null) {
                ps.setString(1, nombre);
            } else {
                ps.setNull(1, Types.VARCHAR);
            }
            ps.execute();
        }
        if (fisica != null) {
            ultimoValor.put(fisica, valor);
        }
    }

    /** Valor fijado en cada conexión física; débil para no retener las que el pool cierra. */
    private final Map<Connection, Object> ultimoValor = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Object SIN_USUARIO = new Object();

    private static Connection conexionFisica(Connection conn) {
        try {
            Connection fisica = conn.unwrap(Connection.class);
            // Sin envoltorio del pool no hay forma de saber si es la misma: se fija siempre
            return fisica != conn ? fisica : null;
        } catch (SQLException | RuntimeException sinDesenvolver) {
            return null;
        }
    }
}
