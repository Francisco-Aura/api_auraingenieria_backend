package api.aura.service;

import api.aura.service.mcas.engine.MotorMcas;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * El reparto en cuotas debe ser el mismo en MCAS y en los servicios: mismas
 * cifras para el mismo importe y el mismo numero de pagos. Antes MCAS redondeaba
 * la cuota base a la mitad hacia arriba y los servicios truncaban el centimo, asi
 * que 1,00 EUR en 8 pagos salia 0,13x7 + 0,09 en un sitio y 0,12x7 + 0,16 en el
 * otro. Las dos sumaban el total, pero eran repartos distintos.
 */
class RepartoCuotasTest {

    private final MotorMcas motor = new MotorMcas(null, null, null, null, null);

    /** Reparto de referencia de los servicios: centimos truncados, la ultima cuota absorbe el resto. */
    private List<BigDecimal> repartoServicios(String total, int cuotas) {
        long centimos = new BigDecimal(total).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        long base = centimos / cuotas;
        long ultima = centimos - base * (cuotas - 1L);
        return java.util.stream.IntStream.rangeClosed(1, cuotas)
                .mapToObj(i -> BigDecimal.valueOf(i == cuotas ? ultima : base, 2))
                .toList();
    }

    @Test
    void mcas_reparte_igual_que_los_servicios() {
        for (String total : List.of("1.00", "150.00", "411.84", "100.00", "0.10", "999.99")) {
            for (int cuotas : new int[] { 1, 2, 3, 4, 6, 8, 12 }) {
                List<BigDecimal> esperado = repartoServicios(total, cuotas);
                List<BigDecimal> obtenido = motor.buildPagos(new BigDecimal(total), cuotas);
                assertThat(obtenido)
                        .as("%s en %d cuotas", total, cuotas)
                        .usingElementComparator(BigDecimal::compareTo)
                        .containsExactlyElementsOf(esperado);
            }
        }
    }

    @Test
    void ninguna_cuota_intermedia_supera_a_la_ultima_y_la_suma_cuadra() {
        List<BigDecimal> pagos = motor.buildPagos(new BigDecimal("1.00"), 8);

        assertThat(pagos).hasSize(8);
        assertThat(pagos.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1.00");
        BigDecimal ultima = pagos.get(pagos.size() - 1);
        assertThat(pagos.subList(0, 7)).allSatisfy(c -> assertThat(c).isLessThanOrEqualTo(ultima));
    }

    @Test
    void un_solo_pago_es_el_importe_entero() {
        assertThat(motor.buildPagos(new BigDecimal("411.84"), 1))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("411.84"));
        assertThat(motor.buildPagos(new BigDecimal("411.84"), 0))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("411.84"));
    }

    @Test
    void el_motor_no_necesita_repositorios_para_repartir() {
        // Guarda de diseño: buildPagos es una función pura; si algún día tocara la
        // base de datos, este test fallaría al construirlo con dependencias nulas.
        assertThat(new MotorMcas(null, null, null, mock(api.aura.service.mcas.engine.RegistroEstrategias.class), null)
                .buildPagos(new BigDecimal("10.00"), 3))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3.33"), new BigDecimal("3.33"), new BigDecimal("3.34"));
    }
}
