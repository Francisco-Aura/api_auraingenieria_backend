package api.aura.service.tablas_maestras;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import api.aura.entity.tablas_maestras.TBSectores;
import api.aura.repository.tablas_maestras.TBSectoresRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBSectoresService {

    @Autowired
    private TBSectoresRepository sectoresRepository;

    @CacheEvict(value = "tablas-maestras", key = "'sectores'")
    public TBSectores crearSector(TBSectores sector) {
        return sectoresRepository.save(sector);
    }

    @Cacheable(value = "tablas-maestras", key = "'sectores'")
    public List<TBSectores> obtenerTodosLosSectores() {
        return sectoresRepository.findAll();
    }

    public Optional<TBSectores> obtenerSectorPorId(Integer id) {
        return sectoresRepository.findById(id);
    }

    @CacheEvict(value = "tablas-maestras", key = "'sectores'")
    public TBSectores actualizarSector(Integer id, TBSectores sectorActualizado) {
        return sectoresRepository.findById(id)
                .map(sector -> {
                    sector.setNombre(sectorActualizado.getNombre());
                    sector.setDescripcion(sectorActualizado.getDescripcion());
                    return sectoresRepository.save(sector);
                })
                .orElseThrow(() -> new IllegalArgumentException("Sector no encontrado con ID: " + id));
    }

    @CacheEvict(value = "tablas-maestras", key = "'sectores'")
    public void eliminarSector(Integer id) {
        sectoresRepository.deleteById(id);
    }
}
