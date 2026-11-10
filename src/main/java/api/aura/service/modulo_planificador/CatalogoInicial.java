package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.entity.modulo_planificador.pl_Marca;
import api.aura.entity.modulo_planificador.pl_Meta;
import api.aura.entity.modulo_planificador.pl_Proveedor;
import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import api.aura.repository.modulo_planificador.pl_MarcaRepository;
import api.aura.repository.modulo_planificador.pl_MetaRepository;
import api.aura.repository.modulo_planificador.pl_ProveedorRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Catálogo con el que arranca una instalación nueva (equivale al arranque de db.py): los 149 productos del inventario de ejemplo, la lista de
 * marcas con sus logos y la lista de proveedores. Cada paso se hace una sola vez (queda anotado en pl_meta) y solo si no hay ya datos: una base
 * migrada desde el Planificador anterior conserva su inventario tal cual. Se enciende con {@code planificador.catalogo-inicial=true}.
 */
@Component
@ConditionalOnProperty(name = "planificador.catalogo-inicial", havingValue = "true")
@RequiredArgsConstructor
public class CatalogoInicial implements ApplicationRunner {

    static final String CLAVE_EQUIPOS = "equipment_seed_v1";
    static final String CLAVE_MARCAS = "brands_seed_v1";
    static final String CLAVE_PROVEEDORES = "suppliers_seed_v1";
    static final String CLAVE_REVISIONES = "price_reviews_reset_v1";

    /** Las carpetas de marca de «FICHAS TECNICAS» más las marcas que ya usaban productos del catálogo (como brands.py). */
    static final List<String> MARCAS = List.of(
            "ABB", "ACTARIS", "AE SOLAR", "AIKO", "ATERSA", "AXPERT", "BYD", "CANADIAN", "DANFOSS", "DEYE", "DISANLUZ", "DYNESS", "ENNOVA", "ENPHASE",
            "FRONIUS", "FUJI", "GENERAL CABLE", "HUAWEI", "INGETEAM", "JA SOLAR", "JANITZA", "JINKO", "LEGRAND", "LONGI", "MAKITA", "PEIMAR", "PEMSA",
            "PHOENIX CONTACT", "PHONO SOLAR", "POWER ELECTRONICS", "PRISMA", "PRYSMIAN", "PYLONTECH", "REC", "RISEN", "RS ISOLSEC", "SEG", "SHELLY",
            "SIGENERGY", "SMA", "SOLAREDGE", "SOLIS", "SUNGROW", "SUNSUPPORT", "TAB", "TOP CABLE", "TORYTRANS", "TRINA", "VICTRON", "WEKO", "WURTH",
            "ZNSHINE");

