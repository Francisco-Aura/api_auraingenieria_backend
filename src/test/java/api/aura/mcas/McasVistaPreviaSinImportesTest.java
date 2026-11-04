package api.aura.mcas;

import api.aura.dto.mcas.McasCampo;
import api.aura.dto.mcas.McasVistaPreviaDTO;
import api.aura.dto.mcas.McasVistaPreviaDTO.Estado;
import api.aura.service.mcas.McasVistaPreviaService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Quien graba el contrato sin ser administrador ve qué falta, pero no la comisión (reunión del 04/11/2026). */
class McasVistaPreviaSinImportesTest {

    private static final List<McasVistaPreviaDTO.Campo> CAMPOS = List.of(McasVistaPreviaDTO.Campo.builder()
            .clave(McasCampo.FEE).etiqueta("FEE del contrato").falta(false).usadoPor(List.of("Pymes de luz")).build());

    @Test
    void calculada_sin_importe_pagos_calendario_regla_ni_avisos() {
        McasVistaPreviaDTO dto = McasVistaPreviaDTO.builder().estado(Estado.CALCULADO)
                .mensaje("Comisión estimada: 571,26 € en 1 pago.").importe(new BigDecimal("571.26")).numeroPagos(1)
                .calendario(List.of(McasVistaPreviaDTO.Cuota.builder().numero(1).fecha(LocalDate.of(2026, 3, 16))
                        .importe(new BigDecimal("571.26")).build()))
                .regla("Pymes de luz").avisos(List.of("El FEE está por debajo del mínimo")).campos(CAMPOS).build();

        McasVistaPreviaDTO r = McasVistaPreviaService.sinImportes(dto);

        assertThat(r.getEstado()).isEqualTo(Estado.CALCULADO);
        assertThat(r.getImporte()).isNull();
        assertThat(r.getNumeroPagos()).isNull();
        assertThat(r.getCalendario()).isEmpty();
        assertThat(r.getRegla()).isNull();
        assertThat(r.getAvisos()).isEmpty();
        assertThat(r.getCampos()).isEqualTo(CAMPOS);
        assertThat(r.getMensaje()).doesNotContain("€")
                .isEqualTo("Datos de la comisión completos: se calculará al guardar el contrato y la revisará un administrador.");
    }

    @Test
    void con_comision_aprobada_no_dice_cuanto() {
        McasVistaPreviaDTO r = McasVistaPreviaService.sinImportes(McasVistaPreviaDTO.builder()
                .estado(Estado.YA_TIENE_COMISION).mensaje("Este contrato ya tiene una comisión aprobada: 300,00 € en 2 pagos.")
                .importe(new BigDecimal("300")).numeroPagos(2).build());

        assertThat(r.getImporte()).isNull();
        assertThat(r.getMensaje()).doesNotContain("€");
    }

    @Test
    void si_faltan_datos_se_mantiene_lo_que_falta() {
        String mensaje = "Falta un dato para calcular la comisión: FEE del contrato.";
        McasVistaPreviaDTO r = McasVistaPreviaService.sinImportes(McasVistaPreviaDTO.builder()
                .estado(Estado.REQUIERE_DATOS).mensaje(mensaje).campos(CAMPOS).build());

        assertThat(r.getMensaje()).isEqualTo(mensaje);
        assertThat(r.getCampos()).isEqualTo(CAMPOS);
    }
}
