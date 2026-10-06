package api.aura.service.mcas;

import api.aura.dto.mcas.McasIncidenciaDTO;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.service.mcas.engine.McasTexto;
import api.aura.service.mcas.engine.RegistroEstrategias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Validación previa obligatoria antes de activar (publicar) una tabla de comisiones.
 *
 * <p>Cada problema es una {@link McasIncidenciaDTO}: la regla (por su nombre legible,
 * o por su código si aún no tiene nombre) y, en las tablas por tramos, el orden de la
 * celda, para que la pantalla lleve al administrador al sitio exacto. {@code errores()}
 * repite las incidencias como texto («regla: mensaje») para las pantallas que solo
 * muestran una lista. Mensajes en español, con tildes y en usted.</p>
 */
@Service
@RequiredArgsConstructor
public class McasRuleSetValidator {

    /** Métodos de cálculo que leen sus importes de mcas_valores_rango. */
    public static final Set<String> METODOS_TABLA = Set.of("RANGE_LOOKUP", "MATRIX_LOOKUP", "MULTI_DIMENSION_LOOKUP");

    private static final Set<String> DIMENSIONES_NUMERICAS = Set.of(
            "consumo_kwh", "consumo_mwh", "potencia_kw");
    private static final Set<String> DIMENSIONES_TEXTO = Set.of(
            "tarifa_acceso", "codigo_producto", "segmento", "tipo_suministro", "zona_comision");
    private static final Map<String, String> ETIQUETA_DIMENSION = Map.of(
            "consumo_kwh", "consumo (kWh)", "consumo_mwh", "consumo (MWh)", "potencia_kw", "potencia (kW)",
            "tarifa_acceso", "tarifa de acceso", "codigo_producto", "producto", "segmento", "segmento",
            "tipo_suministro", "tipo de suministro", "zona_comision", "zona de comisión");

    private static final Set<String> CONDICIONES_TEXTO = Set.of(
            "tipo_suministro", "segmento", "tipo_renovacion", "ambito",
            "zona_comision", "tarifa_acceso", "codigo_producto");
    private static final Set<String> CONDICIONES_BOOLEANAS = Set.of("es_renovacion", "alta_nueva");
    private static final List<RangoJson> RANGOS_CONDICION = List.of(
            new RangoJson("consumo_min_kwh", "consumo_max_kwh", "consumo_min_inclusivo", "consumo_max_inclusivo"),
            new RangoJson("consumo_min_mwh", "consumo_max_mwh", "consumo_min_mwh_inc", "consumo_max_mwh_inc"),
            new RangoJson("potencia_min_kw", "potencia_max_kw", "potencia_min_inc", "potencia_max_inc"));
    private static final Set<String> CONDICIONES_PERMITIDAS;
    private static final Map<String, String> ETIQUETA_CONDICION = Map.ofEntries(
            Map.entry("tipo_suministro", "tipo de suministro"), Map.entry("segmento", "segmento"),
            Map.entry("tipo_renovacion", "tipo de renovación"), Map.entry("ambito", "ámbito"),
            Map.entry("zona_comision", "zona de comisión"), Map.entry("tarifa_acceso", "tarifa de acceso"),
            Map.entry("codigo_producto", "producto"), Map.entry("es_renovacion", "renovación"),
            Map.entry("alta_nueva", "alta nueva"),
            Map.entry("consumo_min_kwh", "consumo mínimo (kWh)"), Map.entry("consumo_max_kwh", "consumo máximo (kWh)"),
            Map.entry("consumo_min_inclusivo", "incluir el consumo mínimo"),
            Map.entry("consumo_max_inclusivo", "incluir el consumo máximo"),
            Map.entry("consumo_min_mwh", "consumo mínimo (MWh)"), Map.entry("consumo_max_mwh", "consumo máximo (MWh)"),
            Map.entry("consumo_min_mwh_inc", "incluir el consumo mínimo (MWh)"),
            Map.entry("consumo_max_mwh_inc", "incluir el consumo máximo (MWh)"),
            Map.entry("potencia_min_kw", "potencia mínima (kW)"), Map.entry("potencia_max_kw", "potencia máxima (kW)"),
            Map.entry("potencia_min_inc", "incluir la potencia mínima"),
            Map.entry("potencia_max_inc", "incluir la potencia máxima"));

