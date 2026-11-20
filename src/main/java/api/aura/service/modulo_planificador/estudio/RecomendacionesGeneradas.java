package api.aura.service.modulo_planificador.estudio;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Las recomendaciones de un estudio, escritas a partir de sus propios números: cada una dice qué se propone y por qué, con las cifras
 * de este cliente (cobertura, excedente, VAN, retorno…). Solo salen las que el estudio justifica; no hay textos genéricos.
 */
public final class RecomendacionesGeneradas {

    /** Una recomendación: id estable (para elegirla en el informe), título y texto con **negrita**, listas «- » y párrafos. */
    public record Recomendacion(String id, String titulo, String texto) {
    }

    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
    private static final Locale ES = Locale.forLanguageTag("es-ES");

    private RecomendacionesGeneradas() {
    }

    public static List<Recomendacion> de(Calculo c) {
        return de(c, null);
    }

    /** Con {@code optima} (la mejor inclinación encontrada para la ubicación), la de orientación dice cuánto se pierde respecto a ella. */
    public static List<Recomendacion> de(Calculo c, InclinacionOptima.Resultado optima) {
        List<Recomendacion> l = new ArrayList<>();
        for (Recomendacion r : new Recomendacion[]{potencia(c), orientacion(c, optima), excedenteYBateria(c), estacionalidad(c), rentabilidad(c), potenciaContratada(c), curvaEstimada(c)}) {
            if (r != null) l.add(r);
        }
        return l;
    }

    // ------------------------------------------------------------------ cada recomendación

    private static Recomendacion potencia(Calculo c) {
        Calculo.Punto b = c.base;
        Simulacion.Resultado s = b.sim();
        Calculo.Punto mejor = b;
        for (Calculo.Punto p : c.escenarios) if (p.fin().van() > mejor.fin().van()) mejor = p;
        Calculo.Punto menos = c.escenarios.get(Calculo.INDICE_BASE - 1), mas = c.escenarios.get(Calculo.INDICE_BASE + 1);
        StringBuilder t = new StringBuilder();
        t.append("Con **").append(num(b.kwp(), 0)).append(" kWp** se producen ").append(mwh(s.produccion())).append(" al año; se aprovechan ")
                .append(pct(s.aprovechamiento())).append(" y cubren el **").append(pct(s.cobertura())).append("** del consumo (").append(mwh(s.consumo())).append(").\n\n");
        t.append("Así cambia el resultado con otra potencia:\n");
        for (Calculo.Punto p : new Calculo.Punto[]{menos, b, mas}) {
            t.append("- **").append(num(p.kwp(), 0)).append(" kWp:** cubre el ").append(pct(p.sim().cobertura())).append(", aprovecha el ")
                    .append(pct(p.sim().aprovechamiento())).append(", VAN ").append(eur(p.fin().van())).append(", retorno ").append(retorno(p.fin().retorno())).append(".\n");
        }
        t.append("\n");
        if (mejor == b) {
            t.append("La potencia elegida es la de mayor VAN a 25 años entre las cinco comparadas (de ").append(num(c.escenarios.get(0).kwp(), 0)).append(" a ")
                    .append(num(c.escenarios.get(c.escenarios.size() - 1).kwp(), 0)).append(" kWp).");
            if (mas.sim().cobertura() > s.cobertura()) {
                t.append(" Añadir paneles sube la cobertura ").append(num((mas.sim().cobertura() - s.cobertura()) * 100, 1))
                        .append(" puntos, pero ese extra se produce en horas en que la planta ya consume lo que genera, y el aprovechamiento baja al ")
                        .append(pct(mas.sim().aprovechamiento())).append(".");
            }
        } else {
            t.append("La mayor rentabilidad entre las comparadas se da con **").append(num(mejor.kwp(), 0)).append(" kWp**: VAN de ").append(eur(mejor.fin().van()))
                    .append(" frente a ").append(eur(b.fin().van())).append(" y retorno ").append(retorno(mejor.fin().retorno())).append(" frente a ").append(retorno(b.fin().retorno()))
                    .append(". Conviene valorar esa potencia (cubriría el ").append(pct(mejor.sim().cobertura())).append(" del consumo).");
        }
        return new Recomendacion("potencia", "Potencia de paneles: " + num(b.kwp(), 0) + " kWp", t.toString());
    }

