package api.aura.service.tablas_maestras;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBTarifasGas;
import api.aura.repository.tablas_maestras.TBTarifasGasRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBTarifasGasService {

    @Autowired
    private TBTarifasGasRepository tarifasGasRepository;

    @CacheEvict(value = "tablas-maestras", key = "'tarifas-gas'")
    public TBTarifasGas crearTarifaGas(TBTarifasGas tarifaGas) {
        return tarifasGasRepository.save(tarifaGas);
    }

    @Cacheable(value = "tablas-maestras", key = "'tarifas-gas'")
    public List<TBTarifasGas> obtenerTodasLasTarifasGas() {
        return tarifasGasRepository.findAll();
    }

    public Optional<TBTarifasGas> obtenerTarifaGasPorId(Integer id) {
        return tarifasGasRepository.findById(id);
    }

    @CacheEvict(value = "tablas-maestras", key = "'tarifas-gas'")
    public TBTarifasGas actualizarTarifaGas(Integer id, TBTarifasGas tarifaGasActualizada) {
        return tarifasGasRepository.findById(id)
                .map(tarifaGas -> {
                    tarifaGas.setNombre(tarifaGasActualizada.getNombre());
                    tarifaGas.setDescripcion(tarifaGasActualizada.getDescripcion());
                    return tarifasGasRepository.save(tarifaGas);
                })
                .orElseThrow(() -> new RuntimeException("Tarifa de Gas no encontrada con ID: " + id));
    }

    @CacheEvict(value = "tablas-maestras", key = "'tarifas-gas'")
    public void eliminarTarifaGas(Integer id) {
        tarifasGasRepository.deleteById(id);
    }
}
