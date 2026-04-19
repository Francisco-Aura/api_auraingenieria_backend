package api.aura.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.persistence.EntityNotFoundException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errores);
    }

    @ExceptionHandler({ IllegalArgumentException.class, EntityNotFoundException.class,
            EmptyResultDataAccessException.class })
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Map<String, String> error = new HashMap<>();
        String mensaje = traducirErrorBD(ex);
        error.put("error", mensaje);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        // Recorrer la cadena de causas buscando errores de BD traducibles
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof DataIntegrityViolationException dive) {
                return handleDataIntegrityViolation(dive);
            }
            String msg = cause.getMessage();
            if (msg != null) {
                if (msg.contains("Data too long") || msg.contains("too long for column")) {
                    String columna = extraerEntre(msg, "column '", "'");
                    String texto = columna != null
                        ? "El valor introducido en el campo '" + traducirColumna(columna) + "' es demasiado largo."
                        : "Uno de los valores introducidos supera la longitud máxima permitida.";
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", texto));
                }
                if (msg.contains("Duplicate entry")) {
                    String valor = extraerEntre(msg, "Duplicate entry '", "' for key '");
                    String clave = extraerEntre(msg, "' for key '", "'");
                    String texto = (clave != null && clave.toLowerCase().contains("cif"))
                        ? "Ya existe un cliente con el CIF '" + valor + "'. El CIF debe ser único."
                        : "Ya existe un registro con esos datos. No se permiten duplicados.";
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", texto));
                }
                if (msg.contains("cannot be null")) {
                    String columna = extraerEntre(msg, "Column '", "' cannot be null");
                    String texto = columna != null
                        ? "El campo '" + traducirColumna(columna) + "' es obligatorio y no puede estar vacío."
                        : "Hay un campo obligatorio que no puede estar vacío.";
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", texto));
                }
            }
            cause = cause.getCause();
        }
        Map<String, String> error = new HashMap<>();
        error.put("error", "Se ha producido un error inesperado. Por favor, inténtelo de nuevo.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // -----------------------------------------------------------------------
    // Traduce excepciones de base de datos a mensajes comprensibles en español
    // -----------------------------------------------------------------------
    private String traducirErrorBD(DataIntegrityViolationException ex) {
        String msg = getRootCauseMessage(ex);
        if (msg == null) return "No se pudo guardar el registro por un conflicto de datos.";

        // --- Entradas duplicadas (UNIQUE constraint) -----------------------
        if (msg.contains("Duplicate entry")) {
            // Extraer el valor duplicado: Duplicate entry 'X' for key 'tabla.columna'
            String valor = extraerEntre(msg, "Duplicate entry '", "' for key '");
            String clave = extraerEntre(msg, "' for key '", "'");
            if (clave != null) {
                String claveMin = clave.toLowerCase();
                if (claveMin.contains("cif"))
                    return "Ya existe un cliente con el CIF '" + valor + "'. El CIF debe ser único.";
                if (claveMin.contains("clientes") && claveMin.contains("nombre"))
                    return "Ya existe un cliente con ese nombre.";
                if (claveMin.contains("distribuidora") || claveMin.contains("tbdistribuidora"))
                    return "Ya existe una distribuidora con ese nombre.";
                if (claveMin.contains("propiedad") || claveMin.contains("tbpropiedad"))
                    return "Ya existe un tipo de propiedad con ese nombre.";
                if (claveMin.contains("tipocontador") || claveMin.contains("tbtipocontador"))
                    return "Ya existe un tipo de contador con ese nombre.";
                if (claveMin.contains("tipocomunicacion") || claveMin.contains("tbtipocomunicacion"))
                    return "Ya existe un tipo de comunicación con ese nombre.";
                if (claveMin.contains("categoria") && claveMin.contains("codigo"))
                    return "Ya existe una categoría con ese código.";
                if (claveMin.contains("categoria"))
                    return "Ya existe una categoría con ese nombre.";
                if (claveMin.contains("subcategoria"))
                    return "Ya existe una subcategoría con ese nombre.";
                if (claveMin.contains("tareaestado") || claveMin.contains("mt_tareaestado"))
                    return "Ya existe un estado de tarea con ese nombre.";
                if (claveMin.contains("moduloconfiguracion") || claveMin.contains("modulo_configuracion"))
                    return "Ya existe un módulo con ese nombre.";
            }
            return valor != null
                ? "Ya existe un registro con el valor '" + valor + "'. No se permiten duplicados."
                : "Ya existe un registro con esos datos. No se permiten duplicados.";
        }

        // --- Campo nulo en columna NOT NULL --------------------------------
        if (msg.contains("cannot be null") || msg.contains("Column") && msg.contains("null")) {
            String columna = extraerEntre(msg, "Column '", "' cannot be null");
            if (columna == null) columna = extraerEntre(msg, "column '", "'");
            if (columna != null) {
                return "El campo '" + traducirColumna(columna) + "' es obligatorio y no puede estar vacío.";
            }
            return "Hay un campo obligatorio que no puede estar vacío.";
        }

        // --- Dato demasiado largo -------------------------------------------
        if (msg.contains("Data too long") || msg.contains("too long for column")) {
            String columna = extraerEntre(msg, "column '", "'");
            if (columna != null) {
                return "El valor introducido en el campo '" + traducirColumna(columna) + "' es demasiado largo.";
            }
            return "Uno de los valores introducidos supera la longitud máxima permitida.";
        }

        // --- Clave foránea (FK) violada -------------------------------------
        if (msg.contains("foreign key constraint") || msg.contains("FOREIGN KEY")) {
            if (msg.contains("Referenc") || msg.contains("REFERENCES")) {
                return "No se puede eliminar este registro porque está siendo utilizado por otros datos del sistema.";
            }
            return "No se pudo guardar porque hay una referencia a un dato que no existe.";
        }

        // --- Valor fuera de rango (overflow numérico) ----------------------
        if (msg.contains("Out of range") || msg.contains("out of range")) {
            return "Uno de los valores numéricos está fuera del rango permitido.";
        }

        // --- Formato de fecha incorrecto -----------------------------------
        if (msg.contains("Incorrect date") || msg.contains("Incorrect datetime")) {
            return "El formato de una de las fechas no es válido.";
        }

        // --- Fallback genérico (nunca expone técnico) ----------------------
        return "No se pudo guardar el registro por un conflicto de datos. Revise los valores introducidos.";
    }

    private String getRootCauseMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    private String extraerEntre(String texto, String inicio, String fin) {
        if (texto == null) return null;
        int i = texto.indexOf(inicio);
        if (i < 0) return null;
        i += inicio.length();
        int j = texto.indexOf(fin, i);
        if (j < 0) return null;
        return texto.substring(i, j);
    }

    private String traducirColumna(String columna) {
        Map<String, String> nombres = new HashMap<>();
        nombres.put("cif", "CIF");
        nombres.put("nombre", "Nombre");
        nombres.put("direccion", "Dirección");
        nombres.put("poblacion", "Población");
        nombres.put("provincia", "Provincia");
        nombres.put("codigoPostal", "Código Postal");
        nombres.put("iban", "IBAN");
        nombres.put("email", "Email");
        nombres.put("telefono", "Teléfono");
        nombres.put("fechaInicio", "Fecha de inicio");
        nombres.put("fechaFin", "Fecha de fin");
        nombres.put("fechaEnvio", "Fecha de envío");
        nombres.put("contrato", "Número de contrato");
        nombres.put("cupsluz", "CUPS Luz");
        nombres.put("cupsgas", "CUPS Gas");
        nombres.put("codigo", "Código");
        nombres.put("titulo", "Título");
        nombres.put("descripcion", "Descripción");
        return nombres.getOrDefault(columna, columna);
    }
}
