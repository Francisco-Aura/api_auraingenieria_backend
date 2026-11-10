package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.dto.modulo_planificador.EquipoCrearRequest;
import api.aura.dto.modulo_planificador.EquipoDTO;
import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.entity.modulo_planificador.pl_HistorialPrecio;
import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import api.aura.repository.modulo_planificador.pl_HistorialPrecioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import api.aura.service.modulo_planificador.dropbox.DropboxAlmacen;
import api.aura.service.modulo_planificador.dropbox.DropboxError;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Catálogo de equipos: alta, edición, ofertas por proveedor, historial de precios y fichas (equivale a routes.py/equipment). */
@Service
@RequiredArgsConstructor
public class EquipoService {

    private final DropboxAlmacen dropbox;

    private static final String SIN_IDENTIFICAR = "Sin identificar";
    private static final String SIN_DROPBOX = "Las fichas técnicas no están configuradas en este servidor (Dropbox)";

    private final pl_EquipoRepository equipos;
    private final pl_HistorialPrecioRepository historial;
    private final MarcaService marcas;
    private final ProveedorService proveedores;
    private final PresupuestoService presupuestos;

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public Map<String, Object> catalogo() {
        List<Map<String, String>> partidas = CatalogoEquipos.PARTIDAS.stream()
                .map(p -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("key", p.key());
                    m.put("label", p.label());
                    return m;
                }).toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("partidas", partidas);
        r.put("categories", CatalogoEquipos.categorias());
        r.put("items", equipos.findAllByOrderByPosicionAsc().stream().map(EquipoService::aDto).toList());
        return r;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historialPrecios(String id) {
        buscar(id);
        List<Map<String, Object>> salida = new ArrayList<>();
        for (pl_HistorialPrecio p : historial.findByEquipoIdOrderByFechaAsc(id)) {
            Map<String, Object> punto = new LinkedHashMap<>();
            punto.put("at", p.getFecha().toString());
            punto.put("price", p.getPrecio());
            punto.put("supplier", p.getProveedor());
            punto.put("offers", p.getOfertas());
            punto.put("by", p.getPor());
            salida.add(punto);
        }
        return salida;
    }

    // -------------------------------------------------------------------- alta

    @Transactional
    public EquipoDTO crear(EquipoCrearRequest cuerpo) {
        comprobarCategoriaYPartida(cuerpo.getCategoria(), cuerpo.getPartida());
        String marca = comprobarMarca(cuerpo.getMarca());
        comprobarFicha(marca, cuerpo.getFichaTecnica());
        pl_Equipo e = new pl_Equipo();
        e.setId(UUID.randomUUID().toString());
        Integer ultima = equipos.maxPosicion();
        e.setPosicion((ultima == null ? 0 : ultima) + 1);
        e.setCategoria(cuerpo.getCategoria());
        e.setPartida(cuerpo.getPartida());
        e.setMarca(marca);
        e.setModelo(texto(cuerpo.getModelo()));
        e.setProveedor(comprobarProveedor(cuerpo.getProveedor()));
        e.setUnidad(cuerpo.getUnidad() == null ? "ud" : cuerpo.getUnidad());
        e.setPrecioUnitario(cuerpo.getPrecioUnitario());
        e.setPorModulo(cuerpo.isPorModulo());
        e.setCantidadDefecto(cuerpo.getCantidadDefecto());
        e.setAtributos(limpiarAtributos(cuerpo.getCategoria(), nuloAMapa(cuerpo.getAtributos()), null));
        e.setNotas(texto(cuerpo.getNotas()));
        e.setStock(cuerpo.getStock());
        e.setStockMinimo(cuerpo.getStockMinimo());
        e.setFichaTecnica(texto(cuerpo.getFichaTecnica()));
        List<Map<String, Object>> ofertas = limpiarOfertas(cuerpo.getOfertas() == null ? List.of() : cuerpo.getOfertas());
        e.setOfertas(ofertas);
        Set<String> cambios = new HashSet<>();
        cambios.add(ofertas.isEmpty() ? "supplier" : "offers");
        sincronizarOfertas(e, cambios);
        if (e.getPrecioUnitario() > 0) {
            anotarPrecio(e);
        }
        equipos.save(e);
        return aDto(e);
    }

