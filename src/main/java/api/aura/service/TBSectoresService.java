package api.aura.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import api.aura.entity.TBSectores;
import api.aura.repository.TBSectoresRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TBSectoresService {

    @Autowired
    private TBSectoresRepository sectoresRepository;

    public TBSectores crearSector(TBSectores sector) {
        return sectoresRepository.save(sector);
    }

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
