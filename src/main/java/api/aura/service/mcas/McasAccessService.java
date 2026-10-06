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

    private Usuario usuarioIdentificado() {
        String nombre = AuraUserHolder.get();
        if (nombre == null || nombre.isBlank())
            throw new SecurityException("Usuario no identificado para operar MCAS");
        return usuarios.findFirstByNombreIgnoreCase(nombre.trim())
                .orElseThrow(() -> new SecurityException("Usuario no autorizado para operar MCAS"));
    }
}
