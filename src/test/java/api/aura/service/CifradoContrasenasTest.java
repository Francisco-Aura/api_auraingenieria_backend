package api.aura.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CifradoContrasenasTest {

    private final CifradoContrasenas cifrado = UsuarioActualizacionTest.CIFRADO;

    @Test
    void cifra_y_descifra_y_cabe_en_la_columna() {
        String max = "ñ".repeat(22); // 44 bytes en UTF-8
        String guardada = cifrado.cifrar(max);
        assertThat(guardada).startsWith("{c}").hasSizeLessThanOrEqualTo(100);
        assertThat(cifrado.descifrar(guardada)).isEqualTo(max);
        // Cada cifrado es distinto (IV aleatorio): dos usuarios con la misma clave no se delatan
        assertThat(cifrado.cifrar("igual")).isNotEqualTo(cifrado.cifrar("igual"));
    }

    @Test
    void las_que_estan_en_claro_se_devuelven_tal_cual() {
        assertThat(cifrado.estaCifrada("antigua")).isFalse();
        assertThat(cifrado.descifrar("antigua")).isEqualTo("antigua");
    }

    @Test
    void con_otra_clave_o_dañada_no_se_puede_leer() {
        String guardada = cifrado.cifrar("secreto");
        CifradoContrasenas otra = new CifradoContrasenas("+nxbp1sJ9AT6Ps7vZWdClp19z/LcaOfnVl1msedLykY=");
        assertThatThrownBy(() -> otra.descifrar(guardada)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifrado.descifrar("{c}no-es-base64!")).isInstanceOf(IllegalStateException.class);
    }
}
