package api.aura.config;

import org.springframework.http.HttpStatus;

/** Error con código HTTP y mensaje; se devuelve como {"detail": "..."} igual que el Planificador actual. */
public class ErrorApi extends RuntimeException {

    private final HttpStatus estado;

    public ErrorApi(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
