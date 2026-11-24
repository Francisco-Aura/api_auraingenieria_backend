package api.aura.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Nombres de archivo subidos y ruta del PDF del contrato que sale al navegador. */
class NombresArchivoTest {

    @Test
    void un_nombre_con_saltos_de_linea_no_puede_colar_lineas_en_el_log() {
        assertThat(NombresArchivo.sinControl("informe\n2026-10-15 ERROR falso\r.pdf"))
                .isEqualTo("informe2026-10-15 ERROR falso.pdf");
        assertThat(NombresArchivo.sinControl("acta\u2028.pdf")).isEqualTo("acta.pdf");
        assertThat(NombresArchivo.sinControl("Acta; reunión.pdf")).isEqualTo("Acta; reunión.pdf");
        assertThat(NombresArchivo.sinControl(null)).isNull();
    }

    @Test
    void la_ruta_del_contrato_sale_solo_con_el_nombre_del_archivo() {
        assertThat(SoloNombreArchivoSerializer.soloNombre("/home/aura/contratos/ACME_B123/contrato_7.pdf"))
                .isEqualTo("contrato_7.pdf");
        assertThat(SoloNombreArchivoSerializer.soloNombre("C:\\aura\\contratos\\c.pdf")).isEqualTo("c.pdf");
        assertThat(SoloNombreArchivoSerializer.soloNombre(null)).isNull();
        assertThat(SoloNombreArchivoSerializer.soloNombre("")).isEmpty();
    }

    @Test
    void en_el_json_del_contrato_no_va_la_ruta_del_servidor() throws Exception {
        api.aura.entity.Contrato contrato = new api.aura.entity.Contrato();
        contrato.setRutaArchivoContrato("/home/aura/contratos/ACME_B123/contrato_7.pdf");
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(contrato);

        assertThat(json).contains("\"rutaArchivoContrato\":\"contrato_7.pdf\"").doesNotContain("/home/aura");
    }
}
