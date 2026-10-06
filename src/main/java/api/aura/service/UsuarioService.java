package api.aura.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Transactional
    public Usuario crearUsuario(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    public List<Usuario> obtenerTodosLosUsuarios() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> obtenerUsuarioPorId(Integer id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> obtenerUsuarioPorNombreUsuarioPass(String user, String pass) {
        return Optional.ofNullable(usuarioRepository.findByUser(user, pass));
    }

    @Transactional
    public Usuario actualizarUsuario(Integer id, Usuario usuarioActualizado) {
        return usuarioRepository.findById(id)
                .map(usuario -> {
                    usuario.setNombre(usuarioActualizado.getNombre());
                    usuario.setEmail(usuarioActualizado.getEmail());
                    usuario.setTelefono(usuarioActualizado.getTelefono());
                    usuario.setUser(usuarioActualizado.getUser());
                    // La contraseña solo se cambia si viene una nueva: una petición sin ese
                    // campo dejaba al usuario sin contraseña y sin poder entrar.
                    if (usuarioActualizado.getPass() != null && !usuarioActualizado.getPass().isBlank()) {
                        usuario.setPass(usuarioActualizado.getPass());
                    }
                    usuario.setNivelAcceso(usuarioActualizado.getNivelAcceso());
                    return usuarioRepository.save(usuario);
                })
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));
    }

    @Transactional
    public void eliminarUsuario(Integer id) {
        usuarioRepository.deleteById(id);
    }
}