    static {
        Set<String> permitidas = new HashSet<>(CONDICIONES_TEXTO);
        permitidas.addAll(CONDICIONES_BOOLEANAS);
        for (RangoJson rango : RANGOS_CONDICION) {
            permitidas.add(rango.min()); permitidas.add(rango.max());
            permitidas.add(rango.minInclusivo()); permitidas.add(rango.maxInclusivo());
        }
        CONDICIONES_PERMITIDAS = Set.copyOf(permitidas);
    }

    private final McasReglaComisionRepository reglaRepo;
    private final McasValorRangoRepository rangoRepo;
    private final McasConjuntoReglasRepository conjuntoRepo;
    private final RegistroEstrategias estrategias;
    private final ObjectMapper mapper;

    /**
     * Resultado de la validación. {@code errores} son las incidencias en texto
     * («Endesa · pyme luz 3.0TD: la celda 12 no tiene importe.»).
     */
    public record Resultado(boolean valido, List<String> errores, List<McasIncidenciaDTO> incidencias) {
        /** Compatibilidad: incidencias sin regla ni celda, una por error. */
        public Resultado(boolean valido, List<String> errores) {
            this(valido, errores, errores.stream()
                    .map(e -> McasIncidenciaDTO.builder().mensaje(e).build()).toList());
        }

        static Resultado de(List<McasIncidenciaDTO> incidencias) {
            return new Resultado(incidencias.isEmpty(), textos(incidencias), List.copyOf(incidencias));
        }
    }

    /**
     * Problemas que impiden guardar o activar: los controladores responden 400
     * con {@code error}, {@code incidencias} y {@code detalles} (los textos).
     */
    public static class IncidenciasException extends IllegalStateException {
        private final transient List<McasIncidenciaDTO> incidencias;

        public IncidenciasException(String mensaje, List<McasIncidenciaDTO> incidencias) {
            super(mensaje);
            this.incidencias = List.copyOf(incidencias);
        }

        public List<McasIncidenciaDTO> getIncidencias() { return incidencias; }

        public List<String> getDetalles() { return textos(incidencias); }
    }

    // ============================================================
    // Conjunto completo
    // ============================================================

    public Resultado validar(McasConjuntoReglas conjunto) {
        Registro registro = new Registro();
        if (conjunto.getIdComercializadora() == null) registro.conjunto("Elija la comercializadora de la tabla.");
        if (conjunto.getVigenciaDesde() == null) registro.conjunto("Indique desde qué fecha se aplica la tabla.");
        if (conjunto.getVigenciaDesde() != null && conjunto.getVigenciaHasta() != null
                && conjunto.getVigenciaHasta().isBefore(conjunto.getVigenciaDesde()))
            registro.conjunto("La fecha de fin de la tabla no puede ser anterior a la de inicio.");
        if (!List.of("FIJO", "INDEXADO", "AMBOS").contains(conjunto.getFasePrecio()))
            registro.conjunto("Elija el tipo de precio de la tabla: fijo, indexado o ambos"
                    + (conjunto.getFasePrecio() == null ? "." : " («" + conjunto.getFasePrecio() + "» no es válido)."));

        List<McasReglaComision> reglas = reglaRepo
                .findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(conjunto.getId());
        if (reglas.isEmpty()) registro.conjunto("La tabla no tiene ninguna regla activa: añada o active al menos una.");

        Map<String, McasReglaComision> reglaPorCondiciones = new HashMap<>();
        List<Map.Entry<McasReglaComision, JsonNode>> condicionesValidas = new ArrayList<>();
        for (McasReglaComision regla : reglas) {
            if (regla.getCodigo() == null || regla.getCodigo().isBlank()) registro.regla(regla, "La regla no tiene código.");
            if (regla.getMetodoCalculo() == null || !estrategias.existe(regla.getMetodoCalculo())) {
                registro.regla(regla, regla.getMetodoCalculo() == null || regla.getMetodoCalculo().isBlank()
                        ? "Elija el método de cálculo de la regla."
                        : "El método de cálculo «" + regla.getMetodoCalculo() + "» no existe; elija otro.");
                continue;
            }
            JsonNode condiciones = jsonObjeto(regla, regla.getCondicionesJson(), "las condiciones", registro);
            JsonNode calculo = jsonObjeto(regla, regla.getCalculoJson(), "los parámetros de cálculo", registro);
            JsonNode politicaPago = jsonObjeto(regla, regla.getPoliticaPagoJson(), "la política de pago", registro);
            if (condiciones != null) {
                validarCondiciones(regla, condiciones, registro);
                McasReglaComision anterior = reglaPorCondiciones.putIfAbsent(condiciones.toString(), regla);
                if (anterior != null)
                    registro.regla(regla, "Tiene las mismas condiciones que «" + nombreRegla(anterior)
                            + "»: las dos se aplicarían al mismo contrato.");
                else condicionesValidas.add(new AbstractMap.SimpleImmutableEntry<>(regla, condiciones));
            }
            validarParametros(regla, calculo, registro);
            validarPoliticaPago(regla, politicaPago, registro);
        }
        for (int i = 0; i < condicionesValidas.size(); i++) {
            for (int j = i + 1; j < condicionesValidas.size(); j++) {
                var a = condicionesValidas.get(i);
                var b = condicionesValidas.get(j);
                if (condicionesSolapan(a.getValue(), b.getValue()))
                    registro.regla(b.getKey(), "Sus condiciones se solapan con las de «" + nombreRegla(a.getKey())
                            + "»: las dos podrían aplicarse al mismo contrato.");
            }
        }
        return Resultado.de(registro.lista);
    }

