package api.aura.service;

import org.springframework.stereotype.Service;

import api.aura.dto.informes.*;
import api.aura.repository.InformesRepository;

import java.sql.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InformesService {
 
    private final InformesRepository repository;

    public InformesService(InformesRepository repository) {
        this.repository = repository;
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
}