    /** Compara la inclinación del estudio con la que más produce en esa ubicación (sin decir nada si no hay comparación). */
    private static void inclinacion(Calculo c, InclinacionOptima.Resultado o, StringBuilder t) {
        if (o == null) return;
        double kwp = c.instalacion.kwp();
        t.append("\n\n**Inclinación.** ");
        if (o.optima() == o.actual() || o.mejora() * 100 < InclinacionOptima.UMBRAL_PORCENTAJE * 2) {
            t.append("Con **").append(o.actual()).append("°** el estudio produce ").append(num(o.produccionActual(), 0)).append(" kWh por kWp al año según PVGIS, prácticamente lo máximo de esta ubicación")
                    .append(o.optima() == o.actual() ? "" : " (con " + o.optima() + "° solo se ganaría un " + num(o.mejora() * 100, 1) + " %)").append(".");
            return;
        }
        t.append("Con **").append(o.actual()).append("°** el estudio produce ").append(num(o.produccionActual(), 0)).append(" kWh por kWp al año según PVGIS; con **")
                .append(o.optima()).append("°** serían ").append(num(o.produccionOptima(), 0)).append(", un **").append(num(o.mejora() * 100, 1)).append(" % más** (unos ")
                .append(num((o.produccionOptima() - o.produccionActual()) * kwp, 0)).append(" kWh al año con los ").append(num(kwp, 0)).append(" kWp de la instalación). ")
                .append("Se ha buscado probando ").append(o.consultas()).append(" inclinaciones, así que el óptimo es aproximado (±5°).");
        switch (c.orientacionElegida) {
            case "este-oeste" -> t.append(" En este-oeste se suele elegir poca inclinación para no sombrearse entre filas: compruébelo con el espacio disponible.");
            case "sur" -> t.append(" Para usarla, elija la orientación «Otra» con azimut 0 y esa inclinación.");
            default -> t.append(" Más producción no siempre significa más ahorro: compruebe el aprovechamiento tras cambiarla.");
        }
    }

    private static Recomendacion orientacion(Calculo c, InclinacionOptima.Resultado optima) {
        Simulacion.Resultado s = c.base.sim();
        double dia = c.fraccionDiurna;
        StringBuilder t = new StringBuilder();
        t.append("El estudio usa **").append(c.orientacionTexto()).append("** (").append(num(c.instalacion.produccionEspecifica(), 0)).append(" kWh por kWp al año")
                .append(c.conPvgis ? ", con la producción real de la ubicación según PVGIS" : ", con el modelo de producción estándar").append("). ")
                .append("El ").append(pct(dia)).append(" del consumo del cliente cae entre las 8 y las 19 h y se aprovecha el ").append(pct(s.aprovechamiento())).append(" de lo producido.\n\n");
        switch (c.orientacionElegida) {
            case "sur" -> {
                if (dia < 0.6) {
                    t.append("Al sur la producción se concentra en torno al mediodía y, con tan poco consumo en horas de sol, buena parte no se puede usar. ")
                            .append("Conviene valorar **Este-Oeste**, que reparte la producción entre la mañana y la tarde y la acerca al consumo.");
                } else {
                    t.append("Al sur se obtiene la máxima energía anual y, como el consumo es mayoritariamente diurno, esa producción se aprovecha: la orientación es coherente con el perfil del cliente.");
                }
            }
            case "personalizada" -> t.append("Es una orientación libre: la producción de cada hora sale de la ubicación y la inclinación indicadas, y el aprovechamiento resultante es del ")
                    .append(pct(s.aprovechamiento())).append(". Si hay que ajustarla, hágase mirando cuánto sobra a mediodía (ver el excedente más abajo).");
            default -> {
                if (dia > 0.8 && s.aprovechamiento() > 0.92) {
                    t.append("Este-Oeste aplana la producción y evita picos a mediodía. Como el consumo es muy diurno y casi todo lo producido se aprovecha, ")
                            .append("compensa comparar también la orientación **Sur**, que daría más kWh por kWp sin generar excedente apreciable.");
                } else {
                    t.append("Este-Oeste reparte la producción entre mañana y tarde, cubre los picos de consumo de esas horas y deja menos excedente a mediodía que una orientación sur, a cambio de menos energía por kWp.");
                }
            }
        }
        inclinacion(c, optima, t);
        return new Recomendacion("orientacion", "Orientación: " + titular(c), t.toString());
    }

