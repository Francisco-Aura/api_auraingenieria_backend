package api.aura.entity;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Los puntos de suministro de un cliente se cargan en diferido: imprimir o
 * comparar un cliente (o un contrato/servicio que lo contiene) fuera de la
 * transacción no debe intentar recorrerlos.
 */
class EntidadesLombokTest {

    private static List<PuntoSuministro> coleccionSinSesion() {
        return new AbstractList<>() {
            @Override
            public PuntoSuministro get(int index) {
                throw new LazyInitializationException("sin sesión");
            }

            @Override
            public int size() {
                throw new LazyInitializationException("sin sesión");
            }
        };
    }

    @Test
    void cliente_contrato_y_servicio_se_pueden_imprimir_y_comparar_sin_cargar_los_puntos() {
        Cliente cliente = Cliente.builder().id(1).nombre("Cliente Demo").puntosSuministro(coleccionSinSesion()).build();
        Contrato contrato = Contrato.builder().id(2).cliente(cliente).build();
        Servicio servicio = Servicio.builder().id(3).cliente(cliente).build();

        assertThatCode(cliente::toString).doesNotThrowAnyException();
        assertThatCode(cliente::hashCode).doesNotThrowAnyException();
        assertThatCode(() -> cliente.equals(Cliente.builder().id(1).build())).doesNotThrowAnyException();
        assertThatCode(contrato::toString).doesNotThrowAnyException();
        assertThatCode(contrato::hashCode).doesNotThrowAnyException();
        assertThatCode(servicio::toString).doesNotThrowAnyException();
    }
}
