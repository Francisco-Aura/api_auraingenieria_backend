package api.aura.service.mcas.engine;

import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.service.mcas.McasMensajes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Motor de reglas MCAS.
 * Diseño unificado: misma lógica para todas las comercializadoras.
 * Los valores específicos están en mcas_conjuntos_reglas y mcas_reglas_comision.
 *
 * Orden de evaluación:
 * 1. Feature flag / fase (verificado externamente)
 * 2. Conjunto PUBLICADO vigente para la fecha efectiva
 * 3. NOT_COMMISSIONABLE (prioridad máxima)
 * 4. MANUAL
 * 5. Renovación específica
 * 6. Producto específico
 * 7. Regla genérica
 * 8. Coincidencia única obligatoria
 */
@Component
@RequiredArgsConstructor
public class MotorMcas {

    private static final Logger log = LoggerFactory.getLogger(MotorMcas.class);

    private final McasConjuntoReglasRepository conjuntoRepo;
    private final McasReglaComisionRepository reglaRepo;
    private final McasValorRangoRepository rangoRepo;
    private final RegistroEstrategias estrategias;
    private final ObjectMapper mapper;

    public ResultadoCalculo evaluar(ContextoCalculo ctx) {
        return conManejoDeErrores(ctx, () -> {
            Optional<McasConjuntoReglas> conjunto = seleccionarConjunto(ctx);
            if (conjunto.isEmpty()) return resultadoManual(ctx, causaSinConjunto(ctx));
            return evaluarReglas(ctx, conjunto.get());
        });
    }

    /** Causa técnica cuando no hay conjunto publicado (la misma en el cálculo y en la vista previa). */
    public static String causaSinConjunto(ContextoCalculo ctx) {
        return "Sin conjunto de reglas PUBLICADO para comercializadora="
                + ctx.getIdComercializadora() + " fecha=" + ctx.getFechaEfectiva();
    }

    /**
     * Conjunto PUBLICADO que el cálculo usaría para el contrato: vigente en la
     * fecha efectiva, de su fase de precio y, si lo hay, el de su gestora antes que
     * el genérico. Vacío si no hay ninguno. Lo usan {@link #evaluar} y la vista
     * previa del contrato.
     *
     * @throws McasCalculoException si varios conjuntos aplican con la misma especificidad
     */
    public Optional<McasConjuntoReglas> seleccionarConjunto(ContextoCalculo ctx) {
        String faseFiltro = "INDEXADO".equalsIgnoreCase(ctx.getTipoPrecio()) ? "INDEXADO" : "FIJO";
        List<McasConjuntoReglas> candidatos = conjuntoRepo.findPublicadoFijoPorFecha(
                ctx.getIdComercializadora(),
                ctx.getIdGestora(),
                ctx.getFechaEfectiva() != null ? ctx.getFechaEfectiva() : LocalDate.now(),
                faseFiltro);
        if (candidatos.isEmpty()) return Optional.empty();

        // Preferir conjunto con gestora específica sobre genérico
        List<McasConjuntoReglas> especificos = candidatos.stream()
                .filter(c -> c.getIdGestora() != null && c.getIdGestora().equals(ctx.getIdGestora()))
                .collect(Collectors.toList());
        List<McasConjuntoReglas> aplicables = especificos.isEmpty()
                ? candidatos.stream().filter(c -> c.getIdGestora() == null).collect(Collectors.toList())
                : especificos;
        if (aplicables.size() > 1) {
            String codigos = aplicables.stream().map(McasConjuntoReglas::getCodigo)
                    .collect(Collectors.joining(", "));
            throw new McasCalculoException(aplicables.size()
                    + " conjuntos PUBLICADOS coinciden con igual especificidad [" + codigos + "]");
        }
        return aplicables.stream().findFirst();
    }

    /** Reglas activas de un conjunto, en orden de prioridad (las que evalúa el motor). */
    public List<McasReglaComision> reglasActivas(McasConjuntoReglas conjunto) {
        return reglaRepo.findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(conjunto.getId());
    }

    /**
     * Evalúa las reglas activas de un conjunto concreto, esté o no publicado.
     * Lo usa la simulación de Configuración MCAS para probar un conjunto en
     * borrador con un contrato real sin generar ninguna propuesta.
     */
    public ResultadoCalculo evaluarConjunto(ContextoCalculo ctx, McasConjuntoReglas conjunto) {
        return conManejoDeErrores(ctx, () -> evaluarReglas(ctx, conjunto));
    }

