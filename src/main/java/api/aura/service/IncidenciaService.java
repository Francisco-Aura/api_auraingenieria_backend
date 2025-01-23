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

    // Crear una nueva incidencia
    public Incidencia crearIncidencia(Incidencia incidencia) {
        return incidenciaRepository.save(incidencia);
    }

    // Obtener todas las incidencias
    public List<Incidencia> obtenerTodasLasIncidencias() {
        return incidenciaRepository.findAll();
    }

    // Obtener una incidencia por ID
    public Optional<Incidencia> obtenerIncidenciaPorId(Integer id) {
        return incidenciaRepository.findById(id);
    }

    // Actualizar una incidencia existente
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

    // Eliminar una incidencia
    public void eliminarIncidencia(Integer id) {
        incidenciaRepository.deleteById(id);
    }
}
