package api.aura.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * Petición cuyo cuerpo no se puede leer más allá de {@link #MAXIMO} bytes (al
 * pasarse, la lectura falla con {@link IOException}).
 *
 * <p>La aplica {@link AuraUserFilter} a las peticiones sin sesión: lo único que
 * leen es el login, de unos bytes. Sin este tope, cualquiera en la red podía mandar
 * al login (o en un PUT/PATCH/DELETE de formulario, que Spring lee antes de mirar la
 * sesión) un cuerpo de cientos de megas que se cargaba entero en memoria.</p>
 */
final class CuerpoAcotado extends HttpServletRequestWrapper {

    /** Muy por encima de cualquier login (usuario y contraseña). */
    static final int MAXIMO = 64 * 1024;

    private ServletInputStream flujo;
    private BufferedReader lector;

    CuerpoAcotado(HttpServletRequest request) {
        super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (flujo == null) flujo = new Acotado(super.getInputStream());
        return flujo;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (lector == null) {
            String juego = getCharacterEncoding();
            Charset charset;
            try {
                charset = juego == null ? StandardCharsets.ISO_8859_1 : Charset.forName(juego);
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                throw new UnsupportedEncodingException(juego);
            }
            lector = new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
        return lector;
    }

    private static final class Acotado extends ServletInputStream {
        private final ServletInputStream origen;
        private long leidos;

        Acotado(ServletInputStream origen) {
            this.origen = origen;
        }

        @Override
        public int read() throws IOException {
            int b = origen.read();
            if (b >= 0) contar(1);
            return b;
        }

        @Override
        public int read(byte[] destino, int desde, int cuantos) throws IOException {
            int n = origen.read(destino, desde, cuantos);
            if (n > 0) contar(n);
            return n;
        }

        private void contar(int n) throws IOException {
            leidos += n;
            if (leidos > MAXIMO)
                throw new IOException("El cuerpo de la petición supera el máximo admitido sin sesión");
        }

        @Override
        public boolean isFinished() {
            return origen.isFinished();
        }

        @Override
        public boolean isReady() {
            return origen.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            origen.setReadListener(listener);
        }
    }
}
