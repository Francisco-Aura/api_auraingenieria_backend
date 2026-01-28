package api.aura.service.tablas_maestras;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBGestoras;
import api.aura.repository.tablas_maestras.TBGestorasRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBGestorasService {

    @Autowired
    private TBGestorasRepository gestorasRepository;

    public TBGestoras crearGestora(TBGestoras gestora) {
        return gestorasRepository.save(gestora);
    }

    public List<TBGestoras> obtenerTodasLasGestoras() {
        return gestorasRepository.findAll();
    }

    public Optional<TBGestoras> obtenerGestoraPorId(Integer id) {
        return gestorasRepository.findById(id);
    }

    public TBGestoras actualizarGestora(Integer id, TBGestoras gestoraActualizada) {
        return gestorasRepository.findById(id)
                .map(gestora -> {
                    gestora.setNombre(gestoraActualizada.getNombre());
                    gestora.setDescripcion(gestoraActualizada.getDescripcion());
                    return gestorasRepository.save(gestora);
                })
                .orElseThrow(() -> new RuntimeException("Gestora no encontrada con ID: " + id));
    }

    public void eliminarGestora(Integer id) {
        gestorasRepository.deleteById(id);
    }
}
