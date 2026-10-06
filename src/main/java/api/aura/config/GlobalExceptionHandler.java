package api.aura.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import jakarta.persistence.EntityNotFoundException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_INESPERADO = "Se ha producido un error inesperado. Por favor, inténtelo de nuevo.";

    private static final Map<String, String> NOMBRES_COLUMNAS = Map.ofEntries(
            Map.entry("cif", "CIF"),
            Map.entry("nombre", "Nombre"),
            Map.entry("direccion", "Dirección"),
            Map.entry("poblacion", "Población"),
            Map.entry("provincia", "Provincia"),
            Map.entry("codigoPostal", "Código Postal"),
            Map.entry("iban", "IBAN"),
            Map.entry("email", "Email"),
            Map.entry("telefono", "Teléfono"),
            Map.entry("fechaInicio", "Fecha de inicio"),
            Map.entry("fechaFin", "Fecha de fin"),
            Map.entry("fechaEnvio", "Fecha de envío"),
            Map.entry("contrato", "Número de contrato"),
            Map.entry("cupsluz", "CUPS Luz"),
            Map.entry("cupsgas", "CUPS Gas"),
            Map.entry("codigo", "Código"),
            Map.entry("titulo", "Título"),
            Map.entry("descripcion", "Descripción"));

    // -----------------------------------------------------------------------
    // Errores de la petición (no del servidor): 4xx con mensaje en español
    // -----------------------------------------------------------------------

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleParametroAusente(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "Falta el parámetro obligatorio '" + ex.getParameterName() + "'.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTipoParametro(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST,
                "El valor '" + ex.getValue() + "' no es válido para el parámetro '" + ex.getName() + "'.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleCuerpoIlegible(HttpMessageNotReadableException ex) {
        log.debug("Cuerpo de petición no legible: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Los datos enviados no tienen un formato válido.");
    }

    /** El límite es el de spring.servlet.multipart.max-file-size (application.properties). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleArchivoGrande(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el tamaño máximo permitido (10 MB).");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleSubidaNoValida(MultipartException ex) {
        log.debug("Petición multipart no válida: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "No se ha podido leer el archivo enviado.");
    }

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
        log.warn("Conflicto de datos al guardar: {}", getRootCauseMessage(ex));
        Map<String, String> error = new HashMap<>();
        String mensaje = traducirErrorBD(ex);
        error.put("error", mensaje);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        // Resto de errores estándar de Spring MVC con estado propio (404, 405, 415...)
        if (ex instanceof ErrorResponse respuesta && respuesta.getStatusCode().is4xxClientError()) {
            return error(respuesta.getStatusCode(), mensajeErrorCliente(respuesta.getStatusCode()));
        }
        // Recorrer la cadena de causas buscando errores de BD traducibles
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof DataIntegrityViolationException dive) {
                return handleDataIntegrityViolation(dive);
            }
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("Data too long") || msg.contains("too long for column")
                    || msg.contains("Duplicate entry") || msg.contains("cannot be null"))) {
                log.warn("Error de datos al guardar: {}", msg);
            }
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
        // Error no previsto: al usuario un mensaje genérico, y en el log el detalle completo
        log.error("Error no controlado al procesar la petición", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INESPERADO);
    }

    private ResponseEntity<Map<String, String>> error(HttpStatusCode estado, String mensaje) {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("error", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }

    private String mensajeErrorCliente(HttpStatusCode estado) {
        return switch (estado.value()) {
            case 404 -> "Recurso no encontrado.";
            case 405 -> "Operación no permitida sobre este recurso.";
            case 406 -> "Formato de respuesta no admitido.";
            case 415 -> "Tipo de contenido no admitido.";
            default -> "La petición no es válida.";
        };
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
        return NOMBRES_COLUMNAS.getOrDefault(columna, columna);
    }
}
