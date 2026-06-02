package api.aura.service.tablas_maestras;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBNivelAcceso;
import api.aura.repository.tablas_maestras.TBNivelAccesoRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBNivelAccesoService {

    @Autowired
    private TBNivelAccesoRepository nivelAccesoRepository;

    public TBNivelAcceso crearNivelAcceso(TBNivelAcceso nivelAcceso) {
        return nivelAccesoRepository.save(nivelAcceso);
    }

    @Cacheable("tablas-maestras")
    public List<TBNivelAcceso> obtenerTodosLosNivelesAcceso() {
        return nivelAccesoRepository.findAll();
    }

    public Optional<TBNivelAcceso> obtenerNivelAccesoPorId(Integer id) {
        return nivelAccesoRepository.findById(id);
    }

    public TBNivelAcceso actualizarNivelAcceso(Integer id, TBNivelAcceso nivelAccesoActualizado) {
        return nivelAccesoRepository.findById(id)
                .map(nivelAcceso -> {
                    nivelAcceso.setNivel(nivelAccesoActualizado.getNivel());
                    nivelAcceso.setDescripcion(nivelAccesoActualizado.getDescripcion());
                    return nivelAccesoRepository.save(nivelAcceso);
                })
                .orElseThrow(() -> new RuntimeException("Nivel de acceso no encontrado con ID: " + id));
    }

    public void eliminarNivelAcceso(Integer id) {
        nivelAccesoRepository.deleteById(id);
    }
}
