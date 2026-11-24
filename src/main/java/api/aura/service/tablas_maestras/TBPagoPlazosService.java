package api.aura.service.tablas_maestras;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBPagoPlazosService {

    @Autowired
    private TBPagoPlazosRepository pagoPlazosRepository;

    @CacheEvict(value = "tablas-maestras", key = "'pago-plazos'")
    public TBPagoPlazos crearPagoPlazo(TBPagoPlazos pagoPlazo) {
        return pagoPlazosRepository.save(pagoPlazo);
    }

    @Cacheable(value = "tablas-maestras", key = "'pago-plazos'")
    public List<TBPagoPlazos> obtenerTodosLosPagosPlazos() {
        return pagoPlazosRepository.findAllByOrderByNumeroCuotasAsc();
    }

    public Optional<TBPagoPlazos> obtenerPagoPlazoPorId(Integer id) {
        return pagoPlazosRepository.findById(id);
    }

    @CacheEvict(value = "tablas-maestras", key = "'pago-plazos'")
    public TBPagoPlazos actualizarPagoPlazo(Integer id, TBPagoPlazos pagoPlazoActualizado) {
        return pagoPlazosRepository.findById(id)
                .map(pagoPlazo -> {
                    pagoPlazo.setNombre(pagoPlazoActualizado.getNombre());
                    pagoPlazo.setDescripcion(pagoPlazoActualizado.getDescripcion());
                    pagoPlazo.setNumeroCuotas(pagoPlazoActualizado.getNumeroCuotas());
                    return pagoPlazosRepository.save(pagoPlazo);
                })
                .orElseThrow(() -> new IllegalArgumentException("Pago plazo no encontrado con ID: " + id));
    }

    @CacheEvict(value = "tablas-maestras", key = "'pago-plazos'")
    public void eliminarPagoPlazo(Integer id) {
        pagoPlazosRepository.deleteById(id);
    }
}
