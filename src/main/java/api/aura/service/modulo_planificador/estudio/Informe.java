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
        return h.toString().replace("@@TOTAL@@", String.valueOf(pagina));
    }

    private void abrirPagina() {
        pagina++;
        h.append("<section class=\"pag\"><header><img class=\"logo\" src=\"").append(logo()).append("\" alt=\"AURA\"><span>")
                .append("INFORME EJECUTIVO · AUTOCONSUMO FOTOVOLTAICO").append(bateria ? " CON BATERÍA" : "").append("</span></header><main>");
    }

    private void cerrarPagina() {
        h.append("</main><footer>AURA Ingeniería Energética · auraingenieria.es · ").append(pagina).append("/@@TOTAL@@</footer></section>");
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
        h.append(", con la energía a ").append(n(c.precioEnergia, 3).replaceAll("0$", "")).append(" €/kWh")
                .append(c.precioPeriodo != null ? " (media de los precios de cada periodo, pesada con la energía solar de cada uno)" : "").append(".</p>");
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
            d = "La actividad funciona " + franjas(hor) + " y baja mucho fuera de ese horario. ";
            if (hor.get("excepciones") instanceof java.util.List<?> ex && !ex.isEmpty()) {
                String[] meses = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
                for (Object o : ex) {
                    Map<String, Object> x = Calculo.mapa(o);
                    java.util.List<String> nombres = new java.util.ArrayList<>();
                    if (x.get("meses") instanceof java.util.List<?> ms) for (Object m : ms) nombres.add(meses[((Number) m).intValue() - 1]);
                    d += "En " + String.join(", ", nombres) + " lo hace " + franjas(x) + ". ";
                }
            }
        }
        return d;
    }

    /** «de 7 a 19 h», o «de 8 a 14 h y de 16 a 20 h» si la jornada tiene interrupciones. */
    private static String franjas(Map<String, Object> m) {
        java.util.List<int[]> t = new java.util.ArrayList<>();
        t.add(new int[] {(int) Calculo.num(m, "ini", 7), (int) Calculo.num(m, "fin", 19)});
        if (m.get("tramos") instanceof java.util.List<?> extra) {
            for (Object o : extra) {
                Map<String, Object> x = Calculo.mapa(o);
                t.add(new int[] {(int) Calculo.num(x, "ini", 0), (int) Calculo.num(x, "fin", 1)});
            }
        }
        t.sort((a, b) -> Integer.compare(a[0], b[0]));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.size(); i++) sb.append(i == 0 ? "de " : (i == t.size() - 1 ? " y de " : ", de ")).append(t.get(i)[0]).append(" a ").append(t.get(i)[1]).append(" h");
        return sb.toString();
    }

    private String orientacionCorta() {
        return "personalizada".equals(c.orientacionElegida) ? Calculo.rumbo(c.azimut).substring(0, 1).toUpperCase() + Calculo.rumbo(c.azimut).substring(1)
                : "sur".equals(c.instalacion.orientacion()) ? "Sur" : "Este-Oeste";
    }

    private String orientacionLarga() {
        return "personalizada".equals(c.orientacionElegida) ? "orientados al " + Calculo.rumbo(c.azimut) + " con " + Math.round(c.inclinacion) + "° de inclinación"
                : "sur".equals(c.instalacion.orientacion()) ? "orientados al sur" : "orientados al este-oeste";
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
        contInstalacion(true);
        Simulacion.Resultado s = c.base.sim();
        double max = 0;
        for (int k = 0; k < 7; k++) if (k != 3) for (double v : s.diaTipo()[k]) max = Math.max(max, v);
        h.append("<h3>Un día laborable, el consumo y la producción solar a lo largo del día</h3>");
        h.append("<div><b class=\"mes\">Junio</b>")
                .append(lineas(new double[][]{s.diaTipo()[0], s.diaTipo()[1], s.diaTipo()[2]}, new String[]{OSCURO, AMARILLO, VERDE},
                        new String[]{"Consumo", "Producción FV", "Solar consumida"}, max))
                .append("</div><div><b class=\"mes\">Diciembre</b>")
                .append(lineas(new double[][]{s.diaTipo()[4], s.diaTipo()[5], s.diaTipo()[6]}, new String[]{OSCURO, AMARILLO, VERDE},
                        new String[]{"Consumo", "Producción FV", "Solar consumida"}, max)).append("</div>");
        h.append("<p class=\"nota\">Consumo y aportación solar (kW) en un día laborable medio de junio y de diciembre. La producción máxima ronda los ")
                .append(n(s.produccionMaxima(), 0)).append(" kW.</p>");
        h.append("<p class=\"nota\">Hipótesis técnicas: producción específica de ").append(n(c.instalacion.produccionEspecifica(), 0))
                .append(" kWh/kWp al año con perfil medio mensual y horario; día medio de cada mes, sin variación meteorológica diaria")
                .append(bateria ? "; batería con rendimiento del 90 % ida y vuelta, descarga útil del 90 % y potencia de carga/descarga de C/2." : ".").append("</p>");
        cerrarPagina();
    }


    private void contInstalacion(boolean titulo2) {
        Simulacion.Resultado s = c.base.sim();
        double wp = 670;
        long modulos = Math.round(c.instalacion.kwp() * 1000 / wp);
        h.append(titulo2 ? "<h2><i>3</i>Instalación propuesta</h2>" : "<h3>Instalación propuesta</h3>").append("<table class=\"t\"><tr><th>Parámetro</th><th>Valor</th></tr>");
        fila("Potencia pico", n(modulos * wp / 1000, 1) + " kWp (" + n(modulos, 0) + " × " + n(wp, 0) + " Wp)");
        if (bateria) fila("Batería", n(c.instalacion.bateriaKwh(), 0) + " kWh");
        fila("Orientación", c.orientacionTexto());
        fila("Modalidad", "Autoconsumo sin excedentes (antivertido)");
        fila("Producción anual estimada", n(s.produccion(), 0) + " kWh");
        if (c.conPvgis) fila("Cálculo de la producción", "PVGIS (Comisión Europea), " + n(c.instalacion.produccionEspecifica(), 0) + " kWh/kWp·año");
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
        contInversion(true);
        cerrarPagina();
    }

    private void contInversion(boolean titulo2) {
        Finanzas.Entrada e = c.base.entrada();
        Finanzas.Resultado f = c.base.fin();
        Simulacion.Resultado s = c.base.sim();
        h.append(titulo2 ? "<h2><i>5</i>La inversión</h2>" : "<h3>La inversión y de dónde sale el ahorro</h3>");
        kpis(new String[][]{{eurRedondeado(e.inversion()), "Inversión total"}, {eurRedondeado((e.kwhDirecta() + e.kwhBateria()) * e.precioKwh()), "Ahorro en energía el año 1"},
                {Double.isNaN(f.retorno()) ? "—" : n(f.retorno(), 1) + " años", "En recuperar la inversión"}, {Double.isNaN(f.tir()) ? "—" : n(f.tir() * 100, 1) + " %", "Rentabilidad anual (TIR)"}});
        h.append("<table class=\"t\"><tr><th>Concepto</th><th>Importe</th></tr>");
        fila("Inversión total (" + n(c.instalacion.kwp(), 1) + " kWp " + (c.conPresupuesto() ? "según el presupuesto, a " + n(c.precioWp, 2) + " €/Wp"
                : "a " + n(c.precioWp, 2) + " €/Wp" + (bateria ? " y " + n(c.instalacion.bateriaKwh(), 0) + " kWh de batería" : "")) + ")", eur(e.inversion()));
        fila("Energía que deja de comprar", n(s.solarConsumida(), 0) + " kWh/año");
        fila("Ahorro en energía (a " + n(c.precioEnergia, 3).replaceAll("0$", "") + " €/kWh)", eur(s.solarConsumida() * c.precioEnergia));
        if (c.ahorroPotencia > 0) fila(c.potenciaCalculada ? "Ahorro en potencia (kW que se reducen, a precio BOE «" + esc(c.regulados.nombre()) + "»)" : "Ahorro en potencia", eur(c.ahorroPotencia));
        fila("Mantenimiento y seguro", "−" + eur(e.mantenimiento()) + "/año");
        fila("<b>Ahorro del año 1 (energía y potencia, menos el mantenimiento)</b>", "<b>" + eur(f.ahorroPrimerAno()) + "</b>");
        h.append("</table>");
        if (c.precioPeriodo != null) {
            double[] sol = s.solarPeriodo();
            int periodos = sol != null && c.regulados != null ? Periodos.periodosEnergia(c.regulados.tarifa()) : c.precioPeriodo[3] == 0 ? 3 : 6;
            h.append("<p class=\"nota\">Precio de la energía por periodo (€/kWh) y energía solar consumida en cada uno: ");
            for (int p = 0; p < periodos; p++) {
                if (p > 0) h.append(" · ");
                h.append("P").append(p + 1).append(" ").append(n(c.precioPeriodo[p], 4));
                if (sol != null) h.append(" (").append(mwh(sol[p])).append(" MWh)");
            }
            h.append(". ").append(c.regulados != null && !Boolean.TRUE.equals(Calculo.mapa(c.datos.get("economia")).get("incluyePeajes"))
                    ? "Incluye los peajes y cargos del BOE «" + esc(c.regulados.nombre()) + "» (" + esc(c.regulados.referencia()) + "). " : "")
                    .append("Importes sin IVA ni impuesto eléctrico; la inversión y el mantenimiento son estimaciones que se confirman en la oferta.</p>");
        } else {
            h.append("<p class=\"nota\">Importes sin IVA. El precio de la energía es un valor medio prudente; la inversión y el mantenimiento son estimaciones que se confirman en la oferta.</p>");
        }
    }

    private void pagRentabilidad() {
        abrirPagina();
        Finanzas.Entrada e = c.base.entrada();
        Finanzas.Resultado f = c.base.fin();
        h.append("<h3>El dinero acumulado año a año: ").append(Double.isNaN(f.retorno()) ? "la inversión no llega a recuperarse en 25 años" : "la inversión se recupera en unos " + n(f.retorno(), 1) + " años").append("</h3>");
        h.append(acumulado(f.acumulado()));
        h.append("<p class=\"nota\">Balance acumulado (€) de cada año: ahorro menos mantenimiento, menos la inversión inicial. A 25 años ronda los ")
                .append(eurRedondeado(f.acumulado()[f.acumulado().length - 1])).append(".</p>");
        contIndicadores();
        cerrarPagina();
    }

    private void contIndicadores() {
        Finanzas.Entrada e = c.base.entrada();
        Finanzas.Resultado f = c.base.fin();
        h.append("<table class=\"t\"><tr><th>Indicador</th><th>Valor</th><th>Qué significa</th></tr>");
        h.append("<tr><td>Retorno de la inversión</td><td>").append(Double.isNaN(f.retorno()) ? "—" : n(f.retorno(), 1) + " años").append("</td><td>Lo que tarda en pagarse sola</td></tr>");
        h.append("<tr><td>Retorno descontado</td><td>").append(Double.isNaN(f.retornoDescontado()) ? "—" : n(f.retornoDescontado(), 1) + " años").append("</td><td>Contando el valor del dinero en el tiempo</td></tr>");
        h.append("<tr><td>VAN a ").append(e.anos()).append(" años</td><td>").append(eurRedondeado(f.van())).append("</td><td>Lo que gana por encima de invertir el dinero al ").append(n(e.descuento() * 100, 0)).append(" %</td></tr>");
        h.append("<tr><td>TIR</td><td>").append(Double.isNaN(f.tir()) ? "—" : n(f.tir() * 100, 1) + " %").append("</td><td>Rentabilidad anual del capital invertido</td></tr>");
        h.append("<tr><td>Ahorro acumulado</td><td>").append(eurRedondeado(f.ahorroAcumulado())).append("</td><td>Suma de los ").append(e.anos()).append(" años</td></tr></table>");
        h.append("<h3>Si la energía vale más, la instalación se paga antes</h3><table class=\"t\"><tr><th>Precio medio de la energía</th><th>Ahorro año 1 (menos mantenimiento)</th><th>Retorno</th><th>TIR</th></tr>");
        for (Calculo.Punto p : c.precios) {
            h.append("<tr><td>").append(n(p.entrada().precioKwh(), 3).replaceAll("0$", "")).append(" €/kWh</td><td>").append(eurRedondeado(p.fin().ahorroPrimerAno())).append("</td><td>")
                    .append(Double.isNaN(p.fin().retorno()) ? "—" : n(p.fin().retorno(), 1) + " años").append("</td><td>")
                    .append(Double.isNaN(p.fin().tir()) ? "—" : n(p.fin().tir() * 100, 1) + " %").append("</td></tr>");
        }
        h.append("</table><p class=\"nota\">Cálculo: energía solar consumida por el precio ").append(c.precioPeriodo != null ? "de su periodo" : "medio de la energía").append(c.ahorroPotencia > 0 ? ", más el ahorro en potencia (sin subida anual)" : "")
                .append(". Subida del precio de la energía del ").append(n(e.subidaEnergia() * 100, 0)).append(" % anual, degradación de los módulos del ").append(n(e.degradacion() * 100, 1))
                .append(" % anual").append(e.degradacionBateria() > 0 ? " y de la batería del " + n(e.degradacionBateria() * 100, 0) + " % anual" : "")
                .append(", mantenimiento y seguro de ").append(eur(e.mantenimiento())).append(" al año (+").append(n(e.subidaMantenimiento() * 100, 0)).append(" % anual)")
                .append(e.reposicion() > 0 ? ", cambio de equipos en el año " + e.anoReposicion() + " (" + eurRedondeado(e.reposicion()) + ")" : "")
                .append(", tasa de descuento del ").append(n(e.descuento() * 100, 0)).append(" % y horizonte de ").append(e.anos()).append(" años. Importes sin IVA.</p>");
    }

    private void pagEscenarios() {
        abrirPagina();
        contEscenarios(true);
        cierre();
        cerrarPagina();
    }

    private void contEscenarios(boolean titulo2) {
        h.append(titulo2 ? "<h2><i>6</i>Escenarios de potencia</h2>" : "<h3>Escenarios de potencia</h3>");
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
        escRow("Ahorro año 1 (menos mantenimiento)", i -> eurRedondeado(e.get(i).fin().ahorroPrimerAno()));
        escRow("Retorno", i -> Double.isNaN(e.get(i).fin().retorno()) ? "—" : n(e.get(i).fin().retorno(), 1) + " años");
        escRow("TIR", i -> Double.isNaN(e.get(i).fin().tir()) ? "—" : n(e.get(i).fin().tir() * 100, 1) + " %");
        escRow("VAN a 25 años", i -> eurRedondeado(e.get(i).fin().van()));
        h.append("</table><p class=\"nota\">VAN: beneficio a 25 años por encima de invertir el dinero al 5 %. La inversión y el mantenimiento de cada escenario se escalan con la potencia; ")
                .append(bateria ? "la batería es la misma en todos." : "no hay batería.").append("</p>");
    }

    private void escRow(String nombre, java.util.function.IntFunction<String> v) {
        h.append("<tr><td>").append(nombre).append("</td>");
        for (int i = 0; i < c.escenarios.size(); i++) h.append("<td").append(i == Calculo.INDICE_BASE ? " class=\"base\"" : "").append('>').append(v.apply(i)).append("</td>");
        h.append("</tr>");
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ informe por módulos

    /** Categorías del informe por módulos, en su orden por defecto. */
    public static final Map<String, String> CATEGORIAS = new java.util.LinkedHashMap<>();
    /** Módulo → categoría a la que pertenece. */
    public static final Map<String, String> CATEGORIA_DE = new java.util.LinkedHashMap<>();
    /** Módulos que el servidor escribe entero (tablas); el resto son gráficas que llegan dibujadas por la pantalla. */
    public static final java.util.Set<String> TABLAS = java.util.Set.of("tabla-instalacion", "tabla-escenarios", "tabla-inversion", "tabla-indicadores");

    static {
        CATEGORIAS.put("consumo", "Consumo");
        CATEGORIAS.put("solar", "Producción solar");
        CATEGORIAS.put("dimension", "Dimensionado");
        CATEGORIAS.put("potencia", "Potencia contratada");
        CATEGORIAS.put("rentabilidad", "Rentabilidad");
        CATEGORIAS.put("financiacion", "Financiación");
        CATEGORIAS.put(Informe.RECOMENDACIONES, "Recomendaciones");
        for (String[] m : new String[][]{{"consumo-periodos", "consumo"}, {"consumo-perfil", "consumo"}, {"solar-dia", "solar"}, {"solar-mensual", "solar"},
                {"tabla-instalacion", "solar"}, {"dim-potencia", "dimension"}, {"dim-cobertura", "dimension"}, {"dim-bateria", "dimension"}, {"dim-tir-bateria", "dimension"},
                {"dim-cobertura-bateria", "dimension"}, {"tabla-escenarios", "dimension"}, {"potencia-contratada", "potencia"}, {"rent-acumulado", "rentabilidad"},
                {"rent-van", "rentabilidad"}, {"tabla-inversion", "rentabilidad"}, {"tabla-indicadores", "rentabilidad"}, {"fin-balance", "financiacion"}}) {
            CATEGORIA_DE.put(m[0], m[1]);
        }
    }

    /** Categoría de textos libres (recomendaciones): sus módulos no son gráficas, llevan título y texto. */
    public static final String RECOMENDACIONES = "recomendaciones";
    private static final java.util.regex.Pattern ID_RECOMENDACION = java.util.regex.Pattern.compile("rec-[0-9A-Za-z_-]{1,40}");
    private static final int TEXTO_MAX = 6000, TITULO_MAX = 255;

    /**
     * Lo que el usuario eligió: categorías en su orden y, en cada una, sus módulos en su orden. Las gráficas llegan con su SVG;
     * las recomendaciones, con su título y su texto (negrita, cursiva, listas y párrafos).
     */
    public record ModuloSel(String id, List<String> svgs, String titulo, String texto) {
        public ModuloSel(String id, List<String> svgs) {
            this(id, svgs, null, null);
        }
    }

    public record CategoriaSel(String id, List<ModuloSel> modulos) {
    }

    /** El informe con solo los módulos elegidos y en el orden elegido; la portada con el resumen va siempre. */
    public static String generar(Calculo c, LocalDate hoy, List<CategoriaSel> seleccion) {
        return new Informe(c, fechaLarga(hoy)).construirPorModulos(seleccion);
    }

    private String construirPorModulos(List<CategoriaSel> seleccion) {
        h.append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"><title>Estudio FV · ").append(esc(cliente)).append("</title><style>")
                .append(CSS).append("</style></head><body>");
        pagResumen();
        int numero = 2;
        // Los bloques van en un flujo continuo y la propia página los reparte en hojas midiendo su altura real (script de PAGINAR)
        h.append("<div id=\"flujo\">");
        boolean hayContenido = false;
        for (CategoriaSel cat : seleccion) {
            String nombre = CATEGORIAS.get(cat.id());
            if (nombre == null) continue;
            List<ModuloSel> validos = new java.util.ArrayList<>();
            for (ModuloSel m : cat.modulos()) {
                if (RECOMENDACIONES.equals(cat.id())) {
                    if (m.id() != null && ID_RECOMENDACION.matcher(m.id()).matches() && m.texto() != null && !m.texto().isBlank()) validos.add(m);
                    continue;
                }
                if (!cat.id().equals(CATEGORIA_DE.get(m.id()))) continue;
                if (!TABLAS.contains(m.id()) && (m.svgs() == null || m.svgs().isEmpty())) continue;
                validos.add(m);
            }
            if (validos.isEmpty()) continue;
            numero++;
            hayContenido = true;
            h.append("<h2 class=\"cat\"><i>").append(numero).append("</i>").append(esc(nombre)).append("</h2>");
            for (ModuloSel m : validos) escribirModulo(m);
        }
        if (hayContenido) cierre();
        h.append("</div><script id=\"paginar\">").append(PAGINAR).append("</script></body></html>");
        return h.toString().replace("@@TOTAL@@", String.valueOf(pagina));
    }

    private void cierre() {
        h.append("<div class=\"cierre\"><b>AURA Ingeniería Energética S.L.</b> · auraingenieria.es<br>Estudio orientativo: las cifras definitivas se fijan en la oferta tras la visita técnica.</div>");
    }

    /** Una recomendación: su título y su texto, con el formato sencillo de la biblioteca. */
    private void escribirRecomendacion(ModuloSel m) {
        String titulo = m.titulo() == null || m.titulo().isBlank() ? "Recomendación" : m.titulo().strip();
        if (titulo.length() > TITULO_MAX) titulo = titulo.substring(0, TITULO_MAX);
        String texto = m.texto().strip();
        if (texto.length() > TEXTO_MAX) texto = texto.substring(0, TEXTO_MAX);
        h.append("<div class=\"modulo recomendacion\"><h3>").append(esc(titulo)).append("</h3>").append(textoEnHtml(texto)).append("</div>");
    }

    /** **negrita**, *cursiva*, listas con «- » y párrafos separados por una línea en blanco; todo lo demás se escribe tal cual (escapado). */
    static String textoEnHtml(String texto) {
        StringBuilder r = new StringBuilder();
        boolean lista = false;
        StringBuilder parrafo = new StringBuilder();
        for (String linea : (texto + "\n").replace("\r", "").split("\n", -1)) {
            String l = linea.strip();
            boolean item = l.startsWith("- ") || l.startsWith("* ") && !l.startsWith("**");
            if (!item && !l.isEmpty() && lista) {
                r.append("</ul>");
                lista = false;
            }
            if (l.isEmpty() || item) {
                if (parrafo.length() > 0) {
                    r.append("<p>").append(formatoEnLinea(parrafo.toString())).append("</p>");
                    parrafo.setLength(0);
                }
                if (l.isEmpty() && lista) {
                    r.append("</ul>");
                    lista = false;
                }
            }
            if (item) {
                if (!lista) {
                    r.append("<ul>");
                    lista = true;
                }
                r.append("<li>").append(formatoEnLinea(l.substring(2).strip())).append("</li>");
            } else if (!l.isEmpty()) {
                if (parrafo.length() > 0) parrafo.append(' ');
                parrafo.append(l);
            }
        }
        if (lista) r.append("</ul>");
        return r.toString();
    }

    private static String formatoEnLinea(String s) {
        String e = esc(s);
        e = e.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        return e.replaceAll("(?<![*\\w])\\*([^*\\s](?:[^*]*[^*\\s])?)\\*(?![*\\w])", "<i>$1</i>");
    }

    /** Una tabla con sus títulos en un solo bloque, para que el reparto en hojas no los separe. */
    private void tabla(Runnable contenido) {
        h.append("<div class=\"modulo\">");
        contenido.run();
        h.append("</div>");
    }

    private void escribirModulo(ModuloSel m) {
        if (m.id() != null && ID_RECOMENDACION.matcher(m.id()).matches()) {
            escribirRecomendacion(m);
            return;
        }
        switch (m.id()) {
            case "tabla-instalacion" -> tabla(() -> contInstalacion(false));
            case "tabla-escenarios" -> tabla(() -> contEscenarios(false));
            case "tabla-inversion" -> tabla(() -> contInversion(false));
            case "tabla-indicadores" -> tabla(this::contIndicadores);
            default -> {
                String[] t = textoModulo(m.id());
                List<String> limpios = new java.util.ArrayList<>();
                for (String svg : m.svgs()) {
                    String l = SvgSeguro.limpiar(svg);
                    if (l != null) limpios.add(l);
                }
                if (limpios.isEmpty()) return;
                h.append("<div class=\"modulo\"><h3>").append(esc(t[0])).append("</h3>");
                if (limpios.size() > 1) {
                    h.append("<div class=\"dos\">");
                    for (String l : limpios) h.append("<div>").append(l).append("</div>");
                    h.append("</div>");
                } else {
                    h.append(limpios.get(0));
                }
                h.append("<p class=\"nota\">").append(esc(t[1])).append("</p></div>");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lista(String clave) {
        return c.graficas().get(clave) instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapaG(String clave) {
        return c.graficas().get(clave) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static double dd(Map<String, Object> m, String clave) {
        return m.get(clave) instanceof Number x ? x.doubleValue() : 0;
    }

    private static String sinCeros(double v) {
        return n(v, 3).replaceAll("0$", "");
    }

    /** Titular y nota de cada gráfica, con los números del estudio. */
    @SuppressWarnings("unchecked")
    private String[] textoModulo(String id) {
        Simulacion.Resultado s = c.base.sim();
        Finanzas.Resultado f = c.base.fin();
        return switch (id) {
            case "consumo-periodos" -> {
                double[] m = c.consumoMes;
                int max1 = 0, max2 = -1, min1 = 0, min2 = -1;
                for (int i = 1; i < 12; i++) if (m[i] > m[max1]) max1 = i;
                for (int i = 0; i < 12; i++) if (i != max1 && (max2 < 0 || m[i] > m[max2])) max2 = i;
                for (int i = 1; i < 12; i++) if (m[i] < m[min1]) min1 = i;
                for (int i = 0; i < 12; i++) if (i != min1 && (min2 < 0 || m[i] < m[min2])) min2 = i;
                yield new String[]{cap(MESES_LARGOS[max1]) + " y " + MESES_LARGOS[max2] + " son los meses de más consumo; " + MESES_LARGOS[min1] + " y " + MESES_LARGOS[min2] + ", los de menos",
                        (c.graficas().get("consumoPeriodoMes") != null ? "Consumo mensual por periodo tarifario (MWh). " : "Consumo mensual (MWh). ")
                                + (c.curva.estimada() ? "Estimado con las lecturas del SIPS." : "Curva horaria del suministro, último año completo.")};
            }
            case "consumo-perfil" -> {
                double[] lab = c.perfilLaborable, fin = c.perfilFinde;
                int hmax = 0;
                for (int i = 1; i < 24; i++) if (lab[i] > lab[hmax]) hmax = i;
                double finMax = 0;
                for (double v : fin) finMax = Math.max(finMax, v);
                yield new String[]{"Los días laborables el consumo llega a unos " + n(lab[hmax], 0) + " kW hacia las " + hmax + " h; el fin de semana no pasa de " + n(finMax, 0) + " kW",
                        "Potencia media por hora del día (kW), de lunes a viernes, sábados y domingos."};
            }
            case "solar-dia" -> new String[]{"Un día laborable, el consumo y la producción solar a lo largo del día",
                    "Consumo y aportación solar (kW) en un día laborable medio de junio y de diciembre. La producción máxima ronda los " + n(s.produccionMaxima(), 0)
                            + " kW. Hipótesis: producción específica de " + n(c.instalacion.produccionEspecifica(), 0) + " kWh/kWp al año con perfil medio mensual y horario; día medio de cada mes, sin variación meteorológica diaria"
                            + (bateria ? "; batería con rendimiento del 90 % ida y vuelta, descarga útil del 90 % y potencia de carga/descarga de C/2." : ".")};
            case "solar-mensual" -> new String[]{"Se aprovecha el " + pct(s.aprovechamiento()) + " de la producción; lo que se recorta se concentra en los meses y días de menos consumo",
                    "Energía solar consumida (al momento y desde la batería) y recortada por mes, frente al consumo (MWh). Encima de cada mes, el porcentaje de la producción que se aprovecha."};
            case "dim-potencia" -> {
                Calculo.Punto mas = c.barrido.get(c.barrido.size() - 1), antes = c.barrido.get(2), centro = c.base;
                yield new String[]{"Con " + n(centro.kwp(), 0) + " kWp se cubre el " + pct(centro.sim().cobertura()) + " del consumo; cada tramo de paneles extra rinde menos",
                        "Energía solar (MWh al año) con cada potencia de paneles. Pasar de " + n(antes.kwp(), 0) + " a " + n(centro.kwp(), 0) + " kWp sube la cobertura del " + pct(antes.sim().cobertura())
                                + " al " + pct(centro.sim().cobertura()) + "; de " + n(centro.kwp(), 0) + " a " + n(mas.kwp(), 0) + " kWp solo la sube hasta el " + pct(mas.sim().cobertura())
                                + " y la producción aprovechada baja del " + pct(centro.sim().aprovechamiento()) + " al " + pct(mas.sim().aprovechamiento()) + "."};
            }
            case "dim-cobertura" -> new String[]{"Con más paneles se cubre más consumo, pero se aprovecha menos de lo que se produce",
                    "Porcentaje del consumo que se cubre y de la producción que se aprovecha con cada potencia de paneles (kWp)."};
            case "dim-bateria" -> {
                List<Map<String, Object>> b = lista("barridoBateria");
                Map<String, Object> sin = b.get(0), act = b.stream().filter(x -> Boolean.TRUE.equals(x.get("actual"))).findFirst().orElse(b.get(0));
                Map<String, Object> ultimo = b.get(b.size() - 1);
                String titular = bateria
                        ? "Con " + n(dd(act, "kwh"), 0) + " kWh de batería la cobertura sube " + n((dd(act, "cobertura") - dd(sin, "cobertura")) * 100, 1) + " puntos frente a no tenerla"
                        : "Con " + n(dd(ultimo, "kwh"), 0) + " kWh de batería la cobertura pasaría del " + pct(dd(sin, "cobertura")) + " al " + pct(dd(ultimo, "cobertura"));
                yield new String[]{titular, "Energía solar (MWh al año) según la capacidad de batería. Encima de cada barra, el consumo cubierto; debajo, los ciclos de batería al año."};
            }
            case "dim-tir-bateria" -> {
                List<Map<String, Object>> b = lista("barridoBateria");
                Map<String, Object> sin = b.get(0), act = b.stream().filter(x -> Boolean.TRUE.equals(x.get("actual"))).findFirst().orElse(b.get(0));
                yield new String[]{"La rentabilidad (TIR) es del " + n(dd(sin, "tir") * 100, 1) + " % sin batería y del " + n(dd(act, "tir") * 100, 1) + " % con " + n(dd(act, "kwh"), 0) + " kWh",
                        "TIR a 25 años con cada capacidad de batería; la inversión de cada una se calcula con el precio de la batería indicado."};
            }
            case "dim-cobertura-bateria" -> {
                List<Map<String, Object>> b = lista("barridoBateria");
                Map<String, Object> sin = b.get(0), act = b.stream().filter(x -> Boolean.TRUE.equals(x.get("actual"))).findFirst().orElse(b.get(0)), ultimo = b.get(b.size() - 1);
                String titular = bateria
                        ? "Los primeros " + n(dd(act, "kwh"), 0) + " kWh de batería suben la cobertura " + n((dd(act, "cobertura") - dd(sin, "cobertura")) * 100, 1) + " puntos; los " + n(dd(ultimo, "kwh") - dd(act, "kwh"), 0) + " siguientes solo " + n((dd(ultimo, "cobertura") - dd(act, "cobertura")) * 100, 1)
                        : "Con " + n(dd(ultimo, "kwh"), 0) + " kWh de batería la cobertura subiría " + n((dd(ultimo, "cobertura") - dd(sin, "cobertura")) * 100, 1) + " puntos";
                yield new String[]{titular, "Consumo cubierto (%) según la capacidad de batería instalada."};
            }
            case "potencia-contratada" -> {
                Map<String, Object> p = mapaG("potencia");
                double ahorro = 0, kw = 0;
                if (p.get("ahorro") instanceof List<?> l) for (Object o : l) ahorro += o instanceof Number x ? x.doubleValue() : 0;
                if (p.get("hoy") instanceof List<?> hoy && p.get("aContratar") instanceof List<?> ac) {
                    for (int i = 0; i < hoy.size(); i++) kw += ((Number) hoy.get(i)).doubleValue() - ((Number) ac.get(i)).doubleValue();
                }
                yield new String[]{kw > 0 ? "Una bajada de unos " + n(kw, 0) + " kW de potencia contratada ahorra unos " + eurRedondeado(ahorro) + " al año" : "Potencia contratada y punta registrada en cada periodo",
                        "Potencia contratada hoy (o punta registrada), punta registrada, potencia que se propone contratar y punta que quedaría con la instalación en cada periodo (kW), con el precio del peaje y cargo del BOE (€/kW·año)."};
            }
            case "rent-acumulado" -> new String[]{"El dinero acumulado año a año: " + (Double.isNaN(f.retorno()) ? "la inversión no llega a recuperarse en 25 años" : "la inversión se recupera en unos " + n(f.retorno(), 1) + " años"),
                    "Balance acumulado (miles de €) de cada año: ahorro menos mantenimiento, menos la inversión inicial. A 25 años ronda los " + eurRedondeado(f.acumulado()[f.acumulado().length - 1]) + "."};
            case "rent-van" -> {
                List<Map<String, Object>> items = (List<Map<String, Object>>) mapaG("escenariosVan").get("items");
                Map<String, Object> mejor = items.get(0);
                for (Map<String, Object> x : items) if (dd(x, "van") > dd(mejor, "van")) mejor = x;
                yield new String[]{"El beneficio a 25 años es mayor con " + n(dd(mejor, "kwp"), 0) + " kWp (" + eurRedondeado(dd(mejor, "van")) + ")",
                        "VAN a 25 años: beneficio por encima de invertir el dinero al " + n(c.base.entrada().descuento() * 100, 0) + " %, con la energía a " + sinCeros(dd(mapaG("escenariosVan"), "precio"))
                                + " y a " + sinCeros(dd(mapaG("escenariosVan"), "precioAlto")) + " €/kWh."};
            }
            case "fin-balance" -> {
                Map<String, Object> x = mapaG("financiacion");
                Object rec = x.get("recuperaConstante");
                yield new String[]{"El cliente pone unos " + eurRedondeado(dd(x, "puestoConstante")) + " en " + n(dd(x, "meses"), 0) + " cuotas y " + (rec instanceof Number r ? "los recupera en el mes " + r.intValue() : "no los recupera en 25 años"),
                        "Balance acumulado del cliente (miles de €): ahorro menos cuotas. Cuota de " + eur(dd(x, "cuota")) + " al mes con un interés (TAE) del " + n(dd(x, "tae"), 1) + " %. Durante las cuotas el balance baja; después sube con todo el ahorro."};
            }
            default -> new String[]{"", ""};
        };
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

    /**
     * Reparte el flujo de bloques en hojas A4: añade cada bloque a la hoja actual y, si se pasa de alto, lo pasa a una hoja nueva
     * (con su título de categoría si se había quedado solo al final). Numera los pies con el total real.
     */
    private static final String PAGINAR = """
            (function () {
              var flujo = document.getElementById('flujo');
              if (!flujo) return;
              var primera = document.querySelector('section.pag');
              var cabecera = primera.querySelector('header');
              var base = 'AURA Ingeniería Energética · auraingenieria.es · ';
              function hoja() {
                var s = document.createElement('section');
                s.className = 'pag';
                s.appendChild(cabecera.cloneNode(true));
                var m = document.createElement('main');
                s.appendChild(m);
                s.appendChild(document.createElement('footer'));
                document.body.insertBefore(s, flujo);
                return m;
              }
              var main = primera.querySelector('main');
              Array.prototype.slice.call(flujo.children).forEach(function (el) {
                main.appendChild(el);
                if (main.scrollHeight <= main.clientHeight + 1 || main.children.length < 2) return;
                var mover = [el];
                var antes = el.previousElementSibling;
                if (antes && antes.classList.contains('cat') && main.children.length > 2) mover.unshift(antes);
                main = hoja();
                mover.forEach(function (x) { main.appendChild(x); });
              });
              var hojas = document.querySelectorAll('section.pag');
              Array.prototype.forEach.call(hojas, function (s, i) {
                s.querySelector('footer').textContent = base + (i + 1) + '/' + hojas.length;
              });
              flujo.parentNode.removeChild(flujo);
            })();
            """;

    private static final String CSS = """
            @page { size: A4; margin: 0 }
            * { box-sizing: border-box }
            body { margin: 0; background: #fff; color: #2c2c2e; font: 12.5px/1.5 -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; -webkit-print-color-adjust: exact; print-color-adjust: exact }
            .pag { width: 210mm; height: 297mm; padding: 14mm 15mm 12mm; position: relative; page-break-after: always; overflow: hidden; display: flex; flex-direction: column }
            .pag:last-of-type { page-break-after: auto }
            @media screen { body { background: #d9dde3; padding: 16px 0 }
              .pag { margin: 0 auto 16px; background: #fff; box-shadow: 0 2px 14px rgba(0, 0, 0, .3) } }
            @media screen and (max-width: 860px) { body { zoom: .8 } }
            @media screen and (max-width: 700px) { body { zoom: .62 } }
            header { display: flex; justify-content: space-between; align-items: center; border-bottom: 3px solid #fcd700; padding-bottom: 8px; margin-bottom: 14px }
            header .logo { height: 34px }
            header span { font-size: 9.5px; letter-spacing: .08em; color: #98999b; font-weight: 600 }
            main { flex: 1; min-height: 0; overflow: hidden }
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
            .grafica-pdf { --text: #2c2c2e; --text-h: #1c1c1e; --muted: #6b6b6e; --bg: #fff; --bg-alt: #f5f5f6; --brand: #fcd700; --border: #e3e3e6 }
            .grafica-pdf .eje line { stroke: #e3e3e6; stroke-width: 1 } .grafica-pdf .eje line.cero { stroke: #98999b }
            .grafica-pdf .eje text { fill: #6b6b6e; font-size: 11px } .grafica-pdf .eje .pequeno { font-size: 9.5px }
            .grafica-pdf .nota { fill: #6b6b6e; font-size: 12px } .grafica-pdf .banda-fija { fill: #000; opacity: .05 }
            .grafica-pdf .ref line { stroke: #2c2c2e; stroke-dasharray: 5 4; opacity: .7 } .grafica-pdf .ref text { fill: #2c2c2e; font-size: 11.5px; font-weight: 600 }
            .grafica-pdf .marca text { fill: #1c1c1e; font-size: 12px; font-weight: 600; paint-order: stroke; stroke: #fff; stroke-width: 5px; stroke-linejoin: round }
            .grafica-pdf .valor-seg { font-size: 9.5px; font-weight: 600 } .grafica-pdf .valor-seg.chico { font-size: 8px }
            .grafica-pdf .valor-seg.sobre-trama { paint-order: stroke; stroke: #fff; stroke-width: 2.5px; stroke-linejoin: round; stroke-opacity: .85 }
            .grafica-pdf .valor-barra { fill: #2c2c2e; font-size: 10.5px; font-weight: 600 } .grafica-pdf .etiqueta { fill: #1c1c1e; font-size: 11.5px; font-weight: 700 }
            .grafica-pdf .leyenda-svg text { fill: #4a4a4c; font-size: 11px }
            .modulo { margin-bottom: 6px; break-inside: avoid } .modulo svg { width: 100%; height: auto; display: block }
            .recomendacion { margin-bottom: 12px } .recomendacion h3 { margin-top: 8px } .recomendacion ul { margin: 6px 0; padding-left: 20px } .recomendacion li { margin: 3px 0 }
            #flujo { display: none } main > :first-child { margin-top: 0 }
            .cierre { margin-top: 18px; padding: 10px 12px; background: #f5f5f6; border-left: 4px solid #fcd700; font-size: 11px }
            """;
}
