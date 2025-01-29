package api.aura.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import api.aura.dto.informes.*;
import api.aura.service.InformesService;


@RestController
@RequestMapping("/api/informes")
public class InformesController {
    private final InformesService servicio;

    public InformesController(InformesService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/contratos-pendientes-renovacion")
    public List<ContratoPendienteRenovacionDTO> obtenerContratosPendientesRenovacion() {
        return servicio.obtenerContratosPendientesRenovacion();
    }

    @GetMapping("/puntos-suministro-sin-contrato")
    public List<PuntosSuministroSinContratoDTO> obtenerPuntosSuministroSinContrato() {
        return servicio.obtenerPuntosSuministroSinContrato();
    }
}
