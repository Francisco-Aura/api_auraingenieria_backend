package api.aura.service.mcas;

import api.aura.dto.mcas.McasRecuentoDTO;
import api.aura.dto.mcas.McasResultadoLoteDTO;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.repository.mcas.McasPropuestaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Operaciones del validador sobre varias propuestas a la vez: el contador de
 * pendientes del menú y la aprobación en lote.
 *
 * <p>La aprobación en lote NO es transaccional a propósito: cada propuesta se
 * aprueba a través del proxy de {@link McasProposalService}, en su propia
 * transacción, de modo que una que falle (desactualizada, ya aprobada...) no
 * deshace las demás y el resultado es parcial.</p>
 */
@Service
public class McasLoteService {

    private static final Logger log = LoggerFactory.getLogger(McasLoteService.class);

    public static final int MAX_LOTE = 200;

    private static final List<String> POR_REVISAR =
            List.of("REQUIERE_DATOS", "REQUIERE_REVISION_MANUAL", "ERROR_CALCULO", "PENDIENTE_CALCULO");
    private static final List<String> EN_CORRECCION = List.of("RECHAZADO", "EN_EDICION");

    private final McasProposalService propuestas;
    private final McasPropuestaRepository propuestaRepo;

    @PersistenceContext
    private EntityManager em;

    public McasLoteService(McasProposalService propuestas, McasPropuestaRepository propuestaRepo) {
        this.propuestas = propuestas;
        this.propuestaRepo = propuestaRepo;
    }

    /**
     * Pendientes por grupo. Una propuesta DESACTUALIZADO solo cuenta si sigue
     * siendo la última de su contrato: al guardar un contrato cambiado, la anterior
     * queda DESACTUALIZADO y otra nueva la sustituye, y esa ya no hay que revisarla.
     */
    @Transactional(readOnly = true)
    public McasRecuentoDTO recuento() {
        List<String> estados = new ArrayList<>(POR_REVISAR);
        estados.addAll(EN_CORRECCION);
        estados.add("PENDIENTE_VALIDACION");
        Map<String, Long> porEstado = new HashMap<>();
        for (Object[] fila : propuestaRepo.contarPorEstado(estados))
            porEstado.put((String) fila[0], ((Number) fila[1]).longValue());

        // Misma regla que «ultimaDelContrato» del listado: mayor creadoEn y, a igualdad, mayor id.
        long desactualizadas = em.createQuery("SELECT COUNT(p) FROM McasPropuesta p WHERE p.estado = 'DESACTUALIZADO' "
                + "AND NOT EXISTS (SELECT q.id FROM McasPropuesta q WHERE q.idContrato = p.idContrato "
                + "AND (q.creadoEn > p.creadoEn OR (q.creadoEn = p.creadoEn AND q.id > p.id)))",
                Long.class).getSingleResult();

        long porValidar = porEstado.getOrDefault("PENDIENTE_VALIDACION", 0L);
        long porRevisar = suma(porEstado, POR_REVISAR) + desactualizadas;
        long enCorreccion = suma(porEstado, EN_CORRECCION);
        // Las retrocesiones pendientes de aprobar también cuentan en el aviso del menú
        long retrocesiones = em.createQuery("SELECT COUNT(r) FROM McasRetrocesion r WHERE r.estado = 'PENDIENTE'",
                Long.class).getSingleResult();
        return new McasRecuentoDTO(porValidar, porRevisar, enCorreccion,
                porValidar + porRevisar + enCorreccion + retrocesiones);
    }

    /**
     * Aprueba las propuestas indicadas (como máximo {@value #MAX_LOTE}, sin
     * repetir) una a una, cada una en su transacción. Devuelve un resultado por
     * propuesta, en el orden recibido, con un mensaje para el usuario.
     */
    public List<McasResultadoLoteDTO> aprobarLote(List<Long> ids) {
        if (ids == null || ids.isEmpty())
            throw new IllegalArgumentException("Seleccione al menos una propuesta para aprobar");
        List<Long> unicos = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (unicos.size() > MAX_LOTE)
            throw new IllegalArgumentException("Se pueden aprobar como máximo " + MAX_LOTE + " propuestas a la vez");
        List<McasResultadoLoteDTO> resultados = new ArrayList<>();
        for (Long id : unicos) resultados.add(aprobarUna(id));
        return resultados;
    }

    private McasResultadoLoteDTO aprobarUna(Long id) {
        Optional<McasPropuesta> actual = propuestas.obtener(id);
        if (actual.isEmpty()) return McasResultadoLoteDTO.error(id, null, "La propuesta no existe.");
        if (!"PENDIENTE_VALIDACION".equals(actual.get().getEstado()))
            return McasResultadoLoteDTO.error(id, actual.get().getEstado(), "Esta propuesta no está pendiente de aprobar.");
        try {
            McasPropuesta aprobada = propuestas.aprobar(id);
            return McasResultadoLoteDTO.ok(id, aprobada.getEstado(), "Servicio #" + aprobada.getIdServicioCreado() + " creado");
        } catch (McasPropuestaDesactualizadaException e) {
            return McasResultadoLoteDTO.error(id, estado(id),
                    "El contrato ha cambiado desde el cálculo: recalcule la propuesta antes de aprobarla.");
        } catch (ObjectOptimisticLockingFailureException e) {
            return McasResultadoLoteDTO.error(id, estado(id),
                    "Otra persona ha modificado esta propuesta a la vez; recargue la lista.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return McasResultadoLoteDTO.error(id, estado(id), e.getMessage());
        } catch (RuntimeException e) {
            log.error("MCAS aprobación en lote: fallo inesperado en la propuesta {}", id, e);
            return McasResultadoLoteDTO.error(id, estado(id), "No se ha podido aprobar por un error inesperado.");
        }
    }

    /** Estado tras el intento (otra transacción): el que quedó guardado. */
    private String estado(Long id) {
        return propuestas.obtener(id).map(McasPropuesta::getEstado).orElse(null);
    }

    private long suma(Map<String, Long> porEstado, List<String> estados) {
        return estados.stream().mapToLong(e -> porEstado.getOrDefault(e, 0L)).sum();
    }
}
