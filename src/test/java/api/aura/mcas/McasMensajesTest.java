package api.aura.mcas;

import api.aura.service.mcas.McasMensajes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Textos del motor MCAS traducidos para quien graba el contrato o valida la
 * propuesta (plan de usabilidad del 30/09/2026): sin códigos internos, en usted y
 * con tildes. La causa técnica sigue en la traza y en la auditoría.
 */
class McasMensajesTest {

    // ============================================================
    // campo · listaCampos · faltanDatos
    // ============================================================

    @Test
    void campo_traduce_los_codigos_tecnicos_a_etiquetas() {
        assertThat(McasMensajes.campo("consumo_kwh")).isEqualTo("Consumo anual");
        assertThat(McasMensajes.campo("consumo_mwh")).isEqualTo("Consumo anual");
        assertThat(McasMensajes.campo("consumo_anual")).isEqualTo("Consumo anual");
        assertThat(McasMensajes.campo("fee")).isEqualTo("FEE del contrato");
        assertThat(McasMensajes.campo("FEE")).isEqualTo("FEE del contrato");
        assertThat(McasMensajes.campo("potencia_kw")).isEqualTo("Potencias contratadas (P1 a P6)");
        assertThat(McasMensajes.campo("tipo_precio_FIJO_o_INDEXADO")).isEqualTo("Tipo de precio");
        assertThat(McasMensajes.campo("tipo_suministro_LUZ_o_GAS")).isEqualTo("Tipo de suministro");
        assertThat(McasMensajes.campo("codigo_producto")).isEqualTo("Tarifa de precios (producto)");
        assertThat(McasMensajes.campo("zona_comision")).isEqualTo("Zona de comisión");
        assertThat(McasMensajes.campo("ambito")).isEqualTo("Ámbito");
        assertThat(McasMensajes.campo("tarifa_acceso")).isEqualTo("Tarifa de acceso");
        assertThat(McasMensajes.campo("es_renovacion")).isEqualTo("Renovación");
        assertThat(McasMensajes.campo("tipo_renovacion")).isEqualTo("Tipo de renovación");
        assertThat(McasMensajes.campo("numero_contrato")).isEqualTo("Número de contrato");
        assertThat(McasMensajes.campo("fecha_inicio")).isEqualTo("Fecha de inicio");
        assertThat(McasMensajes.campo("consumo_anual_no_negativo")).isEqualTo("Consumo anual (no puede ser negativo)");
        assertThat(McasMensajes.campo("numero_pagos_1_a_12")).isEqualTo("Número de pagos (entre 1 y 12)");
    }

    @Test
    void campo_acepta_el_nombre_del_enum_McasCampo() {
        assertThat(McasMensajes.campo("CONSUMO")).isEqualTo("Consumo anual");
        assertThat(McasMensajes.campo("ZONA_COMISION")).isEqualTo("Zona de comisión");
        assertThat(McasMensajes.campo("GESTORA")).isEqualTo("Gestora");
    }

    @Test
    void un_codigo_desconocido_se_muestra_legible_y_sin_guiones_bajos() {
        assertThat(McasMensajes.campo("dato_nuevo_del_motor")).isEqualTo("Dato nuevo del motor");
        assertThat(McasMensajes.campo(null)).isEmpty();
    }

    @Test
    void listaCampos_devuelve_etiquetas_sin_repetir_y_en_orden() {
        assertThat(McasMensajes.listaCampos(List.of("consumo_kwh", "fee", "consumo_mwh", "segmento")))
                .containsExactly("Consumo anual", "FEE del contrato", "Segmento");
        assertThat(McasMensajes.listaCampos(List.of())).isEmpty();
        assertThat(McasMensajes.listaCampos(null)).isEmpty();
    }

    @Test
    void faltanDatos_redacta_la_frase_en_singular_y_en_plural() {
        assertThat(McasMensajes.faltanDatos(List.of("fee")))
                .isEqualTo("Falta un dato para calcular la comisión: FEE del contrato.");
        assertThat(McasMensajes.faltanDatos(List.of("consumo_kwh", "fee", "segmento")))
                .isEqualTo("Faltan datos para calcular la comisión: consumo anual, FEE del contrato y segmento.");
        // consumo en kWh y en MWh son el mismo dato para el usuario
        assertThat(McasMensajes.faltanDatos(List.of("consumo_kwh", "consumo_mwh")))
                .isEqualTo("Falta un dato para calcular la comisión: consumo anual.");
        assertThat(McasMensajes.faltanDatos(List.of())).isEqualTo("Faltan datos para calcular la comisión.");
    }

    // ============================================================
    // paraOperador: un caso por formato técnico conocido
    // ============================================================

