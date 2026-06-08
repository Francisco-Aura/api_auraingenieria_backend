package api.aura.service.tablas_maestras;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBCargoContacto;
import api.aura.repository.tablas_maestras.TBCargoContactoRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBCargoContactoService {

    @Autowired
    private TBCargoContactoRepository cargoContactoRepository;

    @CacheEvict(value = "tablas-maestras", key = "'cargos-contacto'")
    public TBCargoContacto crear(TBCargoContacto cargo) {
        return cargoContactoRepository.save(cargo);
    }

    @Cacheable(value = "tablas-maestras", key = "'cargos-contacto'")
    public List<TBCargoContacto> obtenerTodos() {
        return cargoContactoRepository.findAll();
    }

    public Optional<TBCargoContacto> obtenerPorId(Integer id) {
        return cargoContactoRepository.findById(id);
    }

    @CacheEvict(value = "tablas-maestras", key = "'cargos-contacto'")
    public TBCargoContacto actualizar(Integer id, TBCargoContacto cargoActualizado) {
        return cargoContactoRepository.findById(id)
                .map(cargo -> {
                    cargo.setNombre(cargoActualizado.getNombre());
                    return cargoContactoRepository.save(cargo);
                })
                .orElseThrow(() -> new IllegalArgumentException("Cargo de contacto no encontrado con ID: " + id));
    }

    @CacheEvict(value = "tablas-maestras", key = "'cargos-contacto'")
    public void eliminar(Integer id) {
        cargoContactoRepository.deleteById(id);
    }
}
