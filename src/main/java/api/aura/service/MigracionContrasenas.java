package api.aura.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, cifra las contraseñas que sigan en claro en la tabla Usuarios, para
 * que no quede ninguna legible en la base de datos aunque su usuario no haya entrado.
 *
 * <p>{@code aura.seguridad.contrasenas-al-arrancar}:</p>
 * <ul>
 *   <li>{@code cifrar} (por defecto): cifra las que estén en claro.</li>
 *   <li>{@code descifrar}: vuelta atrás; las deja en claro para poder arrancar la
 *       versión anterior de la aplicación.</li>
 *   <li>{@code no}: no toca nada.</li>
 * </ul>
 * Un error aquí nunca impide arrancar: se avisa en el log y el login sigue
 * funcionando con las contraseñas tal como estén.
 */
@Component
public class MigracionContrasenas {

    private static final Logger log = LoggerFactory.getLogger(MigracionContrasenas.class);

    private final UsuarioService usuarios;
    private final String modo;

    public MigracionContrasenas(UsuarioService usuarios,
                                @Value("${aura.seguridad.contrasenas-al-arrancar:cifrar}") String modo) {
        this.usuarios = usuarios;
        this.modo = modo == null ? "cifrar" : modo.trim().toLowerCase();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            switch (modo) {
                case "cifrar" -> {
                    int n = usuarios.cifrarPendientes();
                    if (n > 0) log.info("Contraseñas cifradas al arrancar: {}", n);
                }
                case "descifrar" -> log.warn("Contraseñas devueltas a texto plano (vuelta atrás): {}",
                        usuarios.descifrarTodas());
                default -> log.info("Migración de contraseñas desactivada (modo '{}')", modo);
            }
        } catch (RuntimeException e) {
            log.error("No se pudo completar la migración de contraseñas ({}); se sigue arrancando", modo, e);
        }
    }
}
