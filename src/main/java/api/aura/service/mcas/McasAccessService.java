package api.aura.service.mcas;

import api.aura.config.AuraUserHolder;
import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

/** Autoriza operaciones MCAS usando la identidad ya empleada por Aura. */
@Service
public class McasAccessService {
    private final UsuarioRepository usuarios;

    public McasAccessService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public void requireAdmin() {
        Usuario usuario = usuarioIdentificado();
        if (!Integer.valueOf(1).equals(usuario.getNivelAcceso()))
            throw new SecurityException("MCAS requiere nivel de acceso administrador");
    }

    /**
     * Basta un usuario identificado y existente, de cualquier nivel (métodos con
     * {@link api.aura.config.McasAccesoUsuario}).
     */
    public void requireUsuario() {
        usuarioIdentificado();
    }

    /**
     * Usuario de la sesión, releído de la BD para usar su nivel actual (un
     * administrador degradado pierde el acceso sin esperar a que caduque la
     * sesión). Por id cuando lo hay; por nombre solo sin sesión (procesos internos).
     */
    private Usuario usuarioIdentificado() {
        Integer id = AuraUserHolder.getId();
        if (id != null)
            return usuarios.findById(id)
                    .orElseThrow(() -> new SecurityException("Usuario no autorizado para operar MCAS"));
        String nombre = AuraUserHolder.get();
        if (nombre == null || nombre.isBlank())
            throw new SecurityException("Usuario no identificado para operar MCAS");
        return usuarios.findFirstByNombreIgnoreCase(nombre.trim())
                .orElseThrow(() -> new SecurityException("Usuario no autorizado para operar MCAS"));
    }
}