    private ResultadoCalculo evaluarReglas(ContextoCalculo ctx, McasConjuntoReglas conjunto) {
        List<McasReglaComision> reglas = reglasActivas(conjunto);

        if (reglas.isEmpty()) {
            return resultadoManual(ctx, "Conjunto '" + conjunto.getCodigo() + "' sin reglas activas", null, conjunto);
        }

        List<EvaluacionCondicion> evaluaciones = reglas.stream()
                .map(r -> evaluarCondicion(r, ctx)).collect(Collectors.toList());
        List<McasReglaComision> coincidentes = evaluaciones.stream()
                .filter(e -> e.coincide() && e.faltantes().isEmpty())
                .map(EvaluacionCondicion::regla).collect(Collectors.toList());

        if (coincidentes.isEmpty()) {
            List<String> faltantes = evaluaciones.stream().filter(EvaluacionCondicion::coincide)
                    .flatMap(e -> e.faltantes().stream()).distinct().sorted().collect(Collectors.toList());
            if (!faltantes.isEmpty())
                return resultadoDatos(ctx, "Faltan datos requeridos por las reglas: " + String.join(", ", faltantes),
                        null, conjunto, faltantes);
            return resultadoManual(ctx, "Ninguna regla coincide en conjunto '" + conjunto.getCodigo() + "'",
                    null, conjunto);
        }
        if (coincidentes.size() > 1) {
            String codigos = coincidentes.stream().map(McasReglaComision::getCodigo)
                    .collect(Collectors.joining(", "));
            return resultadoError(ctx, "Solapamiento: " + coincidentes.size()
                    + " reglas coinciden [" + codigos + "] — revisar configuración", null, conjunto);
        }

        McasReglaComision regla = coincidentes.get(0);
        return conRegla(ctx, regla, conjunto, () -> ejecutarRegla(ctx, regla, conjunto));
    }

    /**
     * Ejecuta la regla elegida. Si la estrategia la manda a revisión manual (regla
     * MANUAL, FEE bajo el mínimo, valor fuera de la tabla), le faltan datos o está
     * mal configurada, la traza dice qué regla y qué conjunto lo decidieron.
     */
    private ResultadoCalculo conRegla(ContextoCalculo ctx, McasReglaComision regla, McasConjuntoReglas conjunto,
                                      Supplier<ResultadoCalculo> calculo) {
        try {
            return calculo.get();
        } catch (McasDatosRequeridosException e) {
            return resultadoDatos(ctx, e.getMessage(), regla, conjunto, e.getCampos());
        } catch (McasRevisionManualException e) {
            return resultadoManual(ctx, e.getMessage(), regla, conjunto);
        } catch (McasNoComisionableException e) {
            return resultadoNoComisionable(ctx, e.getMessage(), regla, conjunto);
        } catch (McasCalculoException e) {
            return resultadoError(ctx, e.getMessage(), regla, conjunto);
        }
    }

