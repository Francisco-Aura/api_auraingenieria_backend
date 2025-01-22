package api.aura.service;

import api.aura.entity.Servicio;
import api.aura.repository.ServicioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ServicioService {

    @Autowired
    private ServicioRepository servicioRepository;

    public Servicio guardarServicio(Servicio servicio) {
        return servicioRepository.save(servicio);
    }

    public List<Servicio> obtenerTodos() {
        return servicioRepository.findAll();
    }

    public Optional<Servicio> obtenerPorId(Integer id) {
        return servicioRepository.findById(id);
    }

    public List<Servicio> obtenerPorClienteId(Integer clienteId) {
        return servicioRepository.findByClienteId(clienteId);
    }

    public List<Servicio> obtenerPorPuntoSuministroId(Integer puntoSuministroId) {
        return servicioRepository.findByPuntoSuministroId(puntoSuministroId);
    }

    public void eliminarPorId(Integer id) {
        servicioRepository.deleteById(id);
    }
}
