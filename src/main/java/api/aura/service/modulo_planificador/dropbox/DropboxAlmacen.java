package api.aura.service.modulo_planificador.dropbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

import static api.aura.service.modulo_planificador.dropbox.DropboxEstructura.unir;

/**
 * Documentos en Dropbox: la carpeta de cada proyecto, los archivos que se aportan a las tareas y las fichas técnicas del inventario
 * (equivale a dropbox_store.py).
 *
 * <p>Se activa solo si el servidor tiene configuradas las claves y la carpeta base (propiedades {@code planificador.dropbox.*}, que
 * se rellenan con variables de entorno; nada de esto va en el código ni en git). Esta capa NUNCA borra nada en Dropbox.
 */
@Component
public class DropboxAlmacen {

    private final String claveApp;
    private final String secretoApp;
    private final String tokenRefresco;
    /** Direcciones de Dropbox; solo se cambian para apuntar a un Dropbox de pruebas. */
    private final String urlApi;
    private final String urlContenido;
    private String base;
    private String raizFichas;
    private DropboxCliente cliente;
    private boolean cargado;

    public DropboxAlmacen(@Value("${planificador.dropbox.app-key:}") String claveApp,
                          @Value("${planificador.dropbox.app-secret:}") String secretoApp,
                          @Value("${planificador.dropbox.refresh-token:}") String tokenRefresco,
                          @Value("${planificador.dropbox.base-path:}") String base,
                          @Value("${planificador.dropbox.fichas-raiz:}") String raizFichas,
                          @Value("${planificador.dropbox.url-api:" + DropboxCliente.URL_API + "}") String urlApi,
                          @Value("${planificador.dropbox.url-contenido:" + DropboxCliente.URL_CONTENIDO + "}") String urlContenido) {
        this.urlApi = urlApi.strip();
        this.urlContenido = urlContenido.strip();
        this.claveApp = claveApp.strip();
        this.secretoApp = secretoApp.strip();
        this.tokenRefresco = tokenRefresco.strip();
        this.base = base.strip();
        this.raizFichas = raizFichas.strip().replaceAll("/+$", "");
    }

    /** Sustituye el cliente y las carpetas (pruebas). Con cliente null vuelve a leer la configuración. */
    public synchronized void usar(DropboxCliente sustituto, String carpetaBase, String carpetaFichas) {
        this.cliente = sustituto;
        this.cargado = sustituto != null;
        this.base = carpetaBase == null ? "" : carpetaBase.strip();
        this.raizFichas = carpetaFichas == null ? "" : carpetaFichas.strip().replaceAll("/+$", "");
    }

    private synchronized DropboxCliente cliente() {
        if (!cargado) {
            cliente = claveApp.isEmpty() || secretoApp.isEmpty() || tokenRefresco.isEmpty() ? null : new DropboxCliente(claveApp, secretoApp, tokenRefresco, urlApi, urlContenido, java.time.Duration.ofSeconds(60));
            cargado = true;
        }
        return cliente;
    }

    public boolean habilitado() {
        return cliente() != null && !base.isEmpty();
    }

    public String rutaBase() {
        return unir(base);
    }

    private DropboxCliente exigir() {
        DropboxCliente c = cliente();
        if (c == null || base.isEmpty()) {
            throw new IllegalStateException("Dropbox no está configurado");
        }
        return c;
    }

    /** Cómo quedaría la carpeta de un proyecto y si ya existe (p. ej. la creó un administrativo a mano). */
    public Map<String, Object> vistaPrevia(LocalDate dia, String nombre) {
        DropboxCliente c = exigir();
        String carpeta = DropboxEstructura.nombreCarpetaProyecto(dia, nombre);
        String rutaAnio = unir(rutaBase(), String.valueOf(dia.getYear()));
        List<String> listadas = c.listarSubcarpetas(rutaAnio); // null = la carpeta del año no existe
        List<String> existentes = listadas == null ? List.of() : listadas;
        String prefijo = String.format("%02d%02d%02d_", dia.getYear() % 100, dia.getMonthValue(), dia.getDayOfMonth());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("folder", carpeta);
        m.put("path", unir(rutaAnio, carpeta));
        // Si la carpeta del año no existe, al crear el proyecto hará falta crearla (o que la cree administración).
        m.put("year_exists", listadas != null);
        m.put("exists", existentes.stream().anyMatch(n -> n.equalsIgnoreCase(carpeta)));
        // Carpetas de ese mismo día, por si la existente se llama algo distinto (hay que elegir una, no se adivina).
        m.put("same_day", existentes.stream().filter(n -> n.toLowerCase().startsWith(prefijo.toLowerCase())).sorted().toList());
        return m;
    }