    // ------------------------------------------------------------------- cambio

    /** Cambia datos de un equipo; solo se tocan las claves presentes (un valor null se ignora). */
    @Transactional
    public EquipoDTO actualizar(String id, Map<String, Object> cambios) {
        validarTipos(cambios);
        pl_Equipo e = buscar(id);
        String categoria = cadena(cambios.get("category"), e.getCategoria());
        String partida = cadena(cambios.get("group"), e.getPartida());
        comprobarCategoriaYPartida(categoria, partida);
        if (cambios.get("attrs") instanceof Map<?, ?> a) {
            cambios.put("attrs", limpiarAtributos(categoria, copiar(a), e.getAtributos()));
        }
        Object ficha = cambios.get("datasheet");
        if (ficha instanceof String f && !f.isEmpty()) {
            comprobarFicha(cambios.get("brand") != null ? String.valueOf(cambios.get("brand")) : e.getMarca(), f);
        }
        if (cambios.get("brand") != null) {
            cambios.put("brand", comprobarMarca(String.valueOf(cambios.get("brand"))));
        }
        if (cambios.get("supplier") != null) {
            cambios.put("supplier", comprobarProveedor(String.valueOf(cambios.get("supplier"))));
        }
        if (cambios.get("offers") instanceof List<?> l) {
            cambios.put("offers", limpiarOfertas(copiarLista(l)));
        }
        double precioAntes = e.getPrecioUnitario();
        List<String> ofertasAntes = firmaOfertas(e.getOfertas());
        aplicar(e, cambios);
        sincronizarOfertas(e, cambios.keySet());
        if (e.getPrecioUnitario() != precioAntes) {
            e.setMuestra(false);  // un precio puesto a mano ya no es el dato de prueba
        }
        if (e.getPrecioUnitario() != precioAntes || !ofertasAntes.equals(firmaOfertas(e.getOfertas()))) {
            // Cambiar un precio NO lo da por revisado: solo cuenta la revisión explícita («Precios revisados»)
            anotarPrecio(e);
        }
        equipos.save(e);
        return aDto(e);
    }

    // ------------------------------------------------------------------- fichas

    /** Quita el enlace de la ficha al producto (el archivo no se borra de Dropbox). */
    @Transactional
    public EquipoDTO quitarFicha(String id) {
        pl_Equipo e = buscar(id);
        e.setFichaTecnica("");
        equipos.save(e);
        return aDto(e);
    }

    private static final int MAX_FICHA = 30 * 1024 * 1024;

    private void exigirFichas() {
        if (!dropbox.fichasHabilitadas()) {
            throw new ErrorApi(HttpStatus.CONFLICT, SIN_DROPBOX);
        }
    }

    /** Adjunta la ficha técnica (PDF) de un producto: se guarda en Dropbox, en la carpeta de su marca. Un archivo del mismo nombre no se sobrescribe. */
    @Transactional
    public EquipoDTO subirFicha(String id, String nombre, byte[] contenido) {
        pl_Equipo e = buscar(id);
        exigirFichas();
        if (e.getMarca().isEmpty()) {
            throw invalido("Elige primero la marca del producto: la ficha se guarda en la carpeta de su marca");
        }
        if (contenido.length == 0) {
            throw invalido("El archivo está vacío");
        }
        if (contenido.length > MAX_FICHA) {
            throw new ErrorApi(HttpStatus.PAYLOAD_TOO_LARGE, "La ficha pesa más de 30 MB");
        }
        if (contenido.length < 5 || !new String(contenido, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1).equals("%PDF-")) {
            throw invalido("La ficha técnica tiene que ser un PDF");
        }
        String archivo = nombre.toLowerCase().endsWith(".pdf") ? nombre : nombre + ".pdf";
        try {
            e.setFichaTecnica(dropbox.guardarFicha(e.getMarca(), archivo, contenido));
        } catch (DropboxError ex) {
            throw new ErrorApi(HttpStatus.BAD_GATEWAY, "No se pudo guardar la ficha en Dropbox: " + ex.getMessage());
        }
        equipos.save(e);
        return aDto(e);
    }

