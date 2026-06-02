package api.aura.service.tablas_maestras;

import org.springframework.beans.factory.annotation.Autowired;
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

    public TBTarifasLuz crearTarifaLuz(TBTarifasLuz tarifaLuz) {
        return tarifasLuzRepository.save(tarifaLuz);
    }

    @Cacheable("tablas-maestras")
    public List<TBTarifasLuz> obtenerTodasLasTarifasLuz() {
        return tarifasLuzRepository.findAll();
    }

    public Optional<TBTarifasLuz> obtenerTarifaLuzPorId(Integer id) {
        return tarifasLuzRepository.findById(id);
    }

    public TBTarifasLuz actualizarTarifaLuz(Integer id, TBTarifasLuz tarifaLuzActualizada) {
        return tarifasLuzRepository.findById(id)
                .map(tarifaLuz -> {
                    tarifaLuz.setNombre(tarifaLuzActualizada.getNombre());
                    tarifaLuz.setDescripcion(tarifaLuzActualizada.getDescripcion());
                    return tarifasLuzRepository.save(tarifaLuz);
                })
                .orElseThrow(() -> new RuntimeException("Tarifa de Luz no encontrada con ID: " + id));
    }

    public void eliminarTarifaLuz(Integer id) {
        tarifasLuzRepository.deleteById(id);
    }
}
