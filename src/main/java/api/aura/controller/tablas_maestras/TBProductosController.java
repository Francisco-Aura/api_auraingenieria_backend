package api.aura.controller.tablas_maestras;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import api.aura.entity.tablas_maestras.TBProductos;
import api.aura.service.tablas_maestras.TBProductosService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/productos")
public class TBProductosController {

    @Autowired
    private TBProductosService productosService;

    @PostMapping
    public ResponseEntity<TBProductos> crearProducto(@RequestBody TBProductos producto) {
        return ResponseEntity.ok(productosService.crearProducto(producto));
    }

    @GetMapping
    public List<TBProductos> obtenerTodosLosProductos() {
        return productosService.obtenerTodosLosProductos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBProductos>> obtenerProductoPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(productosService.obtenerProductoPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBProductos> actualizarProducto(@PathVariable Integer id, @RequestBody TBProductos productoActualizado) {
        return ResponseEntity.ok(productosService.actualizarProducto(id, productoActualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarProducto(@PathVariable Integer id) {
        productosService.eliminarProducto(id);
        return ResponseEntity.noContent().build();
    }
}
