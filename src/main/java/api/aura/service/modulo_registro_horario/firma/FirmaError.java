package api.aura.service.modulo_registro_horario.firma;

import api.aura.config.ErrorApi;
import org.springframework.http.HttpStatus;

/** Problema con la imagen de la firma, con un mensaje apto para mostrar a la persona (se devuelve como 422). */
public class FirmaError extends ErrorApi {

    public FirmaError(String mensaje) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
}