    /**
     * Crea (o reutiliza, si ya existe con ese nombre) la carpeta del proyecto y sus subcarpetas fijas. Devuelve la ruta. Lo que ya existe no
     * se toca, y no se borra ni se mueve nada.
     */
    public String asegurarCarpetaProyecto(LocalDate dia, String nombre) {
        DropboxCliente c = exigir();
        String proyecto = DropboxEstructura.rutaProyecto(rutaBase(), dia, nombre);
        asegurarAnio(c, rutaBase(), unir(rutaBase(), String.valueOf(dia.getYear())), dia.getYear());
        c.asegurarCarpeta(proyecto);
        for (String sub : DropboxEstructura.SUBCARPETAS_FIJAS) { // las 7 carpetas de la estructura de la empresa
            c.asegurarCarpeta(DropboxEstructura.rutaSubcarpeta(proyecto, sub));
        }
        return proyecto;
    }

    /** Trozos de la ruta por debajo de la carpeta base; lanza IllegalArgumentException si queda fuera de ella (o lleva «..»). */
    private List<String> trozosRelativos(String ruta) {
        String b = rutaBase();
        String limpia = unir(ruta);
        for (String p : limpia.replaceAll("^/+|/+$", "").split("/")) {
            if (p.equals("..") || p.equals(".")) {
                throw new IllegalArgumentException("Ruta no válida");
            }
        }
        if (!limpia.equalsIgnoreCase(b) && !limpia.toLowerCase().startsWith(b.toLowerCase() + "/")) {
            throw new IllegalArgumentException("Esa carpeta queda fuera de la carpeta de trabajos de Dropbox");
        }
        String resto = limpia.substring(b.length()).replaceAll("^/+|/+$", "");
        return resto.isEmpty() ? List.of() : Arrays.asList(resto.split("/"));
    }

