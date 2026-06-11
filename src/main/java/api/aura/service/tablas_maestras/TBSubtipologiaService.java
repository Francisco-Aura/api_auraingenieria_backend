package api.aura.service.tablas_maestras;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBSubtipologia;
import api.aura.repository.tablas_maestras.TBSubtipologiaRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBSubtipologiaService {

    @Autowired
    private TBSubtipologiaRepository subtipologiaRepository;

    @CacheEvict(value = "tablas-maestras", key = "'subtipologias'")
    public TBSubtipologia crearSubtipologia(TBSubtipologia subtipologia) {
        return subtipologiaRepository.save(subtipologia);
    }

    @Cacheable(value = "tablas-maestras", key = "'subtipologias'")
    public List<TBSubtipologia> obtenerTodasLasSubtipologias() {
        return subtipologiaRepository.findAll();
    }

    public Optional<TBSubtipologia> obtenerSubtipologiaPorId(Integer id) {
        return subtipologiaRepository.findById(id);
    }

    @CacheEvict(value = "tablas-maestras", key = "'subtipologias'")
    public TBSubtipologia actualizarSubtipologia(Integer id, TBSubtipologia subtipologiaActualizada) {
        return subtipologiaRepository.findById(id)
                .map(subtipologia -> {
                    subtipologia.setNombre(subtipologiaActualizada.getNombre());
                    subtipologia.setDescripcion(subtipologiaActualizada.getDescripcion());
                    return subtipologiaRepository.save(subtipologia);
                })
                .orElseThrow(() -> new RuntimeException("Subtipología no encontrada con ID: " + id));
    }

    @CacheEvict(value = "tablas-maestras", key = "'subtipologias'")
    public void eliminarSubtipologia(Integer id) {
        subtipologiaRepository.deleteById(id);
    }
}
