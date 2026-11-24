package api.aura.mcas;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.service.mcas.engine.ContextoCalculo;
import api.aura.service.mcas.engine.EstrategiaMatrizLookup;
import api.aura.service.mcas.engine.EstrategiaRangoLookup;
import api.aura.service.mcas.engine.McasCalculoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Configuraciones de tabla que el motor trataba mal en silencio.
 *
 * <p>RANGE_LOOKUP leía la dimensión de la primera fila y luego comparaba todas las
 * demás contra ella: una regla con filas de dos dimensiones distintas (que el
 * validador rechaza, pero que se puede escribir a mano en base de datos) calculaba
 * comisión con las filas que casualmente cuadraban e ignoraba el resto sin avisar.</p>
 *
 * <p>MATRIX_LOOKUP comparaba los importes de una celda con equals, así que 10.0 y
 * 10.00 —el mismo dinero— se daban por distintos, y una celda sin importe generaba
 * una línea de comisión con importe nulo.</p>
 */
class McasLookupConfiguracionTest {

    @Mock McasValorRangoRepository rangoRepo;

    @BeforeEach void setUp() { MockitoAnnotations.openMocks(this); }

    private McasValorRango numerico(int orden, String min, String max, String resultado) {
        return McasValorRango.builder().idRegla(30).dimension("consumo_kwh")
                .valorMin(new BigDecimal(min)).valorMax(new BigDecimal(max))
                .minInclusivo(true).maxInclusivo(true)
                .valorResultado(resultado == null ? null : new BigDecimal(resultado))
                .orden(orden).build();
    }

    @Test void range_lookup_avisa_si_la_regla_mezcla_dimensiones() {
        var estrategia = new EstrategiaRangoLookup();
        var regla = McasReglaComision.builder().id(30).build();
        var porConsumo = numerico(1, "0", "100000", "300.00");
        var porTarifa = McasValorRango.builder().idRegla(30).dimension("tarifa_acceso")
                .valorCategoria("2.0TD").valorResultado(new BigDecimal("900.00")).orden(2).build();
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(30)).thenReturn(List.of(porConsumo, porTarifa));
        var ctx = ContextoCalculo.builder()
                .consumoAnualKwh(new BigDecimal("58500")).codigoTarifaAcceso("2.0TD").build();

        assertThatThrownBy(() -> estrategia.calcular(ctx, regla, rangoRepo))
                .isInstanceOf(McasCalculoException.class)
                .hasMessageContaining("dimension");
    }

    @Test void matrix_lookup_acepta_el_mismo_importe_escrito_con_distintos_decimales() {
        var estrategia = new EstrategiaMatrizLookup();
        var regla = McasReglaComision.builder().id(30).build();
        var porConsumo = numerico(1, "0", "100000", "10.0");
        var porPotencia = McasValorRango.builder().idRegla(30).dimension("potencia_kw")
                .valorMin(new BigDecimal("0")).valorMax(new BigDecimal("50"))
                .minInclusivo(true).maxInclusivo(true)
                .valorResultado(new BigDecimal("10.00")).orden(1).build();
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(30)).thenReturn(List.of(porConsumo, porPotencia));
        var ctx = ContextoCalculo.builder()
                .consumoAnualKwh(new BigDecimal("58500")).potenciaCalculoKw(new BigDecimal("15")).build();

        assertThat(estrategia.calcular(ctx, regla, rangoRepo).get(0).getImporte())
                .isEqualByComparingTo("10.00");
    }

    @Test void matrix_lookup_avisa_si_la_celda_no_tiene_importe() {
        var estrategia = new EstrategiaMatrizLookup();
        var regla = McasReglaComision.builder().id(30).build();
        var sinImporte = numerico(1, "0", "100000", null);
        when(rangoRepo.findByIdReglaOrderByOrdenAsc(30)).thenReturn(List.of(sinImporte));
        var ctx = ContextoCalculo.builder().consumoAnualKwh(new BigDecimal("58500")).build();

        assertThatThrownBy(() -> estrategia.calcular(ctx, regla, rangoRepo))
                .isInstanceOf(McasCalculoException.class)
                .hasMessageContaining("importe");
    }
}