    private ResultadoCalculo conManejoDeErrores(ContextoCalculo ctx, Supplier<ResultadoCalculo> calculo) {
        try {
            return calculo.get();
        } catch (McasDatosRequeridosException e) {
            return resultadoDatos(ctx, e.getMessage(), null, null, e.getCampos());
        } catch (McasRevisionManualException e) {
            return resultadoManual(ctx, e.getMessage());
        } catch (McasNoComisionableException e) {
            return resultadoNoComisionable(ctx, e.getMessage(), null, null);
        } catch (McasCalculoException e) {
            return resultadoError(ctx, e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado motor MCAS contrato={}", ctx.getIdContrato(), e);
            return resultadoError(ctx, "Error interno: " + e.getMessage());
        }
    }

    // -----------------------------------------------
    // Evaluación de condiciones
    // -----------------------------------------------

    private EvaluacionCondicion evaluarCondicion(McasReglaComision regla, ContextoCalculo ctx) {
        if (regla.getCondicionesJson() == null || regla.getCondicionesJson().isBlank())
            return new EvaluacionCondicion(regla, true, List.of());
        try {
            JsonNode c = mapper.readTree(regla.getCondicionesJson());
            List<String> faltantes = new ArrayList<>();
            if (!evaluarTexto(c, "tipo_suministro", ctx.getTipoSuministro(), faltantes)
                    || !evaluarTexto(c, "segmento", ctx.getSegmento(), faltantes)
                    || !evaluarTexto(c, "tipo_renovacion", ctx.getTipoRenovacion(), faltantes)
                    || !evaluarTexto(c, "ambito", ctx.getAmbito(), faltantes)
                    || !evaluarTexto(c, "zona_comision", ctx.getZonaComision(), faltantes)
                    || !evaluarTexto(c, "tarifa_acceso", ctx.getCodigoTarifaAcceso(), faltantes)
                    || !evaluarTexto(c, "codigo_producto", ctx.getCodigoProductoComercial(), faltantes)
                    || !evaluarBooleano(c, "es_renovacion", ctx.getEsRenovacion(), faltantes)
                    || !evaluarBooleano(c, "alta_nueva", ctx.getAltaNueva(), faltantes)
                    || !evaluarRango(c, "consumo_min_kwh", "consumo_max_kwh",
                    "consumo_min_inclusivo", "consumo_max_inclusivo", "consumo_kwh", ctx.getConsumoAnualKwh(), faltantes)
                    || !evaluarRango(c, "consumo_min_mwh", "consumo_max_mwh",
                    "consumo_min_mwh_inc", "consumo_max_mwh_inc", "consumo_mwh", ctx.getConsumoAnualMwh(), faltantes)
                    || !evaluarRango(c, "potencia_min_kw", "potencia_max_kw",
                    "potencia_min_inc", "potencia_max_inc", "potencia_kw", ctx.getPotenciaCalculoKw(), faltantes))
                return new EvaluacionCondicion(regla, false, List.of());
            return new EvaluacionCondicion(regla, true, List.copyOf(faltantes));
        } catch (Exception e) {
            log.warn("Error evaluando condiciones regla {}: {}", regla.getId(), e.getMessage());
            return new EvaluacionCondicion(regla, false, List.of());
        }
    }

    private boolean evaluarTexto(JsonNode c, String clave, String valor, List<String> faltantes) {
        if (!c.has(clave) || c.get(clave).isNull()) return true;
        if (valor == null) { faltantes.add(clave); return true; }
        // La tarifa se compara normalizada: «2.0 TD» del catálogo = «2.0TD» de la regla.
        if ("tarifa_acceso".equals(clave)) return McasTexto.mismaTarifa(c.get(clave).asText(), valor);
        return c.get(clave).asText().equalsIgnoreCase(valor);
    }

    private boolean evaluarBooleano(JsonNode c, String clave, Boolean valor, List<String> faltantes) {
        if (!c.has(clave) || c.get(clave).isNull()) return true;
        if (valor == null) { faltantes.add(clave); return true; }
        return c.get(clave).asBoolean() == valor;
    }

    private boolean evaluarRango(JsonNode c, String minKey, String maxKey, String minIncKey,
                                 String maxIncKey, String nombre, BigDecimal valor, List<String> faltantes) {
        if (!c.has(minKey) && !c.has(maxKey)) return true;
        if (valor == null) { faltantes.add(nombre); return true; }
        return matchRango(c, minKey, maxKey, minIncKey, maxIncKey, valor);
    }

    private record EvaluacionCondicion(McasReglaComision regla, boolean coincide, List<String> faltantes) {}

    private boolean matchRango(JsonNode c, String minKey, String maxKey,
                                String minIncKey, String maxIncKey, BigDecimal valor) {
        if (!c.has(minKey) && !c.has(maxKey)) return true;
        if (valor == null) return false;
        if (c.has(minKey) && !c.get(minKey).isNull()) {
            BigDecimal min = c.get(minKey).decimalValue();
            boolean inc = !c.has(minIncKey) || c.get(minIncKey).asBoolean(true);
            int cmp = valor.compareTo(min);
            if (inc ? cmp < 0 : cmp <= 0) return false;
        }
        if (c.has(maxKey) && !c.get(maxKey).isNull()) {
            BigDecimal max = c.get(maxKey).decimalValue();
            boolean inc = c.has(maxIncKey) && c.get(maxIncKey).asBoolean(false);
            int cmp = valor.compareTo(max);
            if (inc ? cmp > 0 : cmp >= 0) return false;
        }
        return true;
    }

    // -----------------------------------------------
    // Ejecución de regla
    // -----------------------------------------------

    private ResultadoCalculo ejecutarRegla(ContextoCalculo ctx, McasReglaComision regla,
                                            McasConjuntoReglas conjunto) {
        List<ResultadoCalculo.LineaResultado> lineas = estrategias.obtener(regla.getMetodoCalculo())
                .calcular(ctx, regla, rangoRepo);

        BigDecimal totalRaw = lineas.stream().map(ResultadoCalculo.LineaResultado::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = totalRaw.setScale(2, RoundingMode.HALF_UP);

        // Avisos técnicos en la traza; al usuario, el mismo aviso en texto humano.
        List<String> avisos = new ArrayList<>();
        int nPagos = resolverNumeroPagos(ctx, regla, avisos);
        List<String> advertencias = avisos.stream().map(McasMensajes::paraOperador).toList();

        // % aplicado al FEE (0.44): el del cliente, distinto del % de retrocesión.
        String porcentajeFee = lineas.stream()
                .filter(l -> "FEE_PERCENTAGE".equals(l.getMetodo()) && l.getValorPorcentaje() != null)
                .map(l -> l.getValorPorcentaje().toPlainString()).findFirst().orElse(null);

        String traza;
        try {
            ObjectNode node = mapper.createObjectNode();
            anotarReglaYContexto(node, ctx, regla, conjunto);
            if (porcentajeFee != null) node.put("porcentaje_aplicacion_fee", porcentajeFee);
            node.put("total_sin_redondeo", totalRaw.toPlainString());
            node.put("total_redondeado", total.toPlainString());
            if (!avisos.isEmpty()) avisos.forEach(node.putArray("avisos")::add);
            traza = mapper.writeValueAsString(node);
        } catch (Exception ex) { traza = "{}"; }

        return ResultadoCalculo.builder()
                .resultado(ResultadoCalculo.Resultado.CALCULADO)
                .idRegla(regla.getId())
                .idConjuntoReglas(conjunto.getId())
                .metodoCalculo(regla.getMetodoCalculo())
                .lineas(lineas)
                .importeTotal(total)
                .numeroPagos(nPagos)
                .trazaJson(traza)
                .advertencias(List.copyOf(advertencias))
                .build();
    }

    // -----------------------------------------------
    // Reparto de pagos — HALF_UP, último ajusta la diferencia
    // -----------------------------------------------

    /**
     * Reparte un importe en n cuotas con el mismo criterio que el resto de la
     * aplicacion ({@code ServiciosPagosService.generarCuotasSegunModalidad}): la
     * cuota base son los centimos truncados y la ultima absorbe la diferencia, de
     * modo que nunca queda por debajo de las anteriores y la suma cuadra exacta.
     */
    public List<BigDecimal> buildPagos(BigDecimal total, int n) {
        if (n <= 1) return List.of(total.setScale(2, RoundingMode.HALF_UP));
        long centimos = total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        long base = centimos / n;
        List<BigDecimal> pagos = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) pagos.add(BigDecimal.valueOf(base, 2));
        pagos.add(BigDecimal.valueOf(centimos - base * (n - 1L), 2));
        return pagos;
    }

    /**
     * Numero de pagos de la comision. Si la politica de la regla no se puede leer se
     * mantiene el reparto en un pago —igual que antes—, pero se deja aviso: el error
     * de configuracion tiene que verse en la propuesta, no quedarse en silencio.
     */
    private int resolverNumeroPagos(ContextoCalculo ctx, McasReglaComision regla, List<String> advertencias) {
        if (ctx.getNumeroPagos() != null && ctx.getNumeroPagos() > 0) return ctx.getNumeroPagos();
        if (regla.getPoliticaPagoJson() != null && !regla.getPoliticaPagoJson().isBlank()) {
            try {
                JsonNode j = mapper.readTree(regla.getPoliticaPagoJson());
                if (j.has("numero_pagos_sugerido")) return j.get("numero_pagos_sugerido").asInt(1);
            } catch (Exception e) {
                String aviso = "La politica de pago de la regla " + regla.getCodigo()
                        + " no se puede leer; se reparte en un solo pago";
                log.warn("MCAS contrato={} {}: {}", ctx.getIdContrato(), aviso, e.getMessage());
                advertencias.add(aviso);
            }
        }
        return 1;
    }

    private ResultadoCalculo resultadoManual(ContextoCalculo ctx, String causa) {
        return resultadoManual(ctx, causa, null, null);
    }

    private ResultadoCalculo resultadoManual(ContextoCalculo ctx, String causa,
                                             McasReglaComision regla, McasConjuntoReglas conjunto) {
        log.info("MCAS contrato={} REQUIERE_REVISION_MANUAL: {}", ctx.getIdContrato(), causa);
        return sinImporte(ResultadoCalculo.Resultado.REQUIERE_REVISION_MANUAL, "revision_manual",
                ctx, causa, regla, conjunto, List.of());
    }

    private ResultadoCalculo resultadoDatos(ContextoCalculo ctx, String causa, McasReglaComision regla,
                                            McasConjuntoReglas conjunto, List<String> campos) {
        log.info("MCAS contrato={} REQUIERE_DATOS: {}", ctx.getIdContrato(), causa);
        return sinImporte(ResultadoCalculo.Resultado.REQUIERE_DATOS, "requiere_datos", ctx, causa, regla, conjunto,
                campos);
    }

    private ResultadoCalculo resultadoError(ContextoCalculo ctx, String causa) {
        return resultadoError(ctx, causa, null, null);
    }

    private ResultadoCalculo resultadoError(ContextoCalculo ctx, String causa,
                                            McasReglaComision regla, McasConjuntoReglas conjunto) {
        log.warn("MCAS contrato={} ERROR_CALCULO: {}", ctx.getIdContrato(), causa);
        return sinImporte(ResultadoCalculo.Resultado.ERROR_CALCULO, "error", ctx, causa, regla, conjunto, List.of());
    }

    private ResultadoCalculo resultadoNoComisionable(ContextoCalculo ctx, String causa,
                                                     McasReglaComision regla, McasConjuntoReglas conjunto) {
        return sinImporte(ResultadoCalculo.Resultado.NO_COMISIONABLE, "no_comisionable",
                ctx, causa, regla, conjunto, List.of());
    }

    /**
     * Resultado sin importe calculado. La traza y {@code causaError} conservan la
     * causa técnica con su clave de siempre y, si ya se sabía, el conjunto y la
     * regla que lo decidieron, más los datos del contrato con los que se evaluó y
     * los que faltan. La advertencia va en texto para el usuario: qué datos faltan
     * o la causa traducida por {@link McasMensajes#paraOperador}.
     */
    private ResultadoCalculo sinImporte(ResultadoCalculo.Resultado resultado, String claveCausa, ContextoCalculo ctx,
                                        String causa, McasReglaComision regla, McasConjuntoReglas conjunto,
                                        List<String> camposFaltantes) {
        List<String> campos = camposFaltantes == null ? List.of() : List.copyOf(camposFaltantes);
        String traza;
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put(claveCausa, causa);
            if (!campos.isEmpty()) campos.forEach(node.putArray("campos_faltantes")::add);
            anotarReglaYContexto(node, ctx, regla, conjunto);
            traza = mapper.writeValueAsString(node);
        } catch (Exception ex) {
            traza = "{\"" + claveCausa + "\":\"" + escapar(causa) + "\"}";
        }
        String advertencia = campos.isEmpty() ? McasMensajes.paraOperador(causa) : McasMensajes.faltanDatos(campos);
        return ResultadoCalculo.builder()
                .resultado(resultado)
                .idRegla(regla != null ? regla.getId() : null)
                .idConjuntoReglas(conjunto != null ? conjunto.getId() : null)
                .metodoCalculo(regla != null ? regla.getMetodoCalculo() : null)
                // NO_COMISIONABLE se cierra con importe cero, como hasta ahora.
                .importeTotal(resultado == ResultadoCalculo.Resultado.NO_COMISIONABLE ? BigDecimal.ZERO : null)
                .causaError(causa).lineas(List.of()).advertencias(List.of(advertencia))
                .camposFaltantes(campos)
                .trazaJson(traza)
                .build();
    }

