package api.aura.service.tablas_maestras;


import org.springframework.beans.factory.annotation.Autowired;
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

    public TBSectores crearSector(TBSectores sector) {
        return sectoresRepository.save(sector);
    }

    @Cacheable("tablas-maestras")
    public List<TBSectores> obtenerTodosLosSectores() {
        return sectoresRepository.findAll();
    }

    public Optional<TBSectores> obtenerSectorPorId(Integer id) {
        return sectoresRepository.findById(id);
    }

    public TBSectores actualizarSector(Integer id, TBSectores sectorActualizado) {
        return sectoresRepository.findById(id)
                .map(sector -> {
                    sector.setNombre(sectorActualizado.getNombre());
                    sector.setDescripcion(sectorActualizado.getDescripcion());
                    return sectoresRepository.save(sector);
                })
                .orElseThrow(() -> new RuntimeException("Sector no encontrado con ID: " + id));
    }

    public void eliminarSector(Integer id) {
        sectoresRepository.deleteById(id);
    }
}
