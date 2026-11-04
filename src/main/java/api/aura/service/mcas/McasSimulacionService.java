package api.aura.service.mcas;

import api.aura.dto.mcas.McasSimulacionDTO;
import api.aura.entity.Contrato;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.engine.ContextoCalculo;
import api.aura.service.mcas.engine.MotorMcas;
import api.aura.service.mcas.engine.ResultadoCalculo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * «Probar con un contrato»: prueba una tabla de comisiones (esté o no activa) con
 * los datos de un contrato real. Solo lee: no crea propuestas, servicios ni
 * auditoría. Todo lo que devuelve para leer va en texto para el usuario.
 */
@Service
@RequiredArgsConstructor
public class McasSimulacionService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final McasContratoLocator localizador;
    private final McasConjuntoReglasRepository conjuntoRepo;
    private final McasReglaComisionRepository reglaRepo;
    private final MotorMcas motor;
    private final McasProposalService propuestas;
    private final ModuloConfiguracionService flags;

    @Transactional(readOnly = true)
    public McasSimulacionDTO simular(Integer idConjunto, String referenciaContrato) {
        McasConjuntoReglas conjunto = conjuntoRepo.findById(idConjunto)
                .orElseThrow(() -> new IllegalArgumentException("No existe la tabla de comisiones " + idConjunto + "."));
        Contrato contrato = localizador.buscarUno(referenciaContrato);
        List<String> avisos = avisosDeAplicacion(conjunto, contrato);

        McasSimulacionDTO.McasSimulacionDTOBuilder dto = McasSimulacionDTO.builder()
                .idContrato(contrato.getId()).numeroContrato(contrato.getContrato())
                .idConjunto(conjunto.getId()).codigoConjunto(conjunto.getCodigo())
                .nombreConjunto(McasRuleSetValidator.nombreConjunto(conjunto))
                .estadoConjunto(conjunto.getEstado()).avisos(avisos)
                .lineas(List.of()).calendario(List.of());

        List<String> faltantes = propuestas.camposFaltantes(contrato);
        if (!faltantes.isEmpty())
            return dto.resultado(ResultadoCalculo.Resultado.REQUIERE_DATOS.name())
                    .mensajes(List.of(McasMensajes.paraOperador("Faltan o son invalidos: " + String.join(", ", faltantes))))
                    .datosContrato(Map.of()).build();

        ContextoCalculo ctx = propuestas.construirContexto(contrato, "simulacion-" + UUID.randomUUID());
        ResultadoCalculo res = motor.evaluarConjunto(ctx, conjunto);
        McasReglaComision regla = res.getIdRegla() != null ? reglaRepo.findById(res.getIdRegla()).orElse(null) : null;

        return dto.resultado(res.getResultado().name())
                .importe(res.getImporteTotal())
                .numeroPagos(res.getNumeroPagos())
                .reglaAplicada(regla != null ? regla.getCodigo() : null)
                .nombreRegla(regla != null ? McasRuleSetValidator.nombreRegla(regla) : null)
                .metodoCalculo(res.getMetodoCalculo())
                // El motor ya los da en texto para el usuario; paraOperador es idempotente
                .mensajes(res.getAdvertencias() == null ? List.of()
                        : res.getAdvertencias().stream().map(McasMensajes::paraOperador).toList())
                .lineas(res.getLineas() == null ? List.of() : res.getLineas().stream()
                        .map(l -> new McasSimulacionDTO.Linea(l.getDescripcion(), l.getMetodo(), l.getImporte()))
                        .toList())
                .calendario(calendario(res, contrato, ctx))
                .datosContrato(datosContrato(ctx))
                .build();
    }

    /** Motivos por los que el cálculo automático real no usaría esta tabla para el contrato. */
    private List<String> avisosDeAplicacion(McasConjuntoReglas conjunto, Contrato c) {
        List<String> avisos = new ArrayList<>();
        String estado = switch (String.valueOf(conjunto.getEstado())) {
            case "PUBLICADO" -> null;
            case "BORRADOR" -> "en borrador";
            case "VALIDADO" -> "validada, pero sin activar";
            case "ARCHIVADO" -> "archivada";
            default -> "sin activar";
        };
        if (estado != null)
            avisos.add("La tabla está " + estado + ": el cálculo automático solo usa las tablas activas.");
        if (!Objects.equals(conjunto.getIdComercializadora(), c.getIdComercializadoraActual()))
            avisos.add("La comercializadora del contrato no es la de esta tabla.");
        if (conjunto.getIdGestora() != null && !Objects.equals(conjunto.getIdGestora(), c.getMcasIdGestora()))
            avisos.add("Esta tabla es solo para una gestora y el contrato tiene otra gestora (o ninguna).");
        String tipoPrecio = c.getMcasTipoPrecio() != null ? c.getMcasTipoPrecio().trim().toUpperCase() : null;
        if (tipoPrecio != null && !"AMBOS".equals(conjunto.getFasePrecio())
                && !tipoPrecio.equals(conjunto.getFasePrecio()))
            avisos.add("El contrato es de " + tipoPrecio(tipoPrecio) + " y la tabla es para contratos de "
                    + tipoPrecio(conjunto.getFasePrecio()) + ".");
        if (c.getFechaInicio() != null && conjunto.getVigenciaDesde() != null) {
            LocalDate inicio = api.aura.util.FechaNegocio.aLocalDate(c.getFechaInicio());
            boolean fuera = inicio.isBefore(conjunto.getVigenciaDesde())
                    || (conjunto.getVigenciaHasta() != null && inicio.isAfter(conjunto.getVigenciaHasta()));
            if (fuera)
                avisos.add("La fecha de inicio del contrato (" + inicio.format(FECHA)
                        + ") está fuera de las fechas de la tabla.");
        }
        if (!flags.estaActivo(McasProposalService.FLAG_ENABLED))
            avisos.add("El interruptor «Módulo» está apagado: al guardar contratos no se calculan comisiones.");
        else if ("FIJO".equals(tipoPrecio) && !flags.estaActivo(McasProposalService.FLAG_FIJO))
            avisos.add("El interruptor «Precio fijo» está apagado: los contratos a precio fijo quedan en revisión manual.");
        else if ("INDEXADO".equals(tipoPrecio) && !flags.estaActivo(McasProposalService.FLAG_INDEXADO))
            avisos.add("El interruptor «Indexado» está apagado: los contratos indexados quedan en revisión manual.");
        return avisos;
    }

    private static String tipoPrecio(String fase) {
        return switch (String.valueOf(fase)) {
            case "FIJO" -> "precio fijo";
            case "INDEXADO" -> "precio indexado";
            case "AMBOS" -> "precio fijo o indexado";
            default -> "precio " + String.valueOf(fase).toLowerCase(java.util.Locale.ROOT);
        };
    }

    private List<McasSimulacionDTO.Cuota> calendario(ResultadoCalculo res, Contrato c, ContextoCalculo ctx) {
        if (res.getResultado() != ResultadoCalculo.Resultado.CALCULADO || res.getImporteTotal() == null)
            return List.of();
        LocalDate primera = ctx.getFechaEfectiva();
        List<BigDecimal> cuotas = motor.buildPagos(res.getImporteTotal(),
                res.getNumeroPagos() != null ? res.getNumeroPagos() : 1);
        List<McasSimulacionDTO.Cuota> lista = new ArrayList<>();
        for (int i = 0; i < cuotas.size(); i++)
            lista.add(new McasSimulacionDTO.Cuota(i + 1, primera != null ? primera.plusMonths(i) : null, cuotas.get(i)));
        return lista;
    }

    private Map<String, Object> datosContrato(ContextoCalculo ctx) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipoPrecio", ctx.getTipoPrecio());
        datos.put("tipoSuministro", ctx.getTipoSuministro());
        datos.put("segmento", ctx.getSegmento());
        datos.put("esRenovacion", ctx.getEsRenovacion());
        datos.put("tipoRenovacion", ctx.getTipoRenovacion());
        datos.put("altaNueva", ctx.getAltaNueva());
        datos.put("ambito", ctx.getAmbito());
        datos.put("zonaComision", ctx.getZonaComision());
        datos.put("codigoProducto", ctx.getCodigoProductoComercial());
        datos.put("tarifaAcceso", ctx.getCodigoTarifaAcceso());
        datos.put("consumoKwh", ctx.getConsumoAnualKwh());
        datos.put("consumoMwh", ctx.getConsumoAnualMwh());
        datos.put("feeEurMwh", ctx.getFeeEurMwh());
        datos.put("potenciaKw", ctx.getPotenciaCalculoKw());
        datos.put("numeroPagos", ctx.getNumeroPagos());
        datos.put("fechaInicio", ctx.getFechaEfectiva());
        return datos;
    }
}