    public Resultado validarPublicacion(McasConjuntoReglas conjunto) {
        return validarPublicacion(conjunto, Map.of());
    }

    /**
     * Como {@link #validarPublicacion(McasConjuntoReglas)}, pero da por hecho que
     * las tablas activas de {@code cierresPrevistos} terminan en la fecha indicada
     * (lo que hará «Activar» al cerrar la vigencia anterior). No modifica nada.
     */
    public Resultado validarPublicacion(McasConjuntoReglas conjunto, Map<Integer, LocalDate> cierresPrevistos) {
        List<McasIncidenciaDTO> incidencias = new ArrayList<>(validar(conjunto).incidencias());
        if (conjunto.getIdComercializadora() == null || conjunto.getVigenciaDesde() == null)
            return Resultado.de(incidencias);
        Registro registro = new Registro();
        for (McasConjuntoReglas publicado : conjuntoRepo.findByIdComercializadoraAndEstado(
                conjunto.getIdComercializadora(), "PUBLICADO")) {
            if (Objects.equals(publicado.getId(), conjunto.getId())) continue;
            boolean mismaEspecificidad = Objects.equals(publicado.getIdGestora(), conjunto.getIdGestora());
            boolean faseSolapada = Objects.equals(publicado.getFasePrecio(), conjunto.getFasePrecio())
                    || "AMBOS".equals(publicado.getFasePrecio()) || "AMBOS".equals(conjunto.getFasePrecio());
            LocalDate finPublicado = cierresPrevistos.containsKey(publicado.getId())
                    ? cierresPrevistos.get(publicado.getId()) : publicado.getVigenciaHasta();
            if (mismaEspecificidad && faseSolapada && fechasSolapan(publicado.getVigenciaDesde(), finPublicado,
                    conjunto.getVigenciaDesde(), conjunto.getVigenciaHasta()))
                registro.conjunto("Coincide en fechas con la tabla activa «" + nombreConjunto(publicado)
                        + "» (misma comercializadora, gestora y tipo de precio): cierre su vigencia o cambie las fechas.");
        }
        incidencias.addAll(registro.lista);
        return Resultado.de(incidencias);
    }

    // ============================================================
    // Tabla de una regla (tramos o celdas) en memoria
    // ============================================================

