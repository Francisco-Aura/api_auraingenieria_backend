package api.aura.service;

import api.aura.controller.ContratoController;
import api.aura.repository.InformesRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Query;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Informes: un contrato sin tarifa elegida no desaparece, y solo se reclaman las
 * cuotas del plan de pagos (como en la pantalla del servicio). Las consultas son SQL
 * nativo de MariaDB, que H2 no interpreta: se comprueba el texto (y se ha probado en
 * MariaDB 10.11 con un plan de 6 cuotas que conservaba la 7.ª sin pagar).
 */
class InformesTarifasYCuotasTest {

    private static final String[] ORDINALES = {"primer", "segundo", "tercer", "cuarto", "quinto", "sexto",
            "septimo", "octavo", "noveno", "decimo", "decimoprimer", "decimosegundo"};

    private static String consulta(String metodo) {
        return Arrays.stream(InformesRepository.class.getMethods())
                .filter(m -> m.getName().equals(metodo))
                .findFirst().orElseThrow()
                .getAnnotation(Query.class).value()
                .replaceAll("\\s+", " ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"findContratosPendientesRenovacion", "findContratosPendientesRenovacionByUsuario",
            "findContratosVigentes", "findContratosHistorico"})
    void un_contrato_sin_tarifa_sigue_saliendo_en_los_informes(String metodo) {
        // Sin tarifa elegida el contrato se guarda con id 0, que no existe en las tablas
        assertThat(consulta(metodo))
                .contains("LEFT JOIN tbtarifas_gas ON contratos.id_tarifa_gas = tbtarifas_gas.ID")
                .contains("LEFT JOIN tbtarifas_luz ON contratos.id_tarifa_luz = tbtarifas_luz.ID")
                .doesNotMatch(".*[^T] JOIN tbtarifas_(luz|gas) ON contratos.*");
    }

    @Test
    void pendientes_de_pago_solo_reclama_cuotas_del_plan() {
        String sql = consulta("findServiciosPendientesPago");
        for (int i = 0; i < ORDINALES.length; i++) {
            String condicion = "COALESCE(sp." + ORDINALES[i] + "_pago_pagado, 0) = 0 AND COALESCE(tbp.numero_cuotas, 12) >= " + (i + 1);
            // fecha, número, importe, estado y filtro: las cinco veces con el límite del plan
            assertThat(sql.split(java.util.regex.Pattern.quote(condicion), -1)).as(ORDINALES[i]).hasSize(6);
            assertThat(sql).doesNotContain("COALESCE(sp." + ORDINALES[i] + "_pago_pagado, 0) = 0 AND sp.");
        }
    }

    @Test
    void servicios_vigentes_solo_da_por_pendiente_una_cuota_del_plan() {
        String sql = consulta("findServiciosVigentes");
        for (int i = 0; i < ORDINALES.length; i++) {
            assertThat(sql).contains("(sp." + ORDINALES[i] + "_pago_pagado = 0 AND COALESCE(tpp.numero_cuotas, 12) >= " + (i + 1) + ")");
        }
    }

    @Test
    void marcar_el_siguiente_pago_tampoco_pasa_del_plan() throws Exception {
        String fuente = Files.readString(Path.of("src/main/java/api/aura/service/InformesService.java"));
        String metodo = fuente.substring(fuente.indexOf("public void marcarSiguientePagoPendiente"));
        metodo = metodo.substring(0, metodo.indexOf("public List<ContratoVigenteDTO>"));
        String sql = metodo.substring(metodo.indexOf("\"\"\""), metodo.lastIndexOf("\"\"\""));
        assertThat(sql).contains("AND numero <= (SELECT COALESCE(MAX(pp.numero_cuotas), 12) FROM servicios_pagos sp");
        // Un parámetro por cada «?» de la consulta
        long parametros = sql.chars().filter(c -> c == '?').count();
        String llamada = metodo.substring(metodo.indexOf("queryForObject(sql, Integer.class,"), metodo.indexOf(");", metodo.indexOf("queryForObject(sql")));
        assertThat(llamada.split("idServicio", -1).length - 1).isEqualTo((int) parametros);
    }

    @Test
    void renovar_con_un_dato_que_choca_en_la_bd_da_409_como_crear_y_editar() throws Exception {
        ContratoService servicio = mock(ContratoService.class);
        when(servicio.renovarContrato(eq(5), any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));
        // Como JacksonConfig: los campos que no son del contrato (idCliente...) se ignoran
        ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ContratoController controlador = new ContratoController(servicio, json);

        // Lo traduce GlobalExceptionHandler (409 con el motivo); antes acababa en un 500 genérico
        assertThatThrownBy(() -> controlador.renovarContratoJson(5, json.readTree("{\"idCliente\": 1}")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> controlador.renovarContratoMultipart(5, "{\"idCliente\": 1}", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
