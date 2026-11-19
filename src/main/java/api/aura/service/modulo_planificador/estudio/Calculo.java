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

    /** Potencias que se comparan, como múltiplo de la elegida: menos y más paneles. */
    public static final double[] MULTIPLOS = {0.6, 0.8, 1.0, 1.2, 1.4};
    /** Posición de la potencia elegida (1.0) entre los escenarios. */
    public static final int INDICE_BASE = 2;
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
    /** True si la instalación es aislada y el resto de la energía lo pone un grupo electrógeno (no hay red). */
    public final boolean aislada;
    /** Litros de gasoil que gasta el grupo por kWh. */
    public final double litrosPorKwh;
    /** Importe y potencia del presupuesto del que sale el coste de la instalación (0 si no hay: se usa el precio provisional). */
    public final double presupuestoTotal, presupuestoKwp;
    /** Peajes y cargos de la tarifa del cliente (null si no se conocen). */
    public final PreciosRegulados regulados;
    /** Precio total (€/kWh) de cada periodo con el que se valora la energía solar, o null si se usa un único precio medio. */
    public final double[] precioPeriodo;
    /** True si el ahorro en potencia sale de los kW que se reducen por los precios de potencia del BOE. */
    public final boolean potenciaCalculada;
    public final List<String> avisos = new ArrayList<>();
    private final int[] periodos;
    /** Tarifa de acceso normalizada (3.0TD, 6.1TD…) o null si no se reconoce. */
    private final String tarifaNorm;
    private final double precioMedio;

    /** True si la producción solar sale de PVGIS (ubicación, inclinación y azimut reales) y no del modelo sencillo. */
    public final boolean conPvgis;
    /** Orientación elegida: sur, este-oeste o personalizada (con su inclinación y azimut). */
    public final String orientacionElegida;
    public final double inclinacion, azimut;

    private Calculo(Curva curva, Map<String, Object> datos, PreciosRegulados regulados, PerfilSolar solar) {
        this.curva = curva;
        this.datos = datos;
        Map<String, Object> ins = mapa(datos.get("instalacion")), eco = mapa(datos.get("economia"));
        double kwp = num(ins, "kwp", 0);
        if (kwp <= 0) throw new ErrorApi(HttpStatus.BAD_REQUEST, "Indica la potencia de paneles (kWp) para calcular el estudio.");
        double bateria = Math.max(0, num(ins, "bateriaKwh", 0));
        String orientacion = ins.get("orientacion") == null ? "este-oeste" : String.valueOf(ins.get("orientacion"));
        Double produccion = ins.get("produccionEspecifica") == null ? null : num(ins, "produccionEspecifica", 0);
        this.orientacionElegida = orientacion;
        this.inclinacion = "sur".equals(orientacion) ? 15 : "este-oeste".equals(orientacion) ? 10 : num(ins, "inclinacion", 15);
        this.azimut = "personalizada".equals(orientacion) ? num(ins, "azimut", 0) : 0;
        // Sin PVGIS, la orientación libre se aproxima con el modelo más parecido: de cara al sur, o repartida entre este y oeste
        String modelo = "personalizada".equals(orientacion) ? (Math.abs(azimut) < 45 ? "sur" : "este-oeste") : orientacion;
        this.conPvgis = solar != null && solar.kwhPorKwp() != null;
        if (solar != null && solar.aviso() != null) avisos.add(solar.aviso());
        this.instalacion = conPvgis ? Simulacion.Instalacion.conPerfil(kwp, bateria, modelo, produccion, solar.kwhPorKwp())
                : Simulacion.Instalacion.de(kwp, bateria, modelo, produccion);
        // Aislada con grupo electrógeno: lo que ahorra cada kWh solar es el combustible y el mantenimiento del grupo; no hay red, tarifa ni potencia
        this.aislada = "aislada".equals(eco.get("modo"));
        this.litrosPorKwh = Math.max(0, num(eco, "consumoGrupo", 0.30));
        this.precioMedio = aislada ? num(eco, "precioGasoil", 1.20) * litrosPorKwh + Math.max(0, num(eco, "mantenimientoGrupo", 0.03))
                : num(eco, "precioEnergia", 0.10);
        // Con un presupuesto de referencia, el coste de la instalación es el suyo (proporcional a los kWp); si no, el provisional
        this.presupuestoTotal = Math.max(0, num(eco, "presupuestoTotal", 0));
        this.presupuestoKwp = Math.max(0, num(eco, "presupuestoKwp", 0));
        this.precioWp = conPresupuesto() ? presupuestoTotal / (presupuestoKwp * 1000) : num(eco, "precioWp", 0.85);
        if (precioMedio <= 0) throw new ErrorApi(HttpStatus.BAD_REQUEST, "El precio de la energía debe ser mayor que cero.");
        this.regulados = aislada ? null : regulados;
        if (this.regulados != null && this.regulados.aviso() != null) avisos.add(this.regulados.aviso());
        String tarifa = regulados != null ? regulados.tarifa() : Periodos.normalizar(datos.get("tarifa") == null ? null : String.valueOf(datos.get("tarifa")));
        this.tarifaNorm = tarifa;
        this.precioPeriodo = !aislada && "periodos".equals(eco.get("modoPrecio")) ? preciosPorPeriodo(eco, tarifa) : null;
        this.periodos = precioPeriodo == null ? null : Periodos.delAno(tarifa);
        double[] reduccion = seis(eco.get("reduccionPotencia"));
        boolean reduce = false;
        for (double v : reduccion) reduce |= v > 0;
        if (aislada) {
            this.ahorroPotencia = 0;
            this.potenciaCalculada = false;
        } else if (reduce && regulados != null) {
            double a = 0;
            for (int p = 0; p < Periodos.periodosPotencia(tarifa); p++) a += Math.max(0, reduccion[p]) * regulados.potencia()[p];
            this.ahorroPotencia = a;
            this.potenciaCalculada = true;
        } else {
            if (reduce) avisos.add("Sin los precios de potencia del BOE de la tarifa no se puede valorar la reducción de potencia: se usa el ahorro anual en potencia que hayas puesto.");
            this.ahorroPotencia = Math.max(0, num(eco, "ahorroPotencia", 0));
            this.potenciaCalculada = false;
        }
        this.base = punto(instalacion, eco, 1.0);
        this.precioEnergia = base.entrada().precioKwh();
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
        return new Calculo(curva, datos, null, null);
    }

    /** Con los peajes y cargos de la tarifa del cliente, para valorar la energía por periodos y la reducción de potencia. */
    public static Calculo de(Map<String, Object> datos, Curva curva, PreciosRegulados regulados) {
        return new Calculo(curva, datos, regulados, null);
    }

    /** Además, con la producción solar real de PVGIS (o su aviso si no se pudo calcular). */
    public static Calculo de(Map<String, Object> datos, Curva curva, PreciosRegulados regulados, PerfilSolar solar) {
        return new Calculo(curva, datos, regulados, solar);
    }

    /** La orientación para el informe: «Sur, 15° de inclinación», «Este-Oeste, 10°…» o el azimut y la inclinación elegidos. */
    public String orientacionTexto() {
        return switch (orientacionElegida) {
            case "sur" -> "Sur, 15° de inclinación";
            case "personalizada" -> "Azimut " + Math.round(azimut) + "° (" + rumbo(azimut) + "), " + Math.round(inclinacion) + "° de inclinación";
            default -> "Este-Oeste, 10° de inclinación";
        };
    }

    /** Rumbo en palabras de un azimut (0 sur, -90 este, 90 oeste, 180 norte). */
    static String rumbo(double azimut) {
        double a = ((azimut + 180) % 360 + 360) % 360 - 180;
        String[] n = {"sur", "suroeste", "oeste", "noroeste", "norte", "noreste", "este", "sureste"};
        return n[(int) Math.round(((a + 360) % 360) / 45.0) % 8];
    }

    /**
     * Precio total de cada periodo: el de la energía que cobra la comercializadora más los peajes y cargos del BOE, o tal cual si el
     * cliente ya los tiene incluidos. Null (y un aviso) si falta algo: entonces se usa el precio medio.
     */
    private double[] preciosPorPeriodo(Map<String, Object> eco, String tarifa) {
        if (tarifa == null) {
            avisos.add("Para usar los precios por periodo hace falta la tarifa de acceso (2.0TD, 3.0TD, 6.1TD…): se usa el precio medio.");
            return null;
        }
        boolean incluidos = Boolean.TRUE.equals(eco.get("incluyePeajes"));
        if (!incluidos && regulados == null) {
            avisos.add("No hay peajes y cargos del BOE cargados para la " + tarifa + ": se usa el precio medio.");
            return null;
        }
        double[] energia = seis(eco.get("preciosPeriodo"));
        double[] total = new double[6];
        for (int p = 0; p < Periodos.periodosEnergia(tarifa); p++) {
            if (energia[p] <= 0) {
                avisos.add("Falta el precio de la energía de P" + (p + 1) + ": se usa el precio medio.");
                return null;
            }
            total[p] = energia[p] + (incluidos ? 0 : regulados.energia()[p]);
        }
        return total;
    }

    /** Precio medio de la energía solar consumida: la media de los precios por periodo pesada con la solar de cada periodo. */
    private double precioDe(Simulacion.Resultado s) {
        if (precioPeriodo == null || s.solarPeriodo() == null || s.solarConsumida() <= 0) return precioMedio;
        double valor = 0;
        for (int p = 0; p < 6; p++) valor += s.solarPeriodo()[p] * precioPeriodo[p];
        return valor / s.solarConsumida();
    }

    /** Seis valores (P1 a P6) de una lista; lo que falta o no es un número vale 0. */
    static double[] seis(Object o) {
        double[] v = new double[6];
        if (o instanceof List<?> l) {
            for (int i = 0; i < Math.min(6, l.size()); i++) {
                Object x = l.get(i);
                try {
                    v[i] = x == null || String.valueOf(x).isBlank() ? 0
                            : x instanceof Number n ? n.doubleValue() : api.aura.service.modulo_planificador.Numeros.aNumero(x);
                } catch (NumberFormatException e) {
                    v[i] = 0;
                }
            }
        }
        return v;
    }

    /** True si el coste de la instalación sale de un presupuesto (importe y kWp conocidos) y no del precio provisional. */
    public boolean conPresupuesto() {
        return presupuestoTotal > 0 && presupuestoKwp > 0;
    }

    private Punto punto(Simulacion.Instalacion ins, Map<String, Object> eco, double multiplo) {
        Simulacion.Resultado s = Simulacion.simular(curva, ins, periodos);
        double inversionBase = conPresupuesto() ? presupuestoTotal * ins.kwp() / presupuestoKwp
                : ins.kwp() * 1000 * num(eco, "precioWp", 0.85) + ins.bateriaKwh() * num(eco, "precioBateriaKwh", 250);
        double inversion = !conPresupuesto() && eco.get("inversionTotal") != null && num(eco, "inversionTotal", 0) > 0 ? num(eco, "inversionTotal", 0) * multiplo : inversionBase;
        double mant = eco.get("mantenimiento") != null && num(eco, "mantenimiento", 0) > 0 ? num(eco, "mantenimiento", 0) * multiplo
                : inversion * num(eco, "mantenimientoPct", 1.0) / 100.0;
        Finanzas.Entrada e = Finanzas.Entrada.conValoresPorDefecto(inversion, s.directa(), s.desdeBateria(), precioDe(s), mant, ahorroPotencia);
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
        m.put("precios", preciosAMapa());
        Map<String, Object> sol = new LinkedHashMap<>();
        sol.put("fuente", conPvgis ? "PVGIS" : "modelo");
        sol.put("orientacion", orientacionElegida);
        sol.put("inclinacion", inclinacion);
        sol.put("azimut", azimut);
        sol.put("produccionEspecifica", instalacion.produccionEspecifica());
        m.put("solar", sol);
        if (aislada) {
            Simulacion.Resultado b = base.sim();
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("litrosPorKwh", litrosPorKwh);
            g.put("costeKwh", precioMedio);
            g.put("energiaGrupoSinSolar", curva.total());
            g.put("energiaGrupoConSolar", Math.max(0, curva.total() - b.solarConsumida()));
            g.put("litrosSinSolar", curva.total() * litrosPorKwh);
            g.put("litrosConSolar", Math.max(0, curva.total() - b.solarConsumida()) * litrosPorKwh);
            g.put("litrosAhorrados", b.solarConsumida() * litrosPorKwh);
            g.put("co2Ahorrado", b.solarConsumida() * litrosPorKwh * 2.68);
            m.put("grupo", g);
        }
        m.put("graficas", graficas());
        return m;
    }

    private Map<String, Object> graficasCache;

    /** Las series de las gráficas (se calculan una vez). */
    public Map<String, Object> graficas() {
        if (graficasCache == null) graficasCache = graficasAMapa();
        return graficasCache;
    }

    /** Las series con las que la pantalla dibuja las gráficas (mes a mes en kWh; hora a hora en kW). */
    private Map<String, Object> graficasAMapa() {
        Simulacion.Resultado s = base.sim();
        Map<String, Object> eco = mapa(datos.get("economia"));
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("consumoMes", redondeada(consumoMes, 0));
        g.put("solarMes", redondeada(s.solarMes(), 0));
        g.put("bateriaMes", redondeada(s.bateriaMes(), 0));
        g.put("recortadaMes", redondeada(s.recortadaMes(), 0));
        g.put("produccionMes", redondeada(s.produccionMes(), 0));
        g.put("perfilLaborable", redondeada(perfilLaborable, 1));
        g.put("perfilSabado", redondeada(curva.perfilDeDia(java.time.DayOfWeek.SATURDAY), 1));
        g.put("perfilDomingo", redondeada(curva.perfilDeDia(java.time.DayOfWeek.SUNDAY), 1));
        g.put("perfilFinde", redondeada(perfilFinde, 1));
        List<List<Double>> dia = new ArrayList<>();
        for (double[] serie : s.diaTipo()) dia.add(redondeada(serie, 1));
        g.put("diaTipo", dia);
        g.put("consumoAnual", Math.round(curva.total()));
        g.put("tarifa", tarifaNorm);
        if (tarifaNorm != null) {
            int n = Periodos.periodosEnergia(tarifaNorm);
            int[] per = Periodos.delAno(tarifaNorm);
            List<List<Double>> porPeriodo = new ArrayList<>();
            for (double[] mes : curva.porMesYPeriodo(per, n)) porPeriodo.add(redondeada(mes, 0));
            g.put("consumoPeriodoMes", porPeriodo);
            g.put("potencia", potenciaAMapa(per, eco));
        }
        g.put("barridoPotencia", barridoPotenciaAMapa());
        g.put("barridoBateria", barridoBateriaAMapa());
        g.put("escenariosVan", escenariosVanAMapa());
        g.put("reposicionAno", num(eco, "reposicion", 0) > 0 ? (Object) (int) num(eco, "reposicionAno", 13) : null);
        g.put("financiacion", financiacionAMapa(eco));
        return g;
    }

    /** Cobertura, aprovechamiento y reparto de la energía con cada potencia de paneles. */
    private List<Map<String, Object>> barridoPotenciaAMapa() {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Punto p : barrido) {
            Simulacion.Resultado s = p.sim();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kwp", p.kwp());
            m.put("directa", Math.round(s.directa()));
            m.put("bateria", Math.round(s.desdeBateria()));
            m.put("recortada", Math.round(s.recortada()));
            m.put("cobertura", s.cobertura());
            m.put("aprovechamiento", s.aprovechamiento());
            m.put("ciclos", s.ciclos());
            m.put("actual", p == base || Math.abs(p.kwp() - base.kwp()) < 1e-6);
            r.add(m);
        }
        return r;
    }

    /** Qué pasaría con otras capacidades de batería (desde sin batería hasta el doble): reparto de la energía, ciclos y rentabilidad. */
    private List<Map<String, Object>> barridoBateriaAMapa() {
        double actual = instalacion.bateriaKwh();
        double ref = actual > 0 ? actual : Math.max(10, Math.round(instalacion.kwp() * 0.5 / 10.0) * 10.0);
        java.util.TreeSet<Long> capacidades = new java.util.TreeSet<>();
        for (double m : new double[]{0, 0.25, 0.5, 0.75, 1, 1.5, 2}) capacidades.add(Math.round(ref * m));
        capacidades.add(Math.round(actual));
        Map<String, Object> eco = mapa(datos.get("economia"));
        List<Map<String, Object>> r = new ArrayList<>();
        for (long cap : capacidades) {
            Punto p = cap == Math.round(actual) ? base : punto(instalacion.conBateria(cap), eco, 1.0);
            Simulacion.Resultado s = p.sim();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kwh", cap);
            m.put("directa", Math.round(s.directa()));
            m.put("bateria", Math.round(s.desdeBateria()));
            m.put("recortada", Math.round(s.recortada()));
            m.put("cobertura", s.cobertura());
            m.put("ciclos", s.ciclos());
            m.put("inversion", p.entrada().inversion());
            m.put("ahorroPrimerAno", p.fin().ahorroPrimerAno());
            m.put("retorno", Double.isNaN(p.fin().retorno()) ? null : p.fin().retorno());
            m.put("tir", Double.isNaN(p.fin().tir()) ? null : p.fin().tir());
            m.put("van", p.fin().van());
            m.put("actual", cap == Math.round(actual));
            r.add(m);
        }
        return r;
    }

    /** VAN a 25 años de cada potencia con el precio de la energía elegido y con uno un 25 % más alto. */
    private Map<String, Object> escenariosVanAMapa() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("precio", precioEnergia);
        r.put("precioAlto", precioEnergia * 1.25);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Punto p : escenarios) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kwp", p.kwp());
            m.put("van", p.fin().van());
            m.put("vanAlto", Finanzas.calcular(p.entrada().conPrecio(p.entrada().precioKwh() * 1.25)).van());
            m.put("actual", p == base);
            items.add(m);
        }
        r.put("items", items);
        return r;
    }

    /** Potencia contratada por periodo: la de hoy, la punta registrada, lo que se reduce y su precio del BOE. */
    private Map<String, Object> potenciaAMapa(int[] energia, Map<String, Object> eco) {
        int n = Periodos.periodosPotencia(tarifaNorm);
        int[] per = energia.clone();
        if ("2.0TD".equals(tarifaNorm)) for (int i = 0; i < per.length; i++) per[i] = energia[i] == 2 ? 1 : 0;
        double[] punta = curva.puntaPorPeriodo(per, n);
        double[] contratada = seis(datos.get("potencias"));
        double[] reduce = seis(eco.get("reduccionPotencia"));
        List<Double> hoy = new ArrayList<>(), aContratar = new ArrayList<>(), precio = new ArrayList<>(), ahorro = new ArrayList<>(), contr = new ArrayList<>();
        boolean hay = false;
        for (int p = 0; p < n; p++) {
            double h = contratada[p] > 0 ? contratada[p] : punta[p];
            double a = Math.max(0, h - Math.max(0, reduce[p]));
            double eur = regulados != null ? regulados.potencia()[p] : 0;
            hay |= h > 0;
            contr.add(contratada[p]);
            hoy.add(Math.round(h * 10) / 10.0);
            aContratar.add(Math.round(a * 10) / 10.0);
            precio.add(eur);
            ahorro.add(Math.max(0, reduce[p]) * eur);
        }
        if (!hay) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("periodos", n);
        m.put("contratada", contr);
        m.put("punta", redondeada(punta, 1));
        m.put("hoy", hoy);
        m.put("aContratar", aContratar);
        m.put("precio", precio);
        m.put("ahorro", ahorro);
        m.put("conPrecios", regulados != null);
        return m;
    }

    /**
     * Balance mensual del cliente si financia la inversión en cuotas: el ahorro de cada mes menos la cuota, con el precio de la energía
     * constante y con la subida anual prevista. Null si no se financia.
     */
    private Map<String, Object> financiacionAMapa(Map<String, Object> eco) {
        int meses = (int) num(eco, "financiacionMeses", 0);
        if (meses <= 0) return null;
        double tae = num(eco, "financiacionTae", 9.8) / 100.0;
        double inversion = base.entrada().inversion();
        double r = Math.pow(1 + tae, 1.0 / 12) - 1;
        double cuota = r <= 0 ? inversion / meses : inversion * r / (1 - Math.pow(1 + r, -meses));
        Finanzas.Entrada e = base.entrada();
        Finanzas.Entrada plano = new Finanzas.Entrada(e.inversion(), e.kwhDirecta(), e.kwhBateria(), e.precioKwh(), e.mantenimiento(), e.ahorroPotencia(), 0,
                e.degradacion(), e.degradacionBateria(), e.subidaMantenimiento(), e.descuento(), e.anos(), e.anoReposicion(), e.reposicion());
        // Se calcula todo el horizonte y se enseña hasta un poco después de que se recupere (o 10 años como mínimo)
        int total = e.anos() * 12;
        double[] constanteTotal = balanceMensual(Finanzas.calcular(plano).neto(), cuota, meses, total);
        double[] conSubidaTotal = balanceMensual(base.fin().neto(), cuota, meses, total);
        Integer recC = mesRecuperado(constanteTotal), recS = mesRecuperado(conSubidaTotal);
        int visible = Math.max(120, meses + 48);
        if (recC != null) visible = Math.max(visible, recC + 24);
        if (recS != null) visible = Math.max(visible, recS + 24);
        int horizonte = Math.min(total, visible);
        double[] constante = java.util.Arrays.copyOf(constanteTotal, horizonte + 1);
        double[] conSubida = java.util.Arrays.copyOf(conSubidaTotal, horizonte + 1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("meses", meses);
        m.put("tae", tae * 100);
        m.put("subida", e.subidaEnergia());
        m.put("cuota", cuota);
        m.put("totalCuotas", cuota * meses);
        m.put("puestoConstante", -minimo(constanteTotal));
        m.put("puestoSubida", -minimo(conSubidaTotal));
        m.put("recuperaConstante", recC);
        m.put("recuperaSubida", recS);
        m.put("balanceConstante", redondeada(constante, 0));
        m.put("balanceSubida", redondeada(conSubida, 0));
        return m;
    }

    private static double[] balanceMensual(double[] netoAnual, double cuota, int meses, int horizonte) {
        double[] b = new double[horizonte + 1];
        for (int m = 1; m <= horizonte; m++) b[m] = b[m - 1] + netoAnual[Math.min(netoAnual.length - 1, (m - 1) / 12)] / 12.0 - (m <= meses ? cuota : 0);
        return b;
    }

    private static double minimo(double[] v) {
        double m = 0;
        for (double x : v) m = Math.min(m, x);
        return m;
    }

    /** Primer mes, después del punto más bajo, en que el balance vuelve a cero; null si no llega. */
    private static Integer mesRecuperado(double[] v) {
        int bajo = 0;
        for (int i = 0; i < v.length; i++) if (v[i] < v[bajo]) bajo = i;
        if (v[bajo] >= 0) return 0;
        for (int i = bajo; i < v.length; i++) if (v[i] >= 0) return i;
        return null;
    }

    private static List<Double> redondeada(double[] v, int decimales) {
        double f = Math.pow(10, decimales);
        List<Double> r = new ArrayList<>(v.length);
        for (double x : v) r.add(Math.round(x * f) / f);
        return r;
    }

    private Map<String, Object> preciosAMapa() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("modo", precioPeriodo == null ? "medio" : "periodos");
        p.put("precioMedio", precioEnergia);
        p.put("regulados", regulados == null ? null : regulados.aMapa());
        if (precioPeriodo != null) {
            int n = regulados != null ? Periodos.periodosEnergia(regulados.tarifa()) : precioPeriodo[3] == 0 ? 3 : 6;
            p.put("preciosPeriodo", PreciosRegulados.lista(precioPeriodo, n));
            p.put("solarPeriodo", PreciosRegulados.lista(base.sim().solarPeriodo(), n));
        }
        p.put("ahorroPotencia", ahorroPotencia);
        p.put("potenciaCalculada", potenciaCalculada);
        p.put("avisos", avisos);
        return p;
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
