package api.aura.service.modulo_planificador.estudio;

import api.aura.config.ErrorApi;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Motor de los estudios fotovoltaicos: finanzas calibradas con un informe real, simulación y lectura de ficheros. */
class EstudioMotorTest {

    private static Curva laboral(double kwhDia) {
        double[] k = new double[Curva.HORAS];
        for (int i = 0; i < Curva.HORAS; i++) {
            int h = Curva.hora(i);
            k[i] = Curva.laborable(i) && h >= 8 && h < 18 ? kwhDia / 10.0 : 0.5;
        }
        return new Curva(k, false, "prueba");
    }

    // ------------------------------------------------------------------ finanzas

    @Test
    void lasFinanzasSalenComoEnElInformeDeReferencia() {
        // Eiffage: 1.000,3 kWp a 0,85 €/Wp, 814.705 kWh solares consumidos, 0,10 €/kWh, 8.330 €/año de mantenimiento
        var e = Finanzas.Entrada.conValoresPorDefecto(850_263.5, 814_705, 0, 0.10, 8_330, 0);
        var r = Finanzas.calcular(e);
        assertEquals(73_140, r.ahorroPrimerAno(), 5);
        assertEquals(10.3, r.retorno(), 0.05);
        assertEquals(14.1, r.retornoDescontado(), 0.05);
        assertEquals(495_000, r.van(), 1_000);
        assertEquals(0.096, r.tir(), 0.001);
        assertEquals(2_548_000, r.ahorroAcumulado(), 1_500);
        assertEquals(26, r.acumulado().length);
        assertEquals(-850_263.5, r.acumulado()[0], 0.01);
    }

    @Test
    void unPrecioDeEnergiaMasAltoAcortaElRetorno() {
        var e = Finanzas.Entrada.conValoresPorDefecto(850_263.5, 814_705, 0, 0.10, 8_330, 0);
        assertEquals(8.3, Finanzas.calcular(e.conPrecio(0.125)).retorno(), 0.1);
        assertEquals(12.5, Finanzas.calcular(e.conPrecio(0.125)).tir() * 100, 0.2);
    }

    @Test
    void siNuncaSeRecuperaNoHayRetorno() {
        var r = Finanzas.calcular(Finanzas.Entrada.conValoresPorDefecto(1_000_000, 1_000, 0, 0.10, 500, 0));
        assertTrue(Double.isNaN(r.retorno()));
        assertNull(Finanzas.aMapa(Finanzas.Entrada.conValoresPorDefecto(1_000_000, 1_000, 0, 0.10, 500, 0), r).get("retorno"));
    }

    // ------------------------------------------------------------------ simulación

    @Test
    void laProduccionAnualEsLaEspecificaPorLosKwp() {
        double[] p = Simulacion.produccionPorKwp(1420, "este-oeste");
        double suma = 0;
        for (double v : p) suma += v;
        assertEquals(1420, suma, 0.5);
        assertEquals(0, p[Curva.indice(5, 15, 2)], 1e-9, "de noche no hay producción");
        assertTrue(p[Curva.indice(5, 15, 13)] > p[Curva.indice(11, 15, 13)], "en junio produce más que en diciembre");
    }

    @Test
    void sinBateriaLoQueNoSeConsumeSeRecorta() {
        Curva c = laboral(400);
        var r = Simulacion.simular(c, Simulacion.Instalacion.de(100, 0, "sur", null));
        assertEquals(r.produccion(), r.directa() + r.recortada(), 1e-6);
        assertTrue(r.directa() <= c.total());
        assertEquals(0, r.desdeBateria(), 1e-9);
        assertEquals(0, r.ciclos(), 1e-9);
        assertEquals(100 * 1685, r.produccion(), 5);
    }

    @Test
    void laBateriaAumentaLaCoberturaYGuardaLaEnergia() {
        Curva c = laboral(300);
        var sin = Simulacion.simular(c, Simulacion.Instalacion.de(150, 0, "sur", null));
        var con = Simulacion.simular(c, Simulacion.Instalacion.de(150, 200, "sur", null));
        assertTrue(con.cobertura() > sin.cobertura());
        assertTrue(con.desdeBateria() > 0);
        assertTrue(con.ciclos() > 0 && con.ciclos() < 400);
        // Lo que sale de la batería nunca supera lo que entró por el rendimiento de ida y vuelta
        double entrada = sin.recortada() - con.recortada();
        assertTrue(con.desdeBateria() <= entrada * 0.90 + 1e-6, "rendimiento de ida y vuelta");
        assertTrue(con.solarConsumida() <= con.produccion() + 1e-6);
    }

