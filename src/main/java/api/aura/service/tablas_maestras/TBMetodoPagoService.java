package api.aura.service.tablas_maestras;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBMetodoPago;
import api.aura.repository.tablas_maestras.TBMetodoPagoRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBMetodoPagoService {

    @Autowired
    private TBMetodoPagoRepository metodoPagoRepository;

    @CacheEvict(value = "tablas-maestras", key = "'metodos-pago'")
    public TBMetodoPago crearMetodoPago(TBMetodoPago metodoPago) {
        return metodoPagoRepository.save(metodoPago);
    }

    @Cacheable(value = "tablas-maestras", key = "'metodos-pago'")
    public List<TBMetodoPago> obtenerTodosLosMetodosPago() {
        return metodoPagoRepository.findAll();
    }

    public Optional<TBMetodoPago> obtenerMetodoPagoPorId(Integer id) {
        return metodoPagoRepository.findById(id);
    }

    @CacheEvict(value = "tablas-maestras", key = "'metodos-pago'")
    public TBMetodoPago actualizarMetodoPago(Integer id, TBMetodoPago metodoPagoActualizado) {
        return metodoPagoRepository.findById(id)
                .map(metodoPago -> {
                    metodoPago.setNombre(metodoPagoActualizado.getNombre());
                    return metodoPagoRepository.save(metodoPago);
                })
                .orElseThrow(() -> new IllegalArgumentException("Método de pago no encontrado con ID: " + id));
    }

    @CacheEvict(value = "tablas-maestras", key = "'metodos-pago'")
    public void eliminarMetodoPago(Integer id) {
        metodoPagoRepository.deleteById(id);
    }
}
