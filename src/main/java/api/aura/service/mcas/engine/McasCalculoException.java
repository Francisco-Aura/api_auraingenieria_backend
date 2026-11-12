package api.aura.service.mcas.engine;

/** Error de configuración del motor (reglas solapadas, JSON inválido, etc.) */
public class McasCalculoException extends RuntimeException {
    public McasCalculoException(String msg) { super(msg); }
}