    @Test
    void datos_minimos_del_contrato() {
        assertThat(McasMensajes.paraOperador("Faltan o son invalidos: tipo_precio_FIJO_o_INDEXADO, consumo_anual_no_negativo"))
                .isEqualTo("Complete o corrija estos datos del contrato: tipo de precio y consumo anual (no puede ser negativo).");
    }

    @Test
    void modulo_desactivado() {
        assertThat(McasMensajes.paraOperador("MCAS_ENABLED=false"))
                .isEqualTo("El cálculo automático de comisiones está desactivado; la comisión se revisará manualmente.");
    }

    @Test
    void fase_fija_desactivada() {
        assertThat(McasMensajes.paraOperador("Fase FIJO no habilitada (MCAS_FIXED_ENABLED=false)"))
                .isEqualTo("El cálculo automático de los contratos a precio fijo está desactivado; "
                        + "la comisión se revisará manualmente.");
    }

    @Test
    void indexados_desactivados() {
        assertThat(McasMensajes.paraOperador("Contratos INDEXADO no habilitados (MCAS_INDEXED_ENABLED=false)"))
                .isEqualTo("El cálculo automático de los contratos indexados aún no está activado; "
                        + "la comisión se revisará manualmente.");
    }

    @Test
    void sin_tabla_publicada_no_muestra_el_id_de_la_comercializadora() {
        String texto = McasMensajes.paraOperador(
                "Sin conjunto de reglas PUBLICADO para comercializadora=37 fecha=2026-03-16");
        assertThat(texto).isEqualTo("Esta comercializadora no tiene una tabla de comisiones activa en la fecha "
                + "del contrato (16/03/2026); la comisión se revisará manualmente.");
        assertThat(texto).doesNotContain("37").doesNotContain("comercializadora=");

        assertThat(McasMensajes.paraOperador("Sin conjunto de reglas PUBLICADO para comercializadora=37 fecha=null"))
                .isEqualTo("Esta comercializadora no tiene una tabla de comisiones activa en la fecha "
                        + "del contrato; la comisión se revisará manualmente.");
    }

    @Test
    void varias_tablas_publicadas_a_la_vez() {
        assertThat(McasMensajes.paraOperador("2 conjuntos PUBLICADOS coinciden con igual especificidad [A-1, A-2]"))
                .isEqualTo("Esta comercializadora tiene varias tablas de comisiones activas a la vez; "
                        + "avise al administrador para que lo revise.");
    }

    @Test
    void ninguna_regla_coincide_sin_codigo_de_conjunto() {
        String texto = McasMensajes.paraOperador("Ninguna regla coincide en conjunto 'IRIS-FIJO-2026'");
        assertThat(texto).isEqualTo("Ninguna regla de la tabla de comisiones se aplica a este contrato; "
                + "la comisión se revisará manualmente.");
        assertThat(texto).doesNotContain("IRIS-FIJO-2026");
    }

    @Test
    void solapamiento_de_reglas() {
        String texto = McasMensajes.paraOperador(
                "Solapamiento: 2 reglas coinciden [IRIS-PYME, IRIS-GEN] — revisar configuración");
        assertThat(texto).isEqualTo("Varias reglas de la tabla de comisiones se aplican a la vez a este contrato; "
                + "avise al administrador para que revise la tabla.");
        assertThat(texto).doesNotContain("IRIS-PYME");
    }

    @Test
    void faltan_datos_requeridos_por_las_reglas() {
        assertThat(McasMensajes.paraOperador("Faltan datos requeridos por las reglas: consumo_kwh, segmento"))
                .isEqualTo("Faltan datos para calcular la comisión: consumo anual y segmento.");
    }

    @Test
    void dato_requerido_por_una_tabla() {
        assertThat(McasMensajes.paraOperador("MATRIX_LOOKUP: dato requerido 'potencia_kw'"))
                .isEqualTo("Falta un dato para calcular la comisión: potencias contratadas (P1 a P6).");
        assertThat(McasMensajes.paraOperador("RANGE_LOOKUP: dato requerido 'zona_comision'"))
                .isEqualTo("Falta un dato para calcular la comisión: zona de comisión.");
        // La matriz pide a la vez todas las dimensiones que faltan.
        assertThat(McasMensajes.paraOperador("MATRIX_LOOKUP: dato requerido 'consumo_kwh', 'potencia_kw'"))
                .isEqualTo("Faltan datos para calcular la comisión: consumo anual y potencias contratadas (P1 a P6).");
    }

