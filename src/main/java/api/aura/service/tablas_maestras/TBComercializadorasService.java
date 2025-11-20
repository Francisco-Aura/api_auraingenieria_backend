package api.aura.service.tablas_maestras;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBComercializadorasService {

    @Autowired
    private TBComercializadorasRepository comercializadorasRepository;

    // Crear un nuevo registro
    public TBComercializadoras crearComercializadora(TBComercializadoras comercializadora) {
        return comercializadorasRepository.save(comercializadora);
    }

    // Listar todos los registros
    public List<TBComercializadoras> obtenerTodasLasComercializadoras() {
        return comercializadorasRepository.findAll();
    }

    // Buscar por ID
    public Optional<TBComercializadoras> obtenerComercializadoraPorId(Integer id) {
        return comercializadorasRepository.findById(id);
    }

    // Actualizar un registro existente
    public TBComercializadoras actualizarComercializadora(Integer id, TBComercializadoras comercializadoraActualizada) {
        return comercializadorasRepository.findById(id)
                .map(comercializadora -> {
                    comercializadora.setNombre(comercializadoraActualizada.getNombre());
                    comercializadora.setDescripcion(comercializadoraActualizada.getDescripcion());
                    return comercializadorasRepository.save(comercializadora);
                })
                .orElseThrow(() -> new RuntimeException("Comercializadora no encontrada con ID: " + id));
    }

    // Eliminar un registro
    public void eliminarComercializadora(Integer id) {
        comercializadorasRepository.deleteById(id);
    }
}
