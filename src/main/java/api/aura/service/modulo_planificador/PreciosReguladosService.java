package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.entity.modulo_planificador.pl_Boe;
import api.aura.entity.modulo_planificador.pl_PrecioRegulado;
import api.aura.repository.modulo_planificador.pl_BoeRepository;
import api.aura.repository.modulo_planificador.pl_PrecioReguladoRepository;
import api.aura.service.modulo_planificador.estudio.Periodos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Los peajes y cargos del BOE que usan los estudios. Cada conjunto es un BOE con su nombre y la fecha desde la que vale (puede haber
 * varios en el mismo año, o uno cada dos): se leen todos y se guarda un BOE entero de una vez (las seis tarifas, energía y potencia, peaje
 * y cargo). Los periodos que la tarifa no tiene se guardan a nulo.
 */
@Service
@RequiredArgsConstructor
public class PreciosReguladosService {

    static final List<String> TERMINOS = List.of("energia", "potencia");
    static final List<String> CONCEPTOS = List.of("peaje", "cargo");
    private static final LocalDate DESDE_MIN = LocalDate.of(2021, 1, 1), DESDE_MAX = LocalDate.of(2100, 12, 31);

    private final pl_BoeRepository boes;
    private final pl_PrecioReguladoRepository precios;

    @Transactional(readOnly = true)
    public Map<String, Object> listar() {
        List<pl_PrecioRegulado> todas = precios.findAll();
        todas.sort(Comparator.comparing(pl_PrecioRegulado::getBoeId)
                .thenComparing(p -> Periodos.TARIFAS.indexOf(p.getTarifa()))
                .thenComparing(p -> TERMINOS.indexOf(p.getTermino()))
                .thenComparing(p -> CONCEPTOS.indexOf(p.getConcepto())));
        List<Map<String, Object>> filas = new ArrayList<>();
        for (pl_PrecioRegulado p : todas) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("boeId", p.getBoeId());
            f.put("tarifa", p.getTarifa());
            f.put("termino", p.getTermino());
            f.put("concepto", p.getConcepto());
            List<Double> periodos = new ArrayList<>();
            for (BigDecimal v : p.periodos()) periodos.add(v == null ? null : v.doubleValue());
            f.put("periodos", periodos);
            f.put("referencia", p.getReferencia());
            filas.add(f);
        }
        List<Map<String, Object>> lista = new ArrayList<>();
        for (pl_Boe b : boes.findAllByOrderByVigenteDesdeDescIdDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("nombre", b.getNombre());
            m.put("vigenteDesde", b.getVigenteDesde().toString());
            lista.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("boes", lista);
        r.put("tarifas", Periodos.TARIFAS);
        r.put("precios", filas);
        return r;
    }

    /** Añade un BOE nuevo con sus precios. Devuelve todo lo de {@link #listar()} y el id del nuevo. */
    @Transactional
    public Map<String, Object> crear(Map<String, Object> cuerpo) {
        pl_Boe b = new pl_Boe();
        ponerDatos(b, cuerpo);
        Map<String, pl_PrecioRegulado> nuevas = comprobarPrecios(cuerpo);
        boes.save(b);
        for (pl_PrecioRegulado p : nuevas.values()) p.setBoeId(b.getId());
        precios.saveAll(nuevas.values());
        Map<String, Object> r = new LinkedHashMap<>(listar());
        r.put("id", b.getId());
        return r;
    }

    /** Cambia un BOE: su nombre, la fecha desde la que vale y sustituye todos sus precios por los que llegan. */
    @Transactional
    public Map<String, Object> guardar(int id, Map<String, Object> cuerpo) {
        pl_Boe b = boes.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Ese BOE no existe."));
        ponerDatos(b, cuerpo);
        Map<String, pl_PrecioRegulado> nuevas = comprobarPrecios(cuerpo);
        boes.save(b);
        for (pl_PrecioRegulado p : nuevas.values()) p.setBoeId(id);
        precios.deleteAll(precios.findByBoeId(id));
        precios.flush();
        precios.saveAll(nuevas.values());
        return listar();
    }

    @Transactional
    public Map<String, Object> eliminar(int id) {
        precios.deleteAll(precios.findByBoeId(id));
        boes.findById(id).ifPresent(boes::delete);
        return listar();
    }

