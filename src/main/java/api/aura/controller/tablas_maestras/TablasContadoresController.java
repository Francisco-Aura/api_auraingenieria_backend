package api.aura.controller.tablas_maestras;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import api.aura.service.tablas_maestras.TablasContadoresService;

import java.util.Map;

@RestController
@RequestMapping("/api/tablas")
public class TablasContadoresController {

    private final TablasContadoresService tablasContadoresService;

    public TablasContadoresController(TablasContadoresService tablasContadoresService) {
        this.tablasContadoresService = tablasContadoresService;
    }

    @GetMapping("/contadores")
    public Map<String, Long> obtenerContadores() {
        return tablasContadoresService.obtenerContadores();
    }
}