    /** Los PDF que ya hay en la carpeta de la marca del producto, para enlazar uno en vez de subirlo. */
    @Transactional(readOnly = true)
    public Map<String, Object> opcionesDeFicha(String id) {
        pl_Equipo e = buscar(id);
        exigirFichas();
        Map<String, Object> m = new LinkedHashMap<>();
        if (e.getMarca().isEmpty()) {
            m.put("brand", "");
            m.put("files", List.of());
            m.put("folder", false);
            return m;
        }
        List<String> archivos;
        try {
            archivos = dropbox.listarFichas(e.getMarca());
        } catch (DropboxError ex) {
            throw new ErrorApi(HttpStatus.BAD_GATEWAY, "No se pudo consultar Dropbox: " + ex.getMessage());
        }
        m.put("brand", e.getMarca());
        m.put("files", archivos == null ? List.of() : archivos);
        m.put("folder", archivos != null);
        return m;
    }

    /** El PDF de la ficha del producto, para verlo en pantalla. */
    @Transactional(readOnly = true)
    public byte[] verFicha(String id) {
        pl_Equipo e = equipos.findById(id).orElse(null);
        if (e == null || e.getFichaTecnica().isEmpty()) {
            throw new ErrorApi(HttpStatus.NOT_FOUND, "Este producto no tiene ficha técnica adjunta");
        }
        if (!dropbox.fichasHabilitadas()) {
            throw new ErrorApi(HttpStatus.SERVICE_UNAVAILABLE, "Las fichas técnicas no están disponibles en este servidor");
        }
        try {
            return dropbox.bytesFicha(e.getFichaTecnica());
        } catch (IllegalArgumentException ex) {
            throw invalido(ex.getMessage());
        } catch (DropboxError ex) {
            boolean noEsta = ex.getEstado() != null && (ex.getEstado() == 404 || ex.getEstado() == 409);
            throw new ErrorApi(noEsta ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY, "No se pudo abrir la ficha en Dropbox: " + ex.getMessage());
        }
    }

    public static String nombreDeFicha(String ficha) {
        return ficha == null || ficha.isEmpty() ? "" : ficha.substring(ficha.lastIndexOf('/') + 1);
    }

    @Transactional(readOnly = true)
    public String nombreFichaDe(String id) {
        return nombreDeFicha(buscar(id).getFichaTecnica());
    }

    // ------------------------------------------------------------------ borrado

    @Transactional
    public void eliminar(String id) {
        pl_Equipo e = buscar(id);
        List<String> presupuestos = presupuestosQueUsan(id);
        if (!presupuestos.isEmpty()) {
            String nombres = String.join(", ", presupuestos.stream().sorted().limit(5).toList());
            throw new ErrorApi(HttpStatus.CONFLICT, "Este producto está en " + presupuestos.size()
                    + " presupuesto(s) guardado(s) (" + nombres + "): no se puede eliminar mientras se use.");
        }
        historial.deleteByEquipoId(id);
        equipos.delete(e);
    }

    /** Nombres de los presupuestos guardados que usan el producto. */
    protected List<String> presupuestosQueUsan(String id) {
        return presupuestos.presupuestosQueUsan(id);
    }

    // ----------------------------------------------------------------- internos

    private pl_Equipo buscar(String id) {
        return equipos.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Equipo no encontrado"));
    }