    /**
     * Valida los tramos o celdas de una regla sin leerlos de la base de datos
     * (editor en rejilla: se valida lo que se va a guardar). Solo tiene efecto
     * en los métodos de {@link #METODOS_TABLA}.
     */
    public List<McasIncidenciaDTO> validarTabla(McasReglaComision regla, List<McasValorRango> valores) {
        Registro registro = new Registro();
        validarTabla(regla, valores, registro);
        return List.copyOf(registro.lista);
    }

    private void validarTabla(McasReglaComision regla, List<McasValorRango> valores, Registro registro) {
        if ("RANGE_LOOKUP".equals(regla.getMetodoCalculo())) validarRangos(regla, valores, registro);
        else if (METODOS_TABLA.contains(regla.getMetodoCalculo())) validarMatriz(regla, valores, registro);
    }

    private void validarRangos(McasReglaComision regla, List<McasValorRango> valores, Registro registro) {
        if (valores.isEmpty()) {
            registro.regla(regla, "La tabla de la regla no tiene tramos: añada al menos uno.");
            return;
        }
        String dimension = valores.get(0).getDimension();
        if (dimension == null || valores.stream().anyMatch(v -> !Objects.equals(dimension, v.getDimension())))
            registro.regla(regla, "Todos los tramos deben buscar por el mismo dato (consumo, potencia, tarifa…); "
                    + "esta tabla mezcla varios.");
        for (int i = 0; i < valores.size(); i++) {
            validarLimites(regla, valores.get(i), "tramo", registro);
            for (int j = i + 1; j < valores.size(); j++) {
                if (solapan(valores.get(i), valores.get(j)))
                    registro.celda(regla, valores.get(j).getOrden(), "Los tramos " + valores.get(i).getOrden()
                            + " y " + valores.get(j).getOrden() + " se solapan: un mismo contrato encajaría en los dos.");
            }
        }
        if (DIMENSIONES_NUMERICAS.contains(dimension)) {
            List<McasValorRango> ordenados = new ArrayList<>(valores);
            ordenados.sort(Comparator.comparing(McasValorRango::getValorMin,
                    Comparator.nullsFirst(Comparator.naturalOrder())));
            for (int i = 0; i + 1 < ordenados.size(); i++) {
                McasValorRango actual = ordenados.get(i);
                McasValorRango siguiente = ordenados.get(i + 1);
                if (hayHueco(actual, siguiente))
                    registro.celda(regla, siguiente.getOrden(), "Hay un hueco entre los tramos " + actual.getOrden()
                            + " y " + siguiente.getOrden() + ": ningún tramo cubre los valores intermedios.");
            }
        }
    }

    private void validarMatriz(McasReglaComision regla, List<McasValorRango> valores, Registro registro) {
        if (valores.isEmpty()) {
            registro.regla(regla, "La tabla de la regla no tiene celdas: añada al menos una.");
            return;
        }
        Map<Integer, List<McasValorRango>> celdas = new LinkedHashMap<>();
        valores.forEach(v -> celdas.computeIfAbsent(v.getOrden(), k -> new ArrayList<>()).add(v));
        for (var entry : celdas.entrySet()) {
            List<McasValorRango> celda = entry.getValue();
            Integer orden = entry.getKey();
            celda.forEach(v -> validarLimites(regla, v, "celda", registro));
            Set<String> vistas = new HashSet<>();
            for (McasValorRango v : celda)
                if (v.getDimension() != null && !vistas.add(v.getDimension()))
                    registro.celda(regla, orden, elemento("celda", orden) + " repite el dato "
                            + etiquetaDimension(v.getDimension()) + ".");
            if (celda.stream().map(McasValorRango::getDimension).filter(Objects::nonNull).distinct().count() < 2)
                registro.celda(regla, orden, elemento("celda", orden)
                        + " necesita al menos dos datos (por ejemplo, consumo y potencia).");
            if (celda.stream().map(McasValorRango::getValorResultado).filter(Objects::nonNull)
                    .map(BigDecimal::stripTrailingZeros).distinct().count() > 1)
                registro.celda(regla, orden, elemento("celda", orden) + " tiene importes distintos en sus filas.");
        }
        List<Map.Entry<Integer, List<McasValorRango>>> lista = new ArrayList<>(celdas.entrySet());
        for (int i = 0; i < lista.size(); i++) {
            for (int j = i + 1; j < lista.size(); j++) {
                if (celdasSolapan(lista.get(i).getValue(), lista.get(j).getValue()))
                    registro.celda(regla, lista.get(j).getKey(), "Las celdas " + lista.get(i).getKey()
                            + " y " + lista.get(j).getKey() + " se solapan: un mismo contrato encajaría en las dos.");
            }
        }
    }

