package api.aura.service.modulo_planificador;

/** No se pudo entregar un aviso a la intranet. */
public class AvisoError extends RuntimeException {

    public AvisoError(String mensaje) {
        super(mensaje);
    }
}
