package api.aura.service.mcas;

import api.aura.entity.mcas.McasAuditoria;
import api.aura.repository.mcas.McasAuditoriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class McasAuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(McasAuditoriaService.class);

    private final McasAuditoriaRepository repo;
    private final ObjectMapper mapper;

    /** REQUIRES_NEW: el audit se persiste aunque la transacción principal falle */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public McasAuditoria registrar(Long idPropuesta, Integer idContrato, String tipoEvento,
                                    String estadoAnterior, String estadoNuevo,
                                    Object datos, String usuario, String correlacionId) {
        String datosJson = null;
        if (datos != null) {
            try {
                datosJson = mapper.writeValueAsString(datos);
            } catch (Exception e) {
                // La fila de auditoria se guarda igual, pero sin el detalle: conviene
                // saber por que, en lugar de encontrarse la columna vacia sin mas.
                log.warn("MCAS auditoria propuesta={} evento={}: no se pudo serializar el detalle ({})",
                        idPropuesta, tipoEvento, e.getMessage());
            }
        }
        return repo.save(McasAuditoria.builder()
                .idPropuesta(idPropuesta).idContrato(idContrato)
                .tipoEvento(tipoEvento)
                .estadoAnterior(estadoAnterior).estadoNuevo(estadoNuevo)
                .datosJson(datosJson).usuario(usuario).idCorrelacion(correlacionId)
                .build());
    }
}