    @Test
    void dato_requerido_por_una_estrategia() {
        assertThat(McasMensajes.paraOperador("FEE_PERCENTAGE: FEE del contrato requerido"))
                .isEqualTo("Falta un dato para calcular la comisión: FEE del contrato.");
        assertThat(McasMensajes.paraOperador("FEE_PERCENTAGE: consumo_anual requerido"))
                .isEqualTo("Falta un dato para calcular la comisión: consumo anual.");
        assertThat(McasMensajes.paraOperador("RATE_PER_MWH: consumo_anual requerido"))
                .isEqualTo("Falta un dato para calcular la comisión: consumo anual.");
        assertThat(McasMensajes.paraOperador("COMPOSITE_SUM: FEE requerido"))
                .isEqualTo("Falta un dato para calcular la comisión: FEE del contrato.");
    }

    @Test
    void conjunto_sin_reglas_activas() {
        assertThat(McasMensajes.paraOperador("Conjunto 'VISALIA-INDEX' sin reglas activas"))
                .isEqualTo("La tabla de comisiones de esta comercializadora no tiene reglas activas; "
                        + "la comisión se revisará manualmente.");
    }

    @Test
    void valor_fuera_de_la_tabla_sin_codigo_de_regla() {
        String texto = McasMensajes.paraOperador("Consumo anual 250.000 kWh y potencia 45 kW fuera de la tabla "
                + "de la regla ENDESA-PYME-30TD: complete la comisión manualmente");
        assertThat(texto).isEqualTo("Consumo anual 250.000 kWh y potencia 45 kW fuera de la tabla de comisiones: "
                + "complete la comisión manualmente.");
        assertThat(texto).doesNotContain("ENDESA-PYME-30TD");
    }

    @Test
    void errores_de_configuracion_de_la_regla() {
        String configuracion = "La tabla de comisiones tiene un error de configuración; "
                + "avise al administrador para que la revise.";
        assertThat(McasMensajes.paraOperador("MATRIX_LOOKUP: 2 celdas coinciden; configuracion ambigua"))
                .isEqualTo(configuracion);
        assertThat(McasMensajes.paraOperador("FIXED_AMOUNT: error en calculo_json — Unexpected token"))
                .isEqualTo(configuracion);
        assertThat(McasMensajes.paraOperador("Estrategia no registrada: 'OTRA'")).isEqualTo(configuracion);
        assertThat(McasMensajes.paraOperador("dimension de lookup no soportada: 'cnae'")).isEqualTo(configuracion);
    }

    @Test
    void error_interno() {
        assertThat(McasMensajes.paraOperador("Error interno: NullPointerException"))
                .isEqualTo("No se ha podido calcular la comisión por un error inesperado; avise al administrador.");
    }

    @Test
    void textos_por_defecto_de_las_reglas_manual_y_no_comisionable() {
        assertThat(McasMensajes.paraOperador("Regla marcada como MANUAL"))
                .isEqualTo("La tabla de comisiones indica que esta comisión se completa manualmente.");
        assertThat(McasMensajes.paraOperador("Este caso no genera comision"))
                .isEqualTo("Según la tabla de comisiones, este contrato no genera comisión.");
    }

    @Test
    void politica_de_pago_ilegible() {
        assertThat(McasMensajes.paraOperador(
                "La politica de pago de la regla IRIS-PYME no se puede leer; se reparte en un solo pago"))
                .isEqualTo("No se ha podido leer el número de pagos de la regla; la comisión se reparte en un solo pago.");
    }

