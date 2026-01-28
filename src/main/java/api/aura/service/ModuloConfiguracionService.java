package api.aura.service;

import api.aura.entity.ModuloConfiguracion;
import api.aura.repository.ModuloConfiguracionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ModuloConfiguracionService {

    private final ModuloConfiguracionRepository moduloConfiguracionRepository;

    public boolean estaActivo(String codigo) {
        return moduloConfiguracionRepository.findByCodigo(codigo)
                .map(ModuloConfiguracion::getActivo)
                .orElse(false);
    }

    public ModuloConfiguracion activar(String codigo) {
        ModuloConfiguracion modulo = moduloConfiguracionRepository.findByCodigo(codigo)
                .orElse(ModuloConfiguracion.builder().codigo(codigo).build());
        modulo.setActivo(true);
        return moduloConfiguracionRepository.save(modulo);
    }

    public ModuloConfiguracion desactivar(String codigo) {
        ModuloConfiguracion modulo = moduloConfiguracionRepository.findByCodigo(codigo)
                .orElse(ModuloConfiguracion.builder().codigo(codigo).build());
        modulo.setActivo(false);
        return moduloConfiguracionRepository.save(modulo);
    }
}
