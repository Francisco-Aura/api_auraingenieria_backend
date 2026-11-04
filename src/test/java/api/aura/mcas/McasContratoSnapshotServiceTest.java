package api.aura.mcas;

import api.aura.entity.Contrato;
import api.aura.service.mcas.McasContratoSnapshotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;

import static org.assertj.core.api.Assertions.assertThat;

class McasContratoSnapshotServiceTest {

    private final McasContratoSnapshotService service =
            new McasContratoSnapshotService(new ObjectMapper());

    @Test
    void captura_fechas_sql_sin_toInstant_y_es_determinista() {
        Contrato contrato = contratoBase();
        contrato.setFechaInicio(Date.valueOf("2026-03-16"));
        contrato.setMcasFechaUltimaRelacionComercializadora(Date.valueOf("2025-12-01"));

        var primera = service.capturar(contrato);
        var segunda = service.capturar(contrato);

        assertThat(primera.hash()).hasSize(64).isEqualTo(segunda.hash());
        assertThat(primera.json()).contains("2026-03-16", "EUR_MWH");
        // La última relación no influye en el cálculo: no cambia la instantánea
        assertThat(primera.json()).doesNotContain("2025-12-01");
    }

    @Test
    void cambia_hash_si_cambia_un_dato_relevante_del_calculo() {
        Contrato contrato = contratoBase();
        String inicial = service.capturar(contrato).hash();

        contrato.setMcasNumeroPagos(12);

        assertThat(service.capturar(contrato).hash()).isNotEqualTo(inicial);
    }

    private Contrato contratoBase() {
        return Contrato.builder()
                .id(42)
                .contrato("CTR-2026-0042")
                .idComercializadoraActual(7)
                .idTarifaLuz(3)
                .consumoAnual(58_500d)
                .mcasTipoPrecio("FIJO")
                .mcasTipoSuministro("LUZ")
                .mcasSegmento("PYME")
                .mcasFeeEurMwh(new BigDecimal("7.25"))
                .mcasFeeUnidad("EUR_MWH")
                .mcasIdGestora(2)
                .mcasIdProductoComercial(9)
                .mcasNumeroPagos(4)
                .build();
    }
}