    /** Claves comunes de la traza: conjunto y regla (si los hay) y los datos del contrato evaluados. */
    private void anotarReglaYContexto(ObjectNode node, ContextoCalculo ctx,
                                      McasReglaComision regla, McasConjuntoReglas conjunto) {
        if (conjunto != null) {
            node.put("conjunto", conjunto.getCodigo());
            node.put("conjunto_version", conjunto.getVersion());
        }
        if (regla != null) {
            node.put("regla", regla.getCodigo());
            if (regla.getNombre() != null && !regla.getNombre().isBlank())
                node.put("regla_nombre", regla.getNombre());
            node.put("regla_id", regla.getId());
            node.put("metodo", regla.getMetodoCalculo());
        }
        node.put("consumo_mwh", texto(ctx.getConsumoAnualMwh()));
        node.put("fee_eur_mwh", texto(ctx.getFeeEurMwh()));
        node.put("tarifa_acceso", ctx.getCodigoTarifaAcceso());
        node.put("codigo_producto", ctx.getCodigoProductoComercial());
        node.put("potencia_calculo_kw", texto(ctx.getPotenciaCalculoKw()));
        node.put("potencia_origen", ctx.getPotenciaOrigen());
    }

    private String texto(BigDecimal valor) {
        return valor != null ? valor.toPlainString() : null;
    }

    private String escapar(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
