package api.aura.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    // Crear un nuevo usuario
    public Usuario crearUsuario(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    // Obtener todos los usuarios
    public List<Usuario> obtenerTodosLosUsuarios() {
        return usuarioRepository.findAll();
    }

    // Obtener usuario por ID
    public Optional<Usuario> obtenerUsuarioPorId(Integer id) {
        return usuarioRepository.findById(id);
    }

    // Buscar usuario por nombre de usuario (campo 'user')
    public Optional<Usuario> obtenerUsuarioPorNombreUsuarioPass(String user, String pass) {
        return Optional.ofNullable(usuarioRepository.findByUser(user,pass));
    }

    // Actualizar un usuario existente
    public Usuario actualizarUsuario(Integer id, Usuario usuarioActualizado) {
        return usuarioRepository.findById(id)
                .map(usuario -> {
                    usuario.setNombre(usuarioActualizado.getNombre());
                    usuario.setEmail(usuarioActualizado.getEmail());
                    usuario.setTelefono(usuarioActualizado.getTelefono());
                    usuario.setUser(usuarioActualizado.getUser());
                    usuario.setPass(usuarioActualizado.getPass());
                    usuario.setNivelAcceso(usuarioActualizado.getNivelAcceso());
                    return usuarioRepository.save(usuario);
                })
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
    }

    // Eliminar usuario (baja lógica)
    public void eliminarUsuario(Integer id) {
        usuarioRepository.deleteById(id);
    }
}
