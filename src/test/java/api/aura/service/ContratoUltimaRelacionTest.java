package api.aura.service;

import api.aura.entity.Contrato;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.service.mcas.McasProposalService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.sql.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * «Última relación con la comercializadora» se rellena sola (reunión del 04/11/2026):
 * el final del contrato anterior del mismo punto, si era con la misma comercializadora.
 */
class ContratoUltimaRelacionTest {

    private static final int IBERDROLA = 2;
    private static final int ENDESA = 5;

    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final ContratoService servicio = new ContratoService(contratos, mock(ClienteRepository.class),
            mock(PuntoSuministroRepository.class), mock(ContratoFileStorageService.class),
            mock(McasProposalService.class), mock(ApplicationEventPublisher.class));
    private final PuntoSuministro punto = new PuntoSuministro();

    ContratoUltimaRelacionTest() {
        punto.setId(40);
    }

    private Contrato contrato(Integer id, int comercializadora, String inicio, String fin) {
        return Contrato.builder().id(id).puntoSuministro(punto).idComercializadoraActual(comercializadora)
                .fechaInicio(Date.valueOf(inicio)).fechaFin(Date.valueOf(fin)).build();
    }

    private Contrato nuevo(int comercializadora, Contrato... anteriores) {
        when(contratos.findByPuntoSuministroId(40)).thenReturn(List.of(anteriores));
        Contrato c = contrato(null, comercializadora, "2026-11-01", "2027-10-31");
        // Lo que llegue del formulario (antes se escribía a mano) no cuenta
        c.setMcasFechaUltimaRelacionComercializadora(Date.valueOf("2020-01-01"));
        servicio.rellenarUltimaRelacion(c);
        return c;
    }

    @Test
    void anterior_con_la_misma_comercializadora_coge_su_fecha_fin() {
        Contrato c = nuevo(IBERDROLA, contrato(1, IBERDROLA, "2025-11-01", "2026-10-31"));

        assertThat(c.getMcasFechaUltimaRelacionComercializadora()).isEqualTo(Date.valueOf("2026-10-31"));
    }

    @Test
    void si_se_dio_de_baja_antes_coge_la_fecha_de_baja() {
        Contrato anterior = contrato(1, IBERDROLA, "2025-11-01", "2026-10-31");
        anterior.setFechaBaja(Date.valueOf("2026-04-15"));

        Contrato c = nuevo(IBERDROLA, anterior);

        assertThat(c.getMcasFechaUltimaRelacionComercializadora()).isEqualTo(Date.valueOf("2026-04-15"));
    }

    @Test
    void cuenta_la_ultima_relacion_con_esa_comercializadora_aunque_despues_hubiera_otra() {
        // Ejemplo del cliente (duda 22): Endesa, luego Iberdrola y vuelve a Endesa
        Contrato c = nuevo(IBERDROLA,
                contrato(1, IBERDROLA, "2024-11-01", "2025-10-31"),
                contrato(2, ENDESA, "2025-11-01", "2026-10-31"));

        assertThat(c.getMcasFechaUltimaRelacionComercializadora()).isEqualTo(Date.valueOf("2025-10-31"));
    }

    @Test
    void si_nunca_estuvo_con_esa_comercializadora_se_queda_vacia() {
        Contrato c = nuevo(IBERDROLA, contrato(2, ENDESA, "2025-11-01", "2026-10-31"));

        assertThat(c.getMcasFechaUltimaRelacionComercializadora()).isNull();
    }

    @Test
    void sin_contratos_anteriores_se_queda_vacia() {
        Contrato c = nuevo(IBERDROLA);

        assertThat(c.getMcasFechaUltimaRelacionComercializadora()).isNull();
    }

    @Test
    void en_una_renovacion_cuenta_la_fecha_fin_y_no_la_baja_que_pone_la_propia_renovacion() {
        Contrato original = contrato(1, IBERDROLA, "2025-11-01", "2026-10-31");
        original.setFechaBaja(Date.valueOf("2026-09-15")); // día en que se pulsó «Renovar»
        when(contratos.findByPuntoSuministroId(40)).thenReturn(List.of(original));
        Contrato renovacion = contrato(2, IBERDROLA, "2026-11-01", "2027-10-31");
        renovacion.setMcasEsRenovacion(true);

        servicio.rellenarUltimaRelacion(renovacion);

        assertThat(renovacion.getMcasFechaUltimaRelacionComercializadora()).isEqualTo(Date.valueOf("2026-10-31"));
    }

    @Test
    void los_contratos_posteriores_no_cuentan() {
        Contrato c = nuevo(IBERDROLA,
                contrato(1, IBERDROLA, "2025-11-01", "2026-10-31"),
                contrato(3, IBERDROLA, "2027-11-01", "2028-10-31"));

        assertThat(c.getMcasFechaUltimaRelacionComercializadora()).isEqualTo(Date.valueOf("2026-10-31"));
    }
}
