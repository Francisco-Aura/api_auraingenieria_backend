package api.aura.service;

import org.springframework.stereotype.Service;
import api.aura.dto.informes.*;
import api.aura.entity.Cliente;
import api.aura.repository.InformesRepository;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.tablas_maestras.TBSectoresRepository;
import api.aura.dto.ServicioPendientePagoDTO;

import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InformesService {

    private final InformesRepository repository;
    private final ContratoRepository contratoRepository;
    private final PuntoSuministroRepository puntoSuministroRepository;
    private final ClienteRepository clienteRepository;
    private final TBSectoresRepository sectoresRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public InformesService(InformesRepository repository, ContratoRepository contratoRepository,
            PuntoSuministroRepository puntoSuministroRepository, ClienteRepository clienteRepository,
            TBSectoresRepository sectoresRepository) {
        this.repository = repository;
        this.contratoRepository = contratoRepository;
        this.puntoSuministroRepository = puntoSuministroRepository;
        this.clienteRepository = clienteRepository;
        this.sectoresRepository = sectoresRepository;
    }

    public List<ContratoPendienteRenovacionDTO> obtenerContratosPendientesRenovacion(Integer nivelAcceso, String usuarioNombre) {
        List<Object[]> resultados;
        if (nivelAcceso != null && nivelAcceso == 1) {
            resultados = repository.findContratosPendientesRenovacion();
        } else {
            resultados = repository.findContratosPendientesRenovacionByUsuario(
                usuarioNombre != null ? usuarioNombre : "");
        }
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
        dto.setComentarioRenovacion((String) row[31]);
        dto.setUsuarioAsignado((String) row[32]);
        return dto;
    }

    /**
     * Obtiene la lista de puntos de suministro que no tienen contratos asociados.
     * 
     * @return Lista de DTOs con la información de los puntos de suministro sin
     *         contrato.
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

    public ContratoPendienteRenovacionDTO actualizarContratoPendienteRenovacion(Integer id,
            ContratoPendienteRenovacionDTO dto) {
        var contratoOpt = contratoRepository.findById(id);
        if (contratoOpt.isEmpty()) {
            throw new RuntimeException("Contrato no encontrado");
        }
        var contrato = contratoOpt.get();
        if (dto.getContrato() != null)
            contrato.setContrato(dto.getContrato());
        if (dto.getTarifaLuz() != null && dto.getIdTarifaLuz() != null)
            contrato.setIdTarifaLuz(dto.getIdTarifaLuz());
        if (dto.getTarifaGas() != null && dto.getIdTarifaGas() != null)
            contrato.setIdTarifaGas(dto.getIdTarifaGas());
        if (dto.getComercializadoraActual() != null && dto.getIdComercializadoraActual() != null)
            contrato.setIdComercializadoraActual(dto.getIdComercializadoraActual());
        if (dto.getComercializadoraAnterior() != null && dto.getIdComercializadoraAnterior() != null)
            contrato.setIdComercializadoraAnterior(dto.getIdComercializadoraAnterior());
        if (dto.getObservaciones() != null)
            contrato.setObservaciones(dto.getObservaciones());
        contratoRepository.save(contrato);
        return dto;
    }

    public PuntosSuministroSinContratoDTO actualizarPuntoSuministroSinContrato(String id,
            PuntosSuministroSinContratoDTO dto) {
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
        if (dto.getDireccion() != null)
            punto.setDireccion(dto.getDireccion());
        if (dto.getCodigoPostal() != null)
            punto.setCodigoPostal(dto.getCodigoPostal());
        if (dto.getPoblacion() != null)
            punto.setPoblacion(dto.getPoblacion());
        if (dto.getProvincia() != null)
            punto.setProvincia(dto.getProvincia());
        if (dto.getCupsLuz() != null)
            punto.setCUPSluz(dto.getCupsLuz());
        if (dto.getCupsGas() != null)
            punto.setCUPSgas(dto.getCupsGas());
        puntoSuministroRepository.save(punto);
        return dto;
    }

    private Integer toInt(Object value) {
        if (value == null)
            return null;
        if (value instanceof Number)
            return ((Number) value).intValue();
        if (value instanceof Boolean)
            return (Boolean) value ? 1 : 0;
        return Integer.valueOf(value.toString());
    }

    public List<ServicioPendientePagoDTO> obtenerServiciosPendientesPago(java.time.LocalDate fechaDesde,
            java.time.LocalDate fechaHasta) {
        List<Object[]> resultados = repository.findServiciosPendientesPago(fechaDesde, fechaHasta);
        List<ServicioPendientePagoDTO> lista = resultados.stream().map(this::mapToServicioPendientePagoDTO)
                .collect(Collectors.toList());

        return lista;
    }

    private ServicioPendientePagoDTO mapToServicioPendientePagoDTO(Object[] row) {
        ServicioPendientePagoDTO dto = new ServicioPendientePagoDTO();

        dto.setIdServicio(Long.valueOf(row[0].toString()));
        dto.setCliente((String) row[1]);
        dto.setGestora((String) row[2]);
        dto.setCups((String) row[3]);
        
        // cupsLuz y cupsGas vienen en row[4] y row[5] respectivamente (tras el cups consolidado)
        String cupsLuzVal = row[4] != null ? ((String) row[4]).trim() : "";
        String cupsGasVal = row[5] != null ? ((String) row[5]).trim() : "";
        dto.setCupsLuz(cupsLuzVal.isEmpty() ? null : cupsLuzVal);
        dto.setCupsGas(cupsGasVal.isEmpty() ? null : cupsGasVal);
        
        // Calcular tipoCups basándose en cuál esté relleno
        if (!cupsLuzVal.isEmpty()) {
            dto.setTipoCups("Luz");
        } else if (!cupsGasVal.isEmpty()) {
            dto.setTipoCups("Gas");
        } else {
            dto.setTipoCups(null);
        }

        // Mapear fecha del siguiente pago (row[6] porque se desplazó 2 posiciones)
        dto.setFechaSiguientePago((java.sql.Date) row[6] != null ? ((java.sql.Date) row[6]).toLocalDate() : null);

        dto.setImporteTotal(row[7] != null ? java.math.BigDecimal.valueOf(((Number) row[7]).doubleValue()) : null);
        dto.setSiguientePagoNumero(toInt(row[8]));
        dto.setImporteCuotaPendiente(
                row[9] != null ? java.math.BigDecimal.valueOf(((Number) row[9]).doubleValue()) : null);

        // Mapear estados de pago (indices 10-21, desplazados 2 posiciones)
        dto.setPrimerPagoPagado(toInt(row[10]));
        dto.setSegundoPagoPagado(toInt(row[11]));
        dto.setTercerPagoPagado(toInt(row[12]));
        dto.setCuartoPagoPagado(toInt(row[13]));
        dto.setQuintoPagoPagado(toInt(row[14]));
        dto.setSextoPagoPagado(toInt(row[15]));
        dto.setSeptimoPagoPagado(toInt(row[16]));
        dto.setOctavoPagoPagado(toInt(row[17]));
        dto.setNovenoPagoPagado(toInt(row[18]));
        dto.setDecimoPagoPagado(toInt(row[19]));
        dto.setDecimoprimerPagoPagado(toInt(row[20]));
        dto.setDecimosegundoPagoPagado(toInt(row[21]));

        dto.setEstadoPago((String) row[22]);
        dto.setNumeroCuotas(toInt(row[23]));

        // Calcular dinámicamente el siguiente pago pendiente si no viene en la consulta
        if (dto.getSiguientePagoNumero() == null) {
            dto.setSiguientePagoNumero(calcularSiguientePagoVencido(dto.getIdServicio()));
        }

        return dto;
    }

    private Integer calcularSiguientePagoVencido(Long idServicio) {
        String sql = """
                SELECT
                    CASE
                        WHEN COALESCE(sp.primer_pago_pagado, 0) = 0 AND sp.primer_pago_fecha <= CURDATE() AND COALESCE(sp.primer_pago_importe, 0) > 0 THEN 1
                        WHEN COALESCE(sp.segundo_pago_pagado, 0) = 0 AND sp.segundo_pago_fecha <= CURDATE() AND COALESCE(sp.segundo_pago_importe, 0) > 0 THEN 2
                        WHEN COALESCE(sp.tercer_pago_pagado, 0) = 0 AND sp.tercer_pago_fecha <= CURDATE() AND COALESCE(sp.tercer_pago_importe, 0) > 0 THEN 3
                        WHEN COALESCE(sp.cuarto_pago_pagado, 0) = 0 AND sp.cuarto_pago_fecha <= CURDATE() AND COALESCE(sp.cuarto_pago_importe, 0) > 0 THEN 4
                        WHEN COALESCE(sp.quinto_pago_pagado, 0) = 0 AND sp.quinto_pago_fecha <= CURDATE() AND COALESCE(sp.quinto_pago_importe, 0) > 0 THEN 5
                        WHEN COALESCE(sp.sexto_pago_pagado, 0) = 0 AND sp.sexto_pago_fecha <= CURDATE() AND COALESCE(sp.sexto_pago_importe, 0) > 0 THEN 6
                        WHEN COALESCE(sp.septimo_pago_pagado, 0) = 0 AND sp.septimo_pago_fecha <= CURDATE() AND COALESCE(sp.septimo_pago_importe, 0) > 0 THEN 7
                        WHEN COALESCE(sp.octavo_pago_pagado, 0) = 0 AND sp.octavo_pago_fecha <= CURDATE() AND COALESCE(sp.octavo_pago_importe, 0) > 0 THEN 8
                        WHEN COALESCE(sp.noveno_pago_pagado, 0) = 0 AND sp.noveno_pago_fecha <= CURDATE() AND COALESCE(sp.noveno_pago_importe, 0) > 0 THEN 9
                        WHEN COALESCE(sp.decimo_pago_pagado, 0) = 0 AND sp.decimo_pago_fecha <= CURDATE() AND COALESCE(sp.decimo_pago_importe, 0) > 0 THEN 10
                        WHEN COALESCE(sp.decimoprimer_pago_pagado, 0) = 0 AND sp.decimoprimer_pago_fecha <= CURDATE() AND COALESCE(sp.decimoprimer_pago_importe, 0) > 0 THEN 11
                        WHEN COALESCE(sp.decimosegundo_pago_pagado, 0) = 0 AND sp.decimosegundo_pago_fecha <= CURDATE() AND COALESCE(sp.decimosegundo_pago_importe, 0) > 0 THEN 12
                        ELSE NULL
                    END AS siguientePagoNumero
                FROM servicios_pagos sp
                WHERE sp.id_servicio = ?
                """;

        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, idServicio);
        } catch (Exception e) {
            return null;
        }
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

    public void marcarSiguientePagoPendiente(Long idServicio) {
        String sql = """
                SELECT numero FROM (
                    SELECT 1 AS numero, COALESCE(sp.primer_pago_pagado, 0) AS pagado, sp.primer_pago_fecha AS fecha, COALESCE(sp.primer_pago_importe, 0) AS importe FROM servicios_pagos sp WHERE sp.id_servicio = ?
                    UNION ALL SELECT 2, COALESCE(sp.segundo_pago_pagado, 0), sp.segundo_pago_fecha, COALESCE(sp.segundo_pago_importe, 0) FROM servicios_pagos sp WHERE sp.id_servicio = ?
                    UNION ALL SELECT 3, COALESCE(sp.tercer_pago_pagado, 0), sp.tercer_pago_fecha, COALESCE(sp.tercer_pago_importe, 0) FROM servicios_pagos sp WHERE sp.id_servicio = ?
                    UNION ALL SELECT 4, COALESCE(sp.cuarto_pago_pagado, 0), sp.cuarto_pago_fecha, COALESCE(sp.cuarto_pago_importe, 0) FROM servicios_pagos sp WHERE sp.id_servicio = ?
                    UNION ALL SELECT 5, COALESCE(sp.quinto_pago_pagado, 0), sp.quinto_pago_fecha, COALESCE(sp.quinto_pago_importe, 0) FROM servicios_pagos sp WHERE sp.id_servicio = ?
                    UNION ALL SELECT 6, COALESCE(sp.sexto_pago_pagado, 0), sp.sexto_pago_fecha, COALESCE(sp.sexto_pago_importe, 0) FROM servicios_pagos sp WHERE sp.id_servicio = ?
                    UNION ALL SELECT 7, COALESCE(sp.septimo_pago_pagado, 0), sp.septimo_pago_fecha, COALESCE(sp.septimo_pago_importe, 0) FROM servicios_pagos sp WHERE sp.id_servicio = ?
                    UNION ALL SELECT 8, COALESCE(sp.octavo_pago_pagado, 0), sp.octavo_pago_fecha, COALESCE(sp.octavo_pago_importe, 0) FROM servicios_pagos sp WHERE sp.id_servicio = ?
                    UNION ALL SELECT 9, COALESCE(sp.noveno_pago_pagado, 0), sp.noveno_pago_fecha, COALESCE(sp.noveno_pago_importe, 0) FROM servicios_pagos sp WHERE sp.id_servicio = ?
                    UNION ALL SELECT 10, COALESCE(sp.decimo_pago_pagado, 0), sp.decimo_pago_fecha, COALESCE(sp.decimo_pago_importe, 0) FROM servicios_pagos sp WHERE sp.id_servicio = ?
                    UNION ALL SELECT 11, COALESCE(sp.decimoprimer_pago_pagado, 0), sp.decimoprimer_pago_fecha, COALESCE(sp.decimoprimer_pago_importe, 0) FROM servicios_pagos sp WHERE sp.id_servicio = ?
                    UNION ALL SELECT 12, COALESCE(sp.decimosegundo_pago_pagado, 0), sp.decimosegundo_pago_fecha, COALESCE(sp.decimosegundo_pago_importe, 0) FROM servicios_pagos sp WHERE sp.id_servicio = ?
                ) AS pagos
                WHERE pagado = 0 AND fecha <= CURDATE() AND importe > 0
                ORDER BY numero
                LIMIT 1
                """;

        try {
            Integer siguientePago = jdbcTemplate.queryForObject(sql, Integer.class, idServicio, idServicio, idServicio,
                    idServicio, idServicio, idServicio, idServicio, idServicio, idServicio, idServicio, idServicio,
                    idServicio);

            if (siguientePago != null) {
                actualizarPagoParcial(idServicio, siguientePago, true);
            } else {
                throw new IllegalArgumentException("No hay pagos pendientes para este servicio");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo marcar el pago. Por favor, int\u00e9ntelo de nuevo.", e);
        }
    }

    public List<ContratoVigenteDTO> obtenerContratosVigentes() {
        List<Object[]> resultados = repository.findContratosVigentes();
        return resultados.stream().map(this::mapToContratoVigenteDTO).collect(Collectors.toList());
    }

    private ContratoVigenteDTO mapToContratoVigenteDTO(Object[] row) {
        ContratoVigenteDTO dto = new ContratoVigenteDTO();
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
        dto.setContrato((String) row[10]);
        dto.setIdTarifaLuz((Integer) row[11]);
        dto.setTarifaLuz((String) row[12]);
        dto.setIdTarifaGas((Integer) row[13]);
        dto.setTarifaGas((String) row[14]);
        dto.setPotencia1((Double) row[15]);
        dto.setPotencia2((Double) row[16]);
        dto.setPotencia3((Double) row[17]);
        dto.setPotencia4((Double) row[18]);
        dto.setPotencia5((Double) row[19]);
        dto.setPotencia6((Double) row[20]);
        dto.setConsumoAnual((Double) row[21]);
        dto.setExencionIE((Integer) row[22]);
        dto.setAutoconsumo((Integer) row[23]);
        dto.setIdComercializadoraActual((Integer) row[24]);
        dto.setComercializadoraActual((String) row[25]);
        dto.setIdComercializadoraAnterior((Integer) row[26]);
        dto.setComercializadoraAnterior((String) row[27]);
        dto.setObservaciones((String) row[28]);
        return dto;
    }

    public List<ServicioVigenteDTO> obtenerServiciosVigentes(java.time.LocalDate fechaDesde,
            java.time.LocalDate fechaHasta) {
        List<Object[]> resultados = repository.findServiciosVigentes(fechaDesde, fechaHasta);
        return resultados.stream().map(this::mapToServicioVigenteDTO).collect(Collectors.toList());
    }

    private ServicioVigenteDTO mapToServicioVigenteDTO(Object[] row) {
        ServicioVigenteDTO dto = new ServicioVigenteDTO();
        dto.setIdServicio(Long.valueOf(row[0].toString()));
        dto.setCliente((String) row[1]);
        dto.setServicio((String) row[2]);
        dto.setFechaInicioServicio((Date) row[3]);
        dto.setFechaFinServicio((Date) row[4]);
        dto.setFechaPago((Date) row[5]);
        dto.setGestora((String) row[6]);
        dto.setProducto((String) row[7]);
        dto.setComercializadora((String) row[8]);
        dto.setCups((String) row[9]);
        dto.setImporte(row[10] != null ? java.math.BigDecimal.valueOf(((Number) row[10]).doubleValue()) : null);
        dto.setDescuento(row[11] != null ? java.math.BigDecimal.valueOf(((Number) row[11]).doubleValue()) : null);
        dto.setImporteFinal(row[12] != null ? java.math.BigDecimal.valueOf(((Number) row[12]).doubleValue()) : null);
        dto.setMetodoPago((String) row[13]);
        dto.setPagoPlazos((String) row[14]);
        dto.setNumeroCuotas(toInt(row[15]));
        dto.setObservaciones((String) row[16]);
        dto.setEstadoVencimiento((String) row[17]);
        dto.setEstadoPago((String) row[18]);
        return dto;
    }

    public List<ContactoClienteDTO> obtenerContactosClientes() {
        List<Cliente> clientes = clienteRepository.findAll();
        List<ContactoClienteDTO> resultado = new ArrayList<>();

        // Cargar sectores en un mapa para resolución eficiente
        Map<Integer, String> sectorMap = new HashMap<>();
        sectoresRepository.findAll().forEach(s -> sectorMap.put(s.getId(), s.getNombre()));

        for (Cliente c : clientes) {
            String nombreCliente = c.getNombre() != null ? c.getNombre() : "";
            String estadoBaja = c.getFechaBaja() != null ? "SI" : "NO";
            String sector = c.getCodigoSector() != null ? sectorMap.getOrDefault(c.getCodigoSector(), "") : "";

            // Fila de datos principales (Autorizado) — siempre se incluye para que
            // todos los clientes aparezcan en el informe, tengan o no datos de contacto
            resultado.add(new ContactoClienteDTO(
                    nombreCliente,
                    "Datos Principales",
                    nvl(c.getNombreAutorizado()),
                    "",
                    nvl(c.getTelefonoAutorizado()),
                    nvl(c.getEmailAutorizado()),
                    sector,
                    estadoBaja));

            // Contacto 1
            if (tieneContacto(c.getNombreContacto1(), c.getTelefonoContacto1(), c.getEmailContacto1())) {
                resultado.add(new ContactoClienteDTO(
                        nombreCliente,
                        "Contacto 1",
                        nvl(c.getNombreContacto1()),
                        nvl(c.getCargoContacto1()),
                        nvl(c.getTelefonoContacto1()),
                        nvl(c.getEmailContacto1()),
                        sector,
                        estadoBaja));
            }

            // Contacto 2
            if (tieneContacto(c.getNombreContacto2(), c.getTelefonoContacto2(), c.getEmailContacto2())) {
                resultado.add(new ContactoClienteDTO(
                        nombreCliente,
                        "Contacto 2",
                        nvl(c.getNombreContacto2()),
                        nvl(c.getCargoContacto2()),
                        nvl(c.getTelefonoContacto2()),
                        nvl(c.getEmailContacto2()),
                        sector,
                        estadoBaja));
            }

            // Contacto 3
            if (tieneContacto(c.getNombreContacto3(), c.getTelefonoContacto3(), c.getEmailContacto3())) {
                resultado.add(new ContactoClienteDTO(
                        nombreCliente,
                        "Contacto 3",
                        nvl(c.getNombreContacto3()),
                        nvl(c.getCargoContacto3()),
                        nvl(c.getTelefonoContacto3()),
                        nvl(c.getEmailContacto3()),
                        sector,
                        estadoBaja));
            }
        }

        // Ordenar: activos primero (NO baja), luego por nombre de cliente
        resultado.sort(java.util.Comparator
                .comparing((ContactoClienteDTO d) -> d.getEstadoBaja())   // "NO" < "SI" lexicográficamente
                .thenComparing(ContactoClienteDTO::getNombreCliente, String.CASE_INSENSITIVE_ORDER));

        return resultado;
    }

    private boolean tieneContacto(String nombre, String telefono, String correo) {
        return (nombre != null && !nombre.trim().isEmpty())
                || (telefono != null && !telefono.trim().isEmpty())
                || (correo != null && !correo.trim().isEmpty());
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}