    // ============================================================
    // Idempotencia y textos que ya son humanos
    // ============================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "Faltan o son invalidos: tipo_precio_FIJO_o_INDEXADO, fee_no_negativo",
            "MCAS_ENABLED=false",
            "Fase FIJO no habilitada (MCAS_FIXED_ENABLED=false)",
            "Contratos INDEXADO no habilitados (MCAS_INDEXED_ENABLED=false)",
            "Sin conjunto de reglas PUBLICADO para comercializadora=37 fecha=2026-03-16",
            "2 conjuntos PUBLICADOS coinciden con igual especificidad [A-1, A-2]",
            "Ninguna regla coincide en conjunto 'IRIS-FIJO-2026'",
            "Solapamiento: 2 reglas coinciden [IRIS-PYME, IRIS-GEN] — revisar configuración",
            "Faltan datos requeridos por las reglas: consumo_kwh, segmento",
            "MATRIX_LOOKUP: dato requerido 'potencia_kw'",
            "FEE_PERCENTAGE: FEE del contrato requerido",
            "Conjunto 'VISALIA-INDEX' sin reglas activas",
            "Consumo anual 250.000 kWh fuera de la tabla de la regla IBE-01: complete la comisión manualmente",
            "MATRIX_LOOKUP: 2 celdas coinciden; configuracion ambigua",
            "Error interno: NullPointerException",
            "Regla marcada como MANUAL",
            "Este caso no genera comision",
            "La politica de pago de la regla IRIS-PYME no se puede leer; se reparte en un solo pago"
    })
    void traducir_dos_veces_da_lo_mismo_que_una(String causaTecnica) {
        String una = McasMensajes.paraOperador(causaTecnica);
        assertThat(una).isNotEqualTo(causaTecnica);
        assertThat(McasMensajes.paraOperador(una)).isEqualTo(una);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "El contrato ya tiene una comisión aprobada (propuesta #5, servicio #9). Revise el servicio existente.",
            "FEE del contrato 4 €/MWh por debajo del mínimo 5 €/MWh de la regla: complete la comisión manualmente",
            "Renovación tácita: la comercializadora no paga comisión.",
            ""
    })
    void un_texto_que_no_reconoce_pasa_sin_cambios(String humano) {
        assertThat(McasMensajes.paraOperador(humano)).isEqualTo(humano);
    }

    @Test
    void sin_causa_no_hay_mensaje() {
        assertThat(McasMensajes.paraOperador(null)).isNull();
    }

    // ============================================================
    // Importes y campos de las causas antiguas (BE-Operador)
    // ============================================================

    @Test
    void importe_en_formato_espanol_con_los_pagos() {
        assertThat(McasMensajes.importeEnPagos(new java.math.BigDecimal("571.26"), 1)).isEqualTo("571,26 € en 1 pago");
        assertThat(McasMensajes.importeEnPagos(new java.math.BigDecimal("1234.5"), 12)).isEqualTo("1.234,50 € en 12 pagos");
        assertThat(McasMensajes.importeEnPagos(new java.math.BigDecimal("40"), null)).isEqualTo("40,00 € en 1 pago");
        assertThat(McasMensajes.importe(new java.math.BigDecimal("0.5"))).isEqualTo("0,50 €");
    }

    @Test
    void camposDe_extrae_los_codigos_de_las_causas_tecnicas() {
        assertThat(McasMensajes.camposDe("Faltan o son invalidos: tipo_precio_FIJO_o_INDEXADO, fee_no_negativo"))
                .containsExactly("tipo_precio_FIJO_o_INDEXADO", "fee_no_negativo");
        assertThat(McasMensajes.camposDe("Faltan datos requeridos por las reglas: consumo_kwh, segmento"))
                .containsExactly("consumo_kwh", "segmento");
        assertThat(McasMensajes.camposDe("MATRIX_LOOKUP: dato requerido 'potencia_kw'")).containsExactly("potencia_kw");
        assertThat(McasMensajes.camposDe("MATRIX_LOOKUP: dato requerido 'consumo_kwh', 'potencia_kw'"))
                .containsExactly("consumo_kwh", "potencia_kw");
        assertThat(McasMensajes.camposDe("FEE_PERCENTAGE: FEE del contrato requerido")).containsExactly("FEE del contrato");
        assertThat(McasMensajes.camposDe("Ninguna regla coincide en conjunto 'X'")).isEmpty();
        assertThat(McasMensajes.camposDe(null)).isEmpty();
    }

    @Test
    void retrocesion_mal_configurada_en_texto_humano_tambien_en_propuestas_antiguas() {
        assertThat(McasMensajes.paraOperador("Sin politica de retrocesion asignada: 2 politicas de retrocesion "
                + "PUBLICADAS coinciden con igual especificidad")).isEqualTo("No se ha podido mostrar la retrocesión "
                + "potencial: hay varias políticas de retrocesión publicadas que encajan con este contrato. "
                + "Avise al administrador para que lo revise.");
        assertThat(McasMensajes.paraOperador("Sin politica de retrocesion asignada: NullPointerException"))
                .isEqualTo("No se ha podido mostrar la retrocesión potencial. Avise al administrador para que lo revise.");
    }

    @Test
    void estado_de_la_propuesta_con_la_etiqueta_de_la_pantalla() {
        assertThat(McasMensajes.estadoPropuesta("PENDIENTE_VALIDACION")).isEqualTo("Pendiente de validación");
        assertThat(McasMensajes.estadoPropuesta("REQUIERE_REVISION_MANUAL")).isEqualTo("Revisión manual");
        assertThat(McasMensajes.estadoPropuesta("FINALIZADO_EDITADO")).isEqualTo("Aprobada con edición");
        assertThat(McasMensajes.estadoPropuesta("OTRO_ESTADO")).isEqualTo("Otro estado");
        assertThat(McasMensajes.estadoPropuesta(null)).isEqualTo("");
    }
}
