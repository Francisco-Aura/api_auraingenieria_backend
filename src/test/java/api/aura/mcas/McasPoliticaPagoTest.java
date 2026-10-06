package api.aura.mcas;

import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.service.mcas.engine.ContextoCalculo;
import api.aura.service.mcas.engine.EstrategiaImporteFijo;
import api.aura.service.mcas.engine.MotorMcas;
import api.aura.service.mcas.engine.RegistroEstrategias;
import api.aura.service.mcas.engine.ResultadoCalculo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Politica de pago de la regla mal escrita.
 *
 * <p>Si politica_pago_json no es JSON valido, el motor se quedaba callado y repartia
 * la comision en un solo pago. El validador lo impide al publicar, pero una regla
 * tocada a mano en base de datos se colaba y nadie se enteraba de que el numero de
 * pagos no era el configurado.</p>
 */
class McasPoliticaPagoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ResultadoCalculo evaluarCon(String politicaPagoJson) {
        McasConjuntoReglasRepository conjuntos = mock(McasConjuntoReglasRepository.class);
        McasReglaComisionRepository reglas = mock(McasReglaComisionRepository.class);
        McasValorRangoRepository rangos = mock(McasValorRangoRepository.class);
        RegistroEstrategias registro = new RegistroEstrategias(List.of(new EstrategiaImporteFijo(mapper)));
        MotorMcas motor = new MotorMcas(conjuntos, reglas, rangos, registro, mapper);

        var conjunto = McasConjuntoReglas.builder().id(40).codigo("POLITICA").idComercializadora(5)
                .vigenciaDesde(LocalDate.of(2026, 1, 1)).build();
        var regla = McasReglaComision.builder().id(41).codigo("FIJA").metodoCalculo("FIXED_AMOUNT")
                .condicionesJson("{}").calculoJson("{\"importe\":100}")
                .politicaPagoJson(politicaPagoJson).build();
        when(conjuntos.findPublicadoFijoPorFecha(eq(5), isNull(), any(), eq("FIJO"))).thenReturn(List.of(conjunto));
        when(reglas.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(40)).thenReturn(List.of(regla));

        return motor.evaluar(ContextoCalculo.builder().idContrato(99).idComercializadora(5)
                .tipoPrecio("FIJO").fechaEfectiva(LocalDate.of(2026, 3, 16)).build());
    }

    @Test
    void una_politica_de_pago_ilegible_deja_aviso_en_la_propuesta() {
        ResultadoCalculo resultado = evaluarCon("{esto no es json}");

        assertThat(resultado.getResultado()).isEqualTo(ResultadoCalculo.Resultado.CALCULADO);
        assertThat(resultado.getNumeroPagos()).isEqualTo(1);
        assertThat(resultado.getAdvertencias())
                .containsExactly("No se ha podido leer el número de pagos de la regla; "
                        + "la comisión se reparte en un solo pago.");
    }

    @Test
    void una_politica_de_pago_correcta_no_genera_avisos() {
        ResultadoCalculo resultado = evaluarCon("{\"numero_pagos_sugerido\":3}");

        assertThat(resultado.getNumeroPagos()).isEqualTo(3);
        assertThat(resultado.getAdvertencias()).isEmpty();
    }
}
