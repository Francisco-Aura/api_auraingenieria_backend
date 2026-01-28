package api.aura.controller;

import api.aura.entity.ModuloConfiguracion;
import api.aura.service.ModuloConfiguracionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/configuracion-modulos")
@RequiredArgsConstructor
public class ConfiguracionModulosController {

    private final ModuloConfiguracionService moduloConfiguracionService;

    @GetMapping("/{codigo}/activo")
    public ResponseEntity<Map<String, Boolean>> estaActivo(@PathVariable String codigo) {
        return ResponseEntity.ok(Map.of("activo", moduloConfiguracionService.estaActivo(codigo)));
    }

    @PostMapping("/{codigo}/activar")
    public ResponseEntity<ModuloConfiguracion> activar(@PathVariable String codigo) {
        return ResponseEntity.ok(moduloConfiguracionService.activar(codigo));
    }

    @PostMapping("/{codigo}/desactivar")
    public ResponseEntity<ModuloConfiguracion> desactivar(@PathVariable String codigo) {
        return ResponseEntity.ok(moduloConfiguracionService.desactivar(codigo));
    }
}