    private boolean celdasSolapan(List<McasValorRango> a, List<McasValorRango> b) {
        Map<String, McasValorRango> porDimensionA = new HashMap<>();
        Map<String, McasValorRango> porDimensionB = new HashMap<>();
        a.forEach(v -> porDimensionA.put(v.getDimension(), v));
        b.forEach(v -> porDimensionB.put(v.getDimension(), v));
        Set<String> dimensiones = new HashSet<>(porDimensionA.keySet());
        dimensiones.addAll(porDimensionB.keySet());
        for (String dimension : dimensiones) {
            McasValorRango ra = porDimensionA.get(dimension);
            McasValorRango rb = porDimensionB.get(dimension);
            if (ra != null && rb != null && !solapan(ra, rb)) return false;
        }
        return true;
    }

    private void validarLimites(McasReglaComision regla, McasValorRango valor, String tipo, Registro registro) {
        Integer orden = valor.getOrden();
        String quien = elemento(tipo, orden);
        String dimension = valor.getDimension();
        if (dimension == null || dimension.isBlank())
            registro.celda(regla, orden, quien + " no indica por qué dato se busca (consumo, potencia, tarifa…).");
        else if (!DIMENSIONES_NUMERICAS.contains(dimension) && !DIMENSIONES_TEXTO.contains(dimension))
            registro.celda(regla, orden, quien + " busca por un dato que no existe («" + dimension + "»).");
        else if (DIMENSIONES_TEXTO.contains(dimension)) {
            if (valor.getValorCategoria() == null || valor.getValorCategoria().isBlank())
                registro.celda(regla, orden, quien + " no indica el valor de " + etiquetaDimension(dimension) + ".");
            if (valor.getValorMin() != null || valor.getValorMax() != null)
                registro.celda(regla, orden, quien + " no admite mínimo ni máximo en " + etiquetaDimension(dimension)
                        + ": indique solo el valor.");
        } else if (valor.getValorCategoria() != null && !valor.getValorCategoria().isBlank()) {
            registro.celda(regla, orden, quien + " no admite un texto en " + etiquetaDimension(dimension)
                    + ": indique el mínimo y el máximo.");
        }
        if (valor.getValorResultado() == null)
            registro.celda(regla, orden, quien + " no tiene importe.");
        if (orden == null || orden < 1)
            registro.celda(regla, orden, quien + " tiene un número de orden no válido (debe ser 1 o mayor).");
        if (valor.getValorMin() != null && valor.getValorMax() != null
                && valor.getValorMin().compareTo(valor.getValorMax()) > 0)
            registro.celda(regla, orden, "En " + minusculaInicial(quien) + " el mínimo es mayor que el máximo.");
    }

    // ============================================================
    // Condiciones, parámetros y política de pago de cada regla
    // ============================================================

    private boolean fechasSolapan(LocalDate desdeA, LocalDate hastaA, LocalDate desdeB, LocalDate hastaB) {
        LocalDate finA = hastaA != null ? hastaA : LocalDate.MAX;
        LocalDate finB = hastaB != null ? hastaB : LocalDate.MAX;
        return !finA.isBefore(desdeB) && !finB.isBefore(desdeA);
    }

