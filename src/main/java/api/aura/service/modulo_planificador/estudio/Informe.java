package api.aura.service.modulo_planificador.estudio;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * El informe ejecutivo del estudio, en HTML listo para imprimir a PDF (A4 vertical, 7 páginas) con gráficas en SVG, así que no necesita
 * ningún script. Sigue el formato de los informes de autoconsumo de AURA: resumen, consumo actual, instalación, aprovechamiento,
 * inversión, rentabilidad y escenarios.
 */
public final class Informe {

    private static final String GRIS = "#98999b", AMARILLO = "#fcd700", OSCURO = "#3b3b3d", VERDE = "#4f9d6b", NARANJA = "#e3a63b", CLARO = "#e8e8ea";
    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
    private static final String[] MESES_LARGOS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre",
            "noviembre", "diciembre"};
    private static final int PAGINAS = 7;

    private final Calculo c;
    private final Map<String, Object> datos;
    private final String cliente, ubicacion, cups, tarifa, fecha;
    private final boolean bateria;
    private final StringBuilder h = new StringBuilder(60_000);
    private int pagina = 0;

    private Informe(Calculo c, String fecha) {
        this.c = c;
        this.datos = c.datos;
        this.cliente = texto(datos.get("cliente"));
        String loc = texto(datos.get("localidad")), prov = texto(datos.get("provincia"));
        this.ubicacion = loc.isEmpty() ? prov : prov.isEmpty() || prov.equalsIgnoreCase(loc) ? loc : loc + " (" + prov + ")";
        this.cups = texto(datos.get("cups"));
        this.tarifa = texto(datos.get("tarifa"));
        this.fecha = fecha;
        this.bateria = c.instalacion.bateriaKwh() > 0;
    }

    public static String generar(Calculo c, LocalDate hoy) {
        Informe i = new Informe(c, fechaLarga(hoy));
        return i.construir();
    }

    // ------------------------------------------------------------------ formato

    private static final DecimalFormatSymbols ES = new DecimalFormatSymbols(Locale.forLanguageTag("es-ES"));

    static {
        ES.setGroupingSeparator('.');
        ES.setDecimalSeparator(',');
    }

    static String n(double v, int dec) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "—";
        StringBuilder p = new StringBuilder("#,##0");
        if (dec > 0) p.append('.').append("0".repeat(dec));
        DecimalFormat f = new DecimalFormat(p.toString(), ES);
        return f.format(v);
    }

    /** Importe redondeado a la decena más cercana cuando es grande, como en los informes de referencia. */
    static String eur(double v) {
        return n(Math.round(v), 0) + " €";
    }

    private static String eurRedondeado(double v) {
        double a = Math.abs(v);
        double r = a >= 100_000 ? Math.round(a / 1000.0) * 1000 : a >= 10_000 ? Math.round(a / 100.0) * 100 : a >= 1_000 ? Math.round(a / 10.0) * 10 : Math.round(a);
        return (v < 0 ? "−" : "") + n(r, 0) + " €";
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o).strip();
    }

    private static String fechaLarga(LocalDate d) {
        return d.getDayOfMonth() + " de " + MESES_LARGOS[d.getMonthValue() - 1] + " de " + d.getYear();
    }

    private static String mwh(double kwh) {
        return n(kwh / 1000.0, kwh >= 100_000 ? 0 : 1);
    }

    private static String pct(double f) {
        return n(f * 100, 0) + " %";
    }

    // ------------------------------------------------------------------ páginas

    private String construir() {
        h.append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"><title>Estudio FV · ").append(esc(cliente)).append("</title><style>")
                .append(CSS).append("</style></head><body>");
        pagResumen();
        pagConsumo();
        pagInstalacion();
        pagAprovechamiento();
        pagInversion();
        pagRentabilidad();
        pagEscenarios();
        h.append("</body></html>");
        return h.toString();
    }

    private void abrirPagina() {
        pagina++;
        h.append("<section class=\"pag\"><header><img class=\"logo\" src=\"").append(logo()).append("\" alt=\"AURA\"><span>")
                .append("INFORME EJECUTIVO · AUTOCONSUMO FOTOVOLTAICO").append(bateria ? " CON BATERÍA" : "").append("</span></header><main>");
    }

    private void cerrarPagina() {
        h.append("</main><footer>AURA Ingeniería Energética · auraingenieria.es · ").append(pagina).append('/').append(PAGINAS).append("</footer></section>");
    }

    private void kpis(String[][] k) {
        h.append("<div class=\"kpis\">");
        for (String[] x : k) h.append("<div class=\"kpi\"><b>").append(x[0]).append("</b><span>").append(x[1]).append("</span></div>");
        h.append("</div>");
    }

    private void pagResumen() {
        abrirPagina();
        Simulacion.Resultado s = c.base.sim();
        Finanzas.Resultado f = c.base.fin();
        String titular = n(c.instalacion.kwp(), c.instalacion.kwp() == Math.rint(c.instalacion.kwp()) ? 0 : 1) + " kWp"
                + (bateria ? " y " + n(c.instalacion.bateriaKwh(), 0) + " kWh de batería" : "") + " cubren el " + pct(s.cobertura()) + " del consumo"
                + (cliente.isEmpty() ? "" : " de " + esc(cliente));
        h.append("<h1>").append(titular).append("</h1><p class=\"sub\">").append(esc(cliente));
        if (!ubicacion.isEmpty()) h.append(" · ").append(esc(ubicacion));
        h.append("<br>");
        if (!cups.isEmpty()) h.append("CUPS ").append(esc(cups)).append(" · ");
        if (!tarifa.isEmpty()) h.append("Tarifa ").append(esc(tarifa)).append(" · ");
        h.append(fecha).append("</p>");
        h.append("<h2><i>1</i>Resumen</h2>");
        kpis(new String[][]{{n(c.instalacion.kwp(), 0) + " kWp", "Potencia FV " + orientacionCorta()},
                bateria ? new String[]{n(c.instalacion.bateriaKwh(), 0) + " kWh", "Batería"} : new String[]{eurRedondeado(c.base.entrada().inversion()), "Inversión a " + n(c.instalacion.kwp() > 0 ? c.base.entrada().inversion() / (c.instalacion.kwp() * 1000) : 0, 2) + " €/Wp"},
                {mwh(s.solarConsumida()) + " MWh", "Energía solar consumida al año"}, {pct(s.cobertura()), "Del consumo, cubierto con FV" + (bateria ? " y batería" : "")}});
        h.append("<p>Consumo anual: ").append(mwh(c.curva.total())).append(" MWh. ").append(descripcionConsumo()).append("</p>");
        h.append("<p>Instalación: ").append(n(c.instalacion.kwp(), 1)).append(" kWp de paneles ").append(orientacionLarga())
                .append(bateria ? " y " + n(c.instalacion.bateriaKwh(), 0) + " kWh de batería" : " sin batería").append(", sin vertido a red.</p>");
        h.append("<p>Resultado: ").append(bateria ? "la FV y la batería cubren" : "cubre").append(" el ").append(pct(s.cobertura())).append(" del consumo");
        if (!Double.isNaN(f.retorno())) h.append(" y la inversión se recupera en unos ").append(n(f.retorno(), 1)).append(" años");
        h.append(", con la energía a ").append(n(c.precioEnergia, 3).replaceAll("0$", "")).append(" €/kWh.</p>");
        h.append("<h2><i>2</i>Consumo actual</h2>");
        kpis(new String[][]{{mwh(c.curva.total()) + " MWh", "Consumo anual"}, {n(c.potenciaMedia, 0) + " kW", "Potencia media"},
                {n(c.potenciaMaxima, 0) + " kW", "Potencia máxima horaria"}, {pct(c.fraccionDiurna), "Del consumo cae entre las 8 y las 19 h"}});
        if (c.curva.estimada()) {
            h.append("<p class=\"aviso\"><b>Curva estimada.</b> No se ha cargado la curva horaria de la distribuidora: el consumo por horas se ha estimado con las "
                    + "lecturas mensuales del SIPS y el horario de funcionamiento de la actividad. Con la curva real, el aprovechamiento solar y la potencia máxima "
                    + "pueden variar.</p>");
        }
        cerrarPagina();
    }

    private String descripcionConsumo() {
        Map<String, Object> hor = Calculo.mapa(datos.get("horario"));
        String d = "";
        if (!hor.isEmpty()) {
            d = "La actividad funciona de " + (int) Calculo.num(hor, "ini", 7) + " a " + (int) Calculo.num(hor, "fin", 19) + " h y baja mucho fuera de ese horario. ";
        }
        return d;
    }

    private String orientacionCorta() {
        return "sur".equals(c.instalacion.orientacion()) ? "Sur" : "Este-Oeste";
    }

    private String orientacionLarga() {
        return "sur".equals(c.instalacion.orientacion()) ? "orientados al sur" : "orientados al este-oeste";
    }

    private void pagConsumo() {
        abrirPagina();
        double[] m = c.consumoMes;
        int max1 = 0, max2 = -1, min1 = 0, min2 = -1;
        for (int i = 1; i < 12; i++) if (m[i] > m[max1]) max1 = i;
        for (int i = 0; i < 12; i++) if (i != max1 && (max2 < 0 || m[i] > m[max2])) max2 = i;
        for (int i = 1; i < 12; i++) if (m[i] < m[min1]) min1 = i;
        for (int i = 0; i < 12; i++) if (i != min1 && (min2 < 0 || m[i] < m[min2])) min2 = i;
        h.append("<h3>").append(cap(MESES_LARGOS[max1])).append(" y ").append(MESES_LARGOS[max2]).append(" son los meses de más consumo; ")
                .append(MESES_LARGOS[min1]).append(" y ").append(MESES_LARGOS[min2]).append(", los de menos</h3>");
        double[] mwh = new double[12];
        for (int i = 0; i < 12; i++) mwh[i] = m[i] / 1000.0;
        h.append(barras(new double[][]{mwh}, new String[]{GRIS}, new String[]{"Consumo (MWh)"}, 1, null, false));
        h.append("<p class=\"nota\">Consumo mensual (MWh). ").append(c.curva.estimada() ? "Estimado con las lecturas del SIPS." : "Curva horaria del suministro, último año completo.").append("</p>");
        double[] lab = c.perfilLaborable, fin = c.perfilFinde;
        int hmax = 0;
        for (int i = 1; i < 24; i++) if (lab[i] > lab[hmax]) hmax = i;
        double finMax = 0;
        for (double v : fin) finMax = Math.max(finMax, v);
        h.append("<h3>Los días laborables el consumo llega a unos ").append(n(lab[hmax], 0)).append(" kW hacia las ").append(hmax).append(" h; el fin de semana no pasa de ")
                .append(n(finMax, 0)).append(" kW</h3>");
        h.append(lineas(new double[][]{lab, fin}, new String[]{OSCURO, GRIS}, new String[]{"Laborable", "Fin de semana"}, null));
        h.append("<p class=\"nota\">Potencia media por hora y tipo de día (kW).</p>");
        cerrarPagina();
    }

    private void pagInstalacion() {
        abrirPagina();
        Simulacion.Resultado s = c.base.sim();
        double wp = 670;
        long modulos = Math.round(c.instalacion.kwp() * 1000 / wp);
        h.append("<h2><i>3</i>Instalación propuesta</h2><table class=\"t\"><tr><th>Parámetro</th><th>Valor</th></tr>");
        fila("Potencia pico", n(modulos * wp / 1000, 1) + " kWp (" + n(modulos, 0) + " × " + n(wp, 0) + " Wp)");
        if (bateria) fila("Batería", n(c.instalacion.bateriaKwh(), 0) + " kWh");
        fila("Orientación", "sur".equals(c.instalacion.orientacion()) ? "Sur, 30° de inclinación" : "Este-Oeste, 15° de inclinación");
        fila("Modalidad", "Autoconsumo sin excedentes (antivertido)");
        fila("Producción anual estimada", n(s.produccion(), 0) + " kWh");
        fila(bateria ? "Consumida al momento" : "Energía solar consumida al momento", n(s.directa(), 0) + " kWh/año");
        if (bateria) {
            fila("Consumida desde la batería", n(s.desdeBateria(), 0) + " kWh/año");
            fila("Energía solar consumida", n(s.solarConsumida(), 0) + " kWh/año");
            fila("Ciclos de batería al año", "≈ " + n(s.ciclos(), 0));
        } else {
            fila("Aprovechamiento de la producción", pct(s.aprovechamiento()));
        }
        fila("Superficie de cubierta aproximada", "≈ " + n(Math.round(c.instalacion.kwp() * ("sur".equals(c.instalacion.orientacion()) ? 7.5 : 5.5) / 50.0) * 50, 0) + " m²");
        h.append("</table>");
        double max = 0;
        for (int k = 0; k < 6; k++) for (double v : s.diaTipo()[k]) max = Math.max(max, v);
        h.append("<h3>Un día laborable, el consumo y la producción solar a lo largo del día</h3>");
        h.append("<div><b class=\"mes\">Junio</b>")
                .append(lineas(new double[][]{s.diaTipo()[0], s.diaTipo()[1], s.diaTipo()[2]}, new String[]{OSCURO, AMARILLO, VERDE},
                        new String[]{"Consumo", "Producción FV", "Solar consumida"}, max))
                .append("</div><div><b class=\"mes\">Diciembre</b>")
                .append(lineas(new double[][]{s.diaTipo()[3], s.diaTipo()[4], s.diaTipo()[5]}, new String[]{OSCURO, AMARILLO, VERDE},
                        new String[]{"Consumo", "Producción FV", "Solar consumida"}, max)).append("</div>");
        h.append("<p class=\"nota\">Consumo y aportación solar (kW) en un día laborable medio de junio y de diciembre. La producción máxima ronda los ")
                .append(n(s.produccionMaxima(), 0)).append(" kW.</p>");
        h.append("<p class=\"nota\">Hipótesis técnicas: producción específica de ").append(n(c.instalacion.produccionEspecifica(), 0))
                .append(" kWh/kWp al año con perfil medio mensual y horario; día medio de cada mes, sin variación meteorológica diaria")
                .append(bateria ? "; batería con rendimiento del 90 % ida y vuelta, descarga útil del 90 % y potencia de carga/descarga de C/2." : ".").append("</p>");
        cerrarPagina();
    }

    private void fila(String a, String b) {
        h.append("<tr><td>").append(a).append("</td><td>").append(b).append("</td></tr>");
    }

    private void pagAprovechamiento() {
        abrirPagina();
        Simulacion.Resultado s = c.base.sim();
        h.append("<h3>Se aprovecha el ").append(pct(s.aprovechamiento())).append(" de la producción; lo que se recorta se concentra en los meses y días de menos consumo</h3>");
        double[] solar = new double[12], rec = new double[12];
        String[] etiq = new String[12];
        for (int i = 0; i < 12; i++) {
            solar[i] = s.solarMes()[i] / 1000.0;
            rec[i] = s.recortadaMes()[i] / 1000.0;
            double prod = s.produccionMes()[i];
            etiq[i] = prod <= 0 ? "" : n(100 * s.solarMes()[i] / prod, 0) + " %";
        }
        h.append(barras(new double[][]{solar, rec}, new String[]{VERDE, NARANJA}, new String[]{"Solar consumida (MWh)", "Recortada (MWh)"}, 1, etiq, true));
        h.append("<p class=\"nota\">Energía solar consumida y recortada por mes (MWh). Encima de cada barra, el porcentaje de la producción que se aprovecha.</p>");
        h.append("<h2><i>4</i>Por qué ").append(n(c.instalacion.kwp(), 0)).append(" kWp").append(bateria ? " y " + n(c.instalacion.bateriaKwh(), 0) + " kWh" : " y sin batería").append("</h2>");
        double[] cob = new double[c.barrido.size()], apr = new double[c.barrido.size()];
        String[] ejeX = new String[c.barrido.size()];
        for (int i = 0; i < cob.length; i++) {
            cob[i] = c.barrido.get(i).sim().cobertura() * 100;
            apr[i] = c.barrido.get(i).sim().aprovechamiento() * 100;
            ejeX[i] = n(c.barrido.get(i).kwp(), 0);
        }
        h.append(lineasX(new double[][]{cob, apr}, new String[]{VERDE, NARANJA}, new String[]{"Consumo cubierto (%)", "Producción aprovechada (%)"}, ejeX));
        h.append("<p class=\"nota\">Con cada potencia de paneles (kWp), porcentaje del consumo que se cubre y de la producción que se aprovecha.</p>");
        Calculo.Punto mas = c.barrido.get(c.barrido.size() - 1);
        Calculo.Punto antes = c.barrido.get(2), centro = c.base;
        h.append("<p>Pasar de ").append(n(antes.kwp(), 0)).append(" a ").append(n(centro.kwp(), 0)).append(" kWp sube la cobertura del ").append(pct(antes.sim().cobertura()))
                .append(" al ").append(pct(centro.sim().cobertura())).append("; de ").append(n(centro.kwp(), 0)).append(" a ").append(n(mas.kwp(), 0))
                .append(" kWp solo la sube hasta el ").append(pct(mas.sim().cobertura())).append(" y la producción aprovechada baja del ")
                .append(pct(centro.sim().aprovechamiento())).append(" al ").append(pct(mas.sim().aprovechamiento())).append(". Cuanto más paneles, más sol sobra en los momentos de poco consumo.</p>");
        cerrarPagina();
    }

    private void pagInversion() {
        abrirPagina();
        Finanzas.Entrada e = c.base.entrada();
        Finanzas.Resultado f = c.base.fin();
        Simulacion.Resultado s = c.base.sim();
        h.append("<h2><i>5</i>La inversión</h2>");
        kpis(new String[][]{{eurRedondeado(e.inversion()), "Inversión total"}, {eurRedondeado(f.ahorroPrimerAno()), "Ahorro neto el primer año"},
                {Double.isNaN(f.retorno()) ? "—" : n(f.retorno(), 1) + " años", "En recuperar la inversión"}, {Double.isNaN(f.tir()) ? "—" : n(f.tir() * 100, 1) + " %", "Rentabilidad anual (TIR)"}});
        h.append("<table class=\"t\"><tr><th>Concepto</th><th>Importe</th></tr>");
        fila("Inversión total (" + n(c.instalacion.kwp(), 1) + " kWp a " + n(c.precioWp, 2) + " €/Wp" + (bateria ? " y " + n(c.instalacion.bateriaKwh(), 0) + " kWh de batería" : "") + ")", eur(e.inversion()));
        fila("Energía que deja de comprar", n(s.solarConsumida(), 0) + " kWh/año");
        fila("Ahorro en energía (a " + n(c.precioEnergia, 3).replaceAll("0$", "") + " €/kWh)", eur(s.solarConsumida() * c.precioEnergia));
        if (c.ahorroPotencia > 0) fila("Ahorro en potencia", eur(c.ahorroPotencia));
        fila("Mantenimiento y seguro", "−" + eur(e.mantenimiento()) + "/año");
        fila("<b>Ahorro neto el primer año</b>", "<b>" + eur(f.ahorroPrimerAno()) + "</b>");
        h.append("</table>");
        h.append("<p class=\"nota\">Importes sin IVA. El precio de la energía es un valor medio prudente; la inversión y el mantenimiento son estimaciones que se confirman en la oferta.</p>");
        cerrarPagina();
    }

    private void pagRentabilidad() {
        abrirPagina();
        Finanzas.Entrada e = c.base.entrada();
        Finanzas.Resultado f = c.base.fin();
        h.append("<h3>El dinero acumulado año a año: ").append(Double.isNaN(f.retorno()) ? "la inversión no llega a recuperarse en 25 años" : "la inversión se recupera en unos " + n(f.retorno(), 1) + " años").append("</h3>");
        h.append(acumulado(f.acumulado()));
        h.append("<p class=\"nota\">Balance acumulado (€) de cada año: ahorro menos mantenimiento, menos la inversión inicial. A 25 años ronda los ")
                .append(eurRedondeado(f.acumulado()[f.acumulado().length - 1])).append(".</p>");
        h.append("<table class=\"t\"><tr><th>Indicador</th><th>Valor</th><th>Qué significa</th></tr>");
        h.append("<tr><td>Retorno de la inversión</td><td>").append(Double.isNaN(f.retorno()) ? "—" : n(f.retorno(), 1) + " años").append("</td><td>Lo que tarda en pagarse sola</td></tr>");
        h.append("<tr><td>Retorno descontado</td><td>").append(Double.isNaN(f.retornoDescontado()) ? "—" : n(f.retornoDescontado(), 1) + " años").append("</td><td>Contando el valor del dinero en el tiempo</td></tr>");
        h.append("<tr><td>VAN a ").append(e.anos()).append(" años</td><td>").append(eurRedondeado(f.van())).append("</td><td>Lo que gana por encima de invertir el dinero al ").append(n(e.descuento() * 100, 0)).append(" %</td></tr>");
        h.append("<tr><td>TIR</td><td>").append(Double.isNaN(f.tir()) ? "—" : n(f.tir() * 100, 1) + " %").append("</td><td>Rentabilidad anual del capital invertido</td></tr>");
        h.append("<tr><td>Ahorro acumulado</td><td>").append(eurRedondeado(f.ahorroAcumulado())).append("</td><td>Suma de los ").append(e.anos()).append(" años</td></tr></table>");
        h.append("<h3>Si la energía vale más, la instalación se paga antes</h3><table class=\"t\"><tr><th>Precio medio de la energía</th><th>Ahorro año 1</th><th>Retorno</th><th>TIR</th></tr>");
        for (Calculo.Punto p : c.precios) {
            h.append("<tr><td>").append(n(p.entrada().precioKwh(), 3).replaceAll("0$", "")).append(" €/kWh</td><td>").append(eurRedondeado(p.fin().ahorroPrimerAno())).append("</td><td>")
                    .append(Double.isNaN(p.fin().retorno()) ? "—" : n(p.fin().retorno(), 1) + " años").append("</td><td>")
                    .append(Double.isNaN(p.fin().tir()) ? "—" : n(p.fin().tir() * 100, 1) + " %").append("</td></tr>");
        }
        h.append("</table><p class=\"nota\">Cálculo: energía solar consumida por el precio medio de la energía").append(c.ahorroPotencia > 0 ? ", más el ahorro en potencia (sin subida anual)" : "")
                .append(". Subida del precio de la energía del ").append(n(e.subidaEnergia() * 100, 0)).append(" % anual, degradación de los módulos del ").append(n(e.degradacion() * 100, 1))
                .append(" % anual").append(e.degradacionBateria() > 0 ? " y de la batería del " + n(e.degradacionBateria() * 100, 0) + " % anual" : "")
                .append(", mantenimiento y seguro de ").append(eur(e.mantenimiento())).append(" al año (+").append(n(e.subidaMantenimiento() * 100, 0)).append(" % anual)")
                .append(e.reposicion() > 0 ? ", cambio de equipos en el año " + e.anoReposicion() + " (" + eurRedondeado(e.reposicion()) + ")" : "")
                .append(", tasa de descuento del ").append(n(e.descuento() * 100, 0)).append(" % y horizonte de ").append(e.anos()).append(" años. Importes sin IVA.</p>");
        cerrarPagina();
    }

    private void pagEscenarios() {
        abrirPagina();
        h.append("<h2><i>6</i>Escenarios de potencia</h2>");
        List<Calculo.Punto> e = c.escenarios;
        h.append("<h3>Cómo cambian el ahorro y el retorno con más o menos paneles</h3><table class=\"t esc\"><tr><th>Escenario</th>");
        for (Calculo.Punto p : e) h.append("<th>").append(c.instalacion.kwp() >= 500 ? n(p.kwp() / 1000.0, 2) + " MWp" : n(p.kwp(), 0) + " kWp").append("</th>");
        h.append("</tr>");
        long[] mod = new long[e.size()];
        for (int i = 0; i < mod.length; i++) mod[i] = Math.round(e.get(i).kwp() * 1000 / 670);
        escRow("Módulos de 670 Wp", i -> n(mod[i], 0));
        escRow("Potencia pico", i -> n(mod[i] * 0.67, 1) + " kWp");
        escRow("Inversión", i -> eurRedondeado(e.get(i).entrada().inversion()));
        escRow("Producción anual", i -> n(e.get(i).sim().produccion() / 1000.0, 0) + " MWh");
        escRow("Energía solar consumida", i -> n(e.get(i).sim().solarConsumida() / 1000.0, 0) + " MWh");
        escRow("Consumo cubierto", i -> pct(e.get(i).sim().cobertura()));
        escRow("Producción aprovechada", i -> pct(e.get(i).sim().aprovechamiento()));
        escRow("Ahorro neto año 1", i -> eurRedondeado(e.get(i).fin().ahorroPrimerAno()));
        escRow("Retorno", i -> Double.isNaN(e.get(i).fin().retorno()) ? "—" : n(e.get(i).fin().retorno(), 1) + " años");
        escRow("TIR", i -> Double.isNaN(e.get(i).fin().tir()) ? "—" : n(e.get(i).fin().tir() * 100, 1) + " %");
        escRow("VAN a 25 años", i -> eurRedondeado(e.get(i).fin().van()));
        h.append("</table><p class=\"nota\">VAN: beneficio a 25 años por encima de invertir el dinero al 5 %. La inversión y el mantenimiento de cada escenario se escalan con la potencia; ")
                .append(bateria ? "la batería es la misma en todos." : "no hay batería.").append("</p>");
        h.append("<div class=\"cierre\"><b>AURA Ingeniería Energética S.L.</b> · auraingenieria.es<br>Estudio orientativo: las cifras definitivas se fijan en la oferta tras la visita técnica.</div>");
        cerrarPagina();
    }

    private void escRow(String nombre, java.util.function.IntFunction<String> v) {
        h.append("<tr><td>").append(nombre).append("</td>");
        for (int i = 0; i < c.escenarios.size(); i++) h.append("<td").append(i == 0 ? " class=\"base\"" : "").append('>').append(v.apply(i)).append("</td>");
        h.append("</tr>");
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ gráficas SVG

    private static final int W = 680, H = 230, ML = 46, MR = 12, MT = 14, MB = 26;

    private static double techo(double v) {
        if (v <= 0) return 1;
        double p = Math.pow(10, Math.floor(Math.log10(v)));
        double r = v / p;
        return (r <= 1 ? 1 : r <= 2 ? 2 : r <= 4 ? 4 : r <= 6 ? 6 : r <= 8 ? 8 : 10) * p;
    }

    private String ejes(double max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= 4; i++) {
            double v = max * i / 4.0;
            double y = MT + (H - MT - MB) * (1 - i / 4.0);
            double paso = max / 4.0;
            sb.append("<line x1=\"").append(ML).append("\" x2=\"").append(W - MR).append("\" y1=\"").append(y).append("\" y2=\"").append(y).append("\" class=\"g\"/>")
                    .append("<text x=\"").append(ML - 6).append("\" y=\"").append(y + 3).append("\" class=\"ty\" text-anchor=\"end\">")
                    .append(n(v, paso >= 1 && paso == Math.rint(paso) ? 0 : paso >= 1 ? 1 : 2)).append("</text>");
        }
        return sb.toString();
    }

    private String leyenda(String[] nombres, String[] colores) {
        StringBuilder sb = new StringBuilder("<div class=\"ley\">");
        for (int i = 0; i < nombres.length; i++) sb.append("<span><i style=\"background:").append(colores[i]).append("\"></i>").append(nombres[i]).append("</span>");
        return sb.append("</div>").toString();
    }

    /** Barras mensuales: una serie suelta o series apiladas, con un texto opcional encima de cada mes. */
    private String barras(double[][] series, String[] colores, String[] nombres, int dec, String[] encima, boolean apiladas) {
        double max = 0;
        for (int m = 0; m < 12; m++) {
            double t = 0;
            for (double[] s : series) t = apiladas ? t + s[m] : Math.max(t, s[m]);
            max = Math.max(max, t);
        }
        max = techo(max * 1.12);
        double ancho = (W - ML - MR) / 12.0;
        StringBuilder sb = new StringBuilder("<svg viewBox=\"0 0 " + W + " " + H + "\" class=\"graf\">").append(ejes(max));
        for (int m = 0; m < 12; m++) {
            double base = 0;
            double x = ML + ancho * m + ancho * 0.18;
            for (int k = 0; k < series.length; k++) {
                double v = series[k][m];
                double hh = (H - MT - MB) * v / max;
                double y = MT + (H - MT - MB) - hh - (apiladas ? (H - MT - MB) * base / max : 0);
                sb.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"").append(ancho * 0.64).append("\" height=\"").append(Math.max(hh, 0))
                        .append("\" fill=\"").append(colores[k]).append("\"/>");
                if (apiladas) base += v;
            }
            if (encima != null && encima[m] != null && !encima[m].isEmpty()) {
                double alto = (H - MT - MB) * base / max;
                sb.append("<text x=\"").append(x + ancho * 0.32).append("\" y=\"").append(MT + (H - MT - MB) - alto - 4).append("\" class=\"tv\" text-anchor=\"middle\">").append(encima[m]).append("</text>");
            }
            sb.append("<text x=\"").append(x + ancho * 0.32).append("\" y=\"").append(H - 8).append("\" class=\"tx\" text-anchor=\"middle\">").append(MESES[m]).append("</text>");
        }
        return sb.append("</svg>").append(nombres.length > 1 ? leyenda(nombres, colores) : "").toString();
    }

    /** Líneas por hora del día (24 puntos). */
    private String lineas(double[][] series, String[] colores, String[] nombres, Double techoFijo) {
        double max = 0;
        for (double[] s : series) for (double v : s) max = Math.max(max, v);
        if (techoFijo != null) max = Math.max(max, techoFijo);
        max = techo(max * 1.1);
        StringBuilder sb = new StringBuilder("<svg viewBox=\"0 0 " + W + " " + H + "\" class=\"graf\">").append(ejes(max));
        double dx = (W - ML - MR) / 23.0;
        for (int k = 0; k < series.length; k++) {
            StringBuilder pts = new StringBuilder();
            for (int i = 0; i < 24; i++) {
                pts.append(ML + dx * i).append(',').append(MT + (H - MT - MB) * (1 - series[k][i] / max)).append(' ');
            }
            sb.append("<polyline fill=\"none\" stroke=\"").append(colores[k]).append("\" stroke-width=\"2.2\" stroke-linejoin=\"round\" points=\"").append(pts).append("\"/>");
        }
        for (int i = 0; i < 24; i += 3) {
            sb.append("<text x=\"").append(ML + dx * i).append("\" y=\"").append(H - 8).append("\" class=\"tx\" text-anchor=\"middle\">").append(i).append(" h</text>");
        }
        return sb.append("</svg>").append(leyenda(nombres, colores)).toString();
    }

    /** Líneas con un eje X de etiquetas (por ejemplo, kWp). */
    private String lineasX(double[][] series, String[] colores, String[] nombres, String[] ejeX) {
        double max = 100;
        int n = ejeX.length;
        StringBuilder sb = new StringBuilder("<svg viewBox=\"0 0 " + W + " " + H + "\" class=\"graf\">").append(ejes(max));
        double dx = (W - ML - MR) / (double) (n - 1);
        for (int k = 0; k < series.length; k++) {
            StringBuilder pts = new StringBuilder();
            for (int i = 0; i < n; i++) {
                double x = ML + dx * i, y = MT + (H - MT - MB) * (1 - series[k][i] / max);
                pts.append(x).append(',').append(y).append(' ');
            }
            sb.append("<polyline fill=\"none\" stroke=\"").append(colores[k]).append("\" stroke-width=\"2.4\" points=\"").append(pts).append("\"/>");
            for (int i = 0; i < n; i++) {
                sb.append("<circle cx=\"").append(ML + dx * i).append("\" cy=\"").append(MT + (H - MT - MB) * (1 - series[k][i] / max)).append("\" r=\"3\" fill=\"").append(colores[k]).append("\"/>");
            }
        }
        for (int i = 0; i < n; i++) {
            sb.append("<text x=\"").append(ML + dx * i).append("\" y=\"").append(H - 8).append("\" class=\"tx\" text-anchor=\"middle\">").append(ejeX[i]).append("</text>");
        }
        return sb.append("</svg>").append(leyenda(nombres, colores)).toString();
    }

    /** Balance acumulado (años 0 a N): área bajo cero en rojo suave y sobre cero en verde. */
    private String acumulado(double[] acum) {
        double min = 0, max = 0;
        for (double v : acum) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double techo = techo(Math.max(max, 1) * 1.1), suelo = -techo(Math.max(-min, 1) * 1.1);
        double dx = (W - ML - MR) / (double) (acum.length - 1);
        java.util.function.DoubleUnaryOperator yy = v -> MT + (H - MT - MB) * (techo - v) / (techo - suelo);
        StringBuilder sb = new StringBuilder("<svg viewBox=\"0 0 " + W + " " + H + "\" class=\"graf\">");
        for (int i = 0; i <= 4; i++) {
            double v = suelo + (techo - suelo) * i / 4.0;
            sb.append("<line x1=\"").append(ML).append("\" x2=\"").append(W - MR).append("\" y1=\"").append(yy.applyAsDouble(v)).append("\" y2=\"").append(yy.applyAsDouble(v)).append("\" class=\"g\"/>")
                    .append("<text x=\"").append(ML - 6).append("\" y=\"").append(yy.applyAsDouble(v) + 3).append("\" class=\"ty\" text-anchor=\"end\">").append(n(v / 1000.0, 0)).append(" k</text>");
        }
        sb.append("<line x1=\"").append(ML).append("\" x2=\"").append(W - MR).append("\" y1=\"").append(yy.applyAsDouble(0)).append("\" y2=\"").append(yy.applyAsDouble(0)).append("\" stroke=\"").append(OSCURO).append("\"/>");
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < acum.length; i++) pts.append(ML + dx * i).append(',').append(yy.applyAsDouble(acum[i])).append(' ');
        sb.append("<polyline fill=\"none\" stroke=\"").append(VERDE).append("\" stroke-width=\"2.6\" stroke-linejoin=\"round\" points=\"").append(pts).append("\"/>");
        for (int i = 0; i < acum.length; i += 5) {
            sb.append("<text x=\"").append(ML + dx * i).append("\" y=\"").append(H - 8).append("\" class=\"tx\" text-anchor=\"middle\">").append(i == 0 ? "año 0" : i).append("</text>");
        }
        return sb.append("</svg>").toString();
    }

    private static String logoCache;

    private static synchronized String logo() {
        if (logoCache == null) {
            try (InputStream in = Informe.class.getResourceAsStream("/propuesta/logo-aura.png")) {
                logoCache = in == null ? "" : "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
            } catch (IOException e) {
                logoCache = "";
            }
        }
        return logoCache;
    }

    private static final String CSS = """
            @page { size: A4; margin: 0 }
            * { box-sizing: border-box }
            body { margin: 0; background: #fff; color: #2c2c2e; font: 12.5px/1.5 -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; -webkit-print-color-adjust: exact; print-color-adjust: exact }
            .pag { width: 210mm; height: 297mm; padding: 14mm 15mm 12mm; position: relative; page-break-after: always; overflow: hidden; display: flex; flex-direction: column }
            .pag:last-child { page-break-after: auto }
            header { display: flex; justify-content: space-between; align-items: center; border-bottom: 3px solid #fcd700; padding-bottom: 8px; margin-bottom: 14px }
            header .logo { height: 34px }
            header span { font-size: 9.5px; letter-spacing: .08em; color: #98999b; font-weight: 600 }
            main { flex: 1 }
            footer { font-size: 10px; color: #98999b; border-top: 1px solid #e8e8ea; padding-top: 6px; text-align: right }
            h1 { font-size: 25px; line-height: 1.2; margin: 6px 0 6px; color: #2c2c2e }
            .sub { color: #6b6b6e; margin: 0 0 14px }
            h2 { font-size: 17px; margin: 16px 0 8px; display: flex; align-items: center; gap: 9px }
            h2 i { font-style: normal; background: #fcd700; color: #2c2c2e; width: 24px; height: 24px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700 }
            h3 { font-size: 15px; line-height: 1.3; margin: 12px 0 6px }
            p { margin: 7px 0 }
            .nota { color: #6b6b6e; font-size: 10.5px }
            .aviso { background: #fff8d6; border-left: 4px solid #fcd700; padding: 7px 10px; font-size: 11px }
            .kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 9px; margin: 8px 0 10px }
            .kpi { background: #f5f5f6; border-radius: 8px; padding: 10px 10px 9px; border-top: 3px solid #fcd700 }
            .kpi b { display: block; font-size: 19px; line-height: 1.15 }
            .kpi span { font-size: 10.5px; color: #6b6b6e }
            .t { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 11.5px }
            .t th { text-align: left; background: #98999b; color: #fff; padding: 6px 9px; font-weight: 600 }
            .t td { padding: 5.5px 9px; border-bottom: 1px solid #e8e8ea }
            .t tr:nth-child(even) td { background: #fafafa }
            .esc th, .esc td { text-align: right } .esc th:first-child, .esc td:first-child { text-align: left }
            .esc .base { background: #fff8d6 !important; font-weight: 600 }
            .graf { width: 100%; height: auto; display: block }
            .graf .g { stroke: #e8e8ea } .graf .ty, .graf .tx { font-size: 10px; fill: #6b6b6e } .graf .tv { font-size: 9.5px; fill: #2c2c2e; font-weight: 600 }
            .ley { display: flex; gap: 14px; flex-wrap: wrap; font-size: 10.5px; color: #4a4a4c; margin: 2px 0 4px 46px }
            .ley i { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 5px }
            .dos { display: grid; grid-template-columns: 1fr 1fr; gap: 10px } .mes { font-size: 11px; color: #6b6b6e }
            .cierre { margin-top: 18px; padding: 10px 12px; background: #f5f5f6; border-left: 4px solid #fcd700; font-size: 11px }
            """;
}
