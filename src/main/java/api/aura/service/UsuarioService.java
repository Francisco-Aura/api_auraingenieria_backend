package api.aura.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    /** Formato de un hash BCrypt ($2a$, $2b$ o $2y$, coste y 53 caracteres). */
    private static final Pattern HASH_BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    /**
     * Solo para comprobar contraseñas que una versión previa de esta rama (p. ej. en
     * PRE) pudiera haber guardado como hash: al entrar se pasan al cifrado reversible.
     */
    private final PasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CifradoContrasenas cifrado;

    /**
     * «descifrar» = vuelta atrás a la versión anterior: no se cifra nada nuevo (ni al
     * entrar ni al crear o editar usuarios), para que la versión anterior pueda leerlas.
     */
    @Value("${aura.seguridad.contrasenas-al-arrancar:cifrar}")
    private String modoContrasenas;

    @Transactional
    public Usuario crearUsuario(Usuario usuario) {
        // Alta: el id lo genera la BD. Un id en el cuerpo convertía el alta en una
        // sobrescritura de otro usuario.
        usuario.setId(null);
        if (usuario.getPass() != null && !usuario.getPass().isBlank()) {
            usuario.setPass(paraGuardar(usuario.getPass()));
        }
        return usuarioRepository.save(usuario);
    }

    public List<Usuario> obtenerTodosLosUsuarios() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> obtenerUsuarioPorId(Integer id) {
        return usuarioRepository.findById(id);
    }

    /**
     * Contraseña en claro para la pantalla de Administración (los administradores
     * las consultan para poder decírselas a cada usuario). Vacía si no se puede leer.
     */
    public String contrasenaVisible(Usuario usuario) {
        String guardada = usuario.getPass();
        if (guardada == null || esHash(guardada)) return guardada == null ? null : "";
        try {
            return cifrado.descifrar(guardada);
        } catch (IllegalStateException ilegible) {
            log.warn("No se pudo descifrar la contraseña del usuario {}", usuario.getId());
            return "";
        }
    }

    /**
     * Comprueba usuario y contraseña. La comparación es la misma que hacía antes
     * MariaDB con la colación utf16_spanish_ci (sin distinguir mayúsculas ni tildes,
     * sin espacios finales), así que para el usuario nada cambia. Una contraseña que
     * aún esté en claro se cifra en ese mismo login.
     */
    public Optional<Usuario> obtenerUsuarioPorNombreUsuarioPass(String user, String pass) {
        if (user == null || user.isBlank() || pass == null || pass.isEmpty()) {
            return Optional.empty();
        }
        String tecleada = canonica(pass);
        if (tecleada.isEmpty()) return Optional.empty();
        for (Usuario usuario : usuarioRepository.findAllByUser(user)) {
            String guardada = usuario.getPass();
            if (guardada == null || guardada.isEmpty()) continue;
            if (esHash(guardada)) {
                if (bcrypt.matches(tecleada, guardada)) {
                    // Del hash no se puede recuperar el original: se guarda lo tecleado
                    return Optional.of(guardarCifrada(usuario, pass.stripTrailing()));
                }
                continue;
            }
            String plano;
            try {
                plano = cifrado.descifrar(guardada);
            } catch (IllegalStateException ilegible) {
                log.warn("No se pudo descifrar la contraseña del usuario {}", usuario.getId());
                continue;
            }
            if (igualesEnTiempoConstante(tecleada, canonica(plano))) {
                return Optional.of(cifrado.estaCifrada(guardada) ? usuario : guardarCifrada(usuario, guardada));
            }
        }
        return Optional.empty();
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
                    // campo dejaba al usuario sin contraseña y sin poder entrar. Si es la
                    // misma que ya tenía (el formulario la envía rellena), no se toca: así
                    // editar otro dato no cierra las sesiones abiertas del usuario.
                    String nueva = usuarioActualizado.getPass();
                    if (nueva != null && !nueva.isBlank() && !Objects.equals(nueva, contrasenaVisible(usuario))) {
                        usuario.setPass(paraGuardar(nueva));
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

    /**
     * Cifra las contraseñas que sigan en claro (se ejecuta al arrancar). Las que no
     * caben cifradas en la columna se dejan como están y se avisa en el log.
     *
     * @return número de contraseñas cifradas
     */
    @Transactional
    public int cifrarPendientes() {
        int cifradas = 0;
        for (Usuario usuario : usuarioRepository.findAll()) {
            String guardada = usuario.getPass();
            if (guardada == null || guardada.isEmpty() || cifrado.estaCifrada(guardada) || esHash(guardada)) continue;
            if (!cifrado.cabe(guardada)) {
                log.warn("La contraseña del usuario {} es demasiado larga para cifrarla; se deja como estaba", usuario.getId());
                continue;
            }
            usuario.setPass(cifrado.cifrar(guardada));
            cifradas++;
        }
        return cifradas;
    }

    /**
     * Vuelta atrás: deja en claro todas las contraseñas cifradas, para poder arrancar
     * de nuevo la versión anterior de la aplicación (aura.seguridad.contrasenas-al-arrancar=descifrar).
     *
     * @return número de contraseñas descifradas
     */
    @Transactional
    public int descifrarTodas() {
        int descifradas = 0;
        for (Usuario usuario : usuarioRepository.findAll()) {
            if (!cifrado.estaCifrada(usuario.getPass())) continue;
            usuario.setPass(cifrado.descifrar(usuario.getPass()));
            descifradas++;
        }
        return descifradas;
    }

    private String paraGuardar(String plano) {
        // Guardada sin cifrar, una contraseña que empieza por «{c}» se tomaría por cifrada:
        // no se podría descifrar y su usuario no podría entrar
        if (cifrado.estaCifrada(plano) && (vueltaAtras() || !cifrado.cabe(plano))) {
            throw new IllegalArgumentException("La contraseña no puede empezar por «{c}»");
        }
        if (vueltaAtras()) return plano;
        // Cifrada solo cabe hasta 44 bytes en la columna (VARCHAR(100)). Una más larga se
        // guarda como hasta ahora en vez de rechazarla: el login y Administración la leen
        // igual, y el administrador puede seguir poniendo contraseñas de hasta 100
        if (!cifrado.cabe(plano)) {
            log.warn("Contraseña demasiado larga para cifrarla en la columna: se guarda sin cifrar");
            return plano;
        }
        return cifrado.cifrar(plano);
    }

    private boolean vueltaAtras() {
        return modoContrasenas != null && "descifrar".equalsIgnoreCase(modoContrasenas.trim());
    }

    private Usuario guardarCifrada(Usuario usuario, String plano) {
        if (vueltaAtras()) return usuario;
        if (!cifrado.cabe(plano)) return usuario;
        usuario.setPass(cifrado.cifrar(plano));
        return usuarioRepository.save(usuario);
    }

    /**
     * Forma en que se comparan las contraseñas, igual que la colación de MariaDB que
     * se usaba antes: sin espacios finales, sin mayúsculas y sin tildes (la ñ se
     * mantiene distinta de la n).
     */
    static String canonica(String valor) {
        return Normalizer.normalize(valor.stripTrailing(), Normalizer.Form.NFD)
                .replaceAll("[\\u0300-\\u0302\\u0304-\\u036f]", "")
                .transform(t -> Normalizer.normalize(t, Normalizer.Form.NFC))
                .toLowerCase(Locale.ROOT);
    }

    private static boolean esHash(String valor) {
        return HASH_BCRYPT.matcher(valor).matches();
    }

    private static boolean igualesEnTiempoConstante(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
