package api.aura.service.tablas_maestras;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBProductos;
import api.aura.repository.tablas_maestras.TBProductosRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBProductosService {

    @Autowired
    private TBProductosRepository productosRepository;

    public TBProductos crearProducto(TBProductos producto) {
        return productosRepository.save(producto);
    }

    public List<TBProductos> obtenerTodosLosProductos() {
        return productosRepository.findAll();
    }

    public Optional<TBProductos> obtenerProductoPorId(Integer id) {
        return productosRepository.findById(id);
    }

    public TBProductos actualizarProducto(Integer id, TBProductos productoActualizado) {
        return productosRepository.findById(id)
                .map(producto -> {
                    producto.setNombre(productoActualizado.getNombre());
                    producto.setDescripcion(productoActualizado.getDescripcion());
                    return productosRepository.save(producto);
                })
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + id));
    }

    public void eliminarProducto(Integer id) {
        productosRepository.deleteById(id);
    }
}