    /** Carpetas que hay dentro de `ruta` (por defecto, la carpeta base), para elegir una a mano. */
    public Map<String, Object> listarCarpetas(String ruta) {
        DropboxCliente c = exigir();
        String b = rutaBase();
        String actual = ruta == null || ruta.isEmpty() ? b : unir(ruta);
        List<String> partes = trozosRelativos(actual);
        List<String> listadas = c.listarSubcarpetas(actual);
        if (listadas == null) {
            throw new DropboxError("No existe la carpeta " + actual, 404);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", actual);
        m.put("base", b);
        m.put("depth", partes.size());
        m.put("can_choose", partes.size() == 2);
        m.put("folders", listadas.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        return m;
    }

    /** Valida una carpeta de proyecto que ya existe (base/año/proyecto) para usarla tal cual. No crea ni toca nada. */
    public String usarCarpetaExistente(String ruta) {
        DropboxCliente c = exigir();
        if (trozosRelativos(ruta).size() != 2) {
            throw new IllegalArgumentException("Elige la carpeta del proyecto, dentro de la carpeta de su año (base/año/proyecto)");
        }
        String limpia = unir(ruta);
        if (!c.carpetaExiste(limpia)) {
            throw new DropboxError("La carpeta " + limpia + " ya no existe en Dropbox", 404);
        }
        return limpia;
    }

    /** Crea las subcarpetas fijas que falten en la carpeta de un proyecto que ya existe; devuelve las creadas. Nunca crea la del proyecto. */
    public List<String> completarSubcarpetas(String rutaProyecto) {
        DropboxCliente c = exigir();
        if (!c.carpetaExiste(rutaProyecto)) {
            throw new DropboxError("No existe la carpeta del proyecto en Dropbox (" + rutaProyecto + ").", 409);
        }
        List<String> creadas = new ArrayList<>();
        for (String sub : DropboxEstructura.SUBCARPETAS_FIJAS) {
            String ruta = DropboxEstructura.rutaSubcarpeta(rutaProyecto, sub);
            if (!c.carpetaExiste(ruta)) {
                c.asegurarCarpeta(ruta);
                creadas.add(sub);
            }
        }
        return creadas;
    }

    /** La carpeta del año tiene que existir; solo se intenta crear si falta, y nunca más arriba. */
    private static void asegurarAnio(DropboxCliente c, String base, String rutaAnio, int anio) {
        if (c.carpetaExiste(rutaAnio)) {
            return;
        }
        if (!c.carpetaExiste(base)) {
            throw new DropboxError("No existe la carpeta base de Dropbox (" + base + ") ni la del año " + anio
                    + ". Revisa la carpeta base configurada o créala en Dropbox.", 409);
        }
        try {
            c.asegurarCarpeta(rutaAnio);
        } catch (DropboxError e) {
            if (e.getResumen().contains("no_write_permission") || e.getResumen().contains("insufficient")) {
                throw new DropboxError("Falta la carpeta del año " + anio + " en " + base + " y esta cuenta no puede crearla: "
                        + "pide a administración que la cree y vuelve a intentarlo.", 403, e.getResumen());
            }
            throw e;
        }
    }

    /** Sube un archivo a la subcarpeta indicada del proyecto (o a su raíz si no se indica). */
    public Map<String, Object> guardarArchivo(String rutaProyecto, String subcarpeta, String nombreArchivo, byte[] contenido) {
        DropboxCliente c = exigir();
        boolean conSub = subcarpeta != null && !subcarpeta.isEmpty();
        String carpeta = conSub ? DropboxEstructura.rutaSubcarpeta(rutaProyecto, subcarpeta) : rutaProyecto;
        if (conSub) {
            c.asegurarCarpeta(carpeta);
        }
        return c.subir(unir(carpeta, DropboxEstructura.limpiarNombreArchivo(nombreArchivo)), contenido);
    }

    public String enlaceTemporal(String ruta) {
        return exigir().enlaceTemporal(ruta);
    }

    // -------------------------------------------------------------------- fichas técnicas del inventario
    // Las fichas están en la carpeta «FICHAS TECNICAS» de la empresa, una carpeta por marca. La ficha de un producto es un PDF directamente
    // en la carpeta de su marca: no se entra en subcarpetas. En la base de datos solo se guarda «MARCA/archivo.pdf».

    public boolean fichasHabilitadas() {
        return cliente() != null && !raizFichas.isEmpty();
    }

    private DropboxCliente exigirFichas() {
        DropboxCliente c = cliente();
        if (c == null || raizFichas.isEmpty()) {
            throw new IllegalStateException("Las fichas técnicas no están configuradas");
        }
        return c;
    }

    /** «MARCA/archivo.pdf»: un PDF directamente en la carpeta de una marca, sin rutas raras. */
    public static boolean esRutaDeFicha(String relativa) {
        String[] partes = relativa.split("/", -1);
        if (partes.length != 2 || relativa.contains("\\")) {
            return false;
        }
        for (String p : partes) {
            if (p.isEmpty() || !p.strip().equals(p) || p.equals(".") || p.equals("..")) {
                return false;
            }
        }
        return partes[1].toLowerCase().endsWith(".pdf");
    }

    public static String marcaDeFicha(String relativa) {
        return relativa.split("/")[0];
    }

    private String rutaFicha(String relativa) {
        return unir(raizFichas, relativa);
    }

    /** PDF que hay en la carpeta de la marca (null si la marca aún no tiene carpeta). */
    public List<String> listarFichas(String marca) {
        List<String> nombres = exigirFichas().listarArchivos(unir(raizFichas, marca));
        return nombres == null ? null : nombres.stream().filter(n -> n.toLowerCase().endsWith(".pdf")).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public boolean fichaExiste(String relativa) {
        return exigirFichas().archivoExiste(rutaFicha(relativa));
    }

    /** Sube una ficha a la carpeta de su marca (se crea si falta) y devuelve «MARCA/archivo.pdf». Nunca sobrescribe. */
    public String guardarFicha(String marca, String nombreArchivo, byte[] contenido) {
        DropboxCliente c = exigirFichas();
        String carpeta = unir(raizFichas, marca);
        c.asegurarCarpeta(carpeta);
        Map<String, Object> guardado = c.subir(unir(carpeta, DropboxEstructura.limpiarNombreArchivo(nombreArchivo)), contenido);
        String ruta = String.valueOf(guardado.get("path"));
        return marca + "/" + ruta.substring(ruta.lastIndexOf('/') + 1);
    }

    /** Contenido de una ficha guardada, para enseñarla en la propia pantalla. */
    public byte[] bytesFicha(String relativa) {
        if (!esRutaDeFicha(relativa)) {
            throw new IllegalArgumentException("Ruta de ficha no válida");
        }
        return exigirFichas().descargar(rutaFicha(relativa));
    }
}
