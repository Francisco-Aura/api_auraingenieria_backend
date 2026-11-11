package api.aura.service.modulo_registro_horario.documento;

/** Datos que no cuadran (horas, ningún día trabajado, campos obligatorios...): el mensaje se explica a la persona. */
public class DatosNoValidosException extends RuntimeException {

    public DatosNoValidosException(String mensaje) {
        super(mensaje);
    }
}
