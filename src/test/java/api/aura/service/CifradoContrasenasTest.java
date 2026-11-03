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

    @Test
    void la_huella_no_cambia_al_cifrar_ni_al_volver_a_cifrar_la_misma_contrasena() {
        // El filtro de sesión la compara cada minuto: si cambiara al cifrarlas al arrancar,
        // echaría a todos los que tenían la sesión abierta
        String huella = cifrado.huella("secreto");
        assertThat(cifrado.huella(cifrado.cifrar("secreto"))).isEqualTo(huella);
        assertThat(cifrado.huella(cifrado.cifrar("secreto"))).isEqualTo(huella);
        assertThat(cifrado.huella(cifrado.cifrar("otra"))).isNotEqualTo(huella);
        assertThat(cifrado.huella(null)).isEmpty();
    }

    @Test
    void la_huella_depende_de_la_clave_de_la_aplicacion() {
        // Va en los ficheros de sesión: sin la clave no sirve para adivinar la contraseña probando
        CifradoContrasenas otra = new CifradoContrasenas("+nxbp1sJ9AT6Ps7vZWdClp19z/LcaOfnVl1msedLykY=");
        assertThat(otra.huella("secreto")).isNotEqualTo(cifrado.huella("secreto"));
    }

    @Test
    void una_contrasena_ilegible_tiene_huella_sin_fallar() {
        assertThat(cifrado.huella("{c}no-es-base64!")).isNotEmpty().isEqualTo(cifrado.huella("{c}no-es-base64!"));
    }
}
