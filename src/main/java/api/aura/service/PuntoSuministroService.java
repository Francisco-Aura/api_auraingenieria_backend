package api.aura.service;

import api.aura.dto.PuntosSuministrosCarteraDTO;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.DistribuidoraRepository;
import api.aura.repository.TipoContadorRepository;
import api.aura.repository.TipoComunicacionRepository;
import api.aura.repository.PropiedadRepository;
import api.aura.repository.AppMonitorizacionRepository;
import api.aura.repository.IntegracionSistemasFVRepository;
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
    @Autowired
    private AppMonitorizacionRepository appMonitorizacionRepository;
    @Autowired
    private IntegracionSistemasFVRepository integracionSistemasFVRepository;

    public PuntoSuministro guardarPuntoSuministro(PuntoSuministro puntoSuministro) {
        try {
            System.out.println("=== DEBUGGING PUNTO SUMINISTRO SAVE ===");
            System.out.println("CUPSluz received: " + puntoSuministro.getCUPSluz());
            System.out.println("CUPSgas received: " + puntoSuministro.getCUPSgas());
            
            // DEBUG: Campos de instalaciones
            System.out.println("=== INSTALACIONES DEBUG ===");
            System.out.println("Batería Condensadores: " + puntoSuministro.getBateriaCondensadores());
            System.out.println("Potencia Batería: " + puntoSuministro.getPotenciaBateria());
            System.out.println("Modelo Batería: " + puntoSuministro.getModeloBateria());
            System.out.println("Instalación FV: " + puntoSuministro.getInstalacionFV());
            System.out.println("KW Inversor: " + puntoSuministro.getKwInversor());
            System.out.println("KW Paneles: " + puntoSuministro.getKwPanelesSolares());
            System.out.println("Batería Almacenamiento: " + puntoSuministro.getBateriaAlmacenamiento());
            System.out.println("Potencia Batería Almacenamiento: " + puntoSuministro.getPotenciaBateriaAlmacenamiento());
            System.out.println("Backup: " + puntoSuministro.getBackup());
            System.out.println("Peaksaving: " + puntoSuministro.getPeaksaving());
            System.out.println("AppMonitorizacion ID: " + (puntoSuministro.getAppMonitorizacion() != null ? puntoSuministro.getAppMonitorizacion().getId() : "null"));
            System.out.println("IntegracionSistemasFV ID: " + (puntoSuministro.getIntegracionSistemasFV() != null ? puntoSuministro.getIntegracionSistemasFV().getId() : "null"));
            System.out.println("================================");

            // Validar campos obligatorios
            if (puntoSuministro.getDireccion() == null || puntoSuministro.getDireccion().trim().isEmpty() ||
                    puntoSuministro.getCodigoPostal() == null || puntoSuministro.getCodigoPostal().trim().isEmpty() ||
                    puntoSuministro.getPoblacion() == null || puntoSuministro.getPoblacion().trim().isEmpty() ||
                    puntoSuministro.getProvincia() == null || puntoSuministro.getProvincia().trim().isEmpty() ||
                    puntoSuministro.getCliente() == null || puntoSuministro.getCliente().getId() == null) {
                throw new IllegalArgumentException(
                        "Los campos dirección, código postal, población, provincia y cliente son obligatorios");
            }

            // Manejar las relaciones con las tablas maestras
            if (puntoSuministro.getDistribuidora() != null && puntoSuministro.getDistribuidora().getId() != null) {
                distribuidoraRepository.findById(puntoSuministro.getDistribuidora().getId())
                        .ifPresentOrElse(
                                puntoSuministro::setDistribuidora,
                                () -> {
                                    throw new IllegalArgumentException("Distribuidora no encontrada");
                                });
            } else {
                puntoSuministro.setDistribuidora(null);
            }

            if (puntoSuministro.getTipoContador() != null && puntoSuministro.getTipoContador().getId() != null) {
                tipoContadorRepository.findById(puntoSuministro.getTipoContador().getId())
                        .ifPresentOrElse(
                                puntoSuministro::setTipoContador,
                                () -> {
                                    throw new IllegalArgumentException("Tipo de contador no encontrado");
                                });
            } else {
                puntoSuministro.setTipoContador(null);
            }

            if (puntoSuministro.getTipoComunicacion() != null
                    && puntoSuministro.getTipoComunicacion().getId() != null) {
                tipoComunicacionRepository.findById(puntoSuministro.getTipoComunicacion().getId())
                        .ifPresentOrElse(
                                puntoSuministro::setTipoComunicacion,
                                () -> {
                                    throw new IllegalArgumentException("Tipo de comunicación no encontrado");
                                });
            } else {
                puntoSuministro.setTipoComunicacion(null);
            }

            if (puntoSuministro.getPropiedad() != null && puntoSuministro.getPropiedad().getId() != null) {
                propiedadRepository.findById(puntoSuministro.getPropiedad().getId())
                        .ifPresentOrElse(
                                puntoSuministro::setPropiedad,
                                () -> {
                                    throw new IllegalArgumentException("Propiedad no encontrada");
                                });
            } else {
                puntoSuministro.setPropiedad(null);
            }

            // Validar CUPS
            if ((puntoSuministro.getCUPSluz() == null || puntoSuministro.getCUPSluz().trim().isEmpty()) &&
                    (puntoSuministro.getCUPSgas() == null || puntoSuministro.getCUPSgas().trim().isEmpty())) {
                throw new IllegalArgumentException("Debe proporcionar al menos un CUPS (luz o gas)");
            }

            // Validar que la fecha de activación de vertido sea obligatoria si autoconsumo
            // está presente
            if (puntoSuministro.getAutoconsumo() != null && !puntoSuministro.getAutoconsumo().trim().isEmpty()
                    && puntoSuministro.getFechaActivacionVertido() == null) {
                throw new IllegalArgumentException(
                        "La Fecha de Activación de Vertido es obligatoria si el Autoconsumo está seleccionado.");
            }

            // Manejar relaciones de instalaciones - AppMonitorizacion
            if (puntoSuministro.getAppMonitorizacion() != null && puntoSuministro.getAppMonitorizacion().getId() != null) {
                appMonitorizacionRepository.findById(puntoSuministro.getAppMonitorizacion().getId())
                        .ifPresentOrElse(
                                puntoSuministro::setAppMonitorizacion,
                                () -> {
                                    throw new IllegalArgumentException("APP Monitorización no encontrada");
                                });
            } else {
                puntoSuministro.setAppMonitorizacion(null);
            }

            // Manejar relaciones de instalaciones - IntegracionSistemasFV
            if (puntoSuministro.getIntegracionSistemasFV() != null && puntoSuministro.getIntegracionSistemasFV().getId() != null) {
                integracionSistemasFVRepository.findById(puntoSuministro.getIntegracionSistemasFV().getId())
                        .ifPresentOrElse(
                                puntoSuministro::setIntegracionSistemasFV,
                                () -> {
                                    throw new IllegalArgumentException("Integración Sistemas FV no encontrada");
                                });
            } else {
                puntoSuministro.setIntegracionSistemasFV(null);
            }

            System.out.println("=== ANTES DE GUARDAR ===");
            System.out.println("Punto Suministro ID: " + puntoSuministro.getId());
            System.out.println("Datos finales de instalaciones antes del save:");
            System.out.println("  Batería Condensadores: " + puntoSuministro.getBateriaCondensadores());
            System.out.println("  Instalación FV: " + puntoSuministro.getInstalacionFV());
            System.out.println("  Batería Almacenamiento: " + puntoSuministro.getBateriaAlmacenamiento());
            
            PuntoSuministro saved = puntoSuministroRepository.save(puntoSuministro);
            
            System.out.println("=== DESPUÉS DE GUARDAR ===");
            System.out.println("Punto Suministro guardado con ID: " + saved.getId());
            System.out.println("Datos guardados de instalaciones:");
            System.out.println("  Batería Condensadores: " + saved.getBateriaCondensadores());
            System.out.println("  Instalación FV: " + saved.getInstalacionFV());
            System.out.println("  Batería Almacenamiento: " + saved.getBateriaAlmacenamiento());
            System.out.println("===============================");
            
            return saved;
        } catch (Exception e) {
            System.out.println("ERROR al guardar punto suministro: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al guardar el punto de suministro: " + e.getMessage(), e);
        }
    }

    public List<PuntoSuministro> obtenerTodos() {
        return puntoSuministroRepository.findAll();
    }

    public List<PuntosSuministrosCarteraDTO> obtenerCarteraPuntoSuministro() {
        List<PuntosSuministrosCarteraDTO> resultado = puntoSuministroRepository.findAllPuntosSuministro();
        
        // Debug específico para el punto problemático
        resultado.stream()
            .filter(p -> p.getId() == 44413)
            .forEach(p -> {
                System.out.println("=== DEBUG PUNTO 44413 ===");
                System.out.println("ID: " + p.getId());
                System.out.println("Cliente: " + p.getCliente());
                System.out.println("TarifaActual: " + p.getTarifaActual());
                System.out.println("=========================");
            });
        
        return resultado;
    }

    public List<PuntosSuministrosCarteraDTO> obtenerCarteraPuntoSuministroPorCliente(Integer clienteId) {
        return puntoSuministroRepository.findAllPuntosSuministroPorCliente(clienteId);
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
