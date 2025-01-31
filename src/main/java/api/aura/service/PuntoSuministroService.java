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

    public PuntoSuministro guardarPuntoSuministro(PuntoSuministro puntoSuministro) {
        return puntoSuministroRepository.save(puntoSuministro);
    }

    public List<PuntoSuministro> obtenerTodos() {
        return puntoSuministroRepository.findAll();
    }

    public List<PuntosSuministrosCarteraDTO> obtenerCarteraPuntoSuministro() {
        return puntoSuministroRepository.findAllPuntosSuministro();
    }

    public Optional<PuntoSuministro> obtenerPorId(Integer id) {
        return puntoSuministroRepository.findById(id);
    }

    public List<PuntoSuministro> obtenerPorClienteId(Integer clienteId) {
        return puntoSuministroRepository.findByClienteId(clienteId);
    }

    public void eliminarPorId(Integer id) {
        puntoSuministroRepository.deleteById(id);
    }
}
