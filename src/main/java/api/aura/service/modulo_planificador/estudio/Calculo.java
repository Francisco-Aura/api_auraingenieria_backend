package api.aura.service.modulo_planificador.estudio;

import api.aura.config.ErrorApi;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El estudio completo de un cliente: consumo, instalación simulada, rentabilidad, escenarios de potencia y variaciones del precio de la
 * energía. Se calcula siempre a partir de los datos guardados y de la curva, así que el mismo estudio da el mismo resultado.
 */
public final class Calculo {

    public static final double[] MULTIPLOS = {1.0, 1.2, 1.4, 1.6, 1.8};
    public static final double[] MULTIPLOS_BARRIDO = {0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0};
    public static final double[] FACTORES_PRECIO = {1.0, 1.1, 1.25, 1.4};

    /** Un punto del estudio: una potencia de paneles con su simulación y su rentabilidad. */
    public record Punto(Simulacion.Resultado sim, Finanzas.Entrada entrada, Finanzas.Resultado fin) {
        public double kwp() {
            return sim.kwp();
        }
    }

    public final Curva curva;
    public final Map<String, Object> datos;
    public final Simulacion.Instalacion instalacion;
    public final Punto base;
    public final List<Punto> escenarios = new ArrayList<>();
    public final List<Punto> barrido = new ArrayList<>();
    public final List<Punto> precios = new ArrayList<>();
    public final double[] perfilLaborable, perfilFinde, consumoMes;
    public final double potenciaMedia, potenciaMaxima, fraccionDiurna;
    public final double precioEnergia, precioWp, ahorroPotencia;

    private Calculo(Curva curva, Map<String, Object> datos) {
        this.curva = curva;
        this.datos = datos;
        Map<String, Object> ins = mapa(datos.get("instalacion")), eco = mapa(datos.get("economia"));
        double kwp = num(ins, "kwp", 0);
        if (kwp <= 0) throw new ErrorApi(HttpStatus.BAD_REQUEST, "Indica la potencia de paneles (kWp) para calcular el estudio.");
        double bateria = Math.max(0, num(ins, "bateriaKwh", 0));
        Object orientacion = ins.get("orientacion");
        Double produccion = ins.get("produccionEspecifica") == null ? null : num(ins, "produccionEspecifica", 0);
        this.instalacion = Simulacion.Instalacion.de(kwp, bateria, orientacion == null ? "este-oeste" : String.valueOf(orientacion), produccion);
        this.precioEnergia = num(eco, "precioEnergia", 0.10);
        this.precioWp = num(eco, "precioWp", 0.85);
        this.ahorroPotencia = Math.max(0, num(eco, "ahorroPotencia", 0));
        if (precioEnergia <= 0) throw new ErrorApi(HttpStatus.BAD_REQUEST, "El precio de la energía debe ser mayor que cero.");
        this.base = punto(instalacion, eco, 1.0);
        for (double m : MULTIPLOS) escenarios.add(m == 1.0 ? base : punto(instalacion.conKwp(kwp * m), eco, m));
        for (double m : MULTIPLOS_BARRIDO) barrido.add(m == 1.0 ? base : punto(instalacion.conKwp(kwp * m), eco, m));
        for (double f : FACTORES_PRECIO) precios.add(new Punto(base.sim(), base.entrada().conPrecio(precioEnergia * f),
                Finanzas.calcular(base.entrada().conPrecio(precioEnergia * f))));
        this.perfilLaborable = curva.perfilHorario(true);
        this.perfilFinde = curva.perfilHorario(false);
        this.consumoMes = curva.porMes();
        this.potenciaMedia = curva.total() / Curva.HORAS;
        this.potenciaMaxima = curva.maximo();
        this.fraccionDiurna = curva.fraccionDiurna();
    }

    public static Calculo de(Map<String, Object> datos, Curva curva) {
        return new Calculo(curva, datos);
    }

    private Punto punto(Simulacion.Instalacion ins, Map<String, Object> eco, double multiplo) {
        Simulacion.Resultado s = Simulacion.simular(curva, ins);
        double inversionBase = ins.kwp() * 1000 * num(eco, "precioWp", 0.85) + ins.bateriaKwh() * num(eco, "precioBateriaKwh", 250);
        double inversion = eco.get("inversionTotal") != null && num(eco, "inversionTotal", 0) > 0 ? num(eco, "inversionTotal", 0) * multiplo : inversionBase;
        double mant = eco.get("mantenimiento") != null && num(eco, "mantenimiento", 0) > 0 ? num(eco, "mantenimiento", 0) * multiplo
                : inversion * num(eco, "mantenimientoPct", 1.0) / 100.0;
        Finanzas.Entrada e = Finanzas.Entrada.conValoresPorDefecto(inversion, s.directa(), s.desdeBateria(), num(eco, "precioEnergia", 0.10), mant,
                num(eco, "ahorroPotencia", 0));
        double rep = num(eco, "reposicion", 0);
        int ano = (int) num(eco, "reposicionAno", 13);
        if (rep > 0 && ano >= 1 && ano <= 25) {
            e = new Finanzas.Entrada(e.inversion(), e.kwhDirecta(), e.kwhBateria(), e.precioKwh(), e.mantenimiento(), e.ahorroPotencia(),
                    e.subidaEnergia(), e.degradacion(), e.degradacionBateria(), e.subidaMantenimiento(), e.descuento(), e.anos(), ano, rep);
        }
        return new Punto(s, e, Finanzas.calcular(e));
    }

    /** Potencia de paneles sugerida: la que produce la mitad del consumo anual (punto de partida que el consultor ajusta). */
    public static double kwpSugerido(Curva curva, String orientacion) {
        double y = Simulacion.Instalacion.produccionPorDefecto("sur".equals(orientacion) ? "sur" : "este-oeste");
        return Math.max(5, Math.round(0.5 * curva.total() / y / 5.0) * 5.0);
    }

    /** Resumen que se guarda con el estudio y que enseña la pantalla. */
    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("consumoAnual", curva.total());
        c.put("potenciaMedia", potenciaMedia);
        c.put("potenciaMaxima", potenciaMaxima);
        c.put("fraccionDiurna", fraccionDiurna);
        c.put("estimada", curva.estimada());
        m.put("consumo", c);
        Map<String, Object> s = Simulacion.aMapa(base.sim());
        s.put("kwp", base.kwp());
        m.put("simulacion", s);
        m.put("finanzas", Finanzas.aMapa(base.entrada(), base.fin()));
        List<Map<String, Object>> esc = new ArrayList<>();
        for (Punto p : escenarios) {
            Map<String, Object> f = Simulacion.aMapa(p.sim());
            f.put("inversion", p.entrada().inversion());
            f.put("ahorroPrimerAno", p.fin().ahorroPrimerAno());
            f.put("retorno", Double.isNaN(p.fin().retorno()) ? null : p.fin().retorno());
            f.put("tir", Double.isNaN(p.fin().tir()) ? null : p.fin().tir());
            f.put("van", p.fin().van());
            esc.add(f);
        }
        m.put("escenarios", esc);
        return m;
    }

    // ------------------------------------------------------------------ lectura de números

    @SuppressWarnings("unchecked")
    static Map<String, Object> mapa(Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    static double num(Map<String, Object> m, String clave, double defecto) {
        Object v = m.get(clave);
        if (v == null) return defecto;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return api.aura.service.modulo_planificador.Numeros.aNumero(v);
        } catch (NumberFormatException e) {
            return defecto;
        }
    }
}
