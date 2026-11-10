package api.aura.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

/**
 * Errores del Registro Horario con el formato del servicio anterior (FastAPI): {"detail": "..."} y 422 en los datos no válidos.
 * Solo afecta a los controladores del módulo. El mensaje nunca lleva datos de la petición.
 */
@RestControllerAdvice(basePackages = "api.aura.controller.modulo_registro_horario")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RegistroHorarioExceptionHandler {

    @ExceptionHandler(ErrorApi.class)
    public ResponseEntity<Map<String, String>> errorApi(ErrorApi e) {
        return ResponseEntity.status(e.getEstado()).body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, String>> noValido(Exception e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("detail", "Hay un dato no válido."));
    }
}
