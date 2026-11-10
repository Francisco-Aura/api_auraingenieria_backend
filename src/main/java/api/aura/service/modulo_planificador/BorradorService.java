package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.entity.modulo_planificador.pl_Borrador;
import api.aura.repository.modulo_planificador.pl_BorradorRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static api.aura.service.modulo_planificador.TareaProyectoService.invalido;

/** Borradores compartidos del alta de proyecto: si dos personas guardan sobre la misma versión, la segunda recibe un 409. */
@Service
@RequiredArgsConstructor
public class BorradorService {

    private static final int MAX_CARACTERES = 300_000;
    private static final String NO_EXISTE = "Borrador no encontrado (puede que lo hayan descartado o convertido en proyecto)";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    private final pl_BorradorRepository borradores;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return borradores.findAllByOrderByActualizadoElDesc(PageRequest.of(0, 200)).stream().map(BorradorService::resumen).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(String id) {
        pl_Borrador b = borradores.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, NO_EXISTE));
        Map<String, Object> m = resumen(b);
        m.put("data", b.getDatos());
        return m;
    }

    @Transactional
    public Map<String, Object> crear(Map<String, Object> cuerpo) {
        pl_Borrador b = new pl_Borrador();
        b.setId(UUID.randomUUID().toString());
        rellenar(b, cuerpo);
        b.setActualizadoEl(ahora());
        b.setActualizadoPor(actor());
        borradores.save(b);
        Map<String, Object> m = resumen(b);
        m.put("data", b.getDatos());
        return m;
    }

    @Transactional
    public Map<String, Object> actualizar(String id, Map<String, Object> cuerpo) {
        pl_Borrador b = borradores.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, NO_EXISTE));
        Object base = cuerpo.get("base_updated_at");
        if (base != null && !String.valueOf(base).isEmpty() && !String.valueOf(base).equals(iso(b.getActualizadoEl()))) {
            String quien = b.getActualizadoPor().isEmpty() ? "Otra persona" : b.getActualizadoPor();
            throw new ErrorApi(HttpStatus.CONFLICT, quien + " ha guardado este borrador mientras lo editabas. Recárgalo para ver sus cambios.");
        }
        rellenar(b, cuerpo);
        b.setActualizadoEl(ahora());
        b.setActualizadoPor(actor());
        borradores.save(b);
        return resumen(b);
    }

    @Transactional
    public void eliminar(String id) {
        if (borradores.existsById(id)) {  // borrar uno que ya no existe no es un error
            borradores.deleteById(id);
        }
    }

    @SuppressWarnings("unchecked")
    private void rellenar(pl_Borrador b, Map<String, Object> cuerpo) {
        Object nombre = cuerpo.get("name");
        String n = nombre == null ? "" : String.valueOf(nombre).strip();
        b.setNombre(n.length() > 200 ? n.substring(0, 200) : n);
        Object datos = cuerpo.get("data");
        Map<String, Object> m = datos instanceof Map<?, ?> d ? new LinkedHashMap<>((Map<String, Object>) d) : new LinkedHashMap<>();
        try {
            if (json.writeValueAsString(m).length() > MAX_CARACTERES) {
                throw new ErrorApi(HttpStatus.PAYLOAD_TOO_LARGE, "El borrador es demasiado grande");
            }
        } catch (JsonProcessingException e) {
            throw invalido("El borrador no tiene el formato esperado");
        }
        b.setDatos(m);
    }

    private static Map<String, Object> resumen(pl_Borrador b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("name", b.getNombre());
        m.put("updated_at", iso(b.getActualizadoEl()));
        m.put("updated_by", b.getActualizadoPor());
        return m;
    }

    private static String iso(LocalDateTime h) {
        return h.atOffset(ZoneOffset.UTC).format(ISO);
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static String actor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        return u == null || u.nombre() == null || u.nombre().isBlank() ? "Sin identificar" : u.nombre();
    }
}
