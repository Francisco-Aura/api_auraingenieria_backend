package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.entity.modulo_planificador.pl_Marca;
import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import api.aura.repository.modulo_planificador.pl_MarcaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MarcaService {

    private static final Pattern PATRON_LOGO =
            Pattern.compile("^data:(image/(?:png|jpeg|webp|svg\\+xml));base64,([A-Za-z0-9+/=]+)$");
    private static final int LOGO_MAX_BYTES = 400_000;

    private final pl_MarcaRepository marcas;
    private final pl_EquipoRepository equipos;

    /** Imagen decodificada de un logo. */
    public record Imagen(String tipo, byte[] contenido) {
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return marcas.findAll().stream()
                .sorted(Comparator.comparing(m -> m.getNombre().toLowerCase()))
                .map(m -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("name", m.getNombre());
                    fila.put("products", equipos.countByMarca(m.getNombre()));
                    fila.put("logo", !m.getLogo().isEmpty());
                    return fila;
                }).toList();
    }

    @Transactional
    public Map<String, Object> crear(String nombreEscrito) {
        String nombre = Nombres.limpiar(nombreEscrito).toUpperCase();
        if (nombre.isEmpty()) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "Escribe el nombre de la marca");
        }
        if (nombre.length() > 60 || nombre.contains("/") || nombre.contains("\\")) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "El nombre de la marca no es válido");
        }
        String existente = Nombres.canonico(nombre, nombresExistentes());
        if (existente != null) {
            throw new ErrorApi(HttpStatus.CONFLICT, "La marca " + existente + " ya existe");
        }
        marcas.save(new pl_Marca(nombre, ""));
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("name", nombre);
        respuesta.put("products", 0);
        return respuesta;
    }

    @Transactional
    public void eliminar(String nombre) {
        pl_Marca marca = marcas.findById(nombre).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "La marca no existe"));
        long usadas = equipos.countByMarca(nombre);
        if (usadas > 0) {
            throw new ErrorApi(HttpStatus.CONFLICT, "La marca " + nombre + " está en " + usadas
                    + " producto(s): cámbiales la marca antes de eliminarla.");
        }
        marcas.delete(marca);
    }

    @Transactional
    public Map<String, Object> guardarLogo(String nombre, String logo) {
        pl_Marca marca = marcas.findById(nombre).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "La marca no existe"));
        if (logo == null || logo.length() > LOGO_MAX_BYTES * 4 / 3 || !PATRON_LOGO.matcher(logo).matches()) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El logo tiene que ser una imagen PNG, JPG, WebP o SVG de menos de 400 KB");
        }
        marca.setLogo(logo);
        marcas.save(marca);
        return resultadoLogo(marca, true);
    }

    @Transactional
    public Map<String, Object> borrarLogo(String nombre) {
        pl_Marca marca = marcas.findById(nombre).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "La marca no existe"));
        marca.setLogo("");
        marcas.save(marca);
        return resultadoLogo(marca, false);
    }

    @Transactional(readOnly = true)
    public Imagen logo(String nombre) {
        pl_Marca marca = marcas.findById(nombre).orElse(null);
        Matcher m = marca != null && !marca.getLogo().isEmpty() ? PATRON_LOGO.matcher(marca.getLogo()) : null;
        if (m == null || !m.matches()) {
            throw new ErrorApi(HttpStatus.NOT_FOUND, "Esta marca no tiene logo");
        }
        return new Imagen(m.group(1), Base64.getDecoder().decode(m.group(2)));
    }

    /** Nombres de todas las marcas (para comprobar que la marca de un producto está en la lista). */
    public List<String> nombresExistentes() {
        return marcas.findAll().stream().map(pl_Marca::getNombre).toList();
    }

    private static Map<String, Object> resultadoLogo(pl_Marca marca, boolean tiene) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", marca.getNombre());
        r.put("logo", tiene);
        return r;
    }
}