    private void validarCondiciones(McasReglaComision regla, JsonNode condiciones, Registro registro) {
        condiciones.fieldNames().forEachRemaining(campo -> {
            JsonNode valor = condiciones.get(campo);
            if (!CONDICIONES_PERMITIDAS.contains(campo)) {
                registro.regla(regla, "La condición «" + campo + "» no existe; revise cómo está escrita.");
            } else if (CONDICIONES_TEXTO.contains(campo) && !valor.isNull() && !valor.isTextual()) {
                registro.regla(regla, "La condición «" + etiquetaCondicion(campo) + "» debe ser un texto.");
            } else if (CONDICIONES_BOOLEANAS.contains(campo) && !valor.isNull() && !valor.isBoolean()) {
                registro.regla(regla, "La condición «" + etiquetaCondicion(campo) + "» debe ser sí o no.");
            }
        });
        for (RangoJson rango : RANGOS_CONDICION) {
            validarNumeroCondicion(regla, condiciones, rango.min(), registro);
            validarNumeroCondicion(regla, condiciones, rango.max(), registro);
            validarBooleanoCondicion(regla, condiciones, rango.minInclusivo(), registro);
            validarBooleanoCondicion(regla, condiciones, rango.maxInclusivo(), registro);
            if (numero(condiciones, rango.min()) != null && numero(condiciones, rango.max()) != null
                    && numero(condiciones, rango.min()).compareTo(numero(condiciones, rango.max())) > 0)
                registro.regla(regla, "En las condiciones, el " + etiquetaCondicion(rango.min())
                        + " no puede ser mayor que el " + etiquetaCondicion(rango.max()) + ".");
        }
    }

    private void validarNumeroCondicion(McasReglaComision regla, JsonNode condiciones, String campo, Registro registro) {
        if (condiciones.has(campo) && !condiciones.get(campo).isNull() && !condiciones.get(campo).isNumber())
            registro.regla(regla, "La condición «" + etiquetaCondicion(campo) + "» debe ser un número.");
    }

    private void validarBooleanoCondicion(McasReglaComision regla, JsonNode condiciones, String campo, Registro registro) {
        if (condiciones.has(campo) && !condiciones.get(campo).isNull() && !condiciones.get(campo).isBoolean())
            registro.regla(regla, "La condición «" + etiquetaCondicion(campo) + "» debe ser sí o no.");
    }

    private void validarPoliticaPago(McasReglaComision regla, JsonNode politica, Registro registro) {
        if (politica == null || !politica.has("numero_pagos_sugerido")) return;
        JsonNode valor = politica.get("numero_pagos_sugerido");
        if (!valor.isIntegralNumber() || valor.asInt() < 1 || valor.asInt() > 12)
            registro.regla(regla, "El número de pagos sugerido debe ser un número entero entre 1 y 12.");
    }

    private boolean condicionesSolapan(JsonNode a, JsonNode b) {
        for (String campo : CONDICIONES_TEXTO) {
            JsonNode va = a.get(campo), vb = b.get(campo);
            if (va != null && vb != null && va.isTextual() && vb.isTextual()
                    && !mismoTexto(campo, va.asText(), vb.asText())) return false;
        }
        for (String campo : CONDICIONES_BOOLEANAS) {
            JsonNode va = a.get(campo), vb = b.get(campo);
            if (va != null && vb != null && va.isBoolean() && vb.isBoolean()
                    && va.asBoolean() != vb.asBoolean()) return false;
        }
        for (RangoJson rango : RANGOS_CONDICION)
            if (!rangosJsonSolapan(a, b, rango)) return false;
        return true;
    }

    private boolean rangosJsonSolapan(JsonNode a, JsonNode b, RangoJson rango) {
        BigDecimal minA = numero(a, rango.min()), maxA = numero(a, rango.max());
        BigDecimal minB = numero(b, rango.min()), maxB = numero(b, rango.max());
        boolean minIncA = booleano(a, rango.minInclusivo(), true);
        boolean maxIncA = booleano(a, rango.maxInclusivo(), false);
        boolean minIncB = booleano(b, rango.minInclusivo(), true);
        boolean maxIncB = booleano(b, rango.maxInclusivo(), false);
        if (maxA != null && minB != null) {
            int cmp = maxA.compareTo(minB);
            if (cmp < 0 || (cmp == 0 && !(maxIncA && minIncB))) return false;
        }
        if (maxB != null && minA != null) {
            int cmp = maxB.compareTo(minA);
            if (cmp < 0 || (cmp == 0 && !(maxIncB && minIncA))) return false;
        }
        return true;
    }

    private BigDecimal numero(JsonNode node, String campo) {
        JsonNode valor = node.get(campo);
        return valor != null && valor.isNumber() ? valor.decimalValue() : null;
    }

