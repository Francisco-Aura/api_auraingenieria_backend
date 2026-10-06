package api.aura.service.mcas;

import api.aura.dto.mcas.McasResumenContratoDTO;
import api.aura.entity.Contrato;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.repository.ContratoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Comisión del contrato para quien lo graba (pestaña del contrato): estado,
 * importe, pagos, qué falta y una frase en usted. Sin traza, códigos de regla,
 * JSON ni usuarios; la ven todos los usuarios identificados.
 */
@Service
@RequiredArgsConstructor
public class McasResumenContratoService {

    private static final List<String> OCULTAS = List.of("SUPERADO", "CANCELADO");

    private final McasProposalService propuestas;
    private final ContratoRepository contratos;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public McasResumenContratoDTO resumen(Integer idContrato) {
        Contrato contrato = contratos.findById(idContrato)
                .orElseThrow(() -> new IllegalArgumentException("El contrato no existe"));
        boolean moduloActivo = propuestas.moduloHabilitado();
        Optional<McasPropuesta> actual = propuestaActual(idContrato);
        if (actual.isEmpty()) return sinPropuesta(contrato, moduloActivo);

        McasPropuesta p = actual.get();
        BigDecimal importe = p.getImporteDefinitivo() != null ? p.getImporteDefinitivo() : p.getImporteCalculado();
        List<String> advertencias = advertencias(p);
        List<String> codigos = "REQUIERE_DATOS".equals(p.getEstado()) ? camposFaltantes(p) : List.of();
        return McasResumenContratoDTO.builder()
                .moduloActivo(moduloActivo).hayPropuesta(true).idPropuesta(p.getId()).estado(p.getEstado())
                .importe(importe).numeroPagos(p.getNumeroPagos()).queFalta(McasMensajes.listaCampos(codigos))
                .mensaje(mensaje(p, importe, advertencias, codigos))
                .actualizadoEn(p.getActualizadoEn() != null ? p.getActualizadoEn() : p.getCreadoEn())
                .servicioCreado(p.getIdServicioCreado() != null)
                .build();
    }

    /**
     * La última propuesta del contrato (sin las sustituidas ni canceladas). Si ya
     * tiene una comisión aprobada vigente para ese evento, se muestra esa: la
     * propuesta posterior en revisión solo avisa de que no habrá otra comisión.
     */
    private Optional<McasPropuesta> propuestaActual(Integer idContrato) {
        List<McasPropuesta> todas = propuestas.listarPorContrato(idContrato).stream()
                .filter(p -> !OCULTAS.contains(p.getEstado())).toList();
        if (todas.isEmpty()) return Optional.empty();
        McasPropuesta ultima = todas.get(0);
        if (McasProposalService.ESTADOS_CON_COMISION.contains(ultima.getEstado())) return Optional.of(ultima);
        Set<String> conComision = propuestas.contratosConComisionVigente(todas);
        if (conComision.contains(McasProposalService.claveComision(idContrato, ultima.getTipoEvento())))
            return todas.stream()
                    .filter(p -> McasProposalService.ESTADOS_CON_COMISION.contains(p.getEstado()))
                    .filter(p -> Objects.equals(p.getTipoEvento(), ultima.getTipoEvento()))
                    .findFirst().or(() -> Optional.of(ultima));
        return Optional.of(ultima);
    }

    private McasResumenContratoDTO sinPropuesta(Contrato contrato, boolean moduloActivo) {
        if (!moduloActivo)
            return McasResumenContratoDTO.builder().moduloActivo(false).hayPropuesta(false)
                    .mensaje("El cálculo automático de comisiones está desactivado: la comisión se gestiona manualmente.")
                    .build();
        List<String> queFalta = McasMensajes.listaCampos(propuestas.camposFaltantes(contrato));
        return McasResumenContratoDTO.builder().moduloActivo(true).hayPropuesta(false).queFalta(queFalta)
                .mensaje(queFalta.isEmpty()
                        ? "Este contrato aún no tiene comisión calculada. Se calculará al guardar el contrato."
                        : "Este contrato aún no tiene comisión calculada. Complete los datos que faltan y guarde el contrato.")
                .build();
    }