    private static ErrorApi invalido(String mensaje) {
        return new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private static String cadena(Object valor, String porDefecto) {
        return valor == null ? porDefecto : String.valueOf(valor);
    }

    private static Map<String, Object> nuloAMapa(Map<String, Object> m) {
        return m == null ? new LinkedHashMap<>() : m;
    }

    private static Map<String, Object> copiar(Map<?, ?> origen) {
        Map<String, Object> salida = new LinkedHashMap<>();
        origen.forEach((k, v) -> salida.put(String.valueOf(k), v));
        return salida;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> copiarLista(List<?> origen) {
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Object o : origen) {
            if (!(o instanceof Map<?, ?> m)) {
                throw invalido("Cada oferta tiene que ser un objeto");
            }
            salida.add(copiar(m));
        }
        return salida;
    }

    /** Las mismas comprobaciones de tipo y rango que hace el Planificador actual al recibir el cuerpo. */
    private static void validarTipos(Map<String, Object> c) {
        for (String clave : List.of("unit_price", "default_qty", "stock", "stock_min")) {
            Object v = c.get(clave);
            if (v == null) {
                continue;
            }
            if (!(v instanceof Number n) || n.doubleValue() < 0 || Double.isNaN(n.doubleValue())) {
                throw invalido(clave + ": tiene que ser un número de 0 o más");
            }
        }
        for (String clave : List.of("category", "group", "brand", "model", "supplier", "unit", "notes", "datasheet")) {
            Object v = c.get(clave);
            if (v != null && !(v instanceof String)) {
                throw invalido(clave + ": tiene que ser texto");
            }
        }
        if (c.get("per_module") != null && !(c.get("per_module") instanceof Boolean)) {
            throw invalido("per_module: tiene que ser verdadero o falso");
        }
        if (c.get("attrs") != null && !(c.get("attrs") instanceof Map)) {
            throw invalido("attrs: tiene que ser un objeto");
        }
        if (c.get("offers") != null && (!(c.get("offers") instanceof List<?> l) || l.size() > 30)) {
            throw invalido("offers: tiene que ser una lista de 30 como máximo");
        }
    }

    private void aplicar(pl_Equipo e, Map<String, Object> c) {
        c.forEach((clave, valor) -> {
            if (valor == null) {
                return;  // ningún campo admite null: se ignora en vez de dejarlo vacío a medias
            }
            switch (clave) {
                case "category" -> e.setCategoria((String) valor);
                case "group" -> e.setPartida((String) valor);
                case "brand" -> e.setMarca((String) valor);
                case "model" -> e.setModelo((String) valor);
                case "supplier" -> e.setProveedor((String) valor);
                case "unit" -> e.setUnidad((String) valor);
                case "unit_price" -> e.setPrecioUnitario(((Number) valor).doubleValue());
                case "per_module" -> e.setPorModulo((Boolean) valor);
                case "default_qty" -> e.setCantidadDefecto(((Number) valor).doubleValue());
                case "attrs" -> e.setAtributos(copiar((Map<?, ?>) valor));
                case "notes" -> e.setNotas((String) valor);
                case "stock" -> e.setStock(((Number) valor).doubleValue());
                case "stock_min" -> e.setStockMinimo(((Number) valor).doubleValue());
                case "datasheet" -> e.setFichaTecnica((String) valor);
                case "offers" -> e.setOfertas(copiarLista((List<?>) valor));
                default -> { /* clave desconocida: se ignora, como hace el Planificador actual */ }
            }
        });
    }

    private static void comprobarCategoriaYPartida(String categoria, String partida) {
        if (!CatalogoEquipos.clavesCategoria().contains(categoria)) {
            throw invalido("Categoría desconocida: " + categoria);
        }
        if (!CatalogoEquipos.partidaExiste(partida)) {
            throw invalido("Partida desconocida: " + partida);
        }
        if (!CatalogoEquipos.categoriaAdmitePartida(categoria, partida)) {
            throw invalido("La categoría «" + categoria + "» no puede estar en la partida «" + partida + "»");
        }
    }

    /** Las características con lista cerrada llevan uno de sus valores; un valor antiguo no tocado se respeta. */
    private static Map<String, Object> limpiarAtributos(String categoria, Map<String, Object> atributos, Map<String, Object> actuales) {
        Map<String, Object> salida = new LinkedHashMap<>(atributos);
        CatalogoEquipos.clavesConLista(categoria).forEach((clave, opciones) -> {
            Object valor = salida.get(clave);
            if (valor == null || String.valueOf(valor).trim().isEmpty()) {
                return;
            }
            String canonico = CatalogoEquipos.opcionCanonica(categoria, clave, valor);
            if (canonico == null) {
                if (actuales != null && Objects.equals(actuales.get(clave), valor)) {
                    return;
                }
                throw invalido("«" + valor + "» no es un valor válido de «" + clave + "»: elige uno de la lista ("
                        + String.join(", ", opciones) + ")");
            }
            salida.put(clave, canonico);
        });
        return salida;
    }

    private String comprobarProveedor(String proveedor) {
        if (proveedor == null || proveedor.isBlank()) {
            return "";
        }
        String encontrado = Nombres.canonico(proveedor, proveedores.nombresExistentes());
        if (encontrado == null) {
            throw invalido("El proveedor «" + proveedor.trim() + "» no está en la lista: créalo primero");
        }
        return encontrado;
    }

    private String comprobarMarca(String marca) {
        if (marca == null || marca.isBlank()) {
            return "";
        }
        String encontrada = Nombres.canonico(marca, marcas.nombresExistentes());
        if (encontrada == null) {
            throw invalido("La marca «" + marca.trim() + "» no está en la lista: créala primero");
        }
        return encontrada;
    }

    /** La ficha es un PDF directamente en la carpeta de la marca del producto («MARCA/archivo.pdf»). */
    private static void comprobarFicha(String marca, String relativa) {
        if (relativa == null || relativa.isEmpty()) {
            return;
        }
        String[] partes = relativa.split("/", -1);
        boolean valida = partes.length == 2
                && Arrays.stream(partes).allMatch(p -> p.strip().equals(p) && !p.isEmpty() && !p.equals(".") && !p.equals(".."))
                && partes[1].toLowerCase().endsWith(".pdf")
                && !relativa.contains("\\");
        if (!valida) {
            throw invalido("La ficha tiene que ser un PDF de la carpeta de su marca (MARCA/archivo.pdf)");
        }
        if (marca == null || marca.isEmpty() || !partes[0].equalsIgnoreCase(marca)) {
            throw invalido("La ficha tiene que estar en la carpeta de la marca del producto");
        }
    }

    /** Ofertas validadas: cada proveedor está en la lista, aparece una vez y su precio es un número de 0 o más. */
    private List<Map<String, Object>> limpiarOfertas(List<Map<String, Object>> ofertas) {
        List<Map<String, Object>> salida = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (Map<String, Object> cruda : ofertas) {
            String proveedor = comprobarProveedor(cadena(cruda.get("supplier"), ""));
            if (proveedor.isEmpty()) {
                throw invalido("Cada oferta necesita un proveedor");
            }
            if (!vistos.add(proveedor.toLowerCase())) {
                throw invalido("El proveedor " + proveedor + " está repetido en el producto");
            }
            double precio;
            try {
                Object p = cruda.get("price");
                precio = p == null || String.valueOf(p).isEmpty() ? 0 : Double.parseDouble(String.valueOf(p));
            } catch (NumberFormatException ex) {
                throw invalido("El precio de " + proveedor + " no es un número");
            }
            precio = redondear4(precio);
            if (!(precio >= 0) || Double.isInfinite(precio)) {
                throw invalido("El precio de " + proveedor + " tiene que ser 0 o más");
            }
            Map<String, Object> oferta = new LinkedHashMap<>();
            oferta.put("supplier", proveedor);
            oferta.put("price", precio);
            oferta.put("ref", recortar(cadena(cruda.get("ref"), "").trim(), 80));
            oferta.put("note", recortar(cadena(cruda.get("note"), "").trim(), 200));
            salida.add(oferta);
        }
        return salida;
    }

    private static double redondear4(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return v;
        }
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_EVEN).doubleValue();
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * Deja de acuerdo el proveedor principal y su precio con las ofertas del producto. El principal es el de la oferta que
     * da el precio del producto. Si se mandan las ofertas, el precio sale de la oferta del principal (si no está entre ellas,
     * pasa a ser el primero); si solo cambia el precio, se lo lleva la oferta del principal; si cambia el principal, el
     * precio pasa a ser el de su oferta (o se crea esa oferta con el precio actual).
     */
    private static void sincronizarOfertas(pl_Equipo e, Set<String> cambios) {
        List<Map<String, Object>> ofertas = new ArrayList<>();
        for (Map<String, Object> o : e.getOfertas()) {
            ofertas.add(new LinkedHashMap<>(o));
        }
        Map<String, Map<String, Object>> porNombre = new LinkedHashMap<>();
        ofertas.forEach(o -> porNombre.put(String.valueOf(o.get("supplier")).toLowerCase(), o));
        String principal = e.getProveedor().toLowerCase();
        if (cambios.contains("offers")) {
            if (!ofertas.isEmpty()) {
                if (!porNombre.containsKey(principal)) {
                    e.setProveedor(String.valueOf(ofertas.get(0).get("supplier")));
                    principal = e.getProveedor().toLowerCase();
                }
                e.setPrecioUnitario(((Number) porNombre.get(principal).get("price")).doubleValue());
            }
        } else if (!e.getProveedor().isEmpty()) {
            if (porNombre.containsKey(principal)) {
                if (cambios.contains("supplier") && !cambios.contains("unit_price")) {
                    e.setPrecioUnitario(((Number) porNombre.get(principal).get("price")).doubleValue());
                } else {
                    porNombre.get(principal).put("price", e.getPrecioUnitario());
                }
            } else {
                Map<String, Object> nueva = new LinkedHashMap<>();
                nueva.put("supplier", e.getProveedor());
                nueva.put("price", e.getPrecioUnitario());
                nueva.put("ref", "");
                nueva.put("note", "");
                ofertas.add(nueva);
            }
        }
        e.setOfertas(ofertas);
    }