    private boolean booleano(JsonNode node, String campo, boolean defecto) {
        JsonNode valor = node.get(campo);
        return valor == null || !valor.isBoolean() ? defecto : valor.asBoolean();
    }

    private record RangoJson(String min, String max, String minInclusivo, String maxInclusivo) {}

    private void validarParametros(McasReglaComision regla, JsonNode calculo, Registro registro) {
        String metodo = regla.getMetodoCalculo();
        if ("MANUAL".equals(metodo) && (regla.getMotivoManual() == null || regla.getMotivoManual().isBlank()))
            registro.regla(regla, "Indique el motivo por el que esta comisión se completa a mano.");
        if ("NOT_COMMISSIONABLE".equals(metodo)
                && (regla.getMotivoManual() == null || regla.getMotivoManual().isBlank()))
            registro.regla(regla, "Indique el motivo por el que este caso no genera comisión.");
        if (calculo != null) {
            switch (metodo) {
                case "FIXED_AMOUNT" -> requeridoDecimal(regla, calculo, "importe",
                        "Indique el importe fijo de la comisión (un número).", registro);
                case "RATE_PER_MWH" -> requeridoDecimal(regla, calculo, "tasa_eur_mwh",
                        "Indique el importe por MWh (un número).", registro);
                case "FEE_PERCENTAGE" -> {
                    requeridoDecimal(regla, calculo, "porcentaje_aplicacion_fee",
                            "Indique el porcentaje del FEE (un número).", registro);
                    if (calculo.has("porcentaje_aplicacion_fee")
                            && calculo.get("porcentaje_aplicacion_fee").isNumber()
                            && (calculo.get("porcentaje_aplicacion_fee").decimalValue().signum() < 0
                            || calculo.get("porcentaje_aplicacion_fee").decimalValue().compareTo(BigDecimal.ONE) > 0))
                        registro.regla(regla, "El porcentaje del FEE debe estar entre 0 y 1 "
                                + "(por ejemplo, 0,44 para el 44 %).");
                }
                case "COMPOSITE_SUM" -> {
                    if (!calculo.has("lineas") || !calculo.get("lineas").isArray()
                            || calculo.get("lineas").isEmpty())
                        registro.regla(regla, "Añada al menos una línea de cálculo.");
                }
                default -> { }
            }
        }
        if (METODOS_TABLA.contains(metodo))
            validarTabla(regla, rangoRepo.findByIdReglaOrderByOrdenAsc(regla.getId()), registro);
    }

    private JsonNode jsonObjeto(McasReglaComision regla, String valor, String que, Registro registro) {
        if (valor == null || valor.isBlank()) return mapper.createObjectNode();
        try {
            JsonNode node = mapper.readTree(valor);
            if (!node.isObject()) {
                registro.regla(regla, "No se pueden leer " + que + ": se espera un objeto JSON («{…}»).");
                return null;
            }
            return node;
        } catch (Exception e) {
            registro.regla(regla, "No se pueden leer " + que + ": el texto no es JSON válido.");
            return null;
        }
    }

    private void requeridoDecimal(McasReglaComision regla, JsonNode calculo, String campo, String mensaje,
                                  Registro registro) {
        if (!calculo.has(campo) || !calculo.get(campo).isNumber()) registro.regla(regla, mensaje);
    }

    // ============================================================
    // Textos
    // ============================================================

    /** Nombre legible de la regla; su código si no tiene nombre; «Regla 7» si no tiene ninguno. */
    public static String nombreRegla(McasReglaComision regla) {
        if (regla.getNombre() != null && !regla.getNombre().isBlank()) return regla.getNombre().trim();
        if (regla.getCodigo() != null && !regla.getCodigo().isBlank()) return regla.getCodigo().trim();
        return "Regla " + regla.getId();
    }

    /** Nombre legible de la tabla de comisiones; su código si no tiene nombre. */
    public static String nombreConjunto(McasConjuntoReglas conjunto) {
        if (conjunto.getNombre() != null && !conjunto.getNombre().isBlank()) return conjunto.getNombre().trim();
        if (conjunto.getCodigo() != null && !conjunto.getCodigo().isBlank()) return conjunto.getCodigo().trim();
        return "Tabla " + conjunto.getId();
    }

