package api.aura.service;

import org.springframework.stereotype.Service;
import api.aura.dto.informes.*;
import api.aura.repository.InformesRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.dto.ServicioPendientePagoDTO;

import java.sql.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InformesService {
 
    private final InformesRepository repository;
    private final ContratoRepository contratoRepository;
    private final PuntoSuministroRepository puntoSuministroRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public InformesService(InformesRepository repository, ContratoRepository contratoRepository, PuntoSuministroRepository puntoSuministroRepository) {
        this.repository = repository;
        this.contratoRepository = contratoRepository;
        this.puntoSuministroRepository = puntoSuministroRepository;
    }

    public List<ContratoPendienteRenovacionDTO> obtenerContratosPendientesRenovacion() {
        List<Object[]> resultados = repository.findContratosPendientesRenovacion();
        return resultados.stream().map(this::mapToContratoPendienteRenovacionDTO).collect(Collectors.toList());
    }

    private ContratoPendienteRenovacionDTO mapToContratoPendienteRenovacionDTO(Object[] row) {
        ContratoPendienteRenovacionDTO dto = new ContratoPendienteRenovacionDTO();
        dto.setCodigoContrato((Integer) row[0]);
        dto.setFecha((Date) row[1]);
        dto.setFechaEnvio((Date) row[2]);
        dto.setFechaInicio((Date) row[3]);
        dto.setFechaFin((Date) row[4]);
        dto.setIdCliente((Integer) row[5]);
        dto.setCliente((String) row[6]);
        dto.setIdPuntoSuministro((Integer) row[7]);
        dto.setCupsLuz((String) row[8]);
        dto.setCupsGas((String) row[9]);
        dto.setUserTelemedida((String) row[10]);
        dto.setPassTelemedida((String) row[11]);
        dto.setContrato((String) row[12]);
        dto.setIdTarifaLuz((Integer) row[13]);
        dto.setTarifaLuz((String) row[14]);
        dto.setIdTarifaGas((Integer) row[15]);
        dto.setTarifaGas((String) row[16]);
        dto.setPot1((Double) row[17]);
        dto.setPot2((Double) row[18]);
        dto.setPot3((Double) row[19]);
        dto.setPot4((Double) row[20]);
        dto.setPot5((Double) row[21]);
        dto.setPot6((Double) row[22]);
        dto.setConsumoAnual((Double) row[23]);
        dto.setExencionIE((Integer) row[24]);
        dto.setAutoconsumo((Integer) row[25]);
        dto.setIdComercializadoraActual((Integer) row[26]);
        dto.setComercializadoraActual((String) row[27]);
        dto.setIdComercializadoraAnterior((Integer) row[28]);
        dto.setComercializadoraAnterior((String) row[29]);
        dto.setObservaciones((String) row[30]);
        return dto;
    }

    /**
     * Obtiene la lista de puntos de suministro que no tienen contratos asociados.
     * @return Lista de DTOs con la información de los puntos de suministro sin contrato.
     */
    public List<PuntosSuministroSinContratoDTO> obtenerPuntosSuministroSinContrato() {
        List<Object[]> resultados = repository.findPuntosSuministroSinContrato();
        return resultados.stream().map(this::mapToPuntosSuministroSinContratoDTO).collect(Collectors.toList());
    }

    private PuntosSuministroSinContratoDTO mapToPuntosSuministroSinContratoDTO(Object[] row) {
        PuntosSuministroSinContratoDTO dto = new PuntosSuministroSinContratoDTO();
        dto.setIdPS((String) row[0]);
        dto.setIdCliente((String) row[1]);
        dto.setCliente((String) row[2]);
        dto.setCupsLuz((String) row[3]);
        dto.setCupsGas((String) row[4]);
        dto.setDireccion((String) row[5]);
        dto.setCodigoPostal((String) row[6]);
        dto.setPoblacion((String) row[7]);
        dto.setProvincia((String) row[8]);
        return dto;
    }

    public ContratoPendienteRenovacionDTO actualizarContratoPendienteRenovacion(Integer id, ContratoPendienteRenovacionDTO dto) {
        var contratoOpt = contratoRepository.findById(id);
        if (contratoOpt.isEmpty()) {
            throw new RuntimeException("Contrato no encontrado");
        }
        var contrato = contratoOpt.get();
        if (dto.getContrato() != null) contrato.setContrato(dto.getContrato());
        if (dto.getTarifaLuz() != null && dto.getIdTarifaLuz() != null) contrato.setIdTarifaLuz(dto.getIdTarifaLuz());
        if (dto.getTarifaGas() != null && dto.getIdTarifaGas() != null) contrato.setIdTarifaGas(dto.getIdTarifaGas());
        if (dto.getComercializadoraActual() != null && dto.getIdComercializadoraActual() != null) contrato.setIdComercializadoraActual(dto.getIdComercializadoraActual());
        if (dto.getComercializadoraAnterior() != null && dto.getIdComercializadoraAnterior() != null) contrato.setIdComercializadoraAnterior(dto.getIdComercializadoraAnterior());
        if (dto.getObservaciones() != null) contrato.setObservaciones(dto.getObservaciones());
        contratoRepository.save(contrato);
        return dto;
    }

    public PuntosSuministroSinContratoDTO actualizarPuntoSuministroSinContrato(String id, PuntosSuministroSinContratoDTO dto) {
        Integer idInt;
        try {
            idInt = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            throw new RuntimeException("ID de punto de suministro inválido");
        }
        var puntoOpt = puntoSuministroRepository.findById(idInt);
        if (puntoOpt.isEmpty()) {
            throw new RuntimeException("Punto de suministro no encontrado");
        }
        var punto = puntoOpt.get();
        if (dto.getDireccion() != null) punto.setDireccion(dto.getDireccion());
        if (dto.getCodigoPostal() != null) punto.setCodigoPostal(dto.getCodigoPostal());
        if (dto.getPoblacion() != null) punto.setPoblacion(dto.getPoblacion());
        if (dto.getProvincia() != null) punto.setProvincia(dto.getProvincia());
        if (dto.getCupsLuz() != null) punto.setCUPSluz(dto.getCupsLuz());
        if (dto.getCupsGas() != null) punto.setCUPSgas(dto.getCupsGas());
        puntoSuministroRepository.save(punto);
        return dto;
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
        return Integer.valueOf(value.toString());
    }

    public List<ServicioPendientePagoDTO> obtenerServiciosPendientesPago(java.time.LocalDate fechaDesde, java.time.LocalDate fechaHasta) {
        List<Object[]> resultados = repository.findServiciosPendientesPago(fechaDesde, fechaHasta);
        return resultados.stream().map(this::mapToServicioPendientePagoDTO).collect(Collectors.toList());
    }

    private ServicioPendientePagoDTO mapToServicioPendientePagoDTO(Object[] row) {
        ServicioPendientePagoDTO dto = new ServicioPendientePagoDTO();
        dto.setIdServicio(Long.valueOf(row[0].toString()));
        dto.setCliente((String) row[1]);
        dto.setServicio((String) row[2]);
        dto.setFechaVencimiento((java.sql.Date) row[3] != null ? ((java.sql.Date) row[3]).toLocalDate() : null);
        dto.setImporteTotal(row[4] != null ? java.math.BigDecimal.valueOf(((Number) row[4]).doubleValue()) : null);
        dto.setPrimerPagoPagado(toInt(row[5]));
        dto.setSegundoPagoPagado(toInt(row[6]));
        dto.setTercerPagoPagado(toInt(row[7]));
        dto.setCuartoPagoPagado(toInt(row[8]));
        dto.setQuintoPagoPagado(toInt(row[9]));
        dto.setSextoPagoPagado(toInt(row[10]));
        dto.setSeptimoPagoPagado(toInt(row[11]));
        dto.setOctavoPagoPagado(toInt(row[12]));
        dto.setNovenoPagoPagado(toInt(row[13]));
        dto.setDecimoPagoPagado(toInt(row[14]));
        dto.setDecimoprimerPagoPagado(toInt(row[15]));
        dto.setDecimosegundoPagoPagado(toInt(row[16]));
        dto.setEstadoVencimiento((String) row[17]);
        dto.setEstadoPago((String) row[18]);
        dto.setNumeroCuotas(toInt(row[19]));
        return dto;
    }

    public void actualizarPagoParcial(Long idServicio, int numeroPago, boolean pagado) {
        String campo = switch (numeroPago) {
            case 1 -> "primer_pago_pagado";
            case 2 -> "segundo_pago_pagado";
            case 3 -> "tercer_pago_pagado";
            case 4 -> "cuarto_pago_pagado";
            case 5 -> "quinto_pago_pagado";
            case 6 -> "sexto_pago_pagado";
            case 7 -> "septimo_pago_pagado";
            case 8 -> "octavo_pago_pagado";
            case 9 -> "noveno_pago_pagado";
            case 10 -> "decimo_pago_pagado";
            case 11 -> "decimoprimer_pago_pagado";
            case 12 -> "decimosegundo_pago_pagado";
            default -> throw new IllegalArgumentException("Número de pago inválido: " + numeroPago);
        };
        String sql = "UPDATE servicios_pagos SET " + campo + " = ? WHERE id_servicio = ?";
        jdbcTemplate.update(sql, pagado ? 1 : 0, idServicio);
    }
}
