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

        return resultados.stream().map(obj -> {
            ContratoPendienteRenovacionDTO dto = new ContratoPendienteRenovacionDTO();
            dto.setCodigoContrato((Integer) obj[0]);
            dto.setFecha((Date) obj[1]);
            dto.setFechaEnvio((Date) obj[2]);
            dto.setFechaInicio((Date) obj[3]);
            dto.setFechaFin((Date) obj[4]);
            dto.setIdCliente((Integer) obj[5]);
            dto.setCliente((String) obj[6]);
            dto.setIdPuntoSuministro((Integer) obj[7]);
            dto.setCupsLuz((String) obj[8]);
            dto.setCupsGas((String) obj[9]);
            dto.setUserTelemedida((String) obj[10]);
            dto.setPassTelemedida((String) obj[11]);
            dto.setContrato((String) obj[12]);
            dto.setIdTarifaLuz((Integer) obj[13]);
            dto.setTarifaLuz((String) obj[14]);
            dto.setIdTarifaGas((Integer) obj[15]);
            dto.setTarifaGas((String) obj[16]);
            dto.setPot1((Double) obj[17]);
            dto.setPot2((Double) obj[18]);
            dto.setPot3((Double) obj[19]);
            dto.setPot4((Double) obj[20]);
            dto.setPot5((Double) obj[21]); 
            dto.setPot6((Double) obj[22]);
            dto.setConsumoAnual((Double) obj[23]);
            dto.setExencionIE((Integer) obj[24]);
            dto.setAutoconsumo((Integer) obj[25]);
            dto.setIdComercializadoraActual((Integer) obj[26]);
            dto.setComercializadoraActual((String) obj[27]);
            dto.setIdComercializadoraAnterior((Integer) obj[28]);
            dto.setComercializadoraAnterior((String) obj[29]);
            dto.setObservaciones((String) obj[30]);
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Obtiene la lista de puntos de suministro que no tienen contratos asociados.
     * @return Lista de DTOs con la información de los puntos de suministro sin contrato.
     */
    public List<PuntosSuministroSinContratoDTO> obtenerPuntosSuministroSinContrato() {
        List<Object[]> resultados = repository.findPuntosSuministroSinContrato();
        return resultados.stream().map(obj -> {
            PuntosSuministroSinContratoDTO dto = new PuntosSuministroSinContratoDTO();
            dto.setIdPS((String) obj[0]);
            dto.setIdCliente((String) obj[1]);
            dto.setCliente((String) obj[2]);
            dto.setCupsLuz((String) obj[3]);
            dto.setCupsGas((String) obj[4]);
            dto.setDireccion((String) obj[5]);
            dto.setCodigoPostal((String) obj[6]);
            dto.setPoblacion((String) obj[7]);
            dto.setProvincia((String) obj[8]);
            return dto;
        }).collect(Collectors.toList());

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
        return resultados.stream().map(obj -> {
            ServicioPendientePagoDTO dto = new ServicioPendientePagoDTO();
            dto.setIdServicio(obj[0] != null ? ((Number) obj[0]).longValue() : null);
            dto.setCliente((String) obj[1]);
            dto.setServicio((String) obj[2]);
            dto.setFechaVencimiento(obj[3] != null ? ((java.sql.Date) obj[3]).toLocalDate() : null);
            dto.setImporteTotal(obj[4] != null ? new java.math.BigDecimal(obj[4].toString()) : null);
            dto.setPrimerPagoPagado(toInt(obj[5]));
            dto.setSegundoPagoPagado(toInt(obj[6]));
            dto.setTercerPagoPagado(toInt(obj[7]));
            dto.setCuartoPagoPagado(toInt(obj[8]));
            dto.setQuintoPagoPagado(toInt(obj[9]));
            dto.setSextoPagoPagado(toInt(obj[10]));
            dto.setEstadoVencimiento((String) obj[11]);
            dto.setEstadoPago((String) obj[12]);
            return dto;
        }).toList();
    }

    public void actualizarPagoParcial(Long idServicio, int numeroPago, boolean pagado) {
        String campo = switch (numeroPago) {
            case 1 -> "primer_pago_pagado";
            case 2 -> "segundo_pago_pagado";
            case 3 -> "tercer_pago_pagado";
            case 4 -> "cuarto_pago_pagado";
            case 5 -> "quinto_pago_pagado";
            case 6 -> "sexto_pago_pagado";
            default -> throw new IllegalArgumentException("Número de pago inválido");
        };
        String sql = "UPDATE servicios_pagos SET " + campo + " = ? WHERE id_servicio = ?";
        jdbcTemplate.update(sql, pagado ? 1 : 0, idServicio);
    }
}