    private final pl_EquipoRepository equipos;
    private final pl_MarcaRepository marcas;
    private final pl_ProveedorRepository proveedores;
    private final pl_MetaRepository meta;
    private final ObjectMapper json;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        sembrarEquipos();
        sembrarMarcas();
        sembrarProveedores();
        sembrarLogos();
        reiniciarRevisionesDePrecios();
    }

    private boolean hecho(String clave) {
        return meta.existsById(clave);
    }

    private void anotar(String clave) {
        meta.save(new pl_Meta(clave, "ok"));
    }

    private void sembrarEquipos() throws IOException {
        if (hecho(CLAVE_EQUIPOS)) {
            return;
        }
        if (equipos.count() == 0) {
            try (InputStream in = getClass().getResourceAsStream("/catalogo/equipment_seed.json")) {
                if (in == null) {
                    return;
                }
                List<Map<String, Object>> semilla = json.readValue(in, new TypeReference<>() {
                });
                int posicion = 0;
                for (Map<String, Object> s : semilla) {
                    pl_Equipo e = new pl_Equipo();
                    e.setId(texto(s, "id"));
                    e.setPosicion(posicion++);
                    e.setCategoria(texto(s, "category"));
                    e.setPartida(texto(s, "group"));
                    e.setMarca(texto(s, "brand"));
                    e.setModelo(texto(s, "model"));
                    e.setProveedor(texto(s, "supplier"));
                    e.setUnidad(s.get("unit") == null ? "ud" : texto(s, "unit"));
                    e.setPrecioUnitario(numero(s, "unit_price", 0));
                    e.setPorModulo(Boolean.TRUE.equals(s.get("per_module")));
                    e.setCantidadDefecto(numero(s, "default_qty", 1));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> atributos = s.get("attrs") instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
                    e.setAtributos(atributos);
                    e.setNotas(texto(s, "notes"));
                    e.setMuestra(Boolean.TRUE.equals(s.get("sample")));
                    e.setStock(numero(s, "stock", 0));
                    e.setStockMinimo(numero(s, "stock_min", 0));
                    e.setFichaTecnica(texto(s, "datasheet"));
                    equipos.save(e);
                }
            }
        }
        anotar(CLAVE_EQUIPOS);
    }

    private void sembrarMarcas() {
        if (hecho(CLAVE_MARCAS)) {
            return;
        }
        Set<String> existentes = new HashSet<>();
        marcas.findAll().forEach(m -> existentes.add(m.getNombre()));
        for (String nombre : MARCAS) {
            if (!existentes.contains(nombre)) {
                pl_Marca m = new pl_Marca();
                m.setNombre(nombre);
                marcas.save(m);
            }
        }
        // Cada producto con el nombre exacto de su marca (HUAWEI y no Huawei)
        for (pl_Equipo e : equipos.findAll()) {
            String limpia = String.join(" ", e.getMarca().trim().split("\\s+")).toLowerCase(Locale.ROOT);
            MARCAS.stream().filter(b -> b.toLowerCase(Locale.ROOT).equals(limpia)).findFirst().ifPresent(exacta -> {
                if (!exacta.equals(e.getMarca())) {
                    e.setMarca(exacta);
                    equipos.save(e);
                }
            });
        }
        anotar(CLAVE_MARCAS);
    }

    /** Los proveedores con los que se trabaja (sustituyen a los que traía el catálogo de ejemplo). */
    static final List<String> PROVEEDORES = List.of("BET SOLAR", "SOLAR TODAY", "SACLIMA", "SONEPAR", "PEISA", "ENNOVA", "SOPORTES SOLARES", "SOLARBLOC");

    /**
     * Como acaba el arranque del Planificador anterior: el proveedor y el precio de cada producto pasan a ser su primera oferta, y la lista
     * de proveedores es PROVEEDORES; los productos cuyo proveedor no está en la lista se quedan sin proveedor (conservan su precio).
     */
    private void sembrarProveedores() {
        if (hecho(CLAVE_PROVEEDORES)) {
            return;
        }
        Map<String, String> permitidos = new LinkedHashMap<>();
        PROVEEDORES.forEach(n -> permitidos.put(n.toLowerCase(Locale.ROOT), n));
        for (pl_Equipo e : equipos.findAll()) {
            String limpio = String.join(" ", e.getProveedor().trim().split("[ \t]+"));
            List<Map<String, Object>> ofertas = new ArrayList<>(e.getOfertas());
            if (!limpio.isEmpty() && ofertas.isEmpty()) {
                Map<String, Object> oferta = new LinkedHashMap<>();
                oferta.put("supplier", limpio);
                oferta.put("price", e.getPrecioUnitario());
                oferta.put("ref", "");
                oferta.put("note", "");
                ofertas.add(oferta);
            }
            ofertas.removeIf(o -> !permitidos.containsKey(String.valueOf(o.get("supplier")).toLowerCase(Locale.ROOT)));
            String proveedor = permitidos.getOrDefault(limpio.toLowerCase(Locale.ROOT), "");
            if (!proveedor.equals(e.getProveedor()) || ofertas.size() != e.getOfertas().size() || e.getOfertas().isEmpty() != ofertas.isEmpty()) {
                e.setProveedor(proveedor);
                e.setOfertas(ofertas);
                equipos.save(e);
            }
        }
        Set<String> existentes = new HashSet<>();
        proveedores.findAll().forEach(p -> existentes.add(p.getNombre()));
        for (String nombre : PROVEEDORES) {
            if (!existentes.contains(nombre)) {
                proveedores.save(new pl_Proveedor(nombre));
            }
        }
        anotar(CLAVE_PROVEEDORES);
    }

    /** Las marcas sin logo reciben el de la carpeta de logos (un PNG o SVG con el nombre de la marca), si lo hay. No pisa uno subido a mano. */
    private void sembrarLogos() throws IOException {
        for (pl_Marca m : marcas.findAll()) {
            if (m.getLogo() != null && !m.getLogo().isEmpty()) {
                continue;
            }
            for (String[] tipo : new String[][]{{".png", "image/png"}, {".svg", "image/svg+xml"}}) {
                try (InputStream in = getClass().getResourceAsStream("/catalogo/logos/" + m.getNombre() + tipo[0])) {
                    if (in != null) {
                        m.setLogo("data:" + tipo[1] + ";base64," + Base64.getEncoder().encodeToString(in.readAllBytes()));
                        marcas.save(m);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Una sola vez: ningún precio figura como revisado. Hasta ahora crear un producto o cambiar su precio contaba como revisión sin que nadie la
     * hubiera hecho; desde ahora solo cuenta marcarlo como revisado a propósito.
     */
    private void reiniciarRevisionesDePrecios() {
        if (hecho(CLAVE_REVISIONES)) {
            return;
        }
        for (pl_Equipo e : equipos.findAll()) {
            if (e.getPrecioRevisadoEl() != null) {
                e.setPrecioRevisadoEl(null);
                equipos.save(e);
            }
        }
        meta.deleteById(RevisionPreciosService.META_BASE);
        meta.deleteById(RevisionPreciosService.META_AVISADO);
        anotar(CLAVE_REVISIONES);
    }

    private static String texto(Map<String, Object> s, String clave) {
        Object v = s.get(clave);
        return v == null ? "" : String.valueOf(v);
    }

    private static double numero(Map<String, Object> s, String clave, double defecto) {
        return s.get(clave) instanceof Number n ? n.doubleValue() : defecto;
    }
}
