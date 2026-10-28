package api.aura.service.mcas;

import api.aura.dto.mcas.McasSimulacionDTO;
import api.aura.entity.Contrato;
import api.aura.entity.mcas.McasConjuntoReglas;
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
 * Prueba un conjunto de reglas (esté o no publicado) con los datos de un
 * contrato real. Solo lee: no crea propuestas, servicios ni auditoría.
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
                .orElseThrow(() -> new IllegalArgumentException("Conjunto de reglas no encontrado: " + idConjunto));
        Contrato contrato = localizador.buscarUno(referenciaContrato);
        List<String> avisos = avisosDeAplicacion(conjunto, contrato);

        McasSimulacionDTO.McasSimulacionDTOBuilder dto = McasSimulacionDTO.builder()
                .idContrato(contrato.getId()).numeroContrato(contrato.getContrato())
                .idConjunto(conjunto.getId()).codigoConjunto(conjunto.getCodigo())
                .estadoConjunto(conjunto.getEstado()).avisos(avisos)
                .lineas(List.of()).calendario(List.of());

        List<String> faltantes = propuestas.camposFaltantes(contrato);
        if (!faltantes.isEmpty())
            return dto.resultado(ResultadoCalculo.Resultado.REQUIERE_DATOS.name())
                    .mensajes(List.of(McasMensajes.paraOperador("Faltan o son invalidos: " + String.join(", ", faltantes))))
                    .datosContrato(Map.of()).build();

        ContextoCalculo ctx = propuestas.construirContexto(contrato, "simulacion-" + UUID.randomUUID());
        ResultadoCalculo res = motor.evaluarConjunto(ctx, conjunto);
        String regla = res.getIdRegla() != null
                ? reglaRepo.findById(res.getIdRegla()).map(r -> r.getCodigo()).orElse(null) : null;

        return dto.resultado(res.getResultado().name())
                .importe(res.getImporteTotal())
                .numeroPagos(res.getNumeroPagos())
                .reglaAplicada(regla)
                .metodoCalculo(res.getMetodoCalculo())
                .mensajes(res.getAdvertencias() != null ? res.getAdvertencias() : List.of())
                .lineas(res.getLineas() == null ? List.of() : res.getLineas().stream()
                        .map(l -> new McasSimulacionDTO.Linea(l.getDescripcion(), l.getMetodo(), l.getImporte()))
                        .toList())
                .calendario(calendario(res, contrato, ctx))
                .datosContrato(datosContrato(ctx))
                .build();
    }

    /** Motivos por los que el cálculo automático real no usaría este conjunto para el contrato. */
    private List<String> avisosDeAplicacion(McasConjuntoReglas conjunto, Contrato c) {
        List<String> avisos = new ArrayList<>();
        if (!"PUBLICADO".equals(conjunto.getEstado()))
            avisos.add("El conjunto está en estado " + conjunto.getEstado()
                    + ": el cálculo automático solo usa conjuntos publicados.");
        if (!Objects.equals(conjunto.getIdComercializadora(), c.getIdComercializadoraActual()))
            avisos.add("La comercializadora actual del contrato no es la del conjunto.");
        if (conjunto.getIdGestora() != null && !Objects.equals(conjunto.getIdGestora(), c.getMcasIdGestora()))
            avisos.add("El conjunto es solo para una gestora y el contrato tiene otra gestora MCAS (o ninguna).");
        String tipoPrecio = c.getMcasTipoPrecio() != null ? c.getMcasTipoPrecio().trim().toUpperCase() : null;
        if (tipoPrecio != null && !"AMBOS".equals(conjunto.getFasePrecio())
                && !tipoPrecio.equals(conjunto.getFasePrecio()))
            avisos.add("El contrato es de precio " + tipoPrecio + " y el conjunto es de fase " + conjunto.getFasePrecio() + ".");
        if (c.getFechaInicio() != null && conjunto.getVigenciaDesde() != null) {
            LocalDate inicio = api.aura.util.FechaNegocio.aLocalDate(c.getFechaInicio());
            boolean fuera = inicio.isBefore(conjunto.getVigenciaDesde())
                    || (conjunto.getVigenciaHasta() != null && inicio.isAfter(conjunto.getVigenciaHasta()));
            if (fuera)
                avisos.add("La fecha de inicio del contrato (" + inicio.format(FECHA)
                        + ") está fuera de la vigencia del conjunto.");
        }
        if (!flags.estaActivo(McasProposalService.FLAG_ENABLED))
            avisos.add("El indicador «Módulo» está desactivado: al guardar contratos no se generan propuestas.");
        else if ("FIJO".equals(tipoPrecio) && !flags.estaActivo(McasProposalService.FLAG_FIJO))
            avisos.add("El indicador «Precio fijo» está desactivado: los contratos a precio fijo quedan en revisión manual.");
        else if ("INDEXADO".equals(tipoPrecio) && !flags.estaActivo(McasProposalService.FLAG_INDEXADO))
            avisos.add("El indicador «Indexado» está desactivado: los contratos indexados quedan en revisión manual.");
        return avisos;
    }

    private List<McasSimulacionDTO.Cuota> calendario(ResultadoCalculo res, Contrato c, ContextoCalculo ctx) {
        if (res.getResultado() != ResultadoCalculo.Resultado.CALCULADO || res.getImporteTotal() == null)
            return List.of();
        LocalDate primera = c.getMcasFechaPrimeraAplicacion() != null
                ? api.aura.util.FechaNegocio.aLocalDate(c.getMcasFechaPrimeraAplicacion())
                : ctx.getFechaEfectiva();
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