    private static List<String> firmaOfertas(List<Map<String, Object>> ofertas) {
        return ofertas.stream().map(o -> o.get("supplier") + "=" + ((Number) o.get("price")).doubleValue()).toList();
    }

    /** Anota en el historial el precio del producto y el de sus ofertas tal como están ahora. */
    private void anotarPrecio(pl_Equipo e) {
        pl_HistorialPrecio p = new pl_HistorialPrecio();
        p.setId(UUID.randomUUID().toString());
        p.setEquipoId(e.getId());
        p.setPrecio(e.getPrecioUnitario());
        p.setProveedor(e.getProveedor());
        List<Map<String, Object>> ofertas = new ArrayList<>();
        for (Map<String, Object> o : e.getOfertas()) {
            Map<String, Object> breve = new LinkedHashMap<>();
            breve.put("supplier", o.get("supplier"));
            breve.put("price", o.get("price"));
            ofertas.add(breve);
        }
        p.setOfertas(ofertas);
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        String nombre = u == null || u.nombre() == null || u.nombre().isBlank() ? SIN_IDENTIFICAR : u.nombre();
        p.setPor(recortar(nombre, 60));
        p.setFecha(LocalDateTime.now(ZoneOffset.UTC));
        historial.save(p);
    }

    static EquipoDTO aDto(pl_Equipo e) {
        return new EquipoDTO(e.getId(), e.getPosicion(), e.getCategoria(), e.getPartida(), e.getMarca(), e.getModelo(),
                e.getProveedor(), e.getUnidad(), e.getPrecioUnitario(), e.isPorModulo(), e.getCantidadDefecto(),
                e.getAtributos(), e.getNotas(), e.isMuestra(), e.getStock(), e.getStockMinimo(), e.getFichaTecnica(),
                e.getOfertas(), e.getPrecioRevisadoEl());
    }
}