    private String mensaje(McasPropuesta p, BigDecimal importe, List<String> advertencias, List<String> codigos) {
        int datos = McasMensajes.listaCampos(codigos).size();
        String primera = advertencias.isEmpty() ? "" : advertencias.get(0);
        String conAviso = primera.isEmpty() ? "" : " " + primera;
        return switch (p.getEstado()) {
            case "PENDIENTE_CALCULO" -> "La comisión se está calculando.";
            case "PENDIENTE_VALIDACION" -> "Comisión pendiente de aprobar: "
                    + McasMensajes.importeEnPagos(importe, p.getNumeroPagos()) + ".";
            case "FINALIZADO_APROBADO", "FINALIZADO_EDITADO" -> "Comisión aprobada: "
                    + McasMensajes.importeEnPagos(importe, p.getNumeroPagos()) + "."
                    + (p.getIdServicioCreado() != null ? " Se ha creado el servicio de comisión." : "");
            case "REQUIERE_DATOS" -> datos == 0
                    ? (primera.isEmpty() ? "Faltan datos para calcular la comisión." : primera)
                    : McasMensajes.faltanDatos(codigos)
                      + (datos == 1 ? " Complételo y guarde el contrato." : " Complételos y guarde el contrato.");
            // El motivo libre de un «No procede» es del validador: no se enseña aquí.
            case "REQUIERE_REVISION_MANUAL" -> propuestas.vieneDeDescarte(p)
                    ? "Comisión pendiente de revisión manual: estaba cerrada sin comisión (no procede) y el contrato ha cambiado."
                    : "Comisión pendiente de revisión manual." + conAviso;
            case "ERROR_CALCULO" -> "No se ha podido calcular la comisión; el validador la revisará." + conAviso;
            case "FINALIZADO_NO_COMISIONABLE" -> propuestas.esDescarte(p)
                    ? "Cerrada sin comisión (no procede)."
                    : "Este contrato no genera comisión." + conAviso;
            case "RECHAZADO", "EN_EDICION" -> "El validador está corrigiendo la comisión.";
            case "DESACTUALIZADO" -> "El contrato ha cambiado desde el cálculo; el validador recalculará la comisión.";
            default -> primera;
        };
    }

    /** Advertencias en texto para el usuario (las antiguas guardaban la causa técnica). */
    private List<String> advertencias(McasPropuesta p) {
        if (p.getAdvertenciasJson() == null || p.getAdvertenciasJson().isBlank()) return List.of();
        try {
            List<String> lista = mapper.readValue(p.getAdvertenciasJson(), new TypeReference<List<String>>() {});
            return lista.stream().filter(Objects::nonNull).map(McasMensajes::paraOperador).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Códigos de lo que falta: la traza los guarda desde esta versión; en las
     * propuestas antiguas se leen de la causa técnica guardada en las advertencias.
     */
    private List<String> camposFaltantes(McasPropuesta p) {
        if (p.getTrazaCalculoJson() != null) {
            try {
                JsonNode campos = mapper.readTree(p.getTrazaCalculoJson()).path("campos_faltantes");
                if (campos.isArray() && !campos.isEmpty()) {
                    List<String> codigos = new ArrayList<>();
                    campos.forEach(c -> codigos.add(c.asText()));
                    return codigos;
                }
            } catch (Exception ignorada) {
                // Traza ilegible: se intenta con las advertencias.
            }
        }
        List<String> codigos = new ArrayList<>();
        try {
            List<String> guardadas = mapper.readValue(p.getAdvertenciasJson() != null ? p.getAdvertenciasJson() : "[]",
                    new TypeReference<List<String>>() {});
            guardadas.forEach(a -> codigos.addAll(McasMensajes.camposDe(a)));
        } catch (Exception ignorada) {
            // Sin advertencias legibles no se sabe qué falta.
        }
        return codigos;
    }
}
