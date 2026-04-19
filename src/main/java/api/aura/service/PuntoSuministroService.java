package api.aura.service;

import api.aura.dto.PuntosSuministrosCarteraDTO;
import api.aura.entity.PuntoSuministro;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.tablas_maestras.TBAppMonitorizacionRepository;
import api.aura.repository.tablas_maestras.TBDistribuidoraRepository;
import api.aura.repository.tablas_maestras.TBIntegracionSistemasFVRepository;
import api.aura.repository.tablas_maestras.TBPropiedadRepository;
import api.aura.repository.tablas_maestras.TBTipoComunicacionRepository;
import api.aura.repository.tablas_maestras.TBTipoContadorRepository;
import api.aura.repository.ContratoRepository;
import api.aura.entity.Contrato;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PuntoSuministroService {

    @Autowired
    private PuntoSuministroRepository puntoSuministroRepository;

    @Autowired
    private TBDistribuidoraRepository distribuidoraRepository;
    @Autowired
    private TBTipoContadorRepository tipoContadorRepository;
    @Autowired
    private TBTipoComunicacionRepository tipoComunicacionRepository;
    @Autowired
    private TBPropiedadRepository propiedadRepository;
    @Autowired
    private TBAppMonitorizacionRepository appMonitorizacionRepository;
    @Autowired
    private TBIntegracionSistemasFVRepository integracionSistemasFVRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Transactional
    public PuntoSuministro guardarPuntoSuministro(PuntoSuministro puntoSuministro) {
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

            // Validar CUPS (ya normalizados en el controlador)
            String cupsLuz = puntoSuministro.getCUPSluz();
            String cupsGas = puntoSuministro.getCUPSgas();

            // Validar que al menos uno de los CUPS tenga un valor válido
            boolean tieneCupsLuz = cupsLuz != null && !cupsLuz.trim().isEmpty();
            boolean tieneCupsGas = cupsGas != null && !cupsGas.trim().isEmpty();

            if (!tieneCupsLuz && !tieneCupsGas) {
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
            if (puntoSuministro.getAppMonitorizacion() != null
                    && puntoSuministro.getAppMonitorizacion().getId() != null) {
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
            if (puntoSuministro.getIntegracionSistemasFV() != null
                    && puntoSuministro.getIntegracionSistemasFV().getId() != null) {
                integracionSistemasFVRepository.findById(puntoSuministro.getIntegracionSistemasFV().getId())
                        .ifPresentOrElse(
                                puntoSuministro::setIntegracionSistemasFV,
                                () -> {
                                    throw new IllegalArgumentException("Integración Sistemas FV no encontrada");
                                });
            } else {
                puntoSuministro.setIntegracionSistemasFV(null);
            }

            PuntoSuministro saved = puntoSuministroRepository.save(puntoSuministro);

            // SINCRONIZAR CONTRATOS: Actualizar todos los contratos asociados a este punto
            // de suministro
            sincronizarContratosConPuntoSuministro(saved);

            return saved;
    }

    public List<PuntoSuministro> obtenerTodos() {
        return puntoSuministroRepository.findAll();
    }

    public List<PuntosSuministrosCarteraDTO> obtenerCarteraPuntoSuministro() {
        List<PuntosSuministrosCarteraDTO> resultado = puntoSuministroRepository.findAllPuntosSuministro();

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

    public List<PuntoSuministro> obtenerDadosDeBajaPorClienteId(Integer clienteId) {
        return puntoSuministroRepository.findByClienteIdAndFechaBajaIsNotNull(clienteId);
    }

    public void reactivarPuntoSuministro(Integer id) {
        puntoSuministroRepository.findById(id).ifPresent(ps -> {
            ps.setFechaBaja(null);
            puntoSuministroRepository.save(ps);
        });
    }

    /**
     * Sincroniza todos los contratos asociados a un punto de suministro
     * con los valores de exención de impuesto eléctrico y autoconsumo del punto.
     * 
     * @param puntoSuministro El punto de suministro actualizado
     */
    private void sincronizarContratosConPuntoSuministro(PuntoSuministro puntoSuministro) {
        try {
            // Buscar todos los contratos asociados a este punto de suministro
            List<Contrato> contratos = contratoRepository.findByPuntoSuministroId(puntoSuministro.getId());

            // Actualizar cada contrato con los valores del punto de suministro
            for (Contrato contrato : contratos) {
                // Sincronizar exención de impuesto eléctrico (convertir Boolean a Integer)
                contrato.setExencionImpuestoElectricidad(
                        puntoSuministro.getExencionImpuestoElectrico() != null
                                && puntoSuministro.getExencionImpuestoElectrico() ? 1 : 0);

                // Sincronizar autoconsumo (convertir String a Integer)
                boolean tieneAutoconsumo = puntoSuministro.getAutoconsumo() != null &&
                        !puntoSuministro.getAutoconsumo().trim().isEmpty();
                contrato.setAutoconsumo(tieneAutoconsumo ? 1 : 0);
            }

            // Guardar todos los contratos actualizados
            if (!contratos.isEmpty()) {
                contratoRepository.saveAll(contratos);
            }
        } catch (Exception e) {
            // No lanzar excepción para no afectar el guardado del punto de suministro
        }
    }
}
