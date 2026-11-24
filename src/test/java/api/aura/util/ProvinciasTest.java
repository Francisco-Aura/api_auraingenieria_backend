package api.aura.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Las 52 provincias del desplegable y el reconocimiento de las que se escribieron a mano. */
class ProvinciasTest {

    @Test
    void son_las_52_con_los_codigos_del_codigo_postal_y_salen_por_orden_alfabetico() {
        List<Provincias.Provincia> todas = Provincias.todas();

        assertThat(todas).hasSize(52);
        assertThat(todas).extracting(Provincias.Provincia::codigo)
                .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 52).mapToObj(n -> String.format("%02d", n)).toList());
        assertThat(todas).extracting(Provincias.Provincia::nombre).doesNotHaveDuplicates()
                .startsWith("A Coruña", "Álava", "Albacete", "Alicante", "Almería", "Asturias", "Ávila")
                .endsWith("Zamora", "Zaragoza");
        assertThat(Provincias.nombre("30")).isEqualTo("Murcia");
        assertThat(Provincias.nombre("04")).isEqualTo("Almería");
        assertThat(Provincias.existe("52")).isTrue();
        assertThat(Provincias.existe("53")).isFalse();
        assertThat(Provincias.existe(null)).isFalse();
    }

    @Test
    void reconoce_lo_escrito_a_mano_sin_mirar_mayusculas_tildes_ni_signos() {
        assertThat(Provincias.codigoDe("MURCIA")).contains("30");
        assertThat(Provincias.codigoDe("  murcia ")).contains("30");
        assertThat(Provincias.codigoDe("Región de Murcia")).contains("30");
        assertThat(Provincias.codigoDe("almeria")).contains("04");
        assertThat(Provincias.codigoDe("ALMERÍA.")).contains("04");
        assertThat(Provincias.codigoDe("Alicante/Alacant")).contains("03");
        assertThat(Provincias.codigoDe("Alacant")).contains("03");
        assertThat(Provincias.codigoDe("Vizcaya")).contains("48");
        assertThat(Provincias.codigoDe("Guipúzcoa")).contains("20");
        assertThat(Provincias.codigoDe("La Coruña")).contains("15");
        assertThat(Provincias.codigoDe("Illes Balears")).contains("07");
        assertThat(Provincias.codigoDe("S.C. Tenerife")).contains("38");
        assertThat(Provincias.codigoDe("ciudad  real")).contains("13");
    }

    @Test
    void lo_que_no_es_una_provincia_no_se_inventa() {
        assertThat(Provincias.codigoDe(null)).isEmpty();
        assertThat(Provincias.codigoDe("   ")).isEmpty();
        assertThat(Provincias.codigoDe("Mursia")).isEmpty();
        assertThat(Provincias.codigoDe("Cuevas del Almanzora")).isEmpty();
        assertThat(Provincias.codigoDe("30")).isEmpty();
    }

    @Test
    void al_guardar_se_deja_el_nombre_del_desplegable_y_lo_desconocido_tal_cual() {
        assertThat(Provincias.nombreOficial("MURCIA")).isEqualTo("Murcia");
        assertThat(Provincias.nombreOficial("valència")).isEqualTo("Valencia");
        assertThat(Provincias.nombreOficial("  Mursia ")).isEqualTo("Mursia");
        assertThat(Provincias.nombreOficial("")).isEmpty();
        assertThat(Provincias.nombreOficial(null)).isNull();
    }
}
