package api.aura.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import api.aura.entity.Usuario;
import api.aura.service.UsuarioService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Endpoint para crear un usuario
    @PostMapping
    public ResponseEntity<Usuario> crearUsuario(@RequestBody Usuario usuario) {
        Usuario nuevoUsuario = usuarioService.crearUsuario(usuario);
        return ResponseEntity.ok(nuevoUsuario);
    }

    // Endpoint para listar todos los usuarios
    @GetMapping
    public ResponseEntity<List<Usuario>> obtenerTodosLosUsuarios() {
        List<Usuario> usuarios = usuarioService.obtenerTodosLosUsuarios();
        return ResponseEntity.ok(usuarios);
    }

    // Endpoint para obtener un usuario por ID
    @GetMapping("/{id}")
    public ResponseEntity<Usuario> obtenerUsuarioPorId(@PathVariable Integer id) {
        return usuarioService.obtenerUsuarioPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Endpoint para verificar usuario y contraseña (login)
    @GetMapping("/login")
    public ResponseEntity<Map<String, Object>> autenticar(
            @RequestParam String user,
            @RequestParam String pass) {
        Optional<Usuario> usuario = usuarioService.obtenerUsuarioPorNombreUsuarioPass(user, pass);

        if (usuario.isPresent()) {
            // Construimos un mapa con la información necesaria del usuario
            Map<String, Object> respuesta = Map.of(
                    "id", usuario.get().getId(),
                    "nombre", usuario.get().getNombre(),
                    "email", usuario.get().getEmail(),
                    "nivelAcceso", usuario.get().getNivelAcceso());

            return ResponseEntity.ok(respuesta);
        } else {
            // Devolvemos un código 401 (No autorizado) con un mensaje de error
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Usuario o contraseña incorrectos"));
        }
    }

    // Endpoint para actualizar un usuario
    @PutMapping("/{id}")
    public ResponseEntity<Usuario> actualizarUsuario(@PathVariable Integer id,
            @RequestBody Usuario usuarioActualizado) {
        Usuario usuario = usuarioService.actualizarUsuario(id, usuarioActualizado);
        return ResponseEntity.ok(usuario);
    }

    // Endpoint para eliminar un usuario por ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable Integer id) {
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }
}
