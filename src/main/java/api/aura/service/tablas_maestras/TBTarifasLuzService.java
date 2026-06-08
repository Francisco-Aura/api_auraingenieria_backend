package api.aura.service.tablas_maestras;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBTarifasLuz;
import api.aura.repository.tablas_maestras.TBTarifasLuzRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBTarifasLuzService {

    @Autowired
    private TBTarifasLuzRepository tarifasLuzRepository;

    @CacheEvict(value = "tablas-maestras", key = "'tarifas-luz'")
    public TBTarifasLuz crearTarifaLuz(TBTarifasLuz tarifaLuz) {
        return tarifasLuzRepository.save(tarifaLuz);
    }

    @Cacheable(value = "tablas-maestras", key = "'tarifas-luz'")
    public List<TBTarifasLuz> obtenerTodasLasTarifasLuz() {
        return tarifasLuzRepository.findAll();
    }

    public Optional<TBTarifasLuz> obtenerTarifaLuzPorId(Integer id) {
        return tarifasLuzRepository.findById(id);
    }

    @CacheEvict(value = "tablas-maestras", key = "'tarifas-luz'")
    public TBTarifasLuz actualizarTarifaLuz(Integer id, TBTarifasLuz tarifaLuzActualizada) {
        return tarifasLuzRepository.findById(id)
                .map(tarifaLuz -> {
                    tarifaLuz.setNombre(tarifaLuzActualizada.getNombre());
                    tarifaLuz.setDescripcion(tarifaLuzActualizada.getDescripcion());
                    return tarifasLuzRepository.save(tarifaLuz);
                })
                .orElseThrow(() -> new RuntimeException("Tarifa de Luz no encontrada con ID: " + id));
    }

    @CacheEvict(value = "tablas-maestras", key = "'tarifas-luz'")
    public void eliminarTarifaLuz(Integer id) {
        tarifasLuzRepository.deleteById(id);
    }
}
