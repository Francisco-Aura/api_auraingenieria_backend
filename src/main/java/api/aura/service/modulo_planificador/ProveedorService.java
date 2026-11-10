package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.entity.modulo_planificador.pl_Proveedor;
import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import api.aura.repository.modulo_planificador.pl_ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final pl_ProveedorRepository proveedores;
    private final pl_EquipoRepository equipos;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        List<pl_Equipo> todos = equipos.findAll();
        return proveedores.findAll().stream()
                .sorted(Comparator.comparing(p -> p.getNombre().toLowerCase()))
                .map(p -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("name", p.getNombre());
                    fila.put("products", todos.stream().filter(e -> p.getNombre().equals(e.getProveedor())).count());
                    return fila;
                }).toList();
    }

    @Transactional
    public Map<String, Object> crear(String nombreEscrito) {
        String nombre = Nombres.limpiar(nombreEscrito).toUpperCase();
        if (nombre.isEmpty()) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "Escribe el nombre del proveedor");
        }
        if (nombre.length() > 80) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "El nombre del proveedor es demasiado largo");
        }
        String existente = Nombres.canonico(nombre, nombresExistentes());
        if (existente != null) {
            throw new ErrorApi(HttpStatus.CONFLICT, "El proveedor " + existente + " ya existe");
        }
        proveedores.save(new pl_Proveedor(nombre));
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("name", nombre);
        respuesta.put("products", 0);
        return respuesta;
    }

    @Transactional
    public void eliminar(String nombre) {
        pl_Proveedor proveedor = proveedores.findById(nombre)
                .orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "El proveedor no existe"));
        long usados = equipos.findAll().stream()
                .filter(e -> nombre.equals(e.getProveedor())
                        || e.getOfertas().stream().anyMatch(o -> nombre.equals(o.get("supplier"))))
                .count();
        if (usados > 0) {
            throw new ErrorApi(HttpStatus.CONFLICT, "El proveedor " + nombre + " está en " + usados
                    + " producto(s): cámbiales el proveedor antes de eliminarlo.");
        }
        proveedores.delete(proveedor);
    }

    public List<String> nombresExistentes() {
        return proveedores.findAll().stream().map(pl_Proveedor::getNombre).toList();
    }
}
