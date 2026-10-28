package api.aura.controller.tablas_maestras;

import api.aura.controller.SesionActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import api.aura.entity.tablas_maestras.TBSectores;
import api.aura.service.tablas_maestras.TBSectoresService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/sectores")
public class TBSectoresController {

    @Autowired
    private TBSectoresService sectoresService;

    @PostMapping
    public ResponseEntity<TBSectores> crearSector(@RequestBody TBSectores sector) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(sectoresService.crearSector(sector));
    }

    @GetMapping
    public List<TBSectores> obtenerTodosLosSectores() {
        return sectoresService.obtenerTodosLosSectores();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBSectores>> obtenerSectorPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(sectoresService.obtenerSectorPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBSectores> actualizarSector(@PathVariable Integer id, @RequestBody TBSectores sectorActualizado) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(sectoresService.actualizarSector(id, sectorActualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarSector(@PathVariable Integer id) {
        SesionActual.exigirAdmin();
        sectoresService.eliminarSector(id);
        return ResponseEntity.noContent().build();
    }
}
