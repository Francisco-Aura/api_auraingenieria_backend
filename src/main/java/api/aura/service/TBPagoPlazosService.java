package api.aura.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import api.aura.entity.TBPagoPlazos;
import api.aura.repository.TBPagoPlazosRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBPagoPlazosService {

    @Autowired
    private TBPagoPlazosRepository pagoPlazosRepository;

    public TBPagoPlazos crearPagoPlazo(TBPagoPlazos pagoPlazo) {
        return pagoPlazosRepository.save(pagoPlazo);
    }

    public List<TBPagoPlazos> obtenerTodosLosPagosPlazos() {
        return pagoPlazosRepository.findAllByOrderByNumeroCuotasAsc();
    }

    public Optional<TBPagoPlazos> obtenerPagoPlazoPorId(Integer id) {
        return pagoPlazosRepository.findById(id);
    }

    public TBPagoPlazos actualizarPagoPlazo(Integer id, TBPagoPlazos pagoPlazoActualizado) {
        return pagoPlazosRepository.findById(id)
                .map(pagoPlazo -> {
                    pagoPlazo.setNombre(pagoPlazoActualizado.getNombre());
                    pagoPlazo.setDescripcion(pagoPlazoActualizado.getDescripcion());
                    pagoPlazo.setNumeroCuotas(pagoPlazoActualizado.getNumeroCuotas());
                    return pagoPlazosRepository.save(pagoPlazo);
                })
                .orElseThrow(() -> new RuntimeException("Pago plazo no encontrado con ID: " + id));
    }

    public void eliminarPagoPlazo(Integer id) {
        pagoPlazosRepository.deleteById(id);
    }
}
