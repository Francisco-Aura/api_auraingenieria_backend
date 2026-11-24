package api.aura.service.modulo_planificador.estudio;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Informe por módulos: qué se incluye, en qué orden, y que el SVG que llega del navegador se limpia. */
class InformeModulosTest {

    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 50\" class=\"estudio-svg grafica-pdf\"><rect x=\"1\" y=\"2\" width=\"10\" height=\"5\" fill=\"rgb(1,2,3)\"/>"
            + "<text x=\"5\" y=\"5\">Hola &amp; adiós</text></svg>";

    private static Calculo calculo() {
        double[] k = new double[Curva.HORAS];
        for (int i = 0; i < Curva.HORAS; i++) k[i] = Curva.laborable(i) && Curva.hora(i) >= 8 && Curva.hora(i) < 18 ? 40 : 3;
        Map<String, Object> ins = new HashMap<>(Map.of("kwp", 100, "bateriaKwh", 80, "orientacion", "sur"));
        Map<String, Object> d = new HashMap<>();
        d.put("instalacion", ins);
        d.put("economia", new HashMap<>(Map.of("precioEnergia", 0.1, "financiacionMeses", 36)));
        d.put("cliente", "Cliente de prueba");
        return Calculo.de(d, new Curva(k, false, "prueba"));
    }

    private static String textoOrientacion(InclinacionOptima.Resultado optima) {
        return RecomendacionesGeneradas.de(calculo(), optima).stream().filter(r -> "orientacion".equals(r.id())).findFirst().orElseThrow().texto();
    }

    @Test
    void laRecomendacionDeOrientacionDiceCuantoSePierdeRespectoALaOptima() {
        String t = textoOrientacion(new InclinacionOptima.Resultado(15, 1500, 30, 1650, 6));
        assertTrue(t.contains("**15°**") && t.contains("**30°**"), t);
        assertTrue(t.contains("10,0 % más"), t);
        assertTrue(t.contains("«Otra»"), "al sur se explica cómo usarla");
        String cerca = textoOrientacion(new InclinacionOptima.Resultado(15, 1500, 15, 1500, 3));
        assertTrue(cerca.contains("prácticamente lo máximo") && !cerca.contains("más**"), cerca);
        assertFalse(textoOrientacion(null).contains("Inclinación."), "sin comparación no se dice nada");
    }

    @Test
    void elSvgSoloConservaElDibujo() {
        assertNotNull(SvgSeguro.limpiar(SVG));
        assertTrue(SvgSeguro.limpiar(SVG).contains("Hola &amp; adiós"));
        String malo = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"><script>alert(1)</script><style>@import url(http://x)</style>"
                + "<foreignObject><iframe src=\"http://x\"/></foreignObject><image href=\"http://x/a.png\"/>"
                + "<rect x=\"1\" fill=\"url(http://x/a)\" onclick=\"x()\" style=\"background:url(x)\" width=\"2\"/><path d=\"M0 0 L1 1\"/></svg>";
        String limpio = SvgSeguro.limpiar(malo);
        assertNotNull(limpio);
        for (String prohibido : List.of("script", "onload", "onclick", "style", "image", "iframe", "foreignObject", "http://x", "@import"))
            assertFalse(limpio.contains(prohibido), prohibido + " en " + limpio);
        assertTrue(limpio.contains("<path d=\"M0 0 L1 1\"></path>"));
        assertNull(SvgSeguro.limpiar("<!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><svg>&x;</svg>"));
        assertNull(SvgSeguro.limpiar("<html><body/></html>"));
        assertNull(SvgSeguro.limpiar("no es xml"));
        assertNull(SvgSeguro.limpiar("<svg>" + "a".repeat(SvgSeguro.MAX_CARACTERES) + "</svg>"));
    }

    @Test
    void lasBarrasConEsquinasRedondeadasPasanElFiltro() {
        String barra = "M10,20H30A1.5,1.5 0 0 1 31.5,21.5V60H10V20Z";
        String limpio = SvgSeguro.limpiar("<svg viewBox=\"0 0 100 100\"><path d=\"" + barra + "\" fill=\"#fcd700\" stroke=\"none\"/></svg>");
        assertNotNull(limpio);
        assertTrue(limpio.contains("d=\"" + barra + "\""), "el trazado de la barra no se descarta");
        assertFalse(SvgSeguro.limpiar("<svg><path d=\"M0,0 javascript:alert(1)\"/></svg>").contains("javascript"));
    }

    @Test
    void elInformeLlevaSoloLoElegidoYEnElOrdenElegido() {
        List<Informe.CategoriaSel> sel = List.of(
                new Informe.CategoriaSel("rentabilidad", List.of(new Informe.ModuloSel("tabla-indicadores", List.of()), new Informe.ModuloSel("rent-acumulado", List.of(SVG)))),
                new Informe.CategoriaSel("consumo", List.of(new Informe.ModuloSel("consumo-perfil", List.of(SVG)))));
        String html = Informe.generar(calculo(), LocalDate.of(2026, 11, 16), sel);
        assertTrue(html.contains("Resumen"), "la portada va siempre");
        int rent = html.indexOf("</i>Rentabilidad</h2>"), cons = html.indexOf("</i>Consumo</h2>");
        assertTrue(rent > 0 && cons > rent, "las categorías van en el orden pedido");
        assertTrue(html.indexOf("Indicador</th>") < html.indexOf("El dinero acumulado"), "y los módulos dentro de cada una también");
        assertFalse(html.contains("Escenarios de potencia"), "lo no elegido no sale");
        assertFalse(html.contains("Instalación propuesta"));
        assertTrue(html.contains("Hola &amp; adiós"));
        assertFalse(html.contains("@@TOTAL@@"));
        assertTrue(html.contains("/" + (html.split("class=\"pag\"", -1).length - 1) + "</footer>"), "el pie lleva el total de páginas");
    }