    private static Recomendacion excedenteYBateria(Calculo c) {
        Simulacion.Resultado s = c.base.sim();
        double share = s.produccion() <= 0 ? 0 : s.recortada() / s.produccion();
        double bat = c.instalacion.bateriaKwh();
        Map<String, Object> eco = Calculo.mapa(c.datos.get("economia"));
        double precio = c.base.entrada().precioKwh();
        StringBuilder t = new StringBuilder();
        if (bat <= 0) {
            if (share < 0.05) {
                t.append("Solo se pierde el ").append(pct(share)).append(" de la producción (").append(mwh(s.recortada())).append(" al año) por no poder consumirla en el momento: ")
                        .append("una batería tendría muy poca energía que almacenar, así que no se recomienda para este cliente.");
                return new Recomendacion("excedente", "Batería: no es necesaria", t.toString());
            }
            double cap = Math.max(5, Math.round(s.recortada() / 365.0 / 5.0) * 5.0);
            Simulacion.Resultado conBat = Simulacion.simular(c.curva, c.instalacion.conBateria(cap));
            double extra = conBat.solarConsumida() - s.solarConsumida();
            double ahorro = extra * precio, coste = cap * Calculo.num(eco, "precioBateriaKwh", 250);
            double simple = ahorro > 0 ? coste / ahorro : Double.NaN;
            t.append("Sin batería se pierde el **").append(pct(share)).append("** de la producción (").append(mwh(s.recortada())).append(" al año), sobre todo en ")
                    .append(MESES[maximo(s.recortadaMes())]).append(", porque el consumo no la absorbe y la instalación no vierte a red.\n\n");
            t.append("Una batería de **").append(num(cap, 0)).append(" kWh** (la energía sobrante de un día medio) aprovecharía ").append(mwh(extra)).append(" más al año y subiría la cobertura del ")
                    .append(pct(s.cobertura())).append(" al ").append(pct(conBat.cobertura())).append(". Supone unos ").append(eur(ahorro)).append(" de ahorro anual frente a ")
                    .append(eur(coste)).append(" de inversión: retorno simple de ").append(retorno(simple)).append(", sin contar degradación ni reposición.\n\n");
            if (!Double.isNaN(simple) && (Double.isNaN(c.base.fin().retorno()) || simple > c.base.fin().retorno())) {
                t.append("Es menos rentable que los paneles (retorno de ").append(retorno(c.base.fin().retorno())).append("): no es prioritaria y solo se justifica si el cliente quiere más autoconsumo o prevé un precio de la energía más alto.");
            } else {
                t.append("Se recupera antes que los paneles (retorno de ").append(retorno(c.base.fin().retorno())).append("): merece la pena incluirla en la oferta.");
            }
            return new Recomendacion("excedente", "Batería: " + pct(share) + " de la producción se pierde", t.toString());
        }
        Simulacion.Resultado mitad = Simulacion.simular(c.curva, c.instalacion.conBateria(bat / 2));
        double perdida = (s.cobertura() - mitad.cobertura()) * 100;
        t.append("La batería de **").append(num(bat, 0)).append(" kWh** hace ").append(num(s.ciclos(), 0)).append(" ciclos equivalentes al año y devuelve ").append(mwh(s.desdeBateria()))
                .append(" al consumo. Aun así sobra el ").append(pct(share)).append(" de la producción (").append(mwh(s.recortada())).append(").\n\n");
        if (perdida < 1.5) {
            t.append("Con la mitad de capacidad (").append(num(bat / 2, 0)).append(" kWh) la cobertura bajaría solo ").append(num(perdida, 1))
                    .append(" puntos: la batería está sobredimensionada y reducirla ahorraría unos ").append(eur(bat / 2 * Calculo.num(eco, "precioBateriaKwh", 250))).append(" de inversión.");
        } else {
            t.append("Con la mitad de capacidad la cobertura bajaría ").append(num(perdida, 1)).append(" puntos (del ").append(pct(s.cobertura())).append(" al ").append(pct(mitad.cobertura()))
                    .append("): la capacidad elegida está justificada.");
        }
        return new Recomendacion("excedente", "Batería de " + num(bat, 0) + " kWh: dimensionado", t.toString());
    }

