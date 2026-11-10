package api.aura.controller.modulo_planificador;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dropbox de mentira para las pruebas: un servidor HTTP local con un árbol de carpetas y archivos en memoria. Nunca toca el Dropbox real. */
class DropboxFalso implements AutoCloseable {

    private final HttpServer servidor;
    private final ObjectMapper json = new ObjectMapper();
    /** Carpetas existentes (ruta en minúsculas → ruta con su forma original). */
    final Map<String, String> carpetas = new TreeMap<>();
    final Map<String, byte[]> archivos = new TreeMap<>();
    final List<String> llamadas = new ArrayList<>();
    int peticionesDeToken;
    boolean rechazarCredenciales;
    String raizEquipo = "";

    DropboxFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    void crearCarpeta(String ruta) {
        String[] partes = ruta.replaceAll("^/+", "").split("/");
        StringBuilder acumulada = new StringBuilder();
        for (String p : partes) {
            acumulada.append('/').append(p);
            carpetas.put(acumulada.toString().toLowerCase(), acumulada.toString());
        }
    }

    boolean existeCarpeta(String ruta) {
        return carpetas.containsKey(ruta.toLowerCase());
    }

    @Override
    public void close() {
        servidor.stop(0);
    }

    // ------------------------------------------------------------------------------------------------

    private static String padre(String ruta) {
        int i = ruta.lastIndexOf('/');
        return i <= 0 ? "" : ruta.substring(0, i);
    }

    private void responder(HttpExchange e, int estado, Object cuerpo) throws IOException {
        byte[] bytes = cuerpo instanceof byte[] b ? b : json.writeValueAsBytes(cuerpo);
        e.getResponseHeaders().add("Content-Type", cuerpo instanceof byte[] ? "application/octet-stream" : "application/json");
        e.sendResponseHeaders(estado, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            e.getResponseBody().write(bytes);
        }
        e.close();
    }

    private ObjectNode error(String resumen) {
        ObjectNode n = json.createObjectNode();
        n.put("error_summary", resumen);
        return n;
    }

    private void atender(HttpExchange e) throws IOException {
        String camino = e.getRequestURI().getPath();
        byte[] cuerpo = e.getRequestBody().readAllBytes();
        llamadas.add(camino);
        if (camino.equals("/oauth2/token")) {
            peticionesDeToken++;
            if (rechazarCredenciales) {
                responder(e, 400, error("invalid_grant"));
                return;
            }
            ObjectNode n = json.createObjectNode();
            n.put("access_token", "token-de-prueba");
            n.put("expires_in", 14400);
            responder(e, 200, n);
            return;
        }
        if (!"Bearer token-de-prueba".equals(e.getRequestHeaders().getFirst("Authorization"))) {
            responder(e, 401, error("expired_access_token"));
            return;
        }
        JsonNode arg = cuerpo.length > 0 && e.getRequestHeaders().getFirst("Dropbox-API-Arg") == null ? json.readTree(cuerpo)
                : e.getRequestHeaders().getFirst("Dropbox-API-Arg") != null ? json.readTree(e.getRequestHeaders().getFirst("Dropbox-API-Arg")) : json.createObjectNode();
        String ruta = arg.path("path").asText("");
        switch (camino) {
            case "/2/users/get_current_account" -> {
                ObjectNode n = json.createObjectNode();
                n.putObject("name").put("display_name", "Cuenta de prueba");
                n.put("email", "prueba@example.test");
                ObjectNode info = n.putObject("root_info");
                info.put("home_namespace_id", "1");
                info.put("root_namespace_id", raizEquipo.isEmpty() ? "1" : raizEquipo);
                responder(e, 200, n);
            }
            case "/2/files/get_metadata" -> {
                ObjectNode n = json.createObjectNode();
                if (existeCarpeta(ruta)) {
                    n.put(".tag", "folder");
                    responder(e, 200, n);
                } else if (archivos.containsKey(ruta.toLowerCase())) {
                    n.put(".tag", "file");
                    responder(e, 200, n);
                } else {
                    responder(e, 409, error("path/not_found/."));
                }
            }
            case "/2/files/list_folder" -> {
                if (!existeCarpeta(ruta)) {
                    responder(e, 409, error("path/not_found/."));
                    return;
                }
                ObjectNode n = json.createObjectNode();
                ArrayNode entradas = n.putArray("entries");
                String prefijo = ruta.toLowerCase() + "/";
                carpetas.forEach((clave, real) -> {
                    if (clave.startsWith(prefijo) && clave.indexOf('/', prefijo.length()) < 0) {
                        entradas.addObject().put(".tag", "folder").put("name", real.substring(real.lastIndexOf('/') + 1));
                    }
                });
                archivos.forEach((clave, bytes) -> {
                    if (clave.startsWith(prefijo) && clave.indexOf('/', prefijo.length()) < 0) {
                        entradas.addObject().put(".tag", "file").put("name", nombresReales.getOrDefault(clave, clave.substring(clave.lastIndexOf('/') + 1)));
                    }
                });
                n.put("has_more", false);
                responder(e, 200, n);
            }
            case "/2/files/create_folder_v2" -> {
                if (existeCarpeta(ruta)) {
                    responder(e, 409, error("path/conflict/folder/."));
                } else if (!padre(ruta).isEmpty() && !existeCarpeta(padre(ruta))) {
                    responder(e, 409, error("path/not_found/."));
                } else {
                    crearCarpeta(ruta);
                    responder(e, 200, json.createObjectNode());
                }
            }
            case "/2/files/upload" -> {
                if (!existeCarpeta(padre(ruta))) {
                    responder(e, 409, error("path/not_found/."));
                    return;
                }
                String destino = ruta;
                int n = 0;
                while (archivos.containsKey(destino.toLowerCase())) {
                    n++;
                    int punto = ruta.lastIndexOf('.');
                    destino = punto > ruta.lastIndexOf('/') ? ruta.substring(0, punto) + " (" + n + ")" + ruta.substring(punto) : ruta + " (" + n + ")";
                }
                archivos.put(destino.toLowerCase(), cuerpo);
                nombresReales.put(destino.toLowerCase(), destino.substring(destino.lastIndexOf('/') + 1));
                ObjectNode r = json.createObjectNode();
                r.put("id", "id:" + archivos.size());
                r.put("path_display", destino);
                r.put("size", cuerpo.length);
                responder(e, 200, r);
            }
            case "/2/files/download" -> {
                byte[] datos = archivos.get(ruta.toLowerCase());
                if (datos == null) {
                    responder(e, 409, error("path/not_found/."));
                } else {
                    responder(e, 200, datos);
                }
            }
            case "/2/files/get_temporary_link" -> {
                if (!archivos.containsKey(ruta.toLowerCase())) {
                    responder(e, 409, error("path/not_found/."));
                } else {
                    ObjectNode r = json.createObjectNode();
                    r.put("link", "https://dl.example.test/temporal" + ruta.replace(" ", "%20"));
                    responder(e, 200, r);
                }
            }
            default -> responder(e, 404, error("endpoint/not_found"));
        }
    }

    private final Map<String, String> nombresReales = new HashMap<>();

    /** Lo último que llegó con ese nombre, tal y como se guardó. */
    byte[] archivo(String ruta) {
        return archivos.get(ruta.toLowerCase());
    }

    static String texto(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