    @Test
    void masPanelesNuncaBajanLaProduccionAprovechadaTotal() {
        Curva c = laboral(500);
        double antes = 0;
        for (double kwp : new double[]{50, 100, 150, 200, 300}) {
            double v = Simulacion.simular(c, Simulacion.Instalacion.de(kwp, 0, "este-oeste", null)).solarConsumida();
            assertTrue(v >= antes);
            antes = v;
        }
    }

    // ------------------------------------------------------------------ curva real

    @Test
    void leeLaCurvaHorariaDeUnCsvConHoraDel1Al24() {
        StringBuilder sb = new StringBuilder("CUPS;Fecha;Hora;Consumo_kWh;Metodo\n");
        for (LocalDate d = LocalDate.of(2025, 1, 1); d.isBefore(LocalDate.of(2026, 1, 1)); d = d.plusDays(1)) {
            for (int h = 1; h <= 24; h++) {
                sb.append("ES001;").append(String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear()))
                        .append(';').append(h).append(';').append("1,5;R\n");
            }
        }
        Curva c = Curva.leer(sb.toString().getBytes(StandardCharsets.UTF_8), "curva.csv");
        assertEquals(8760 * 1.5, c.total(), 1e-6);
        assertFalse(c.estimada());
    }

    @Test
    void laColumnaDeFechasPuedeLlamarseMes() {
        StringBuilder sb = new StringBuilder("CUPS;Mes;Hora;Consumo_kWh\n");
        StringBuilder junto = new StringBuilder("Mes;Consumo (kWh)\n");
        for (LocalDate d = LocalDate.of(2025, 1, 1); d.isBefore(LocalDate.of(2026, 1, 1)); d = d.plusDays(1)) {
            for (int h = 1; h <= 24; h++) {
                String fecha = String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
                sb.append("ES001;").append(fecha).append(';').append(h).append(";1,5\n");
                junto.append(fecha).append(' ').append(String.format("%02d:00", h - 1)).append(";1,5\n");
            }
        }
        assertEquals(8760 * 1.5, Curva.leer(sb.toString().getBytes(StandardCharsets.UTF_8), "curva.csv").total(), 1e-6);
        assertEquals(8760 * 1.5, Curva.leer(junto.toString().getBytes(StandardCharsets.UTF_8), "curva.csv").total(), 1e-6);
    }

    @Test
    void leeLaCurvaCuartohorariaDeUnExcelConVariasHojas() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            // Primera hoja: un resumen mensual con una columna «Mes» y totales, que no es la curva
            Sheet resumen = wb.createSheet("Resumen");
            Row cab = resumen.createRow(0);
            String[] titulos = {"Mes", "P1", "P2", "Total kWh", "Potencia máx. kW"};
            for (int i = 0; i < titulos.length; i++) cab.createCell(i).setCellValue(titulos[i]);
            Row mes = resumen.createRow(1);
            mes.createCell(0).setCellValue("2025-01");
            mes.createCell(3).setCellValue(1000);
            // Segunda hoja: los cuartos de hora con la fecha-hora como fecha de Excel
            Sheet datos = wb.createSheet("Cuarto horario");
            Row c2 = datos.createRow(0);
            String[] t2 = {"Fecha-hora (inicio del cuarto)", "Mes", "Periodo", "Consumo kWh", "Origen"};
            for (int i = 0; i < t2.length; i++) c2.createCell(i).setCellValue(t2[i]);
            var estilo = wb.createCellStyle();
            estilo.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
            int fila = 1;
            for (LocalDateTime t = LocalDateTime.of(2025, 1, 1, 0, 0); t.isBefore(LocalDateTime.of(2026, 1, 1, 0, 0)); t = t.plusMinutes(15)) {
                Row r = datos.createRow(fila++);
                var f = r.createCell(0);
                f.setCellValue(t);
                f.setCellStyle(estilo);
                r.createCell(1).setCellValue(t.getYear() + "-" + String.format("%02d", t.getMonthValue()));
                r.createCell(2).setCellValue("P6");
                r.createCell(3).setCellValue(0.5);
                r.createCell(4).setCellValue("real");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            Curva c = Curva.leer(out.toByteArray(), "curva_cuartohoraria.xlsx");
            assertEquals(365 * 24 * 4 * 0.5, c.total(), 1e-6, "los cuatro cuartos de cada hora se suman");
        }
    }

    @Test
    void leeElExcelDeTelegestConLaHoraQueTermina() {
        // Como la hoja «amCharts» de Telegest: «date» es la hora que termina (de 01:00 de un día a 00:00 del mismo día un año después)
        StringBuilder sb = new StringBuilder("date;value;max;tooltip;c_punto;t_punto\n");
        LocalDateTime t = LocalDateTime.of(2024, 12, 9, 1, 0);
        for (int i = 0; i < Curva.HORAS; i++, t = t.plusHours(1)) {
            double v = t.getHour() == 1 ? 7 : 2;
            sb.append(t.toString().replace('T', ' ')).append(":00;").append(v).append(';').append(v).append(";Consumo;#00CC00;1\n");
        }
        Curva c = Curva.leer(sb.toString().getBytes(StandardCharsets.UTF_8), "Telegest - Grafico Curva Horaria.csv");
        assertFalse(c.estimada());
        assertEquals(365 * (7 + 23 * 2.0), c.total(), 1e-6);
        assertEquals(7.0, c.kwh()[Curva.indice(5, 15, 0)], 1e-9, "lo de las 01:00 es el consumo de 0:00 a 1:00");
        assertEquals(2.0, c.kwh()[Curva.indice(5, 15, 1)], 1e-9);
    }

    @Test
    void sumaLosCuartosDeHoraYSeQuedaConElUltimoAno() {
        StringBuilder sb = new StringBuilder("Fecha,Consumo kWh\n");
        for (LocalDate d = LocalDate.of(2024, 6, 1); d.isBefore(LocalDate.of(2026, 1, 1)); d = d.plusDays(1)) {
            for (int q = 0; q < 96; q++) {
                sb.append(d).append(' ').append(String.format("%d:%02d", q / 4, (q % 4) * 15)).append(",0.25\n");
            }
        }
        Curva c = Curva.leer(sb.toString().getBytes(StandardCharsets.UTF_8), "cuartos.csv");
        assertEquals(8760.0, c.total(), 30.0, "24 kWh al día durante un año (sin el 29 de febrero)");
    }

    @Test
    void leeLaTablaHtmlConExtensionXlsYLaFechaSinCabecera() {
        StringBuilder sb = new StringBuilder("<html><body><table><thead><tr><th class=\"empty\"></th><th>Energía activa consumida - CLIENTE PUNTO SL_1234AB - 1234AB</th></tr></thead><tbody>");
        for (LocalDate d = LocalDate.of(2025, 1, 1); d.isBefore(LocalDate.of(2026, 1, 1)); d = d.plusDays(1)) {
            for (int q = 0; q < 96; q++) {
                sb.append("<tr><th scope=\"row\">").append(d).append(' ').append(String.format("%02d:%02d:00", q / 4, (q % 4) * 15)).append("</th><td>1</td></tr>");
            }
        }
        sb.append("</tbody></table></body></html>");
        Curva c = Curva.leer(sb.toString().getBytes(StandardCharsets.UTF_8), "DATOS CUARTOHORARIOS X.xls");
        assertEquals(8760.0 * 4, c.total(), 120.0, "1 kWh cada cuarto de hora durante un año");
    }

    @Test
    void unaCurvaIncompletaSeRechazaConUnMensaje() {
        StringBuilder sb = new StringBuilder("Fecha;Hora;kWh\n");
        for (int d = 1; d <= 20; d++) for (int h = 1; h <= 24; h++) sb.append(String.format("%02d/01/2025;%d;1\n", d, h));
        ErrorApi e = assertThrows(ErrorApi.class, () -> Curva.leer(sb.toString().getBytes(StandardCharsets.UTF_8), "x.csv"));
        assertTrue(e.getMessage().contains("cubre"));
        ErrorApi sinColumnas = assertThrows(ErrorApi.class, () -> Curva.leer("a;b\n1;2\n".getBytes(StandardCharsets.UTF_8), "y.csv"));
        assertTrue(sinColumnas.getMessage().contains("columnas"));
    }

    @Test
    void laCurvaSeGuardaYSeRecuperaSinPerdidaUtil() {
        Curva c = laboral(400);
        Curva otra = Curva.desdeTexto(c.comoTexto(), false, "x");
        assertEquals(c.total(), otra.total(), 5);
    }

    // ------------------------------------------------------------------ SIPS

    private static byte[] sipsDePrueba(int lecturas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet h = wb.createSheet("sips");
            h.createRow(0).createCell(0).setCellValue("Datos suministro");
            Row cab = h.createRow(1);
            String[] nombres = {"", "Distribuidora", "Tarifa", "Potencia Contratada P1", "Potencia Contratada P2", "Potencia Contratada P3",
                    "Potencia Contratada P4", "Potencia Contratada P5", "Potencia Contratada P6", "kWh Anual"};
            for (int i = 0; i < nombres.length; i++) cab.createCell(i).setCellValue(nombres[i]);
            Row val = h.createRow(2);
            val.createCell(0).setCellValue("ES0000000000000000AA");
            val.createCell(1).setCellValue("i-DE");
            val.createCell(2).setCellValue("3.0TD");
            for (int p = 0; p < 6; p++) val.createCell(3 + p).setCellValue(21);
            val.createCell(9).setCellValue(124494);
            h.createRow(3).createCell(0).setCellValue("Datos lecturas");
            Row cl = h.createRow(4);
            cl.createCell(1).setCellValue("Fecha Lectura");
            cl.createCell(2).setCellValue("Fecha Lectura Anterior");
            for (int p = 0; p < 6; p++) cl.createCell(4 + p).setCellValue("Consumo Activa P" + (p + 1));
            LocalDate fin = LocalDate.of(2025, 12, 31);
            for (int m = 0; m < lecturas; m++) {
                LocalDate hasta = fin.minusMonths(m).withDayOfMonth(fin.minusMonths(m).lengthOfMonth());
                LocalDate desde = hasta.minusMonths(1).withDayOfMonth(hasta.minusMonths(1).lengthOfMonth());
                Row r = h.createRow(5 + m);
                r.createCell(0).setCellValue("ES0000000000000000AA");
                r.createCell(1).setCellValue(hasta.toEpochDay() + 25569);
                r.createCell(2).setCellValue(desde.toEpochDay() + 25569);
                for (int p = 0; p < 6; p++) r.createCell(4 + p).setCellValue(p == 2 ? 3000 : 1000);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void leeElSipsYEstimaLaCurvaConSusLecturas() throws Exception {
        Sips s = Sips.leer(sipsDePrueba(12));
        assertEquals("3.0TD", s.suministro().get("Tarifa"));
        assertEquals("ES0000000000000000AA", s.suministro().get("CUPS"));
        assertEquals(21, s.potencias()[5], 1e-9);
        assertEquals(124494, s.kwhAnual(), 1e-9);
        assertEquals(12, s.lecturas().size());
        List<Sips.Lectura> otra = Sips.lecturasDeMapa(s.aMapa().get("lecturas"));
        assertEquals(12, otra.size());
        Curva c = CurvaEstimada.crear(s.lecturas(), s.kwhAnual(), CurvaEstimada.Horario.porDefecto());
        assertTrue(c.estimada());
        assertEquals(12 * 8000, c.total(), 12 * 8000 * 0.01);
        assertTrue(c.fraccionDiurna() > 0.6, "con horario de 7 a 19 h casi todo cae de día");
    }

    @Test
    void leeElSipsEnCsvIgualQueEnExcel() {
        StringBuilder csv = new StringBuilder("Datos suministro\n");
        csv.append(";Distribuidora;Tarifa;Potencia Contratada P1;Potencia Contratada P2;Potencia Contratada P3;Potencia Contratada P4;Potencia Contratada P5;Potencia Contratada P6;kWh Anual\n");
        csv.append("ES0000000000000000AA;i-DE;3.0TD;21;21;21;21;21;21;124494\n");
        csv.append("Datos lecturas\n");
        csv.append(";Fecha Lectura;Fecha Lectura Anterior;;Consumo Activa P1;Consumo Activa P2;Consumo Activa P3;Consumo Activa P4;Consumo Activa P5;Consumo Activa P6\n");
        for (int m = 1; m <= 12; m++) {
            LocalDate hasta = LocalDate.of(2025, m, 1).plusMonths(1).minusDays(1), desde = LocalDate.of(2025, m, 1).minusDays(1);
            csv.append(String.format("ES0000000000000000AA;%02d/%02d/%d;%02d/%02d/%d;;1000,5;1000;3000;1000;1000;1000\n",
                    hasta.getDayOfMonth(), hasta.getMonthValue(), hasta.getYear(), desde.getDayOfMonth(), desde.getMonthValue(), desde.getYear()));
        }
        // Lo mismo guardado como UTF-8, como UTF-16 con marca (lo que da Excel con «Unicode») y como UTF-16 sin marca
        byte[] utf16 = csv.toString().getBytes(StandardCharsets.UTF_16LE);
        byte[] conMarca = new byte[utf16.length + 2];
        conMarca[0] = (byte) 0xFF;
        conMarca[1] = (byte) 0xFE;
        System.arraycopy(utf16, 0, conMarca, 2, utf16.length);
        for (byte[] fichero : List.of(csv.toString().getBytes(StandardCharsets.UTF_8), conMarca, utf16)) {
            Sips s = Sips.leer(fichero);
            assertEquals("3.0TD", s.suministro().get("Tarifa"));
            assertEquals("ES0000000000000000AA", s.suministro().get("CUPS"));
            assertEquals(21, s.potencias()[5], 1e-9);
            assertEquals(124494, s.kwhAnual(), 1e-9);
            assertEquals(12, s.lecturas().size());
            assertEquals(1000.5, s.lecturas().get(0).kwh()[0], 1e-9);
        }
    }

    @Test
    void conPocasLecturasNoSeInventaLaCurva() throws Exception {
        Sips s = Sips.leer(sipsDePrueba(3));
        ErrorApi e = assertThrows(ErrorApi.class, () -> CurvaEstimada.crear(s.lecturas(), s.kwhAnual(), CurvaEstimada.Horario.porDefecto()));
        assertTrue(e.getMessage().contains("faltan meses"));
        assertThrows(ErrorApi.class, () -> Sips.leer("no es un excel".getBytes(StandardCharsets.UTF_8)));
    }

    /** Doce lecturas mensuales; cada mes tiene el reparto por periodos de un cliente que consume con el horario de ese mes. */
    private static List<Sips.Lectura> sipsDeCliente(java.util.function.IntFunction<CurvaEstimada.Horario> realDelMes) {
        int[] periodo = Periodos.delAno("3.0TD");
        List<Sips.Lectura> l = new ArrayList<>();
        for (int m = 0; m < 12; m++) {
            Curva verdad = CurvaEstimada.crear(List.of(), 120_000, realDelMes.apply(m));
            double[] kwh = new double[6];
            for (int i = 0; i < Curva.HORAS; i++) if (Curva.mes(i) == m) kwh[periodo[i]] += verdad.kwh()[i];
            l.add(new Sips.Lectura(LocalDate.of(2025, m + 1, 1), LocalDate.of(2025, m + 1, 1).plusMonths(1), kwh));
        }
        return l;
    }

    @Test
    void avisaSiElSipsContradiceElHorarioSupuesto() {
        // El cliente trabaja las 24 h todo el año, pero la estimación supone de 7 a 19 h
        List<Sips.Lectura> sips = sipsDeCliente(m -> new CurvaEstimada.Horario(0, 24, 1, 1));
        Curva estimada = CurvaEstimada.crear(sips, 120_000, CurvaEstimada.Horario.porDefecto());
        String aviso = CurvaEstimada.avisoPeriodos(estimada, sips, "3.0TD");
        assertNotNull(aviso);
        assertTrue(aviso.contains("según el SIPS"));
    }

    @Test
    void avisaSoloDeLosMesesConOtraJornada() {
        // De enero a junio trabaja las 24 h; de julio a diciembre, el horario supuesto
        CurvaEstimada.Horario supuesto = CurvaEstimada.Horario.porDefecto();
        List<Sips.Lectura> sips = sipsDeCliente(m -> m < 6 ? new CurvaEstimada.Horario(0, 24, 1, 1) : supuesto);
        Curva estimada = CurvaEstimada.crear(sips, 120_000, supuesto);
        String aviso = CurvaEstimada.avisoPeriodos(estimada, sips, "3.0TD");
        assertNotNull(aviso);
        assertTrue(aviso.contains("enero"));
        assertFalse(aviso.contains("noviembre") || aviso.contains("diciembre"), "los meses que encajan no se señalan");
    }

    @Test
    void conLaTarifaCadaPeriodoCuadraConElSips() {
        // El cliente trabaja las 24 h; aunque se suponga de 7 a 19 h, con la tarifa los periodos salen como los del SIPS
        List<Sips.Lectura> sips = sipsDeCliente(m -> new CurvaEstimada.Horario(0, 24, 1, 1));
        Curva sinTarifa = CurvaEstimada.crear(sips, 120_000, CurvaEstimada.Horario.porDefecto());
        Curva conTarifa = CurvaEstimada.crear(sips, 120_000, CurvaEstimada.Horario.porDefecto(), "3.0TD");
        assertNotNull(CurvaEstimada.avisoPeriodos(sinTarifa, sips, "3.0TD"));
        assertNull(CurvaEstimada.avisoPeriodos(conTarifa, sips, "3.0TD"));
        double total = 0;
        for (Sips.Lectura l : sips) total += l.total();
        assertEquals(total, conTarifa.total(), total * 1e-6, "el consumo anual no cambia");
    }

    @Test
    void noAvisaSiElHorarioEncajaConElSips() {
        CurvaEstimada.Horario h = CurvaEstimada.Horario.porDefecto();
        List<Sips.Lectura> sips = sipsDeCliente(m -> h);
        Curva estimada = CurvaEstimada.crear(sips, 120_000, h);
        assertNull(CurvaEstimada.avisoPeriodos(estimada, sips, "3.0TD"));
    }

    @Test
    void unaJornadaDistintaEnAlgunosMesesCambiaSoloEsosMeses() {
        var verano = new CurvaEstimada.Excepcion(List.of(7, 8), 0, 24, 1, 1);
        var base = CurvaEstimada.Horario.porDefecto();
        var conExcepcion = new CurvaEstimada.Horario(7, 19, 0.15, 0.10, List.of(verano));
        Curva a = CurvaEstimada.crear(List.of(), 120_000, base);
        Curva b = CurvaEstimada.crear(List.of(), 120_000, conExcepcion);
        assertEquals(a.total(), b.total(), 1, "el consumo anual no cambia, solo su reparto");
        // De madrugada en enero igual que antes (en proporción); en agosto, mucho más
        int enero3 = Curva.indice(0, 8, 3), agosto3 = Curva.indice(7, 8, 3);
        assertTrue(b.kwh()[agosto3] > 3 * a.kwh()[agosto3], "en agosto trabaja de noche");
        assertTrue(b.kwh()[enero3] < a.kwh()[enero3], "el resto de meses pierde el peso que gana agosto");
    }

    @Test
    void dosJornadasNoPuedenCompartirMes() {
        var h = new CurvaEstimada.Horario(7, 19, 0.15, 0.10, List.of(
                new CurvaEstimada.Excepcion(List.of(7, 8), 0, 24, 1, 1), new CurvaEstimada.Excepcion(List.of(8, 9), 8, 15, 0.1, 0.1)));
        assertThrows(ErrorApi.class, () -> CurvaEstimada.crear(List.of(), 120_000, h));
    }

    @Test
    void conLasJornadasDeCadaMesElAvisoNoSalta() {
        var verano = new CurvaEstimada.Excepcion(List.of(7, 8), 0, 24, 1, 1);
        var h = new CurvaEstimada.Horario(7, 19, 0.15, 0.10, List.of(verano));
        List<Sips.Lectura> sips = sipsDeCliente(m -> m == 6 || m == 7 ? new CurvaEstimada.Horario(0, 24, 1, 1) : CurvaEstimada.Horario.porDefecto());
        assertNull(CurvaEstimada.avisoPeriodos(CurvaEstimada.crear(sips, 120_000, h), sips, "3.0TD"));
        // Sin declararla, el aviso señala julio y agosto
        String sin = CurvaEstimada.avisoPeriodos(CurvaEstimada.crear(sips, 120_000, CurvaEstimada.Horario.porDefecto()), sips, "3.0TD");
        assertNotNull(sin);
        assertTrue(sin.contains("julio") && sin.contains("agosto"));
    }

    @Test
    void unaInterrupcionBajaElConsumoEnESaFranja() {
        // De 8 a 14 y de 16 a 20 h: a las 15 h para, como fuera de horario
        var partida = new CurvaEstimada.Horario(8, 14, 0.15, 0.10, List.of(), List.of(new CurvaEstimada.Tramo(16, 20)));
        var seguida = new CurvaEstimada.Horario(8, 20, 0.15, 0.10);
        Curva p = CurvaEstimada.crear(List.of(), 120_000, partida);
        Curva s = CurvaEstimada.crear(List.of(), 120_000, seguida);
        int h15 = Curva.indice(0, 8, 15), h17 = Curva.indice(0, 8, 17);
        assertTrue(p.kwh()[h15] < 0.5 * s.kwh()[h15], "a las 15 h la jornada partida para");
        // El total anual es el mismo, así que con menos horas activas cada una pesa algo más; a las 17 h sigue a plena carga
        assertTrue(p.kwh()[h17] > s.kwh()[h17], "a las 17 h trabaja a plena carga");
        assertTrue(p.kwh()[h17] > 3 * p.kwh()[h15], "y mucho más que en la interrupción");
    }

    @Test
    void lasFranjasNoPuedenSolaparse() {
        var h = new CurvaEstimada.Horario(8, 14, 0.15, 0.10, List.of(), List.of(new CurvaEstimada.Tramo(13, 20)));
        assertThrows(ErrorApi.class, () -> CurvaEstimada.crear(List.of(), 120_000, h));
    }

    @Test
    void unHorarioAbsurdoSeRechaza() throws Exception {
        Sips s = Sips.leer(sipsDePrueba(12));
        assertThrows(ErrorApi.class, () -> CurvaEstimada.crear(s.lecturas(), 0, new CurvaEstimada.Horario(19, 7, 0.1, 0.1)));
        List<Sips.Lectura> sin = new ArrayList<>();
        assertEquals(124494, CurvaEstimada.crear(sin, 124494, CurvaEstimada.Horario.porDefecto()).total(), 1);
    }

    @Test
    void aisladaValoraLaSolarConElCosteDelGrupoYNoUsaLaRed() {
        java.util.function.Function<String, java.util.Map<String, Object>> datos = modo -> {
            var ins = new java.util.HashMap<String, Object>(java.util.Map.of("kwp", 50, "bateriaKwh", 0, "orientacion", "sur"));
            var eco = new java.util.HashMap<String, Object>(java.util.Map.of("modo", modo, "precioEnergia", 0.10, "precioGasoil", 1.20,
                    "consumoGrupo", 0.30, "mantenimientoGrupo", 0.03, "ahorroPotencia", 5000));
            return new java.util.HashMap<String, Object>(java.util.Map.of("instalacion", ins, "economia", eco));
        };
        Curva c = laboral(400);
        Calculo red = Calculo.de(datos.apply("red"), c);
        Calculo aislada = Calculo.de(datos.apply("aislada"), c);
        assertFalse(red.aislada);
        assertTrue(aislada.aislada);
        assertEquals(0.39, aislada.precioEnergia, 1e-9);
        assertEquals(0, aislada.ahorroPotencia, 0);
        assertEquals(5000, red.ahorroPotencia, 0);
        double solar = aislada.base.sim().solarConsumida();
        @SuppressWarnings("unchecked")
        var g = (java.util.Map<String, Object>) aislada.aMapa().get("grupo");
        assertEquals(solar * 0.30, (double) g.get("litrosAhorrados"), 1e-6);
        assertEquals(c.total() * 0.30, (double) g.get("litrosSinSolar"), 1e-6);
        assertNull(red.aMapa().get("grupo"));
    }
}