    private static Recomendacion estacionalidad(Calculo c) {
        Simulacion.Resultado s = c.base.sim();
        double[] cob = new double[12];
        for (int m = 0; m < 12; m++) cob[m] = s.consumoMes()[m] <= 0 ? 0 : s.solarMes()[m] / s.consumoMes()[m];
        int mejor = maximo(cob), peor = minimo(cob);
        if (cob[mejor] - cob[peor] < 0.15) return null;
        int pMax = maximo(s.produccionMes()), pMin = minimo(s.produccionMes());
        int cMax = maximo(s.consumoMes()), cMin = minimo(s.consumoMes());
        StringBuilder t = new StringBuilder();
        t.append("En **").append(MESES[mejor]).append("** la instalación cubre el ").append(pct(cob[mejor])).append(" del consumo y en **").append(MESES[peor]).append("** el ").append(pct(cob[peor]))
                .append(". La media anual del ").append(pct(s.cobertura())).append(" esconde esa diferencia.\n\n");
        t.append("La producción de ").append(MESES[pMax]).append(" (").append(mwh(s.produccionMes()[pMax])).append(") es ").append(num(s.produccionMes()[pMax] / Math.max(1, s.produccionMes()[pMin]), 1))
                .append(" veces la de ").append(MESES[pMin]).append(", mientras que el consumo va de ").append(mwh(s.consumoMes()[cMin])).append(" (").append(MESES[cMin]).append(") a ")
                .append(mwh(s.consumoMes()[cMax])).append(" (").append(MESES[cMax]).append("). ");
        t.append(cMax == pMax || Math.abs(cMax - pMax) <= 1
                ? "El consumo y el sol coinciden en el calendario, lo que favorece el autoconsumo."
                : "El consumo no sigue el calendario del sol: en los meses fuertes la producción supera lo que se consume, y en los débiles el consumo se cubre sobre todo con la red.");
        return new Recomendacion("estacionalidad", "Cobertura mes a mes: de " + pct(cob[peor]) + " a " + pct(cob[mejor]), t.toString());
    }

    private static Recomendacion rentabilidad(Calculo c) {
        Calculo.Punto b = c.base;
        Finanzas.Resultado f = b.fin();
        Calculo.Punto caro = c.precios.get(2);
        StringBuilder t = new StringBuilder();
        t.append("Con una inversión de **").append(eur(b.entrada().inversion())).append("**, el ahorro del primer año (energía y potencia, menos el mantenimiento) es de ").append(eur(f.ahorroPrimerAno())).append(" (energía a ")
                .append(num(c.precioEnergia, 3)).append(" €/kWh con una subida anual del ").append(num(b.entrada().subidaEnergia() * 100, 0)).append(" %). ")
                .append(Double.isNaN(f.retorno()) ? "No se recupera en el periodo estudiado" : "Se recupera en **" + retorno(f.retorno()) + "**").append(", con una TIR del ").append(Double.isNaN(f.tir()) ? "—" : pct(f.tir())).append(" y un VAN a ").append(b.entrada().anos())
                .append(" años de ").append(eur(f.van())).append(".\n\n");
        t.append("El resultado depende del precio de la energía: si fuese un 25 % más caro, el retorno sería de ").append(retorno(caro.fin().retorno())).append(" y el VAN de ").append(eur(caro.fin().van())).append(". ");
        t.append(f.van() > 0 && caro.fin().van() > f.van()
                ? "Cuanto más suba la energía, más margen gana la inversión."
                : "");
        return new Recomendacion("rentabilidad", Double.isNaN(f.retorno()) ? "Rentabilidad de la inversión" : "Rentabilidad: retorno en " + retorno(f.retorno()), t.toString().strip());
    }