    /** «Endesa · pyme luz 3.0TD: la celda 12 no tiene importe.»; sin regla, solo el mensaje. */
    public static List<String> textos(List<McasIncidenciaDTO> incidencias) {
        return incidencias.stream().map(i -> i.getRegla() == null
                ? i.getMensaje() : i.getRegla() + ": " + minusculaInicial(i.getMensaje())).toList();
    }

    private static String elemento(String tipo, Integer orden) {
        boolean celda = "celda".equals(tipo);
        if (orden == null) return celda ? "Una celda" : "Un tramo";
        return (celda ? "La celda " : "El tramo ") + orden;
    }

    private static String etiquetaDimension(String dimension) {
        return ETIQUETA_DIMENSION.getOrDefault(dimension, dimension);
    }

    private static String etiquetaCondicion(String campo) {
        return ETIQUETA_CONDICION.getOrDefault(campo, campo);
    }

    /** Primera letra en minúscula, salvo siglas («FEE»). */
    private static String minusculaInicial(String texto) {
        if (texto == null || texto.length() < 2 || !Character.isUpperCase(texto.charAt(0))
                || Character.isUpperCase(texto.charAt(1))) return texto;
        return Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
    }

    /** Igualdad de textos con el mismo criterio que el motor: la tarifa de acceso, normalizada. */
    private static boolean mismoTexto(String campo, String a, String b) {
        return "tarifa_acceso".equals(campo)
                ? McasTexto.mismaTarifa(a, b)
                : a.equalsIgnoreCase(b);
    }

    private boolean solapan(McasValorRango a, McasValorRango b) {
        if (!Objects.equals(a.getDimension(), b.getDimension())) return false;
        if (DIMENSIONES_TEXTO.contains(a.getDimension()))
            return a.getValorCategoria() != null && b.getValorCategoria() != null
                    && mismoTexto(a.getDimension(), a.getValorCategoria(), b.getValorCategoria());
        if (a.getValorMax() != null && b.getValorMin() != null) {
            int cmp = a.getValorMax().compareTo(b.getValorMin());
            if (cmp < 0 || (cmp == 0 && !(Boolean.TRUE.equals(a.getMaxInclusivo())
                    && Boolean.TRUE.equals(b.getMinInclusivo())))) return false;
        }
        if (b.getValorMax() != null && a.getValorMin() != null) {
            int cmp = b.getValorMax().compareTo(a.getValorMin());
            if (cmp < 0 || (cmp == 0 && !(Boolean.TRUE.equals(b.getMaxInclusivo())
                    && Boolean.TRUE.equals(a.getMinInclusivo())))) return false;
        }
        return true;
    }

    private boolean hayHueco(McasValorRango anterior, McasValorRango siguiente) {
        if (anterior.getValorMax() == null || siguiente.getValorMin() == null) return false;
        int cmp = anterior.getValorMax().compareTo(siguiente.getValorMin());
        return cmp < 0 || (cmp == 0 && !Boolean.TRUE.equals(anterior.getMaxInclusivo())
                && !Boolean.TRUE.equals(siguiente.getMinInclusivo()));
    }

    /** Incidencias en el orden en que aparecen, sin repetir (una celda tiene una fila por dato). */
    private static final class Registro {
        private final List<McasIncidenciaDTO> lista = new ArrayList<>();

        void conjunto(String mensaje) {
            anadir(new McasIncidenciaDTO(null, null, null, mensaje));
        }

        void regla(McasReglaComision regla, String mensaje) {
            anadir(new McasIncidenciaDTO(regla.getId(), nombreRegla(regla), null, mensaje));
        }

        void celda(McasReglaComision regla, Integer orden, String mensaje) {
            anadir(new McasIncidenciaDTO(regla.getId(), nombreRegla(regla), orden, mensaje));
        }

        private void anadir(McasIncidenciaDTO incidencia) {
            if (!lista.contains(incidencia)) lista.add(incidencia);
        }
    }
}
