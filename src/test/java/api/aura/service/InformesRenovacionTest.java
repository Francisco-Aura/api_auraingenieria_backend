package api.aura.service;

import api.aura.repository.InformesRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.Query;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La comercializadora anterior es opcional en el formulario del contrato: un
 * contrato sin ella tiene que aparecer igualmente en «Pendientes de renovación»
 * (como ya ocurre en «Contratos vigentes» e «Histórico»).
 *
 * <p>La consulta es SQL nativo de MariaDB (DATE_SUB ... INTERVAL 30 DAY), que H2
 * no interpreta; por eso se comprueba el texto de la consulta.</p>
 */
class InformesRenovacionTest {

    private static String consulta(String metodo) {
        return Arrays.stream(InformesRepository.class.getMethods())
                .filter(m -> m.getName().equals(metodo))
                .findFirst().orElseThrow()
                .getAnnotation(Query.class).value()
                .replaceAll("\\s+", " ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"findContratosPendientesRenovacion", "findContratosPendientesRenovacionByUsuario"})
    void las_comercializadoras_se_unen_sin_excluir_contratos_que_no_las_tienen(String metodo) {
        String sql = consulta(metodo);

        assertThat(sql)
                .contains("LEFT JOIN tbcomercializadoras AS ACT ON contratos.id_comercializadora_actual = ACT.ID")
                .contains("LEFT JOIN tbcomercializadoras AS ANT ON contratos.id_comercializadora_anterior = ANT.ID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"findContratosVigentes", "findContratosHistorico"})
    void los_demas_informes_de_contratos_ya_lo_hacian_asi(String metodo) {
        assertThat(consulta(metodo))
                .contains("LEFT JOIN tbcomercializadoras AS ANT ON contratos.id_comercializadora_anterior = ANT.ID");
    }
}
