package api.aura.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import api.aura.entity.TBTarifasGas;
import api.aura.repository.TBTarifasGasRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBTarifasGasService {

    @Autowired
    private TBTarifasGasRepository tarifasGasRepository;

    public TBTarifasGas crearTarifaGas(TBTarifasGas tarifaGas) {
        return tarifasGasRepository.save(tarifaGas);
    }

    public List<TBTarifasGas> obtenerTodasLasTarifasGas() {
        return tarifasGasRepository.findAll();
    }

    public Optional<TBTarifasGas> obtenerTarifaGasPorId(Integer id) {
        return tarifasGasRepository.findById(id);
    }

    public TBTarifasGas actualizarTarifaGas(Integer id, TBTarifasGas tarifaGasActualizada) {
        return tarifasGasRepository.findById(id)
                .map(tarifaGas -> {
                    tarifaGas.setNombre(tarifaGasActualizada.getNombre());
                    tarifaGas.setDescripcion(tarifaGasActualizada.getDescripcion());
                    return tarifasGasRepository.save(tarifaGas);
                })
                .orElseThrow(() -> new RuntimeException("Tarifa de Gas no encontrada con ID: " + id));
    }

    public void eliminarTarifaGas(Integer id) {
        tarifasGasRepository.deleteById(id);
    }
}
