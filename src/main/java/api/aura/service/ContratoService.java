package api.aura.service;

import api.aura.entity.Contrato;
import api.aura.repository.ContratoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ContratoService {

    @Autowired
    private ContratoRepository contratoRepository;

    public Contrato guardarContrato(Contrato contrato) {
        return contratoRepository.save(contrato);
    }

    public List<Contrato> obtenerTodos() {
        return contratoRepository.findAll();
    }

    public Optional<Contrato> obtenerPorId(Integer id) {
        return contratoRepository.findById(id);
    }

    public List<Contrato> obtenerPorClienteId(Integer clienteId) {
        return contratoRepository.findByClienteId(clienteId);
    }

    public List<Contrato> obtenerPorPuntoSuministroId(Integer puntoSuministroId) {
        return contratoRepository.findByPuntoSuministroId(puntoSuministroId);
    }

    public void eliminarPorId(Integer id) {
        contratoRepository.deleteById(id);
    }
}
