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
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.entity.Cliente;
import api.aura.util.BajaEfectiva;
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
    private ClienteRepository clienteRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Transactional
    public PuntoSuministro guardarPuntoSuministro(PuntoSuministro puntoSuministro) {
        // Si es una actualización, cargamos la entidad gestionada desde BD para
        // mergear sobre ella solo los campos editables. Guardar directamente el
        // objeto "detached" que llega del @RequestBody sobrescribiría con null en
        // BD cualquier campo que el frontend no incluyera en el payload.
        PuntoSuministro existente = null;
        if (puntoSuministro.getId() != null) {
            existente = puntoSuministroRepository.findById(puntoSuministro.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No se encontró el punto de suministro con ID: " + puntoSuministro.getId()));
        }

        // Validar que no se reactive un PS si el cliente está dado de baja
        if (existente != null && BajaEfectiva.estaActivo(puntoSuministro.getFechaBaja())
                && puntoSuministro.getCliente() != null && puntoSuministro.getCliente().getId() != null
                && BajaEfectiva.tieneBaja(existente.getFechaBaja())) {
            Cliente cliente = clienteRepository.findById(puntoSuministro.getCliente().getId()).orElse(null);
            if (cliente != null && BajaEfectiva.tieneBaja(cliente.getFechaBaja())) {
                throw new IllegalArgumentException(
                        "No se puede reactivar el punto de suministro porque el cliente asociado está dado de baja. Debe reactivar el cliente primero.");
            }
        }

        // Validar campos obligatorios
        if (puntoSuministro.getDireccion() == null || puntoSuministro.getDireccion().trim().isEmpty() ||
                puntoSuministro.getCodigoPostal() == null || puntoSuministro.getCodigoPostal().trim().isEmpty() ||
                puntoSuministro.getPoblacion() == null || puntoSuministro.getPoblacion().trim().isEmpty() ||
                puntoSuministro.getProvincia() == null || puntoSuministro.getProvincia().trim().isEmpty() ||
                puntoSuministro.getCliente() == null || puntoSuministro.getCliente().getId() == null) {
            throw new IllegalArgumentException(
                    "Los campos dirección, código postal, población, provincia y cliente son obligatorios");
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

        // Entidad sobre la que se van a persistir los cambios: la gestionada por
        // JPA (findById) en una actualización, o la propia entidad nueva al crear.
        PuntoSuministro target = existente != null ? existente : puntoSuministro;
        if (existente != null) {
            aplicarCamposEditables(puntoSuministro, target);
        }

        // Manejar las relaciones con las tablas maestras (sobre la entidad destino)
        if (puntoSuministro.getDistribuidora() != null && puntoSuministro.getDistribuidora().getId() != null) {
            distribuidoraRepository.findById(puntoSuministro.getDistribuidora().getId())
                    .ifPresentOrElse(
                            target::setDistribuidora,
                            () -> {
                                throw new IllegalArgumentException("Distribuidora no encontrada");
                            });
        } else {
            target.setDistribuidora(null);
        }

        if (puntoSuministro.getTipoContador() != null && puntoSuministro.getTipoContador().getId() != null) {
            tipoContadorRepository.findById(puntoSuministro.getTipoContador().getId())
                    .ifPresentOrElse(
                            target::setTipoContador,
                            () -> {
                                throw new IllegalArgumentException("Tipo de contador no encontrado");
                            });
        } else {
            target.setTipoContador(null);
        }

        if (puntoSuministro.getTipoComunicacion() != null
                && puntoSuministro.getTipoComunicacion().getId() != null) {
            tipoComunicacionRepository.findById(puntoSuministro.getTipoComunicacion().getId())
                    .ifPresentOrElse(
                            target::setTipoComunicacion,
                            () -> {
                                throw new IllegalArgumentException("Tipo de comunicación no encontrado");
                            });
        } else {
            target.setTipoComunicacion(null);
        }

        if (puntoSuministro.getPropiedad() != null && puntoSuministro.getPropiedad().getId() != null) {
            propiedadRepository.findById(puntoSuministro.getPropiedad().getId())
                    .ifPresentOrElse(
                            target::setPropiedad,
                            () -> {
                                throw new IllegalArgumentException("Propiedad no encontrada");
                            });
        } else {
            target.setPropiedad(null);
        }

        // Manejar relaciones de instalaciones - AppMonitorizacion
        if (puntoSuministro.getAppMonitorizacion() != null
                && puntoSuministro.getAppMonitorizacion().getId() != null) {
            appMonitorizacionRepository.findById(puntoSuministro.getAppMonitorizacion().getId())
                    .ifPresentOrElse(
                            target::setAppMonitorizacion,
                            () -> {
                                throw new IllegalArgumentException("APP Monitorización no encontrada");
                            });
        } else {
            target.setAppMonitorizacion(null);
        }

        // Manejar relaciones de instalaciones - IntegracionSistemasFV
        if (puntoSuministro.getIntegracionSistemasFV() != null
                && puntoSuministro.getIntegracionSistemasFV().getId() != null) {
            integracionSistemasFVRepository.findById(puntoSuministro.getIntegracionSistemasFV().getId())
                    .ifPresentOrElse(
                            target::setIntegracionSistemasFV,
                            () -> {
                                throw new IllegalArgumentException("Integración Sistemas FV no encontrada");
                            });
        } else {
            target.setIntegracionSistemasFV(null);
        }

        PuntoSuministro saved = puntoSuministroRepository.save(target);

        // SINCRONIZAR CONTRATOS: Actualizar todos los contratos asociados a este punto
        // de suministro
        sincronizarContratosConPuntoSuministro(saved);

        return saved;
    }

    /**
     * Copia sobre {@code destino} (entidad gestionada por JPA) los campos
     * editables recibidos en {@code origen} (entidad "detached" deserializada
     * del @RequestBody). Las relaciones @ManyToOne se resuelven aparte contra
     * las tablas maestras, y el {@code id} nunca se sobrescribe.
     */
    private void aplicarCamposEditables(PuntoSuministro origen, PuntoSuministro destino) {
        destino.setCliente(origen.getCliente());
        destino.setDireccion(origen.getDireccion());
        destino.setCodigoPostal(origen.getCodigoPostal());
        destino.setPoblacion(origen.getPoblacion());
        destino.setProvincia(origen.getProvincia());
        destino.setIdentificacion(origen.getIdentificacion());
        destino.setUbicacion(origen.getUbicacion());
        destino.setCUPSluz(origen.getCUPSluz());
        destino.setCUPSgas(origen.getCUPSgas());
        destino.setFechaBaja(origen.getFechaBaja());
        destino.setFechaInstalacion(origen.getFechaInstalacion());
        // Campos antiguos que el formulario ya no envía: antes cada edición los vaciaba
        if (origen.getModem() != null) destino.setModem(origen.getModem());
        if (origen.getPropiedadModem() != null) destino.setPropiedadModem(origen.getPropiedadModem());
        if (origen.getContador() != null) destino.setContador(origen.getContador());
        if (origen.getPropiedadContador() != null) destino.setPropiedadContador(origen.getPropiedadContador());
        destino.setObservaciones(origen.getObservaciones());
        destino.setPotenciaBIE(origen.getPotenciaBIE());
        destino.setPotenciaTecnicaRed(origen.getPotenciaTecnicaRed());
        destino.setApmCentroTransformacion(origen.getApmCentroTransformacion());
        destino.setNumeroContador(origen.getNumeroContador());
        destino.setNumeroTelefono(origen.getNumeroTelefono());
        destino.setDireccionEnlace(origen.getDireccionEnlace());
        destino.setPuntoMedida(origen.getPuntoMedida());
        destino.setClave(origen.getClave());
        destino.setExencionImpuestoElectrico(origen.getExencionImpuestoElectrico());
        destino.setPorcentajeConcesion(origen.getPorcentajeConcesion());
        destino.setFechaConcesion(origen.getFechaConcesion());
        destino.setAutoconsumo(origen.getAutoconsumo());
        destino.setFechaActivacionVertido(origen.getFechaActivacionVertido());
        destino.setUsuarioMonitorizacion(origen.getUsuarioMonitorizacion());
        destino.setPassMonitorizacion(origen.getPassMonitorizacion());
        destino.setBateriaCondensadores(origen.getBateriaCondensadores());
        destino.setPotenciaBateria(origen.getPotenciaBateria());
        destino.setModeloBateria(origen.getModeloBateria());
        destino.setInstalacionFV(origen.getInstalacionFV());
        destino.setKwInversor(origen.getKwInversor());
        destino.setKwPanelesSolares(origen.getKwPanelesSolares());
        destino.setEjecutadoPorAuraFv(origen.getEjecutadoPorAuraFv());
        destino.setUsuarioInstalacionFv(origen.getUsuarioInstalacionFv());
        destino.setContrasenaInstalacionFv(origen.getContrasenaInstalacionFv());
        destino.setBateriaAlmacenamiento(origen.getBateriaAlmacenamiento());
        destino.setPotenciaBateriaAlmacenamiento(origen.getPotenciaBateriaAlmacenamiento());
        destino.setBackup(origen.getBackup());
        destino.setPeaksaving(origen.getPeaksaving());
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

    @Transactional
    public void reactivarPuntoSuministro(Integer id) {
        puntoSuministroRepository.findById(id).ifPresent(ps -> {
            if (ps.getCliente() != null && BajaEfectiva.tieneBaja(ps.getCliente().getFechaBaja())) {
                throw new IllegalArgumentException("No se puede reactivar el punto de suministro porque el cliente asociado está dado de baja. Debe reactivar el cliente primero.");
            }
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
        int exencionImpuestoElectricidad = puntoSuministro.getExencionImpuestoElectrico() != null
                && puntoSuministro.getExencionImpuestoElectrico() ? 1 : 0;

        boolean tieneAutoconsumo = puntoSuministro.getAutoconsumo() != null &&
                !puntoSuministro.getAutoconsumo().trim().isEmpty();
        int autoconsumo = tieneAutoconsumo ? 1 : 0;

        // La excepcion debe salir del metodo @Transactional para revertir tambien
        // el guardado del punto de suministro.
        contratoRepository.actualizarExencionYAutoconsumoPorPuntoSuministro(
                puntoSuministro.getId(), exencionImpuestoElectricidad, autoconsumo);
    }
}