    private static void ponerDatos(pl_Boe b, Map<String, Object> cuerpo) {
        String nombre = cuerpo == null ? "" : texto(cuerpo.get("nombre")).strip();
        if (nombre.isEmpty()) throw invalido("Pon el nombre del BOE.");
        if (nombre.length() > 255) throw invalido("El nombre del BOE admite como máximo 255 caracteres.");
        LocalDate desde;
        try {
            desde = LocalDate.parse(texto(cuerpo.get("vigenteDesde")).strip());
        } catch (DateTimeParseException e) {
            throw invalido("Pon la fecha desde la que vale el BOE.");
        }
        if (desde.isBefore(DESDE_MIN) || desde.isAfter(DESDE_MAX)) throw invalido("La fecha desde la que vale el BOE tiene que estar entre 2021 y 2100.");
        b.setNombre(nombre);
        b.setVigenteDesde(desde);
    }

    /** Los precios que llegan, comprobados: tienen que estar todas las tarifas, términos y conceptos, con todos sus periodos. */
    @SuppressWarnings("unchecked")
    private static Map<String, pl_PrecioRegulado> comprobarPrecios(Map<String, Object> cuerpo) {
        Object lista = cuerpo == null ? null : cuerpo.get("precios");
        if (!(lista instanceof List<?> entrada)) throw invalido("Faltan los precios del BOE.");
        Map<String, pl_PrecioRegulado> nuevas = new LinkedHashMap<>();
        for (Object o : entrada) {
            if (!(o instanceof Map<?, ?> m)) throw invalido("Los precios no tienen el formato esperado.");
            pl_PrecioRegulado p = fila((Map<String, Object>) m);
            String clave = p.getTarifa() + "|" + p.getTermino() + "|" + p.getConcepto();
            if (nuevas.put(clave, p) != null) throw invalido("Hay precios repetidos de la " + p.getTarifa() + ".");
        }
        for (String t : Periodos.TARIFAS)
            for (String termino : TERMINOS)
                for (String concepto : CONCEPTOS)
                    if (!nuevas.containsKey(t + "|" + termino + "|" + concepto))
                        throw invalido("Falta el " + concepto + " de " + termino + " de la " + t + ".");
        return nuevas;
    }

    private static pl_PrecioRegulado fila(Map<String, Object> m) {
        String tarifa = Periodos.normalizar(texto(m.get("tarifa")));
        if (tarifa == null) throw invalido("Tarifa desconocida: " + texto(m.get("tarifa")) + ".");
        String termino = texto(m.get("termino"));
        String concepto = texto(m.get("concepto"));
        if (!TERMINOS.contains(termino)) throw invalido("Término desconocido: " + termino + ".");
        if (!CONCEPTOS.contains(concepto)) throw invalido("Concepto desconocido: " + concepto + ".");
        int n = "energia".equals(termino) ? Periodos.periodosEnergia(tarifa) : Periodos.periodosPotencia(tarifa);
        List<?> periodos = m.get("periodos") instanceof List<?> l ? l : List.of();
        BigDecimal[] v = new BigDecimal[6];
        for (int i = 0; i < n; i++) {
            Object x = i < periodos.size() ? periodos.get(i) : null;
            BigDecimal d = numero(x);
            if (d == null) throw invalido("Falta el P" + (i + 1) + " del " + concepto + " de " + termino + " de la " + tarifa + ".");
            if (d.signum() < 0 || d.compareTo(new BigDecimal("1000000")) >= 0)
                throw invalido("El P" + (i + 1) + " del " + concepto + " de " + termino + " de la " + tarifa + " no es válido.");
            v[i] = d.setScale(6, java.math.RoundingMode.HALF_UP);
        }
        pl_PrecioRegulado p = new pl_PrecioRegulado();
        p.setTarifa(tarifa);
        p.setTermino(termino);
        p.setConcepto(concepto);
        p.setP1(v[0]);
        p.setP2(v[1]);
        p.setP3(v[2]);
        p.setP4(v[3]);
        p.setP5(v[4]);
        p.setP6(v[5]);
        String ref = texto(m.get("referencia")).strip();
        p.setReferencia(ref.length() > 255 ? ref.substring(0, 255) : ref);
        return p;
    }

    private static BigDecimal numero(Object x) {
        if (x instanceof Number n) return Double.isFinite(n.doubleValue()) ? new BigDecimal(n.toString()) : null;
        if (x instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.strip().replace(',', '.'));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static ErrorApi invalido(String mensaje) {
        return new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
}
