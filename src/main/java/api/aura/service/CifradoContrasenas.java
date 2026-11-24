package api.aura.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Cifrado reversible (AES-256-GCM) de las contraseñas de la tabla Usuarios.
 *
 * <p>Requisito del cliente: los administradores consultan las contraseñas de todos
 * para poder decírselas. Por eso no se usa un hash (irreversible) sino un cifrado
 * con una clave de la aplicación: la pantalla de Administración las sigue mostrando
 * como siempre, pero ya no quedan en claro en la base de datos ni en sus copias.</p>
 *
 * <p>Formato guardado: {@value #PREFIJO} + Base64(iv de 12 bytes + texto cifrado +
 * etiqueta de 16 bytes). Cabe en la columna {@code pass} VARCHAR(100) para
 * contraseñas de hasta {@value #MAX_BYTES} bytes.</p>
 */
@Component
public class CifradoContrasenas {

    static final String PREFIJO = "{c}";
    /** Máximo que cabe cifrado en VARCHAR(100): 3 + Base64(12 + 44 + 16 = 72 bytes) = 99. */
    static final int MAX_BYTES = 44;
    private static final int IV = 12;
    private static final int ETIQUETA_BITS = 128;

    private final SecretKeySpec clave;
    /** Para las huellas de sesión: sacada de la de cifrado, nunca la misma clave para dos cosas. */
    private final SecretKeySpec claveHuella;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradoContrasenas(@Value("${aura.seguridad.clave-contrasenas}") String claveBase64) {
        byte[] bytes = Base64.getDecoder().decode(claveBase64.trim());
        if (bytes.length != 32)
            throw new IllegalStateException("aura.seguridad.clave-contrasenas debe ser una clave AES de 32 bytes en Base64");
        this.clave = new SecretKeySpec(bytes, "AES");
        this.claveHuella = new SecretKeySpec(hmac(new SecretKeySpec(bytes, "HmacSHA256"), "aura-huella-sesion"), "HmacSHA256");
    }

    public boolean estaCifrada(String guardada) {
        return guardada != null && guardada.startsWith(PREFIJO);
    }

    /** true si la contraseña cabe cifrada en la columna. */
    public boolean cabe(String plano) {
        return plano.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    public String cifrar(String plano) {
        if (!cabe(plano))
            throw new IllegalArgumentException("La contraseña no puede superar los " + MAX_BYTES + " caracteres.");
        try {
            byte[] iv = new byte[IV];
            aleatorio.nextBytes(iv);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(ETIQUETA_BITS, iv));
            byte[] cifrado = cifrador.doFinal(plano.getBytes(StandardCharsets.UTF_8));
            return PREFIJO + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(IV + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar la contraseña", e);
        }
    }

    /**
     * Texto de una contraseña guardada: la descifra si está cifrada y devuelve tal
     * cual las que aún estén en claro (anteriores a esta versión).
     *
     * @throws IllegalStateException si está cifrada con otra clave o dañada
     */
    public String descifrar(String guardada) {
        if (!estaCifrada(guardada)) return guardada;
        try {
            byte[] datos = Base64.getDecoder().decode(guardada.substring(PREFIJO.length()));
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(ETIQUETA_BITS, datos, 0, IV));
            return new String(cifrador.doFinal(datos, IV, datos.length - IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new IllegalStateException("Contraseña cifrada ilegible (¿clave distinta?)", e);
        }
    }

    /**
     * Huella corta de una contraseña guardada (nunca la contraseña), para saber si ha
     * cambiado desde que se abrió una sesión. Sale de la contraseña descifrada: cifrarla
     * al arrancar, al entrar o volver a cifrarla (cada vez da un texto distinto) no la
     * cambia, y así no cierra ninguna sesión. Es un HMAC con la clave de la aplicación,
     * no un hash simple, porque se guarda en los ficheros de sesión: sin la clave no
     * sirve para adivinar la contraseña probando.
     */
    public String huella(String guardada) {
        if (guardada == null) return "";
        String plano;
        try {
            plano = descifrar(guardada);
        } catch (IllegalStateException ilegible) {
            // Con otra clave o dañada: la guardada tal cual, que no cambia mientras no la cambien
            plano = guardada;
        }
        return HexFormat.of().formatHex(hmac(claveHuella, plano), 0, 8);
    }

    private static byte[] hmac(SecretKeySpec claveHmac, String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(claveHmac);
            return mac.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