    private static Recomendacion potenciaContratada(Calculo c) {
        if (c.ahorroPotencia <= 0) return null;
        double ahorro1 = c.base.fin().ahorroPrimerAno();
        StringBuilder t = new StringBuilder();
        t.append("El estudio incluye un ahorro de **").append(eur(c.ahorroPotencia)).append(" al año** por reducir la potencia contratada")
                .append(c.potenciaCalculada ? ", valorado con los precios de potencia del BOE de la tarifa del cliente" : ", según el importe indicado")
                .append(". Es el ").append(pct(ahorro1 <= 0 ? 0 : c.ahorroPotencia / ahorro1)).append(" del ahorro del primer año.\n\n");
        t.append("La potencia máxima registrada en la curva es de ").append(num(c.potenciaMaxima, 0)).append(" kW. Conviene confirmar la reducción contra las puntas de cada periodo antes de bajar la contratada, ")
                .append("porque la fotovoltaica no garantiza producción en las horas de mayor demanda (noche, días nublados).");
        return new Recomendacion("potencia-contratada", "Reducir la potencia contratada", t.toString());
    }

    private static Recomendacion curvaEstimada(Calculo c) {
        if (!c.curva.estimada()) return null;
        Simulacion.Resultado s = c.base.sim();
        String t = "Este estudio parte de una curva **estimada** a partir del consumo anual (" + mwh(c.curva.total()) + "), no de la medida del contador. "
                + "La cobertura del " + pct(s.cobertura()) + " y el aprovechamiento del " + pct(s.aprovechamiento()) + " dependen de a qué horas se consume, y el reparto supuesto ("
                + pct(c.fraccionDiurna) + " del consumo entre las 8 y las 19 h) puede no ser el real.\n\n"
                + "Antes de fijar la potencia y la batería conviene pedir la curva de carga a la distribuidora y repetir el cálculo con ella.";
        return new Recomendacion("curva", "Confirmar con la curva horaria real", t);
    }

    // ------------------------------------------------------------------ utilidades

    private static String titular(Calculo c) {
        return switch (c.orientacionElegida) {
            case "sur" -> "Sur, " + num(c.inclinacion, 0) + "°";
            case "personalizada" -> "libre, " + num(c.inclinacion, 0) + "°";
            default -> "Este-Oeste, " + num(c.inclinacion, 0) + "°";
        };
    }

    private static int maximo(double[] v) {
        int m = 0;
        for (int i = 1; i < v.length; i++) if (v[i] > v[m]) m = i;
        return m;
    }

    private static int minimo(double[] v) {
        int m = 0;
        for (int i = 1; i < v.length; i++) if (v[i] < v[m]) m = i;
        return m;
    }

    private static String retorno(double anos) {
        return Double.isNaN(anos) ? "sin retorno en el periodo estudiado" : num(anos, 1) + " años";
    }

    private static String mwh(double kwh) {
        double m = kwh / 1000.0;
        return num(m, Math.abs(m) < 100 ? 1 : 0) + " MWh";
    }

    private static String eur(double v) {
        return num(v, 0) + " €";
    }

    private static String pct(double fraccion) {
        return num(fraccion * 100, 0) + " %";
    }

    private static String num(double v, int decimales) {
        NumberFormat f = NumberFormat.getNumberInstance(ES);
        f.setMaximumFractionDigits(decimales);
        f.setMinimumFractionDigits(decimales);
        f.setGroupingUsed(true);
        return f.format(v);
    }
}
