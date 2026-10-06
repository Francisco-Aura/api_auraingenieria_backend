package api.aura.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuraUserDataSourceTest {

    @AfterEach
    void limpiar() {
        AuraUserHolder.clear();
    }

    @Test
    void si_no_se_puede_fijar_el_usuario_la_conexion_se_devuelve_al_pool() throws Exception {
        DataSource pool = mock(DataSource.class);
        Connection conexion = mock(Connection.class);
        when(pool.getConnection()).thenReturn(conexion);
        when(conexion.prepareStatement(anyString())).thenThrow(new SQLException("conexión caída"));

        assertThatThrownBy(() -> new AuraUserDataSource(pool).getConnection())
                .isInstanceOf(SQLException.class)
                .hasMessage("conexión caída");

        verify(conexion).close();
    }

    @Test
    void fija_el_usuario_de_la_peticion_en_cada_conexion() throws Exception {
        DataSource pool = mock(DataSource.class);
        Connection conexion = mock(Connection.class);
        PreparedStatement sentencia = mock(PreparedStatement.class);
        when(pool.getConnection()).thenReturn(conexion);
        when(conexion.prepareStatement("SET @aura_usuario_nombre = ?")).thenReturn(sentencia);
        AuraUserHolder.set("Ana Pérez");

        assertThat(new AuraUserDataSource(pool).getConnection()).isSameAs(conexion);

        verify(sentencia).setString(1, "Ana Pérez");
        verify(sentencia).execute();
    }
}