    @Test
    void sePuedenElegirTodosLosModulosAunqueFalteAlgo() {
        // Un módulo de otra categoría, desconocido o sin gráfica se ignora sin romper el informe
        List<Informe.CategoriaSel> sel = List.of(
                new Informe.CategoriaSel("solar", List.of(new Informe.ModuloSel("consumo-perfil", List.of(SVG)), new Informe.ModuloSel("no-existe", List.of(SVG)),
                        new Informe.ModuloSel("solar-mensual", List.of()), new Informe.ModuloSel("tabla-instalacion", List.of()))),
                new Informe.CategoriaSel("inventada", List.of(new Informe.ModuloSel("rent-van", List.of(SVG)))),
                new Informe.CategoriaSel("financiacion", List.of(new Informe.ModuloSel("fin-balance", List.of(SVG, SVG)))));
        String html = Informe.generar(calculo(), LocalDate.of(2026, 11, 16), sel);
        assertTrue(html.contains("Instalación propuesta"));
        assertTrue(html.contains("</i>Financiación</h2>"));
        assertFalse(html.contains("</i>Consumo</h2>"));
        assertFalse(html.contains("Cómo cambia"));
        assertTrue(html.contains("class=\"dos\""), "un módulo con dos gráficas va en dos columnas");
        assertTrue(html.contains("cuotas"), "el titular de la financiación lleva los números del estudio");
    }

    @Test
    void lasRecomendacionesSalenComoTextoConFormatoYSinInyectarHtml() {
        String texto = "**Análisis horario.** Con la curva se ajusta *a las horas* solares <script>alert(1)</script>\ny sigue en la misma línea.\n\n- **Sur:** máxima producción\n- Este-Oeste & más\n\nÚltimo párrafo.";
        List<Informe.CategoriaSel> sel = List.of(new Informe.CategoriaSel("recomendaciones", List.of(
                new Informe.ModuloSel("rec-7", List.of(), "Orientación <b>x</b>", texto),
                new Informe.ModuloSel("rec-8", List.of(), "Vacía", "  "),
                new Informe.ModuloSel("rec-9;malo", List.of(), "Id raro", "texto"),
                new Informe.ModuloSel("consumo-perfil", List.of(SVG), "Una gráfica no es recomendación", "texto"))));
        String html = Informe.generar(calculo(), LocalDate.of(2026, 11, 16), sel);
        assertTrue(html.contains("</i>Recomendaciones</h2>"));
        assertTrue(html.contains("<h3>Orientación &lt;b&gt;x&lt;/b&gt;</h3>"));
        assertTrue(html.contains("<b>Análisis horario.</b>"));
        assertTrue(html.contains("<i>a las horas</i>"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt; y sigue en la misma línea.</p>"), "las líneas seguidas forman un párrafo");
        assertTrue(html.contains("<ul><li><b>Sur:</b> máxima producción</li><li>Este-Oeste &amp; más</li></ul>"));
        assertTrue(html.contains("<p>Último párrafo.</p>"));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("Vacía") || html.contains("Id raro") || html.contains("Una gráfica no es recomendación"));
        assertFalse(html.contains("</i>Consumo</h2>"));
    }

    @Test
    void sinRecomendacionesValidasNoSeAbreLaCategoria() {
        List<Informe.CategoriaSel> sel = List.of(new Informe.CategoriaSel("recomendaciones", List.of(new Informe.ModuloSel("rec-1", List.of(), "T", ""))));
        assertFalse(Informe.generar(calculo(), LocalDate.of(2026, 11, 16), sel).contains("Recomendaciones</h2>"));
    }

    @Test
    void sinSeleccionVaElInformeCompletoDeSiempre() {
        String html = Informe.generar(calculo(), LocalDate.of(2026, 11, 16));
        assertTrue(html.contains("Escenarios de potencia") && html.contains("La inversión") && html.contains("Instalación propuesta"));
        assertTrue(html.contains("/7</footer>"));
    }

    @Test
    void cadaModuloDelCatalogoTieneTitularYSeEscribe() {
        Calculo c = calculo();
        for (String id : Informe.CATEGORIA_DE.keySet()) {
            String cat = Informe.CATEGORIA_DE.get(id);
            String html = Informe.generar(c, LocalDate.of(2026, 11, 16), List.of(new Informe.CategoriaSel(cat, List.of(new Informe.ModuloSel(id, List.of(SVG))))));
            assertTrue(html.contains("</i>" + Informe.CATEGORIAS.get(cat) + "</h2>"), id);
            assertFalse(html.contains("<h3></h3>"), id);
        }
    }
}
