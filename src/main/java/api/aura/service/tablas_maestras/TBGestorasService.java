package api.aura.service.tablas_maestras;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBGestoras;
import api.aura.repository.tablas_maestras.TBGestorasRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBGestorasService {

    @Autowired
    private TBGestorasRepository gestorasRepository;

    @CacheEvict(value = "tablas-maestras", key = "'gestoras'")
    public TBGestoras crearGestora(TBGestoras gestora) {
        return gestorasRepository.save(gestora);
    }

    @Cacheable(value = "tablas-maestras", key = "'gestoras'")
    public List<TBGestoras> obtenerTodasLasGestoras() {
        return gestorasRepository.findAll();
    }

    public Optional<TBGestoras> obtenerGestoraPorId(Integer id) {
        return gestorasRepository.findById(id);
    }

    @CacheEvict(value = "tablas-maestras", key = "'gestoras'")
    public TBGestoras actualizarGestora(Integer id, TBGestoras gestoraActualizada) {
        return gestorasRepository.findById(id)
                .map(gestora -> {
                    gestora.setNombre(gestoraActualizada.getNombre());
                    gestora.setDescripcion(gestoraActualizada.getDescripcion());
                    return gestorasRepository.save(gestora);
                })
                .orElseThrow(() -> new RuntimeException("Gestora no encontrada con ID: " + id));
    }

    @CacheEvict(value = "tablas-maestras", key = "'gestoras'")
    public void eliminarGestora(Integer id) {
        gestorasRepository.deleteById(id);
    }
}
