package api.aura.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import api.aura.entity.Incidencia;
import api.aura.repository.IncidenciaRepository;

import java.util.List;
import java.util.Optional;

@Service
public class IncidenciaService {

    @Autowired
    private IncidenciaRepository incidenciaRepository;

    public Incidencia crearIncidencia(Incidencia incidencia) {
        return incidenciaRepository.save(incidencia);
    }

    public List<Incidencia> obtenerTodasLasIncidencias() {
        return incidenciaRepository.findAll();
    }

    public Optional<Incidencia> obtenerIncidenciaPorId(Integer id) {
        return incidenciaRepository.findById(id);
    }

    public Incidencia actualizarIncidencia(Integer id, Incidencia incidenciaActualizada) {
        return incidenciaRepository.findById(id)
                .map(incidencia -> {
                    incidencia.setCliente(incidenciaActualizada.getCliente());
                    incidencia.setPuntoSuministro(incidenciaActualizada.getPuntoSuministro());
                    incidencia.setTipo(incidenciaActualizada.getTipo());
                    incidencia.setFecha(incidenciaActualizada.getFecha());
                    incidencia.setFechaResolucion(incidenciaActualizada.getFechaResolucion());
                    incidencia.setDescripcion(incidenciaActualizada.getDescripcion());
                    incidencia.setReferenciada(incidenciaActualizada.getReferenciada());
                    return incidenciaRepository.save(incidencia);
                })
                .orElseThrow(() -> new RuntimeException("Incidencia no encontrada con ID: " + id));
    }

    public void eliminarIncidencia(Integer id) {
        incidenciaRepository.deleteById(id);
    }
}
