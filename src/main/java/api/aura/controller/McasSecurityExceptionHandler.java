package api.aura.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduce las denegaciones de acceso de MCAS a 403 con su motivo.
 *
 * <p>Debe evaluarse antes que {@code GlobalExceptionHandler}: Spring usa el
 * primer advice que tenga algún manejador compatible y el genérico de
 * {@code Exception} de aquel convertiría la denegación en un 500 "error
 * inesperado".</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class McasSecurityExceptionHandler {
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> forbidden(SecurityException e) {
        return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }
}
