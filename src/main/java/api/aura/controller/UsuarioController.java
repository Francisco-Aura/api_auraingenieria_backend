package api.aura.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import api.aura.config.AuraUserFilter;
import api.aura.config.AuraUserHolder;
import api.aura.config.UsuarioSesion;
import api.aura.entity.Usuario;
import api.aura.service.CifradoContrasenas;
import api.aura.service.LoginIntentosService;
import api.aura.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private static final Logger log = LoggerFactory.getLogger(UsuarioController.class);

    private final UsuarioService usuarioService;
    private final LoginIntentosService intentos;
    private final CifradoContrasenas cifrado;

    public UsuarioController(UsuarioService usuarioService, LoginIntentosService intentos, CifradoContrasenas cifrado) {
        this.usuarioService = usuarioService;
        this.intentos = intentos;
        this.cifrado = cifrado;
    }

    /** Credenciales del login por POST (cuerpo JSON), para que no viajen en la URL ni en los logs. */
    public record Credenciales(String user, String pass) { }

    // Endpoint para crear un usuario
    @PostMapping
    public ResponseEntity<Map<String, Object>> crearUsuario(@RequestBody Usuario usuario) {
        SesionActual.exigirAdmin();
        Usuario nuevoUsuario = usuarioService.crearUsuario(usuario);
        return ResponseEntity.ok(vistaAdministrador(nuevoUsuario));
    }

    /**
     * Listado de usuarios. Los administradores reciben también la contraseña en claro
     * (la consultan para poder decírsela a cada usuario, requisito del cliente); el
     * resto de usuarios, nunca.
     */
    @GetMapping
    public ResponseEntity<List<?>> obtenerTodosLosUsuarios() {
        List<Usuario> usuarios = usuarioService.obtenerTodosLosUsuarios();
        if (AuraUserHolder.esAdmin()) {
            return ResponseEntity.ok(usuarios.stream().map(this::vistaAdministrador).toList());
        }
        return ResponseEntity.ok(usuarios);
    }

    // Endpoint para obtener un usuario por ID
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerUsuarioPorId(@PathVariable Integer id) {
        boolean admin = AuraUserHolder.esAdmin();
        return usuarioService.obtenerUsuarioPorId(id)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(admin ? vistaAdministrador(u) : u))
                .orElse(ResponseEntity.notFound().build());
    }

    // Endpoint para verificar usuario y contraseña (login), con las credenciales en el
    // cuerpo. El antiguo GET /login las ponía en la URL (logs de nginx/Tomcat, historial)
    // y se ha retirado: el frontend usa este POST.
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> autenticar(@RequestBody Credenciales credenciales,
                                                          HttpServletRequest request) {
        return iniciarSesion(credenciales.user(), credenciales.pass(), request);
    }

    /** Cierra la sesión del servidor (el frontend además limpia su localStorage). */
    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            try {
                sesion.invalidate();
            } catch (IllegalStateException yaInvalidada) {
                // nada que cerrar
            }
        }
        return ResponseEntity.noContent().build();
    }

    // Endpoint para actualizar un usuario
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizarUsuario(@PathVariable Integer id,
            @RequestBody Usuario usuarioActualizado, HttpServletRequest request) {
        SesionActual.exigirAdmin();
        Usuario usuario = usuarioService.actualizarUsuario(id, usuarioActualizado);
        // Quien cambia su propia contraseña sigue dentro en esta sesión (antes se le echaba
        // al minuto, al revalidarla, y perdía lo que tuviera a medias). Sus demás sesiones,
        // en otros navegadores, se cierran como con cualquier cambio de contraseña.
        HttpSession sesion = request.getSession(false);
        if (sesion != null && id != null && id.equals(AuraUserHolder.getId())) {
            try {
                sesion.setAttribute(AuraUserFilter.ATRIBUTO_HUELLA, cifrado.huella(usuario.getPass()));
            } catch (IllegalStateException sesionCerrada) {
                // Se cerró mientras tanto: no hay nada que actualizar
            }
        }
        return ResponseEntity.ok(vistaAdministrador(usuario));
    }

    // Endpoint para eliminar un usuario por ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable Integer id) {
        SesionActual.exigirAdmin();
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    private static final String CREDENCIALES_INCORRECTAS = "Usuario o contraseña incorrectos";
    private static final String DEMASIADOS_INTENTOS =
            "Demasiados intentos fallidos. Espere unos minutos antes de volver a intentarlo.";

    private ResponseEntity<Map<String, Object>> iniciarSesion(String user, String pass, HttpServletRequest request) {
        // Vacíos: no pueden acertar nada (el formulario ya los exige), así que ni se
        // consultan ni se cuentan ni llenan el log
        if (user == null || user.isBlank() || pass == null || pass.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, CREDENCIALES_INCORRECTAS);
        }
        String ip = request.getRemoteAddr();
        if (!intentos.permitirDesdeIp(ip)) {
            return error(HttpStatus.TOO_MANY_REQUESTS, DEMASIADOS_INTENTOS);
        }
        // Un usuario o contraseña desmesurados no existen (la columna es VARCHAR(100)):
        // fallo sin tocar la BD, para no retener ni comparar megas de texto
        if (user.length() > 200 || pass.length() > 1000) {
            registrarFallo(user, ip);
            return error(HttpStatus.UNAUTHORIZED, CREDENCIALES_INCORRECTAS);
        }
        if (intentos.bloqueado(user, ip)) {
            return error(HttpStatus.TOO_MANY_REQUESTS, DEMASIADOS_INTENTOS);
        }
        if (!intentos.ocuparTurno()) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "El servidor está ocupado. Inténtelo de nuevo en unos segundos.");
        }
        Optional<Usuario> usuario;
        try {
            // Otra vez, ya con turno: los intentos que esperaban a la vez no se cuelan
            if (intentos.bloqueado(user, ip)) {
                return error(HttpStatus.TOO_MANY_REQUESTS, DEMASIADOS_INTENTOS);
            }
            usuario = usuarioService.obtenerUsuarioPorNombreUsuarioPass(user, pass);
        } finally {
            intentos.liberarTurno();
        }

        if (usuario.isPresent()) {
            intentos.registrarExito(user, ip);
            Usuario u = usuario.get();
            abrirSesion(request, new UsuarioSesion(u.getId(), u.getNombre() == null ? null : u.getNombre().trim(), u.getNivelAcceso()),
                    cifrado.huella(u.getPass()));
            log.info("Login correcto: usuario id {} desde {}", u.getId(), paraLog(ip));

            // Construimos un mapa con la información necesaria del usuario.
            // LinkedHashMap y no Map.of: el email (y el resto) pueden ser nulos,
            // y Map.of lanzaría NullPointerException impidiendo el acceso.
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("id", u.getId());
            respuesta.put("nombre", u.getNombre());
            respuesta.put("email", u.getEmail());
            respuesta.put("nivelAcceso", u.getNivelAcceso());
            // Grupo de trabajo (comercial, ingenieria, administracion, coordinacion); los usuarios sin grupo no lo llevan
            if (u.getGrupo() != null) {
                respuesta.put("grupo", u.getGrupo());
            }

            return ResponseEntity.ok(respuesta);
        } else {
            registrarFallo(user, ip);
            // Devolvemos un código 401 (No autorizado) con un mensaje de error
            return error(HttpStatus.UNAUTHORIZED, CREDENCIALES_INCORRECTAS);
        }
    }

    private void registrarFallo(String user, String ip) {
        // Al log solo cuando se alcanza un bloqueo: un ataque no llena el disco
        if (intentos.registrarFallo(user, ip)) {
            log.warn("Login bloqueado temporalmente por intentos fallidos: usuario '{}' desde {}",
                    paraLog(user), paraLog(ip));
        }
    }

    /** Usuario con los mismos campos y orden que la entidad, con la contraseña en claro. */
    private Map<String, Object> vistaAdministrador(Usuario u) {
        Map<String, Object> vista = new LinkedHashMap<>();
        vista.put("id", u.getId());
        vista.put("nombre", u.getNombre());
        vista.put("email", u.getEmail());
        vista.put("telefono", u.getTelefono());
        vista.put("user", u.getUser());
        vista.put("pass", usuarioService.contrasenaVisible(u));
        vista.put("nivelAcceso", u.getNivelAcceso());
        return vista;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("error", mensaje));
    }

    /** Texto apto para el log: sin caracteres de control ni separadores de línea, y acotado. */
    private static String paraLog(String valor) {
        if (valor == null) return "";
        String limpio = valor.replaceAll("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]", "_");
        return limpio.length() > 60 ? limpio.substring(0, 60) + "…" : limpio;
    }

    /** Sesión nueva en cada login: un id de sesión fijado antes del login no sirve después (session fixation). */
    private static void abrirSesion(HttpServletRequest request, UsuarioSesion usuario, String huellaPass) {
        HttpSession anterior = request.getSession(false);
        if (anterior != null) {
            try {
                anterior.invalidate();
            } catch (IllegalStateException yaInvalidada) {
                // seguimos con una sesión nueva
            }
        }
        HttpSession nueva = request.getSession(true);
        nueva.setAttribute(UsuarioSesion.ATRIBUTO, usuario);
        // Si la contraseña cambia (p. ej. un administrador la restablece), las sesiones
        // abiertas con la anterior se cierran (ver AuraUserFilter)
        nueva.setAttribute(AuraUserFilter.ATRIBUTO_HUELLA, huellaPass);
    }
}
