package api.aura.service.tablas_maestras;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBMetodoPago;
import api.aura.repository.tablas_maestras.TBMetodoPagoRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBMetodoPagoService {

    @Autowired
    private TBMetodoPagoRepository metodoPagoRepository;

    public TBMetodoPago crearMetodoPago(TBMetodoPago metodoPago) {
        return metodoPagoRepository.save(metodoPago);
    }

    public List<TBMetodoPago> obtenerTodosLosMetodosPago() {
        return metodoPagoRepository.findAll();
    }

    public Optional<TBMetodoPago> obtenerMetodoPagoPorId(Integer id) {
        return metodoPagoRepository.findById(id);
    }

    public TBMetodoPago actualizarMetodoPago(Integer id, TBMetodoPago metodoPagoActualizado) {
        return metodoPagoRepository.findById(id)
                .map(metodoPago -> {
                    metodoPago.setNombre(metodoPagoActualizado.getNombre());
                    return metodoPagoRepository.save(metodoPago);
                })
                .orElseThrow(() -> new RuntimeException("Método de pago no encontrado con ID: " + id));
    }

    public void eliminarMetodoPago(Integer id) {
        metodoPagoRepository.deleteById(id);
    }
}
