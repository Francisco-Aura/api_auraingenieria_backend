package api.aura.service;

import api.aura.dto.PuntosSuministrosCarteraDTO;
import api.aura.entity.PuntoSuministro;
import api.aura.entity.Cliente;
import api.aura.entity.Distribuidora;
import api.aura.entity.TipoContador;
import api.aura.entity.TipoComunicacion;
import api.aura.entity.Propiedad;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.DistribuidoraRepository;
import api.aura.repository.TipoContadorRepository;
import api.aura.repository.TipoComunicacionRepository;
import api.aura.repository.PropiedadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PuntoSuministroService {

    @Autowired
    private PuntoSuministroRepository puntoSuministroRepository;

    @Autowired
    private DistribuidoraRepository distribuidoraRepository;
    @Autowired
    private TipoContadorRepository tipoContadorRepository;
    @Autowired
    private TipoComunicacionRepository tipoComunicacionRepository;
    @Autowired
    private PropiedadRepository propiedadRepository;

    public PuntoSuministro guardarPuntoSuministro(PuntoSuministro puntoSuministro) {
        try {
            System.out.println("Debugging CUPS values in backend service:");
            System.out.println("CUPSluz received: " + puntoSuministro.getCUPSluz());
            System.out.println("CUPSgas received: " + puntoSuministro.getCUPSgas());

            // Validar campos obligatorios
            if (puntoSuministro.getDireccion() == null || puntoSuministro.getDireccion().trim().isEmpty() ||
                puntoSuministro.getCodigoPostal() == null || puntoSuministro.getCodigoPostal().trim().isEmpty() ||
                puntoSuministro.getPoblacion() == null || puntoSuministro.getPoblacion().trim().isEmpty() ||
                puntoSuministro.getProvincia() == null || puntoSuministro.getProvincia().trim().isEmpty() ||
                puntoSuministro.getCliente() == null || puntoSuministro.getCliente().getId() == null) {
                throw new IllegalArgumentException("Los campos dirección, código postal, población, provincia y cliente son obligatorios");
            }

            // Manejar las relaciones con las tablas maestras
            if (puntoSuministro.getDistribuidora() != null && puntoSuministro.getDistribuidora().getId() != null) {
                distribuidoraRepository.findById(puntoSuministro.getDistribuidora().getId())
                    .ifPresentOrElse(
                        puntoSuministro::setDistribuidora,
                        () -> { throw new IllegalArgumentException("Distribuidora no encontrada"); }
                    );
            } else {
                puntoSuministro.setDistribuidora(null);
            }

            if (puntoSuministro.getTipoContador() != null && puntoSuministro.getTipoContador().getId() != null) {
                tipoContadorRepository.findById(puntoSuministro.getTipoContador().getId())
                    .ifPresentOrElse(
                        puntoSuministro::setTipoContador,
                        () -> { throw new IllegalArgumentException("Tipo de contador no encontrado"); }
                    );
            } else {
                puntoSuministro.setTipoContador(null);
            }

            if (puntoSuministro.getTipoComunicacion() != null && puntoSuministro.getTipoComunicacion().getId() != null) {
                tipoComunicacionRepository.findById(puntoSuministro.getTipoComunicacion().getId())
                    .ifPresentOrElse(
                        puntoSuministro::setTipoComunicacion,
                        () -> { throw new IllegalArgumentException("Tipo de comunicación no encontrado"); }
                    );
            } else {
                puntoSuministro.setTipoComunicacion(null);
            }

            if (puntoSuministro.getPropiedad() != null && puntoSuministro.getPropiedad().getId() != null) {
                propiedadRepository.findById(puntoSuministro.getPropiedad().getId())
                    .ifPresentOrElse(
                        puntoSuministro::setPropiedad,
                        () -> { throw new IllegalArgumentException("Propiedad no encontrada"); }
                    );
            } else {
                puntoSuministro.setPropiedad(null);
            }

            // Validar CUPS
            if ((puntoSuministro.getCUPSluz() == null || puntoSuministro.getCUPSluz().trim().isEmpty()) &&
                (puntoSuministro.getCUPSgas() == null || puntoSuministro.getCUPSgas().trim().isEmpty())) {
                throw new IllegalArgumentException("Debe proporcionar al menos un CUPS (luz o gas)");
            }

            // Validar que la fecha de activación de vertido sea obligatoria si autoconsumo está presente
            if (puntoSuministro.getAutoconsumo() != null && !puntoSuministro.getAutoconsumo().trim().isEmpty() && puntoSuministro.getFechaActivacionVertido() == null) {
                throw new IllegalArgumentException("La Fecha de Activación de Vertido es obligatoria si el Autoconsumo está seleccionado.");
            }

            return puntoSuministroRepository.save(puntoSuministro);
        } catch (Exception e) {
            throw new RuntimeException("Error al guardar el punto de suministro: " + e.getMessage(), e);
        }
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
