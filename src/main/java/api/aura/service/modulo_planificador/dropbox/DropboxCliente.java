package api.aura.service.modulo_planificador.dropbox;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente mínimo de la API de Dropbox (HTTP directo, sin SDK; equivale a dropbox_client.py).
 *
 * <p>Se conecta con una «app» de Dropbox mediante un token de refresco permanente; el token de acceso (que caduca a las pocas horas) se
 * renueva solo. Las credenciales NO están en el código: se leen de la configuración (ver {@code DropboxAlmacen}).
 *
 * <p>Cuenta de empresa: si el usuario ve un espacio de equipo distinto del suyo personal, hay que indicar la raíz con la cabecera
 * {@code Dropbox-API-Path-Root}; el cliente lo detecta solo la primera vez.
 */
public class DropboxCliente {

    public static final String URL_API = "https://api.dropboxapi.com";
    public static final String URL_CONTENIDO = "https://content.dropboxapi.com";
    /** Límite de la subida simple de Dropbox. */
    public static final int MAX_SUBIDA = 150 * 1024 * 1024;

    private record Respuesta(int estado, byte[] cuerpo) {
    }

    private final String claveApp;
    private final String secretoApp;
    private final String tokenRefresco;
    private final String urlApi;
    private final String urlContenido;
    private final Duration espera;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    /** Para la cabecera Dropbox-API-Arg, que solo admite ASCII: el resto va escapado (\\uXXXX). */
    private final ObjectMapper jsonAscii = new ObjectMapper().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, true);
    private final Object cerrojo = new Object();
    private String tokenAcceso = "";
    private long expiraEn;
    /** "" = no hace falta; null = aún no se ha comprobado. */
    private String cabeceraRaiz;

    public DropboxCliente(String claveApp, String secretoApp, String tokenRefresco) {
        this(claveApp, secretoApp, tokenRefresco, URL_API, URL_CONTENIDO, Duration.ofSeconds(60));
    }

    public DropboxCliente(String claveApp, String secretoApp, String tokenRefresco, String urlApi, String urlContenido, Duration espera) {
        this.claveApp = claveApp;
        this.secretoApp = secretoApp;
        this.tokenRefresco = tokenRefresco;
        this.urlApi = urlApi.replaceAll("/+$", "");
        this.urlContenido = urlContenido.replaceAll("/+$", "");
        this.espera = espera;
        this.http = HttpClient.newBuilder().connectTimeout(espera).build();
    }

    /** Que un registro o un error nunca muestren las claves. */
    @Override
    public String toString() {
        return "DropboxCliente(<configurado>)";
    }

    // ------------------------------------------------------------------------------- autenticación

    private static String codificar(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private void renovarToken() {
        String cuerpo = "grant_type=refresh_token&refresh_token=" + codificar(tokenRefresco) + "&client_id=" + codificar(claveApp)
                + "&client_secret=" + codificar(secretoApp);
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(urlApi + "/oauth2/token")).timeout(espera)
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
        Respuesta r = enviar(peticion);
        if (r.estado() != 200) {
            throw new DropboxError("Dropbox rechazó las credenciales de la app (revisa la autorización)", r.estado());
        }
        try {
            JsonNode datos = json.readTree(r.cuerpo());
            tokenAcceso = datos.get("access_token").asText();
            expiraEn = System.currentTimeMillis() / 1000 + datos.path("expires_in").asLong(14400) - 120;
        } catch (IOException | NullPointerException e) {
            throw new DropboxError("Dropbox respondió algo que no se entiende al renovar la autorización");
        }
    }

    private String token(boolean forzar) {
        synchronized (cerrojo) {
            if (forzar || tokenAcceso.isEmpty() || System.currentTimeMillis() / 1000 >= expiraEn) {
                renovarToken();
            }
            return tokenAcceso;
        }
    }

    // ----------------------------------------------------------------------------------- transporte

    private Respuesta enviar(HttpRequest peticion) {
        try {
            HttpResponse<byte[]> r = http.send(peticion, HttpResponse.BodyHandlers.ofByteArray());
            return new Respuesta(r.statusCode(), r.body());
        } catch (IOException e) {
            throw new DropboxError("No se pudo conectar con Dropbox: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DropboxError("No se pudo conectar con Dropbox: InterruptedException");
        }
    }

    private String resumen(byte[] crudo) {
        try {
            JsonNode cuerpo = json.readTree(crudo);
            String resumen = cuerpo.path("error_summary").asText("");
            JsonNode error = cuerpo.path("error");
            String alcance = error.isObject() && error.hasNonNull("required_scope") ? error.get("required_scope").asText() : null;
            return alcance != null ? resumen + " — falta el permiso «" + alcance + "» en la app de Dropbox" : resumen;
        } catch (IOException | RuntimeException e) {
            // Dropbox responde a veces con texto plano (p. ej. un 400 por una petición mal formada)
            String t = new String(crudo, StandardCharsets.UTF_8).strip();
            return t.length() > 300 ? t.substring(0, 300) : t;
        }
    }

    /** Hace una llamada autenticada; si el token ha caducado lo renueva y repite una vez. */
    private Respuesta llamar(String url, Map<String, String> cabeceras, byte[] cuerpo, boolean aceptarConflicto, boolean conRaiz) {
        for (int intento = 1; intento <= 2; intento++) {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(espera)
                    .header("Authorization", "Bearer " + token(intento == 2));
            cabeceras.forEach(b::header);
            if (conRaiz) {
                String raiz = raiz();
                if (!raiz.isEmpty()) {
                    b.header("Dropbox-API-Path-Root", raiz);
                }
            }
            b.POST(cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(cuerpo));
            Respuesta r = enviar(b.build());
            if (r.estado() == 401 && intento == 1) {
                continue;
            }
            if (r.estado() == 409 && aceptarConflicto) {
                return r;
            }
            if (r.estado() != 200) {
                String resumen = resumen(r.cuerpo());
                throw new DropboxError("Dropbox respondió " + r.estado() + ": " + (resumen.isEmpty() ? "error" : resumen), r.estado(), resumen);
            }
            return r;
        }
        throw new DropboxError("Dropbox no aceptó la autorización", 401); // inalcanzable
    }

    private record Rpc(int estado, JsonNode datos) {
    }

    private Rpc rpc(String endpoint, Map<String, Object> arg, boolean aceptarConflicto, boolean conRaiz) {
        try {
            // Sin argumentos, Dropbox espera el cuerpo vacío y sin tipo de contenido (no un «null»).
            Respuesta r = llamar(urlApi + "/2/" + endpoint, arg == null ? Map.of() : Map.of("Content-Type", "application/json"),
                    arg == null ? null : json.writeValueAsBytes(arg), aceptarConflicto, conRaiz);
            return new Rpc(r.estado(), r.cuerpo().length == 0 ? json.createObjectNode() : json.readTree(r.cuerpo()));
        } catch (IOException e) {
            throw new DropboxError("Dropbox respondió algo que no se entiende");
        }
    }

    private Rpc rpc(String endpoint, Map<String, Object> arg) {
        return rpc(endpoint, arg, false, true);
    }

    private Rpc rpcConflicto(String endpoint, Map<String, Object> arg) {
        return rpc(endpoint, arg, true, true);
    }

    // ------------------------------------------------------------------ raíz del espacio (cuentas de empresa)

    private String raiz() {
        if (cabeceraRaiz == null) {
            JsonNode info = rpc("users/get_current_account", null, false, false).datos().path("root_info");
            String raiz = info.path("root_namespace_id").asText("");
            String casa = info.path("home_namespace_id").asText("");
            if (!raiz.isEmpty() && !casa.isEmpty() && !raiz.equals(casa)) {
                ObjectNode n = json.createObjectNode();
                n.put(".tag", "root");
                n.put("root", raiz);
                cabeceraRaiz = n.toString();
            } else {
                cabeceraRaiz = "";
            }
        }
        return cabeceraRaiz;
    }

    // ---------------------------------------------------------------------------------- operaciones

    private static Map<String, Object> ruta(String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", path);
        return m;
    }

    public boolean carpetaExiste(String path) {
        Rpc r = rpcConflicto("files/get_metadata", ruta(path));
        return r.estado() == 200 && "folder".equals(r.datos().path(".tag").asText());
    }

    public boolean archivoExiste(String path) {
        Rpc r = rpcConflicto("files/get_metadata", ruta(path));
        return r.estado() == 200 && "file".equals(r.datos().path(".tag").asText());
    }

    /** Nombres de las entradas del tipo indicado que hay directamente en `path`, o null si no existe. */
    private List<String> listar(String path, String tipo) {
        Map<String, Object> arg = ruta(path);
        arg.put("recursive", false);
        arg.put("limit", 2000);
        Rpc r = rpcConflicto("files/list_folder", arg);
        if (r.estado() == 409) {
            String resumen = r.datos().path("error_summary").asText("");
            if (resumen.startsWith("path/not_found")) {
                return null;
            }
            throw new DropboxError("No se pudo leer la carpeta: " + (resumen.isEmpty() ? "conflicto" : resumen), 409);
        }
        List<String> nombres = new ArrayList<>();
        JsonNode datos = r.datos();
        while (true) {
            for (JsonNode e : datos.path("entries")) {
                if (tipo.equals(e.path(".tag").asText())) {
                    nombres.add(e.path("name").asText());
                }
            }
            if (!datos.path("has_more").asBoolean(false)) {
                return nombres;
            }
            Map<String, Object> sig = new LinkedHashMap<>();
            sig.put("cursor", datos.path("cursor").asText());
            datos = rpc("files/list_folder/continue", sig).datos();
        }
    }

    /** Nombres de los archivos que hay directamente en `path` (no entra en subcarpetas), o null si no existe. */
    public List<String> listarArchivos(String path) {
        return listar(path, "file");
    }

    /** Nombres de las carpetas que hay dentro de `path` (solo el primer nivel), o null si `path` no existe. */
    public List<String> listarSubcarpetas(String path) {
        return listar(path, "folder");
    }

    /**
     * Crea la carpeta (y las que falten por encima). Si ya existe no hace nada ni duplica. Antes de crear se mira si existe: así una
     * carpeta que ya está no pide permiso de escritura en su carpeta padre.
     */
    public void asegurarCarpeta(String path) {
        if (carpetaExiste(path)) {
            return;
        }
        for (int intento = 0; intento < 2; intento++) {
            Map<String, Object> arg = ruta(path);
            arg.put("autorename", false);
            Rpc r = rpcConflicto("files/create_folder_v2", arg);
            if (r.estado() != 409) {
                return;
            }
            String resumen = r.datos().path("error_summary").asText("");
            if (resumen.startsWith("path/conflict/folder")) {
                return; // ya existe
            }
            String padre = path.replaceAll("/+$", "");
            padre = padre.contains("/") ? padre.substring(0, padre.lastIndexOf('/')) : "";
            if (intento == 0 && resumen.startsWith("path/not_found") && !padre.isEmpty()) {
                asegurarCarpeta(padre); // falta una carpeta por encima: se crea y se reintenta
                continue;
            }
            throw new DropboxError("No se pudo crear la carpeta: " + (resumen.isEmpty() ? "conflicto" : resumen), 409, resumen);
        }
    }

    /** Quién es la cuenta conectada y si trabaja sobre un espacio de equipo (para comprobar la autorización). */
    public Map<String, Object> resumenCuenta() {
        JsonNode cuenta = rpc("users/get_current_account", null, false, false).datos();
        JsonNode info = cuenta.path("root_info");
        String raiz = info.path("root_namespace_id").asText("");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", cuenta.path("name").path("display_name").asText(""));
        m.put("email", cuenta.path("email").asText(""));
        m.put("team_space", !raiz.isEmpty() && !raiz.equals(info.path("home_namespace_id").asText("")));
        return m;
    }

    /** Sube un archivo sin sobrescribir nunca: si ya hay uno con ese nombre, Dropbox renombra el nuevo. */
    public Map<String, Object> subir(String path, byte[] contenido) {
        if (contenido.length > MAX_SUBIDA) {
            throw new DropboxError("El archivo supera el máximo de la subida simple de Dropbox (150 MB)");
        }
        Map<String, Object> arg = ruta(path);
        arg.put("mode", "add");
        arg.put("autorename", true);
        arg.put("mute", true);
        try {
            Respuesta r = llamar(urlContenido + "/2/files/upload",
                    Map.of("Dropbox-API-Arg", jsonAscii.writeValueAsString(arg), "Content-Type", "application/octet-stream"), contenido, false, true);
            JsonNode d = json.readTree(r.cuerpo());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.path("id").asText(""));
            m.put("path", d.path("path_display").asText(path));
            m.put("size", d.path("size").asLong(contenido.length));
            return m;
        } catch (IOException e) {
            throw new DropboxError("Dropbox respondió algo que no se entiende");
        }
    }

    /** Contenido de un archivo (para enseñarlo en pantalla, p. ej. una ficha técnica en PDF). */
    public byte[] descargar(String path) {
        try {
            return llamar(urlContenido + "/2/files/download", Map.of("Dropbox-API-Arg", jsonAscii.writeValueAsString(ruta(path))), new byte[0], false, true).cuerpo();
        } catch (IOException e) {
            throw new DropboxError("No se pudo preparar la petición a Dropbox");
        }
    }

    /** Enlace de descarga directa válido unas horas (no hay que guardarlo). */
    public String enlaceTemporal(String path) {
        return rpc("files/get_temporary_link", ruta(path)).datos().path("link").asText();
    }
}
