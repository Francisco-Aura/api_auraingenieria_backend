package api.aura.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Errores del Planificador con el formato que ya espera su pantalla (el del Planificador anterior, FastAPI): {"detail": "..."}.
 * Solo afecta a los controladores del módulo; el resto de la intranet sigue con GlobalExceptionHandler ({"error": "..."}).
 */
@RestControllerAdvice(basePackages = "api.aura.controller.modulo_planificador")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlanificadorExceptionHandler {

    @ExceptionHandler(ErrorApi.class)
    public ResponseEntity<Map<String, String>> errorApi(ErrorApi e) {
        return ResponseEntity.status(e.getEstado()).body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> peticionIncorrecta(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> noValido(MethodArgumentNotValidException e) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("Datos no válidos");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("detail", mensaje));
    }
}
