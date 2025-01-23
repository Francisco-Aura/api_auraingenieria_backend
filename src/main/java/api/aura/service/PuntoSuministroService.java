package api.aura.service;

import api.aura.dto.PuntosSuministrosCarteraDTO;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.PuntoSuministroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PuntoSuministroService {

    @Autowired
    private PuntoSuministroRepository puntoSuministroRepository;

    // Crear o actualizar un punto de suministro
    public PuntoSuministro guardarPuntoSuministro(PuntoSuministro puntoSuministro) {
        return puntoSuministroRepository.save(puntoSuministro);
    }

    // Obtener todos los puntos de suministro
    public List<PuntoSuministro> obtenerTodos() {
        return puntoSuministroRepository.findAll();
    }

    // Obtener cartera de puntos de suministro
    public List<PuntosSuministrosCarteraDTO> obtenerCarteraPuntoSuministro() {
        return puntoSuministroRepository.findAllPuntosSuministro();
    }

    // Obtener un punto de suministro por ID
    public Optional<PuntoSuministro> obtenerPorId(Integer id) {
        return puntoSuministroRepository.findById(id);
    }

    // Obtener puntos de suministro por cliente
    public List<PuntoSuministro> obtenerPorClienteId(Integer clienteId) {
        return puntoSuministroRepository.findByClienteId(clienteId);
    }

    // Eliminar un punto de suministro por ID
    public void eliminarPorId(Integer id) {
        puntoSuministroRepository.deleteById(id);
    }
}
