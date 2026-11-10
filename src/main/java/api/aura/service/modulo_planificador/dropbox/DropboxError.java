package api.aura.service.modulo_planificador.dropbox;

/** Fallo al hablar con Dropbox. El mensaje nunca incluye claves ni tokens. */
public class DropboxError extends RuntimeException {

    private final Integer estado;
    private final String resumen;

    public DropboxError(String mensaje) {
        this(mensaje, null, "");
    }

    public DropboxError(String mensaje, Integer estado) {
        this(mensaje, estado, "");
    }

    public DropboxError(String mensaje, Integer estado, String resumen) {
        super(mensaje);
        this.estado = estado;
        this.resumen = resumen == null ? "" : resumen;
    }

    /** Código HTTP que devolvió Dropbox (null si ni siquiera hubo respuesta). */
    public Integer getEstado() {
        return estado;
    }

    /** `error_summary` de Dropbox, p. ej. «path/not_found/...». */
    public String getResumen() {
        return resumen;
    }
}
