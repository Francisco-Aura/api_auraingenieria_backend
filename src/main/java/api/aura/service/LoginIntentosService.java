package api.aura.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Freno a la fuerza bruta del login, en memoria y sin efecto para quien entra bien:
 * <ul>
 *   <li>{@value #MAX_FALLOS} fallos seguidos con un usuario desde una misma IP bloquean
 *       ese usuario en esa IP durante {@link #BLOQUEO}.</li>
 *   <li>{@value #MAX_FALLOS_USUARIO} fallos con un usuario desde cualquier IP (ataque
 *       repartido) lo bloquean también, salvo desde las IP en las que ese usuario ya
 *       entró bien: un tercero no puede dejar fuera al usuario en su propio equipo.</li>
 * </ul>
 * El usuario se normaliza de forma agresiva (sin mayúsculas, tildes, anchos ni
 * caracteres invisibles), para que las variantes que la colación de MariaDB da por
 * iguales cuenten como el mismo usuario.
 */
@Service
public class LoginIntentosService {

    static final int MAX_FALLOS = 10;
    public static final int MAX_FALLOS_USUARIO = 30;
    static final Duration BLOQUEO = Duration.ofMinutes(5);
    /** Tope de entradas para que una avalancha de usuarios inventados no agote la memoria. */
    static final int MAX_ENTRADAS = 10_000;
    static final int MAX_SIMULTANEOS = 4;
    static final int MAX_IPS_CONOCIDAS = 5;
    /** Más que cualquier usuario real (la columna es VARCHAR(100)). */
    static final int MAX_LONGITUD_USUARIO = 200;

    private record Fallos(int numero, Instant ultimo) { }

    /**
     * Fallos por IP y minuto, con cualquier usuario. Los aciertos no cuentan: varios
     * usuarios detrás de la misma IP que entran a la vez nunca se bloquean.
     */
    static final int MAX_FALLOS_POR_MINUTO_IP = 20;
    private record Ventana(int numero, Instant inicio) { }
    private final Map<String, Ventana> fallosPorIp = new ConcurrentHashMap<>();

    private final Map<String, Fallos> fallos = new ConcurrentHashMap<>();
    /** IP desde las que cada usuario ha entrado bien (las últimas {@value #MAX_IPS_CONOCIDAS}). */
    private final Map<String, Deque<String>> ipsConocidas = new ConcurrentHashMap<>();
    /**
     * Logins comprobándose a la vez: cada uno descifra contraseñas (y BCrypt, si queda
     * alguna antigua, es lento a propósito); una ráfaga de intentos con usuarios
     * inventados (que no se bloquean) agotaría la CPU.
     */
    private final Semaphore simultaneos = new Semaphore(MAX_SIMULTANEOS);
    private final Clock reloj;

    public LoginIntentosService() {
        this(Clock.systemUTC());
    }

    LoginIntentosService(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * Usuario normalizado para contar intentos: acotado, sin mayúsculas, tildes,
     * formas de ancho completo ni caracteres invisibles. Unir de más es inofensivo
     * para un contador; unir de menos permitiría saltarse el bloqueo.
     */
    static String normalizar(String usuario) {
        if (usuario == null) return "";
        String acotado = usuario.length() > MAX_LONGITUD_USUARIO ? usuario.substring(0, MAX_LONGITUD_USUARIO) : usuario;
        return Normalizer.normalize(acotado, Normalizer.Form.NFKD)
                .replaceAll("[\\p{M}\\p{Cf}\\p{Cc}\\s]", "")
                .toLowerCase(Locale.ROOT)
                .replace("ß", "ss");
    }

    static String claveIp(String usuario, String ip) {
        return "I|" + normalizar(usuario) + "|" + (ip == null ? "" : ip);
    }

    static String claveUsuario(String usuario) {
        return "U|" + normalizar(usuario);
    }

    /** true si ese usuario no puede intentar entrar ahora desde esa IP. */
    public boolean bloqueado(String usuario, String ip) {
        if (alcanzado(claveIp(usuario, ip), MAX_FALLOS)) return true;
        return alcanzado(claveUsuario(usuario), MAX_FALLOS_USUARIO) && !ipConocida(usuario, ip);
    }

    /**
     * Cuenta un fallo. Devuelve true si con él se acaba de alcanzar un bloqueo (para
     * dejarlo en el log una sola vez).
     */
    public boolean registrarFallo(String usuario, String ip) {
        sumarFalloIp(ip);
        boolean porIp = sumar(claveIp(usuario, ip)) == MAX_FALLOS;
        boolean porUsuario = sumar(claveUsuario(usuario)) == MAX_FALLOS_USUARIO;
        return porIp || porUsuario;
    }

    public void registrarExito(String usuario, String ip) {
        fallos.remove(claveIp(usuario, ip));
        fallos.remove(claveUsuario(usuario));
        if (ip == null) return;
        if (ipsConocidas.size() >= MAX_ENTRADAS) ipsConocidas.clear();
        Deque<String> ips = ipsConocidas.computeIfAbsent(normalizar(usuario), u -> new ArrayDeque<>());
        synchronized (ips) {
            ips.remove(ip);
            ips.addFirst(ip);
            while (ips.size() > MAX_IPS_CONOCIDAS) ips.removeLast();
        }
    }

    /**
     * false si desde esa IP ya ha habido {@value #MAX_FALLOS_POR_MINUTO_IP} fallos en el
     * último minuto, con usuarios cualesquiera. Frena a quien prueba muchos usuarios
     * distintos desde el mismo equipo.
     */
    public boolean permitirDesdeIp(String ip) {
        if (ip == null) return true;
        Ventana v = fallosPorIp.get(ip);
        return v == null || caducada(v, reloj.instant()) || v.numero() < MAX_FALLOS_POR_MINUTO_IP;
    }

    private void sumarFalloIp(String ip) {
        if (ip == null) return;
        Instant ahora = reloj.instant();
        if (fallosPorIp.size() >= MAX_ENTRADAS) {
            fallosPorIp.values().removeIf(v -> caducada(v, ahora));
            if (fallosPorIp.size() >= MAX_ENTRADAS) fallosPorIp.clear();
        }
        fallosPorIp.merge(ip, new Ventana(1, ahora), (anterior, nueva) ->
                caducada(anterior, ahora) ? nueva : new Ventana(anterior.numero() + 1, anterior.inicio()));
    }

    private static boolean caducada(Ventana v, Instant ahora) {
        return !v.inicio().plusSeconds(60).isAfter(ahora);
    }

    /** Reserva turno para comprobar un login (espera hasta 0,5 s); false si el servidor está saturado. */
    public boolean ocuparTurno() {
        try {
            return simultaneos.tryAcquire(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void liberarTurno() {
        simultaneos.release();
    }

    private boolean ipConocida(String usuario, String ip) {
        Deque<String> ips = ipsConocidas.get(normalizar(usuario));
        if (ips == null || ip == null) return false;
        synchronized (ips) {
            return ips.contains(ip);
        }
    }

    private boolean alcanzado(String clave, int maximo) {
        Fallos f = fallos.get(clave);
        if (f == null) return false;
        if (caducado(f, reloj.instant())) {
            fallos.remove(clave, f);
            return false;
        }
        return f.numero() >= maximo;
    }

    /** Suma un fallo y devuelve el total (0 si no cabe y no se pudo registrar). */
    private int sumar(String clave) {
        Instant ahora = reloj.instant();
        if (!fallos.containsKey(clave) && fallos.size() >= MAX_ENTRADAS && !hacerHueco(ahora)) return 0;
        return fallos.merge(clave, new Fallos(1, ahora), (anterior, nuevo) ->
                caducado(anterior, ahora) ? nuevo : new Fallos(anterior.numero() + 1, ahora)).numero();
    }

    /**
     * Libera sitio: primero lo caducado y después contadores que aún no bloquean;
     * nunca un bloqueo activo (si no, llenar el mapa lo levantaría).
     */
    private boolean hacerHueco(Instant ahora) {
        fallos.values().removeIf(f -> caducado(f, ahora));
        Iterator<Map.Entry<String, Fallos>> it = fallos.entrySet().iterator();
        while (fallos.size() >= MAX_ENTRADAS && it.hasNext()) {
            if (it.next().getValue().numero() < MAX_FALLOS) it.remove();
        }
        return fallos.size() < MAX_ENTRADAS;
    }

    private static boolean caducado(Fallos f, Instant ahora) {
        return f.ultimo().plus(BLOQUEO).isBefore(ahora);
    }

    int entradas() {
        return fallos.size();
    }
}
