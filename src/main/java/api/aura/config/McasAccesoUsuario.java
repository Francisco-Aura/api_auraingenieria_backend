package api.aura.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Método de un controlador MCAS abierto a cualquier usuario identificado
 * (cabecera {@code X-Aura-Usuario-Nombre} de un usuario existente, de cualquier
 * nivel). Sin esta anotación, {@link McasAccessInterceptor} exige administrador.
 *
 * <p>La llevan el resumen de la comisión del contrato y la vista previa: quien
 * graba el contrato ve el estado y el importe, pero no valida.</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McasAccesoUsuario {
}
